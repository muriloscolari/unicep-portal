package httpapi

import (
	"context"
	"errors"
	"log"
	"net/http"
	"strings"
	"time"

	"unicep-portal/internal/portal"
	"unicep-portal/internal/storage"
)

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	writeJSON(w, http.StatusOK, map[string]any{
		"status": "ok",
		"time":   time.Now().UTC().Format(time.RFC3339),
	})
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	ip := s.clientIP(r)
	if !s.loginIPLim.Allow(ip) {
		writeError(w, http.StatusTooManyRequests, "muitas tentativas de login; aguarde alguns minutos")
		return
	}

	var req struct {
		RA       string `json:"ra"`
		Password string `json:"password"`
		Perfil   string `json:"perfil"`
	}
	if err := decodeStrictJSON(w, r, &req, 8192); err != nil {
		writeError(w, http.StatusBadRequest, "JSON inválido")
		return
	}
	req.RA = strings.TrimSpace(req.RA)
	req.Perfil = strings.TrimSpace(req.Perfil)
	if req.Perfil == "" {
		req.Perfil = "alunograd"
	}
	if !isSafeRA(req.RA) || req.Password == "" || len(req.Password) > 128 {
		writeError(w, http.StatusBadRequest, "RA ou senha inválidos")
		return
	}
	if _, ok := allowedProfiles[req.Perfil]; !ok {
		writeError(w, http.StatusBadRequest, "perfil acadêmico inválido")
		return
	}
	if !s.loginRALim.Allow(req.RA) {
		writeError(w, http.StatusTooManyRequests, "muitas tentativas para este RA; aguarde alguns minutos")
		return
	}

	info, nacOpen, err := s.portal.Login(r.Context(), req.RA, req.Password, req.Perfil)
	if err != nil {
		if errors.Is(err, portal.ErrInvalidCredentials) {
			writeError(w, http.StatusUnauthorized, "usuário e/ou senha inválidos")
			return
		}
		writeError(w, http.StatusBadGateway, "falha ao autenticar no portal da UNICEP")
		return
	}

	encPass, err := s.crypto.Encrypt(req.Password)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro interno de criptografia")
		return
	}
	if err := s.store.UpsertUser(storage.UserRecord{
		RA:                info.RA,
		Perfil:            info.Perfil,
		Name:              info.Name,
		Campus:            info.Campus,
		EncryptedPassword: encPass,
		PollingEnabled:    true,
	}); err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao salvar usuário")
		return
	}

	tv, err := s.store.GetTokenVersion(info.RA)
	if err != nil || tv < 1 {
		tv = 1
	}
	token, exp, err := s.crypto.IssueToken(info.RA, info.Perfil, tv)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao gerar token")
		return
	}

	// Seed snapshot asynchronously so initial login stays snappy.
	go func(ra string) {
		ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
		defer cancel()
		if _, err := s.worker.SyncUser(ctx, ra, true); err != nil {
			log.Printf("initial snapshot seed for %s: %v", ra, err)
		}
	}(info.RA)

	writeJSON(w, http.StatusOK, map[string]any{
		"token":     token,
		"expiresAt": exp.Format(time.RFC3339),
		"student":   info,
		"nacOpen":   nacOpen,
	})
}

func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	claims := currentClaims(r)
	if err := s.store.IncrementTokenVersion(claims.RA); err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao encerrar sessão")
		return
	}
	s.portal.EvictSession(claims.RA)
	if s.d2l != nil {
		s.d2l.EvictSession(claims.RA)
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleMe(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	u, err := s.store.GetUser(claims.RA)
	if err != nil || u == nil {
		writeError(w, http.StatusUnauthorized, "usuário não encontrado")
		return
	}
	writeJSON(w, http.StatusOK, portal.StudentInfo{
		RA:     u.RA,
		Name:   u.Name,
		Campus: u.Campus,
		Perfil: u.Perfil,
	})
}
