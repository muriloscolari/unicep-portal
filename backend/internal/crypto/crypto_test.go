package crypto

import (
	"crypto/sha256"
	"testing"
)

func TestEncryptDecryptAndJWT(t *testing.T) {
	secret := "super-secret-test-key-32-bytes-long"
	svc := NewService(secret)

	cipher, err := svc.Encrypt("Senha@19")
	if err != nil {
		t.Fatalf("encrypt: %v", err)
	}
	plain, err := svc.Decrypt(cipher)
	if err != nil {
		t.Fatalf("decrypt: %v", err)
	}
	if plain != "Senha@19" {
		t.Fatalf("expected Senha@19, got %s", plain)
	}

	// Verify legacy single-SHA256 encrypted values remain decryptable for migration.
	legacySum := sha256.Sum256([]byte(secret))
	legacyCipher, err := sealAESGCM(legacySum[:], "Legacy@1")
	if err != nil {
		t.Fatalf("legacy encrypt: %v", err)
	}
	legacyPlain, err := svc.Decrypt(legacyCipher)
	if err != nil || legacyPlain != "Legacy@1" {
		t.Fatalf("expected legacy decrypt to succeed, got %q err=%v", legacyPlain, err)
	}

	// Verify encKey and jwtKey are separated (legacy key cannot decrypt new ciphertexts).
	if _, err := openAESGCM(legacySum[:], []byte(cipher)); err == nil {
		t.Fatal("expected domain-separated ciphertext to differ from legacy key")
	}

	tok, _, err := svc.IssueToken("1234567", "alunograd", 2)
	if err != nil {
		t.Fatalf("issue token: %v", err)
	}
	claims, err := svc.VerifyToken(tok)
	if err != nil {
		t.Fatalf("verify token: %v", err)
	}
	if claims.RA != "1234567" || claims.Perfil != "alunograd" || claims.TokenVersion != 2 {
		t.Fatalf("unexpected claims: %+v", claims)
	}
}

func TestProxyTicketsAreSingleUseAndPathBound(t *testing.T) {
	svc := NewService("super-secret-test-key-32-bytes-long")
	path := "/ambiente_aluno/Localizacao_salas_v1.pdf"

	ticket, err := svc.IssueProxyTicket("1234567", path)
	if err != nil {
		t.Fatalf("issue ticket: %v", err)
	}

	ra, err := svc.ConsumeProxyTicket(ticket, path)
	if err != nil || ra != "1234567" {
		t.Fatalf("expected valid ticket consumption, got ra=%q err=%v", ra, err)
	}

	// Replay attempt must fail.
	if _, err := svc.ConsumeProxyTicket(ticket, path); err == nil {
		t.Fatal("expected second use of single-use ticket to fail")
	}

	// Path mismatch must fail and burn the ticket.
	ticket2, _ := svc.IssueProxyTicket("1234567", path)
	if _, err := svc.ConsumeProxyTicket(ticket2, "/ambiente_aluno/other.pdf"); err == nil {
		t.Fatal("expected mismatched path to fail")
	}
	if _, err := svc.ConsumeProxyTicket(ticket2, path); err == nil {
		t.Fatal("expected burned ticket after mismatch to fail")
	}
}
