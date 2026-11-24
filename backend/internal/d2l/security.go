package d2l

import (
	"net/url"
	"path"
	"strings"
)

// IsAllowedFilePath restricts D2L file proxying strictly to read-only topic/attachment/content paths.
func IsAllowedFilePath(p string) bool {
	if p == "" || len(p) > 512 || strings.ContainsAny(p, "\r\n\x00\\") {
		return false
	}
	lower := strings.ToLower(p)
	if strings.Contains(lower, "://") || strings.Contains(lower, "..") || strings.Contains(lower, "%2e") || strings.Contains(lower, "%5c") {
		return false
	}
	parsed, err := url.Parse(p)
	if err != nil || parsed.Scheme != "" || parsed.Host != "" || parsed.User != nil || parsed.Fragment != "" {
		return false
	}
	cleaned := path.Clean(parsed.Path)
	if cleaned != parsed.Path || strings.Contains(cleaned, "..") {
		return false
	}
	lowerClean := strings.ToLower(cleaned)
	if strings.HasPrefix(lowerClean, "/d2l/api/le/") {
		return strings.Contains(lowerClean, "/attachments/") ||
			strings.HasSuffix(lowerClean, "/file") ||
			isAllowedSubmissionFilePath(lowerClean)
	}
	allowed := []string{
		"/content/enforced/",
		"/d2l/common/viewfile.d2lfile/",
	}
	for _, prefix := range allowed {
		if strings.HasPrefix(lowerClean, prefix) {
			return true
		}
	}
	return false
}

func isAllowedD2LAPIVersion(seg string) bool {
	if len(seg) < 3 || len(seg) > 12 {
		return false
	}
	dots := 0
	for i := 0; i < len(seg); i++ {
		c := seg[i]
		if c == '.' {
			if i == 0 || i == len(seg)-1 {
				return false
			}
			dots++
			if dots > 1 {
				return false
			}
		} else if c < '0' || c > '9' {
			return false
		}
	}
	return dots == 1
}

func isPositiveIntSegment(seg string) bool {
	if len(seg) == 0 || len(seg) > 20 || seg[0] == '0' {
		return false
	}
	for i := 0; i < len(seg); i++ {
		if seg[i] < '0' || seg[i] > '9' {
			return false
		}
	}
	return true
}

func isAllowedSubmissionFilePath(lowerClean string) bool {
	trimmed := strings.TrimPrefix(lowerClean, "/")
	segs := strings.Split(trimmed, "/")
	if len(segs) != 12 {
		return false
	}
	return segs[0] == "d2l" &&
		segs[1] == "api" &&
		segs[2] == "le" &&
		isAllowedD2LAPIVersion(segs[3]) &&
		isPositiveIntSegment(segs[4]) &&
		segs[5] == "dropbox" &&
		segs[6] == "folders" &&
		isPositiveIntSegment(segs[7]) &&
		segs[8] == "submissions" &&
		isPositiveIntSegment(segs[9]) &&
		segs[10] == "files" &&
		isPositiveIntSegment(segs[11])
}
