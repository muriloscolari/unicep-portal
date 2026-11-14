package config

import (
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
	DataDir             string
	WebDir              string
	PortalBaseURL       string
	PollIntervalMinutes int
	NotifyAbsences      bool
}

// Load reads configuration from the environment with safe defaults for local/Docker runs.
func Load() Config {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	if secret == "" {
		log.Println("WARN: APP_SECRET not set; using ephemeral dev secret (set APP_SECRET in production)")
		secret = "dev-only-change-me-in-production-unicep-portal"
	}

	pollMin := 15
	if v := strings.TrimSpace(os.Getenv("POLL_INTERVAL_MINUTES")); v != "" {
		if parsed, err := strconv.Atoi(v); err == nil && parsed >= 5 {
			pollMin = parsed
		}
	}

	notifyAbsences := true
	if v := strings.TrimSpace(os.Getenv("NOTIFY_ABSENCES")); v != "" {
		notifyAbsences = strings.EqualFold(v, "true") || v == "1"
	}

	return Config{
		Port:                envOr("PORT", "47832"),
		AppSecret:           secret,
		CORSOrigin:          envOr("CORS_ORIGIN", "https://unicep.muriloscolari.me"),
		DataDir:             envOr("DATA_DIR", "./data"),
		WebDir:              strings.TrimSpace(os.Getenv("WEB_DIR")),
		PortalBaseURL:       strings.TrimRight(envOr("PORTAL_BASE_URL", "https://cdseducacional.com.br"), "/"),
		PollIntervalMinutes: pollMin,
		NotifyAbsences:      notifyAbsences,
	}
}

func envOr(key, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return fallback
}
