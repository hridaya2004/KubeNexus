package client

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/client-go/rest"
)

const nginxDeploymentYAML = `apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx
  labels:
    app: nginx
spec:
  replicas: 2
  selector:
    matchLabels:
      app: nginx
`

const nginxDeploymentJSON = `{
  "apiVersion": "apps/v1",
  "kind": "Deployment",
  "metadata": {"name": "nginx", "labels": {"app": "nginx"}},
  "spec": {"replicas": 2}
}`

// The fake API server's reply, including the managedFields that CreateResource
// must strip before handing the object back.
const createdDeploymentJSON = `{
  "apiVersion": "apps/v1",
  "kind": "Deployment",
  "metadata": {
    "name": "nginx",
    "namespace": "default",
    "uid": "6d8f4b21-c7a9-4f0e-b1e3-9a2d5c7f8e10",
    "managedFields": [{"manager": "kubenexus", "operation": "Update"}]
  },
  "spec": {"replicas": 2},
  "status": {}
}`

// recordedCall captures one HTTP request the fake API server received.
type recordedCall struct {
	Method string
	Path   string
	Body   map[string]any
}

// newCreateTestClient returns a client backed by a fake API server that accepts
// any create, records every request and replays createdDeploymentJSON.
func newCreateTestClient(t *testing.T, calls *[]recordedCall) *Client {
	t.Helper()
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		body, err := io.ReadAll(r.Body)
		if err != nil {
			t.Errorf("reading request body: %v", err)
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		obj := map[string]any{}
		if len(body) > 0 {
			if err := json.Unmarshal(body, &obj); err != nil {
				t.Errorf("request body is not JSON: %v", err)
			}
		}
		*calls = append(*calls, recordedCall{Method: r.Method, Path: r.URL.Path, Body: obj})

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		io.WriteString(w, createdDeploymentJSON)
	})

	srv := httptest.NewServer(mux)
	t.Cleanup(srv.Close)

	c, err := newClientFromConfig(&rest.Config{Host: srv.URL}, 15*time.Second)
	if err != nil {
		t.Fatalf("newClientFromConfig() error = %v", err)
	}
	return c
}

// newFailingServerURL backs validation tests: any request reaching it means
// CreateResource made a network call despite invalid arguments.
func newFailingServerURL(t *testing.T) string {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Errorf("unexpected %s %s reached the fake API server", r.Method, r.URL.Path)
		http.Error(w, "should not be called", http.StatusInternalServerError)
	}))
	t.Cleanup(srv.Close)
	return srv.URL
}

func TestCreateResource_CreatesFromManifest(t *testing.T) {
	tests := []struct {
		name     string
		gvr      *GroupVersionResource
		ns       string
		manifest string
		wantPath string
	}{
		{
			name:     "yaml deployment into default namespace",
			gvr:      NewGroupVersionResource("apps", "v1", "deployments"),
			ns:       "default",
			manifest: nginxDeploymentYAML,
			wantPath: "/apis/apps/v1/namespaces/default/deployments",
		},
		{
			name:     "json deployment",
			gvr:      NewGroupVersionResource("apps", "v1", "deployments"),
			ns:       "default",
			manifest: nginxDeploymentJSON,
			wantPath: "/apis/apps/v1/namespaces/default/deployments",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var calls []recordedCall
			c := newCreateTestClient(t, &calls)

			got, err := c.CreateResource(tt.gvr, tt.ns, tt.manifest)
			if err != nil {
				t.Fatalf("CreateResource() error = %v", err)
			}
			if len(calls) != 1 {
				t.Fatalf("fake API server received %d requests, want 1: %+v", len(calls), calls)
			}
			call := calls[0]
			if call.Method != http.MethodPost {
				t.Errorf("request method = %q, want POST", call.Method)
			}
			if call.Path != tt.wantPath {
				t.Errorf("request path = %q, want %q", call.Path, tt.wantPath)
			}

			// The manifest must reach the wire intact.
			if got := call.Body["apiVersion"]; got != "apps/v1" {
				t.Errorf("posted apiVersion = %v, want apps/v1", got)
			}
			if got := call.Body["kind"]; got != "Deployment" {
				t.Errorf("posted kind = %v, want Deployment", got)
			}
			metadata, ok := call.Body["metadata"].(map[string]any)
			if !ok {
				t.Fatal("posted metadata is missing or not an object")
			}
			if metadata["name"] != "nginx" {
				t.Errorf("posted metadata.name = %v, want nginx", metadata["name"])
			}
			if metadata["namespace"] != "default" {
				t.Errorf("posted metadata.namespace = %v, want default", metadata["namespace"])
			}
			spec, ok := call.Body["spec"].(map[string]any)
			if !ok {
				t.Fatal("posted spec is missing or not an object")
			}
			if spec["replicas"] != float64(2) {
				t.Errorf("posted spec.replicas = %v, want 2", spec["replicas"])
			}

			// The return value must be the server's response verbatim,
			// minus managedFields. UnstructuredJSONScheme.Encode terminates
			// the payload with a newline, matching GetJSON/ListJSON.
			var created map[string]any
			if err := json.Unmarshal([]byte(createdDeploymentJSON), &created); err != nil {
				t.Fatalf("decoding server response fixture: %v", err)
			}
			unstructured.RemoveNestedField(created, "metadata", "managedFields")
			want, err := json.Marshal(created)
			if err != nil {
				t.Fatalf("encoding expected payload: %v", err)
			}
			if got != string(want)+"\n" {
				t.Errorf("CreateResource() =\n%s\nwant verbatim server response:\n%s", got, want)
			}
		})
	}
}

func TestCreateResource_NamespaceHandling(t *testing.T) {
	const stagingManifest = `apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx
  namespace: staging
spec:
  replicas: 2
`

	tests := []struct {
		name       string
		ns         string
		manifest   string
		wantPath   string
		wantBodyNS string
	}{
		{
			name:       "empty namespace falls back to the manifest's own",
			manifest:   stagingManifest,
			wantPath:   "/apis/apps/v1/namespaces/staging/deployments",
			wantBodyNS: "staging",
		},
		{
			name:       "non-empty namespace overrides the manifest",
			ns:         "prod",
			manifest:   stagingManifest,
			wantPath:   "/apis/apps/v1/namespaces/prod/deployments",
			wantBodyNS: "prod",
		},
		{
			name: "no namespace anywhere uses the cluster-scoped path",
			manifest: `apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx
spec:
  replicas: 2
`,
			wantPath:   "/apis/apps/v1/deployments",
			wantBodyNS: "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var calls []recordedCall
			c := newCreateTestClient(t, &calls)

			if _, err := c.CreateResource(NewGroupVersionResource("apps", "v1", "deployments"), tt.ns, tt.manifest); err != nil {
				t.Fatalf("CreateResource() error = %v", err)
			}
			if len(calls) != 1 {
				t.Fatalf("fake API server received %d requests, want 1: %+v", len(calls), calls)
			}
			call := calls[0]
			if call.Path != tt.wantPath {
				t.Errorf("request path = %q, want %q", call.Path, tt.wantPath)
			}
			metadata, ok := call.Body["metadata"].(map[string]any)
			if !ok {
				t.Fatal("posted metadata is missing or not an object")
			}
			if tt.wantBodyNS == "" {
				if _, present := metadata["namespace"]; present {
					t.Error("posted metadata.namespace should be absent for a cluster-scoped create")
				}
				return
			}
			if metadata["namespace"] != tt.wantBodyNS {
				t.Errorf("posted metadata.namespace = %v, want %q", metadata["namespace"], tt.wantBodyNS)
			}
		})
	}
}

func TestCountManifestObjects(t *testing.T) {
	tests := []struct {
		name     string
		manifest string
		want     int
	}{
		{"single document", "apiVersion: v1\nkind: Pod\n", 1},
		{"leading separator", "---\napiVersion: v1\nkind: Pod\n", 1},
		{"trailing separator and comment", "apiVersion: v1\nkind: Pod\n---\n# nothing here\n", 1},
		{"two documents", "apiVersion: v1\nkind: Pod\n---\napiVersion: v1\nkind: Service\n", 2},
		{"json", `{"apiVersion":"v1","kind":"Pod"}`, 1},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := countManifestObjects(tt.manifest)
			if err != nil || got != tt.want {
				t.Errorf("countManifestObjects() = %d, %v; want %d", got, err, tt.want)
			}
		})
	}
}

// Argument validation must happen before any network call is attempted.
func TestCreateResource_Validation(t *testing.T) {
	serverURL := newFailingServerURL(t)
	c, err := newClientFromConfig(&rest.Config{Host: serverURL}, 15*time.Second)
	if err != nil {
		t.Fatalf("newClientFromConfig() error = %v", err)
	}

	tests := []struct {
		name     string
		gvr      *GroupVersionResource
		ns       string
		manifest string
		wantErr  string
	}{
		{
			name:     "missing apiVersion",
			gvr:      NewGroupVersionResource("apps", "v1", "deployments"),
			manifest: "kind: Deployment\nmetadata:\n  name: nginx\n",
			wantErr:  "manifest is missing apiVersion",
		},
		{
			name:     "missing kind",
			gvr:      NewGroupVersionResource("apps", "v1", "deployments"),
			manifest: "apiVersion: apps/v1\nmetadata:\n  name: nginx\n",
			wantErr:  "manifest is missing kind",
		},
		{
			name:     "missing metadata.name",
			gvr:      NewGroupVersionResource("apps", "v1", "deployments"),
			manifest: "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  labels:\n    app: nginx\n",
			wantErr:  "manifest is missing metadata.name",
		},
		{
			name:     "syntactically invalid yaml",
			gvr:      NewGroupVersionResource("apps", "v1", "deployments"),
			manifest: "apiVersion: apps/v1\nkind: Deployment\nmetadata: [unclosed\n",
			wantErr:  "parsing manifest",
		},
		{
			name:     "empty manifest",
			gvr:      NewGroupVersionResource("apps", "v1", "deployments"),
			manifest: "",
			wantErr:  "manifest is required",
		},
		{
			name:     "whitespace-only manifest",
			gvr:      NewGroupVersionResource("apps", "v1", "deployments"),
			manifest: "   \n\t  ",
			wantErr:  "manifest is required",
		},
		{
			name:     "multi-document manifest",
			gvr:      NewGroupVersionResource("apps", "v1", "deployments"),
			manifest: nginxDeploymentYAML + "\n---\napiVersion: v1\nkind: Service\nmetadata:\n  name: nginx\n",
			wantErr:  "manifest contains 2 objects",
		},
		{
			name:     "nil resource identifier",
			gvr:      nil,
			manifest: nginxDeploymentYAML,
			wantErr:  "resource identifier is required",
		},
		{
			name:     "resource identifier without version",
			gvr:      NewGroupVersionResource("apps", "", "deployments"),
			manifest: nginxDeploymentYAML,
			wantErr:  "version is required",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := c.CreateResource(tt.gvr, tt.ns, tt.manifest)
			if err == nil {
				t.Fatalf("CreateResource() error = nil, want %q", tt.wantErr)
			}
			if !strings.Contains(err.Error(), tt.wantErr) {
				t.Errorf("CreateResource() error = %q, want to contain %q", err, tt.wantErr)
			}
		})
	}
}

func TestCreateResource_UnconfiguredClient(t *testing.T) {
	configured := &Client{timeout: defaultTimeout}
	var unconfigured *Client

	for name, c := range map[string]*Client{"nil receiver": unconfigured, "zero dynamic client": configured} {
		t.Run(name, func(t *testing.T) {
			_, err := c.CreateResource(PodsResource(), "default", nginxDeploymentYAML)
			if err == nil {
				t.Fatal("CreateResource() on unconfigured client expected error, got nil")
			}
			if !strings.Contains(err.Error(), "client is not configured") {
				t.Errorf("error = %q, want to contain %q", err, "client is not configured")
			}
		})
	}
}
