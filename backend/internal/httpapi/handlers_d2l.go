package httpapi

import (
	"context"
	"errors"
	"net/http"
	"strconv"
	"strings"

	"unicep-portal/internal/d2l"
)

func (s *Server) handleD2LOverview(w http.ResponseWriter, r *http.Request) {
	if s.d2l == nil {
		writeErrorWithCode(w, http.StatusServiceUnavailable, "d2l_unavailable", "integração com a Sala Virtual indisponível")
		return
	}
	claims := currentClaims(r)
	forceRefresh := r.URL.Query().Get("refresh") == "1" || strings.EqualFold(r.URL.Query().Get("refresh"), "true")
	if forceRefresh {
		if !s.d2lOverviewLim.Allow("d2loverview:" + claims.RA) {
			w.Header().Set("Retry-After", "60")
			writeErrorWithCode(w, http.StatusTooManyRequests, "rate_limited", "muitas atualizações na Sala Virtual; aguarde um instante")
			return
		}
	}
	ov, err := s.d2l.GetOverviewWithOptions(r.Context(), claims.RA, forceRefresh)
	if err != nil {
		s.writeD2LErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, ov)
}

func (s *Server) handleD2LCourseDetail(w http.ResponseWriter, r *http.Request) {
	if s.d2l == nil {
		writeErrorWithCode(w, http.StatusServiceUnavailable, "d2l_unavailable", "integração com a Sala Virtual indisponível")
		return
	}
	ouRaw := strings.TrimSpace(r.PathValue("ou"))
	ou, err := strconv.ParseInt(ouRaw, 10, 64)
	if err != nil || ou <= 0 {
		writeError(w, http.StatusBadRequest, "identificador de disciplina inválido")
		return
	}
	claims := currentClaims(r)
	if !s.d2lDetailLim.Allow("d2ldetail:" + claims.RA) {
		w.Header().Set("Retry-After", "60")
		writeErrorWithCode(w, http.StatusTooManyRequests, "rate_limited", "muitas consultas seguidas na Sala Virtual; aguarde alguns segundos")
		return
	}
	detail, err := s.d2l.GetCourseDetail(r.Context(), claims.RA, ou)
	if err != nil {
		s.writeD2LErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, detail)
}

func (s *Server) handleD2LTicket(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	if s.d2l == nil {
		writeErrorWithCode(w, http.StatusServiceUnavailable, "d2l_unavailable", "integração com a Sala Virtual indisponível")
		return
	}
	claims := currentClaims(r)
	if !s.d2lTicketLim.Allow("d2lticket:" + claims.RA) {
		w.Header().Set("Retry-After", "60")
		writeErrorWithCode(w, http.StatusTooManyRequests, "rate_limited", "muitas solicitações; aguarde alguns segundos")
		return
	}
	var req struct {
		Path string `json:"path"`
	}
	if err := decodeStrictJSON(w, r, &req, 4096); err != nil {
		writeError(w, http.StatusBadRequest, "JSON inválido")
		return
	}
	relPath := strings.TrimSpace(req.Path)
	if !d2l.IsAllowedFilePath(relPath) {
		writeError(w, http.StatusForbidden, "caminho não permitido na Sala Virtual")
		return
	}
	ticket, err := s.crypto.IssueProxyTicket(claims.RA, "d2l:"+relPath)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao gerar ticket de acesso")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"ticket":    ticket,
		"expiresIn": 60,
	})
}

func (s *Server) handleGetD2LSettings(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	u, err := s.store.GetUser(claims.RA)
	if err != nil || u == nil {
		writeError(w, http.StatusNotFound, "usuário não encontrado")
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{
		"pollingEnabled": u.D2LPollingEnabled,
	})
}

func (s *Server) handlePatchD2LSettings(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	var req struct {
		PollingEnabled bool `json:"pollingEnabled"`
	}
	if err := decodeStrictJSON(w, r, &req, 1024); err != nil {
		writeError(w, http.StatusBadRequest, "JSON inválido")
		return
	}
	if err := s.store.SetD2LPollingEnabled(claims.RA, req.PollingEnabled); err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao atualizar preferência")
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{
		"pollingEnabled": req.PollingEnabled,
	})
}

func (s *Server) handleD2LFile(w http.ResponseWriter, r *http.Request) {
	if s.d2l == nil {
		writeErrorWithCode(w, http.StatusServiceUnavailable, "d2l_unavailable", "integração com a Sala Virtual indisponível")
		return
	}
	relPath := strings.TrimSpace(r.URL.Query().Get("path"))
	if !d2l.IsAllowedFilePath(relPath) {
		writeError(w, http.StatusForbidden, "caminho não permitido na Sala Virtual")
		return
	}
	ra, ok := s.authenticateProxyRequest(w, r, "d2l:"+relPath)
	if !ok {
		return
	}
	raw, ct, cd, err := s.d2l.FetchRaw(r.Context(), ra, relPath)
	if err != nil {
		s.writeD2LErr(w, err)
		return
	}
	forceDownload := strings.TrimSpace(r.URL.Query().Get("download")) == "1"
	requestedName := r.URL.Query().Get("name")
	writeSandboxedFileResponse(w, raw, ct, cd, forceDownload, requestedName)
}

func (s *Server) writeD2LErr(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, d2l.ErrCourseNotAvailable):
		writeErrorWithCode(w, http.StatusNotFound, "d2l_not_found", "disciplina não encontrada ou sem acesso na Sala Virtual")
	case errors.Is(err, d2l.ErrFileNotFound):
		writeErrorWithCode(w, http.StatusNotFound, "d2l_not_found", "arquivo não encontrado na Sala Virtual")
	case errors.Is(err, d2l.ErrFileTooLarge):
		writeErrorWithCode(w, http.StatusRequestEntityTooLarge, "d2l_file_too_large", "o arquivo excede o limite de 16 MB; abra-o diretamente na Sala Virtual")
	case errors.Is(err, context.DeadlineExceeded):
		writeErrorWithCode(w, http.StatusGatewayTimeout, "d2l_timeout", "a Sala Virtual demorou muito para responder; tente novamente")
	case errors.Is(err, d2l.ErrInvalidCredentials), errors.Is(err, d2l.ErrSessionExpired):
		// Keep HTTP 502 (never 401) so the main portal session token stays active while signaling D2L auth failure via code.
		writeErrorWithCode(w, http.StatusBadGateway, "d2l_auth_failed", "não foi possível autenticar na Sala Virtual; verifique suas credenciais ou faça login novamente")
	default:
		writeErrorWithCode(w, http.StatusBadGateway, "d2l_unavailable", "erro ao consultar a Sala Virtual (UnicepVirtual)")
	}
}
