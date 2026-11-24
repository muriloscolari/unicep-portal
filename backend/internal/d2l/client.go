package d2l

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/cookiejar"
	"net/url"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

const (
	rootOrgUnitID    int64 = 6606
	leAPIVersion           = "1.74"
	lpAPIVersion           = "1.43"
	maxBodyBytes           = 16 << 20
	maxSessions            = 4096
	sessionIdleTTL         = 2 * time.Hour
	overviewCacheTTL       = 90 * time.Second
)

var (
	// ErrInvalidCredentials indicates Brightspace rejected the student's login credentials.
	ErrInvalidCredentials = errors.New("credenciais inválidas na Sala Virtual")
	// ErrSessionExpired indicates Brightspace redirected to the login/session-expired page.
	ErrSessionExpired = errors.New("sessão da Sala Virtual expirada")
	// ErrCourseNotAvailable indicates the requested Brightspace course offering is missing or inaccessible.
	ErrCourseNotAvailable = errors.New("disciplina não disponível na Sala Virtual")
	// ErrFileTooLarge indicates a Brightspace file exceeded the 16 MiB download limit.
	ErrFileTooLarge = errors.New("arquivo excede o limite de 16 MB")
	// ErrFileNotFound indicates Brightspace returned 404 for the requested attachment/content file.
	ErrFileNotFound = errors.New("arquivo não encontrado na Sala Virtual")

	errToolUnavailable = errors.New("d2l tool unavailable")

	saoPauloTZ = loadSaoPauloLocation()
)

func loadSaoPauloLocation() *time.Location {
	if loc, err := time.LoadLocation("America/Sao_Paulo"); err == nil {
		return loc
	}
	return time.FixedZone("BRT", -3*3600)
}

// CredentialProvider resolves stored RA password for transparent Brightspace authentication.
type CredentialProvider func(ra string) (password string, err error)

type cachedOverview struct {
	resp      OverviewResponse
	expiresAt time.Time
}

type overviewCall struct {
	done chan struct{}
	resp OverviewResponse
	err  error
}

// Manager maintains per-student authenticated Brightspace sessions and provides read-only data access.
type Manager struct {
	baseURL          string
	baseScheme       string
	baseHost         string
	mu               sync.RWMutex
	sessions         map[string]*studentSession
	overviewMu       sync.Mutex
	overviewCache    map[string]cachedOverview
	overviewInflight map[string]*overviewCall
	creds            CredentialProvider
}

type studentSession struct {
	mu               sync.Mutex
	client           *http.Client
	userID           string
	userName         string
	loggedIn         bool
	lastLoggedIn     time.Time
	lastUsedNano     atomic.Int64
	consecutiveFails int
	retryAfter       time.Time
}

// NewManager creates a read-only D2L Brightspace manager targeting baseURL.
func NewManager(baseURL string, creds CredentialProvider) *Manager {
	trimmed := strings.TrimRight(strings.TrimSpace(baseURL), "/")
	parsed, _ := url.Parse(trimmed)
	scheme, host := "https", ""
	if parsed != nil {
		if parsed.Scheme != "" {
			scheme = strings.ToLower(parsed.Scheme)
		}
		host = parsed.Host
	}
	return &Manager{
		baseURL:          trimmed,
		baseScheme:       scheme,
		baseHost:         host,
		sessions:         make(map[string]*studentSession),
		overviewCache:    make(map[string]cachedOverview),
		overviewInflight: make(map[string]*overviewCall),
		creds:            creds,
	}
}

// BaseURL returns the configured Brightspace base URL.
func (m *Manager) BaseURL() string {
	return m.baseURL
}

// EvictSession drops any cached Brightspace session, cookies, and overview cache for ra.
func (m *Manager) EvictSession(ra string) {
	m.mu.Lock()
	delete(m.sessions, ra)
	m.mu.Unlock()

	m.overviewMu.Lock()
	delete(m.overviewCache, ra)
	m.overviewMu.Unlock()
}

func (m *Manager) newHTTPClient() (*http.Client, error) {
	jar, err := cookiejar.New(nil)
	if err != nil {
		return nil, err
	}
	return &http.Client{
		Jar:     jar,
		Timeout: 25 * time.Second,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if m.baseHost != "" && (!strings.EqualFold(req.URL.Host, m.baseHost) || !strings.EqualFold(req.URL.Scheme, m.baseScheme)) {
				return errors.New("blocked external d2l redirect")
			}
			p := strings.ToLower(req.URL.Path)
			if strings.Contains(p, "/d2l/login") || strings.Contains(p, "/d2l/lp/auth/session/") {
				return ErrSessionExpired
			}
			if len(via) >= 8 {
				return errors.New("too many redirects")
			}
			return nil
		},
	}, nil
}

func (m *Manager) getOrCreateSession(ra string) (*studentSession, error) {
	now := time.Now()
	m.mu.RLock()
	s, ok := m.sessions[ra]
	m.mu.RUnlock()
	if ok {
		s.lastUsedNano.Store(now.UnixNano())
		return s, nil
	}

	m.mu.Lock()
	defer m.mu.Unlock()
	if s, ok = m.sessions[ra]; ok {
		s.lastUsedNano.Store(now.UnixNano())
		return s, nil
	}

	cutoff := now.Add(-sessionIdleTTL).UnixNano()
	for k, existing := range m.sessions {
		if last := existing.lastUsedNano.Load(); last > 0 && last < cutoff {
			delete(m.sessions, k)
		}
	}
	if len(m.sessions) >= maxSessions {
		var oldestKey string
		var oldestNano int64
		for k, existing := range m.sessions {
			last := existing.lastUsedNano.Load()
			if oldestKey == "" || last < oldestNano {
				oldestKey = k
				oldestNano = last
			}
		}
		if oldestKey != "" {
			delete(m.sessions, oldestKey)
		}
	}

	client, err := m.newHTTPClient()
	if err != nil {
		return nil, err
	}
	s = &studentSession{client: client}
	s.lastUsedNano.Store(now.UnixNano())
	m.sessions[ra] = s
	return s, nil
}

func (m *Manager) ensureClient(ctx context.Context, ra string, forceRelogin bool) (*http.Client, string, string, error) {
	sess, err := m.getOrCreateSession(ra)
	if err != nil {
		return nil, "", "", err
	}
	sess.mu.Lock()
	defer sess.mu.Unlock()

	if !forceRelogin && sess.loggedIn && sess.client != nil && time.Since(sess.lastLoggedIn) < 45*time.Minute {
		return sess.client, sess.userID, sess.userName, nil
	}
	if !sess.retryAfter.IsZero() && time.Now().Before(sess.retryAfter) {
		return nil, "", "", ErrInvalidCredentials
	}

	if m.creds == nil {
		return nil, "", "", ErrSessionExpired
	}
	password, err := m.creds(ra)
	if err != nil {
		return nil, "", "", err
	}

	if err := m.loginLocked(ctx, sess, ra, password); err != nil {
		if errors.Is(err, ErrInvalidCredentials) {
			sess.consecutiveFails++
			if sess.consecutiveFails >= 6 {
				sess.retryAfter = time.Now().Add(2 * time.Hour)
			} else if sess.consecutiveFails >= 3 {
				sess.retryAfter = time.Now().Add(30 * time.Minute)
			}
		}
		return nil, "", "", err
	}
	sess.consecutiveFails = 0
	sess.retryAfter = time.Time{}
	return sess.client, sess.userID, sess.userName, nil
}

func (m *Manager) loginLocked(ctx context.Context, sess *studentSession, ra, password string) error {
	client, err := m.newHTTPClient()
	if err != nil {
		return err
	}

	// Step 1: Prime login cookies / canary cookies without triggering ErrSessionExpired on /d2l/login.
	initReq, err := http.NewRequestWithContext(ctx, http.MethodGet, m.baseURL+"/d2l/login?sessionExpired=0&target=%2fd2l%2fhome", nil)
	if err != nil {
		return err
	}
	initResp, err := client.Do(initReq)
	if err != nil {
		return fmt.Errorf("d2l login init: %w", err)
	}
	_, _ = io.Copy(io.Discard, io.LimitReader(initResp.Body, 1<<20))
	_ = initResp.Body.Close()

	// Step 2: Submit credentials via Brightspace native form endpoint.
	form := url.Values{
		"d2l_referrer": {""},
		"target":       {"/d2l/home"},
		"loginPath":    {"/d2l/login"},
		"userName":     {ra},
		"password":     {password},
	}
	loginReq, err := http.NewRequestWithContext(ctx, http.MethodPost, m.baseURL+"/d2l/lp/auth/login/login.d2l", strings.NewReader(form.Encode()))
	if err != nil {
		return err
	}
	loginReq.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	loginResp, err := client.Do(loginReq)
	if err != nil {
		if errors.Is(err, ErrSessionExpired) {
			return ErrInvalidCredentials
		}
		return err
	}
	_, _ = io.Copy(io.Discard, io.LimitReader(loginResp.Body, 1<<20))
	_ = loginResp.Body.Close()

	// Step 3: Verify session via whoami.
	var who whoamiPayload
	if err := m.doGetJSON(ctx, client, "/d2l/api/lp/1.0/users/whoami", &who); err != nil {
		if errors.Is(err, ErrSessionExpired) {
			return ErrInvalidCredentials
		}
		return err
	}
	if strings.TrimSpace(who.Identifier) == "" {
		return ErrInvalidCredentials
	}

	sess.client = client
	sess.userID = who.Identifier
	sess.userName = strings.TrimSpace(strings.TrimSpace(who.FirstName) + " " + strings.TrimSpace(who.LastName))
	sess.loggedIn = true
	sess.lastLoggedIn = time.Now()
	return nil
}

func (m *Manager) withClient(ctx context.Context, ra string, fn func(client *http.Client, userID, userName string) error) error {
	client, userID, userName, err := m.ensureClient(ctx, ra, false)
	if err != nil {
		return err
	}
	err = fn(client, userID, userName)
	if errors.Is(err, ErrSessionExpired) {
		client, userID, userName, err = m.ensureClient(ctx, ra, true)
		if err != nil {
			return err
		}
		return fn(client, userID, userName)
	}
	return err
}

func (m *Manager) doGetJSON(ctx context.Context, client *http.Client, relPath string, out any) error {
	raw, status, _, _, err := m.doGetBytes(ctx, client, relPath)
	if err != nil {
		return err
	}
	if status == http.StatusUnauthorized || (status == http.StatusForbidden && strings.HasPrefix(strings.ToLower(relPath), "/d2l/api/lp/")) {
		return ErrSessionExpired
	}
	if status == http.StatusForbidden || status == http.StatusNotFound {
		return fmt.Errorf("%w: status %d on %s", errToolUnavailable, status, relPath)
	}
	if status >= 400 {
		return fmt.Errorf("d2l api status %d on %s", status, relPath)
	}
	if err := json.Unmarshal(raw, out); err != nil {
		return fmt.Errorf("decode %s: %w", relPath, err)
	}
	return nil
}

func (m *Manager) doGetBytes(ctx context.Context, client *http.Client, relPath string) ([]byte, int, string, string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, m.baseURL+relPath, nil)
	if err != nil {
		return nil, 0, "", "", err
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, 0, "", "", err
	}
	defer resp.Body.Close()

	finalPath := strings.ToLower(resp.Request.URL.Path)
	if strings.Contains(finalPath, "/d2l/login") || strings.Contains(finalPath, "/d2l/lp/auth/session/") {
		_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 1<<20))
		return nil, resp.StatusCode, "", "", ErrSessionExpired
	}

	if resp.ContentLength > maxBodyBytes {
		_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 64<<10))
		return nil, resp.StatusCode, "", "", ErrFileTooLarge
	}
	raw, err := io.ReadAll(io.LimitReader(resp.Body, maxBodyBytes+1))
	if err != nil {
		return nil, resp.StatusCode, "", "", err
	}
	if int64(len(raw)) > maxBodyBytes {
		return nil, resp.StatusCode, "", "", ErrFileTooLarge
	}
	return raw, resp.StatusCode, resp.Header.Get("Content-Type"), resp.Header.Get("Content-Disposition"), nil
}
