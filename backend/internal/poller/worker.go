package poller

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strconv"
	"strings"
	"sync"
	"time"

	"unicep-portal/internal/crypto"
	"unicep-portal/internal/d2l"
	"unicep-portal/internal/portal"
	"unicep-portal/internal/storage"
)

// Worker periodically polls UNICEP grades and absences and persists change notifications.
type Worker struct {
	store          *storage.Store
	crypto         *crypto.Service
	portal         *portal.Manager
	d2l            *d2l.Manager
	interval       time.Duration
	notifyAbsences bool
}

// NewWorker creates a background grade and virtual-assignment poller.
func NewWorker(store *storage.Store, cry *crypto.Service, pm *portal.Manager, dm *d2l.Manager, interval time.Duration, notifyAbsences bool) *Worker {
	return &Worker{
		store:          store,
		crypto:         cry,
		portal:         pm,
		d2l:            dm,
		interval:       interval,
		notifyAbsences: notifyAbsences,
	}
}

// Start launches the polling ticker until ctx is cancelled.
func (w *Worker) Start(ctx context.Context) {
	ticker := time.NewTicker(w.interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			w.runCycle(ctx)
		}
	}
}

func (w *Worker) runCycle(ctx context.Context) {
	users, err := w.store.ListPollingUsers()
	if err != nil {
		log.Printf("poller: list users error: %v", err)
		return
	}
	sem := make(chan struct{}, 3)
	var wg sync.WaitGroup
	for _, u := range users {
		if ctx.Err() != nil {
			break
		}
		u := u
		wg.Add(1)
		go func() {
			defer wg.Done()
			select {
			case sem <- struct{}{}:
				defer func() { <-sem }()
			case <-ctx.Done():
				return
			}
			if _, err := w.syncUserWithRecord(ctx, u.RA, u.D2LPollingEnabled, false, false); err != nil {
				log.Printf("poller: sync user %s error: %v", u.RA, err)
			}
		}()
	}
	wg.Wait()
}

// SyncUser fetches the latest semester grades and report card for ra, saves the snapshot, and returns new notifications.
// When seedOnly is true and no prior snapshot exists, no notifications are emitted.
func (w *Worker) SyncUser(ctx context.Context, ra string, seedOnly bool) (int, error) {
	return w.SyncUserWithOptions(ctx, ra, seedOnly, false)
}

// SyncUserWithOptions runs legacy and D2L synchronization for ra, optionally forcing a fresh D2L fetch bypassing cache.
func (w *Worker) SyncUserWithOptions(ctx context.Context, ra string, seedOnly, forceD2LRefresh bool) (int, error) {
	d2lEnabled := true
	if u, err := w.store.GetUser(ra); err == nil && u != nil {
		d2lEnabled = u.D2LPollingEnabled
	}
	return w.syncUserWithRecord(ctx, ra, d2lEnabled, seedOnly, forceD2LRefresh)
}

func (w *Worker) syncUserWithRecord(ctx context.Context, ra string, d2lEnabled, seedOnly, forceD2LRefresh bool) (int, error) {
	legacyCtx, cancelLegacy := context.WithTimeout(ctx, 45*time.Second)
	defer cancelLegacy()
	created, legacyErr := w.syncLegacy(legacyCtx, ra, seedOnly)

	if d2lEnabled && w.d2l != nil {
		d2lCtx, cancelD2L := context.WithTimeout(ctx, 60*time.Second)
		defer cancelD2L()
		d2lCreated, d2lErr := w.syncD2L(d2lCtx, ra, seedOnly, forceD2LRefresh)
		if d2lErr != nil {
			log.Printf("poller: sync D2L %s error: %v", ra, d2lErr)
		} else {
			created += d2lCreated
		}
	}
	return created, legacyErr
}

func (w *Worker) syncLegacy(ctx context.Context, ra string, seedOnly bool) (int, error) {
	gradesSelectorHTML, _, _, err := w.portal.FetchHTML(ctx, ra, "/ambiente_aluno/consulta_notas_primeira.asp")
	if err != nil {
		return 0, fmt.Errorf("fetch grade periods: %w", err)
	}
	gradePeriods, err := portal.ParsePeriodOptions(gradesSelectorHTML, "/ambiente_aluno/relatorios/consulta_notas.asp?matr=")
	if err != nil || len(gradePeriods) == 0 {
		return 0, err
	}

	reportSelectorHTML, _, _, err := w.portal.FetchHTML(ctx, ra, "/ambiente_aluno/relatorios/boletim_geral.asp")
	if err != nil {
		return 0, fmt.Errorf("fetch report periods: %w", err)
	}
	reportPeriods, err := portal.ParsePeriodOptions(reportSelectorHTML, "/ambiente_aluno/relatorios/boletim_detalhado.asp")
	if err != nil {
		return 0, err
	}

	latestPeriod := gradePeriods[0]
	gradesHTML, _, _, err := w.portal.FetchHTML(ctx, ra, latestPeriod.Query)
	if err != nil {
		return 0, fmt.Errorf("fetch grades: %w", err)
	}
	currGrades, err := portal.ParseGrades(gradesHTML)
	if err != nil {
		return 0, err
	}

	var currReport portal.ReportCardResponse
	if len(reportPeriods) > 0 {
		reportHTML, _, _, err := w.portal.FetchHTML(ctx, ra, reportPeriods[0].Query)
		if err == nil {
			currReport, _ = portal.ParseReportCard(reportHTML, reportPeriods[0].ID)
		}
	}

	gradesBytes, _ := json.Marshal(currGrades)
	reportBytes, _ := json.Marshal(currReport.Items)

	prevSnap, err := w.store.GetSnapshot(ra, latestPeriod.ID)
	if err != nil {
		return 0, err
	}

	if prevSnap == nil {
		if err := w.store.SaveSnapshot(storage.SnapshotRecord{
			RA:         ra,
			PeriodKey:  latestPeriod.ID,
			GradesJSON: string(gradesBytes),
			ReportJSON: string(reportBytes),
		}); err != nil {
			return 0, err
		}
		return 0, nil
	}

	if seedOnly {
		return 0, nil
	}

	var prevGrades []portal.PartialGradeItem
	var prevReport []portal.ReportCardItem
	_ = json.Unmarshal([]byte(prevSnap.GradesJSON), &prevGrades)
	_ = json.Unmarshal([]byte(prevSnap.ReportJSON), &prevReport)

	events := DiffSnapshots(ra, latestPeriod.ID, prevGrades, currGrades, prevReport, currReport.Items, w.notifyAbsences)
	created := 0
	for _, ev := range events {
		if id, err := w.store.InsertNotification(ev); err == nil && id > 0 {
			created++
		}
	}

	if err := w.store.SaveSnapshot(storage.SnapshotRecord{
		RA:         ra,
		PeriodKey:  latestPeriod.ID,
		GradesJSON: string(gradesBytes),
		ReportJSON: string(reportBytes),
	}); err != nil {
		return created, err
	}
	return created, nil
}

func (w *Worker) syncD2L(ctx context.Context, ra string, seedOnly, forceRefresh bool) (int, error) {
	if w.d2l == nil {
		return 0, nil
	}
	ov, err := w.d2l.GetOverviewWithOptions(ctx, ra, forceRefresh)
	if err != nil {
		return 0, err
	}
	if len(ov.Courses) > 0 && len(ov.SucceededCourseIDs) == 0 && !ov.Complete {
		return 0, fmt.Errorf("d2l overview incomplete for %s", ra)
	}

	created := 0

	// 1. Atividades (Dropbox)
	prevAssignJSON, err := w.store.GetD2LSnapshot(ra)
	if err != nil {
		return 0, err
	}
	var prevAssigns []d2l.AssignmentItem
	if prevAssignJSON != "" {
		_ = json.Unmarshal([]byte(prevAssignJSON), &prevAssigns)
	}
	mergedAssigns := mergeByCourse(prevAssigns, ov.UpcomingAssignments, ov.Complete, ov.SucceededCourseIDs, func(a d2l.AssignmentItem) int64 {
		return a.OrgUnitID
	})
	currAssignBytes, _ := json.Marshal(mergedAssigns)
	if prevAssignJSON == "" {
		if err := w.store.SaveD2LSnapshot(ra, string(currAssignBytes)); err != nil {
			return 0, err
		}
	} else if !seedOnly {
		for _, ev := range DiffD2LAssignments(ra, prevAssigns, mergedAssigns) {
			if id, err := w.store.InsertNotification(ev); err == nil && id > 0 {
				created++
			}
		}
		if err := w.store.SaveD2LSnapshot(ra, string(currAssignBytes)); err != nil {
			return created, err
		}
	}

	// 2. Avisos (News)
	currAnns := ov.Announcements
	if currAnns == nil {
		currAnns = []d2l.Announcement{}
	}
	currAnnBytes, _ := json.Marshal(currAnns)
	prevAnnJSON, err := w.store.GetD2LAnnouncementsSnapshot(ra)
	if err != nil {
		return created, err
	}
	if prevAnnJSON == "" {
		if err := w.store.SaveD2LAnnouncementsSnapshot(ra, string(currAnnBytes)); err != nil {
			return created, err
		}
	} else if !seedOnly {
		var prevAnns []d2l.Announcement
		_ = json.Unmarshal([]byte(prevAnnJSON), &prevAnns)
		for _, ev := range DiffD2LAnnouncements(ra, prevAnns, currAnns) {
			if id, err := w.store.InsertNotification(ev); err == nil && id > 0 {
				created++
			}
		}
		if err := w.store.SaveD2LAnnouncementsSnapshot(ra, string(currAnnBytes)); err != nil {
			return created, err
		}
	}

	// 3. Questionários (Quizzes)
	prevQuizzesJSON, err := w.store.GetD2LQuizzesSnapshot(ra)
	if err != nil {
		return created, err
	}
	var prevQuizzes []d2l.QuizItem
	if prevQuizzesJSON != "" {
		_ = json.Unmarshal([]byte(prevQuizzesJSON), &prevQuizzes)
	}
	mergedQuizzes := mergeByCourse(prevQuizzes, ov.UpcomingQuizzes, ov.Complete, ov.SucceededCourseIDs, func(q d2l.QuizItem) int64 {
		return q.OrgUnitID
	})
	currQuizzesBytes, _ := json.Marshal(mergedQuizzes)
	if prevQuizzesJSON == "" {
		if err := w.store.SaveD2LQuizzesSnapshot(ra, string(currQuizzesBytes)); err != nil {
			return created, err
		}
	} else if !seedOnly {
		for _, ev := range DiffD2LQuizzes(ra, prevQuizzes, mergedQuizzes) {
			if id, err := w.store.InsertNotification(ev); err == nil && id > 0 {
				created++
			}
		}
		if err := w.store.SaveD2LQuizzesSnapshot(ra, string(currQuizzesBytes)); err != nil {
			return created, err
		}
	}

	// 4. Materiais / Conteúdo (Content TOC)
	topics, topicSucceededIDs, topicsErr := w.d2l.FetchActiveCourseTopics(ctx, ra, ov.Courses)
	if topicsErr != nil {
		log.Printf("poller: fetch D2L topics %s error: %v", ra, topicsErr)
		return created, nil
	}
	prevContentJSON, err := w.store.GetD2LContentSnapshot(ra)
	if err != nil {
		return created, err
	}
	var prevTopics []d2l.ContentTopicSnapshot
	if prevContentJSON != "" {
		_ = json.Unmarshal([]byte(prevContentJSON), &prevTopics)
	}
	topicsComplete := ov.Complete && len(topicSucceededIDs) >= len(ov.SucceededCourseIDs)
	mergedTopics := mergeByCourse(prevTopics, topics, topicsComplete, topicSucceededIDs, func(t d2l.ContentTopicSnapshot) int64 {
		return t.OrgUnitID
	})
	currContentBytes, _ := json.Marshal(mergedTopics)
	if prevContentJSON == "" {
		if err := w.store.SaveD2LContentSnapshot(ra, string(currContentBytes)); err != nil {
			return created, err
		}
	} else if !seedOnly {
		for _, ev := range DiffD2LContent(ra, prevTopics, mergedTopics) {
			if id, err := w.store.InsertNotification(ev); err == nil && id > 0 {
				created++
			}
		}
		if err := w.store.SaveD2LContentSnapshot(ra, string(currContentBytes)); err != nil {
			return created, err
		}
	}

	return created, nil
}

func mergeByCourse[T any](prev, curr []T, complete bool, succeededIDs []int64, orgID func(T) int64) []T {
	if complete {
		if curr == nil {
			return []T{}
		}
		return curr
	}
	okSet := make(map[int64]bool, len(succeededIDs))
	for _, id := range succeededIDs {
		okSet[id] = true
	}
	merged := make([]T, 0, len(prev)+len(curr))
	for _, item := range prev {
		if !okSet[orgID(item)] {
			merged = append(merged, item)
		}
	}
	for _, item := range curr {
		if okSet[orgID(item)] {
			merged = append(merged, item)
		}
	}
	return merged
}

// DiffD2LAssignments compares previous and current D2L assignments and emits notifications for newly posted pending items.
func DiffD2LAssignments(ra string, prev, curr []d2l.AssignmentItem) []storage.Notification {
	seen := make(map[string]struct{}, len(prev))
	for _, item := range prev {
		seen[fmt.Sprintf("%d:%d", item.OrgUnitID, item.ID)] = struct{}{}
	}
	brt := time.FixedZone("BRT", -3*3600)
	var out []storage.Notification
	for _, item := range curr {
		if item.Status == "expired" || item.Status == "submitted" || item.Status == "graded" {
			continue
		}
		key := fmt.Sprintf("%d:%d", item.OrgUnitID, item.ID)
		if _, ok := seen[key]; ok {
			continue
		}
		dueLabel := "Nova atividade postada"
		if item.DueAtMillis > 0 {
			dueLabel = "Prazo: " + time.UnixMilli(item.DueAtMillis).In(brt).Format("02/01 às 15:04")
		} else if trimmed := strings.TrimSpace(item.DueDate); trimmed != "" {
			dueLabel = "Prazo: " + trimmed
		}
		out = append(out, storage.Notification{
			RA:             ra,
			PeriodKey:      fmt.Sprintf("%d", item.OrgUnitID),
			DisciplineCode: key,
			DisciplineName: item.OrgUnitName,
			Field:          item.Name,
			OldValue:       "",
			NewValue:       dueLabel,
			Kind:           "d2l_assignment",
		})
	}
	return out
}

// DiffD2LAnnouncements compares previous and current D2L announcements and emits notifications for newly posted items.
func DiffD2LAnnouncements(ra string, prev, curr []d2l.Announcement) []storage.Notification {
	seen := make(map[string]struct{}, len(prev))
	for _, item := range prev {
		seen[fmt.Sprintf("%d:%d", item.OrgUnitID, item.ID)] = struct{}{}
	}
	var out []storage.Notification
	for _, item := range curr {
		key := fmt.Sprintf("%d:%d", item.OrgUnitID, item.ID)
		if _, ok := seen[key]; ok {
			continue
		}
		title := strings.TrimSpace(item.Title)
		if title == "" {
			title = "Novo aviso"
		}
		preview := truncatePreview(item.Body, 160)
		if preview == "" && len(item.Attachments) > 0 {
			preview = fmt.Sprintf("%d anexo(s)", len(item.Attachments))
		}
		disciplineName := strings.TrimSpace(item.OrgUnitName)
		if disciplineName == "" {
			disciplineName = "UNICEP"
		}
		out = append(out, storage.Notification{
			RA:             ra,
			PeriodKey:      fmt.Sprintf("%d", item.OrgUnitID),
			DisciplineCode: key,
			DisciplineName: disciplineName,
			Field:          title,
			OldValue:       "",
			NewValue:       preview,
			Kind:           "d2l_announcement",
		})
	}
	return out
}

// DiffD2LQuizzes compares previous and current D2L quizzes and emits notifications for newly posted active non-expired quizzes.
func DiffD2LQuizzes(ra string, prev, curr []d2l.QuizItem) []storage.Notification {
	seen := make(map[string]struct{}, len(prev))
	for _, item := range prev {
		seen[fmt.Sprintf("%d:%d", item.OrgUnitID, item.QuizID)] = struct{}{}
	}
	brt := time.FixedZone("BRT", -3*3600)
	now := time.Now().UTC()
	var out []storage.Notification
	for _, item := range curr {
		if !item.IsActive {
			continue
		}
		key := fmt.Sprintf("%d:%d", item.OrgUnitID, item.QuizID)
		if _, ok := seen[key]; ok {
			continue
		}
		rawDeadline := strings.TrimSpace(item.DueDate)
		if rawDeadline == "" {
			rawDeadline = strings.TrimSpace(item.EndDate)
		}
		dueLabel := "Novo questionário disponível"
		if rawDeadline != "" {
			if deadline, ok := d2l.ParseD2LTime(rawDeadline); ok {
				if deadline.Before(now) {
					continue
				}
				dueLabel = "Prazo: " + deadline.In(brt).Format("02/01 às 15:04")
			} else {
				dueLabel = "Prazo: " + rawDeadline
			}
		}
		title := strings.TrimSpace(item.Name)
		if title == "" {
			title = "Novo questionário"
		}
		disciplineName := strings.TrimSpace(item.OrgUnitName)
		if disciplineName == "" {
			disciplineName = "Sala Virtual"
		}
		out = append(out, storage.Notification{
			RA:             ra,
			PeriodKey:      fmt.Sprintf("%d", item.OrgUnitID),
			DisciplineCode: key,
			DisciplineName: disciplineName,
			Field:          title,
			OldValue:       "",
			NewValue:       dueLabel,
			Kind:           "d2l_quiz",
		})
	}
	return out
}

// DiffD2LContent compares previous and current D2L content topics and emits notifications for newly posted course materials.
func DiffD2LContent(ra string, prev, curr []d2l.ContentTopicSnapshot) []storage.Notification {
	seen := make(map[string]struct{}, len(prev))
	for _, item := range prev {
		seen[fmt.Sprintf("%d:%d", item.OrgUnitID, item.TopicID)] = struct{}{}
	}
	var out []storage.Notification
	for _, item := range curr {
		title := strings.TrimSpace(item.Title)
		if item.TopicID <= 0 || title == "" {
			continue
		}
		key := fmt.Sprintf("%d:%d", item.OrgUnitID, item.TopicID)
		if _, ok := seen[key]; ok {
			continue
		}
		modLabel := strings.TrimSpace(item.ModuleTitle)
		if modLabel == "" {
			modLabel = "Novo material disponível"
		}
		disciplineName := strings.TrimSpace(item.OrgUnitName)
		if disciplineName == "" {
			disciplineName = "Sala Virtual"
		}
		out = append(out, storage.Notification{
			RA:             ra,
			PeriodKey:      fmt.Sprintf("%d", item.OrgUnitID),
			DisciplineCode: key,
			DisciplineName: disciplineName,
			Field:          title,
			OldValue:       "",
			NewValue:       modLabel,
			Kind:           "d2l_content",
		})
	}
	return out
}

func truncatePreview(s string, maxRunes int) string {
	collapsed := strings.Join(strings.Fields(s), " ")
	runes := []rune(collapsed)
	if len(runes) <= maxRunes {
		return collapsed
	}
	return strings.TrimSpace(string(runes[:maxRunes-1])) + "…"
}

// DiffSnapshots compares previous and current grades/report cards and returns notification records.
func DiffSnapshots(
	ra, periodKey string,
	prevGrades, currGrades []portal.PartialGradeItem,
	prevReport, currReport []portal.ReportCardItem,
	notifyAbsences bool,
) []storage.Notification {
	var out []storage.Notification

	prevGMap := make(map[string]portal.PartialGradeItem, len(prevGrades))
	for _, g := range prevGrades {
		prevGMap[g.Code] = g
	}

	examFields := [4]string{"P1", "P2", "P3", "P4"}
	workFields := [4]string{"T1", "T2", "T3", "T4"}

	for _, curr := range currGrades {
		prev, exists := prevGMap[curr.Code]
		if !exists {
			continue
		}
		for i := 0; i < 4; i++ {
			out = appendGradeDiff(out, ra, periodKey, curr.Code, curr.Name, examFields[i], prev.Exams[i].Display, curr.Exams[i].Display)
			out = appendGradeDiff(out, ra, periodKey, curr.Code, curr.Name, workFields[i], prev.Works[i].Display, curr.Works[i].Display)
		}
		out = appendGradeDiff(out, ra, periodKey, curr.Code, curr.Name, "Média", prev.Average.Display, curr.Average.Display)
	}

	prevRMap := make(map[string]portal.ReportCardItem, len(prevReport))
	for _, r := range prevReport {
		prevRMap[r.Code] = r
	}
	for _, curr := range currReport {
		prev, exists := prevRMap[curr.Code]
		if !exists {
			continue
		}
		out = appendGradeDiff(out, ra, periodKey, curr.Code, curr.Name, "Média Final", prev.FinalAverage, curr.FinalAverage)
		if notifyAbsences && curr.Absences > prev.Absences {
			out = append(out, storage.Notification{
				RA:             ra,
				PeriodKey:      periodKey,
				DisciplineCode: curr.Code,
				DisciplineName: curr.Name,
				Field:          "Faltas",
				OldValue:       strconv.Itoa(prev.Absences),
				NewValue:       strconv.Itoa(curr.Absences),
				Kind:           "absence",
			})
		}
	}

	return out
}

func appendGradeDiff(out []storage.Notification, ra, periodKey, code, name, field, oldVal, newVal string) []storage.Notification {
	if newVal == "" || oldVal == newVal {
		return out
	}
	return append(out, storage.Notification{
		RA:             ra,
		PeriodKey:      periodKey,
		DisciplineCode: code,
		DisciplineName: name,
		Field:          field,
		OldValue:       oldVal,
		NewValue:       newVal,
		Kind:           "grade",
	})
}
