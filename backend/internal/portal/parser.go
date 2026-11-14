package portal

import (
	"regexp"
	"strconv"
	"strings"

	"github.com/PuerkitoBio/goquery"
)

var (
	reGradeParam      = regexp.MustCompile(`grade=(\d+)`)
	reCurriculumEntry = regexp.MustCompile(`^(\d{4,})\s*-\s*(.+?)(?:\s*-\s*(\d+)\s*horas?)?$`)
	statusLabels      = map[string]string{
		"C": "Cursando",
		"A": "Aprovado",
		"R": "Reprovado",
		"T": "Trancado",
		"X": "Cancelado",
		"F": "Transferido",
		"P": "Pendente",
	}
)

// StatusLabel returns the Portuguese description for single-letter UNICEP status codes.
func StatusLabel(code string) string {
	code = strings.ToUpper(strings.TrimSpace(code))
	if label, ok := statusLabels[code]; ok {
		return label
	}
	return code
}

// ParsePeriodOptions extracts non-zero <option> items from #cboPeriodo.
func ParsePeriodOptions(html string, basePath string) ([]PeriodOption, error) {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		return nil, err
	}
	var out []PeriodOption
	doc.Find("#cboPeriodo option, select[name='cboPeriodo'] option").Each(func(_ int, s *goquery.Selection) {
		val := strings.TrimSpace(s.AttrOr("value", ""))
		if val == "" || val == "0" {
			return
		}
		label := cleanText(s.Text())
		label = strings.TrimPrefix(label, "- ")
		query := val
		if strings.HasPrefix(val, "?") {
			query = basePath + val
		} else if basePath != "" {
			query = basePath + val
		}
		out = append(out, PeriodOption{
			ID:    val,
			Label: label,
			Query: query,
		})
	})
	return out, nil
}

// ParseGrades parses the 19-column table.topoesq from consulta_notas.asp.
func ParseGrades(html string) ([]PartialGradeItem, error) {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		return nil, err
	}

	table := findDataTable(doc, "P1")
	if table == nil {
		return []PartialGradeItem{}, nil
	}

	var headers []string
	var items []PartialGradeItem

	table.Find("tr").Each(func(_ int, row *goquery.Selection) {
		cells := row.ChildrenFiltered("td, th")
		if cells.Length() < 11 {
			return
		}
		if len(headers) == 0 {
			headers = make([]string, cells.Length())
			cells.Each(func(i int, c *goquery.Selection) {
				headers[i] = cleanText(c.Text())
			})
			return
		}

		// Skip duplicate header rows if any.
		first := cleanText(cells.Eq(0).Text())
		if strings.EqualFold(first, "Código") || first == "" {
			return
		}

		colMap := make(map[string]*goquery.Selection, cells.Length())
		for i, h := range headers {
			if i < cells.Length() {
				colMap[strings.ToUpper(h)] = cells.Eq(i)
			}
		}

		item := PartialGradeItem{
			Code:    cellText(colMap["CÓDIGO"]),
			Name:    cellText(colMap["NOME DA DISCIPLINA"]),
			Average: parseGradeCell(colMap["MÉDIA"]),
		}
		if avgSel := colMap["MÉDIA"]; avgSel != nil {
			style := strings.ToLower(avgSel.Find("span").AttrOr("style", ""))
			item.BelowPassing = strings.Contains(style, "red")
		}

		examKeys := [4]string{"P1", "P2", "P3", "P4"}
		workKeys := [4]string{"T1", "T2", "T3", "T4"}
		for i := 0; i < 4; i++ {
			item.Exams[i] = parseGradeCell(colMap[examKeys[i]])
			item.Works[i] = parseGradeCell(colMap[workKeys[i]])
			item.ExamWeights[i] = parseGradeCell(colMap["%"+examKeys[i]])
			item.WorkWeights[i] = parseGradeCell(colMap["%"+workKeys[i]])
		}

		items = append(items, item)
	})

	return items, nil
}

// ParseReportCard parses boletim_detalhado.asp into structured subjects + complementary hours.
func ParseReportCard(html string, periodQuery string) (ReportCardResponse, error) {
	res := ReportCardResponse{
		PeriodQuery: periodQuery,
		Items:       []ReportCardItem{},
	}
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		return res, err
	}

	table := findDataTable(doc, "Freq.")
	if table == nil {
		return res, nil
	}

	var headers []string
	table.Find("tr").Each(func(_ int, row *goquery.Selection) {
		txt := cleanText(row.Text())
		if strings.Contains(txt, "Atividade(s) Complementar(es) no Semestre") {
			res.SemesterComplementaryHours = txt
			return
		}
		if strings.Contains(txt, "Total de Hora(s) de Atividade(s) Complementar(es)") {
			res.TotalComplementaryHours = txt
			return
		}

		cells := row.ChildrenFiltered("td, th")
		if cells.Length() < 8 {
			return
		}
		if len(headers) == 0 {
			headers = make([]string, cells.Length())
			cells.Each(func(i int, c *goquery.Selection) {
				headers[i] = cleanText(c.Text())
			})
			return
		}

		code := cleanText(cells.Eq(0).Text())
		if code == "" || strings.EqualFold(code, "Código") {
			return
		}

		colMap := make(map[string]string, cells.Length())
		for i, h := range headers {
			if i < cells.Length() {
				colMap[strings.ToUpper(h)] = cleanText(cells.Eq(i).Text())
			}
		}

		ch, _ := strconv.Atoi(colMap["CH"])
		fa, _ := strconv.Atoi(colMap["FA"])
		maxAbsences := ch / 4
		remaining := maxAbsences - fa

		mfStr := normalizeDash(colMap["MF"])
		mfVal := parseBrazilianFloat(mfStr)
		freqVal := parseBrazilianFloat(normalizeDash(colMap["FREQ. (%)"]))
		sit := strings.TrimSpace(colMap["SIT."])

		res.Items = append(res.Items, ReportCardItem{
			Code:              code,
			Name:              colMap["NOME DA DISCIPLINA"],
			UpdatedAt:         colMap["DT.ATUAL."],
			ClassGroup:        colMap["TURMA"],
			FinalAverage:      mfStr,
			FinalAverageValue: mfVal,
			Hours:             ch,
			Absences:          fa,
			MaxAbsences:       maxAbsences,
			RemainingAbsences: remaining,
			FreqPercent:       freqVal,
			Status:            sit,
			StatusLabel:       StatusLabel(sit),
		})
	})

	return res, nil
}

// ParseDashboard extracts greeting, notices, curriculum grade ID, and enrollment links from ambiente_estudante.asp.
func ParseDashboard(html string, nacOpen bool) (DashboardSummary, error) {
	out := DashboardSummary{
		Notices:         []string{},
		EnrollmentLinks: []LinkItem{},
		NacOpen:         nacOpen,
	}
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		return out, err
	}

	// Greeting is usually the uppercase BOA NOITE/BOM DIA/BOA TARDE text block.
	doc.Find("td, div, p, b, strong").Each(func(_ int, s *goquery.Selection) {
		t := cleanText(s.Text())
		upper := strings.ToUpper(t)
		if out.Greeting == "" && (strings.HasPrefix(upper, "BOM DIA ") || strings.HasPrefix(upper, "BOA TARDE ") || strings.HasPrefix(upper, "BOA NOITE ")) && len(t) < 120 {
			out.Greeting = t
		}
	})

	// Capture gradeId from Visualizar grade link.
	if m := reGradeParam.FindStringSubmatch(html); len(m) == 2 {
		out.GradeID = m[1]
	}

	// Capture enrollment PDF/confirmation links and notices.
	doc.Find("a").Each(func(_ int, a *goquery.Selection) {
		href := strings.TrimSpace(a.AttrOr("href", ""))
		title := cleanText(a.Text())
		if href == "" || title == "" {
			return
		}
		lower := strings.ToLower(href)
		if strings.Contains(lower, "symlinkftp") || strings.Contains(lower, "matricula_confirmacao.asp") {
			out.EnrollmentLinks = append(out.EnrollmentLinks, LinkItem{
				Title:    strings.TrimPrefix(title, "- "),
				Path:     href,
				Category: "Matrícula",
				Proxy:    true,
			})
		}
	})

	doc.Find("td, p, div").Each(func(_ int, s *goquery.Selection) {
		if s.Children().Length() > 4 {
			return
		}
		t := cleanText(s.Text())
		if strings.HasPrefix(strings.ToUpper(t), "AVISO") && len(t) > 10 && len(t) < 1200 {
			if len(out.Notices) == 0 || out.Notices[len(out.Notices)-1] != t {
				out.Notices = append(out.Notices, t)
			}
		}
	})

	return out, nil
}

// ParseTranscript parses ficha_individual_todas_semDuplicidade.asp across all semester tables.
func ParseTranscript(html string) ([]TranscriptItem, error) {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		return nil, err
	}

	var items []TranscriptItem
	seen := make(map[string]struct{})

	doc.Find("table").Each(func(_ int, t *goquery.Selection) {
		var hasHeader bool
		t.Find("tr").Each(func(_ int, row *goquery.Selection) {
			if row.Find("table").Length() > 0 {
				return
			}
			cells := row.ChildrenFiltered("td, th")
			if cells.Length() < 7 {
				return
			}

			c0 := cleanText(cells.Eq(0).Text())
			if c0 == "" || isDashedSeparator(c0) {
				return
			}
			if strings.EqualFold(c0, "Disciplina") {
				hasHeader = true
				return
			}
			if !hasHeader && !looksLikeTranscriptRow(c0) {
				return
			}

			yearSem := cleanText(cells.Eq(1).Text())
			key := c0 + "|" + yearSem
			if _, dup := seen[key]; dup {
				return
			}
			seen[key] = struct{}{}

			sit := cleanText(cells.Eq(cells.Length() - 1).Text())
			syllabus := cells.Eq(0).Find("a").AttrOr("href", "")
			if syllabus != "" && !strings.HasPrefix(syllabus, "/") && !strings.HasPrefix(syllabus, "http") {
				syllabus = "/modulo_matricula/relatorios/" + syllabus
			}
			items = append(items, TranscriptItem{
				Name:        c0,
				YearSem:     yearSem,
				Exam:        normalizeDash(cleanText(cells.Eq(2).Text())),
				Average:     normalizeDash(cleanText(cells.Eq(3).Text())),
				Hours:       cleanText(cells.Eq(4).Text()),
				Absences:    cleanText(cells.Eq(5).Text()),
				FreqPercent: cleanText(cells.Eq(6).Text()),
				Status:      sit,
				StatusLabel: StatusLabel(sit),
				SyllabusURL: syllabus,
			})
		})
	})
	return items, nil
}

// ParseCurriculum parses relatorio_matriz_curricular_apartir_2025_unicep.asp.
func ParseCurriculum(html string) ([]CurriculumItem, error) {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		return nil, err
	}
	var items []CurriculumItem
	seen := make(map[string]struct{})
	currentPeriod := ""

	doc.Find("table tr").Each(func(_ int, row *goquery.Selection) {
		if row.Find("table").Length() > 0 {
			return
		}
		cells := row.ChildrenFiltered("td, th")
		if cells.Length() == 0 {
			return
		}
		if cells.Length() <= 2 {
			t := cleanText(row.Text())
			lower := strings.ToLower(t)
			if strings.Contains(lower, "período") || strings.Contains(lower, "semestre") {
				currentPeriod = t
			}
			return
		}
		if cells.Length() >= 4 {
			c0 := cleanText(cells.Eq(0).Text())
			c1 := cleanText(cells.Eq(1).Text())
			if c0 == "" || isDashedSeparator(c0) || strings.EqualFold(c0, "Código") || strings.EqualFold(c0, "Disciplina") {
				return
			}
			prereq := cleanText(cells.Eq(cells.Length() - 1).Text())
			if m := reCurriculumEntry.FindStringSubmatch(c0); len(m) == 4 {
				code, name, hours := m[1], strings.TrimSpace(m[2]), m[3]
				if _, dup := seen[code]; !dup {
					seen[code] = struct{}{}
					items = append(items, CurriculumItem{
						Period:       currentPeriod,
						Code:         code,
						Name:         name,
						Hours:        hours,
						Prerequisite: prereq,
					})
				}
				return
			}
			if len(c0) >= 4 && isDigits(c0) {
				if _, dup := seen[c0]; !dup {
					seen[c0] = struct{}{}
					items = append(items, CurriculumItem{
						Period:       currentPeriod,
						Code:         c0,
						Name:         c1,
						Hours:        cleanText(cells.Eq(2).Text()),
						Prerequisite: prereq,
					})
				}
			}
		}
	})
	return items, nil
}

// ParseSchedule parses quadro_horarios.asp generically into headers + rows.
func ParseSchedule(html string) (ScheduleResponse, error) {
	res := ScheduleResponse{Title: "Horário das Aulas", Headers: []string{}, Rows: [][]string{}}
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		return res, err
	}
	doc.Find("table.topoesq tr").Each(func(_ int, row *goquery.Selection) {
		cells := row.ChildrenFiltered("td, th")
		if cells.Length() < 2 {
			return
		}
		vals := make([]string, 0, cells.Length())
		cells.Each(func(_ int, c *goquery.Selection) {
			vals = append(vals, cleanText(c.Text()))
		})
		if len(res.Headers) == 0 {
			res.Headers = vals
		} else {
			res.Rows = append(res.Rows, vals)
		}
	})
	return res, nil
}

// ParseTuition parses listaDebito.asp into structured installment rows.
func ParseTuition(html string) ([]TuitionItem, error) {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		return nil, err
	}
	table := findDataTable(doc, "Mensalidade")
	if table == nil {
		return []TuitionItem{}, nil
	}
	var items []TuitionItem
	headerSeen := false
	table.Find("tr").Each(func(_ int, row *goquery.Selection) {
		cells := row.ChildrenFiltered("td, th")
		if cells.Length() < 6 {
			return
		}
		if !headerSeen {
			headerSeen = true
			return
		}
		ra := cleanText(cells.Eq(0).Text())
		if ra == "" || strings.EqualFold(ra, "RA") {
			return
		}
		items = append(items, TuitionItem{
			RA:             ra,
			Installment:    cleanText(cells.Eq(1).Text()),
			DueDate:        cleanText(cells.Eq(2).Text()),
			Status:         cleanText(cells.Eq(3).Text()),
			GrossAmount:    cleanText(cells.Eq(4).Text()),
			DiscountAmount: cleanText(cells.Eq(5).Text()),
		})
	})
	return items, nil
}

// ParseInstitutionalDocs parses documentos_institucionais.asp.
func ParseInstitutionalDocs(html string) ([]LinkItem, error) {
	doc, err := goquery.NewDocumentFromReader(strings.NewReader(html))
	if err != nil {
		return nil, err
	}
	var items []LinkItem
	doc.Find("a").Each(func(_ int, a *goquery.Selection) {
		href := strings.TrimSpace(a.AttrOr("href", ""))
		title := cleanText(a.Text())
		if href == "" || title == "" {
			return
		}
		if !strings.HasPrefix(href, "/") && !strings.HasPrefix(href, "http") {
			href = "/ambiente_aluno/" + href
		}
		items = append(items, LinkItem{
			Title:    title,
			Path:     href,
			Category: "Institucional",
			Proxy:    !strings.HasPrefix(href, "http"),
		})
	})
	return items, nil
}

func findDataTable(doc *goquery.Document, headerMarker string) *goquery.Selection {
	var matched *goquery.Selection
	markerLower := strings.ToLower(headerMarker)
	doc.Find("table.topoesq, table").EachWithBreak(func(_ int, t *goquery.Selection) bool {
		// Prefer inner table containing the header marker within its first few non-nested rows.
		found := false
		t.Find("tr").Slice(0, minInt(5, t.Find("tr").Length())).EachWithBreak(func(_ int, row *goquery.Selection) bool {
			if row.Find("table").Length() > 0 {
				return true
			}
			if strings.Contains(strings.ToLower(cleanText(row.Text())), markerLower) {
				found = true
				return false
			}
			return true
		})
		if found {
			matched = t
			return false
		}
		return true
	})
	return matched
}

func isDashedSeparator(s string) bool {
	trimmed := strings.Trim(s, "-_ ")
	return trimmed == ""
}

func looksLikeTranscriptRow(c0 string) bool {
	return len(c0) >= 7 && isDigits(c0[:4]) && strings.Contains(c0, " - ")
}

func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func cellText(sel *goquery.Selection) string {
	if sel == nil {
		return ""
	}
	return cleanText(sel.Text())
}

func parseGradeCell(sel *goquery.Selection) GradeCell {
	raw := normalizeDash(cellText(sel))
	return GradeCell{
		Display: raw,
		Value:   parseBrazilianFloat(raw),
	}
}

func normalizeDash(s string) string {
	s = strings.TrimSpace(s)
	if s == "" || s == "-" || s == "\u00a0" {
		return ""
	}
	return s
}

func parseBrazilianFloat(s string) *float64 {
	if s == "" {
		return nil
	}
	normalized := strings.ReplaceAll(strings.TrimSpace(s), ",", ".")
	v, err := strconv.ParseFloat(normalized, 64)
	if err != nil {
		return nil
	}
	return &v
}

func cleanText(s string) string {
	s = strings.ReplaceAll(s, "\u00a0", " ")
	fields := strings.Fields(s)
	return strings.Join(fields, " ")
}

func isDigits(s string) bool {
	for _, r := range s {
		if r < '0' || r > '9' {
			return false
		}
	}
	return s != ""
}
