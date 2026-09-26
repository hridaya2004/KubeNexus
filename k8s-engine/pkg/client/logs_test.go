package client

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"k8s.io/client-go/rest"
)

// recordingLogCallback is safe for the concurrent use StartLogStream makes of it.
type recordingLogCallback struct {
	mu     sync.Mutex
	lines  []string
	errors []string
	done   chan struct{}
	once   sync.Once
}

func newRecordingLogCallback() *recordingLogCallback {
	return &recordingLogCallback{done: make(chan struct{})}
}

func (m *recordingLogCallback) OnLogLine(line string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.lines = append(m.lines, line)
}

func (m *recordingLogCallback) OnError(err string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.errors = append(m.errors, err)
}

func (m *recordingLogCallback) OnDone() {
	m.once.Do(func() { close(m.done) })
}

func (m *recordingLogCallback) snapshot() (lines, errs []string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return append([]string(nil), m.lines...), append([]string(nil), m.errors...)
}

func (m *recordingLogCallback) waitDone(t *testing.T, within time.Duration) {
	t.Helper()
	select {
	case <-m.done:
	case <-time.After(within):
		t.Fatalf("OnDone not delivered within %v", within)
	}
}

// streamingLogServer serves the pod log endpoint, writing one line per interval until
// it has written count lines (or forever when count is negative).
func streamingLogServer(t *testing.T, interval time.Duration, count int) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasSuffix(r.URL.Path, "/pods/pod-1/log") {
			http.NotFound(w, r)
			return
		}
		flusher, _ := w.(http.Flusher)
		w.WriteHeader(http.StatusOK)
		for i := 0; count < 0 || i < count; i++ {
			if _, err := fmt.Fprintf(w, "line %d\n", i); err != nil {
				return
			}
			if flusher != nil {
				flusher.Flush()
			}
			select {
			case <-r.Context().Done():
				return
			case <-time.After(interval):
			}
		}
	}))
}

func newTestClientWithTimeout(t *testing.T, host string, timeout time.Duration) *Client {
	t.Helper()
	config := &rest.Config{Host: host, Timeout: timeout}
	c, err := newClientFromConfig(config, timeout)
	if err != nil {
		t.Fatalf("newClientFromConfig() error = %v", err)
	}
	return c
}

// A nil callback must be refused rather than panic, since the Kotlin side can
// pass null across the JNI boundary.
func TestStartLogStream_NilCallbackIsRejected(t *testing.T) {
	c := &Client{timeout: defaultTimeout}
	if _, err := c.StartLogStream("default", "pod-1", "container-1", 10, nil); err == nil {
		t.Fatal("StartLogStream(nil callback) error = nil, want an error")
	}
}

// Following logs is open-ended. The client's request timeout must not cut the
// stream off, which it did when the stream shared the timed clientset.
func TestStartLogStream_OutlivesClientTimeout(t *testing.T) {
	server := streamingLogServer(t, 100*time.Millisecond, 8)
	defer server.Close()
	c := newTestClientWithTimeout(t, server.URL, 250*time.Millisecond)

	cb := newRecordingLogCallback()
	if _, err := c.StartLogStream("default", "pod-1", "", 0, cb); err != nil {
		t.Fatalf("StartLogStream() error = %v", err)
	}
	cb.waitDone(t, 5*time.Second)

	lines, errs := cb.snapshot()
	if len(errs) != 0 {
		t.Fatalf("OnError = %v; the stream was cut off", errs)
	}
	if len(lines) != 8 {
		t.Fatalf("got %d lines, want all 8 (the stream ran ~800ms against a 250ms timeout)", len(lines))
	}
}

// Cancel must end the stream promptly and must not be reported as an error.
func TestStartLogStream_CancelStopsStream(t *testing.T) {
	server := streamingLogServer(t, 20*time.Millisecond, -1)
	defer server.Close()
	c := newTestClientWithTimeout(t, server.URL, defaultTimeout)

	cb := newRecordingLogCallback()
	stream, err := c.StartLogStream("default", "pod-1", "", 0, cb)
	if err != nil {
		t.Fatalf("StartLogStream() error = %v", err)
	}
	time.Sleep(100 * time.Millisecond)
	stream.Cancel()
	stream.Cancel() // idempotent
	cb.waitDone(t, 2*time.Second)

	lines, errs := cb.snapshot()
	if len(lines) == 0 {
		t.Error("no lines arrived before Cancel")
	}
	if len(errs) != 0 {
		t.Errorf("OnError = %v; cancellation must not surface as an error", errs)
	}
}

func TestStartLogStream_ReportsOpenFailure(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, `{"kind":"Status","status":"Failure","message":"pods \"pod-1\" not found","code":404}`, http.StatusNotFound)
	}))
	defer server.Close()
	c := newTestClientWithTimeout(t, server.URL, defaultTimeout)

	cb := newRecordingLogCallback()
	if _, err := c.StartLogStream("default", "pod-1", "", 0, cb); err != nil {
		t.Fatalf("StartLogStream() error = %v", err)
	}
	cb.waitDone(t, 5*time.Second)
	if _, errs := cb.snapshot(); len(errs) != 1 {
		t.Fatalf("OnError calls = %v, want exactly one", errs)
	}
}

func TestReadTail(t *testing.T) {
	t.Run("under the limit is returned whole", func(t *testing.T) {
		data, truncated, err := readTail(strings.NewReader("a\nb\n"), 16)
		if err != nil || truncated || string(data) != "a\nb\n" {
			t.Fatalf("readTail() = %q, %v, %v", data, truncated, err)
		}
	})

	t.Run("over the limit keeps the newest complete lines", func(t *testing.T) {
		var sb strings.Builder
		for i := 0; i < 1000; i++ {
			fmt.Fprintf(&sb, "line-%04d\n", i)
		}
		data, truncated, err := readTail(strings.NewReader(sb.String()), 105)
		if err != nil || !truncated {
			t.Fatalf("readTail() truncated = %v, err = %v", truncated, err)
		}
		got := string(data)
		if !strings.HasSuffix(got, "line-0999\n") {
			t.Errorf("tail = %q, want it to end with the newest line", got)
		}
		if !strings.HasPrefix(got, "line-") {
			t.Errorf("tail = %q, want it to start on a line boundary", got)
		}
		if len(got) > 105 {
			t.Errorf("len = %d, want <= 105", len(got))
		}
	})
}
