package crypto

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"io"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// Service handles AES-256-GCM credential encryption and HS256 JWT tokens.
type Service struct {
	key []byte
}

// Claims represents the authenticated student inside the BFF JWT.
type Claims struct {
	RA     string `json:"ra"`
	Perfil string `json:"perfil"`
	jwt.RegisteredClaims
}

// NewService derives a 32-byte key from the application secret.
func NewService(secret string) *Service {
	sum := sha256.Sum256([]byte(secret))
	return &Service{key: sum[:]}
}

// Encrypt encrypts plaintext using AES-256-GCM and encodes nonce+ciphertext as base64.
func (s *Service) Encrypt(plaintext string) (string, error) {
	block, err := aes.NewCipher(s.key)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", err
	}
	sealed := gcm.Seal(nonce, nonce, []byte(plaintext), nil)
	return base64.StdEncoding.EncodeToString(sealed), nil
}

// Decrypt decodes and decrypts a base64 AES-256-GCM payload.
func (s *Service) Decrypt(encoded string) (string, error) {
	raw, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil {
		return "", err
	}
	block, err := aes.NewCipher(s.key)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	if len(raw) < gcm.NonceSize() {
		return "", errors.New("ciphertext too short")
	}
	nonce, ciphertext := raw[:gcm.NonceSize()], raw[gcm.NonceSize():]
	plain, err := gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return "", err
	}
	return string(plain), nil
}

// IssueToken issues a 30-day HS256 JWT for the authenticated student.
func (s *Service) IssueToken(ra, perfil string) (string, time.Time, error) {
	now := time.Now().UTC()
	exp := now.Add(30 * 24 * time.Hour)
	claims := Claims{
		RA:     ra,
		Perfil: perfil,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   ra,
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(exp),
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, err := token.SignedString(s.key)
	return signed, exp, err
}

// VerifyToken validates and parses an HS256 JWT token string.
func (s *Service) VerifyToken(tokenStr string) (*Claims, error) {
	parsed, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, errors.New("unexpected signing method")
		}
		return s.key, nil
	})
	if err != nil {
		return nil, err
	}
	claims, ok := parsed.Claims.(*Claims)
	if !ok || !parsed.Valid {
		return nil, errors.New("invalid token")
	}
	return claims, nil
}
