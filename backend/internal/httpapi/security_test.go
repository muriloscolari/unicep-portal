package httpapi

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"unicep-portal/internal/config"
	"unicep-portal/internal/crypto"
	"unicep-portal/internal/d2l"
	"unicep-portal/internal/poller"
	"unicep-portal/internal/portal"
	"unicep-portal/internal/storage"
)

func newTestServer(t *testing.T, portalURL string, allowDevCORS bool) (http.Handler, *storage.Store, *crypto.Service) {
	t.Helper()
	store, err := storage.Open(t.TempDir())
	if err != nil {
		t.Fatalf("open store: %v", err)
	}
	t.Cleanup(func() { _ = store.Close() })

	secret := "0123456789abcdef0123456789abcdef0123456789abcdef"
	cry := crypto.NewService(secret)
	pm := portal.NewManager(portalURL, func(ra string) (string, string, error) {
		u, err := store.GetUser(ra)
		if err != nil || u == nil {
			return "", "", portal.ErrSessionExpired
		}
		pass, err := cry.Decrypt(u.EncryptedPassword)
		return pass, u.Perfil, err
	})
	worker := poller.NewWorker(store, cry, pm, nil, 15*time.Minute, true)

	webDir := t.TempDir()
	_ = os.WriteFile(filepath.Join(webDir, "index.html"), []byte("<html>ok</html>"), 0o644)
	_ = os.WriteFile(filepath.Join(webDir, ".env"), []byte("SECRET=leak"), 0o644)

	cfg := config.Config{
		Port:              "47832",
		AppSecret:         secret,
		CORSOrigin:        "https://unicep.muriloscolari.me",
		AllowDevCORS:      allowDevCORS,
		WebDir:            webDir,
		PortalBaseURL:     portalURL,
		TrustedProxyCIDRs: []string{"127.0.0.1/8", "::1/128"},
	}
	return NewServer(cfg, store, cry, pm, worker, nil), store, cry
}

func newTestServerWithD2L(t *testing.T, portalURL, d2lURL string) (http.Handler, *storage.Store, *crypto.Service) {
	t.Helper()
	store, err := storage.Open(t.TempDir())
	if err != nil {
		t.Fatalf("open store: %v", err)
	}
	t.Cleanup(func() { _ = store.Close() })

	secret := "0123456789abcdef0123456789abcdef0123456789abcdef"
	cry := crypto.NewService(secret)
	pm := portal.NewManager(portalURL, func(ra string) (string, string, error) {
		u, err := store.GetUser(ra)
		if err != nil || u == nil {
			return "", "", portal.ErrSessionExpired
		}
		pass, err := cry.Decrypt(u.EncryptedPassword)
		return pass, u.Perfil, err
	})
	dm := d2l.NewManager(d2lURL, func(ra string) (string, error) {
		u, err := store.GetUser(ra)
		if err != nil || u == nil {
			return "", d2l.ErrSessionExpired
		}
		return cry.Decrypt(u.EncryptedPassword)
	})
	worker := poller.NewWorker(store, cry, pm, nil, 15*time.Minute, true)
	cfg := config.Config{
		Port:              "47832",
		AppSecret:         secret,
		CORSOrigin:        "https://unicep.muriloscolari.me",
		PortalBaseURL:     portalURL,
		D2LBaseURL:        d2lURL,
		TrustedProxyCIDRs: []string{"127.0.0.1/8", "::1/128"},
	}
	return NewServer(cfg, store, cry, pm, worker, dm), store, cry
}

func TestJWTRevocationOnLogout(t *testing.T) {
	srv, store, cry := newTestServer(t, "https://cdseducacional.com.br", false)
	encPass, _ := cry.Encrypt("pass123")
	if err := store.UpsertUser(storage.UserRecord{
		RA:                "1234567",
		Perfil:            "alunograd",
		Name:              "Aluno Exemplo",
		EncryptedPassword: encPass,
		PollingEnabled:    true,
	}); err != nil {
		t.Fatalf("upsert user: %v", err)
	}
	tok, _, err := cry.IssueToken("1234567", "alunograd", 1)
	if err != nil {
		t.Fatalf("issue token: %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/auth/me", nil)
	req.Header.Set("Authorization", "Bearer "+tok)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 before logout, got %d: %s", rec.Code, rec.Body.String())
	}

	// Token via cookie must no longer authenticate.
	cookieReq := httptest.NewRequest(http.MethodGet, "/api/auth/me", nil)
	cookieReq.AddCookie(&http.Cookie{Name: "portal_token", Value: tok})
	cookieRec := httptest.NewRecorder()
	srv.ServeHTTP(cookieRec, cookieReq)
	if cookieRec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 when using cookie only, got %d", cookieRec.Code)
	}

	logoutReq := httptest.NewRequest(http.MethodPost, "/api/auth/logout", nil)
	logoutReq.Header.Set("Authorization", "Bearer "+tok)
	logoutRec := httptest.NewRecorder()
	srv.ServeHTTP(logoutRec, logoutReq)
	if logoutRec.Code != http.StatusOK {
		t.Fatalf("expected 200 on logout, got %d: %s", logoutRec.Code, logoutRec.Body.String())
	}

	// Old token must immediately fail with 401 after logout.
	req2 := httptest.NewRequest(http.MethodGet, "/api/auth/me", nil)
	req2.Header.Set("Authorization", "Bearer "+tok)
	rec2 := httptest.NewRecorder()
	srv.ServeHTTP(rec2, req2)
	if rec2.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 after logout, got %d: %s", rec2.Code, rec2.Body.String())
	}
}

func TestProxyTicketsSandboxAndPathTraversal(t *testing.T) {
	var upstream *httptest.Server
	upstream = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/index/auth/auth_academico.asp":
			http.SetCookie(w, &http.Cookie{Name: "userName", Value: "Aluno", Path: "/"})
			http.Redirect(w, r, "/ambiente_aluno/ambiente_estudante.asp", http.StatusFound)
		case "/ambiente_aluno/ambiente_estudante.asp":
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte("<html>Bem-vindo</html>"))
		case "/ambiente_aluno/Localizacao_salas_v1.pdf":
			w.Header().Set("Content-Type", "text/html")
			_, _ = w.Write([]byte("<script>alert(1)</script>"))
		case "/ambiente_aluno/open_redirect.asp":
			http.Redirect(w, r, "https://evil.example/phish", http.StatusFound)
		default:
			http.NotFound(w, r)
		}
	}))
	defer upstream.Close()

	srv, store, cry := newTestServer(t, upstream.URL, false)
	encPass, _ := cry.Encrypt("pass123")
	_ = store.UpsertUser(storage.UserRecord{
		RA:                "1234567",
		Perfil:            "alunograd",
		EncryptedPassword: encPass,
		PollingEnabled:    true,
	})
	tok, _, _ := cry.IssueToken("1234567", "alunograd", 1)

	badPaths := []string{
		"/ambiente_aluno/../etc/passwd",
		"/ambiente_aluno/%2e%2e/secret",
		"/ambiente_aluno\\evil.asp",
		"https://evil.com/ambiente_aluno/x",
		"/index/login.php",
		"/ambiente_aluno/file.pdf#frag",
	}
	for _, p := range badPaths {
		if isAllowedProxyPath(p) {
			t.Fatalf("expected path %q to be blocked", p)
		}
	}

	// Legacy ?token= query param on /api/proxy must be rejected.
	legacyReq := httptest.NewRequest(http.MethodGet, "/api/proxy?path=/ambiente_aluno/Localizacao_salas_v1.pdf&token="+tok, nil)
	legacyRec := httptest.NewRecorder()
	srv.ServeHTTP(legacyRec, legacyReq)
	if legacyRec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 when passing JWT in query string, got %d", legacyRec.Code)
	}

	// Issue single-use ticket.
	body, _ := json.Marshal(map[string]string{"path": "/ambiente_aluno/Localizacao_salas_v1.pdf"})
	tReq := httptest.NewRequest(http.MethodPost, "/api/proxy/ticket", bytes.NewReader(body))
	tReq.Header.Set("Authorization", "Bearer "+tok)
	tRec := httptest.NewRecorder()
	srv.ServeHTTP(tRec, tReq)
	if tRec.Code != http.StatusOK {
		t.Fatalf("expected 200 issuing ticket, got %d: %s", tRec.Code, tRec.Body.String())
	}
	var tResp struct {
		Ticket string `json:"ticket"`
	}
	_ = json.Unmarshal(tRec.Body.Bytes(), &tResp)
	if tResp.Ticket == "" {
		t.Fatal("expected non-empty ticket")
	}

	// First use succeeds and carries sandbox CSP + nosniff.
	pReq := httptest.NewRequest(http.MethodGet, "/api/proxy?path=/ambiente_aluno/Localizacao_salas_v1.pdf&ticket="+tResp.Ticket, nil)
	pRec := httptest.NewRecorder()
	srv.ServeHTTP(pRec, pReq)
	if pRec.Code != http.StatusOK {
		t.Fatalf("expected 200 on first proxy fetch, got %d: %s", pRec.Code, pRec.Body.String())
	}
	if !strings.Contains(pRec.Header().Get("Content-Security-Policy"), "sandbox") {
		t.Fatalf("expected sandbox CSP header on proxy response, got %q", pRec.Header().Get("Content-Security-Policy"))
	}
	if pRec.Header().Get("X-Content-Type-Options") != "nosniff" {
		t.Fatal("expected X-Content-Type-Options: nosniff")
	}

	// Second use (replay) fails with 401.
	pRec2 := httptest.NewRecorder()
	srv.ServeHTTP(pRec2, httptest.NewRequest(http.MethodGet, "/api/proxy?path=/ambiente_aluno/Localizacao_salas_v1.pdf&ticket="+tResp.Ticket, nil))
	if pRec2.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 on proxy ticket replay, got %d", pRec2.Code)
	}

	// External portal redirect must be blocked.
	redirReq := httptest.NewRequest(http.MethodGet, "/api/proxy?path=/ambiente_aluno/open_redirect.asp", nil)
	redirReq.Header.Set("Authorization", "Bearer "+tok)
	redirRec := httptest.NewRecorder()
	srv.ServeHTTP(redirRec, redirReq)
	if redirRec.Code != http.StatusBadGateway {
		t.Fatalf("expected 502 on blocked external redirect, got %d", redirRec.Code)
	}
}

func TestClientIPSpoofingRateLimitBoundAndSecurityHeaders(t *testing.T) {
	srvNoDev, _, _ := newTestServer(t, "https://cdseducacional.com.br", false)
	s := &Server{trustedProxies: parseCIDRs([]string{"127.0.0.1/8", "::1/128"})}

	untrusted := httptest.NewRequest(http.MethodPost, "/api/auth/login", nil)
	untrusted.RemoteAddr = "203.0.113.55:4444"
	untrusted.Header.Set("X-Forwarded-For", "1.2.3.4")
	untrusted.Header.Set("CF-Connecting-IP", "5.6.7.8")
	if got := s.clientIP(untrusted); got != "203.0.113.55" {
		t.Fatalf("untrusted source spoofed clientIP: got %q", got)
	}

	trusted := httptest.NewRequest(http.MethodPost, "/api/auth/login", nil)
	trusted.RemoteAddr = "127.0.0.1:5000"
	trusted.Header.Set("CF-Connecting-IP", "198.51.100.42")
	if got := s.clientIP(trusted); got != "198.51.100.42" {
		t.Fatalf("trusted proxy should honor CF-Connecting-IP, got %q", got)
	}

	// Bounded rate limiter memory check.
	lim := newRateLimiter(3, time.Minute)
	for i := 0; i < maxLimiterEntries+500; i++ {
		lim.Allow(fmt.Sprintf("ip-%d", i))
	}
	if len(lim.entries) > maxLimiterEntries {
		t.Fatalf("rate limiter exceeded max entries: %d", len(lim.entries))
	}

	// Dev CORS disabled in prod + hidden dotfiles blocked.
	corsReq := httptest.NewRequest(http.MethodGet, "/api/health", nil)
	corsReq.Header.Set("Origin", "http://localhost:3000")
	corsRec := httptest.NewRecorder()
	srvNoDev.ServeHTTP(corsRec, corsReq)
	if corsRec.Header().Get("Access-Control-Allow-Origin") != "" {
		t.Fatal("expected localhost CORS disabled when AllowDevCORS=false")
	}
	if corsRec.Header().Get("X-Frame-Options") != "DENY" || corsRec.Header().Get("Content-Security-Policy") == "" {
		t.Fatalf("missing security headers: %v", corsRec.Header())
	}

	dotReq := httptest.NewRequest(http.MethodGet, "/.env", nil)
	dotRec := httptest.NewRecorder()
	srvNoDev.ServeHTTP(dotRec, dotReq)
	if dotRec.Code != http.StatusNotFound {
		t.Fatalf("expected 404 for hidden dotfile, got %d", dotRec.Code)
	}
}

func TestD2LRoutesAndTicketIsolation(t *testing.T) {
	d2lMock := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/d2l/login":
			_, _ = w.Write([]byte("login"))
		case "/d2l/lp/auth/login/login.d2l":
			http.SetCookie(w, &http.Cookie{Name: "d2lSessionVal", Value: "ok", Path: "/"})
			http.Redirect(w, r, "/d2l/home", http.StatusFound)
		case "/d2l/home":
			_, _ = w.Write([]byte("home"))
		case "/d2l/api/lp/1.0/users/whoami":
			_, _ = w.Write([]byte(`{"Identifier":"10001","FirstName":"ALUNO","LastName":"EXEMPLO","UniqueName":"1234567"}`))
		case "/d2l/api/le/1.74/50001/content/topics/501/file":
			w.Header().Set("Content-Type", "application/pdf")
			_, _ = w.Write([]byte("%PDF-1.4-D2L"))
		default:
			http.NotFound(w, r)
		}
	}))
	defer d2lMock.Close()

	srv, store, cry := newTestServerWithD2L(t, "https://cdseducacional.com.br", d2lMock.URL)
	encPass, _ := cry.Encrypt("pass123")
	_ = store.UpsertUser(storage.UserRecord{
		RA:                "1234567",
		Perfil:            "alunograd",
		Name:              "Aluno Exemplo",
		EncryptedPassword: encPass,
		PollingEnabled:    true,
	})
	tok, _, _ := cry.IssueToken("1234567", "alunograd", 1)

	// Unauthenticated D2L overview fails with 401.
	unauth := httptest.NewRecorder()
	srv.ServeHTTP(unauth, httptest.NewRequest(http.MethodGet, "/api/d2l/overview", nil))
	if unauth.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 for unauth d2l overview, got %d", unauth.Code)
	}

	// Invalid orgUnitId fails with 400.
	badOU := httptest.NewRequest(http.MethodGet, "/api/d2l/courses/abc", nil)
	badOU.Header.Set("Authorization", "Bearer "+tok)
	badOURec := httptest.NewRecorder()
	srv.ServeHTTP(badOURec, badOU)
	if badOURec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for invalid ou, got %d", badOURec.Code)
	}

	// Blocked D2L file path returns 403.
	badTicketReq := httptest.NewRequest(http.MethodPost, "/api/d2l/ticket", strings.NewReader(`{"path":"/d2l/api/le/1.74/50001/dropbox/folders/1/submissions/"}`))
	badTicketReq.Header.Set("Authorization", "Bearer "+tok)
	badTicketRec := httptest.NewRecorder()
	srv.ServeHTTP(badTicketRec, badTicketReq)
	if badTicketRec.Code != http.StatusForbidden {
		t.Fatalf("expected 403 for disallowed D2L path, got %d", badTicketRec.Code)
	}

	// Valid D2L file ticket downloads PDF with sandbox CSP and cannot be replayed.
	filePath := "/d2l/api/le/1.74/50001/content/topics/501/file?stream=false"
	tReq := httptest.NewRequest(http.MethodPost, "/api/d2l/ticket", strings.NewReader(`{"path":"`+filePath+`"}`))
	tReq.Header.Set("Authorization", "Bearer "+tok)
	tRec := httptest.NewRecorder()
	srv.ServeHTTP(tRec, tReq)
	if tRec.Code != http.StatusOK {
		t.Fatalf("expected 200 on d2l ticket, got %d: %s", tRec.Code, tRec.Body.String())
	}
	var tResp struct {
		Ticket string `json:"ticket"`
	}
	_ = json.Unmarshal(tRec.Body.Bytes(), &tResp)

	fReq := httptest.NewRequest(http.MethodGet, "/api/d2l/file?path="+strings.ReplaceAll(filePath, "&", "%26")+"&ticket="+tResp.Ticket, nil)
	fRec := httptest.NewRecorder()
	srv.ServeHTTP(fRec, fReq)
	if fRec.Code != http.StatusOK || fRec.Body.String() != "%PDF-1.4-D2L" {
		t.Fatalf("expected 200 and PDF body on /api/d2l/file, got %d %q", fRec.Code, fRec.Body.String())
	}
	if !strings.Contains(fRec.Header().Get("Content-Security-Policy"), "sandbox") {
		t.Fatalf("expected sandbox CSP header, got %q", fRec.Header().Get("Content-Security-Policy"))
	}

	// Replay fails with 401.
	fRec2 := httptest.NewRecorder()
	srv.ServeHTTP(fRec2, httptest.NewRequest(http.MethodGet, "/api/d2l/file?path="+strings.ReplaceAll(filePath, "&", "%26")+"&ticket="+tResp.Ticket, nil))
	if fRec2.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 on d2l ticket replay, got %d", fRec2.Code)
	}

	// ?download=1&name=Slides.pdf forces Content-Disposition: attachment even for application/pdf.
	tReq2 := httptest.NewRequest(http.MethodPost, "/api/d2l/ticket", strings.NewReader(`{"path":"`+filePath+`"}`))
	tReq2.Header.Set("Authorization", "Bearer "+tok)
	tRec2 := httptest.NewRecorder()
	srv.ServeHTTP(tRec2, tReq2)
	if tRec2.Code != http.StatusOK {
		t.Fatalf("expected 200 issuing second d2l ticket, got %d", tRec2.Code)
	}
	var tResp2 struct {
		Ticket string `json:"ticket"`
	}
	_ = json.Unmarshal(tRec2.Body.Bytes(), &tResp2)
	dlReq := httptest.NewRequest(http.MethodGet, "/api/d2l/file?path="+strings.ReplaceAll(filePath, "&", "%26")+"&ticket="+tResp2.Ticket+"&download=1&name=Slides.pdf", nil)
	dlRec := httptest.NewRecorder()
	srv.ServeHTTP(dlRec, dlReq)
	if dlRec.Code != http.StatusOK {
		t.Fatalf("expected 200 on forced download, got %d", dlRec.Code)
	}
	if cd := dlRec.Header().Get("Content-Disposition"); !strings.Contains(cd, "attachment") || !strings.Contains(cd, "Slides.pdf") {
		t.Fatalf("expected Content-Disposition attachment with filename Slides.pdf, got %q", cd)
	}
}
