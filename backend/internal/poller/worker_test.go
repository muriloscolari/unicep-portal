package poller

import (
	"testing"

	"unicep-portal/internal/d2l"
	"unicep-portal/internal/portal"
)

func TestDiffSnapshots(t *testing.T) {
	v := func(x float64) *float64 { return &x }

	prevG := []portal.PartialGradeItem{
		{
			Code: "101",
			Name: "Cálculo I",
			Exams: [4]portal.GradeCell{
				{Display: "7,5", Value: v(7.5)},
				{Display: ""},
			},
		},
	}
	currG := []portal.PartialGradeItem{
		{
			Code: "101",
			Name: "Cálculo I",
			Exams: [4]portal.GradeCell{
				{Display: "7,5", Value: v(7.5)},
				{Display: "9,0", Value: v(9.0)},
			},
			Average: portal.GradeCell{Display: "8,25", Value: v(8.25)},
		},
	}
	prevR := []portal.ReportCardItem{
		{Code: "101", Name: "Cálculo I", Absences: 2},
	}
	currR := []portal.ReportCardItem{
		{Code: "101", Name: "Cálculo I", Absences: 4},
	}

	events := DiffSnapshots("1234567", "999999@@2@@2026", prevG, currG, prevR, currR, true)
	if len(events) != 3 {
		t.Fatalf("expected 3 events (P2, Média, Faltas), got %d: %+v", len(events), events)
	}
}

func TestDiffD2LAssignments(t *testing.T) {
	prev := []d2l.AssignmentItem{
		{ID: 10, OrgUnitID: 50001, OrgUnitName: "DSW", Name: "Trabalho 1", Status: "not_submitted"},
	}
	curr := []d2l.AssignmentItem{
		{ID: 10, OrgUnitID: 50001, OrgUnitName: "DSW", Name: "Trabalho 1", Status: "not_submitted"},
		{ID: 11, OrgUnitID: 50001, OrgUnitName: "DSW", Name: "Prova Online", Status: "not_submitted", DueAtMillis: 1795132740000},
		{ID: 12, OrgUnitID: 50001, OrgUnitName: "DSW", Name: "Antiga", Status: "expired"},
	}
	events := DiffD2LAssignments("1234567", prev, curr)
	if len(events) != 1 {
		t.Fatalf("expected 1 new non-expired D2L assignment event, got %d: %+v", len(events), events)
	}
	if events[0].Kind != "d2l_assignment" || events[0].Field != "Prova Online" || events[0].DisciplineName != "DSW" {
		t.Fatalf("unexpected D2L notification: %+v", events[0])
	}
}

func TestDiffD2LAssignmentsIgnoresSubmittedAndGraded(t *testing.T) {
	prev := []d2l.AssignmentItem{
		{ID: 1, OrgUnitID: 50001, OrgUnitName: "DSW", Name: "Existente", Status: "not_submitted"},
	}
	curr := []d2l.AssignmentItem{
		{ID: 1, OrgUnitID: 50001, OrgUnitName: "DSW", Name: "Existente", Status: "not_submitted"},
		{ID: 2, OrgUnitID: 50001, OrgUnitName: "DSW", Name: "Já Enviada", Status: "submitted"},
		{ID: 3, OrgUnitID: 50001, OrgUnitName: "DSW", Name: "Avaliada", Status: "graded"},
		{ID: 4, OrgUnitID: 50001, OrgUnitName: "DSW", Name: "Encerrada", Status: "expired"},
		{ID: 5, OrgUnitID: 50001, OrgUnitName: "DSW", Name: "Nova Pendente", Status: "not_submitted"},
	}
	events := DiffD2LAssignments("1234567", prev, curr)
	if len(events) != 1 || events[0].Field != "Nova Pendente" {
		t.Fatalf("expected only 'Nova Pendente' notification, got %+v", events)
	}
}

func TestDiffD2LAnnouncements(t *testing.T) {
	prev := []d2l.Announcement{
		{ID: 100, OrgUnitID: 50001, OrgUnitName: "DSW", Title: "Aviso antigo", Body: "Conteúdo antigo"},
	}
	curr := []d2l.Announcement{
		{ID: 100, OrgUnitID: 50001, OrgUnitName: "DSW", Title: "Aviso antigo", Body: "Conteúdo antigo"},
		{ID: 101, OrgUnitID: 50001, OrgUnitName: "DSW", Title: "Prova adiada", Body: "A prova será na próxima\n\nsegunda-feira."},
		{ID: 102, OrgUnitID: 6606, OrgUnitName: "", Title: "   ", Body: "", Attachments: []d2l.FileAttachment{{FileID: 1, FileName: "edital.pdf"}}},
	}
	events := DiffD2LAnnouncements("1234567", prev, curr)
	if len(events) != 2 {
		t.Fatalf("expected 2 new D2L announcement events, got %d: %+v", len(events), events)
	}
	if events[0].Kind != "d2l_announcement" || events[0].Field != "Prova adiada" || events[0].DisciplineName != "DSW" {
		t.Fatalf("unexpected first announcement event: %+v", events[0])
	}
	if events[0].NewValue != "A prova será na próxima segunda-feira." {
		t.Fatalf("unexpected collapsed body preview: %q", events[0].NewValue)
	}
	if events[1].DisciplineName != "UNICEP" || events[1].Field != "Novo aviso" || events[1].NewValue != "1 anexo(s)" {
		t.Fatalf("unexpected second announcement event: %+v", events[1])
	}
}
