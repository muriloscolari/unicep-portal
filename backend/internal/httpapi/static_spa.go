package httpapi

import (
	"errors"
	"io/fs"
	"mime"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

func (s *Server) staticSPAHandler(dir string) http.Handler {
	absDir, err := filepath.Abs(dir)
	if err != nil {
		absDir = dir
	}
	indexPath := filepath.Join(absDir, "index.html")
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/api/") {
			writeError(w, http.StatusNotFound, "endpoint não encontrado")
			return
		}
		if hasHiddenSegment(r.URL.Path) {
			writeError(w, http.StatusNotFound, "não encontrado")
			return
		}

		clean := filepath.Clean(strings.TrimPrefix(r.URL.Path, "/"))
		if clean == "." || clean == "" {
			clean = "index.html"
		}
		full, err := filepath.Abs(filepath.Join(absDir, clean))
		if err != nil || !isWithinDir(absDir, full) {
			writeError(w, http.StatusForbidden, "caminho inválido")
			return
		}

		if info, err := os.Stat(full); err == nil && !info.IsDir() {
			serveStaticAsset(w, r, full, clean)
			return
		}
		if filepath.Ext(clean) != "" {
			writeError(w, http.StatusNotFound, "arquivo não encontrado")
			return
		}
		if _, err := os.Stat(indexPath); err == nil && !errors.Is(err, fs.ErrNotExist) {
			serveStaticAsset(w, r, indexPath, "index.html")
			return
		}
		writeError(w, http.StatusNotFound, "frontend não encontrado")
	})
}

func serveStaticAsset(w http.ResponseWriter, r *http.Request, fullPath, relPath string) {
	ext := strings.ToLower(filepath.Ext(relPath))
	switch ext {
	case ".wasm":
		w.Header().Set("Content-Type", "application/wasm")
		w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
	case ".html":
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-cache, must-revalidate")
	case ".js":
		w.Header().Set("Content-Type", "application/javascript; charset=utf-8")
		w.Header().Set("Cache-Control", "no-cache, must-revalidate")
	case ".svg":
		w.Header().Set("Content-Type", "image/svg+xml")
	default:
		if ext != "" {
			if ct := mime.TypeByExtension(ext); ct != "" {
				w.Header().Set("Content-Type", ct)
			}
		}
	}

	targetPath := fullPath
	if isPrecompressibleExt(ext) {
		ae := r.Header.Get("Accept-Encoding")
		w.Header().Add("Vary", "Accept-Encoding")
		if acceptsEncoding(ae, "br") && isRegularFile(fullPath+".br") {
			w.Header().Set("Content-Encoding", "br")
			targetPath = fullPath + ".br"
		} else if acceptsEncoding(ae, "gzip") && isRegularFile(fullPath+".gz") {
			w.Header().Set("Content-Encoding", "gzip")
			targetPath = fullPath + ".gz"
		}
	}
	http.ServeFile(w, r, targetPath)
}

func isPrecompressibleExt(ext string) bool {
	switch ext {
	case ".wasm", ".js", ".html", ".svg", ".css", ".json":
		return true
	default:
		return false
	}
}

func isRegularFile(p string) bool {
	info, err := os.Stat(p)
	return err == nil && !info.IsDir()
}

func acceptsEncoding(header, target string) bool {
	if header == "" {
		return false
	}
	for _, part := range strings.Split(header, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		name := part
		qZero := false
		if idx := strings.IndexByte(part, ';'); idx >= 0 {
			name = strings.TrimSpace(part[:idx])
			params := strings.ToLower(strings.TrimSpace(part[idx+1:]))
			if strings.Contains(params, "q=0") && !strings.Contains(params, "q=0.") {
				qZero = true
			} else if strings.HasSuffix(params, "q=0.0") || strings.HasSuffix(params, "q=0.00") || strings.HasSuffix(params, "q=0.000") {
				qZero = true
			}
		}
		if strings.EqualFold(name, target) {
			return !qZero
		}
	}
	return false
}
