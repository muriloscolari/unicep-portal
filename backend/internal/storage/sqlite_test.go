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
