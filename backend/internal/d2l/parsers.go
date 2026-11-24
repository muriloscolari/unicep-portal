package d2l

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"
)

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
	FileID    int64  `json:"FileId"`
	FileName  string `json:"FileName"`
	Size      int64  `json:"Size"`
	IsDeleted bool   `json:"IsDeleted"`
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
	IsHidden           bool            `json:"IsHidden"`
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
