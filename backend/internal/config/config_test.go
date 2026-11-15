package config

import "testing"

func TestLoadFromLookupSecretPolicies(t *testing.T) {
	emptyLookup := func(string) (string, bool) { return "", false }

	cfg1, err := LoadFromLookup(emptyLookup)
	if err != nil {
		t.Fatalf("dev fallback should succeed: %v", err)
	}
	cfg2, err := LoadFromLookup(emptyLookup)
	if err != nil {
		t.Fatalf("dev fallback 2 should succeed: %v", err)
	}
	if len(cfg1.AppSecret) < 32 || cfg1.AppSecret == cfg2.AppSecret {
		t.Fatal("expected random ephemeral non-hardcoded secret when APP_SECRET is unset")
	}

	strictMissing := func(k string) (string, bool) {
		if k == "REQUIRE_SECRET" {
			return "true", true
		}
		if k == "APP_SECRET" {
			return "too-short", true
		}
		return "", false
	}
	if _, err := LoadFromLookup(strictMissing); err == nil {
		t.Fatal("expected error when REQUIRE_SECRET=true and APP_SECRET < 32 chars")
	}

	strictValid := func(k string) (string, bool) {
		if k == "REQUIRE_SECRET" {
			return "true", true
		}
		if k == "APP_SECRET" {
			return "0123456789abcdef0123456789abcdef", true
		}
		return "", false
	}
	cfgStrict, err := LoadFromLookup(strictValid)
	if err != nil {
		t.Fatalf("expected valid secret to pass: %v", err)
	}
	if !cfgStrict.RequireSecret || cfgStrict.AllowDevCORS {
		t.Fatalf("unexpected flags: %+v", cfgStrict)
	}
}

func TestLoadFromLookupD2LValidation(t *testing.T) {
	withEnv := func(vals map[string]string) func(string) (string, bool) {
		return func(k string) (string, bool) {
			v, ok := vals[k]
			return v, ok
		}
	}

	if _, err := LoadFromLookup(withEnv(map[string]string{
		"D2L_BASE_URL": "http://salavirtual.unicep.edu.br",
	})); err == nil {
		t.Fatal("expected non-HTTPS external D2L_BASE_URL to be rejected")
	}

	if _, err := LoadFromLookup(withEnv(map[string]string{
		"PORTAL_BASE_URL": "ftp://cdseducacional.com.br",
	})); err == nil {
		t.Fatal("expected non-HTTPS PORTAL_BASE_URL to be rejected")
	}

	cfgLocal, err := LoadFromLookup(withEnv(map[string]string{
		"D2L_BASE_URL": "http://127.0.0.1:8080/",
	}))
	if err != nil {
		t.Fatalf("expected localhost HTTP D2L_BASE_URL to be allowed: %v", err)
	}
	if cfgLocal.D2LBaseURL != "http://127.0.0.1:8080" {
		t.Fatalf("expected trailing slash trimmed, got %q", cfgLocal.D2LBaseURL)
	}
}
