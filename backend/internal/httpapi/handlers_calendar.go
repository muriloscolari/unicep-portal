package httpapi

import (
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"unicep-portal/internal/storage"
)

var allowedCalendarColors = map[string]struct{}{
	"accent": {},
	"red":    {},
	"orange": {},
	"green":  {},
	"indigo": {},
	"teal":   {},
}

var saoPauloCalendarLoc = func() *time.Location {
	if loc, err := time.LoadLocation("America/Sao_Paulo"); err == nil {
		return loc
	}
	return time.FixedZone("BRT", -3*3600)
}()

type calendarEventRequest struct {
	Title           string `json:"title"`
	Description     string `json:"description"`
	Date            string `json:"date"`
	Time            string `json:"time"`
	AllDay          bool   `json:"allDay"`
	StartAtMillis   int64  `json:"startAtMillis"`
	DisciplineName  string `json:"disciplineName"`
	ColorKey        string `json:"colorKey"`
	ReminderMinutes []int  `json:"reminderMinutes"`
}

func parseAndValidateCalendarEvent(req calendarEventRequest) (storage.CalendarEvent, string) {
	title := strings.TrimSpace(req.Title)
	if title == "" {
		return storage.CalendarEvent{}, "o título do evento é obrigatório"
	}
	if utf8.RuneCountInString(title) > 160 {
		return storage.CalendarEvent{}, "título deve ter no máximo 160 caracteres"
	}
	desc := strings.TrimSpace(req.Description)
	if utf8.RuneCountInString(desc) > 1000 {
		return storage.CalendarEvent{}, "descrição deve ter no máximo 1000 caracteres"
	}
	disc := strings.TrimSpace(req.DisciplineName)
	if utf8.RuneCountInString(disc) > 120 {
		return storage.CalendarEvent{}, "nome da disciplina deve ter no máximo 120 caracteres"
	}

	dateStr := strings.TrimSpace(req.Date)
	if len(dateStr) != 10 {
		return storage.CalendarEvent{}, "data inválida (use YYYY-MM-DD)"
	}
	if _, err := time.Parse("2006-01-02", dateStr); err != nil {
		return storage.CalendarEvent{}, "data inválida (use YYYY-MM-DD)"
	}

	timeStr := strings.TrimSpace(req.Time)
	if req.AllDay {
		timeStr = ""
	} else {
		if len(timeStr) != 5 || timeStr[2] != ':' {
			return storage.CalendarEvent{}, "horário inválido (use HH:MM)"
		}
		hh, errH := strconv.Atoi(timeStr[:2])
		mm, errM := strconv.Atoi(timeStr[3:])
		if errH != nil || errM != nil || hh < 0 || hh > 23 || mm < 0 || mm > 59 {
			return storage.CalendarEvent{}, "horário inválido (use HH:MM)"
		}
	}

	startMs := req.StartAtMillis
	if startMs <= 0 {
		effectiveTime := timeStr
		if req.AllDay || effectiveTime == "" {
			effectiveTime = "08:00"
		}
		if parsed, err := time.ParseInLocation("2006-01-02 15:04", dateStr+" "+effectiveTime, saoPauloCalendarLoc); err == nil {
			startMs = parsed.UnixMilli()
		}
	}

	colorKey := strings.TrimSpace(req.ColorKey)
	if colorKey == "" {
		colorKey = "accent"
	} else if _, ok := allowedCalendarColors[colorKey]; !ok {
		return storage.CalendarEvent{}, "cor do evento inválida"
	}

	seen := make(map[int]struct{}, len(req.ReminderMinutes))
	reminders := make([]int, 0, len(req.ReminderMinutes))
	for _, m := range req.ReminderMinutes {
		if m < 0 || m > 20160 {
			return storage.CalendarEvent{}, "lembrete deve estar entre 0 e 20160 minutos"
		}
		if _, exists := seen[m]; !exists {
			seen[m] = struct{}{}
			reminders = append(reminders, m)
		}
	}
	if len(reminders) > 5 {
		return storage.CalendarEvent{}, "máximo de 5 lembretes por evento"
	}
	sort.Ints(reminders)

	return storage.CalendarEvent{
		Title:           title,
		Description:     desc,
		Date:            dateStr,
		Time:            timeStr,
		AllDay:          req.AllDay,
		StartAtMillis:   startMs,
		DisciplineName:  disc,
		ColorKey:        colorKey,
		ReminderMinutes: reminders,
	}, ""
}

func (s *Server) handleListCalendarEvents(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	items, err := s.store.ListCalendarEvents(claims.RA)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao listar eventos do calendário")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"events": items})
}

func (s *Server) handleCreateCalendarEvent(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	if !s.actionRALim.Allow("cal:" + claims.RA) {
		writeError(w, http.StatusTooManyRequests, "muitas alterações seguidas; aguarde um instante")
		return
	}
	var req calendarEventRequest
	if err := decodeStrictJSON(w, r, &req, 16384); err != nil {
		writeError(w, http.StatusBadRequest, "JSON inválido")
		return
	}
	ev, msg := parseAndValidateCalendarEvent(req)
	if msg != "" {
		writeError(w, http.StatusBadRequest, msg)
		return
	}
	count, err := s.store.CountCalendarEvents(claims.RA)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao verificar eventos")
		return
	}
	if count >= 300 {
		writeError(w, http.StatusBadRequest, "limite de 300 eventos no calendário atingido")
		return
	}
	ev.RA = claims.RA
	created, err := s.store.CreateCalendarEvent(ev)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao salvar evento no calendário")
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{"event": created})
}

func (s *Server) handleUpdateCalendarEvent(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	if !s.actionRALim.Allow("cal:" + claims.RA) {
		writeError(w, http.StatusTooManyRequests, "muitas alterações seguidas; aguarde um instante")
		return
	}
	id, err := strconv.ParseInt(strings.TrimSpace(r.PathValue("id")), 10, 64)
	if err != nil || id <= 0 {
		writeError(w, http.StatusBadRequest, "identificador de evento inválido")
		return
	}
	var req calendarEventRequest
	if err := decodeStrictJSON(w, r, &req, 16384); err != nil {
		writeError(w, http.StatusBadRequest, "JSON inválido")
		return
	}
	ev, msg := parseAndValidateCalendarEvent(req)
	if msg != "" {
		writeError(w, http.StatusBadRequest, msg)
		return
	}
	ev.ID = id
	updated, err := s.store.UpdateCalendarEvent(claims.RA, ev)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao atualizar evento")
		return
	}
	if updated == nil {
		writeError(w, http.StatusNotFound, "evento não encontrado")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"event": updated})
}

func (s *Server) handleDeleteCalendarEvent(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	if !s.actionRALim.Allow("cal:" + claims.RA) {
		writeError(w, http.StatusTooManyRequests, "muitas alterações seguidas; aguarde um instante")
		return
	}
	id, err := strconv.ParseInt(strings.TrimSpace(r.PathValue("id")), 10, 64)
	if err != nil || id <= 0 {
		writeError(w, http.StatusBadRequest, "identificador de evento inválido")
		return
	}
	deleted, err := s.store.DeleteCalendarEvent(claims.RA, id)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao excluir evento")
		return
	}
	if !deleted {
		writeError(w, http.StatusNotFound, "evento não encontrado")
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}
