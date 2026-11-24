package d2l

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

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
			wg         sync.WaitGroup
			semesters  = []SemesterOption{}
			courses    = []CourseSummary{}
			coursesErr error
			globalNews = []Announcement{}
			errMu      sync.Mutex
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
		if courses == nil {
			courses = []CourseSummary{}
		}
		res.Courses = courses

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
			allAssigns    = []AssignmentItem{}
			allQuizzes    = []QuizItem{}
			succeededIDs  = []int64{}
			calendarItems = []CalendarEvent{}
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
			p := fmt.Sprintf("/d2l/api/le/%s/calendar/events/myEvents/?startDateTime=%s&endDateTime=%s&orgUnitIdsCSV=%s",
				leAPIVersion, url.QueryEscape(start), url.QueryEscape(end), url.QueryEscape(strings.Join(orgIDs, ",")))
			var calResp rawCalendarList
			if err := m.doGetJSON(ctx, client, p, &calResp); err == nil {
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
			assignErr  error
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
			if e == nil && mods != nil {
				res.Modules = mods
			}
		}()
		go func() {
			defer wg.Done()
			assigns, e := m.fetchAssignments(ctx, client, orgUnitID, course.Name)
			recordSessionErr(e)
			if e != nil {
				assignErr = e
				return
			}
			if assigns != nil {
				res.Assignments = assigns
			}
		}()
		go func() {
			defer wg.Done()
			grades, e := m.fetchGrades(ctx, client, orgUnitID)
			recordSessionErr(e)
			if e == nil && grades != nil {
				res.Grades = grades
			}
		}()
		go func() {
			defer wg.Done()
			news, e := m.fetchNews(ctx, client, orgUnitID, course.Name)
			recordSessionErr(e)
			if e == nil && news != nil {
				res.Announcements = news
			}
		}()
		go func() {
			defer wg.Done()
			quizzes, e := m.fetchQuizzes(ctx, client, orgUnitID, course.Name)
			recordSessionErr(e)
			if e == nil && quizzes != nil {
				res.Quizzes = quizzes
			}
		}()
		go func() {
			defer wg.Done()
			forums, e := m.fetchDiscussions(ctx, client, orgUnitID)
			recordSessionErr(e)
			if e == nil && forums != nil {
				res.Discussions = forums
			}
		}()
		go func() {
			defer wg.Done()
			cal, e := m.fetchCourseCalendar(ctx, client, orgUnitID, course.Name)
			recordSessionErr(e)
			if e == nil && cal != nil {
				res.Calendar = cal
			}
		}()

		wg.Wait()
		if sessionErr != nil {
			return sessionErr
		}
		if assignErr != nil {
			return assignErr
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
		m.reconcileCachedOverviewWithCourse(ra, res)
		out = res
		return nil
	})
	return out, err
}

func (m *Manager) reconcileCachedOverviewWithCourse(ra string, detail CourseDetailResponse) {
	m.overviewMu.Lock()
	defer m.overviewMu.Unlock()

	cached, ok := m.overviewCache[ra]
	if !ok || !time.Now().Before(cached.expiresAt) {
		return
	}

	courseID := detail.Course.OrgUnitID
	inScope := false
	for _, idx := range selectActiveCourseIndices(cached.resp.Semesters, cached.resp.Courses) {
		if idx >= 0 && idx < len(cached.resp.Courses) && cached.resp.Courses[idx].OrgUnitID == courseID {
			inScope = true
			break
		}
	}
	if !inScope {
		for _, a := range cached.resp.UpcomingAssignments {
			if a.OrgUnitID == courseID {
				inScope = true
				break
			}
		}
	}
	if !inScope {
		for _, c := range cached.resp.Courses {
			if c.OrgUnitID == courseID && (c.AssignmentCount > 0 || c.PendingCount > 0) {
				inScope = true
				break
			}
		}
	}
	if !inScope {
		return
	}

	updated := cached.resp
	updatedCourses := make([]CourseSummary, len(cached.resp.Courses))
	copy(updatedCourses, cached.resp.Courses)
	for i := range updatedCourses {
		if updatedCourses[i].OrgUnitID == courseID {
			updatedCourses[i].AssignmentCount = detail.Course.AssignmentCount
			updatedCourses[i].PendingCount = detail.Course.PendingCount
		}
	}
	updated.Courses = updatedCourses

	updatedAssigns := make([]AssignmentItem, 0, len(cached.resp.UpcomingAssignments)+len(detail.Assignments))
	for _, a := range cached.resp.UpcomingAssignments {
		if a.OrgUnitID != courseID {
			updatedAssigns = append(updatedAssigns, a)
		}
	}
	for _, a := range detail.Assignments {
		if a.Status != "expired" {
			updatedAssigns = append(updatedAssigns, a)
		}
	}
	sort.SliceStable(updatedAssigns, func(i, j int) bool {
		return compareAssignmentPriority(updatedAssigns[i], updatedAssigns[j])
	})
	updated.UpcomingAssignments = updatedAssigns

	m.overviewCache[ra] = cachedOverview{
		resp:      updated,
		expiresAt: cached.expiresAt,
	}
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
