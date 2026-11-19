package poller

import (
	"context"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	"unicep-portal/internal/d2l"
	"unicep-portal/internal/portal"
	"unicep-portal/internal/storage"
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

func TestDiffD2LContent(t *testing.T) {
	prev := []d2l.ContentTopicSnapshot{
		{OrgUnitID: 101, OrgUnitName: "Engenharia", ModuleTitle: "Aula 1", TopicID: 10, Title: "Slides 1.pdf"},
	}
	curr := []d2l.ContentTopicSnapshot{
		{OrgUnitID: 101, OrgUnitName: "Engenharia", ModuleTitle: "Aula 1", TopicID: 10, Title: "Slides 1.pdf"},
		{OrgUnitID: 101, OrgUnitName: "Engenharia", ModuleTitle: "Aula 2", TopicID: 11, Title: "Lista 2.pdf"},
		{OrgUnitID: 102, OrgUnitName: "", ModuleTitle: "", TopicID: 20, Title: "Ementa"},
	}
	events := DiffD2LContent("1234567", prev, curr)
	if len(events) != 2 {
		t.Fatalf("expected 2 content events, got %d: %+v", len(events), events)
	}
	if events[0].Kind != "d2l_content" || events[0].Field != "Lista 2.pdf" || events[0].NewValue != "Aula 2" || events[0].DisciplineName != "Engenharia" {
		t.Fatalf("unexpected first content event: %+v", events[0])
	}
	if events[1].DisciplineName != "Sala Virtual" || events[1].NewValue != "Novo material disponível" {
		t.Fatalf("unexpected fallback content event: %+v", events[1])
	}
}

func TestDiffD2LQuizzes(t *testing.T) {
	prev := []d2l.QuizItem{
		{QuizID: 1, OrgUnitID: 101, OrgUnitName: "DSW", Name: "Quiz Antigo", IsActive: true},
	}
	curr := []d2l.QuizItem{
		{QuizID: 1, OrgUnitID: 101, OrgUnitName: "DSW", Name: "Quiz Antigo", IsActive: true},
		{QuizID: 2, OrgUnitID: 101, OrgUnitName: "DSW", Name: "Quiz Rascunho", IsActive: false, DueDate: "2099-11-25T23:59:00Z"},
		{QuizID: 3, OrgUnitID: 101, OrgUnitName: "DSW", Name: "Quiz Expirado", IsActive: true, DueDate: "2020-01-01T12:00:00Z"},
		{QuizID: 4, OrgUnitID: 101, OrgUnitName: "DSW", Name: "Quiz P2", IsActive: true, DueDate: "2099-11-25T23:59:00Z"},
	}
	events := DiffD2LQuizzes("1234567", prev, curr)
	if len(events) != 1 {
		t.Fatalf("expected 1 active non-expired quiz event, got %d: %+v", len(events), events)
	}
	if events[0].Kind != "d2l_quiz" || events[0].Field != "Quiz P2" || events[0].NewValue != "Prazo: 25/11 às 20:59" {
		t.Fatalf("unexpected quiz event: %+v", events[0])
	}
}

func TestMergeByCourseOnPartialFailure(t *testing.T) {
	prev := []d2l.ContentTopicSnapshot{
		{OrgUnitID: 101, TopicID: 1, Title: "Antigo 101"},
		{OrgUnitID: 202, TopicID: 2, Title: "Preservado 202"},
	}
	curr := []d2l.ContentTopicSnapshot{
		{OrgUnitID: 101, TopicID: 1, Title: "Antigo 101"},
		{OrgUnitID: 101, TopicID: 3, Title: "Novo 101"},
	}
	merged := mergeByCourse(prev, curr, false, []int64{101}, func(t d2l.ContentTopicSnapshot) int64 { return t.OrgUnitID })
	if len(merged) != 3 {
		t.Fatalf("expected 3 merged items (2 from 101 + 1 preserved from 202), got %+v", merged)
	}
}

func TestSyncD2LSeedsAndDetectsNewPostsEvenWithOptional403(t *testing.T) {
	store, err := storage.Open(t.TempDir())
	if err != nil {
		t.Fatalf("open store: %v", err)
	}
	defer store.Close()

	if err := store.UpsertUser(storage.UserRecord{
		RA:                "1234567",
		Perfil:            "alunograd",
		Name:              "Aluno Exemplo",
		EncryptedPassword: "enc",
		PollingEnabled:    true,
	}); err != nil {
		t.Fatalf("upsert user: %v", err)
	}

	var phase atomic.Int32
	phase.Store(1)

	mux := http.NewServeMux()
	mux.HandleFunc("/d2l/login", func(w http.ResponseWriter, r *http.Request) { _, _ = w.Write([]byte("login")) })
	mux.HandleFunc("/d2l/lp/auth/login/login.d2l", func(w http.ResponseWriter, r *http.Request) {
		http.SetCookie(w, &http.Cookie{Name: "d2lSessionVal", Value: "ok", Path: "/"})
		http.Redirect(w, r, "/d2l/home", http.StatusFound)
	})
	mux.HandleFunc("/d2l/home", func(w http.ResponseWriter, r *http.Request) { _, _ = w.Write([]byte("home")) })
	mux.HandleFunc("/d2l/api/lp/1.0/users/whoami", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"Identifier":"10001","FirstName":"ALUNO","LastName":"EXEMPLO"}`))
	})
	mux.HandleFunc("/d2l/le/manageCourses/api/mysemesters", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`[{"OrgUnitId":"48648","Name":"2/2026","Code":"2/2026"}]`))
	})
	mux.HandleFunc("/d2l/le/manageCourses/api/mycourses", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"Courses":[
			{"OrgUnitId":"101","Name":"Engenharia","Code":"ENG","IsActive":true,"SemesterName":"2/2026","CanAccessCourse":true},
			{"OrgUnitId":"102","Name":"Sem Dropbox","Code":"SD","IsActive":true,"SemesterName":"2/2026","CanAccessCourse":true}
		]}`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/6606/news/", func(w http.ResponseWriter, r *http.Request) { _, _ = w.Write([]byte(`[]`)) })
	mux.HandleFunc("/d2l/api/le/1.74/calendar/events/myEvents/", func(w http.ResponseWriter, r *http.Request) { _, _ = w.Write([]byte(`{"Objects":[]}`)) })

	mux.HandleFunc("/d2l/api/le/1.74/101/dropbox/folders/", func(w http.ResponseWriter, r *http.Request) {
		if phase.Load() == 1 {
			_, _ = w.Write([]byte(`[]`))
			return
		}
		_, _ = w.Write([]byte(`[{"Id":50,"Name":"Atividade Nova","DueDate":"2099-12-01T23:59:00Z"}]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/101/dropbox/folders/50/submissions/", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`[]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/101/news/", func(w http.ResponseWriter, r *http.Request) {
		if phase.Load() == 1 {
			_, _ = w.Write([]byte(`[]`))
			return
		}
		_, _ = w.Write([]byte(`[{"Id":88,"Title":"Material postado","Body":{"Text":"Leiam o PDF"}}]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/101/quizzes/", func(w http.ResponseWriter, r *http.Request) {
		if phase.Load() == 1 {
			_, _ = w.Write([]byte(`{"Objects":[]}`))
			return
		}
		_, _ = w.Write([]byte(`{"Objects":[{"QuizId":77,"Name":"Quiz Novo","IsActive":true,"DueDate":"2099-12-02T23:59:00Z"}]}`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/101/content/toc", func(w http.ResponseWriter, r *http.Request) {
		if phase.Load() == 1 {
			_, _ = w.Write([]byte(`{"Modules":[]}`))
			return
		}
		_, _ = w.Write([]byte(`{"Modules":[{"ModuleId":1,"Title":"Semana 1","Topics":[{"TopicId":999,"Title":"Apostila.pdf","TypeIdentifier":"File"}],"Modules":[]}]}`))
	})

	// Curso 102 com 403 no Dropbox (não pode travar notificações do curso 101!)
	mux.HandleFunc("/d2l/api/le/1.74/102/dropbox/folders/", func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(http.StatusForbidden) })
	mux.HandleFunc("/d2l/api/le/1.74/102/news/", func(w http.ResponseWriter, r *http.Request) { _, _ = w.Write([]byte(`[]`)) })
	mux.HandleFunc("/d2l/api/le/1.74/102/quizzes/", func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(http.StatusNotFound) })
	mux.HandleFunc("/d2l/api/le/1.74/102/content/toc", func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(http.StatusNotFound) })

	srv := httptest.NewServer(mux)
	defer srv.Close()

	dm := d2l.NewManager(srv.URL, func(string) (string, error) { return "secret", nil })
	w := NewWorker(store, nil, nil, dm, 0, true)
	ctx := context.Background()

	// Seed inicial (como no login): grava snapshots e cria 0 notificações.
	createdSeed, err := w.syncD2L(ctx, "1234567", true, true)
	if err != nil || createdSeed != 0 {
		t.Fatalf("seed syncD2L failed: created=%d err=%v", createdSeed, err)
	}

	// Professor posta atividade, aviso, quiz e material.
	phase.Store(2)
	created, err := w.syncD2L(ctx, "1234567", false, true)
	if err != nil {
		t.Fatalf("poll syncD2L failed: %v", err)
	}
	if created != 4 {
		t.Fatalf("expected 4 notifications (assignment, announcement, quiz, content), got %d", created)
	}
	notifs, err := store.ListNotifications("1234567", false, 20)
	if err != nil || len(notifs) != 4 {
		t.Fatalf("expected 4 stored notifications, got %d err=%v", len(notifs), err)
	}
}
