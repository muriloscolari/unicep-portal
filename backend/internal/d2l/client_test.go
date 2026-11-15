package d2l

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
)

func TestD2LClientPassiveFlow(t *testing.T) {
	var loginCalls atomic.Int32
	var mutatingCalls atomic.Int32

	mux := http.NewServeMux()
	mux.HandleFunc("/d2l/login", func(w http.ResponseWriter, r *http.Request) {
		http.SetCookie(w, &http.Cookie{Name: "d2lSameSiteCanaryA", Value: "1", Path: "/"})
		_, _ = w.Write([]byte("<html>login</html>"))
	})
	mux.HandleFunc("/d2l/lp/auth/login/login.d2l", func(w http.ResponseWriter, r *http.Request) {
		loginCalls.Add(1)
		_ = r.ParseForm()
		if r.Form.Get("userName") != "1234567" || r.Form.Get("password") != "secret-pass" {
			http.Redirect(w, r, "/d2l/login?failed=1", http.StatusFound)
			return
		}
		http.SetCookie(w, &http.Cookie{Name: "d2lSessionVal", Value: "valid-sess", Path: "/"})
		http.Redirect(w, r, "/d2l/home", http.StatusFound)
	})
	mux.HandleFunc("/d2l/home", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("<html>home</html>"))
	})

	requireSess := func(w http.ResponseWriter, r *http.Request) bool {
		if r.Method != http.MethodGet {
			mutatingCalls.Add(1)
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return false
		}
		c, err := r.Cookie("d2lSessionVal")
		if err != nil || c.Value != "valid-sess" {
			http.Redirect(w, r, "/d2l/login?sessionExpired=1", http.StatusFound)
			return false
		}
		return true
	}

	mux.HandleFunc("/d2l/api/lp/1.0/users/whoami", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`{"Identifier":"10001","FirstName":"ALUNO","LastName":"EXEMPLO","UniqueName":"1234567"}`))
	})
	mux.HandleFunc("/d2l/le/manageCourses/api/mysemesters", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`[{"OrgUnitId":"48648","Name":"2/2026","Code":"2/2026"}]`))
	})
	mux.HandleFunc("/d2l/le/manageCourses/api/mycourses", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`{"Courses":[{"OrgUnitId":"50001","Name":"DESENVOLVIMENTO WEB","Code":"DSW","IsActive":true,"SemesterName":"2/2026","CanAccessCourse":true},{"OrgUnitId":"50002","Name":"MCTI","Code":"MCTI","IsActive":true,"SemesterName":"1/2026","CanAccessCourse":true}]}`))
	})
	mux.HandleFunc("/d2l/le/1.74/50002/", http.NotFound)
	mux.HandleFunc("/d2l/api/le/1.74/50002/dropbox/folders/", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`[{"Id":999,"Name":"Planilha com grafico","DueDate":"2026-03-23T23:59:00Z"}]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/6606/news/", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`[{"Id":1,"Title":"Aviso Geral","Body":{"Text":"","Html":"<p>Olá <b>alunos</b><script>alert(1)</script></p>"},"StartDate":"2026-11-10T10:00:00Z"}]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/50001/news/", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`[{"Id":2,"Title":"Prova P2","Body":{"Text":"Estudem capítulo 3"},"StartDate":"2026-11-12T10:00:00Z"}]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/calendar/events/myEvents/", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		if r.URL.Query().Get("orgUnitIdsCSV") == "" {
			http.Error(w, "missing orgUnitIdsCSV", http.StatusBadRequest)
			return
		}
		_, _ = w.Write([]byte(`{"Objects":[{"CalendarEventId":99,"OrgUnitId":50001,"Title":"Entrega Trabalho","StartDateTime":"2026-11-20T23:59:00Z"}]}`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/50001/dropbox/folders/", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`[
			{"Id":10,"Name":"Trabalho 1","CustomInstructions":{"Text":"","Html":"<p>Enviar PDF</p>"},"DueDate":"2026-11-20T23:59:00Z","Attachments":[{"FileId":77,"FileName":"enunciado.pdf","Size":2048}],"Assessment":{"ScoreDenominator":10.0}},
			{"Id":11,"Name":"Trabalho Expirado","CustomInstructions":{"Text":"Prazo encerrado"},"DueDate":"2020-01-10T23:59:00Z","Attachments":[]}
		]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/50001/dropbox/folders/10/submissions/", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`[{"Status":3,"Feedback":{"Score":9.5,"Feedback":{"Text":"Excelente"}},"Submissions":[{"Id":55,"SubmissionDate":"2026-11-14T18:00:00Z","Files":[{"FileId":88,"FileName":"entrega.pdf","Size":1024}]}]}]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/50001/dropbox/folders/11/submissions/", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`[]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/50001/content/toc", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`{"Modules":[{"ModuleId":1,"Title":"Unidade 1","Topics":[{"TopicId":501,"Title":"Slides Aula 1","TypeIdentifier":"File","Url":"/content/enforced/50001-DSW/aula1.pdf","Unread":true}],"Modules":[]}]}`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/50001/grades/values/myGradeValues/", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`[{"GradeObjectIdentifier":"1","GradeObjectName":"Trabalho 1","PointsNumerator":9.5,"PointsDenominator":10,"DisplayedGrade":"9.5 / 10"}]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/50001/quizzes/", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`{"Objects":[{"QuizId":301,"Name":"Quiz 1","Description":{"Text":{"Text":"Revisão"}},"IsActive":true}]}`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/50001/discussions/forums/", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`[{"ForumId":401,"Name":"Dúvidas","Description":{"Text":"Canal de dúvidas"}}]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/50001/calendar/events/", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		_, _ = w.Write([]byte(`[]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/50001/content/topics/501/file", func(w http.ResponseWriter, r *http.Request) {
		if !requireSess(w, r) {
			return
		}
		w.Header().Set("Content-Type", "application/pdf")
		_, _ = w.Write([]byte("%PDF-1.4"))
	})

	srv := httptest.NewServer(mux)
	defer srv.Close()

	mgr := NewManager(srv.URL, func(ra string) (string, error) {
		if ra == "1234567" {
			return "secret-pass", nil
		}
		return "wrong", nil
	})

	ctx := context.Background()
	ov, err := mgr.GetOverview(ctx, "1234567")
	if err != nil {
		t.Fatalf("GetOverview failed: %v", err)
	}
	if ov.UserName != "ALUNO EXEMPLO" || len(ov.Courses) != 2 || len(ov.UpcomingAssignments) != 1 {
		t.Fatalf("unexpected overview: %+v", ov)
	}
	if ov.UpcomingAssignments[0].Status != "graded" || ov.UpcomingAssignments[0].Score == nil || *ov.UpcomingAssignments[0].Score != 9.5 {
		t.Fatalf("unexpected assignment status: %+v", ov.UpcomingAssignments[0])
	}
	if len(ov.Announcements) != 2 || strings.Contains(ov.Announcements[1].Body, "alert") {
		t.Fatalf("unexpected sanitized announcements: %+v", ov.Announcements)
	}

	detail, err := mgr.GetCourseDetail(ctx, "1234567", 50001)
	if err != nil {
		t.Fatalf("GetCourseDetail failed: %v", err)
	}
	if len(detail.Assignments) != 2 || detail.Assignments[1].Status != "expired" || detail.Assignments[1].StatusLabel != "Encerrada" {
		t.Fatalf("expected expired past-due assignment in detail: %+v", detail.Assignments)
	}
	if len(detail.Modules) != 1 || detail.Modules[0].Topics[0].DownloadPath == "" {
		t.Fatalf("unexpected modules: %+v", detail.Modules)
	}
	if len(detail.Grades) != 1 || len(detail.Quizzes) != 1 || len(detail.Discussions) != 1 {
		t.Fatalf("expected grades, quizzes, discussions populated: %+v", detail)
	}

	raw, ct, _, err := mgr.FetchRaw(ctx, "1234567", detail.Modules[0].Topics[0].DownloadPath)
	if err != nil || string(raw) != "%PDF-1.4" || ct != "application/pdf" {
		t.Fatalf("unexpected FetchRaw result: err=%v ct=%q len=%d", err, ct, len(raw))
	}

	if mutatingCalls.Load() != 0 {
		t.Fatalf("expected zero non-GET API calls, got %d", mutatingCalls.Load())
	}
	if loginCalls.Load() != 1 {
		t.Fatalf("expected cached session with 1 login, got %d", loginCalls.Load())
	}

	// Wrong credentials must return ErrInvalidCredentials.
	_, err = mgr.GetOverview(ctx, "9999999")
	if !errors.Is(err, ErrInvalidCredentials) {
		t.Fatalf("expected ErrInvalidCredentials, got %v", err)
	}
}

func TestIsAllowedFilePath(t *testing.T) {
	allowed := []string{
		"/d2l/api/le/1.74/50001/content/topics/501/file?stream=false",
		"/d2l/api/le/1.74/50001/dropbox/folders/10/attachments/77",
		"/content/enforced/50001-DSW/aula1.pdf?ou=50001",
	}
	for _, p := range allowed {
		if !IsAllowedFilePath(p) {
			t.Errorf("expected path %q to be allowed", p)
		}
	}

	blocked := []string{
		"",
		"https://evil.example/file.pdf",
		"/d2l/api/le/1.74/50001/dropbox/folders/10/submissions/",
		"/d2l/api/le/1.74/50001/content/../../secret",
		"/d2l/lp/auth/login/login.d2l",
	}
	for _, p := range blocked {
		if IsAllowedFilePath(p) {
			t.Errorf("expected path %q to be blocked", p)
		}
	}
}

func TestD2LReloginPartialUnknownAndTooLarge(t *testing.T) {
	var loginCalls atomic.Int32
	var forceUnauth atomic.Bool

	mux := http.NewServeMux()
	mux.HandleFunc("/d2l/login", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("login"))
	})
	mux.HandleFunc("/d2l/lp/auth/login/login.d2l", func(w http.ResponseWriter, r *http.Request) {
		loginCalls.Add(1)
		http.SetCookie(w, &http.Cookie{Name: "d2lSessionVal", Value: "s" + string(rune('0'+loginCalls.Load())), Path: "/"})
		http.Redirect(w, r, "/d2l/home", http.StatusFound)
	})
	mux.HandleFunc("/d2l/home", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("ok"))
	})
	mux.HandleFunc("/d2l/api/lp/1.0/users/whoami", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"Identifier":"10001","FirstName":"ALUNO","LastName":"EXEMPLO"}`))
	})
	mux.HandleFunc("/d2l/le/manageCourses/api/mysemesters", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`[{"OrgUnitId":"48648","Name":"2/2026","Code":"2/2026"}]`))
	})
	mux.HandleFunc("/d2l/le/manageCourses/api/mycourses", func(w http.ResponseWriter, r *http.Request) {
		if forceUnauth.Swap(false) {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		_, _ = w.Write([]byte(`{"Courses":[
			{"OrgUnitId":"100","Name":"Course OK","Code":"C1","IsActive":true,"SemesterName":"2/2026","CanAccessCourse":true},
			{"OrgUnitId":"200","Name":"Course Fail","Code":"C2","IsActive":true,"SemesterName":"2/2026","CanAccessCourse":true}
		]}`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/6606/news/", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`[]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/calendar/events/myEvents/", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"Objects":[]}`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/100/dropbox/folders/", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`[]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/100/news/", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`[]`))
	})
	mux.HandleFunc("/d2l/api/le/1.74/200/dropbox/folders/", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	})
	mux.HandleFunc("/d2l/api/le/1.74/200/news/", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`[]`))
	})
	mux.HandleFunc("/content/enforced/100/huge.pdf", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Length", "20971520")
		w.WriteHeader(http.StatusOK)
	})

	srv := httptest.NewServer(mux)
	defer srv.Close()

	mgr := NewManager(srv.URL, func(string) (string, error) { return "secret", nil })
	ctx := context.Background()

	// Partial course assignment failure sets Complete=false but keeps healthy course data.
	ov, err := mgr.GetOverviewWithOptions(ctx, "1234567", true)
	if err != nil {
		t.Fatalf("expected partial overview to succeed, got %v", err)
	}
	if ov.Complete {
		t.Fatalf("expected Complete=false on partial dropbox failure")
	}

	// Next call hitting 401 on mycourses triggers re-login cleanly.
	forceUnauth.Store(true)
	_, err = mgr.GetOverviewWithOptions(ctx, "1234567", true)
	if err != nil {
		t.Fatalf("expected re-login after 401 to succeed, got %v", err)
	}
	if loginCalls.Load() != 2 {
		t.Fatalf("expected 2 logins (initial + 401 relogin), got %d", loginCalls.Load())
	}

	// Unknown orgUnitId returns ErrCourseNotAvailable.
	if _, err := mgr.GetCourseDetail(ctx, "1234567", 999999); !errors.Is(err, ErrCourseNotAvailable) {
		t.Fatalf("expected ErrCourseNotAvailable for unknown OU, got %v", err)
	}

	// Oversized file returns ErrFileTooLarge.
	if _, _, _, err := mgr.FetchRaw(ctx, "1234567", "/content/enforced/100/huge.pdf"); !errors.Is(err, ErrFileTooLarge) {
		t.Fatalf("expected ErrFileTooLarge, got %v", err)
	}
}

func TestParseD2LTimeWithoutOffset(t *testing.T) {
	tm, ok := parseD2LTime("2026-11-20T23:59:00")
	if !ok {
		t.Fatal("expected naive timestamp to parse")
	}
	// America/Sao_Paulo (-03:00): 23:59 local == 02:59Z on 2026-11-21.
	want := "2026-11-21T02:59:00Z"
	if got := tm.UTC().Format("2006-01-02T15:04:05Z"); got != want {
		t.Fatalf("expected %s, got %s", want, got)
	}
}
