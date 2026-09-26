package client

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/portforward"
	"k8s.io/client-go/transport/spdy"
	"k8s.io/streaming/pkg/httpstream"
)

// PortForwardCallback receives lifecycle events and error updates for an active
// port-forward session.
//
// This interface is exported for gomobile bindings and is implemented on the
// Android Kotlin side across JNI.
type PortForwardCallback interface {
	PortForwardReady(handleID string, localPort int32)
	PortForwardError(handleID string, message string)
	PortForwardStopped(handleID string, reason string)
}

type portForwardDialerFactoryFunc func(config *rest.Config, method string, u *url.URL) (httpstream.Dialer, error)

func defaultPortForwardDialerFactory(config *rest.Config, method string, u *url.URL) (httpstream.Dialer, error) {
	wsDialer, wsErr := portforward.NewSPDYOverWebsocketDialerForStreaming(u, config)

	transport, upgrader, spdyErr := spdy.RoundTripperFor(config)
	if spdyErr != nil {
		if wsErr == nil && wsDialer != nil {
			return wsDialer, nil
		}
		return nil, fmt.Errorf("creating SPDY round tripper: %w", spdyErr)
	}

	spdyDialer := spdy.NewDialerForStreaming(upgrader, &http.Client{Transport: transport}, method, u)
	if wsErr == nil && wsDialer != nil {
		return portforward.NewFallbackDialerForStreaming(wsDialer, spdyDialer, httpstream.IsUpgradeFailure), nil
	}
	return spdyDialer, nil
}

// forwarderState tracks who owns the outcome of a starting forward. StartPortForward
// and the owner goroutine race to report it; the state makes exactly one of them win,
// so a forward either starts (and then reports its lifecycle through callbacks) or
// fails to start (and reports only through the returned error).
type forwarderState int

const (
	stateStarting forwarderState = iota
	stateRunning
	stateFailedToStart
	stateAbandoned // StartPortForward gave up waiting; the goroutine cleans up silently
)

// activeForwarder manages the runtime state, channels, and lifecycle callbacks
// of a single port-forwarding session.
type activeForwarder struct {
	stateMu     sync.Mutex
	state       forwarderState
	handleID    string
	localPort   int32
	remotePort  int32
	namespace   string
	podName     string
	stopChan    chan struct{}
	stopOnce    sync.Once
	stopped     atomic.Bool
	stoppedOnce sync.Once
	readyOnce   sync.Once
	forwarder   *portforward.PortForwarder
	cb          PortForwardCallback
	doneChan    chan struct{} // closed when the single owner goroutine finishes execution
}

func newActiveForwarder(
	handleID string,
	localPort int32,
	remotePort int32,
	namespace string,
	podName string,
	cb PortForwardCallback,
	stopChan chan struct{},
) *activeForwarder {
	return &activeForwarder{
		handleID:   handleID,
		localPort:  localPort,
		remotePort: remotePort,
		namespace:  namespace,
		podName:    podName,
		stopChan:   stopChan,
		cb:         cb,
		doneChan:   make(chan struct{}),
	}
}

func (f *activeForwarder) stop() {
	f.stopped.Store(true)
	f.stopOnce.Do(func() {
		close(f.stopChan)
	})
}

func (f *activeForwarder) isStopped() bool {
	return f.stopped.Load()
}

// transition moves from stateStarting to next and reports whether it did. Once the
// state has left stateStarting it never changes again.
func (f *activeForwarder) transition(next forwarderState) (forwarderState, bool) {
	f.stateMu.Lock()
	defer f.stateMu.Unlock()
	if f.state != stateStarting {
		return f.state, false
	}
	f.state = next
	return next, true
}

func (f *activeForwarder) currentState() forwarderState {
	f.stateMu.Lock()
	defer f.stateMu.Unlock()
	return f.state
}

func (f *activeForwarder) fireReady(port int32) {
	f.readyOnce.Do(func() {
		if f.cb != nil {
			f.cb.PortForwardReady(f.handleID, port)
		}
	})
}

// fireError and fireStopped only report on a forward that started; before that, the
// outcome is StartPortForward's return value and the caller does not know the handle.
func (f *activeForwarder) fireError(msg string) {
	if f.cb != nil && f.currentState() == stateRunning {
		f.cb.PortForwardError(f.handleID, msg)
	}
}

func (f *activeForwarder) fireStopped(reason string) {
	if f.currentState() != stateRunning {
		return
	}
	f.stoppedOnce.Do(func() {
		if f.cb != nil {
			f.cb.PortForwardStopped(f.handleID, reason)
		}
	})
}

// portForwardRegistry tracks active port-forward sessions across the process,
// enforcing local port exclusivity and thread-safe handle lookups.
type portForwardRegistry struct {
	mu         sync.Mutex
	forwarders map[string]*activeForwarder
	ports      map[int32]string // localPort -> handleID
}

var globalRegistry = newPortForwardRegistry()

func newPortForwardRegistry() *portForwardRegistry {
	return &portForwardRegistry{
		forwarders: make(map[string]*activeForwarder),
		ports:      make(map[int32]string),
	}
}

func (r *portForwardRegistry) reserve(f *activeForwarder) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	if existingID, exists := r.ports[f.localPort]; exists {
		return fmt.Errorf("local port %d is already in use by active port forward %q", f.localPort, existingID)
	}

	r.forwarders[f.handleID] = f
	r.ports[f.localPort] = f.handleID
	return nil
}

func (r *portForwardRegistry) remove(handleID string) (*activeForwarder, bool) {
	r.mu.Lock()
	defer r.mu.Unlock()

	f, exists := r.forwarders[handleID]
	if !exists {
		return nil, false
	}
	delete(r.forwarders, handleID)
	delete(r.ports, f.localPort)
	return f, true
}

func generateHandleID() (string, error) {
	var b [8]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", fmt.Errorf("generating handle id: %w", err)
	}
	return fmt.Sprintf("pf-%s", hex.EncodeToString(b[:])), nil
}

// StartPortForward initiates port-forwarding from 127.0.0.1:<localPort> to the
// specified pod's <remotePort>.
//
// Returns an opaque handle ID string once the tunnel is listening. PortForwardReady is
// delivered before it returns; PortForwardError and PortForwardStopped follow for the
// rest of the tunnel's life.
//
// Returns an error, and delivers no callbacks, if parameters are invalid, localPort is
// already bound or in use, client construction fails, the initial dial fails, or the
// tunnel is not up within the client timeout. On error the local port is released.
func (c *Client) StartPortForward(
	kubeconfig string,
	namespace string,
	podName string,
	localPort int32,
	remotePort int32,
	cb PortForwardCallback,
) (string, error) {
	if cb == nil {
		return "", fmt.Errorf("callback cannot be nil")
	}

	ns := strings.TrimSpace(namespace)
	if ns == "" {
		return "", fmt.Errorf("namespace is required")
	}

	pod := strings.TrimSpace(podName)
	if pod == "" {
		return "", fmt.Errorf("pod name is required")
	}

	if localPort < 1 || localPort > 65535 {
		return "", fmt.Errorf("invalid local port %d: must be between 1 and 65535", localPort)
	}

	if remotePort < 1 || remotePort > 65535 {
		return "", fmt.Errorf("invalid remote port %d: must be between 1 and 65535", remotePort)
	}

	// Pre-check whether local port can be bound by a local TCP listener on 127.0.0.1.
	var lc net.ListenConfig
	testLn, err := lc.Listen(context.Background(), "tcp4", fmt.Sprintf("127.0.0.1:%d", localPort))
	if err != nil {
		return "", fmt.Errorf("local port %d is unreachable or already in use: %w", localPort, err)
	}
	_ = testLn.Close()

	// Prepare kubernetes client configuration.
	var config *rest.Config
	var clientset *kubernetes.Clientset

	timeout := defaultTimeout
	if c != nil && c.timeout > 0 {
		timeout = c.timeout
	}

	if strings.TrimSpace(kubeconfig) != "" {
		cfg, parseErr := restConfigFromKubeconfig([]byte(kubeconfig))
		if parseErr != nil {
			return "", parseErr
		}
		cfg.Timeout = timeout
		cs, csErr := kubernetes.NewForConfig(cfg)
		if csErr != nil {
			return "", fmt.Errorf("creating clientset: %w", csErr)
		}
		config = cfg
		clientset = cs
	} else {
		if c == nil || c.config == nil || c.clientset == nil {
			return "", fmt.Errorf("client is not configured and kubeconfig is empty")
		}
		config = c.config
		clientset = c.clientset
	}

	handleID, err := generateHandleID()
	if err != nil {
		return "", err
	}

	stopChan := make(chan struct{})
	readyChan := make(chan struct{})
	f := newActiveForwarder(handleID, localPort, remotePort, ns, pod, cb, stopChan)

	// Reserve localPort and handleID in process-wide registry before dialing.
	if err := globalRegistry.reserve(f); err != nil {
		return "", err
	}

	req := clientset.CoreV1().RESTClient().Post().
		Resource("pods").
		Namespace(ns).
		Name(pod).
		SubResource("portforward")

	dialerFactory := defaultPortForwardDialerFactory
	if c != nil && c.portForwardDialerFactory != nil {
		dialerFactory = c.portForwardDialerFactory
	}

	dialer, err := dialerFactory(config, http.MethodPost, req.URL())
	if err != nil {
		globalRegistry.remove(handleID)
		return "", fmt.Errorf("creating port forward dialer: %w", err)
	}

	ports := []string{fmt.Sprintf("%d:%d", localPort, remotePort)}
	addresses := []string{"127.0.0.1"}

	errOut := &callbackWriter{
		fn: func(msg string) {
			msg = strings.TrimSpace(msg)
			if msg != "" {
				f.fireError(msg)
			}
		},
	}

	pf, err := portforward.NewOnAddressesForStreaming(dialer, addresses, ports, stopChan, readyChan, nil, errOut)
	if err != nil {
		globalRegistry.remove(handleID)
		return "", fmt.Errorf("initializing port forwarder: %w", err)
	}
	f.forwarder = pf

	startErrChan := make(chan error, 1)

	// Goroutine Ownership & Structured Concurrency:
	// Exactly ONE owner goroutine is launched per StartPortForward call.
	// This goroutine runs pf.ForwardPorts() until StopPortForward signals stopChan
	// or an unrecoverable network failure occurs. It guarantees unregistration
	// from globalRegistry, closure of doneChan, and invocation of the final
	// PortForwardStopped callback.
	go func() {
		defer close(f.doneChan)
		defer globalRegistry.remove(handleID)

		forwardErr := pf.ForwardPorts()
		if forwardErr == nil {
			forwardErr = errForwardEnded
		}

		// Still starting: this failure is the start outcome, reported by the caller.
		if _, ok := f.transition(stateFailedToStart); ok {
			startErrChan <- forwardErr
			return
		}

		if f.isStopped() || errors.Is(forwardErr, errForwardEnded) {
			f.fireStopped("stopped")
			return
		}
		f.fireError(forwardErr.Error())
		f.fireStopped(forwardErr.Error())
	}()

	startTimer := time.NewTimer(timeout)
	defer startTimer.Stop()

	select {
	case <-readyChan:
		if _, ok := f.transition(stateRunning); ok {
			f.fireReady(localPort)
			return handleID, nil
		}
		// The tunnel died between becoming ready and being reported.
		globalRegistry.remove(handleID)
		return "", fmt.Errorf("starting port forward: %w", <-startErrChan)

	case startErr := <-startErrChan:
		globalRegistry.remove(handleID)
		return "", fmt.Errorf("starting port forward: %w", startErr)

	case <-startTimer.C:
		if _, ok := f.transition(stateAbandoned); ok {
			// The dial has no deadline of its own. Stop the forwarder so it closes as soon
			// as the dial returns, and free the port now so the user can retry.
			f.stop()
			globalRegistry.remove(handleID)
			return "", fmt.Errorf("port forward to %s/%s did not connect within %v", ns, pod, timeout)
		}
		if f.currentState() == stateFailedToStart {
			globalRegistry.remove(handleID)
			return "", fmt.Errorf("starting port forward: %w", <-startErrChan)
		}
		// Unreachable: only this function moves the state to stateRunning.
		return handleID, nil
	}
}

// errForwardEnded stands in for a nil error from ForwardPorts, which means the
// forwarder was stopped or its connection closed cleanly.
var errForwardEnded = errors.New("port forward ended")

// StopPortForward terminates an active port-forward session by handle ID.
// Returns an error if the handle ID is empty or not found in the active registry.
func (c *Client) StopPortForward(handleID string) error {
	id := strings.TrimSpace(handleID)
	if id == "" {
		return fmt.Errorf("handle id is required")
	}

	f, exists := globalRegistry.remove(id)
	if !exists {
		return fmt.Errorf("port forward session %q not found", id)
	}

	f.stop()

	// Wait briefly for the owner goroutine to clean up listeners and fire callbacks.
	select {
	case <-f.doneChan:
	case <-time.After(3 * time.Second):
	}

	return nil
}
