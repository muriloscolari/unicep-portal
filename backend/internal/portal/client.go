package portal

import (
	"bytes"
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
	"time"

	"golang.org/x/text/encoding/charmap"
)

var (
	// ErrInvalidCredentials indicates the portal rejected username/password/profile.
	ErrInvalidCredentials = errors.New("invalid portal credentials")
	// ErrSessionExpired indicates the portal redirected to session_abandon.asp or login.php.
	ErrSessionExpired = errors.New("portal session expired")
)

// CredentialProvider resolves a user's stored password and profile for transparent re-login.
type CredentialProvider func(ra string) (password string, perfil string, err error)

// Manager maintains per-student authenticated HTTP cookie jars and performs portal operations.
type Manager struct {
	baseURL  string
	mu       sync.RWMutex
	sessions map[string]*studentSession
	creds    CredentialProvider
}

type studentSession struct {
	mu      sync.Mutex
	client  *http.Client
	jar     *cookiejar.Jar
	info    StudentInfo
	nacOpen bool
}

// NewManager creates a portal client manager targeting baseURL.
func NewManager(baseURL string, creds CredentialProvider) *Manager {
	return &Manager{
		baseURL:  strings.TrimRight(baseURL, "/"),
		sessions: make(map[string]*studentSession),
		creds:    creds,
	}
}

func (m *Manager) getOrCreateSession(ra, perfil string) (*studentSession, error) {
	m.mu.RLock()
	s, ok := m.sessions[ra]
	m.mu.RUnlock()
	if ok {
		return s, nil
	}

	m.mu.Lock()
	defer m.mu.Unlock()
	if s, ok = m.sessions[ra]; ok {
		return s, nil
	}

	jar, err := cookiejar.New(nil)
	if err != nil {
		return nil, err
	}

	client := &http.Client{
		Jar:     jar,
		Timeout: 25 * time.Second,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			path := strings.ToLower(req.URL.Path)
			if strings.Contains(path, "session_abandon.asp") || strings.Contains(path, "/index/login.php") {
				return ErrSessionExpired
			}
			if len(via) >= 8 {
				return errors.New("too many redirects")
			}
			return nil
		},
	}

	s = &studentSession{
		client: client,
		jar:    jar,
		info: StudentInfo{
			RA:     ra,
			Perfil: perfil,
		},
	}
	m.sessions[ra] = s
	return s, nil
}

// Login authenticates against auth_academico.asp, runs validaEntrada.asp, and initializes the ASP/PHP cookies.
func (m *Manager) Login(ctx context.Context, ra, password, perfil string) (StudentInfo, bool, error) {
	if perfil == "" {
		perfil = "alunograd"
	}
	sess, err := m.getOrCreateSession(ra, perfil)
	if err != nil {
		return StudentInfo{}, false, err
	}
	sess.mu.Lock()
	defer sess.mu.Unlock()

	if err := m.loginLocked(ctx, sess, ra, password, perfil); err != nil {
		return StudentInfo{}, false, err
	}
	return sess.info, sess.nacOpen, nil
}

func (m *Manager) loginLocked(ctx context.Context, sess *studentSession, ra, password, perfil string) error {
	// Reset jar so stale session cookies cannot poison a fresh login.
	jar, err := cookiejar.New(nil)
	if err != nil {
		return err
	}
	sess.jar = jar
	sess.client.Jar = jar

	form := url.Values{}
	form.Set("campo", "")
	form.Set("u", "uni")
	form.Set("ambiente", "academico")
	form.Set("usuario", ra)
	form.Set("senha", password)
	form.Set("txttipo", perfil)

	encodedBody, err := encodeFormISO88591(form)
	if err != nil {
		return err
	}

	// Do not auto-follow the login 302 so we can inspect where auth_academico.asp redirects.
	noRedirectClient := *sess.client
	noRedirectClient.CheckRedirect = func(req *http.Request, via []*http.Request) error {
		return http.ErrUseLastResponse
	}

	loginURL := m.baseURL + "/index/auth/auth_academico.asp"
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, loginURL, strings.NewReader(encodedBody))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Referer", m.baseURL+"/index/login.php?amb=academico&u=uni")

	resp, err := noRedirectClient.Do(req)
	if err != nil {
		return fmt.Errorf("login request: %w", err)
	}
	_, _ = io.Copy(io.Discard, resp.Body)
	_ = resp.Body.Close()

	loc := resp.Header.Get("Location")
	if resp.StatusCode != http.StatusFound || loc == "" || strings.Contains(strings.ToLower(loc), "login.php") {
		return ErrInvalidCredentials
	}

	// Follow preAvaliacaoInstitucionalDis.php if gated by NAC, then run validaEntrada.asp.
	if strings.Contains(strings.ToLower(loc), "preavaliacaoinstitucionaldis.php") {
		preURL := resolveRelative(loginURL, loc)
		if _, _, err := m.doGetRaw(ctx, sess.client, preURL); err != nil {
			return fmt.Errorf("load evaluation gate: %w", err)
		}
		sess.nacOpen = m.checkNacGate(ctx, sess.client)
		if _, _, err := m.doGetRaw(ctx, sess.client, m.baseURL+"/MVCGraduacao/Controller/validaEntrada.asp"); err != nil {
			return fmt.Errorf("validaEntrada: %w", err)
		}
	}

	// Load ambiente_estudante.asp to populate usuarioLogado + campus cookies and verify access.
	body, _, err := m.doGetRaw(ctx, sess.client, m.baseURL+"/ambiente_aluno/ambiente_estudante.asp")
	if err != nil {
		if errors.Is(err, ErrSessionExpired) {
			return ErrInvalidCredentials
		}
		return fmt.Errorf("enter student portal: %w", err)
	}

	if strings.Contains(strings.ToLower(string(body)), "usuário e/ou senha inválidos") {
		return ErrInvalidCredentials
	}

	sess.info = m.extractStudentInfo(sess.jar, ra, perfil)
	return nil
}

func (m *Manager) checkNacGate(ctx context.Context, client *http.Client) bool {
	u := m.baseURL + "/MVCGraduacao/Controller/avaliacaoInstitucionalDis.php?acao=verificaPrazoResposta&destinacao=DI"
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, u, nil)
	if err != nil {
		return false
	}
	resp, err := client.Do(req)
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	var payload struct {
		Retorno string `json:"retorno"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return false
	}
	return strings.EqualFold(payload.Retorno, "obrigatorio")
}

func (m *Manager) extractStudentInfo(jar *cookiejar.Jar, ra, perfil string) StudentInfo {
	u, _ := url.Parse(m.baseURL)
	info := StudentInfo{RA: ra, Perfil: perfil}
	for _, c := range jar.Cookies(u) {
		val, err := url.QueryUnescape(c.Value)
		if err != nil {
			val = c.Value
		}
		switch c.Name {
		case "userName":
			info.Name = strings.TrimSpace(val)
		case "userCampus", "campus":
			if info.Campus == "" {
				info.Campus = strings.TrimSpace(val)
			}
		case "userSemestre":
			info.Semester = strings.TrimSpace(val)
		case "userRA":
			if val != "" {
				info.RA = strings.TrimSpace(val)
			}
		}
	}
	return info
}

// FetchHTML fetches a portal path and decodes windows-1252/UTF-8 automatically, retrying once after re-login on session expiry.
func (m *Manager) FetchHTML(ctx context.Context, ra, relPath string) (string, StudentInfo, bool, error) {
	raw, _, info, nacOpen, err := m.FetchRaw(ctx, ra, relPath)
	if err != nil {
		return "", StudentInfo{}, false, err
	}
	decoded, err := DecodePortalHTML(raw)
	if err != nil {
		return "", StudentInfo{}, false, err
	}
	return decoded, info, nacOpen, nil
}

// FetchRaw fetches raw bytes and Content-Type for HTML pages or PDFs, transparently refreshing expired sessions.
func (m *Manager) FetchRaw(ctx context.Context, ra, relPath string) ([]byte, string, StudentInfo, bool, error) {
	sess, err := m.getOrCreateSession(ra, "alunograd")
	if err != nil {
		return nil, "", StudentInfo{}, false, err
	}
	sess.mu.Lock()
	defer sess.mu.Unlock()

	targetURL := m.normalizeURL(relPath)
	if sess.info.Name == "" {
		if err := m.reloginLocked(ctx, sess, ra); err != nil {
			return nil, "", StudentInfo{}, false, err
		}
	}

	raw, ct, err := m.doGetRaw(ctx, sess.client, targetURL)
	if errors.Is(err, ErrSessionExpired) {
		if err := m.reloginLocked(ctx, sess, ra); err != nil {
			return nil, "", StudentInfo{}, false, err
		}
		raw, ct, err = m.doGetRaw(ctx, sess.client, targetURL)
	}
	if err != nil {
		return nil, "", StudentInfo{}, false, err
	}
	return raw, ct, sess.info, sess.nacOpen, nil
}

func (m *Manager) reloginLocked(ctx context.Context, sess *studentSession, ra string) error {
	if m.creds == nil {
		return ErrSessionExpired
	}
	pass, perfil, err := m.creds(ra)
	if err != nil {
		return err
	}
	return m.loginLocked(ctx, sess, ra, pass, perfil)
}

func (m *Manager) doGetRaw(ctx context.Context, client *http.Client, targetURL string) ([]byte, string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		return nil, "", err
	}
	resp, err := client.Do(req)
	if err != nil {
		var urlErr *url.Error
		if errors.As(err, &urlErr) && errors.Is(urlErr.Err, ErrSessionExpired) {
			return nil, "", ErrSessionExpired
		}
		if errors.Is(err, ErrSessionExpired) {
			return nil, "", ErrSessionExpired
		}
		return nil, "", err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 16*1024*1024))
	if err != nil {
		return nil, "", err
	}
	ct := resp.Header.Get("Content-Type")
	return body, ct, nil
}

func (m *Manager) normalizeURL(relPath string) string {
	if strings.HasPrefix(relPath, "http://") || strings.HasPrefix(relPath, "https://") {
		return relPath
	}
	if !strings.HasPrefix(relPath, "/") {
		return m.baseURL + "/" + relPath
	}
	return m.baseURL + relPath
}

func resolveRelative(baseStr, loc string) string {
	base, err := url.Parse(baseStr)
	if err != nil {
		return loc
	}
	ref, err := url.Parse(loc)
	if err != nil {
		return loc
	}
	return base.ResolveReference(ref).String()
}

func encodeFormISO88591(values url.Values) (string, error) {
	enc := charmap.ISO8859_1.NewEncoder()
	parts := make([]string, 0, len(values))
	for k, list := range values {
		for _, v := range list {
			isoVal, err := enc.String(v)
			if err != nil {
				isoVal = v
			}
			parts = append(parts, url.QueryEscape(k)+"="+escapeISO88591Bytes([]byte(isoVal)))
		}
	}
	return strings.Join(parts, "&"), nil
}

func escapeISO88591Bytes(b []byte) string {
	var buf bytes.Buffer
	for _, c := range b {
		if (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~' {
			buf.WriteByte(c)
		} else if c == ' ' {
			buf.WriteByte('+')
		} else {
			fmt.Fprintf(&buf, "%%%02X", c)
		}
	}
	return buf.String()
}

// DecodePortalHTML converts windows-1252 HTML responses to UTF-8 unless the document is explicitly UTF-8.
func DecodePortalHTML(raw []byte) (string, error) {
	sample := strings.ToLower(string(raw[:min(len(raw), 1024)]))
	// ambiente_estudante.asp declares both utf-8 and iso-8859-1; Chrome renders utf-8 for it,
	// whereas report pages (/Templates/tela_janela2.dwt) only declare iso-8859-1.
	if strings.Contains(sample, `<meta charset="utf-8"`) {
		return string(raw), nil
	}
	decoded, err := charmap.Windows1252.NewDecoder().Bytes(raw)
	if err != nil {
		return string(raw), nil
	}
	return string(decoded), nil
}
