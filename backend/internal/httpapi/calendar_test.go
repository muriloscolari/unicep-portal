package httpapi

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"unicep-portal/internal/storage"
)

func TestCalendarEventsCRUDValidationAndIsolation(t *testing.T) {
	srv, store, cry := newTestServer(t, "https://cdseducacional.com.br", false)
	encPass, _ := cry.Encrypt("pass123")
	for _, ra := range []string{"1234567", "9998887"} {
		if err := store.UpsertUser(storage.UserRecord{
			RA:                ra,
			Perfil:            "alunograd",
			Name:              "Aluno " + ra,
			EncryptedPassword: encPass,
			PollingEnabled:    true,
		}); err != nil {
			t.Fatalf("upsert %s: %v", ra, err)
		}
	}
	tokA, _, _ := cry.IssueToken("1234567", "alunograd", 1)
	tokB, _, _ := cry.IssueToken("9998887", "alunograd", 1)

	// 1. Preflight CORS inclui PUT/PATCH/DELETE.
	optReq := httptest.NewRequest(http.MethodOptions, "/api/calendar/events", nil)
	optReq.Header.Set("Origin", "https://unicep.muriloscolari.me")
	optRec := httptest.NewRecorder()
	srv.ServeHTTP(optRec, optReq)
	if optRec.Code != http.StatusNoContent {
		t.Fatalf("expected 204 preflight, got %d", optRec.Code)
	}
	allowMethods := optRec.Header().Get("Access-Control-Allow-Methods")
	for _, m := range []string{"PUT", "PATCH", "DELETE"} {
		if !strings.Contains(allowMethods, m) {
			t.Fatalf("expected %s in Access-Control-Allow-Methods, got %q", m, allowMethods)
		}
	}

	// 2. Validações 400.
	badPayloads := []string{
		`{"title":"   ","date":"2026-11-20","time":"19:00"}`,
		`{"title":"Prova","date":"2026-02-29","time":"19:00"}`,
		`{"title":"Prova","date":"2026-11-20","time":"25:00","allDay":false}`,
		`{"title":"Prova","date":"2026-11-20","time":"19:00","colorKey":"pink"}`,
		`{"title":"Prova","date":"2026-11-20","time":"19:00","reminderMinutes":[1,2,3,4,5,6]}`,
	}
	for _, body := range badPayloads {
		req := httptest.NewRequest(http.MethodPost, "/api/calendar/events", strings.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+tokA)
		rec := httptest.NewRecorder()
		srv.ServeHTTP(rec, req)
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("expected 400 for payload %s, got %d: %s", body, rec.Code, rec.Body.String())
		}
	}

	// 3. Criação (201) + cálculo automático de startAtMillis.
	createReq := httptest.NewRequest(
		http.MethodPost,
		"/api/calendar/events",
		strings.NewReader(`{"title":"Prova P2","description":"Sala 4","date":"2026-11-24","time":"19:30","allDay":false,"disciplineName":"DSW","colorKey":"red","reminderMinutes":[1440,60,60]}`),
	)
	createReq.Header.Set("Authorization", "Bearer "+tokA)
	createRec := httptest.NewRecorder()
	srv.ServeHTTP(createRec, createReq)
	if createRec.Code != http.StatusCreated {
		t.Fatalf("expected 201 on create, got %d: %s", createRec.Code, createRec.Body.String())
	}
	var createdPayload struct {
		Event storage.CalendarEvent `json:"event"`
	}
	if err := json.Unmarshal(createRec.Body.Bytes(), &createdPayload); err != nil {
		t.Fatalf("decode created: %v", err)
	}
	ev := createdPayload.Event
	if ev.ID <= 0 || ev.StartAtMillis <= 0 || len(ev.ReminderMinutes) != 2 || ev.ReminderMinutes[0] != 60 || ev.ReminderMinutes[1] != 1440 {
		t.Fatalf("unexpected created event: %+v", ev)
	}

	// 4. Usuário B não vê nem edita/apaga evento do Usuário A.
	listBReq := httptest.NewRequest(http.MethodGet, "/api/calendar/events", nil)
	listBReq.Header.Set("Authorization", "Bearer "+tokB)
	listBRec := httptest.NewRecorder()
	srv.ServeHTTP(listBRec, listBReq)
	if listBRec.Code != http.StatusOK || !strings.Contains(listBRec.Body.String(), `"events":[]`) {
		t.Fatalf("expected empty events for user B, got %d: %s", listBRec.Code, listBRec.Body.String())
	}

	putBReq := httptest.NewRequest(
		http.MethodPut,
		fmt.Sprintf("/api/calendar/events/%d", ev.ID),
		strings.NewReader(`{"title":"Hack","date":"2026-11-24","time":"19:30"}`),
	)
	putBReq.Header.Set("Authorization", "Bearer "+tokB)
	putBRec := httptest.NewRecorder()
	srv.ServeHTTP(putBRec, putBReq)
	if putBRec.Code != http.StatusNotFound {
		t.Fatalf("expected 404 for cross-user PUT, got %d: %s", putBRec.Code, putBRec.Body.String())
	}

	delBReq := httptest.NewRequest(http.MethodDelete, fmt.Sprintf("/api/calendar/events/%d", ev.ID), nil)
	delBReq.Header.Set("Authorization", "Bearer "+tokB)
	delBRec := httptest.NewRecorder()
	srv.ServeHTTP(delBRec, delBReq)
	if delBRec.Code != http.StatusNotFound {
		t.Fatalf("expected 404 for cross-user DELETE, got %d: %s", delBRec.Code, delBRec.Body.String())
	}

	// 5. Usuário A atualiza (200) e depois apaga (200), e segundo DELETE retorna 404.
	putAReq := httptest.NewRequest(
		http.MethodPut,
		fmt.Sprintf("/api/calendar/events/%d", ev.ID),
		strings.NewReader(`{"title":"Prova P2 (Remarcada)","date":"2026-11-26","allDay":true,"colorKey":"orange","reminderMinutes":[1440]}`),
	)
	putAReq.Header.Set("Authorization", "Bearer "+tokA)
	putARec := httptest.NewRecorder()
	srv.ServeHTTP(putARec, putAReq)
	if putARec.Code != http.StatusOK || !strings.Contains(putARec.Body.String(), `"Prova P2 (Remarcada)"`) {
		t.Fatalf("expected 200 on owner PUT, got %d: %s", putARec.Code, putARec.Body.String())
	}

	delAReq := httptest.NewRequest(http.MethodDelete, fmt.Sprintf("/api/calendar/events/%d", ev.ID), nil)
	delAReq.Header.Set("Authorization", "Bearer "+tokA)
	delARec := httptest.NewRecorder()
	srv.ServeHTTP(delARec, delAReq)
	if delARec.Code != http.StatusOK {
		t.Fatalf("expected 200 on owner DELETE, got %d: %s", delARec.Code, delARec.Body.String())
	}

	delAReq2 := httptest.NewRequest(http.MethodDelete, fmt.Sprintf("/api/calendar/events/%d", ev.ID), nil)
	delAReq2.Header.Set("Authorization", "Bearer "+tokA)
	delARec2 := httptest.NewRecorder()
	srv.ServeHTTP(delARec2, delAReq2)
	if delARec2.Code != http.StatusNotFound {
		t.Fatalf("expected 404 on second DELETE, got %d: %s", delARec2.Code, delARec2.Body.String())
	}
}
