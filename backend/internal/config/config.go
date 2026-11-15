package config

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"log"
	"os"
	"strconv"
	"strings"
)

// Config holds runtime configuration loaded from environment variables.
type Config struct {
	Port                string
	AppSecret           string
	CORSOrigin          string
	AllowDevCORS        bool
	DataDir             string
	WebDir              string
	PortalBaseURL       string
	D2LBaseURL          string
	PollIntervalMinutes int
	NotifyAbsences      bool
	RequireSecret       bool
	TrustedProxyCIDRs   []string
}

// Load reads configuration from the environment and terminates if production security requirements are unmet.
func Load() Config {
	cfg, err := LoadFromLookup(os.LookupEnv)
	if err != nil {
		log.Fatalf("config: %v", err)
	}
	return cfg
}

// LoadFromLookup parses configuration from a custom environment lookup function (testable without os.Setenv).
func LoadFromLookup(lookup func(string) (string, bool)) (Config, error) {
	get := func(key string) string {
		v, _ := lookup(key)
		return strings.TrimSpace(v)
	}
	getOr := func(key, fallback string) string {
		if v := get(key); v != "" {
			return v
		}
		return fallback
	}

	requireSecret := parseBool(get("REQUIRE_SECRET"), false)
	secret := get("APP_SECRET")
	if len(secret) < 32 {
		if requireSecret {
			return Config{}, errors.New("APP_SECRET must be at least 32 characters when REQUIRE_SECRET=true")
		}
		buf := make([]byte, 32)
		if _, err := rand.Read(buf); err != nil {
			return Config{}, err
		}
		secret = hex.EncodeToString(buf)
		log.Println("WARN: APP_SECRET missing or <32 chars; generated ephemeral random secret for this process")
	}

	pollMin := 15
	if v := get("POLL_INTERVAL_MINUTES"); v != "" {
		if parsed, err := strconv.Atoi(v); err == nil && parsed >= 5 {
			pollMin = parsed
		}
	}

	cidrsRaw := getOr("TRUSTED_PROXY_CIDRS", "127.0.0.1/8,::1/128,172.16.0.0/12")
	var cidrs []string
	for _, part := range strings.Split(cidrsRaw, ",") {
		if trimmed := strings.TrimSpace(part); trimmed != "" {
			cidrs = append(cidrs, trimmed)
		}
	}

	return Config{
		Port:                getOr("PORT", "47832"),
		AppSecret:           secret,
		CORSOrigin:          getOr("CORS_ORIGIN", "https://unicep.muriloscolari.me"),
		AllowDevCORS:        parseBool(get("ALLOW_DEV_CORS"), false),
		DataDir:             getOr("DATA_DIR", "./data"),
		WebDir:              get("WEB_DIR"),
		PortalBaseURL:       strings.TrimRight(getOr("PORTAL_BASE_URL", "https://cdseducacional.com.br"), "/"),
		D2LBaseURL:          strings.TrimRight(getOr("D2L_BASE_URL", "https://salavirtual.unicep.edu.br"), "/"),
		PollIntervalMinutes: pollMin,
		NotifyAbsences:      parseBool(get("NOTIFY_ABSENCES"), true),
		RequireSecret:       requireSecret,
		TrustedProxyCIDRs:   cidrs,
	}, nil
}

func parseBool(raw string, fallback bool) bool {
	if raw == "" {
		return fallback
	}
	return strings.EqualFold(raw, "true") || raw == "1"
}
