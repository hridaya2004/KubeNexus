package client

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestClient_HealthChecks(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/livez", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	mux.HandleFunc("/readyz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	mux.HandleFunc("/version", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"major":"1","minor":"30","gitVersion":"v1.30.0"}`))
	})

	server := httptest.NewTLSServer(mux)
	defer server.Close()

	kubeconfig := tlsKubeconfig(server)

	client, err := NewClient(kubeconfig)
	if err != nil {
		t.Fatalf("failed to create client: %v", err)
	}

	live, err := client.CheckLivez()
	if err != nil || !live {
		t.Errorf("CheckLivez() = %v, %v; want true, nil", live, err)
	}

	ready, err := client.CheckReadyz()
	if err != nil || !ready {
		t.Errorf("CheckReadyz() = %v, %v; want true, nil", ready, err)
	}

	healthy, err := client.CheckHealthz()
	if err != nil || !healthy {
		t.Errorf("CheckHealthz() = %v, %v; want true, nil", healthy, err)
	}

	ver, err := client.ServerVersion()
	if err != nil || ver != "v1.30.0" {
		t.Errorf("ServerVersion() = %q, %v; want v1.30.0, nil", ver, err)
	}

	ping, err := client.Ping()
	if err != nil || !strings.Contains(ping, "ready & healthy") {
		t.Errorf("Ping() = %q, %v; want ready & healthy, nil", ping, err)
	}

	healthJSON, err := client.CheckHealthJSON()
	if err != nil || !strings.Contains(healthJSON, `"livez":true`) {
		t.Errorf("CheckHealthJSON() = %q, %v; want livez:true, nil", healthJSON, err)
	}
}

func TestClient_HealthChecks_Unhealthy(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/livez", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte("internal error"))
	})
	mux.HandleFunc("/readyz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
		_, _ = w.Write([]byte("not ready"))
	})
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte("unhealthy"))
	})

	server := httptest.NewTLSServer(mux)
	defer server.Close()

	kubeconfig := tlsKubeconfig(server)

	client, err := NewClient(kubeconfig)
	if err != nil {
		t.Fatalf("failed to create client: %v", err)
	}

	live, err := client.CheckLivez()
	if err == nil || live {
		t.Errorf("CheckLivez() = %v, %v; want error", live, err)
	}

	ready, err := client.CheckReadyz()
	if err == nil || ready {
		t.Errorf("CheckReadyz() = %v, %v; want error", ready, err)
	}

	ping, err := client.Ping()
	if err == nil {
		t.Errorf("Ping() = %q, expected error", ping)
	}
}

// When every probe fails, the status must say why: an expired token or a TLS problem
// must not look the same as a cluster that is down.
func TestCheckHealthJSON_ReportsWhyItIsUnhealthy(t *testing.T) {
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusUnauthorized)
		_, _ = w.Write([]byte(`{"kind":"Status","apiVersion":"v1","status":"Failure","message":"Unauthorized","reason":"Unauthorized","code":401}`))
	}))
	defer server.Close()

	client, err := NewClient(tlsKubeconfig(server))
	if err != nil {
		t.Fatalf("NewClient() error = %v", err)
	}
	out, err := client.CheckHealthJSON()
	if err != nil {
		t.Fatalf("CheckHealthJSON() error = %v", err)
	}
	var health ClusterHealth
	if err := json.Unmarshal([]byte(out), &health); err != nil {
		t.Fatalf("decoding %s: %v", out, err)
	}
	if !strings.HasPrefix(health.StatusMessage, "Unhealthy: ") || !strings.Contains(health.StatusMessage, "provide credentials") {
		t.Errorf("StatusMessage = %q, want the 401 reason", health.StatusMessage)
	}
}
