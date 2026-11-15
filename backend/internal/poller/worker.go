package poller

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strconv"
	"strings"
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
	for _, u := range users {
		if ctx.Err() != nil {
			return
		}
		if _, err := w.SyncUser(ctx, u.RA, false); err != nil {
			log.Printf("poller: sync user %s error: %v", u.RA, err)
		}
	}
}

// SyncUser fetches the latest semester grades and report card for ra, saves the snapshot, and returns new notifications.
// When seedOnly is true and no prior snapshot exists, no notifications are emitted.
func (w *Worker) SyncUser(ctx context.Context, ra string, seedOnly bool) (int, error) {
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
		if _, err := w.store.InsertNotification(ev); err == nil {
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

	if d2lCreated, d2lErr := w.syncD2LAssignments(ctx, ra, seedOnly); d2lErr != nil {
		log.Printf("poller: sync D2L %s error: %v", ra, d2lErr)
	} else {
		created += d2lCreated
	}
	return created, nil
}

func (w *Worker) syncD2LAssignments(ctx context.Context, ra string, seedOnly bool) (int, error) {
	if w.d2l == nil {
		return 0, nil
	}
	ov, err := w.d2l.GetOverview(ctx, ra)
	if err != nil {
		return 0, err
	}
	currBytes, _ := json.Marshal(ov.UpcomingAssignments)
	prevJSON, err := w.store.GetD2LSnapshot(ra)
	if err != nil {
		return 0, err
	}
	if prevJSON == "" {
		if err := w.store.SaveD2LSnapshot(ra, string(currBytes)); err != nil {
			return 0, err
		}
		return 0, nil
	}
	if seedOnly {
		return 0, nil
	}

	var prev []d2l.AssignmentItem
	_ = json.Unmarshal([]byte(prevJSON), &prev)
	events := DiffD2LAssignments(ra, prev, ov.UpcomingAssignments)
	created := 0
	for _, ev := range events {
		if _, err := w.store.InsertNotification(ev); err == nil {
			created++
		}
	}
	if err := w.store.SaveD2LSnapshot(ra, string(currBytes)); err != nil {
		return created, err
	}
	return created, nil
}

// DiffD2LAssignments compares previous and current D2L assignments and emits notifications for newly posted non-expired items.
func DiffD2LAssignments(ra string, prev, curr []d2l.AssignmentItem) []storage.Notification {
	seen := make(map[string]struct{}, len(prev))
	for _, item := range prev {
		seen[fmt.Sprintf("%d:%d", item.OrgUnitID, item.ID)] = struct{}{}
	}
	brt := time.FixedZone("BRT", -3*3600)
	var out []storage.Notification
	for _, item := range curr {
		if item.Status == "expired" {
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
