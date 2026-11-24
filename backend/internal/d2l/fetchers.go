package d2l

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"sync"
	"time"
)

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

	visibleFolders := make([]rawDropboxFolder, 0, len(folders))
	for _, f := range folders {
		if f.IsHidden {
			continue
		}
		visibleFolders = append(visibleFolders, f)
	}

	out := make([]AssignmentItem, len(visibleFolders))
	var (
		wg         sync.WaitGroup
		errMu      sync.Mutex
		sessionErr error
		firstErr   error
		sem        = make(chan struct{}, 4)
	)

	for i, f := range visibleFolders {
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

			subsPath := fmt.Sprintf("/d2l/api/le/%s/%d/dropbox/folders/%d/submissions/", leAPIVersion, orgUnitID, f.ID)
			var subs []rawEntityDropbox
			err := m.doGetJSON(ctx, client, subsPath, &subs)
			if err != nil && !errors.Is(err, ErrSessionExpired) && !errors.Is(err, errToolUnavailable) {
				timer := time.NewTimer(150 * time.Millisecond)
				select {
				case <-ctx.Done():
					timer.Stop()
					err = ctx.Err()
				case <-timer.C:
					subs = nil
					err = m.doGetJSON(ctx, client, subsPath, &subs)
				}
			}
			if errors.Is(err, ErrSessionExpired) {
				errMu.Lock()
				sessionErr = ErrSessionExpired
				errMu.Unlock()
				return
			}
			if err != nil && !errors.Is(err, errToolUnavailable) {
				errMu.Lock()
				if firstErr == nil {
					firstErr = err
				}
				errMu.Unlock()
				return
			}

			if err == nil && len(subs) > 0 {
				var (
					hasGraded    bool
					hasSubmitted bool
				)
				for _, sub := range subs {
					if cd := strings.TrimSpace(sub.CompletionDate); cd != "" && (item.CompletionDate == "" || cd > item.CompletionDate) {
						item.CompletionDate = cd
					}
					for _, s := range sub.Submissions {
						if subDate := strings.TrimSpace(s.SubmissionDate); subDate != "" && (item.SubmissionDate == "" || subDate > item.SubmissionDate) {
							item.SubmissionDate = subDate
						}
						for _, sf := range s.Files {
							if sf.IsDeleted {
								continue
							}
							var dlPath string
							if s.ID > 0 && sf.FileID > 0 {
								dlPath = fmt.Sprintf("/d2l/api/le/%s/%d/dropbox/folders/%d/submissions/%d/files/%d", leAPIVersion, orgUnitID, f.ID, s.ID, sf.FileID)
							}
							item.SubmittedFiles = append(item.SubmittedFiles, FileAttachment{
								FileID:       sf.FileID,
								FileName:     strings.TrimSpace(sf.FileName),
								Size:         sf.Size,
								DownloadPath: dlPath,
							})
						}
					}
					var entryFeedbackText string
					if sub.Feedback != nil {
						if sub.Feedback.Score != nil {
							item.Score = sub.Feedback.Score
						}
						entryFeedbackText = sub.Feedback.Feedback.PlainText()
						if entryFeedbackText != "" {
							item.FeedbackText = entryFeedbackText
						}
					}
					if sub.Status == 3 || (sub.Feedback != nil && (sub.Feedback.Score != nil || entryFeedbackText != "")) {
						hasGraded = true
					}
					if len(sub.Submissions) > 0 || sub.Status == 1 || sub.Status == 2 || strings.TrimSpace(sub.CompletionDate) != "" {
						hasSubmitted = true
					}
				}
				switch {
				case hasGraded:
					item.Status = "graded"
					item.StatusLabel = "Avaliado"
				case hasSubmitted:
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
	if sessionErr != nil {
		return nil, sessionErr
	}
	if firstErr != nil {
		return nil, firstErr
	}
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
	p := fmt.Sprintf("/d2l/api/le/%s/%d/calendar/events/?startDateTime=%s&endDateTime=%s",
		leAPIVersion, orgUnitID, url.QueryEscape(start), url.QueryEscape(end))
	rawBytes, status, _, _, err := m.doGetBytes(ctx, client, p)
	if err != nil {
		return nil, err
	}
	if status >= 400 {
		return nil, fmt.Errorf("d2l api status %d on %s", status, p)
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
	topics := []ContentTopicSnapshot{}
	succeededIDs := []int64{}
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
			allTopics    = []ContentTopicSnapshot{}
			okIDs        = []int64{}
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
