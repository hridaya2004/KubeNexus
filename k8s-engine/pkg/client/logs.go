package client

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"strings"

	corev1 "k8s.io/api/core/v1"
)

// maxLogBytes bounds a one-shot log fetch. The whole result crosses into the JVM as a
// single UTF-16 string, so an unbounded read of a large container log could exhaust the
// app's heap.
const maxLogBytes = 8 << 20

// logTruncatedNotice is prepended when a one-shot fetch keeps only the newest maxLogBytes.
const logTruncatedNotice = "[KubeNexus: earlier log output omitted; showing the most recent 8 MiB]"

// LogCallback receives streamed container log lines and status events.
//
// Log streaming cannot be expressed through the generic JSON resource methods:
// it is an open HTTP stream rather than a single object, so it stays as a
// purpose-built binding with a callback interface.
type LogCallback interface {
	OnLogLine(line string)
	OnError(err string)
	OnDone()
}

// LogStream is a running follow of a container's logs, started by StartLogStream.
type LogStream struct {
	cancel context.CancelFunc
}

// Cancel stops following the logs and closes the underlying HTTP stream. The callback
// still receives OnDone once the reader has exited, but no OnError for the cancellation
// itself. Calling Cancel more than once is safe.
func (s *LogStream) Cancel() {
	if s != nil && s.cancel != nil {
		s.cancel()
	}
}

// Logs returns the full log output for a container.
func (c *Client) Logs(namespace, podName, container string) (string, error) {
	return c.LogsWithTail(namespace, podName, container, 0)
}

// LogsWithTail returns the log output for a container with a tail lines option.
// If tailLines is greater than zero, output is limited to that many lines from
// the end of the logs. At most the newest 8 MiB is returned; when older output is
// dropped the result starts with a notice line saying so.
func (c *Client) LogsWithTail(namespace, podName, container string, tailLines int64) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), c.timeout)
	defer cancel()

	opts := &corev1.PodLogOptions{}
	if container != "" {
		opts.Container = container
	}
	if tailLines > 0 {
		opts.TailLines = &tailLines
	}

	req := c.clientset.CoreV1().Pods(namespace).GetLogs(podName, opts)
	stream, err := req.Stream(ctx)
	if err != nil {
		return "", fmt.Errorf("opening log stream: %w", err)
	}
	defer func() { _ = stream.Close() }()

	data, truncated, err := readTail(stream, maxLogBytes)
	if err != nil {
		return "", fmt.Errorf("reading logs: %w", err)
	}
	if truncated {
		return logTruncatedNotice + "\n" + string(data), nil
	}
	return string(data), nil
}

// readTail reads r to EOF and returns at most the last limit bytes. When bytes are
// dropped, the result starts at the first complete line so no partial line is shown.
func readTail(r io.Reader, limit int) (data []byte, truncated bool, err error) {
	buf := make([]byte, 0, 64*1024)
	chunk := make([]byte, 64*1024)
	for {
		n, readErr := r.Read(chunk)
		buf = append(buf, chunk[:n]...)
		// Compact only once the buffer is well past the limit, so the copy is amortised.
		if len(buf) > 2*limit {
			buf = append(buf[:0], buf[len(buf)-limit:]...)
			truncated = true
		}
		if errors.Is(readErr, io.EOF) {
			break
		}
		if readErr != nil {
			return nil, false, readErr
		}
	}
	if len(buf) > limit {
		buf = buf[len(buf)-limit:]
		truncated = true
	}
	if truncated {
		if i := bytes.IndexByte(buf, '\n'); i >= 0 {
			buf = buf[i+1:]
		}
	}
	return buf, truncated, nil
}

// StartLogStream follows a container's logs in the background and returns immediately.
// Each non-blank line is delivered to callback.OnLogLine; OnError reports a failure to
// open or read the stream, and OnDone is always delivered last, exactly once. If
// tailLines is greater than zero, streaming starts that many lines from the end.
//
// The stream has no overall timeout: it runs until the container stops, the connection
// fails, or Cancel is called on the returned LogStream.
func (c *Client) StartLogStream(namespace, podName, container string, tailLines int64, callback LogCallback) (*LogStream, error) {
	if callback == nil {
		return nil, fmt.Errorf("callback cannot be nil")
	}

	opts := &corev1.PodLogOptions{
		Follow: true,
	}
	if container != "" {
		opts.Container = container
	}
	if tailLines > 0 {
		opts.TailLines = &tailLines
	}

	ctx, cancel := context.WithCancel(context.Background())
	req := c.streamingClientset.CoreV1().Pods(namespace).GetLogs(podName, opts)

	go func() {
		defer callback.OnDone()
		defer cancel()

		stream, err := req.Stream(ctx)
		if err != nil {
			if ctx.Err() == nil {
				callback.OnError(fmt.Sprintf("opening log stream: %v", err))
			}
			return
		}
		defer func() { _ = stream.Close() }()

		scanner := bufio.NewScanner(stream)
		scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
		for scanner.Scan() {
			line := scanner.Text()
			if strings.TrimSpace(line) != "" {
				callback.OnLogLine(line)
			}
		}

		if err := scanner.Err(); err != nil && ctx.Err() == nil {
			callback.OnError(fmt.Sprintf("reading logs: %v", err))
		}
	}()

	return &LogStream{cancel: cancel}, nil
}
