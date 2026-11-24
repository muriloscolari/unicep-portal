package httpapi

import (
	"mime"
	"net/http"
	"net/url"
	"path"
	"strings"
)

func (s *Server) handleProxyTicket(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	claims := currentClaims(r)
	if !s.actionRALim.Allow("ticket:" + claims.RA) {
		writeError(w, http.StatusTooManyRequests, "muitas solicitações; aguarde alguns segundos")
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
	if !isAllowedProxyPath(relPath) {
		writeError(w, http.StatusForbidden, "caminho não permitido no proxy")
		return
	}
	ticket, err := s.crypto.IssueProxyTicket(claims.RA, relPath)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao gerar ticket de proxy")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"ticket":    ticket,
		"expiresIn": 60,
	})
}

func (s *Server) handleProxy(w http.ResponseWriter, r *http.Request) {
	relPath := strings.TrimSpace(r.URL.Query().Get("path"))
	if !isAllowedProxyPath(relPath) {
		writeError(w, http.StatusForbidden, "caminho não permitido no proxy")
		return
	}

	ra, ok := s.authenticateProxyRequest(w, r, relPath)
	if !ok {
		return
	}

	raw, ct, _, _, err := s.portal.FetchRaw(r.Context(), ra, relPath)
	if err != nil {
		s.writePortalErr(w, err)
		return
	}

	writeSandboxedFileResponse(w, raw, ct, "", false, "")
}

func (s *Server) authenticateProxyRequest(w http.ResponseWriter, r *http.Request, ticketKey string) (string, bool) {
	if bearer := extractBearer(r); bearer != "" {
		claims, err := s.crypto.VerifyToken(bearer)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "sessão expirada")
			return "", false
		}
		tv, err := s.store.GetTokenVersion(claims.RA)
		if err != nil || tv == 0 || claims.TokenVersion != tv {
			writeError(w, http.StatusUnauthorized, "sessão revogada ou expirada")
			return "", false
		}
		return claims.RA, true
	}
	ticket := strings.TrimSpace(r.URL.Query().Get("ticket"))
	ra, err := s.crypto.ConsumeProxyTicket(ticket, ticketKey)
	if err != nil {
		writeError(w, http.StatusUnauthorized, "ticket de acesso ausente ou expirado")
		return "", false
	}
	return ra, true
}

func sanitizeDownloadFilename(raw string) string {
	cleaned := strings.TrimSpace(raw)
	if cleaned == "" {
		return ""
	}
	cleaned = path.Base(strings.ReplaceAll(cleaned, "\\", "/"))
	var b strings.Builder
	for _, r := range cleaned {
		switch {
		case r < 0x20 || r == 0x7f || r == '"' || r == '/' || r == '\\':
			b.WriteRune('_')
		default:
			b.WriteRune(r)
		}
	}
	out := strings.Trim(strings.TrimSpace(b.String()), ".")
	if out == "" {
		return "arquivo"
	}
	if len(out) > 160 {
		out = out[:160]
	}
	return out
}

func writeSandboxedFileResponse(w http.ResponseWriter, raw []byte, ct string, contentDisposition string, forceAttachment bool, requestedName string) {
	safeCT, attach := sanitizeProxyContentType(ct)
	w.Header().Set("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data: https:; form-action 'none'; frame-ancestors 'none'")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("Referrer-Policy", "no-referrer")
	w.Header().Set("Cache-Control", "private, no-store")
	w.Header().Set("Content-Type", safeCT)
	if attach || forceAttachment {
		var resolvedName string
		if safeName := sanitizeDownloadFilename(requestedName); safeName != "" {
			resolvedName = safeName
		} else if contentDisposition != "" {
			if _, params, err := mime.ParseMediaType(contentDisposition); err == nil {
				resolvedName = sanitizeDownloadFilename(params["filename"])
			}
		}
		if resolvedName != "" {
			if formatted := mime.FormatMediaType("attachment", map[string]string{"filename": resolvedName}); formatted != "" {
				w.Header().Set("Content-Disposition", formatted)
			} else {
				w.Header().Set("Content-Disposition", "attachment")
			}
		} else {
			w.Header().Set("Content-Disposition", "attachment")
		}
	}
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(raw)
}

func sanitizeProxyContentType(rawCT string) (contentType string, forceAttachment bool) {
	if rawCT == "" {
		return "application/octet-stream", true
	}
	mediaType, params, err := mime.ParseMediaType(rawCT)
	if err != nil {
		return "application/octet-stream", true
	}
	mediaType = strings.ToLower(mediaType)
	switch mediaType {
	case "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp", "text/plain", "text/html":
		if charset, ok := params["charset"]; ok && charset != "" {
			return mediaType + "; charset=" + charset, false
		}
		return mediaType, false
	default:
		return "application/octet-stream", true
	}
}

func isAllowedProxyPath(p string) bool {
	if p == "" || len(p) > 512 || hasControlOrBackslash(p) {
		return false
	}
	lower := strings.ToLower(p)
	if strings.Contains(lower, "://") || strings.Contains(lower, "..") || strings.Contains(lower, "%2e") || strings.Contains(lower, "%5c") {
		return false
	}
	parsed, err := url.Parse(p)
	if err != nil || parsed.Scheme != "" || parsed.Host != "" || parsed.User != nil || parsed.Fragment != "" {
		return false
	}
	cleaned := path.Clean(parsed.Path)
	if cleaned != parsed.Path || strings.Contains(cleaned, "..") {
		return false
	}
	allowed := []string{
		"/ambiente_aluno/",
		"/modulo_matricula/",
		"/financeiro/",
		"/symLinkFTP/",
	}
	for _, prefix := range allowed {
		if strings.HasPrefix(cleaned, prefix) {
			return true
		}
	}
	return false
}
