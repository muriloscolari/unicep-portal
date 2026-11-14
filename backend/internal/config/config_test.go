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
