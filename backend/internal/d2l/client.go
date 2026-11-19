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
	"path"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/PuerkitoBio/goquery"
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

// GetOverview loads semesters, courses, announcements, upcoming calendar events, and pending assignments passively,
// serving a short-lived cache hit when available.
func (m *Manager) GetOverview(ctx context.Context, ra string) (OverviewResponse, error) {
	return m.GetOverviewWithOptions(ctx, ra, false)
}

// GetOverviewWithOptions loads the Sala Virtual overview with per-RA singleflight coalescing and a 90s TTL cache.
func (m *Manager) GetOverviewWithOptions(ctx context.Context, ra string, forceRefresh bool) (OverviewResponse, error) {
	m.overviewMu.Lock()
	if !forceRefresh {
		if cached, ok := m.overviewCache[ra]; ok && time.Now().Before(cached.expiresAt) {
			m.overviewMu.Unlock()
			return cached.resp, nil
		}
	}
	if call, ok := m.overviewInflight[ra]; ok {
		m.overviewMu.Unlock()
		select {
		case <-call.done:
			return call.resp, call.err
		case <-ctx.Done():
			return OverviewResponse{}, ctx.Err()
		}
	}
	call := &overviewCall{done: make(chan struct{})}
	m.overviewInflight[ra] = call
	m.overviewMu.Unlock()

	reqCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), 45*time.Second)
	go func() {
		defer cancel()
		resp, err := m.fetchOverviewUncached(reqCtx, ra)
		call.resp = resp
		call.err = err

		m.overviewMu.Lock()
		delete(m.overviewInflight, ra)
		if err == nil && resp.Complete {
			m.overviewCache[ra] = cachedOverview{
				resp:      resp,
				expiresAt: time.Now().Add(overviewCacheTTL),
			}
		}
		m.overviewMu.Unlock()
		close(call.done)
	}()

	select {
	case <-call.done:
		return call.resp, call.err
	case <-ctx.Done():
		return OverviewResponse{}, ctx.Err()
	}
}

func (m *Manager) fetchOverviewUncached(ctx context.Context, ra string) (OverviewResponse, error) {
	var out OverviewResponse
	err := m.withClient(ctx, ra, func(client *http.Client, userID, userName string) error {
		res := OverviewResponse{
			BaseURL:             m.baseURL,
			UserID:              userID,
			UserName:            userName,
			Complete:            true,
			Semesters:           []SemesterOption{},
			Courses:             []CourseSummary{},
			UpcomingAssignments: []AssignmentItem{},
			UpcomingQuizzes:     []QuizItem{},
			Announcements:       []Announcement{},
			CalendarEvents:      []CalendarEvent{},
			SucceededCourseIDs:  []int64{},
		}

		var (
			wg           sync.WaitGroup
			semesters    []SemesterOption
			courses      []CourseSummary
			coursesErr   error
			globalNews   []Announcement
			errMu        sync.Mutex
		)

		wg.Add(3)
		go func() {
			defer wg.Done()
			var rawSems []rawSemester
			if err := m.doGetJSON(ctx, client, "/d2l/le/manageCourses/api/mysemesters?isAscending=true&widgetId=97", &rawSems); err == nil {
				for _, s := range rawSems {
					semesters = append(semesters, SemesterOption{
						ID:   s.OrgUnitID.String(),
						Name: strings.TrimSpace(s.Name),
						Code: strings.TrimSpace(s.Code),
					})
				}
			}
		}()

		go func() {
			defer wg.Done()
			list, err := m.fetchCourses(ctx, client)
			if err != nil {
				errMu.Lock()
				coursesErr = err
				errMu.Unlock()
				return
			}
			courses = list
		}()

		go func() {
			defer wg.Done()
			items, _ := m.fetchNews(ctx, client, rootOrgUnitID, "UNICEP")
			globalNews = items
		}()

		wg.Wait()
		if coursesErr != nil {
			return coursesErr
		}

		res.Semesters = semesters
		res.Courses = courses

		// Identify active courses in the latest semester to enrich assignments, news, quizzes, and calendar.
		orgIDs := []string{strconv.FormatInt(rootOrgUnitID, 10)}
		courseMap := make(map[int64]string, len(courses))
		for _, c := range courses {
			courseMap[c.OrgUnitID] = c.Name
			if c.CanAccess {
				orgIDs = append(orgIDs, strconv.FormatInt(c.OrgUnitID, 10))
			}
		}
		activeCourses := selectActiveCourseIndices(semesters, courses)

		var (
			enrichWG      sync.WaitGroup
			mu            sync.Mutex
			allNews       = append([]Announcement{}, globalNews...)
			allAssigns    []AssignmentItem
			allQuizzes    []QuizItem
			succeededIDs  []int64
			calendarItems []CalendarEvent
			successCount  int
			failCount     int
			firstErr      error
			sessionErr    error
			sem           = make(chan struct{}, 4)
		)

		enrichWG.Add(1)
		go func() {
			defer enrichWG.Done()
			now := time.Now().UTC()
			start := now.Add(-30 * 24 * time.Hour).Format("2006-01-02T15:04:05.000Z")
			end := now.Add(120 * 24 * time.Hour).Format("2006-01-02T15:04:05.000Z")
			path := fmt.Sprintf("/d2l/api/le/%s/calendar/events/myEvents/?startDateTime=%s&endDateTime=%s&orgUnitIdsCSV=%s",
				leAPIVersion, url.QueryEscape(start), url.QueryEscape(end), url.QueryEscape(strings.Join(orgIDs, ",")))
			var calResp rawCalendarList
			if err := m.doGetJSON(ctx, client, path, &calResp); err == nil {
				for _, ev := range calResp.Objects {
					calendarItems = append(calendarItems, ev.toModel(m.baseURL, courseMap[ev.OrgUnitID]))
				}
			}
		}()

		for _, idx := range activeCourses {
			idx := idx
			course := courses[idx]
			enrichWG.Add(1)
			go func() {
				defer enrichWG.Done()
				sem <- struct{}{}
				defer func() { <-sem }()

				assigns, assignErr := m.fetchAssignments(ctx, client, course.OrgUnitID, course.Name)
				courseNews, newsErr := m.fetchNews(ctx, client, course.OrgUnitID, course.Name)
				courseQuizzes, quizErr := m.fetchQuizzes(ctx, client, course.OrgUnitID, course.Name)

				if errors.Is(assignErr, ErrSessionExpired) || errors.Is(newsErr, ErrSessionExpired) || errors.Is(quizErr, ErrSessionExpired) {
					mu.Lock()
					sessionErr = ErrSessionExpired
					mu.Unlock()
					return
				}

				pending := 0
				visibleAssigns := make([]AssignmentItem, 0, len(assigns))
				if assignErr == nil {
					for _, a := range assigns {
						if a.Status == "not_submitted" {
							pending++
						}
						if a.Status != "expired" {
							visibleAssigns = append(visibleAssigns, a)
						}
					}
				}

				var courseErr error
				switch {
				case assignErr != nil:
					courseErr = assignErr
				case newsErr != nil:
					courseErr = newsErr
				case quizErr != nil:
					courseErr = quizErr
				}

				mu.Lock()
				if assignErr == nil {
					res.Courses[idx].AssignmentCount = len(visibleAssigns)
					res.Courses[idx].PendingCount = pending
					allAssigns = append(allAssigns, visibleAssigns...)
				}
				if newsErr == nil && len(courseNews) > 0 {
					allNews = append(allNews, courseNews...)
				}
				if quizErr == nil && len(courseQuizzes) > 0 {
					allQuizzes = append(allQuizzes, courseQuizzes...)
				}
				if courseErr != nil {
					failCount++
					if firstErr == nil {
						firstErr = courseErr
					}
				} else {
					successCount++
					succeededIDs = append(succeededIDs, course.OrgUnitID)
				}
				mu.Unlock()
			}()
		}

		enrichWG.Wait()
		if sessionErr != nil {
			return sessionErr
		}
		if len(activeCourses) > 0 && successCount == 0 && failCount > 0 {
			return firstErr
		}
		if failCount > 0 {
			res.Complete = false
		}

		sort.SliceStable(allAssigns, func(i, j int) bool {
			return compareAssignmentPriority(allAssigns[i], allAssigns[j])
		})
		sort.SliceStable(allQuizzes, func(i, j int) bool {
			return compareQuizPriority(allQuizzes[i], allQuizzes[j])
		})
		sort.SliceStable(allNews, func(i, j int) bool {
			return newsSortKey(allNews[i]) > newsSortKey(allNews[j])
		})
		if len(allNews) > 25 {
			allNews = allNews[:25]
		}
		sort.SliceStable(calendarItems, func(i, j int) bool {
			return calendarItems[i].StartDateTime < calendarItems[j].StartDateTime
		})

		res.UpcomingAssignments = allAssigns
		res.UpcomingQuizzes = allQuizzes
		res.Announcements = allNews
		res.CalendarEvents = calendarItems
		res.SucceededCourseIDs = succeededIDs
		out = res
		return nil
	})
	return out, err
}

// GetCourseDetail loads passive details (content modules, assignments, grades, quizzes, forums, announcements, calendar) for one course.
func (m *Manager) GetCourseDetail(ctx context.Context, ra string, orgUnitID int64) (CourseDetailResponse, error) {
	var out CourseDetailResponse
	err := m.withClient(ctx, ra, func(client *http.Client, _, _ string) error {
		courses, err := m.fetchCourses(ctx, client)
		if err != nil {
			return err
		}
		var course CourseSummary
		found := false
		for _, c := range courses {
			if c.OrgUnitID == orgUnitID {
				course = c
				found = true
				break
			}
		}
		if !found {
			return ErrCourseNotAvailable
		}

		res := CourseDetailResponse{
			BaseURL:       m.baseURL,
			Course:        course,
			Announcements: []Announcement{},
			Modules:       []ContentModule{},
			Assignments:   []AssignmentItem{},
			Grades:        []VirtualGradeItem{},
			Quizzes:       []QuizItem{},
			Discussions:   []DiscussionForum{},
			Calendar:      []CalendarEvent{},
		}

		var (
			wg         sync.WaitGroup
			sessionErr error
			errMu      sync.Mutex
		)
		recordSessionErr := func(e error) {
			if errors.Is(e, ErrSessionExpired) {
				errMu.Lock()
				sessionErr = e
				errMu.Unlock()
			}
		}

		wg.Add(7)
		go func() {
			defer wg.Done()
			mods, e := m.fetchContentTOC(ctx, client, orgUnitID)
			recordSessionErr(e)
			if e == nil {
				res.Modules = mods
			}
		}()
		go func() {
			defer wg.Done()
			assigns, e := m.fetchAssignments(ctx, client, orgUnitID, course.Name)
			recordSessionErr(e)
			if e == nil {
				res.Assignments = assigns
			}
		}()
		go func() {
			defer wg.Done()
			grades, e := m.fetchGrades(ctx, client, orgUnitID)
			recordSessionErr(e)
			if e == nil {
				res.Grades = grades
			}
		}()
		go func() {
			defer wg.Done()
			news, e := m.fetchNews(ctx, client, orgUnitID, course.Name)
			recordSessionErr(e)
			if e == nil {
				res.Announcements = news
			}
		}()
		go func() {
			defer wg.Done()
			quizzes, e := m.fetchQuizzes(ctx, client, orgUnitID, course.Name)
			recordSessionErr(e)
			if e == nil {
				res.Quizzes = quizzes
			}
		}()
		go func() {
			defer wg.Done()
			forums, e := m.fetchDiscussions(ctx, client, orgUnitID)
			recordSessionErr(e)
			if e == nil {
				res.Discussions = forums
			}
		}()
		go func() {
			defer wg.Done()
			cal, e := m.fetchCourseCalendar(ctx, client, orgUnitID, course.Name)
			recordSessionErr(e)
			if e == nil {
				res.Calendar = cal
			}
		}()

		wg.Wait()
		if sessionErr != nil {
			return sessionErr
		}

		pending := 0
		activeCount := 0
		for _, a := range res.Assignments {
			if a.Status == "not_submitted" {
				pending++
			}
			if a.Status != "expired" {
				activeCount++
			}
		}
		sort.SliceStable(res.Assignments, func(i, j int) bool {
			return compareAssignmentPriority(res.Assignments[i], res.Assignments[j])
		})
		res.Course.AssignmentCount = activeCount
		res.Course.PendingCount = pending
		out = res
		return nil
	})
	return out, err
}

// FetchRaw downloads a validated read-only file or attachment from Brightspace using the student's session.
func (m *Manager) FetchRaw(ctx context.Context, ra, relPath string) ([]byte, string, string, error) {
	if !IsAllowedFilePath(relPath) {
		return nil, "", "", errors.New("caminho de arquivo não permitido na Sala Virtual")
	}
	var (
		raw []byte
		ct  string
		cd  string
	)
	err := m.withClient(ctx, ra, func(client *http.Client, _, _ string) error {
		b, status, contentType, contentDisp, err := m.doGetBytes(ctx, client, relPath)
		if err != nil {
			return err
		}
		if status == http.StatusUnauthorized || (status == http.StatusForbidden && strings.HasPrefix(strings.ToLower(relPath), "/d2l/api/lp/")) {
			return ErrSessionExpired
		}
		if status == http.StatusNotFound {
			return ErrFileNotFound
		}
		if status >= 400 {
			return fmt.Errorf("d2l download status %d", status)
		}
		raw = b
		ct = contentType
		cd = contentDisp
		return nil
	})
	return raw, ct, cd, err
}

// IsAllowedFilePath restricts D2L file proxying strictly to read-only topic/attachment/content paths.
func IsAllowedFilePath(p string) bool {
	if p == "" || len(p) > 512 || strings.ContainsAny(p, "\r\n\x00\\") {
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
	lowerClean := strings.ToLower(cleaned)
	if strings.HasPrefix(lowerClean, "/d2l/api/le/") {
		return strings.Contains(lowerClean, "/attachments/") || strings.HasSuffix(lowerClean, "/file")
	}
	allowed := []string{
		"/content/enforced/",
		"/d2l/common/viewfile.d2lfile/",
	}
	for _, prefix := range allowed {
		if strings.HasPrefix(lowerClean, prefix) {
			return true
		}
	}
	return false
}

func (m *Manager) fetchCourses(ctx context.Context, client *http.Client) ([]CourseSummary, error) {
	var mc rawMyCoursesResp
	err := m.doGetJSON(ctx, client, "/d2l/le/manageCourses/api/mycourses?pageSize=50&sort=current&autoPinCourses=false&orgUnitTypeId=3&promotePins=true&embedDepth=0&widgetId=97", &mc)
	if err == nil && len(mc.Courses) > 0 {
		out := make([]CourseSummary, 0, len(mc.Courses))
		for _, c := range mc.Courses {
			ou := c.OrgUnitID.Int64()
			if ou <= 0 {
				continue
			}
			out = append(out, CourseSummary{
				OrgUnitID:    ou,
				Name:         strings.TrimSpace(c.Name),
				Code:         strings.TrimSpace(c.Code),
				SemesterName: strings.TrimSpace(c.SemesterName),
				IsActive:     c.IsActive,
				CanAccess:    c.CanAccessCourse,
				StartDate:    strings.TrimSpace(c.StartDate),
				EndDate:      strings.TrimSpace(c.EndDate),
				LastAccessed: strings.TrimSpace(c.LastAccessed),
				HomeURL:      fmt.Sprintf("%s/d2l/home/%d", m.baseURL, ou),
			})
		}
		return out, nil
	}
	if errors.Is(err, ErrSessionExpired) {
		return nil, err
	}

	var enr rawEnrollmentsResp
	if err2 := m.doGetJSON(ctx, client, fmt.Sprintf("/d2l/api/lp/%s/enrollments/myenrollments/", lpAPIVersion), &enr); err2 != nil {
		return nil, err2
	}
	out := make([]CourseSummary, 0, len(enr.Items))
	for _, item := range enr.Items {
		if item.OrgUnit.Type.ID != 3 || item.OrgUnit.ID <= 0 {
			continue
		}
		out = append(out, CourseSummary{
			OrgUnitID:    item.OrgUnit.ID,
			Name:         strings.TrimSpace(item.OrgUnit.Name),
			Code:         strings.TrimSpace(item.OrgUnit.Code),
			IsActive:     item.Access.IsActive,
			CanAccess:    item.Access.CanAccess,
			StartDate:    strings.TrimSpace(item.Access.StartDate),
			EndDate:      strings.TrimSpace(item.Access.EndDate),
			LastAccessed: strings.TrimSpace(item.Access.LastAccessed),
			HomeURL:      fmt.Sprintf("%s/d2l/home/%d", m.baseURL, item.OrgUnit.ID),
		})
	}
	return out, nil
}

func (m *Manager) fetchNews(ctx context.Context, client *http.Client, orgUnitID int64, orgUnitName string) ([]Announcement, error) {
	var raw []rawNewsItem
	if err := m.doGetJSON(ctx, client, fmt.Sprintf("/d2l/api/le/%s/%d/news/", leAPIVersion, orgUnitID), &raw); err != nil {
		if errors.Is(err, errToolUnavailable) {
			return []Announcement{}, nil
		}
		return nil, err
	}
	out := make([]Announcement, 0, len(raw))
	for _, n := range raw {
		if n.IsHidden {
			continue
		}
		atts := make([]FileAttachment, 0, len(n.Attachments))
		for _, a := range n.Attachments {
			atts = append(atts, FileAttachment{
				FileID:       a.FileID,
				FileName:     strings.TrimSpace(a.FileName),
				Size:         a.Size,
				DownloadPath: fmt.Sprintf("/d2l/api/le/%s/%d/news/%d/attachments/%d", leAPIVersion, orgUnitID, n.ID, a.FileID),
			})
		}
		out = append(out, Announcement{
			ID:          n.ID,
			OrgUnitID:   orgUnitID,
			OrgUnitName: orgUnitName,
			Title:       strings.TrimSpace(n.Title),
			Body:        n.Body.PlainText(),
			CreatedDate: strings.TrimSpace(n.CreatedDate),
			StartDate:   strings.TrimSpace(n.StartDate),
			Attachments: atts,
		})
	}
	return out, nil
}

func (m *Manager) fetchContentTOC(ctx context.Context, client *http.Client, orgUnitID int64) ([]ContentModule, error) {
	var toc rawTOC
	if err := m.doGetJSON(ctx, client, fmt.Sprintf("/d2l/api/le/%s/%d/content/toc", leAPIVersion, orgUnitID), &toc); err != nil {
		if errors.Is(err, errToolUnavailable) {
			return []ContentModule{}, nil
		}
		return nil, err
	}
	out := make([]ContentModule, 0, len(toc.Modules))
	for _, mod := range toc.Modules {
		out = append(out, convertModule(orgUnitID, mod))
	}
	return out, nil
}

func convertModule(orgUnitID int64, raw rawTOCModule) ContentModule {
	topics := make([]ContentTopic, 0, len(raw.Topics))
	for _, t := range raw.Topics {
		var dlPath, extURL string
		rawURL := strings.TrimSpace(t.URL)
		if strings.EqualFold(t.TypeIdentifier, "File") || strings.HasPrefix(strings.ToLower(rawURL), "/content/enforced/") {
			dlPath = fmt.Sprintf("/d2l/api/le/%s/%d/content/topics/%d/file?stream=false", leAPIVersion, orgUnitID, t.TopicID)
		} else if strings.HasPrefix(strings.ToLower(rawURL), "https://") || strings.HasPrefix(strings.ToLower(rawURL), "http://") {
			extURL = rawURL
		}
		topics = append(topics, ContentTopic{
			TopicID:          t.TopicID,
			Title:            strings.TrimSpace(t.Title),
			TypeIdentifier:   strings.TrimSpace(t.TypeIdentifier),
			URL:              rawURL,
			DownloadPath:     dlPath,
			ExternalURL:      extURL,
			Unread:           t.Unread,
			LastModifiedDate: strings.TrimSpace(t.LastModifiedDate),
		})
	}
	subMods := make([]ContentModule, 0, len(raw.Modules))
	for _, sub := range raw.Modules {
		subMods = append(subMods, convertModule(orgUnitID, sub))
	}
	return ContentModule{
		ModuleID:    raw.ModuleID,
		Title:       strings.TrimSpace(raw.Title),
		Description: raw.Description.PlainText(),
		Topics:      topics,
		Modules:     subMods,
	}
}

func (m *Manager) fetchAssignments(ctx context.Context, client *http.Client, orgUnitID int64, orgUnitName string) ([]AssignmentItem, error) {
	var folders []rawDropboxFolder
	if err := m.doGetJSON(ctx, client, fmt.Sprintf("/d2l/api/le/%s/%d/dropbox/folders/", leAPIVersion, orgUnitID), &folders); err != nil {
		if errors.Is(err, errToolUnavailable) {
			return []AssignmentItem{}, nil
		}
		return nil, err
	}

	out := make([]AssignmentItem, len(folders))
	var wg sync.WaitGroup
	sem := make(chan struct{}, 4)

	for i, f := range folders {
		i, f := i, f
		wg.Add(1)
		go func() {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()

			atts := make([]FileAttachment, 0, len(f.Attachments))
			for _, a := range f.Attachments {
				atts = append(atts, FileAttachment{
					FileID:       a.FileID,
					FileName:     strings.TrimSpace(a.FileName),
					Size:         a.Size,
					DownloadPath: fmt.Sprintf("/d2l/api/le/%s/%d/dropbox/folders/%d/attachments/%d", leAPIVersion, orgUnitID, f.ID, a.FileID),
				})
			}

			item := AssignmentItem{
				ID:             f.ID,
				OrgUnitID:      orgUnitID,
				OrgUnitName:    orgUnitName,
				Name:           strings.TrimSpace(f.Name),
				Instructions:   f.CustomInstructions.PlainText(),
				DueDate:        strings.TrimSpace(f.DueDate),
				StartDate:      strings.TrimSpace(f.Availability.StartDate),
				EndDate:        strings.TrimSpace(f.Availability.EndDate),
				OutOf:          f.Assessment.ScoreDenominator,
				Attachments:    atts,
				Status:         "not_submitted",
				StatusLabel:    "Não enviado",
				SubmittedFiles: []FileAttachment{},
				WebURL:         fmt.Sprintf("%s/d2l/lms/dropbox/user/folder_submit_files.d2l?db=%d&grpid=0&isprv=0&bp=0&ou=%d", m.baseURL, f.ID, orgUnitID),
			}

			var subs []rawEntityDropbox
			if err := m.doGetJSON(ctx, client, fmt.Sprintf("/d2l/api/le/%s/%d/dropbox/folders/%d/submissions/", leAPIVersion, orgUnitID, f.ID), &subs); err == nil && len(subs) > 0 {
				sub := subs[0]
				item.CompletionDate = strings.TrimSpace(sub.CompletionDate)
				for _, s := range sub.Submissions {
					if item.SubmissionDate == "" || s.SubmissionDate > item.SubmissionDate {
						item.SubmissionDate = strings.TrimSpace(s.SubmissionDate)
					}
					for _, sf := range s.Files {
						item.SubmittedFiles = append(item.SubmittedFiles, FileAttachment{
							FileID:   sf.FileID,
							FileName: strings.TrimSpace(sf.FileName),
							Size:     sf.Size,
						})
					}
				}
				if sub.Feedback != nil {
					item.Score = sub.Feedback.Score
					item.FeedbackText = sub.Feedback.Feedback.PlainText()
				}
				switch {
				case sub.Status == 3 || (sub.Feedback != nil && (sub.Feedback.Score != nil || item.FeedbackText != "")):
					item.Status = "graded"
					item.StatusLabel = "Avaliado"
				case len(sub.Submissions) > 0 || sub.Status == 1 || sub.Status == 2:
					item.Status = "submitted"
					item.StatusLabel = "Enviado"
				}
			}

			if deadline, ok := parseD2LTime(firstNonEmpty(item.DueDate, item.EndDate)); ok {
				item.DueAtMillis = deadline.UnixMilli()
				if item.Status == "not_submitted" && deadline.Before(time.Now().UTC()) {
					item.Status = "expired"
					item.StatusLabel = "Encerrada"
				}
			}

			out[i] = item
		}()
	}
	wg.Wait()
	return out, nil
}

func (m *Manager) fetchGrades(ctx context.Context, client *http.Client, orgUnitID int64) ([]VirtualGradeItem, error) {
	var raw []rawGradeValue
	if err := m.doGetJSON(ctx, client, fmt.Sprintf("/d2l/api/le/%s/%d/grades/values/myGradeValues/", leAPIVersion, orgUnitID), &raw); err != nil {
		return nil, err
	}
	out := make([]VirtualGradeItem, 0, len(raw))
	for _, g := range raw {
		comment := g.Comments.PlainText()
		if priv := g.PrivateComments.PlainText(); priv != "" {
			if comment != "" {
				comment += "\n" + priv
			} else {
				comment = priv
			}
		}
		out = append(out, VirtualGradeItem{
			ID:                strings.TrimSpace(g.GradeObjectIdentifier),
			Name:              strings.TrimSpace(g.GradeObjectName),
			DisplayedGrade:    strings.TrimSpace(g.DisplayedGrade),
			PointsNumerator:   g.PointsNumerator,
			PointsDenominator: g.PointsDenominator,
			Comments:          comment,
			LastModified:      strings.TrimSpace(g.LastModified),
			ReleasedDate:      strings.TrimSpace(g.ReleasedDate),
		})
	}
	return out, nil
}

func (m *Manager) fetchQuizzes(ctx context.Context, client *http.Client, orgUnitID int64, orgUnitName string) ([]QuizItem, error) {
	var raw rawQuizList
	if err := m.doGetJSON(ctx, client, fmt.Sprintf("/d2l/api/le/%s/%d/quizzes/", leAPIVersion, orgUnitID), &raw); err != nil {
		if errors.Is(err, errToolUnavailable) {
			return []QuizItem{}, nil
		}
		return nil, err
	}
	out := make([]QuizItem, 0, len(raw.Objects))
	for _, q := range raw.Objects {
		desc := q.Description.PlainText()
		if desc == "" {
			desc = q.Instructions.PlainText()
		}
		var timeLimit *int
		if q.TimeLimit != nil && q.TimeLimit.TimeLimitValue > 0 {
			v := q.TimeLimit.TimeLimitValue
			timeLimit = &v
		}
		var attempts *int
		if q.AttemptsAllowed != nil && q.AttemptsAllowed.NumberOfAttemptsAllowed > 0 {
			v := q.AttemptsAllowed.NumberOfAttemptsAllowed
			attempts = &v
		}
		out = append(out, QuizItem{
			QuizID:          q.QuizID,
			OrgUnitID:       orgUnitID,
			OrgUnitName:     orgUnitName,
			Name:            strings.TrimSpace(q.Name),
			Description:     desc,
			IsActive:        q.IsActive,
			StartDate:       strings.TrimSpace(q.StartDate),
			EndDate:         strings.TrimSpace(q.EndDate),
			DueDate:         strings.TrimSpace(q.DueDate),
			TimeLimitMins:   timeLimit,
			AttemptsAllowed: attempts,
			WebURL:          fmt.Sprintf("%s/d2l/lms/quizzing/user/quizzes_list.d2l?ou=%d", m.baseURL, orgUnitID),
		})
	}
	return out, nil
}

func (m *Manager) fetchDiscussions(ctx context.Context, client *http.Client, orgUnitID int64) ([]DiscussionForum, error) {
	var raw []rawDiscussionForum
	if err := m.doGetJSON(ctx, client, fmt.Sprintf("/d2l/api/le/%s/%d/discussions/forums/", leAPIVersion, orgUnitID), &raw); err != nil {
		return nil, err
	}
	out := make([]DiscussionForum, 0, len(raw))
	for _, f := range raw {
		out = append(out, DiscussionForum{
			ForumID:     f.ForumID,
			Name:        strings.TrimSpace(f.Name),
			Description: f.Description.PlainText(),
			WebURL:      fmt.Sprintf("%s/d2l/le/%d/discussions/List", m.baseURL, orgUnitID),
		})
	}
	return out, nil
}

func (m *Manager) fetchCourseCalendar(ctx context.Context, client *http.Client, orgUnitID int64, orgUnitName string) ([]CalendarEvent, error) {
	now := time.Now().UTC()
	start := now.Add(-14 * 24 * time.Hour).Format("2006-01-02T15:04:05.000Z")
	end := now.Add(60 * 24 * time.Hour).Format("2006-01-02T15:04:05.000Z")
	path := fmt.Sprintf("/d2l/api/le/%s/%d/calendar/events/?startDateTime=%s&endDateTime=%s",
		leAPIVersion, orgUnitID, url.QueryEscape(start), url.QueryEscape(end))
	rawBytes, status, _, _, err := m.doGetBytes(ctx, client, path)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, fmt.Errorf("d2l api status %d on %s", status, path)
	}
	var events []rawCalendarEvent
	if len(rawBytes) > 0 && rawBytes[0] == '[' {
		if err := json.Unmarshal(rawBytes, &events); err != nil {
			return nil, err
		}
	} else {
		var wrapped rawCalendarList
		if err := json.Unmarshal(rawBytes, &wrapped); err != nil {
			return nil, err
		}
		events = wrapped.Objects
	}
	out := make([]CalendarEvent, 0, len(events))
	for _, ev := range events {
		out = append(out, ev.toModel(m.baseURL, orgUnitName))
	}
	return out, nil
}

// FetchActiveCourseTopics fetches and flattens content topics passively across the student's active courses.
func (m *Manager) FetchActiveCourseTopics(ctx context.Context, ra string, courses []CourseSummary) ([]ContentTopicSnapshot, []int64, error) {
	var (
		topics       []ContentTopicSnapshot
		succeededIDs []int64
	)
	err := m.withClient(ctx, ra, func(client *http.Client, _, _ string) error {
		courseList := courses
		if len(courseList) == 0 {
			fetched, err := m.fetchCourses(ctx, client)
			if err != nil {
				return err
			}
			courseList = fetched
		}
		activeIndices := selectActiveCourseIndices(nil, courseList)
		if len(activeIndices) == 0 {
			topics = []ContentTopicSnapshot{}
			succeededIDs = []int64{}
			return nil
		}

		var (
			wg           sync.WaitGroup
			mu           sync.Mutex
			sem          = make(chan struct{}, 4)
			allTopics    []ContentTopicSnapshot
			okIDs        []int64
			sessionErr   error
			firstErr     error
			successCount int
			failCount    int
		)

		for _, idx := range activeIndices {
			course := courseList[idx]
			wg.Add(1)
			go func(c CourseSummary) {
				defer wg.Done()
				sem <- struct{}{}
				defer func() { <-sem }()

				mods, err := m.fetchContentTOC(ctx, client, c.OrgUnitID)
				if errors.Is(err, ErrSessionExpired) {
					mu.Lock()
					sessionErr = ErrSessionExpired
					mu.Unlock()
					return
				}
				mu.Lock()
				defer mu.Unlock()
				if err != nil {
					failCount++
					if firstErr == nil {
						firstErr = err
					}
					return
				}
				successCount++
				okIDs = append(okIDs, c.OrgUnitID)
				allTopics = append(allTopics, flattenContentTopics(c.OrgUnitID, c.Name, "", mods)...)
			}(course)
		}
		wg.Wait()
		if sessionErr != nil {
			return sessionErr
		}
		if successCount == 0 && failCount > 0 {
			return firstErr
		}

		sort.SliceStable(allTopics, func(i, j int) bool {
			if allTopics[i].OrgUnitID != allTopics[j].OrgUnitID {
				return allTopics[i].OrgUnitID < allTopics[j].OrgUnitID
			}
			return allTopics[i].TopicID < allTopics[j].TopicID
		})
		sort.SliceStable(okIDs, func(i, j int) bool {
			return okIDs[i] < okIDs[j]
		})
		topics = allTopics
		succeededIDs = okIDs
		return nil
	})
	return topics, succeededIDs, err
}

func flattenContentTopics(orgUnitID int64, courseName, parentModule string, mods []ContentModule) []ContentTopicSnapshot {
	var out []ContentTopicSnapshot
	for _, mod := range mods {
		modTitle := strings.TrimSpace(mod.Title)
		if modTitle == "" {
			modTitle = parentModule
		}
		for _, t := range mod.Topics {
			title := strings.TrimSpace(t.Title)
			if t.TopicID <= 0 || title == "" {
				continue
			}
			out = append(out, ContentTopicSnapshot{
				OrgUnitID:      orgUnitID,
				OrgUnitName:    courseName,
				ModuleTitle:    modTitle,
				TopicID:        t.TopicID,
				Title:          title,
				TypeIdentifier: t.TypeIdentifier,
			})
		}
		if len(mod.Modules) > 0 {
			out = append(out, flattenContentTopics(orgUnitID, courseName, modTitle, mod.Modules)...)
		}
	}
	return out
}

func selectActiveCourseIndices(sems []SemesterOption, courses []CourseSummary) []int {
	targetSem := pickLatestSemesterName(sems, courses)
	var active []int
	for i, c := range courses {
		if !c.CanAccess {
			continue
		}
		isCurrentSem := (targetSem != "" && c.SemesterName == targetSem) || (targetSem == "" && c.IsActive) || (c.SemesterName == "" && c.IsActive)
		if isCurrentSem {
			active = append(active, i)
		}
	}
	if len(active) == 0 {
		for i, c := range courses {
			if c.CanAccess && c.IsActive {
				active = append(active, i)
			}
		}
	}
	if len(active) == 0 {
		for i, c := range courses {
			if c.CanAccess {
				active = append(active, i)
			}
		}
	}
	return active
}

func pickLatestSemesterName(sems []SemesterOption, courses []CourseSummary) string {
	bestName := ""
	bestKey := ""
	consider := func(name string) {
		name = strings.TrimSpace(name)
		if name == "" {
			return
		}
		key := semesterSortKey(name)
		if key > bestKey {
			bestKey = key
			bestName = name
		}
	}
	for _, c := range courses {
		if c.CanAccess {
			consider(c.SemesterName)
		}
	}
	if bestName != "" {
		return bestName
	}
	for _, s := range sems {
		consider(s.Name)
	}
	return bestName
}

func semesterSortKey(name string) string {
	// Handles "2/2026" -> "2026-02" and "10/2026" -> "2026-10" so latest semester compares cleanly.
	parts := strings.Split(name, "/")
	if len(parts) == 2 {
		semStr := strings.TrimSpace(parts[0])
		yearStr := strings.TrimSpace(parts[1])
		if semNum, err := strconv.Atoi(semStr); err == nil {
			return fmt.Sprintf("%s-%02d", yearStr, semNum)
		}
		return yearStr + "-" + semStr
	}
	return name
}

func compareQuizPriority(a, b QuizItem) bool {
	if a.IsActive != b.IsActive {
		return a.IsActive
	}
	aDue := firstNonEmpty(a.DueDate, a.EndDate)
	bDue := firstNonEmpty(b.DueDate, b.EndDate)
	if aDue != bDue {
		if aDue == "" {
			return false
		}
		if bDue == "" {
			return true
		}
		return aDue < bDue
	}
	return a.Name < b.Name
}

func statusPriorityRank(status string) int {
	switch status {
	case "not_submitted":
		return 0
	case "submitted":
		return 1
	case "graded":
		return 2
	case "expired":
		return 3
	default:
		return 4
	}
}

func compareAssignmentPriority(a, b AssignmentItem) bool {
	ra, rb := statusPriorityRank(a.Status), statusPriorityRank(b.Status)
	if ra != rb {
		return ra < rb
	}
	aDue := firstNonEmpty(a.DueDate, a.EndDate)
	bDue := firstNonEmpty(b.DueDate, b.EndDate)
	if aDue != bDue {
		if aDue == "" {
			return false
		}
		if bDue == "" {
			return true
		}
		return aDue < bDue
	}
	return a.Name < b.Name
}

// ParseD2LTime parses Brightspace ISO timestamps (with or without explicit timezone offset, defaulting naive timestamps to America/Sao_Paulo).
func ParseD2LTime(raw string) (time.Time, bool) {
	return parseD2LTime(raw)
}

func parseD2LTime(raw string) (time.Time, bool) {
	s := strings.TrimSpace(raw)
	if s == "" {
		return time.Time{}, false
	}
	for _, layout := range []string{
		time.RFC3339Nano,
		time.RFC3339,
		"2006-01-02T15:04:05.000Z",
	} {
		if t, err := time.Parse(layout, s); err == nil {
			return t.UTC(), true
		}
	}
	for _, layout := range []string{
		"2006-01-02T15:04:05.999999999",
		"2006-01-02T15:04:05",
		"2006-01-02 15:04:05",
	} {
		if t, err := time.ParseInLocation(layout, s, saoPauloTZ); err == nil {
			return t.UTC(), true
		}
	}
	return time.Time{}, false
}

func newsSortKey(a Announcement) string {
	return firstNonEmpty(a.StartDate, a.CreatedDate)
}

func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return ""
}

func htmlToPlainText(rawHTML string) string {
	trimmed := strings.TrimSpace(rawHTML)
	if trimmed == "" {
		return ""
	}
	replacer := strings.NewReplacer(
		"<br>", "\n", "<br/>", "\n", "<br />", "\n",
		"</p>", "\n", "</div>", "\n", "</li>", "\n", "</tr>", "\n",
	)
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(replacer.Replace(trimmed)))
	if err != nil {
		return trimmed
	}
	doc.Find("script,style").Remove()
	text := doc.Text()
	lines := strings.Split(text, "\n")
	cleaned := make([]string, 0, len(lines))
	prevBlank := false
	for _, line := range lines {
		line = strings.Join(strings.Fields(strings.TrimSpace(line)), " ")
		if line == "" {
			if !prevBlank && len(cleaned) > 0 {
				cleaned = append(cleaned, "")
			}
			prevBlank = true
			continue
		}
		cleaned = append(cleaned, line)
		prevBlank = false
	}
	return strings.TrimSpace(strings.Join(cleaned, "\n"))
}

// --- Internal D2L API JSON structures ---

type flexID string

func (f *flexID) UnmarshalJSON(data []byte) error {
	s := strings.TrimSpace(string(data))
	if s == "null" || s == "" {
		*f = ""
		return nil
	}
	if strings.HasPrefix(s, "\"") {
		var str string
		if err := json.Unmarshal(data, &str); err != nil {
			return err
		}
		*f = flexID(str)
		return nil
	}
	*f = flexID(s)
	return nil
}

func (f flexID) String() string { return string(f) }
func (f flexID) Int64() int64 {
	v, _ := strconv.ParseInt(strings.TrimSpace(string(f)), 10, 64)
	return v
}

type richText struct {
	Text string `json:"Text"`
	HTML string `json:"Html"`
}

func (r richText) PlainText() string {
	normalized := strings.ReplaceAll(strings.ReplaceAll(r.Text, "\r\n", "\n"), "\r", "\n")
	if t := strings.TrimSpace(normalized); t != "" {
		return t
	}
	return htmlToPlainText(r.HTML)
}

type whoamiPayload struct {
	Identifier string `json:"Identifier"`
	FirstName  string `json:"FirstName"`
	LastName   string `json:"LastName"`
	UniqueName string `json:"UniqueName"`
}

type rawSemester struct {
	OrgUnitID flexID `json:"OrgUnitId"`
	Name      string `json:"Name"`
	Code      string `json:"Code"`
}

type rawMyCoursesResp struct {
	Courses []struct {
		OrgUnitID       flexID `json:"OrgUnitId"`
		Name            string `json:"Name"`
		Code            string `json:"Code"`
		IsActive        bool   `json:"IsActive"`
		StartDate       string `json:"StartDate"`
		EndDate         string `json:"EndDate"`
		SemesterName    string `json:"SemesterName"`
		CanAccessCourse bool   `json:"CanAccessCourse"`
		LastAccessed    string `json:"LastAccessed"`
	} `json:"Courses"`
}

type rawEnrollmentsResp struct {
	Items []struct {
		OrgUnit struct {
			ID   int64  `json:"Id"`
			Name string `json:"Name"`
			Code string `json:"Code"`
			Type struct {
				ID int `json:"Id"`
			} `json:"Type"`
		} `json:"OrgUnit"`
		Access struct {
			IsActive     bool   `json:"IsActive"`
			StartDate    string `json:"StartDate"`
			EndDate      string `json:"EndDate"`
			CanAccess    bool   `json:"CanAccess"`
			LastAccessed string `json:"LastAccessed"`
		} `json:"Access"`
	} `json:"Items"`
}

type rawAttachment struct {
	FileID   int64  `json:"FileId"`
	FileName string `json:"FileName"`
	Size     int64  `json:"Size"`
}

type rawNewsItem struct {
	ID          int64           `json:"Id"`
	Title       string          `json:"Title"`
	Body        richText        `json:"Body"`
	CreatedDate string          `json:"CreatedDate"`
	StartDate   string          `json:"StartDate"`
	IsHidden    bool            `json:"IsHidden"`
	Attachments []rawAttachment `json:"Attachments"`
}

type rawCalendarEvent struct {
	CalendarEventID      int64  `json:"CalendarEventId"`
	OrgUnitID            int64  `json:"OrgUnitId"`
	Title                string `json:"Title"`
	Description          string `json:"Description"`
	StartDateTime        string `json:"StartDateTime"`
	EndDateTime          string `json:"EndDateTime"`
	IsAllDayEvent        bool   `json:"IsAllDayEvent"`
	CalendarEventViewURL string `json:"CalendarEventViewUrl"`
}

func (ev rawCalendarEvent) toModel(baseURL, orgUnitName string) CalendarEvent {
	webURL := strings.TrimSpace(ev.CalendarEventViewURL)
	if strings.HasPrefix(webURL, "/") {
		webURL = baseURL + webURL
	}
	return CalendarEvent{
		ID:            ev.CalendarEventID,
		OrgUnitID:     ev.OrgUnitID,
		OrgUnitName:   orgUnitName,
		Title:         strings.TrimSpace(ev.Title),
		Description:   htmlToPlainText(ev.Description),
		StartDateTime: strings.TrimSpace(ev.StartDateTime),
		EndDateTime:   strings.TrimSpace(ev.EndDateTime),
		AllDay:        ev.IsAllDayEvent,
		WebURL:        webURL,
	}
}

type rawCalendarList struct {
	Objects []rawCalendarEvent `json:"Objects"`
}

type rawTOCTopic struct {
	TopicID          int64  `json:"TopicId"`
	Title            string `json:"Title"`
	TypeIdentifier   string `json:"TypeIdentifier"`
	URL              string `json:"Url"`
	Unread           bool   `json:"Unread"`
	LastModifiedDate string `json:"LastModifiedDate"`
}

type rawTOCModule struct {
	ModuleID    int64          `json:"ModuleId"`
	Title       string         `json:"Title"`
	Description richText       `json:"Description"`
	Topics      []rawTOCTopic  `json:"Topics"`
	Modules     []rawTOCModule `json:"Modules"`
}

type rawTOC struct {
	Modules []rawTOCModule `json:"Modules"`
}

type rawDropboxFolder struct {
	ID                 int64           `json:"Id"`
	Name               string          `json:"Name"`
	CustomInstructions richText        `json:"CustomInstructions"`
	Attachments        []rawAttachment `json:"Attachments"`
	DueDate            string          `json:"DueDate"`
	Availability       struct {
		StartDate string `json:"StartDate"`
		EndDate   string `json:"EndDate"`
	} `json:"Availability"`
	Assessment struct {
		ScoreDenominator *float64 `json:"ScoreDenominator"`
	} `json:"Assessment"`
}

type rawEntityDropbox struct {
	Status   int `json:"Status"`
	Feedback *struct {
		Score    *float64 `json:"Score"`
		Feedback richText `json:"Feedback"`
	} `json:"Feedback"`
	Submissions []struct {
		ID             int64           `json:"Id"`
		SubmissionDate string          `json:"SubmissionDate"`
		Files          []rawAttachment `json:"Files"`
	} `json:"Submissions"`
	CompletionDate string `json:"CompletionDate"`
}

type rawGradeValue struct {
	GradeObjectIdentifier string   `json:"GradeObjectIdentifier"`
	GradeObjectName       string   `json:"GradeObjectName"`
	PointsNumerator       *float64 `json:"PointsNumerator"`
	PointsDenominator     *float64 `json:"PointsDenominator"`
	DisplayedGrade        string   `json:"DisplayedGrade"`
	Comments              richText `json:"Comments"`
	PrivateComments       richText `json:"PrivateComments"`
	LastModified          string   `json:"LastModified"`
	ReleasedDate          string   `json:"ReleasedDate"`
}

type quizRichText struct {
	text richText
}

func (q *quizRichText) UnmarshalJSON(data []byte) error {
	var nested struct {
		Text richText `json:"Text"`
	}
	if err := json.Unmarshal(data, &nested); err == nil && (nested.Text.Text != "" || nested.Text.HTML != "") {
		q.text = nested.Text
		return nil
	}
	var direct richText
	if err := json.Unmarshal(data, &direct); err == nil {
		q.text = direct
	}
	return nil
}

func (q quizRichText) PlainText() string {
	return q.text.PlainText()
}

type rawQuizList struct {
	Objects []struct {
		QuizID       int64        `json:"QuizId"`
		Name         string       `json:"Name"`
		Description  quizRichText `json:"Description"`
		Instructions quizRichText `json:"Instructions"`
		IsActive     bool         `json:"IsActive"`
		StartDate    string       `json:"StartDate"`
		EndDate      string       `json:"EndDate"`
		DueDate      string       `json:"DueDate"`
		TimeLimit    *struct {
			TimeLimitValue int `json:"TimeLimitValue"`
		} `json:"TimeLimit"`
		AttemptsAllowed *struct {
			NumberOfAttemptsAllowed int `json:"NumberOfAttemptsAllowed"`
		} `json:"AttemptsAllowed"`
	} `json:"Objects"`
}

type rawDiscussionForum struct {
	ForumID     int64    `json:"ForumId"`
	Name        string   `json:"Name"`
	Description richText `json:"Description"`
}
