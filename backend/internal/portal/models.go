package portal

// StudentInfo holds authenticated student metadata extracted from portal cookies.
type StudentInfo struct {
	RA       string `json:"ra"`
	Name     string `json:"name"`
	Campus   string `json:"campus"`
	Perfil   string `json:"perfil"`
	Semester string `json:"semester"`
}

// PeriodOption represents a selectable academic semester from a portal <select>.
type PeriodOption struct {
	ID    string `json:"id"`
	Label string `json:"label"`
	Query string `json:"query"`
}

// GradeCell holds both the raw display value and parsed numeric value when available.
type GradeCell struct {
	Display string   `json:"display"`
	Value   *float64 `json:"value,omitempty"`
}

// PartialGradeItem represents one row from consulta_notas.asp.
type PartialGradeItem struct {
	Code         string       `json:"code"`
	Name         string       `json:"name"`
	Exams        [4]GradeCell `json:"exams"`
	Works        [4]GradeCell `json:"works"`
	ExamWeights  [4]GradeCell `json:"examWeights"`
	WorkWeights  [4]GradeCell `json:"workWeights"`
	Average      GradeCell    `json:"average"`
	BelowPassing bool         `json:"belowPassing"`
}

// ReportCardItem represents one subject row from boletim_detalhado.asp.
type ReportCardItem struct {
	Code              string   `json:"code"`
	Name              string   `json:"name"`
	UpdatedAt         string   `json:"updatedAt"`
	ClassGroup        string   `json:"classGroup"`
	FinalAverage      string   `json:"finalAverage"`
	FinalAverageValue *float64 `json:"finalAverageValue,omitempty"`
	Hours             int      `json:"hours"`
	Absences          int      `json:"absences"`
	MaxAbsences       int      `json:"maxAbsences"`
	RemainingAbsences int      `json:"remainingAbsences"`
	FreqPercent       *float64 `json:"freqPercent,omitempty"`
	Status            string   `json:"status"`
	StatusLabel       string   `json:"statusLabel"`
}

// ReportCardResponse represents the full parsed boletim_detalhado.asp page.
type ReportCardResponse struct {
	PeriodQuery                string           `json:"periodQuery"`
	Items                      []ReportCardItem `json:"items"`
	SemesterComplementaryHours string           `json:"semesterComplementaryHours"`
	TotalComplementaryHours    string           `json:"totalComplementaryHours"`
}

// TranscriptItem represents a row from ficha_individual_todas_semDuplicidade.asp.
type TranscriptItem struct {
	Name        string `json:"name"`
	YearSem     string `json:"yearSem"`
	Exam        string `json:"exam"`
	Average     string `json:"average"`
	Hours       string `json:"hours"`
	Absences    string `json:"absences"`
	FreqPercent string `json:"freqPercent"`
	Status      string `json:"status"`
	StatusLabel string `json:"statusLabel"`
	SyllabusURL string `json:"syllabusUrl,omitempty"`
}

// CurriculumItem represents one subject in the curriculum matrix.
type CurriculumItem struct {
	Period       string `json:"period"`
	Code         string `json:"code"`
	Name         string `json:"name"`
	Hours        string `json:"hours"`
	Prerequisite string `json:"prerequisite"`
}

// ScheduleResponse stores parsed class schedule rows (or raw fallback headers/rows).
type ScheduleResponse struct {
	Title   string     `json:"title"`
	Headers []string   `json:"headers"`
	Rows    [][]string `json:"rows"`
}

// TuitionItem represents a row from listaDebito.asp.
type TuitionItem struct {
	RA             string `json:"ra"`
	Installment    string `json:"installment"`
	DueDate        string `json:"dueDate"`
	Status         string `json:"status"`
	GrossAmount    string `json:"grossAmount"`
	DiscountAmount string `json:"discountAmount"`
}

// LinkItem represents a portal document or service link.
type LinkItem struct {
	Title    string `json:"title"`
	Path     string `json:"path"`
	Category string `json:"category"`
	Proxy    bool   `json:"proxy"`
}

// DashboardSummary is the parsed content from ambiente_estudante.asp.
type DashboardSummary struct {
	Greeting        string     `json:"greeting"`
	Notices         []string   `json:"notices"`
	EnrollmentLinks []LinkItem `json:"enrollmentLinks"`
	GradeID         string     `json:"gradeId"`
	NacOpen         bool       `json:"nacOpen"`
}
