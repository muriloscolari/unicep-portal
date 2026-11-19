package storage

import (
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"time"

	_ "modernc.org/sqlite"
)

// Store wraps SQLite persistence for users, snapshots, and grade notifications.
type Store struct {
	db *sql.DB
}

// UserRecord stores encrypted credentials so the background poller can refresh UNICEP sessions.
type UserRecord struct {
	RA                string
	Perfil            string
	Name              string
	Campus            string
	EncryptedPassword string
	PollingEnabled    bool
	D2LPollingEnabled bool
	TokenVersion      int
	LastLoginAt       string
}

// SnapshotRecord stores the last known serialized grades/report JSON per student and period.
type SnapshotRecord struct {
	RA         string
	PeriodKey  string
	GradesJSON string
	ReportJSON string
	UpdatedAt  string
}

// Notification represents a detected grade, average, or absence update.
type Notification struct {
	ID             int64   `json:"id"`
	RA             string  `json:"ra"`
	PeriodKey      string  `json:"periodKey"`
	DisciplineCode string  `json:"disciplineCode"`
	DisciplineName string  `json:"disciplineName"`
	Field          string  `json:"field"`
	OldValue       string  `json:"oldValue"`
	NewValue       string  `json:"newValue"`
	Kind           string  `json:"kind"`
	CreatedAt      string  `json:"createdAt"`
	ReadAt         *string `json:"readAt"`
}

// CalendarEvent represents a user-created calendar event with optional local reminders.
type CalendarEvent struct {
	ID              int64  `json:"id"`
	RA              string `json:"ra,omitempty"`
	Title           string `json:"title"`
	Description     string `json:"description"`
	Date            string `json:"date"`
	Time            string `json:"time"`
	AllDay          bool   `json:"allDay"`
	StartAtMillis   int64  `json:"startAtMillis"`
	DisciplineName  string `json:"disciplineName"`
	ColorKey        string `json:"colorKey"`
	ReminderMinutes []int  `json:"reminderMinutes"`
	CreatedAt       string `json:"createdAt"`
	UpdatedAt       string `json:"updatedAt"`
}

// Open initializes the SQLite database inside dataDir and applies schema migrations.
func Open(dataDir string) (*Store, error) {
	if err := os.MkdirAll(dataDir, 0o700); err != nil {
		return nil, fmt.Errorf("create data dir: %w", err)
	}
	_ = os.Chmod(dataDir, 0o700)
	dbPath := filepath.Join(dataDir, "portal.db")
	dsn := fmt.Sprintf("file:%s?_pragma=journal_mode(WAL)&_pragma=busy_timeout(5000)&_pragma=foreign_keys(ON)", dbPath)
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("open sqlite: %w", err)
	}
	db.SetMaxOpenConns(1)
	if err := migrate(db); err != nil {
		_ = db.Close()
		return nil, err
	}
	_ = os.Chmod(dbPath, 0o600)
	return &Store{db: db}, nil
}

// Close closes the underlying SQLite connection.
func (s *Store) Close() error {
	return s.db.Close()
}

func migrate(db *sql.DB) error {
	const schema = `
CREATE TABLE IF NOT EXISTS users (
	ra TEXT PRIMARY KEY,
	perfil TEXT NOT NULL,
	name TEXT NOT NULL DEFAULT '',
	campus TEXT NOT NULL DEFAULT '',
	encrypted_password TEXT NOT NULL,
	polling_enabled INTEGER NOT NULL DEFAULT 1,
	token_version INTEGER NOT NULL DEFAULT 1,
	last_login_at TEXT NOT NULL,
	updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS grade_snapshots (
	ra TEXT NOT NULL,
	period_key TEXT NOT NULL,
	grades_json TEXT NOT NULL,
	report_json TEXT NOT NULL,
	updated_at TEXT NOT NULL,
	PRIMARY KEY (ra, period_key)
);

CREATE TABLE IF NOT EXISTS notifications (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	ra TEXT NOT NULL,
	period_key TEXT NOT NULL,
	discipline_code TEXT NOT NULL,
	discipline_name TEXT NOT NULL,
	field TEXT NOT NULL,
	old_value TEXT NOT NULL DEFAULT '',
	new_value TEXT NOT NULL,
	kind TEXT NOT NULL,
	created_at TEXT NOT NULL,
	read_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_notifications_ra_created ON notifications(ra, id DESC);

CREATE TABLE IF NOT EXISTS d2l_snapshots (
	ra TEXT PRIMARY KEY,
	assignments_json TEXT NOT NULL,
	updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS calendar_events (
	id INTEGER PRIMARY KEY AUTOINCREMENT,
	ra TEXT NOT NULL,
	title TEXT NOT NULL,
	description TEXT NOT NULL DEFAULT '',
	event_date TEXT NOT NULL,
	event_time TEXT NOT NULL DEFAULT '',
	all_day INTEGER NOT NULL DEFAULT 0,
	start_at_millis INTEGER NOT NULL DEFAULT 0,
	discipline_name TEXT NOT NULL DEFAULT '',
	color_key TEXT NOT NULL DEFAULT 'accent',
	reminder_minutes_json TEXT NOT NULL DEFAULT '[]',
	created_at TEXT NOT NULL,
	updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_calendar_events_ra_start ON calendar_events(ra, start_at_millis, id);
`
	if _, err := db.Exec(schema); err != nil {
		return err
	}
	if err := ensureColumn(db, "users", "token_version", "INTEGER NOT NULL DEFAULT 1"); err != nil {
		return err
	}
	if err := ensureColumn(db, "users", "d2l_polling_enabled", "INTEGER NOT NULL DEFAULT 1"); err != nil {
		return err
	}
	if err := ensureColumn(db, "d2l_snapshots", "announcements_json", "TEXT NOT NULL DEFAULT ''"); err != nil {
		return err
	}
	if err := ensureColumn(db, "d2l_snapshots", "content_json", "TEXT NOT NULL DEFAULT ''"); err != nil {
		return err
	}
	if err := ensureColumn(db, "d2l_snapshots", "quizzes_json", "TEXT NOT NULL DEFAULT ''"); err != nil {
		return err
	}
	_, err := db.Exec(`
DELETE FROM notifications
WHERE period_key != 'test' AND id NOT IN (
	SELECT MIN(id) FROM notifications WHERE period_key != 'test' GROUP BY ra, period_key, discipline_code, field, new_value, kind
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_dedupe
ON notifications(ra, period_key, discipline_code, field, new_value, kind)
WHERE period_key != 'test';
`)
	return err
}

func ensureColumn(db *sql.DB, table, column, definition string) error {
	rows, err := db.Query(fmt.Sprintf("PRAGMA table_info(%s)", table))
	if err != nil {
		return err
	}
	defer rows.Close()

	for rows.Next() {
		var cid int
		var name, ctype string
		var notnull int
		var dflt sql.NullString
		var pk int
		if err := rows.Scan(&cid, &name, &ctype, &notnull, &dflt, &pk); err != nil {
			return err
		}
		if name == column {
			return nil
		}
	}
	if err := rows.Err(); err != nil {
		return err
	}
	_, err = db.Exec(fmt.Sprintf("ALTER TABLE %s ADD COLUMN %s %s", table, column, definition))
	return err
}

// UpsertUser saves or updates the user's encrypted portal credentials and metadata.
func (s *Store) UpsertUser(u UserRecord) error {
	now := time.Now().UTC().Format(time.RFC3339)
	enabled := 0
	if u.PollingEnabled {
		enabled = 1
	}
	_, err := s.db.Exec(`
INSERT INTO users (ra, perfil, name, campus, encrypted_password, polling_enabled, last_login_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(ra) DO UPDATE SET
	perfil = excluded.perfil,
	name = CASE WHEN excluded.name != '' THEN excluded.name ELSE users.name END,
	campus = CASE WHEN excluded.campus != '' THEN excluded.campus ELSE users.campus END,
	encrypted_password = excluded.encrypted_password,
	polling_enabled = excluded.polling_enabled,
	last_login_at = excluded.last_login_at,
	updated_at = excluded.updated_at
`, u.RA, u.Perfil, u.Name, u.Campus, u.EncryptedPassword, enabled, now, now)
	return err
}

// GetUser fetches a stored user by RA.
func (s *Store) GetUser(ra string) (*UserRecord, error) {
	row := s.db.QueryRow(`
SELECT ra, perfil, name, campus, encrypted_password, polling_enabled, d2l_polling_enabled, token_version, last_login_at
FROM users WHERE ra = ?`, ra)
	var u UserRecord
	var enabled int
	var d2lEnabled int
	if err := row.Scan(&u.RA, &u.Perfil, &u.Name, &u.Campus, &u.EncryptedPassword, &enabled, &d2lEnabled, &u.TokenVersion, &u.LastLoginAt); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	u.PollingEnabled = enabled == 1
	u.D2LPollingEnabled = d2lEnabled == 1
	return &u, nil
}

// SetD2LPollingEnabled updates whether background Sala Virtual notifications are active for ra.
func (s *Store) SetD2LPollingEnabled(ra string, enabled bool) error {
	v := 0
	if enabled {
		v = 1
	}
	now := time.Now().UTC().Format(time.RFC3339)
	_, err := s.db.Exec(`UPDATE users SET d2l_polling_enabled = ?, updated_at = ? WHERE ra = ?`, v, now, ra)
	return err
}

// GetTokenVersion returns the active JWT token version for ra (0 if user does not exist).
func (s *Store) GetTokenVersion(ra string) (int, error) {
	var tv int
	err := s.db.QueryRow(`SELECT token_version FROM users WHERE ra = ?`, ra).Scan(&tv)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, nil
	}
	return tv, err
}

// IncrementTokenVersion invalidates all previously issued JWTs for ra.
func (s *Store) IncrementTokenVersion(ra string) error {
	now := time.Now().UTC().Format(time.RFC3339)
	_, err := s.db.Exec(`UPDATE users SET token_version = token_version + 1, updated_at = ? WHERE ra = ?`, now, ra)
	return err
}

// ListPollingUsers returns all users with background polling enabled.
func (s *Store) ListPollingUsers() ([]UserRecord, error) {
	rows, err := s.db.Query(`
SELECT ra, perfil, name, campus, encrypted_password, polling_enabled, d2l_polling_enabled, token_version, last_login_at
FROM users WHERE polling_enabled = 1`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var list []UserRecord
	for rows.Next() {
		var u UserRecord
		var enabled int
		var d2lEnabled int
		if err := rows.Scan(&u.RA, &u.Perfil, &u.Name, &u.Campus, &u.EncryptedPassword, &enabled, &d2lEnabled, &u.TokenVersion, &u.LastLoginAt); err != nil {
			return nil, err
		}
		u.PollingEnabled = enabled == 1
		u.D2LPollingEnabled = d2lEnabled == 1
		list = append(list, u)
	}
	return list, rows.Err()
}

// GetSnapshot loads the latest snapshot for a student and period.
func (s *Store) GetSnapshot(ra, periodKey string) (*SnapshotRecord, error) {
	row := s.db.QueryRow(`
SELECT ra, period_key, grades_json, report_json, updated_at
FROM grade_snapshots WHERE ra = ? AND period_key = ?`, ra, periodKey)
	var rec SnapshotRecord
	if err := row.Scan(&rec.RA, &rec.PeriodKey, &rec.GradesJSON, &rec.ReportJSON, &rec.UpdatedAt); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &rec, nil
}

// SaveSnapshot creates or updates a snapshot for a student and period.
func (s *Store) SaveSnapshot(rec SnapshotRecord) error {
	now := time.Now().UTC().Format(time.RFC3339)
	_, err := s.db.Exec(`
INSERT INTO grade_snapshots (ra, period_key, grades_json, report_json, updated_at)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT(ra, period_key) DO UPDATE SET
	grades_json = excluded.grades_json,
	report_json = excluded.report_json,
	updated_at = excluded.updated_at
`, rec.RA, rec.PeriodKey, rec.GradesJSON, rec.ReportJSON, now)
	return err
}

// InsertNotification stores a newly detected academic change notification, ignoring exact duplicates.
func (s *Store) InsertNotification(n Notification) (int64, error) {
	now := time.Now().UTC().Format(time.RFC3339)
	res, err := s.db.Exec(`
INSERT OR IGNORE INTO notifications (ra, period_key, discipline_code, discipline_name, field, old_value, new_value, kind, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
`, n.RA, n.PeriodKey, n.DisciplineCode, n.DisciplineName, n.Field, n.OldValue, n.NewValue, n.Kind, now)
	if err != nil {
		return 0, err
	}
	affected, err := res.RowsAffected()
	if err != nil || affected == 0 {
		return 0, err
	}
	return res.LastInsertId()
}

// ListNotifications returns recent notifications for a student, optionally filtering unread only.
func (s *Store) ListNotifications(ra string, unreadOnly bool, limit int) ([]Notification, error) {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	query := `
SELECT id, ra, period_key, discipline_code, discipline_name, field, old_value, new_value, kind, created_at, read_at
FROM notifications WHERE ra = ?`
	if unreadOnly {
		query += ` AND read_at IS NULL`
	}
	query += ` ORDER BY id DESC LIMIT ?`

	rows, err := s.db.Query(query, ra, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	list := make([]Notification, 0)
	for rows.Next() {
		var n Notification
		var readAt sql.NullString
		if err := rows.Scan(&n.ID, &n.RA, &n.PeriodKey, &n.DisciplineCode, &n.DisciplineName, &n.Field, &n.OldValue, &n.NewValue, &n.Kind, &n.CreatedAt, &readAt); err != nil {
			return nil, err
		}
		if readAt.Valid {
			v := readAt.String
			n.ReadAt = &v
		}
		list = append(list, n)
	}
	return list, rows.Err()
}

// MarkNotificationsRead marks specified IDs (or all unread notifications when ids is empty) as read.
func (s *Store) MarkNotificationsRead(ra string, ids []int64) error {
	now := time.Now().UTC().Format(time.RFC3339)
	if len(ids) == 0 {
		_, err := s.db.Exec(`UPDATE notifications SET read_at = ? WHERE ra = ? AND read_at IS NULL`, now, ra)
		return err
	}
	tx, err := s.db.Begin()
	if err != nil {
		return err
	}
	stmt, err := tx.Prepare(`UPDATE notifications SET read_at = ? WHERE ra = ? AND id = ? AND read_at IS NULL`)
	if err != nil {
		_ = tx.Rollback()
		return err
	}
	defer stmt.Close()
	for _, id := range ids {
		if _, err := stmt.Exec(now, ra, id); err != nil {
			_ = tx.Rollback()
			return err
		}
	}
	return tx.Commit()
}

// GetD2LSnapshot returns the stored JSON array of D2L assignments for ra (empty if none).
func (s *Store) GetD2LSnapshot(ra string) (string, error) {
	var raw string
	err := s.db.QueryRow(`SELECT assignments_json FROM d2l_snapshots WHERE ra = ?`, ra).Scan(&raw)
	if errors.Is(err, sql.ErrNoRows) {
		return "", nil
	}
	return raw, err
}

// SaveD2LSnapshot stores the latest JSON array of D2L assignments for ra.
func (s *Store) SaveD2LSnapshot(ra, assignmentsJSON string) error {
	now := time.Now().UTC().Format(time.RFC3339)
	_, err := s.db.Exec(`
INSERT INTO d2l_snapshots (ra, assignments_json, updated_at)
VALUES (?, ?, ?)
ON CONFLICT(ra) DO UPDATE SET
	assignments_json = excluded.assignments_json,
	updated_at = excluded.updated_at
`, ra, assignmentsJSON, now)
	return err
}

// GetD2LAnnouncementsSnapshot returns the stored JSON array of D2L announcements for ra (empty if none).
func (s *Store) GetD2LAnnouncementsSnapshot(ra string) (string, error) {
	var raw string
	err := s.db.QueryRow(`SELECT announcements_json FROM d2l_snapshots WHERE ra = ?`, ra).Scan(&raw)
	if errors.Is(err, sql.ErrNoRows) {
		return "", nil
	}
	return raw, err
}

// SaveD2LAnnouncementsSnapshot stores the latest JSON array of D2L announcements for ra.
func (s *Store) SaveD2LAnnouncementsSnapshot(ra, announcementsJSON string) error {
	now := time.Now().UTC().Format(time.RFC3339)
	_, err := s.db.Exec(`
INSERT INTO d2l_snapshots (ra, assignments_json, announcements_json, updated_at)
VALUES (?, '[]', ?, ?)
ON CONFLICT(ra) DO UPDATE SET
	announcements_json = excluded.announcements_json,
	updated_at = excluded.updated_at
`, ra, announcementsJSON, now)
	return err
}

// GetD2LContentSnapshot returns the stored JSON array of D2L content topics for ra (empty if none).
func (s *Store) GetD2LContentSnapshot(ra string) (string, error) {
	var raw string
	err := s.db.QueryRow(`SELECT content_json FROM d2l_snapshots WHERE ra = ?`, ra).Scan(&raw)
	if errors.Is(err, sql.ErrNoRows) {
		return "", nil
	}
	return raw, err
}

// SaveD2LContentSnapshot stores the latest JSON array of D2L content topics for ra.
func (s *Store) SaveD2LContentSnapshot(ra, contentJSON string) error {
	now := time.Now().UTC().Format(time.RFC3339)
	_, err := s.db.Exec(`
INSERT INTO d2l_snapshots (ra, assignments_json, content_json, updated_at)
VALUES (?, '[]', ?, ?)
ON CONFLICT(ra) DO UPDATE SET
	content_json = excluded.content_json,
	updated_at = excluded.updated_at
`, ra, contentJSON, now)
	return err
}

// GetD2LQuizzesSnapshot returns the stored JSON array of D2L quizzes for ra (empty if none).
func (s *Store) GetD2LQuizzesSnapshot(ra string) (string, error) {
	var raw string
	err := s.db.QueryRow(`SELECT quizzes_json FROM d2l_snapshots WHERE ra = ?`, ra).Scan(&raw)
	if errors.Is(err, sql.ErrNoRows) {
		return "", nil
	}
	return raw, err
}

// SaveD2LQuizzesSnapshot stores the latest JSON array of D2L quizzes for ra.
func (s *Store) SaveD2LQuizzesSnapshot(ra, quizzesJSON string) error {
	now := time.Now().UTC().Format(time.RFC3339)
	_, err := s.db.Exec(`
INSERT INTO d2l_snapshots (ra, assignments_json, quizzes_json, updated_at)
VALUES (?, '[]', ?, ?)
ON CONFLICT(ra) DO UPDATE SET
	quizzes_json = excluded.quizzes_json,
	updated_at = excluded.updated_at
`, ra, quizzesJSON, now)
	return err
}

// ListCalendarEvents returns all personal calendar events owned by ra, ordered chronologically.
func (s *Store) ListCalendarEvents(ra string) ([]CalendarEvent, error) {
	rows, err := s.db.Query(`
SELECT id, title, description, event_date, event_time, all_day, start_at_millis, discipline_name, color_key, reminder_minutes_json, created_at, updated_at
FROM calendar_events WHERE ra = ? ORDER BY start_at_millis ASC, id ASC`, ra)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	list := make([]CalendarEvent, 0)
	for rows.Next() {
		ev, err := scanCalendarEvent(rows)
		if err != nil {
			return nil, err
		}
		list = append(list, ev)
	}
	return list, rows.Err()
}

// CountCalendarEvents returns the number of personal calendar events stored for ra.
func (s *Store) CountCalendarEvents(ra string) (int, error) {
	var count int
	err := s.db.QueryRow(`SELECT COUNT(*) FROM calendar_events WHERE ra = ?`, ra).Scan(&count)
	return count, err
}

// CreateCalendarEvent inserts a personal calendar event and returns the persisted record.
func (s *Store) CreateCalendarEvent(ev CalendarEvent) (CalendarEvent, error) {
	now := time.Now().UTC().Format(time.RFC3339)
	reminders := ev.ReminderMinutes
	if reminders == nil {
		reminders = []int{}
	}
	remBytes, _ := json.Marshal(reminders)
	allDayInt := 0
	if ev.AllDay {
		allDayInt = 1
	}
	res, err := s.db.Exec(`
INSERT INTO calendar_events (ra, title, description, event_date, event_time, all_day, start_at_millis, discipline_name, color_key, reminder_minutes_json, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		ev.RA, ev.Title, ev.Description, ev.Date, ev.Time, allDayInt, ev.StartAtMillis, ev.DisciplineName, ev.ColorKey, string(remBytes), now, now)
	if err != nil {
		return CalendarEvent{}, err
	}
	id, err := res.LastInsertId()
	if err != nil {
		return CalendarEvent{}, err
	}
	ev.ID = id
	ev.RA = ""
	ev.ReminderMinutes = reminders
	ev.CreatedAt = now
	ev.UpdatedAt = now
	return ev, nil
}

// UpdateCalendarEvent updates an existing personal calendar event owned by ra (returns nil, nil if not found).
func (s *Store) UpdateCalendarEvent(ra string, ev CalendarEvent) (*CalendarEvent, error) {
	now := time.Now().UTC().Format(time.RFC3339)
	reminders := ev.ReminderMinutes
	if reminders == nil {
		reminders = []int{}
	}
	remBytes, _ := json.Marshal(reminders)
	allDayInt := 0
	if ev.AllDay {
		allDayInt = 1
	}
	res, err := s.db.Exec(`
UPDATE calendar_events
SET title = ?, description = ?, event_date = ?, event_time = ?, all_day = ?, start_at_millis = ?, discipline_name = ?, color_key = ?, reminder_minutes_json = ?, updated_at = ?
WHERE id = ? AND ra = ?`,
		ev.Title, ev.Description, ev.Date, ev.Time, allDayInt, ev.StartAtMillis, ev.DisciplineName, ev.ColorKey, string(remBytes), now, ev.ID, ra)
	if err != nil {
		return nil, err
	}
	affected, err := res.RowsAffected()
	if err != nil {
		return nil, err
	}
	if affected == 0 {
		return nil, nil
	}
	row := s.db.QueryRow(`
SELECT id, title, description, event_date, event_time, all_day, start_at_millis, discipline_name, color_key, reminder_minutes_json, created_at, updated_at
FROM calendar_events WHERE id = ? AND ra = ?`, ev.ID, ra)
	updated, err := scanCalendarEvent(row)
	if err != nil {
		return nil, err
	}
	return &updated, nil
}

// DeleteCalendarEvent deletes an event owned by ra and reports whether a row was removed.
func (s *Store) DeleteCalendarEvent(ra string, id int64) (bool, error) {
	res, err := s.db.Exec(`DELETE FROM calendar_events WHERE id = ? AND ra = ?`, id, ra)
	if err != nil {
		return false, err
	}
	affected, err := res.RowsAffected()
	if err != nil {
		return false, err
	}
	return affected > 0, nil
}

type rowScanner interface {
	Scan(dest ...any) error
}

func scanCalendarEvent(row rowScanner) (CalendarEvent, error) {
	var ev CalendarEvent
	var allDayInt int
	var remJSON string
	if err := row.Scan(
		&ev.ID,
		&ev.Title,
		&ev.Description,
		&ev.Date,
		&ev.Time,
		&allDayInt,
		&ev.StartAtMillis,
		&ev.DisciplineName,
		&ev.ColorKey,
		&remJSON,
		&ev.CreatedAt,
		&ev.UpdatedAt,
	); err != nil {
		return CalendarEvent{}, err
	}
	ev.AllDay = allDayInt == 1
	ev.ReminderMinutes = []int{}
	if remJSON != "" {
		var parsed []int
		if err := json.Unmarshal([]byte(remJSON), &parsed); err == nil && parsed != nil {
			ev.ReminderMinutes = parsed
		}
	}
	return ev, nil
}
