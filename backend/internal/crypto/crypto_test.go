package crypto

import "testing"

func TestEncryptDecryptAndJWT(t *testing.T) {
	svc := NewService("super-secret-test-key-32-bytes-long")
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

	tok, _, err := svc.IssueToken("1234567", "alunograd")
	if err != nil {
		t.Fatalf("issue token: %v", err)
	}
	claims, err := svc.VerifyToken(tok)
	if err != nil {
		t.Fatalf("verify token: %v", err)
	}
	if claims.RA != "1234567" || claims.Perfil != "alunograd" {
		t.Fatalf("unexpected claims: %+v", claims)
	}
}
