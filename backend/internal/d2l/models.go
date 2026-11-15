package d2l

// SemesterOption represents an academic semester returned by Brightspace manageCourses.
type SemesterOption struct {
	ID   string `json:"id"`
	Name string `json:"name"`
	Code string `json:"code"`
}

// CourseSummary represents a course offering enrolled by the student in Sala Virtual.
type CourseSummary struct {
	OrgUnitID       int64  `json:"orgUnitId"`
	Name            string `json:"name"`
	Code            string `json:"code"`
	SemesterName    string `json:"semesterName"`
	IsActive        bool   `json:"isActive"`
	CanAccess       bool   `json:"canAccess"`
	StartDate       string `json:"startDate,omitempty"`
	EndDate         string `json:"endDate,omitempty"`
	LastAccessed    string `json:"lastAccessed,omitempty"`
	HomeURL         string `json:"homeUrl"`
	PendingCount    int    `json:"pendingCount"`
	AssignmentCount int    `json:"assignmentCount"`
}

// FileAttachment represents a downloadable attachment or submitted file in D2L.
type FileAttachment struct {
	FileID       int64  `json:"fileId"`
	FileName     string `json:"fileName"`
	Size         int64  `json:"size"`
	DownloadPath string `json:"downloadPath,omitempty"`
}

// Announcement represents a D2L news/announcement item (institutional or course-level).
type Announcement struct {
	ID          int64            `json:"id"`
	OrgUnitID   int64            `json:"orgUnitId"`
	OrgUnitName string           `json:"orgUnitName,omitempty"`
	Title       string           `json:"title"`
	Body        string           `json:"body"`
	CreatedDate string           `json:"createdDate,omitempty"`
	StartDate   string           `json:"startDate,omitempty"`
	Attachments []FileAttachment `json:"attachments"`
}

// CalendarEvent represents an upcoming or recent D2L calendar item.
type CalendarEvent struct {
	ID            int64  `json:"id"`
	OrgUnitID     int64  `json:"orgUnitId"`
	OrgUnitName   string `json:"orgUnitName,omitempty"`
	Title         string `json:"title"`
	Description   string `json:"description,omitempty"`
	StartDateTime string `json:"startDateTime,omitempty"`
	EndDateTime   string `json:"endDateTime,omitempty"`
	AllDay        bool   `json:"allDay"`
	WebURL        string `json:"webUrl,omitempty"`
}

// AssignmentItem represents a read-only Dropbox assignment, submission status, and professor feedback.
type AssignmentItem struct {
	ID             int64            `json:"id"`
	OrgUnitID      int64            `json:"orgUnitId"`
	OrgUnitName    string           `json:"orgUnitName,omitempty"`
	Name           string           `json:"name"`
	Instructions   string           `json:"instructions,omitempty"`
	DueDate        string           `json:"dueDate,omitempty"`
	DueAtMillis    int64            `json:"dueAtMillis,omitempty"`
	StartDate      string           `json:"startDate,omitempty"`
	EndDate        string           `json:"endDate,omitempty"`
	OutOf          *float64         `json:"outOf,omitempty"`
	Attachments    []FileAttachment `json:"attachments"`
	Status         string           `json:"status"`
	StatusLabel    string           `json:"statusLabel"`
	SubmissionDate string           `json:"submissionDate,omitempty"`
	SubmittedFiles []FileAttachment `json:"submittedFiles"`
	Score          *float64         `json:"score,omitempty"`
	FeedbackText   string           `json:"feedbackText,omitempty"`
	CompletionDate string           `json:"completionDate,omitempty"`
	WebURL         string           `json:"webUrl"`
}

// ContentTopic represents a single topic/resource inside a Brightspace content module.
type ContentTopic struct {
	TopicID          int64  `json:"topicId"`
	Title            string `json:"title"`
	TypeIdentifier   string `json:"typeIdentifier"`
	URL              string `json:"url,omitempty"`
	DownloadPath     string `json:"downloadPath,omitempty"`
	ExternalURL      string `json:"externalUrl,omitempty"`
	Unread           bool   `json:"unread"`
	LastModifiedDate string `json:"lastModifiedDate,omitempty"`
}

// ContentModule represents a Brightspace content module (with nested submodules and topics).
type ContentModule struct {
	ModuleID    int64           `json:"moduleId"`
	Title       string          `json:"title"`
	Description string          `json:"description,omitempty"`
	Topics      []ContentTopic  `json:"topics"`
	Modules     []ContentModule `json:"modules"`
}

// VirtualGradeItem represents a student grade entry inside a D2L course gradebook.
type VirtualGradeItem struct {
	ID                string   `json:"id"`
	Name              string   `json:"name"`
	DisplayedGrade    string   `json:"displayedGrade,omitempty"`
	PointsNumerator   *float64 `json:"pointsNumerator,omitempty"`
	PointsDenominator *float64 `json:"pointsDenominator,omitempty"`
	Comments          string   `json:"comments,omitempty"`
	LastModified      string   `json:"lastModified,omitempty"`
	ReleasedDate      string   `json:"releasedDate,omitempty"`
}

// QuizItem represents a read-only Brightspace quiz summary.
type QuizItem struct {
	QuizID          int64  `json:"quizId"`
	Name            string `json:"name"`
	Description     string `json:"description,omitempty"`
	IsActive        bool   `json:"isActive"`
	StartDate       string `json:"startDate,omitempty"`
	EndDate         string `json:"endDate,omitempty"`
	DueDate         string `json:"dueDate,omitempty"`
	TimeLimitMins   *int   `json:"timeLimitMins,omitempty"`
	AttemptsAllowed *int   `json:"attemptsAllowed,omitempty"`
	WebURL          string `json:"webUrl"`
}

// DiscussionForum represents a read-only Brightspace discussion forum.
type DiscussionForum struct {
	ForumID     int64  `json:"forumId"`
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
	WebURL      string `json:"webUrl"`
}

// OverviewResponse is the aggregated passive home payload for the Sala Virtual section.
type OverviewResponse struct {
	BaseURL             string           `json:"baseUrl"`
	UserID              string           `json:"userId,omitempty"`
	UserName            string           `json:"userName,omitempty"`
	Complete            bool             `json:"complete"`
	Semesters           []SemesterOption `json:"semesters"`
	Courses             []CourseSummary  `json:"courses"`
	UpcomingAssignments []AssignmentItem `json:"upcomingAssignments"`
	Announcements       []Announcement   `json:"announcements"`
	CalendarEvents      []CalendarEvent  `json:"calendarEvents"`
}

// CourseDetailResponse aggregates all read-only data for a single Brightspace course offering.
type CourseDetailResponse struct {
	BaseURL       string            `json:"baseUrl"`
	Course        CourseSummary     `json:"course"`
	Announcements []Announcement    `json:"announcements"`
	Modules       []ContentModule   `json:"modules"`
	Assignments   []AssignmentItem  `json:"assignments"`
	Grades        []VirtualGradeItem `json:"grades"`
	Quizzes       []QuizItem        `json:"quizzes"`
	Discussions   []DiscussionForum `json:"discussions"`
	Calendar      []CalendarEvent   `json:"calendar"`
}
