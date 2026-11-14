package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"io/fs"
	"log"
	"mime"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"unicep-portal/internal/config"
	"unicep-portal/internal/crypto"
	"unicep-portal/internal/poller"
	"unicep-portal/internal/portal"
	"unicep-portal/internal/storage"
)

type ctxKey string

const claimsCtxKey ctxKey = "claims"

// Server exposes the BFF JSON API and optional static Compose Web assets.
type Server struct {
	cfg     config.Config
	store   *storage.Store
	crypto  *crypto.Service
	portal  *portal.Manager
	worker  *poller.Worker
	limiter *loginLimiter
}

// NewServer constructs the HTTP API router and handlers.
func NewServer(cfg config.Config, store *storage.Store, cry *crypto.Service, pm *portal.Manager, worker *poller.Worker) http.Handler {
	s := &Server{
		cfg:     cfg,
		store:   store,
		crypto:  cry,
		portal:  pm,
		worker:  worker,
		limiter: newLoginLimiter(15, 5*time.Minute),
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/health", s.handleHealth)
	mux.HandleFunc("POST /api/auth/login", s.handleLogin)

	// Authenticated routes
	mux.Handle("GET /api/auth/me", s.requireAuth(http.HandlerFunc(s.handleMe)))
	mux.Handle("GET /api/dashboard", s.requireAuth(http.HandlerFunc(s.handleDashboard)))
	mux.Handle("GET /api/periods", s.requireAuth(http.HandlerFunc(s.handlePeriods)))
	mux.Handle("GET /api/grades", s.requireAuth(http.HandlerFunc(s.handleGrades)))
	mux.Handle("GET /api/report", s.requireAuth(http.HandlerFunc(s.handleReport)))
	mux.Handle("GET /api/schedule", s.requireAuth(http.HandlerFunc(s.handleSchedule)))
	mux.Handle("GET /api/curriculum", s.requireAuth(http.HandlerFunc(s.handleCurriculum)))
	mux.Handle("GET /api/finance", s.requireAuth(http.HandlerFunc(s.handleFinance)))
	mux.Handle("GET /api/services", s.requireAuth(http.HandlerFunc(s.handleServices)))
	mux.Handle("POST /api/services/ava-sso", s.requireAuth(http.HandlerFunc(s.handleAvaSSO)))
	mux.Handle("GET /api/proxy", s.requireAuth(http.HandlerFunc(s.handleProxy)))
	mux.Handle("GET /api/notifications", s.requireAuth(http.HandlerFunc(s.handleListNotifications)))
	mux.Handle("POST /api/notifications/read", s.requireAuth(http.HandlerFunc(s.handleMarkNotificationsRead)))
	mux.Handle("POST /api/notifications/sync", s.requireAuth(http.HandlerFunc(s.handleSyncNow)))
	mux.Handle("POST /api/notifications/test", s.requireAuth(http.HandlerFunc(s.handleTestNotification)))

	if cfg.WebDir != "" {
		if info, err := os.Stat(cfg.WebDir); err == nil && info.IsDir() {
			mux.Handle("GET /", s.staticSPAHandler(cfg.WebDir))
		}
	}

	return s.corsMiddleware(mux)
}

func (s *Server) corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		if origin != "" && (origin == s.cfg.CORSOrigin || strings.HasPrefix(origin, "http://localhost:") || strings.HasPrefix(origin, "http://127.0.0.1:")) {
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Vary", "Origin")
			w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type")
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		}
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (s *Server) requireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token := extractBearer(r)
		if token == "" {
			if c, err := r.Cookie("portal_token"); err == nil {
				token = c.Value
			}
		}
		if token == "" {
			writeError(w, http.StatusUnauthorized, "token ausente")
			return
		}
		claims, err := s.crypto.VerifyToken(token)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "sessão expirada")
			return
		}
		ctx := context.WithValue(r.Context(), claimsCtxKey, claims)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func extractBearer(r *http.Request) string {
	auth := strings.TrimSpace(r.Header.Get("Authorization"))
	if len(auth) > 7 && strings.EqualFold(auth[:7], "Bearer ") {
		return strings.TrimSpace(auth[7:])
	}
	if q := strings.TrimSpace(r.URL.Query().Get("token")); q != "" && r.URL.Path == "/api/proxy" {
		return q
	}
	return ""
}

func currentClaims(r *http.Request) *crypto.Claims {
	c, _ := r.Context().Value(claimsCtxKey).(*crypto.Claims)
	return c
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"status": "ok",
		"time":   time.Now().UTC().Format(time.RFC3339),
	})
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	ip := clientIP(r)
	if !s.limiter.Allow(ip) {
		writeError(w, http.StatusTooManyRequests, "muitas tentativas de login; aguarde alguns minutos")
		return
	}

	var req struct {
		RA       string `json:"ra"`
		Password string `json:"password"`
		Perfil   string `json:"perfil"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 8192)).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "JSON inválido")
		return
	}
	req.RA = strings.TrimSpace(req.RA)
	if req.Perfil == "" {
		req.Perfil = "alunograd"
	}
	if req.RA == "" || req.Password == "" {
		writeError(w, http.StatusBadRequest, "RA e senha são obrigatórios")
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

	token, exp, err := s.crypto.IssueToken(info.RA, info.Perfil)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao gerar token")
		return
	}

	// Seed snapshot asynchronously so initial login stays snappy.
	go func(ra string) {
		ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
		defer cancel()
		if _, err := s.worker.SyncUser(ctx, ra, true); err != nil {
			log.Printf("initial snapshot seed for %s: %v", ra, err)
		}
	}(info.RA)

	http.SetCookie(w, &http.Cookie{
		Name:     "portal_token",
		Value:    token,
		Path:     "/",
		Expires:  exp,
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
		Secure:   strings.HasPrefix(s.cfg.CORSOrigin, "https://") && r.Header.Get("X-Forwarded-Proto") == "https",
	})

	writeJSON(w, http.StatusOK, map[string]any{
		"token":     token,
		"expiresAt": exp.Format(time.RFC3339),
		"student":   info,
		"nacOpen":   nacOpen,
	})
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

func (s *Server) handleDashboard(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	homeHTML, info, nacOpen, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/ambiente_estudante.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	summary, _ := portal.ParseDashboard(homeHTML, nacOpen)

	gradesSel, _, _, _ := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/consulta_notas_primeira.asp")
	gradePeriods, _ := portal.ParsePeriodOptions(gradesSel, "/ambiente_aluno/relatorios/consulta_notas.asp?matr=")

	reportSel, _, _, _ := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/relatorios/boletim_geral.asp")
	reportPeriods, _ := portal.ParsePeriodOptions(reportSel, "/ambiente_aluno/relatorios/boletim_detalhado.asp")

	var currentGrades []portal.PartialGradeItem
	if len(gradePeriods) > 0 {
		if gHTML, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, gradePeriods[0].Query); err == nil {
			currentGrades, _ = portal.ParseGrades(gHTML)
		}
	}

	var currentReport portal.ReportCardResponse
	if len(reportPeriods) > 0 {
		if rHTML, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, reportPeriods[0].Query); err == nil {
			currentReport, _ = portal.ParseReportCard(rHTML, reportPeriods[0].ID)
		}
	}

	unread, _ := s.store.ListNotifications(claims.RA, true, 50)

	writeJSON(w, http.StatusOK, map[string]any{
		"student":             info,
		"summary":             summary,
		"gradePeriods":        gradePeriods,
		"reportPeriods":       reportPeriods,
		"currentGrades":       currentGrades,
		"currentReport":       currentReport,
		"unreadNotifications": unread,
	})
}

func (s *Server) handlePeriods(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	gradesSel, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/consulta_notas_primeira.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	gradePeriods, _ := portal.ParsePeriodOptions(gradesSel, "/ambiente_aluno/relatorios/consulta_notas.asp?matr=")

	reportSel, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/relatorios/boletim_geral.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	reportPeriods, _ := portal.ParsePeriodOptions(reportSel, "/ambiente_aluno/relatorios/boletim_detalhado.asp")

	writeJSON(w, http.StatusOK, map[string]any{
		"gradePeriods":  gradePeriods,
		"reportPeriods": reportPeriods,
	})
}

func (s *Server) handleGrades(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	period := strings.TrimSpace(r.URL.Query().Get("period"))
	if period == "" {
		gradesSel, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/consulta_notas_primeira.asp")
		if err != nil {
			s.writePortalErr(w, err)
			return
		}
		periods, _ := portal.ParsePeriodOptions(gradesSel, "")
		if len(periods) == 0 {
			writeJSON(w, http.StatusOK, map[string]any{"period": "", "items": []portal.PartialGradeItem{}})
			return
		}
		period = periods[0].ID
	}

	target := "/ambiente_aluno/relatorios/consulta_notas.asp?matr=" + url.QueryEscape(period)
	html, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, target)
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	items, err := portal.ParseGrades(html)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao interpretar notas")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"period": period,
		"items":  items,
	})
}

func (s *Server) handleReport(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	periodQuery := strings.TrimSpace(r.URL.Query().Get("period"))
	if periodQuery == "" {
		reportSel, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/relatorios/boletim_geral.asp")
		if err != nil {
			s.writePortalErr(w, err)
			return
		}
		periods, _ := portal.ParsePeriodOptions(reportSel, "")
		if len(periods) == 0 {
			writeJSON(w, http.StatusOK, portal.ReportCardResponse{Items: []portal.ReportCardItem{}})
			return
		}
		periodQuery = periods[0].ID
	}
	if !strings.HasPrefix(periodQuery, "?") {
		periodQuery = "?" + periodQuery
	}
	target := "/ambiente_aluno/relatorios/boletim_detalhado.asp" + periodQuery
	html, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, target)
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	resp, err := portal.ParseReportCard(html, periodQuery)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao interpretar boletim")
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleSchedule(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	html, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/relatorios/quadro_horarios.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	sched, err := portal.ParseSchedule(html)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao interpretar horários")
		return
	}
	writeJSON(w, http.StatusOK, sched)
}

func (s *Server) handleCurriculum(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	transcriptHTML, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/modulo_matricula/relatorios/ficha_individual_todas_semDuplicidade.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	transcript, _ := portal.ParseTranscript(transcriptHTML)

	gradeID := strings.TrimSpace(r.URL.Query().Get("gradeId"))
	if gradeID == "" {
		if homeHTML, _, nacOpen, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/ambiente_estudante.asp"); err == nil {
			if summary, err := portal.ParseDashboard(homeHTML, nacOpen); err == nil {
				gradeID = summary.GradeID
			}
		}
	}
	var matrix []portal.CurriculumItem
	if gradeID != "" {
		if mHTML, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/modulo_matricula/relatorios/relatorio_matriz_curricular_apartir_2025_unicep.asp?grade="+url.QueryEscape(gradeID)); err == nil {
			matrix, _ = portal.ParseCurriculum(mHTML)
		}
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"transcript": transcript,
		"matrix":     matrix,
		"gradeId":    gradeID,
	})
}

func (s *Server) handleFinance(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	html, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/relatorios/listaDebito.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	items, err := portal.ParseTuition(html)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao interpretar mensalidades")
		return
	}
	links := []portal.LinkItem{
		{Title: "Declaração de Pagamento (IR)", Path: "/financeiro/relatorios/declaracao_imposto_renda.asp", Category: "Financeiro", Proxy: true},
		{Title: "Pagamento de Mensalidade (Portal)", Path: s.cfg.PortalBaseURL + "/ambiente_aluno/pagamentoMensalidade.asp", Category: "Financeiro", Proxy: false},
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"installments": items,
		"links":        links,
	})
}

func (s *Server) handleServices(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	docsHTML, _, _, err := s.portal.FetchHTML(r.Context(), claims.RA, "/ambiente_aluno/documentos_institucionais.asp")
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	docs, _ := portal.ParseInstitutionalDocs(docsHTML)

	shortcuts := []portal.LinkItem{
		{Title: "Localização de Salas (PDF)", Path: "/ambiente_aluno/Localizacao_salas_v1.pdf", Category: "Campus", Proxy: true},
		{Title: "Impressão de Documentos On-line", Path: s.cfg.PortalBaseURL + "/modulo_matricula/relatorios/docs_online/gerarDocs.php", Category: "Serviços", Proxy: false},
		{Title: "Requerimentos Diversos", Path: s.cfg.PortalBaseURL + "/solicitacaoDocumentos/solicitacaoDocumento.asp", Category: "Serviços", Proxy: false},
		{Title: "Atividades Complementares", Path: s.cfg.PortalBaseURL + "/ambiente_coordenador_novo/controle_atividades_complementares/aluno.asp", Category: "Serviços", Proxy: false},
		{Title: "Banco de Empregos", Path: s.cfg.PortalBaseURL + "/ambiente_aluno/vagas.asp", Category: "Carreira", Proxy: false},
		{Title: "Avaliação Institucional (NAC)", Path: s.cfg.PortalBaseURL + "/MVCGraduacao/View/avaliacaoInstitucionalDis.php", Category: "Institucional", Proxy: false},
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"shortcuts":         shortcuts,
		"institutionalDocs": docs,
	})
}

func (s *Server) handleAvaSSO(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	raw, _, _, _, err := s.portal.FetchRaw(r.Context(), claims.RA, "/d2l_login.php?ra="+url.QueryEscape(claims.RA)+"&nocache="+url.QueryEscape(time.Now().Format("150405")))
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	target := strings.TrimSpace(string(raw))
	if !strings.HasPrefix(target, "http") {
		writeError(w, http.StatusBadGateway, "não foi possível gerar link SSO do UnicepVirtual")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"url": target})
}

func (s *Server) handleProxy(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	relPath := strings.TrimSpace(r.URL.Query().Get("path"))
	if !isAllowedProxyPath(relPath) {
		writeError(w, http.StatusForbidden, "caminho não permitido no proxy")
		return
	}
	raw, ct, _, _, err := s.portal.FetchRaw(r.Context(), claims.RA, relPath)
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	if ct != "" {
		w.Header().Set("Content-Type", ct)
	}
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(raw)
}

func isAllowedProxyPath(p string) bool {
	if p == "" || strings.Contains(p, "..") || strings.Contains(p, "://") {
		return false
	}
	allowed := []string{
		"/ambiente_aluno/",
		"/modulo_matricula/",
		"/financeiro/",
		"/symLinkFTP/",
	}
	for _, prefix := range allowed {
		if strings.HasPrefix(p, prefix) {
			return true
		}
	}
	return false
}

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
	_ = json.NewDecoder(http.MaxBytesReader(w, r.Body, 8192)).Decode(&req)
	if err := s.store.MarkNotificationsRead(claims.RA, req.IDs); err != nil {
		writeError(w, http.StatusInternalServerError, "erro ao atualizar notificações")
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleSyncNow(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
	created, err := s.worker.SyncUser(r.Context(), claims.RA, false)
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]int{"created": created})
}

func (s *Server) handleTestNotification(w http.ResponseWriter, r *http.Request) {
	claims := currentClaims(r)
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

func (s *Server) writePortalErr(w http.ResponseWriter, err error) {
	if errors.Is(err, portal.ErrInvalidCredentials) || errors.Is(err, portal.ErrSessionExpired) {
		writeError(w, http.StatusUnauthorized, "sessão expirada no portal da UNICEP; faça login novamente")
		return
	}
	writeError(w, http.StatusBadGateway, "erro ao consultar o portal da UNICEP")
}

func (s *Server) staticSPAHandler(dir string) http.Handler {
	fileServer := http.FileServer(http.Dir(dir))
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/api/") {
			writeError(w, http.StatusNotFound, "endpoint não encontrado")
			return
		}
		clean := filepath.Clean(strings.TrimPrefix(r.URL.Path, "/"))
		full := filepath.Join(dir, clean)
		if info, err := os.Stat(full); err == nil && !info.IsDir() {
			if ext := filepath.Ext(clean); ext != "" {
				if ct := mime.TypeByExtension(ext); ct != "" {
					w.Header().Set("Content-Type", ct)
				}
				if ext == ".wasm" {
					w.Header().Set("Content-Type", "application/wasm")
					w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
				}
			}
			fileServer.ServeHTTP(w, r)
			return
		}
		if _, err := os.Stat(filepath.Join(dir, "index.html")); err == nil && !errors.Is(err, fs.ErrNotExist) {
			http.ServeFile(w, r, filepath.Join(dir, "index.html"))
			return
		}
		writeError(w, http.StatusNotFound, "frontend não encontrado")
	})
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

func clientIP(r *http.Request) string {
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		return strings.TrimSpace(strings.Split(xff, ",")[0])
	}
	if xri := r.Header.Get("X-Real-IP"); xri != "" {
		return strings.TrimSpace(xri)
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err == nil {
		return host
	}
	return r.RemoteAddr
}

type loginLimiter struct {
	mu      sync.Mutex
	limit   int
	window  time.Duration
	entries map[string][]time.Time
}

func newLoginLimiter(limit int, window time.Duration) *loginLimiter {
	return &loginLimiter{
		limit:   limit,
		window:  window,
		entries: make(map[string][]time.Time),
	}
}

func (l *loginLimiter) Allow(key string) bool {
	now := time.Now()
	cutoff := now.Add(-l.window)

	l.mu.Lock()
	defer l.mu.Unlock()

	list := l.entries[key]
	kept := list[:0]
	for _, ts := range list {
		if ts.After(cutoff) {
			kept = append(kept, ts)
		}
	}
	if len(kept) >= l.limit {
		l.entries[key] = kept
		return false
	}
	kept = append(kept, now)
	l.entries[key] = kept
	return true
}
