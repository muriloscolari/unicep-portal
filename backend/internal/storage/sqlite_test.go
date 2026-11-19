package storage

import "testing"

func TestInsertNotificationDedupeAndD2LSettings(t *testing.T) {
	store, err := Open(t.TempDir())
	if err != nil {
		t.Fatalf("open store: %v", err)
	}
	defer store.Close()

	if err := store.UpsertUser(UserRecord{
		RA:                "1234567",
		Perfil:            "alunograd",
		Name:              "Aluno Exemplo",
		EncryptedPassword: "enc",
		PollingEnabled:    true,
	}); err != nil {
		t.Fatalf("upsert user: %v", err)
	}
	u, err := store.GetUser("1234567")
	if err != nil || u == nil || !u.D2LPollingEnabled {
		t.Fatalf("expected D2LPollingEnabled=true by default, got %+v err=%v", u, err)
	}
	if err := store.SetD2LPollingEnabled("1234567", false); err != nil {
		t.Fatalf("set d2l polling disabled: %v", err)
	}
	u2, err := store.GetUser("1234567")
	if err != nil || u2 == nil || u2.D2LPollingEnabled {
		t.Fatalf("expected D2LPollingEnabled=false, got %+v err=%v", u2, err)
	}

	ev := Notification{
		RA:             "1234567",
		PeriodKey:      "50001",
		DisciplineCode: "50001:10",
		DisciplineName: "DSW",
		Field:          "Trabalho 1",
		OldValue:       "",
		NewValue:       "Prazo: 20/11 às 23:59",
		Kind:           "d2l_assignment",
	}
	id1, err := store.InsertNotification(ev)
	if err != nil || id1 == 0 {
		t.Fatalf("first insert failed: id=%d err=%v", id1, err)
	}
	id2, err := store.InsertNotification(ev)
	if err != nil {
		t.Fatalf("duplicate insert errored: %v", err)
	}
	if id2 != 0 {
		t.Fatalf("expected duplicate insert to be ignored (id=0), got id=%d", id2)
	}
	list, err := store.ListNotifications("1234567", false, 20)
	if err != nil || len(list) != 1 {
		t.Fatalf("expected 1 deduplicated notification, got %d err=%v", len(list), err)
	}

	// Test notifications must still allow repeated triggers.
	testEv := Notification{
		RA:             "1234567",
		PeriodKey:      "test",
		DisciplineCode: "TEST001",
		DisciplineName: "Teste de Notificação",
		Field:          "P1",
		OldValue:       "-",
		NewValue:       "9,5",
		Kind:           "grade",
	}
	tid1, err := store.InsertNotification(testEv)
	tid2, err2 := store.InsertNotification(testEv)
	if err != nil || err2 != nil || tid1 == 0 || tid2 == 0 {
		t.Fatalf("expected test notifications not to be suppressed: tid1=%d (%v), tid2=%d (%v)", tid1, err, tid2, err2)
	}
}

func TestD2LContentQuizzesSnapshotsAndCalendarEventsCRUD(t *testing.T) {
	store, err := Open(t.TempDir())
	if err != nil {
		t.Fatalf("open store: %v", err)
	}
	defer store.Close()

	if err := store.SaveD2LContentSnapshot("1234567", `[{"topicId":1,"title":"Aula 1"}]`); err != nil {
		t.Fatalf("save content snapshot: %v", err)
	}
	if got, err := store.GetD2LContentSnapshot("1234567"); err != nil || got != `[{"topicId":1,"title":"Aula 1"}]` {
		t.Fatalf("get content snapshot: got=%q err=%v", got, err)
	}
	if err := store.SaveD2LQuizzesSnapshot("1234567", `[{"quizId":5,"name":"Quiz"}]`); err != nil {
		t.Fatalf("save quizzes snapshot: %v", err)
	}
	if got, err := store.GetD2LQuizzesSnapshot("1234567"); err != nil || got != `[{"quizId":5,"name":"Quiz"}]` {
		t.Fatalf("get quizzes snapshot: got=%q err=%v", got, err)
	}

	ev, err := store.CreateCalendarEvent(CalendarEvent{
		RA:              "1234567",
		Title:           "Prova P2",
		Description:     "Capítulos 1 a 4",
		Date:            "2026-11-24",
		Time:            "19:30",
		AllDay:          false,
		StartAtMillis:   1795559400000,
		DisciplineName:  "DSW",
		ColorKey:        "red",
		ReminderMinutes: []int{60, 1440},
	})
	if err != nil || ev.ID <= 0 {
		t.Fatalf("create calendar event failed: ev=%+v err=%v", ev, err)
	}
	if count, err := store.CountCalendarEvents("1234567"); err != nil || count != 1 {
		t.Fatalf("expected count=1, got %d err=%v", count, err)
	}

	// Isolamento entre RAs
	if upd, err := store.UpdateCalendarEvent("9999999", CalendarEvent{ID: ev.ID, Title: "Hack"}); err != nil || upd != nil {
		t.Fatalf("expected cross-RA update to return nil, got %+v err=%v", upd, err)
	}
	if deleted, err := store.DeleteCalendarEvent("9999999", ev.ID); err != nil || deleted {
		t.Fatalf("expected cross-RA delete to return false, got %v err=%v", deleted, err)
	}

	updated, err := store.UpdateCalendarEvent("1234567", CalendarEvent{
		ID:              ev.ID,
		Title:           "Prova P2 Atualizada",
		Description:     "Sala 10",
		Date:            "2026-11-25",
		Time:            "20:00",
		AllDay:          false,
		StartAtMillis:   1795647600000,
		DisciplineName:  "DSW",
		ColorKey:        "orange",
		ReminderMinutes: []int{30},
	})
	if err != nil || updated == nil || updated.Title != "Prova P2 Atualizada" {
		t.Fatalf("update failed: updated=%+v err=%v", updated, err)
	}

	list, err := store.ListCalendarEvents("1234567")
	if err != nil || len(list) != 1 || len(list[0].ReminderMinutes) != 1 || list[0].ReminderMinutes[0] != 30 {
		t.Fatalf("unexpected calendar event list: %+v err=%v", list, err)
	}

	deleted, err := store.DeleteCalendarEvent("1234567", ev.ID)
	if err != nil || !deleted {
		t.Fatalf("delete failed: deleted=%v err=%v", deleted, err)
	}
	listAfter, err := store.ListCalendarEvents("1234567")
	if err != nil || len(listAfter) != 0 {
		t.Fatalf("expected empty list after delete, got %+v err=%v", listAfter, err)
	}
}
