package httpapi

import (
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"net/url"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

func decodeStrictJSON(w http.ResponseWriter, r *http.Request, dst any, maxBytes int64) error {
	if r.Body == nil {
		return io.EOF
	}
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxBytes))
	if err := dec.Decode(dst); err != nil {
		return err
	}
	var trailing any
	if err := dec.Decode(&trailing); err != io.EOF {
		return errors.New("unexpected trailing JSON data")
	}
	return nil
}

func isSafeRA(ra string) bool {
	if len(ra) == 0 || len(ra) > 20 {
		return false
	}
	for i := 0; i < len(ra); i++ {
		c := ra[i]
		if !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-' || c == '_') {
			return false
		}
	}
	return true
}

func isSafeParam(v string, maxLen int) bool {
	if len(v) == 0 || len(v) > maxLen || hasControlOrBackslash(v) {
		return false
	}
	lower := strings.ToLower(v)
	return !strings.Contains(lower, "..") && !strings.Contains(lower, "://") && !strings.ContainsAny(v, "#?")
}

func isSafeQueryString(q string, maxLen int) bool {
	if len(q) == 0 || len(q) > maxLen || hasControlOrBackslash(q) {
		return false
	}
	lower := strings.ToLower(q)
	if strings.Contains(lower, "..") || strings.Contains(lower, "://") || strings.Contains(q, "#") {
		return false
	}
	_, err := url.ParseQuery(q)
	return err == nil
}

func hasControlOrBackslash(s string) bool {
	for i := 0; i < len(s); i++ {
		c := s[i]
		if c < 0x20 || c == 0x7f || c == '\\' {
			return true
		}
	}
	return false
}

func isSafeExternalHTTPSURL(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || !strings.EqualFold(u.Scheme, "https") {
		return false
	}
	host := u.Hostname()
	if host == "" || strings.EqualFold(host, "localhost") {
		return false
	}
	if ip := net.ParseIP(host); ip != nil {
		return !(ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() || ip.IsUnspecified())
	}
	return true
}

func hasHiddenSegment(urlPath string) bool {
	for _, seg := range strings.Split(urlPath, "/") {
		if strings.HasPrefix(seg, ".") && seg != "." && seg != ".." {
			return true
		}
	}
	return false
}

func isWithinDir(base, target string) bool {
	rel, err := filepath.Rel(base, target)
	if err != nil {
		return false
	}
	return rel == "." || (rel != ".." && !strings.HasPrefix(rel, ".."+string(filepath.Separator)))
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeErrorWithCode(w, status, "", msg)
}

func writeErrorWithCode(w http.ResponseWriter, status int, code, msg string) {
	payload := struct {
		Error string `json:"error"`
		Code  string `json:"code,omitempty"`
	}{
		Error: msg,
		Code:  code,
	}
	writeJSON(w, status, payload)
}

func parseCIDRs(raw []string) []*net.IPNet {
	var nets []*net.IPNet
	for _, item := range raw {
		_, ipNet, err := net.ParseCIDR(item)
		if err == nil {
			nets = append(nets, ipNet)
			continue
		}
		if ip := net.ParseIP(item); ip != nil {
			maskLen := 32
			if ip.To4() == nil {
				maskLen = 128
			}
			nets = append(nets, &net.IPNet{IP: ip, Mask: net.CIDRMask(maskLen, maskLen)})
		}
	}
	return nets
}

func (s *Server) isTrustedProxy(ip net.IP) bool {
	if ip == nil {
		return false
	}
	for _, n := range s.trustedProxies {
		if n.Contains(ip) {
			return true
		}
	}
	return false
}

func (s *Server) clientIP(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	remoteIP := net.ParseIP(strings.TrimSpace(host))
	if !s.isTrustedProxy(remoteIP) {
		if remoteIP != nil {
			return remoteIP.String()
		}
		return host
	}

	if cfIP := net.ParseIP(strings.TrimSpace(r.Header.Get("CF-Connecting-IP"))); cfIP != nil {
		return cfIP.String()
	}
	if xri := net.ParseIP(strings.TrimSpace(r.Header.Get("X-Real-IP"))); xri != nil {
		return xri.String()
	}
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		for i := len(parts) - 1; i >= 0; i-- {
			if candidate := net.ParseIP(strings.TrimSpace(parts[i])); candidate != nil && !s.isTrustedProxy(candidate) {
				return candidate.String()
			}
		}
	}
	if remoteIP != nil {
		return remoteIP.String()
	}
	return host
}

type rateLimiter struct {
	mu      sync.Mutex
	limit   int
	window  time.Duration
	entries map[string][]time.Time
}

func newRateLimiter(limit int, window time.Duration) *rateLimiter {
	return &rateLimiter{
		limit:   limit,
		window:  window,
		entries: make(map[string][]time.Time),
	}
}

func (l *rateLimiter) Allow(key string) bool {
	now := time.Now()
	cutoff := now.Add(-l.window)

	l.mu.Lock()
	defer l.mu.Unlock()

	if len(l.entries) >= maxLimiterEntries {
		for k, tsList := range l.entries {
			if len(tsList) == 0 || !tsList[len(tsList)-1].After(cutoff) {
				delete(l.entries, k)
			}
		}
		if len(l.entries) >= maxLimiterEntries {
			for k := range l.entries {
				delete(l.entries, k)
				if len(l.entries) < maxLimiterEntries {
					break
				}
			}
		}
	}

	list := l.entries[key]
	kept := list[:0]
	for _, ts := range list {
		if ts.After(cutoff) {
			kept = append(kept, ts)
		}
	}
	if len(kept) >= l.limit {
		l.entries[key] = kept
		return false
	}
	kept = append(kept, now)
	l.entries[key] = kept
	return true
}
