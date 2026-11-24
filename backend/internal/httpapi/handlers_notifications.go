package httpapi

import (
	"errors"
	"io"
	"net/http"

	"unicep-portal/internal/storage"
)

func (s *Server) handleListNotifications(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	unreadOnly := r.URL.Query().Get("unread") == "true"
	items, err := s.store.ListNotifications(claims.RA, unreadOnly, 100)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao listar notificações")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": items})
}

func (s *Server) handleMarkNotificationsRead(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	var req struct {
		IDs []int64 `json:"ids"`
	}
	if r.Body != nil {
		if err := decodeStrictJSON(w, r, &req, 8192); err != nil && !errors.Is(err, io.EOF) {
			writeError(w, http.StatusBadRequest, "JSON inválido")
			return
		}
	}
	if len(req.IDs) > 200 {
		writeError(w, http.StatusBadRequest, "quantidade de IDs excedida")
		return
	}
	if err := s.store.MarkNotificationsRead(claims.RA, req.IDs); err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao atualizar notificações")
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleSyncNow(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	if !s.actionRALim.Allow("sync:" + claims.RA) {
		writeError(w, http.StatusTooManyRequests, "muitas sincronizações; aguarde um instante")
		return
	}
	created, err := s.worker.SyncUserWithOptions(r.Context(), claims.RA, false, true)
	if err != nil && created == 0 {
		s.writePortalErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]int{"created": created})
}

func (s *Server) handleTestNotification(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	if !s.actionRALim.Allow("testnotif:" + claims.RA) {
		writeError(w, http.StatusTooManyRequests, "aguarde antes de enviar outro teste")
		return
	}
	id, err := s.store.InsertNotification(storage.Notification{
		RA:             claims.RA,
		PeriodKey:      "test",
		DisciplineCode: "TEST001",
		DisciplineName: "Teste de Notificação",
		Field:          "P1",
		OldValue:       "-",
		NewValue:       "9,5",
		Kind:           "grade",
	})
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao criar notificação de teste")
		return
	}
	writeJSON(w, http.StatusOK, map[string]int64{"id": id})
}
