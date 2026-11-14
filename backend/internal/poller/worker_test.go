package poller

import (
	"testing"

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
