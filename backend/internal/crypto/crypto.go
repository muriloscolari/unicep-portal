package crypto

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"io"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	jwtIssuer   = "unicep-portal"
	jwtAudience = "unicep-client"
	ticketTTL   = 60 * time.Second
	maxTickets  = 2048
)

type proxyTicketEntry struct {
	ra        string
	path      string
	expiresAt time.Time
}

// Service handles AES-256-GCM credential encryption, HS256 JWT tokens, and single-use proxy tickets.
type Service struct {
	encKey    []byte
	jwtKey    []byte
	legacyKey []byte

	ticketMu sync.Mutex
	tickets  map[string]proxyTicketEntry
}

// Claims represents the authenticated student inside the BFF JWT.
type Claims struct {
	RA           string `json:"ra"`
	Perfil       string `json:"perfil"`
	TokenVersion int    `json:"tv"`
	jwt.RegisteredClaims
}

// NewService derives domain-separated 32-byte keys from the application secret.
func NewService(secret string) *Service {
	legacy := sha256.Sum256([]byte(secret))
	return &Service{
		encKey:    deriveSubkey(secret, "unicep-portal:aes-gcm:v1"),
		jwtKey:    deriveSubkey(secret, "unicep-portal:jwt-hs256:v1"),
		legacyKey: legacy[:],
		tickets:   make(map[string]proxyTicketEntry),
	}
}

func deriveSubkey(secret, label string) []byte {
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(label))
	return mac.Sum(nil)
}

// Encrypt encrypts plaintext using the dedicated AES-256-GCM key and encodes nonce+ciphertext as base64.
func (s *Service) Encrypt(plaintext string) (string, error) {
	return sealAESGCM(s.encKey, plaintext)
}

// Decrypt decodes and decrypts a base64 AES-256-GCM payload, falling back to the legacy key format if needed.
func (s *Service) Decrypt(encoded string) (string, error) {
	raw, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil {
		return "", err
	}
	if plain, err := openAESGCM(s.encKey, raw); err == nil {
		return plain, nil
	}
	// Compatibility fallback for credentials encrypted with v1.0/v1.1 single-key derivation.
	return openAESGCM(s.legacyKey, raw)
}

func sealAESGCM(key []byte, plaintext string) (string, error) {
	block, err := aes.NewCipher(key)
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

func openAESGCM(key, raw []byte) (string, error) {
	block, err := aes.NewCipher(key)
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

// IssueToken issues a 30-day HS256 JWT bound to the user's current tokenVersion.
func (s *Service) IssueToken(ra, perfil string, tokenVersion int) (string, time.Time, error) {
	now := time.Now().UTC()
	exp := now.Add(30 * 24 * time.Hour)
	jtiBytes := make([]byte, 16)
	_, _ = io.ReadFull(rand.Reader, jtiBytes)

	claims := Claims{
		RA:           ra,
		Perfil:       perfil,
		TokenVersion: tokenVersion,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    jwtIssuer,
			Audience:  jwt.ClaimStrings{jwtAudience},
			Subject:   ra,
			ID:        hex.EncodeToString(jtiBytes),
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(exp),
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, err := token.SignedString(s.jwtKey)
	return signed, exp, err
}

// VerifyToken validates signature algorithm, issuer, audience, expiration, and parses claims.
func (s *Service) VerifyToken(tokenStr string) (*Claims, error) {
	parsed, err := jwt.ParseWithClaims(
		tokenStr,
		&Claims{},
		func(t *jwt.Token) (any, error) {
			return s.jwtKey, nil
		},
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
		jwt.WithIssuer(jwtIssuer),
		jwt.WithAudience(jwtAudience),
		jwt.WithExpirationRequired(),
	)
	if err != nil {
		return nil, err
	}
	claims, ok := parsed.Claims.(*Claims)
	if !ok || !parsed.Valid || claims.RA == "" || claims.TokenVersion < 1 {
		return nil, errors.New("invalid token")
	}
	return claims, nil
}

// IssueProxyTicket creates an opaque, single-use 60-second ticket bound to ra and path.
func (s *Service) IssueProxyTicket(ra, path string) (string, error) {
	buf := make([]byte, 24)
	if _, err := io.ReadFull(rand.Reader, buf); err != nil {
		return "", err
	}
	ticket := base64.RawURLEncoding.EncodeToString(buf)
	now := time.Now().UTC()

	s.ticketMu.Lock()
	defer s.ticketMu.Unlock()

	for k, v := range s.tickets {
		if now.After(v.expiresAt) {
			delete(s.tickets, k)
		}
	}
	if len(s.tickets) >= maxTickets {
		for k := range s.tickets {
			delete(s.tickets, k)
			if len(s.tickets) < maxTickets {
				break
			}
		}
	}

	s.tickets[ticket] = proxyTicketEntry{
		ra:        ra,
		path:      path,
		expiresAt: now.Add(ticketTTL),
	}
	return ticket, nil
}

// ConsumeProxyTicket atomically redeems a single-use proxy ticket matching path.
func (s *Service) ConsumeProxyTicket(ticket, path string) (string, error) {
	if ticket == "" || path == "" {
		return "", errors.New("missing ticket or path")
	}
	now := time.Now().UTC()

	s.ticketMu.Lock()
	entry, ok := s.tickets[ticket]
	if ok {
		delete(s.tickets, ticket)
	}
	s.ticketMu.Unlock()

	if !ok || now.After(entry.expiresAt) {
		return "", errors.New("proxy ticket invalid or expired")
	}
	if subtle.ConstantTimeCompare([]byte(entry.path), []byte(path)) != 1 {
		return "", errors.New("proxy ticket path mismatch")
	}
	return entry.ra, nil
}
