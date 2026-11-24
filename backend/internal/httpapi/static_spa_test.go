package httpapi

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestStaticSPAPrecompressedNegotiation(t *testing.T) {
	webDir := t.TempDir()
	rawWasm := []byte("raw-wasm-bytes")
	brWasm := []byte("br-wasm-bytes")
	gzWasm := []byte("gz-wasm-bytes")

	if err := os.WriteFile(filepath.Join(webDir, "index.html"), []byte("<html>ok</html>"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(webDir, "app.wasm"), rawWasm, 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(webDir, "app.wasm.br"), brWasm, 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(webDir, "app.wasm.gz"), gzWasm, 0o644); err != nil {
		t.Fatal(err)
	}

	s := &Server{}
	h := s.staticSPAHandler(webDir)

	t.Run("prefers brotli when accepted", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/app.wasm", nil)
		req.Header.Set("Accept-Encoding", "gzip, deflate, br")
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("expected 200, got %d", rec.Code)
		}
		if got := rec.Header().Get("Content-Encoding"); got != "br" {
			t.Fatalf("expected Content-Encoding=br, got %q", got)
		}
		if got := rec.Header().Get("Content-Type"); got != "application/wasm" {
			t.Fatalf("expected Content-Type=application/wasm, got %q", got)
		}
		if !strings.Contains(rec.Header().Get("Cache-Control"), "immutable") {
			t.Fatalf("expected immutable Cache-Control, got %q", rec.Header().Get("Cache-Control"))
		}
		if !strings.Contains(rec.Header().Get("Vary"), "Accept-Encoding") {
			t.Fatalf("expected Vary Accept-Encoding, got %q", rec.Header().Get("Vary"))
		}
		if rec.Body.String() != string(brWasm) {
			t.Fatalf("unexpected body %q", rec.Body.String())
		}
	})

	t.Run("falls back to gzip when br rejected or absent", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/app.wasm", nil)
		req.Header.Set("Accept-Encoding", "gzip, br;q=0")
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("expected 200, got %d", rec.Code)
		}
		if got := rec.Header().Get("Content-Encoding"); got != "gzip" {
			t.Fatalf("expected Content-Encoding=gzip, got %q", got)
		}
		if got := rec.Header().Get("Content-Type"); got != "application/wasm" {
			t.Fatalf("expected Content-Type=application/wasm, got %q", got)
		}
		if rec.Body.String() != string(gzWasm) {
			t.Fatalf("unexpected body %q", rec.Body.String())
		}
	})

	t.Run("serves uncompressed when no encoding accepted", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/app.wasm", nil)
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("expected 200, got %d", rec.Code)
		}
		if got := rec.Header().Get("Content-Encoding"); got != "" {
			t.Fatalf("expected empty Content-Encoding, got %q", got)
		}
		if rec.Body.String() != string(rawWasm) {
			t.Fatalf("unexpected body %q", rec.Body.String())
		}
	})
}
