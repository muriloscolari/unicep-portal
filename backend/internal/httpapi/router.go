package httpapi

import (
	"context"
	"net"
	"net/http"
	"os"
	"strings"
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
	d2lOverviewLim *rateLimiter
	d2lDetailLim   *rateLimiter
	d2lTicketLim   *rateLimiter
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
		d2lOverviewLim: newRateLimiter(12, 1*time.Minute),
		d2lDetailLim:   newRateLimiter(30, 1*time.Minute),
		d2lTicketLim:   newRateLimiter(60, 1*time.Minute),
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
	mux.Handle("GET /api/d2l/settings", s.requireAuth(http.HandlerFunc(s.handleGetD2LSettings)))
	mux.Handle("PATCH /api/d2l/settings", s.requireAuth(http.HandlerFunc(s.handlePatchD2LSettings)))
	mux.Handle("GET /api/notifications", s.requireAuth(http.HandlerFunc(s.handleListNotifications)))
	mux.Handle("POST /api/notifications/read", s.requireAuth(http.HandlerFunc(s.handleMarkNotificationsRead)))
	mux.Handle("POST /api/notifications/sync", s.requireAuth(http.HandlerFunc(s.handleSyncNow)))
	mux.Handle("POST /api/notifications/test", s.requireAuth(http.HandlerFunc(s.handleTestNotification)))
	mux.Handle("GET /api/calendar/events", s.requireAuth(http.HandlerFunc(s.handleListCalendarEvents)))
	mux.Handle("POST /api/calendar/events", s.requireAuth(http.HandlerFunc(s.handleCreateCalendarEvent)))
	mux.Handle("PUT /api/calendar/events/{id}", s.requireAuth(http.HandlerFunc(s.handleUpdateCalendarEvent)))
	mux.Handle("DELETE /api/calendar/events/{id}", s.requireAuth(http.HandlerFunc(s.handleDeleteCalendarEvent)))

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
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
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
