package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"io/fs"
	"log"
	"mime"
	"net"
	"net/http"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"unicep-portal/internal/config"
	"unicep-portal/internal/crypto"
	"unicep-portal/internal/d2l"
	"unicep-portal/internal/poller"
	"unicep-portal/internal/portal"
	"unicep-portal/internal/storage"
)

type ctxKey string

const (
	claimsCtxKey      ctxKey = "claims"
	maxLimiterEntries        = 4096
)

var allowedProfiles = map[string]struct{}{
	"alunograd":     {},
	"alunopos":      {},
	"coordenador":   {},
	"diretor":       {},
	"egresso":       {},
	"professorgrad": {},
}

// Server exposes the hardened BFF JSON API and optional static Compose Web assets.
type Server struct {
	cfg            config.Config
	store          *storage.Store
	crypto         *crypto.Service
	portal         *portal.Manager
	d2l            *d2l.Manager
	worker         *poller.Worker
	loginIPLim     *rateLimiter
	loginRALim     *rateLimiter
	actionRALim    *rateLimiter
	trustedProxies []*net.IPNet
}

// NewServer constructs the HTTP API router and security middleware chain.
func NewServer(cfg config.Config, store *storage.Store, cry *crypto.Service, pm *portal.Manager, worker *poller.Worker, d2lMgr *d2l.Manager) http.Handler {
	s := &Server{
		cfg:            cfg,
		store:          store,
		crypto:         cry,
		portal:         pm,
		d2l:            d2lMgr,
		worker:         worker,
		loginIPLim:     newRateLimiter(10, 5*time.Minute),
		loginRALim:     newRateLimiter(6, 5*time.Minute),
		actionRALim:    newRateLimiter(20, 1*time.Minute),
		trustedProxies: parseCIDRs(cfg.TrustedProxyCIDRs),
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/health", s.handleHealth)
	mux.HandleFunc("POST /api/auth/login", s.handleLogin)
	mux.HandleFunc("GET /api/proxy", s.handleProxy)
	mux.HandleFunc("GET /api/d2l/file", s.handleD2LFile)

	// Authenticated routes (strictly Bearer JWT + active token_version)
	mux.Handle("POST /api/auth/logout", s.requireAuth(http.HandlerFunc(s.handleLogout)))
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
	mux.Handle("POST /api/proxy/ticket", s.requireAuth(http.HandlerFunc(s.handleProxyTicket)))
	mux.Handle("GET /api/d2l/overview", s.requireAuth(http.HandlerFunc(s.handleD2LOverview)))
	mux.Handle("GET /api/d2l/courses/{ou}", s.requireAuth(http.HandlerFunc(s.handleD2LCourseDetail)))
	mux.Handle("POST /api/d2l/ticket", s.requireAuth(http.HandlerFunc(s.handleD2LTicket)))
	mux.Handle("GET /api/notifications", s.requireAuth(http.HandlerFunc(s.handleListNotifications)))
	mux.Handle("POST /api/notifications/read", s.requireAuth(http.HandlerFunc(s.handleMarkNotificationsRead)))
	mux.Handle("POST /api/notifications/sync", s.requireAuth(http.HandlerFunc(s.handleSyncNow)))
	mux.Handle("POST /api/notifications/test", s.requireAuth(http.HandlerFunc(s.handleTestNotification)))

	if cfg.WebDir != "" {
		if info, err := os.Stat(cfg.WebDir); err == nil && info.IsDir() {
			mux.Handle("GET /", s.staticSPAHandler(cfg.WebDir))
		}
	}

	return s.securityHeadersMiddleware(s.corsMiddleware(mux))
}

func (s *Server) securityHeadersMiddleware(next http.Handler) http.Handler {
	const appCSP = "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' data: blob:; worker-src 'self' blob:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("X-Frame-Options", "DENY")
		h.Set("Referrer-Policy", "no-referrer")
		h.Set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()")
		h.Set("Cross-Origin-Opener-Policy", "same-origin")
		if r.URL.Path != "/api/proxy" && r.URL.Path != "/api/d2l/file" {
			h.Set("Content-Security-Policy", appCSP)
		}
		next.ServeHTTP(w, r)
	})
}

func (s *Server) corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		if origin != "" && s.isAllowedOrigin(origin) {
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

func (s *Server) isAllowedOrigin(origin string) bool {
	if s.cfg.CORSOrigin != "" && origin == s.cfg.CORSOrigin {
		return true
	}
	if s.cfg.AllowDevCORS && (strings.HasPrefix(origin, "http://localhost:") || strings.HasPrefix(origin, "http://127.0.0.1:")) {
		return true
	}
	return false
}

func (s *Server) requireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token := extractBearer(r)
		if token == "" {
			writeError(w, http.StatusUnauthorized, "token ausente")
			return
		}
		claims, err := s.crypto.VerifyToken(token)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "sessão expirada")
			return
		}
		tv, err := s.store.GetTokenVersion(claims.RA)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "erro ao validar sessão")
			return
		}
		if tv == 0 || claims.TokenVersion != tv {
			writeError(w, http.StatusUnauthorized, "sessão revogada ou expirada")
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
	return ""
}

func currentClaims(r *http.Request) *crypto.Claims {
	c, _ := r.Context().Value(claimsCtxKey).(*crypto.Claims)
	return c
}

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
		ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
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
	} else if !isSafeParam(period, 128) {
		writeError(w, http.StatusBadRequest, "período inválido")
		return
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
	if !isSafeQueryString(periodQuery[1:], 1024) {
		writeError(w, http.StatusBadRequest, "parâmetro de boletim inválido")
		return
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
	if gradeID != "" && !isSafeParam(gradeID, 64) {
		writeError(w, http.StatusBadRequest, "gradeId inválido")
		return
	}
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
	if !s.actionRALim.Allow("ava:" + claims.RA) {
		writeError(w, http.StatusTooManyRequests, "muitas solicitações; aguarde alguns segundos")
		return
	}
	raw, _, _, _, err := s.portal.FetchRaw(r.Context(), claims.RA, "/d2l_login.php?ra="+url.QueryEscape(claims.RA)+"&nocache="+url.QueryEscape(time.Now().Format("150405")))
	if err != nil {
		s.writePortalErr(w, err)
		return
	}
	target := strings.TrimSpace(string(raw))
	if !isSafeExternalHTTPSURL(target) {
		writeError(w, http.StatusBadGateway, "não foi possível gerar link SSO seguro do UnicepVirtual")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"url": target})
}

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
	w.Header().Set("Cache-Control", "private, no-store")
	w.Header().Set("Content-Type", safeCT)
	if attach || forceAttachment {
		if safeName := sanitizeDownloadFilename(requestedName); safeName != "" {
			if formatted := mime.FormatMediaType("attachment", map[string]string{"filename": safeName}); formatted != "" {
				w.Header().Set("Content-Disposition", formatted)
			} else {
				w.Header().Set("Content-Disposition", "attachment")
			}
		} else if contentDisposition != "" {
			w.Header().Set("Content-Disposition", contentDisposition)
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
	created, err := s.worker.SyncUser(r.Context(), claims.RA, false)
	if err != nil {
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

func (s *Server) writePortalErr(w http.ResponseWriter, err error) {
	if errors.Is(err, portal.ErrInvalidCredentials) || errors.Is(err, portal.ErrSessionExpired) {
		writeError(w, http.StatusUnauthorized, "sessão expirada no portal da UNICEP; faça login novamente")
		return
	}
	writeError(w, http.StatusBadGateway, "erro ao consultar o portal da UNICEP")
}

func (s *Server) handleD2LOverview(w http.ResponseWriter, r *http.Request) {
	if s.d2l == nil {
		writeError(w, http.StatusServiceUnavailable, "integração com a Sala Virtual indisponível")
		return
	}
	claims := currentClaims(r)
	ov, err := s.d2l.GetOverview(r.Context(), claims.RA)
	if err != nil {
		s.writeD2LErr(w, err)
		return
	}
	writeJSON(w, http.StatusOK, ov)
}

func (s *Server) handleD2LCourseDetail(w http.ResponseWriter, r *http.Request) {
	if s.d2l == nil {
		writeError(w, http.StatusServiceUnavailable, "integração com a Sala Virtual indisponível")
		return
	}
	ouRaw := strings.TrimSpace(r.PathValue("ou"))
	ou, err := strconv.ParseInt(ouRaw, 10, 64)
	if err != nil || ou <= 0 {
		writeError(w, http.StatusBadRequest, "identificador de disciplina inválido")
		return
	}
	claims := currentClaims(r)
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
		writeError(w, http.StatusServiceUnavailable, "integração com a Sala Virtual indisponível")
		return
	}
	claims := currentClaims(r)
	if !s.actionRALim.Allow("d2lticket:" + claims.RA) {
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

func (s *Server) handleD2LFile(w http.ResponseWriter, r *http.Request) {
	if s.d2l == nil {
		writeError(w, http.StatusServiceUnavailable, "integração com a Sala Virtual indisponível")
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
	if errors.Is(err, d2l.ErrInvalidCredentials) || errors.Is(err, d2l.ErrSessionExpired) {
		writeError(w, http.StatusBadGateway, "não foi possível autenticar na Sala Virtual (verifique suas credenciais do UnicepVirtual)")
		return
	}
	writeError(w, http.StatusBadGateway, "erro ao consultar a Sala Virtual (UnicepVirtual)")
}

func (s *Server) staticSPAHandler(dir string) http.Handler {
	absDir, err := filepath.Abs(dir)
	if err != nil {
		absDir = dir
	}
	indexPath := filepath.Join(absDir, "index.html")
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/api/") {
			writeError(w, http.StatusNotFound, "endpoint não encontrado")
			return
		}
		if hasHiddenSegment(r.URL.Path) {
			writeError(w, http.StatusNotFound, "não encontrado")
			return
		}

		clean := filepath.Clean(strings.TrimPrefix(r.URL.Path, "/"))
		if clean == "." || clean == "" {
			clean = "index.html"
		}
		full, err := filepath.Abs(filepath.Join(absDir, clean))
		if err != nil || !isWithinDir(absDir, full) {
			writeError(w, http.StatusForbidden, "caminho inválido")
			return
		}

		if info, err := os.Stat(full); err == nil && !info.IsDir() {
			if ext := filepath.Ext(clean); ext != "" {
				if ct := mime.TypeByExtension(ext); ct != "" {
					w.Header().Set("Content-Type", ct)
				}
				switch ext {
				case ".wasm":
					w.Header().Set("Content-Type", "application/wasm")
					w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
				case ".html", ".js":
					w.Header().Set("Cache-Control", "no-cache, must-revalidate")
				}
			}
			http.ServeFile(w, r, full)
			return
		}
		if filepath.Ext(clean) != "" {
			writeError(w, http.StatusNotFound, "arquivo não encontrado")
			return
		}
		if _, err := os.Stat(indexPath); err == nil && !errors.Is(err, fs.ErrNotExist) {
			w.Header().Set("Cache-Control", "no-cache, must-revalidate")
			http.ServeFile(w, r, indexPath)
			return
		}
		writeError(w, http.StatusNotFound, "frontend não encontrado")
	})
}

func decodeStrictJSON(w http.ResponseWriter, r *http.Request, dst any, maxBytes int64) error {
	if r.Body == nil {
		return io.EOF
	}
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxBytes))
	if err := dec.Decode(dst); err != nil {
		return err
	}
	var trailing any
	if err := dec.Decode(&trailing); err != io.EOF {
		return errors.New("unexpected trailing JSON data")
	}
	return nil
}

func isSafeRA(ra string) bool {
	if len(ra) == 0 || len(ra) > 20 {
		return false
	}
	for i := 0; i < len(ra); i++ {
		c := ra[i]
		if !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-' || c == '_') {
			return false
		}
	}
	return true
}

func isSafeParam(v string, maxLen int) bool {
	if len(v) == 0 || len(v) > maxLen || hasControlOrBackslash(v) {
		return false
	}
	lower := strings.ToLower(v)
	return !strings.Contains(lower, "..") && !strings.Contains(lower, "://") && !strings.ContainsAny(v, "#?")
}

func isSafeQueryString(q string, maxLen int) bool {
	if len(q) == 0 || len(q) > maxLen || hasControlOrBackslash(q) {
		return false
	}
	lower := strings.ToLower(q)
	if strings.Contains(lower, "..") || strings.Contains(lower, "://") || strings.Contains(q, "#") {
		return false
	}
	_, err := url.ParseQuery(q)
	return err == nil
}

func hasControlOrBackslash(s string) bool {
	for i := 0; i < len(s); i++ {
		c := s[i]
		if c < 0x20 || c == 0x7f || c == '\\' {
			return true
		}
	}
	return false
}

func isSafeExternalHTTPSURL(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || !strings.EqualFold(u.Scheme, "https") {
		return false
	}
	host := u.Hostname()
	if host == "" || strings.EqualFold(host, "localhost") {
		return false
	}
	if ip := net.ParseIP(host); ip != nil {
		return !(ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() || ip.IsUnspecified())
	}
	return true
}

func hasHiddenSegment(urlPath string) bool {
	for _, seg := range strings.Split(urlPath, "/") {
		if strings.HasPrefix(seg, ".") && seg != "." && seg != ".." {
			return true
		}
	}
	return false
}

func isWithinDir(base, target string) bool {
	rel, err := filepath.Rel(base, target)
	if err != nil {
		return false
	}
	return rel == "." || (rel != ".." && !strings.HasPrefix(rel, ".."+string(filepath.Separator)))
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

func parseCIDRs(raw []string) []*net.IPNet {
	var nets []*net.IPNet
	for _, item := range raw {
		_, ipNet, err := net.ParseCIDR(item)
		if err == nil {
			nets = append(nets, ipNet)
			continue
		}
		if ip := net.ParseIP(item); ip != nil {
			maskLen := 32
			if ip.To4() == nil {
				maskLen = 128
			}
			nets = append(nets, &net.IPNet{IP: ip, Mask: net.CIDRMask(maskLen, maskLen)})
		}
	}
	return nets
}

func (s *Server) isTrustedProxy(ip net.IP) bool {
	if ip == nil {
		return false
	}
	for _, n := range s.trustedProxies {
		if n.Contains(ip) {
			return true
		}
	}
	return false
}

func (s *Server) clientIP(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	remoteIP := net.ParseIP(strings.TrimSpace(host))
	if !s.isTrustedProxy(remoteIP) {
		if remoteIP != nil {
			return remoteIP.String()
		}
		return host
	}

	if cfIP := net.ParseIP(strings.TrimSpace(r.Header.Get("CF-Connecting-IP"))); cfIP != nil {
		return cfIP.String()
	}
	if xri := net.ParseIP(strings.TrimSpace(r.Header.Get("X-Real-IP"))); xri != nil {
		return xri.String()
	}
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		for i := len(parts) - 1; i >= 0; i-- {
			if candidate := net.ParseIP(strings.TrimSpace(parts[i])); candidate != nil && !s.isTrustedProxy(candidate) {
				return candidate.String()
			}
		}
	}
	if remoteIP != nil {
		return remoteIP.String()
	}
	return host
}

type rateLimiter struct {
	mu      sync.Mutex
	limit   int
	window  time.Duration
	entries map[string][]time.Time
}

func newRateLimiter(limit int, window time.Duration) *rateLimiter {
	return &rateLimiter{
		limit:   limit,
		window:  window,
		entries: make(map[string][]time.Time),
	}
}

func (l *rateLimiter) Allow(key string) bool {
	now := time.Now()
	cutoff := now.Add(-l.window)

	l.mu.Lock()
	defer l.mu.Unlock()

	if len(l.entries) >= maxLimiterEntries {
		for k, tsList := range l.entries {
			if len(tsList) == 0 || !tsList[len(tsList)-1].After(cutoff) {
				delete(l.entries, k)
			}
		}
		if len(l.entries) >= maxLimiterEntries {
			for k := range l.entries {
				delete(l.entries, k)
				if len(l.entries) < maxLimiterEntries {
					break
				}
			}
		}
	}

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
