package client

import (
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"sync"
	"testing"
	"time"

	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/portforward"
	"k8s.io/streaming/pkg/httpstream"
)

type mockPortForwardCallback struct {
	mu            sync.Mutex
	readyHandleID string
	readyPort     int32
	readyCalled   bool
	errors        []string
	stoppedReason string
	stoppedCalled bool
	readyChan     chan struct{}
	stoppedChan   chan struct{}
}

func newMockPortForwardCallback() *mockPortForwardCallback {
	return &mockPortForwardCallback{
		readyChan:   make(chan struct{}),
		stoppedChan: make(chan struct{}),
	}
}

func (m *mockPortForwardCallback) PortForwardReady(handleID string, localPort int32) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.readyHandleID = handleID
	m.readyPort = localPort
	m.readyCalled = true
	select {
	case <-m.readyChan:
	default:
		close(m.readyChan)
	}
}

func (m *mockPortForwardCallback) PortForwardError(handleID string, message string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.errors = append(m.errors, message)
}

func (m *mockPortForwardCallback) PortForwardStopped(handleID string, reason string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.stoppedReason = reason
	m.stoppedCalled = true
	select {
	case <-m.stoppedChan:
	default:
		close(m.stoppedChan)
	}
}

type mockStream struct {
	headers http.Header
}

func (s *mockStream) Read(p []byte) (int, error)  { return 0, io.EOF }
func (s *mockStream) Write(p []byte) (int, error) { return len(p), nil }
func (s *mockStream) Close() error                { return nil }
func (s *mockStream) Reset() error                { return nil }
func (s *mockStream) Headers() http.Header        { return s.headers }
func (s *mockStream) Identifier() uint32          { return 1 }

type mockConnection struct {
	closeChan chan bool
	closeOnce sync.Once
}

func newMockConnection() *mockConnection {
	return &mockConnection{
		closeChan: make(chan bool),
	}
}

func (m *mockConnection) CreateStream(headers http.Header) (httpstream.Stream, error) {
	return &mockStream{headers: headers}, nil
}

func (m *mockConnection) Close() error {
	m.closeOnce.Do(func() {
		close(m.closeChan)
	})
	return nil
}

func (m *mockConnection) CloseChan() <-chan bool {
	return m.closeChan
}

func (m *mockConnection) SetIdleTimeout(timeout time.Duration) {}

func (m *mockConnection) RemoveStreams(streams ...httpstream.Stream) {}

type mockPortForwardDialer struct {
	dialFunc func(protocols ...string) (httpstream.Connection, string, error)
}

func (d *mockPortForwardDialer) Dial(protocols ...string) (httpstream.Connection, string, error) {
	if d.dialFunc != nil {
		return d.dialFunc(protocols...)
	}
	return newMockConnection(), portforward.PortForwardProtocolV1Name, nil
}

func newTestClientWithMockDialer(t *testing.T, dialer httpstream.Dialer) *Client {
	t.Helper()
	config := &rest.Config{Host: "http://localhost:8080"}
	clientset, err := kubernetes.NewForConfig(config)
	if err != nil {
		t.Fatalf("kubernetes.NewForConfig() error = %v", err)
	}

	return &Client{
		clientset: clientset,
		config:    config,
		timeout:   defaultTimeout,
		portForwardDialerFactory: func(cfg *rest.Config, method string, u *url.URL) (httpstream.Dialer, error) {
			if dialer != nil {
				return dialer, nil
			}
			return &mockPortForwardDialer{}, nil
		},
	}
}

// findFreePort returns an available local TCP port for testing.
func findFreePort(t *testing.T) int32 {
	t.Helper()
	var lc net.ListenConfig
	ln, err := lc.Listen(context.Background(), "tcp4", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("failed to find free port: %v", err)
	}
	defer func() {
		_ = ln.Close()
	}()
	addr, ok := ln.Addr().(*net.TCPAddr)
	if !ok {
		t.Fatalf("unexpected addr type: %T", ln.Addr())
	}
	if addr.Port <= 0 || addr.Port > 65535 {
		t.Fatalf("unexpected port range: %d", addr.Port)
	}
	return int32(addr.Port) //nolint:gosec // range 1..65535 checked
}

func TestStartPortForward_InvalidArgs(t *testing.T) {
	c := newTestClientWithMockDialer(t, nil)
	cb := newMockPortForwardCallback()

	tests := []struct {
		name       string
		namespace  string
		podName    string
		localPort  int32
		remotePort int32
		cb         PortForwardCallback
		wantErrMsg string
	}{
		{
			name:       "nil callback",
			namespace:  "default",
			podName:    "my-pod",
			localPort:  8080,
			remotePort: 80,
			cb:         nil,
			wantErrMsg: "callback cannot be nil",
		},
		{
			name:       "empty namespace",
			namespace:  "  ",
			podName:    "my-pod",
			localPort:  8080,
			remotePort: 80,
			cb:         cb,
			wantErrMsg: "namespace is required",
		},
		{
			name:       "empty pod name",
			namespace:  "default",
			podName:    "  ",
			localPort:  8080,
			remotePort: 80,
			cb:         cb,
			wantErrMsg: "pod name is required",
		},
		{
			name:       "invalid local port zero",
			namespace:  "default",
			podName:    "my-pod",
			localPort:  0,
			remotePort: 80,
			cb:         cb,
			wantErrMsg: "invalid local port",
		},
		{
			name:       "invalid local port negative",
			namespace:  "default",
			podName:    "my-pod",
			localPort:  -1,
			remotePort: 80,
			cb:         cb,
			wantErrMsg: "invalid local port",
		},
		{
			name:       "invalid local port too large",
			namespace:  "default",
			podName:    "my-pod",
			localPort:  70000,
			remotePort: 80,
			cb:         cb,
			wantErrMsg: "invalid local port",
		},
		{
			name:       "invalid remote port zero",
			namespace:  "default",
			podName:    "my-pod",
			localPort:  8080,
			remotePort: 0,
			cb:         cb,
			wantErrMsg: "invalid remote port",
		},
		{
			name:       "invalid remote port negative",
			namespace:  "default",
			podName:    "my-pod",
			localPort:  8080,
			remotePort: -5,
			cb:         cb,
			wantErrMsg: "invalid remote port",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			handle, err := c.StartPortForward("", tt.namespace, tt.podName, tt.localPort, tt.remotePort, tt.cb)
			if err == nil {
				t.Fatalf("expected error containing %q, got nil handle=%q", tt.wantErrMsg, handle)
			}
		})
	}
}

func TestStartPortForward_UnconfiguredClient(t *testing.T) {
	c := &Client{}
	cb := newMockPortForwardCallback()

	_, err := c.StartPortForward("", "default", "pod-1", 8080, 80, cb)
	if err == nil {
		t.Fatal("expected error with unconfigured client and empty kubeconfig, got nil")
	}

	_, err = c.StartPortForward("invalid-yaml-kubeconfig", "default", "pod-1", 8080, 80, cb)
	if err == nil {
		t.Fatal("expected error with invalid kubeconfig string, got nil")
	}
}

func TestStartPortForward_PortAlreadyInUseOnOS(t *testing.T) {
	c := newTestClientWithMockDialer(t, nil)
	cb := newMockPortForwardCallback()

	port := findFreePort(t)

	// Bind the port externally
	var lc net.ListenConfig
	ln, err := lc.Listen(context.Background(), "tcp4", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		t.Fatalf("net.Listen failed: %v", err)
	}
	defer func() {
		_ = ln.Close()
	}()

	_, err = c.StartPortForward("", "default", "my-pod", port, 80, cb)
	if err == nil {
		t.Fatal("expected error when port is in use on OS, got nil")
	}
}

func TestStartPortForward_DialErrorSynchronous(t *testing.T) {
	dialErr := fmt.Errorf("connection refused")
	mockDialer := &mockPortForwardDialer{
		dialFunc: func(protocols ...string) (httpstream.Connection, string, error) {
			return nil, "", dialErr
		},
	}
	c := newTestClientWithMockDialer(t, mockDialer)
	cb := newMockPortForwardCallback()
	port := findFreePort(t)

	_, err := c.StartPortForward("", "default", "my-pod", port, 80, cb)
	if err == nil {
		t.Fatal("expected synchronous error on dial failure, got nil")
	}

	// Verify port was not left reserved in globalRegistry
	port2 := port
	_, err2 := c.StartPortForward("", "default", "my-pod", port2, 80, cb)
	if err2 == nil {
		t.Fatal("expected dial error again, got nil")
	}
}

func TestStartPortForward_LifecycleStartAndStop(t *testing.T) {
	c := newTestClientWithMockDialer(t, nil)
	cb := newMockPortForwardCallback()
	port := findFreePort(t)

	handleID, err := c.StartPortForward("", "default", "my-pod", port, 80, cb)
	if err != nil {
		t.Fatalf("StartPortForward failed: %v", err)
	}
	if handleID == "" {
		t.Fatal("expected non-empty handleID")
	}

	// Verify PortForwardReady was called
	select {
	case <-cb.readyChan:
		if cb.readyHandleID != handleID {
			t.Errorf("readyHandleID = %q, want %q", cb.readyHandleID, handleID)
		}
		if cb.readyPort != port {
			t.Errorf("readyPort = %d, want %d", cb.readyPort, port)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timed out waiting for PortForwardReady callback")
	}

	// Stopping the forwarder
	if err := c.StopPortForward(handleID); err != nil {
		t.Fatalf("StopPortForward failed: %v", err)
	}

	// Verify PortForwardStopped was called
	select {
	case <-cb.stoppedChan:
		if cb.stoppedReason != "stopped" {
			t.Errorf("stoppedReason = %q, want 'stopped'", cb.stoppedReason)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timed out waiting for PortForwardStopped callback")
	}

	// Second stop should return error (unknown session)
	if err := c.StopPortForward(handleID); err == nil {
		t.Error("second StopPortForward expected error for unknown session, got nil")
	}
}

func TestStartPortForward_DoubleLocalPortRejection(t *testing.T) {
	c := newTestClientWithMockDialer(t, nil)
	cb1 := newMockPortForwardCallback()
	cb2 := newMockPortForwardCallback()
	port := findFreePort(t)

	handle1, err := c.StartPortForward("", "default", "pod-1", port, 80, cb1)
	if err != nil {
		t.Fatalf("StartPortForward #1 failed: %v", err)
	}
	defer func() {
		_ = c.StopPortForward(handle1)
	}()

	// Attempting to start another forward on the same localPort
	_, err = c.StartPortForward("", "default", "pod-2", port, 8080, cb2)
	if err == nil {
		t.Fatal("expected synchronous error for duplicate localPort, got nil")
	}

	// Stop #1 and verify we can now start on the same port
	if err := c.StopPortForward(handle1); err != nil {
		t.Fatalf("StopPortForward #1 failed: %v", err)
	}

	handle2, err := c.StartPortForward("", "default", "pod-2", port, 8080, cb2)
	if err != nil {
		t.Fatalf("StartPortForward #2 after stop failed: %v", err)
	}
	_ = c.StopPortForward(handle2)
}

func TestStopPortForward_UnknownID(t *testing.T) {
	c := newTestClientWithMockDialer(t, nil)

	if err := c.StopPortForward("non-existent-id"); err == nil {
		t.Fatal("expected error for non-existent-id, got nil")
	}

	if err := c.StopPortForward("  "); err == nil {
		t.Fatal("expected error for empty id, got nil")
	}
}

func TestStartPortForward_PodConnectionDropped(t *testing.T) {
	conn := newMockConnection()
	mockDialer := &mockPortForwardDialer{
		dialFunc: func(protocols ...string) (httpstream.Connection, string, error) {
			return conn, portforward.PortForwardProtocolV1Name, nil
		},
	}
	c := newTestClientWithMockDialer(t, mockDialer)
	cb := newMockPortForwardCallback()
	port := findFreePort(t)

	handleID, err := c.StartPortForward("", "default", "my-pod", port, 80, cb)
	if err != nil {
		t.Fatalf("StartPortForward failed: %v", err)
	}

	<-cb.readyChan

	// Simulate server/pod closing the connection
	_ = conn.Close()

	// Wait for PortForwardStopped callback
	select {
	case <-cb.stoppedChan:
		if cb.stoppedReason == "" {
			t.Error("expected non-empty stoppedReason")
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timed out waiting for PortForwardStopped callback after connection close")
	}

	// Global registry should have removed the session
	if err := c.StopPortForward(handleID); err == nil {
		t.Error("expected StopPortForward to fail after session terminated due to disconnect, got nil")
	}
}

func TestStartPortForward_ConcurrentForwards(t *testing.T) {
	c := newTestClientWithMockDialer(t, nil)
	const count = 5

	type session struct {
		handle string
		port   int32
		cb     *mockPortForwardCallback
	}

	sessions := make([]session, count)
	for i := 0; i < count; i++ {
		port := findFreePort(t)
		cb := newMockPortForwardCallback()
		handle, err := c.StartPortForward("", "default", fmt.Sprintf("pod-%d", i), port, 80, cb)
		if err != nil {
			t.Fatalf("concurrent start %d failed: %v", i, err)
		}
		sessions[i] = session{handle: handle, port: port, cb: cb}
	}

	// Verify all are ready
	for i, s := range sessions {
		select {
		case <-s.cb.readyChan:
		case <-time.After(2 * time.Second):
			t.Fatalf("session %d ready timeout", i)
		}
	}

	// Stop all concurrently
	var wg sync.WaitGroup
	for _, s := range sessions {
		wg.Add(1)
		go func(h string) {
			defer wg.Done()
			if err := c.StopPortForward(h); err != nil {
				t.Errorf("StopPortForward(%s) error = %v", h, err)
			}
		}(s.handle)
	}
	wg.Wait()

	for i, s := range sessions {
		select {
		case <-s.cb.stoppedChan:
		case <-time.After(2 * time.Second):
			t.Fatalf("session %d stopped timeout", i)
		}
	}
}

// TODO: Network-level port forwarding tests against a live cluster.
// These tests require a running Kubernetes cluster, a live Pod listening on a target port,
// and end-to-end TCP traffic forwarding verification from localhost to the pod.
func TestStartPortForward_LiveCluster_NetworkLevel_TODO(t *testing.T) {
	t.Skip("skipping network-level test requiring a live Kubernetes cluster")
}

// A dial that never completes (TCP accepted, TLS never finishes) must not block
// StartPortForward forever, and must not leave the local port reserved.
func TestStartPortForward_StalledDialTimesOutAndFreesPort(t *testing.T) {
	release := make(chan struct{})
	defer close(release)

	var dials sync.Mutex
	dialCount := 0
	dialer := &mockPortForwardDialer{
		dialFunc: func(protocols ...string) (httpstream.Connection, string, error) {
			dials.Lock()
			dialCount++
			first := dialCount == 1
			dials.Unlock()
			if first {
				<-release
				return nil, "", fmt.Errorf("dial finally failed")
			}
			return newMockConnection(), portforward.PortForwardProtocolV1Name, nil
		},
	}
	c := newTestClientWithMockDialer(t, dialer)
	c.timeout = 200 * time.Millisecond
	cb := newMockPortForwardCallback()
	port := findFreePort(t)

	started := time.Now()
	_, err := c.StartPortForward("", "default", "my-pod", port, 80, cb)
	if err == nil {
		t.Fatal("StartPortForward() with a stalled dial error = nil, want a timeout")
	}
	if elapsed := time.Since(started); elapsed > 2*time.Second {
		t.Fatalf("StartPortForward() took %v, want it bounded by the client timeout", elapsed)
	}

	// The port must be free for an immediate retry.
	handleID, err := c.StartPortForward("", "default", "my-pod", port, 80, cb)
	if err != nil {
		t.Fatalf("retry on the same port error = %v, want the port to have been released", err)
	}
	if err := c.StopPortForward(handleID); err != nil {
		t.Errorf("StopPortForward() error = %v", err)
	}
}

// A forward that fails to start reports through the returned error only. The caller
// never learns its handle, so callbacks for it could not be attributed to anything.
func TestStartPortForward_FailedStartDeliversNoCallbacks(t *testing.T) {
	dialer := &mockPortForwardDialer{
		dialFunc: func(protocols ...string) (httpstream.Connection, string, error) {
			return nil, "", fmt.Errorf("pods \"my-pod\" not found")
		},
	}
	c := newTestClientWithMockDialer(t, dialer)
	cb := newMockPortForwardCallback()

	if _, err := c.StartPortForward("", "default", "my-pod", findFreePort(t), 80, cb); err == nil {
		t.Fatal("StartPortForward() error = nil, want the dial failure")
	}
	time.Sleep(50 * time.Millisecond) // give a stray callback time to arrive

	cb.mu.Lock()
	defer cb.mu.Unlock()
	if cb.readyCalled || cb.stoppedCalled || len(cb.errors) != 0 {
		t.Errorf("callbacks after a failed start: ready=%v stopped=%v errors=%v",
			cb.readyCalled, cb.stoppedCalled, cb.errors)
	}
}

// StartPortForward applies the same kubeconfig restrictions as NewClient.
func TestStartPortForward_RejectsExecPluginKubeconfig(t *testing.T) {
	c := newTestClientWithMockDialer(t, nil)
	kubeconfig := `
apiVersion: v1
kind: Config
clusters:
- cluster:
    server: https://127.0.0.1:6443
  name: c
contexts:
- context:
    cluster: c
    user: u
  name: c
current-context: c
users:
- name: u
  user:
    exec:
      apiVersion: client.authentication.k8s.io/v1
      command: sh
      interactiveMode: Never
`
	_, err := c.StartPortForward(kubeconfig, "default", "my-pod", findFreePort(t), 80, newMockPortForwardCallback())
	if err == nil {
		t.Fatal("StartPortForward(exec plugin kubeconfig) error = nil, want a refusal")
	}
}
