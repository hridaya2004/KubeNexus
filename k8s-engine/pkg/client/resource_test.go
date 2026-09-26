package client

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/client-go/rest"
)

func TestNewListOptions_Empty(t *testing.T) {
	opts := NewListOptions()
	if opts == nil {
		t.Fatal("NewListOptions() returned nil")
	}
	if *opts != (ListOptions{}) {
		t.Errorf("NewListOptions() = %+v, want zero value", *opts)
	}
}

func TestListOptions_ToK8s_AllFields(t *testing.T) {
	opts := &ListOptions{
		LabelSelector:        "app=nginx",
		FieldSelector:        "status.phase=Running",
		ResourceVersion:      "12345",
		ResourceVersionMatch: "NotOlderThan",
		Continue:             "token-abc",
		Limit:                100,
		TimeoutSeconds:       45,
	}

	got := opts.toK8s()

	if got.LabelSelector != "app=nginx" {
		t.Errorf("LabelSelector = %q", got.LabelSelector)
	}
	if got.FieldSelector != "status.phase=Running" {
		t.Errorf("FieldSelector = %q", got.FieldSelector)
	}
	if got.ResourceVersion != "12345" {
		t.Errorf("ResourceVersion = %q", got.ResourceVersion)
	}
	if got.ResourceVersionMatch != metav1.ResourceVersionMatchNotOlderThan {
		t.Errorf("ResourceVersionMatch = %q", got.ResourceVersionMatch)
	}
	if got.Continue != "token-abc" {
		t.Errorf("Continue = %q", got.Continue)
	}
	if got.Limit != 100 {
		t.Errorf("Limit = %d", got.Limit)
	}
	if got.TimeoutSeconds == nil {
		t.Fatal("TimeoutSeconds is nil, want pointer to 45")
	}
	if *got.TimeoutSeconds != 45 {
		t.Errorf("*TimeoutSeconds = %d, want 45", *got.TimeoutSeconds)
	}
}

// Zero must mean "unset" rather than "time out immediately", so the pointer has
// to stay nil.
func TestListOptions_ToK8s_ZeroTimeoutStaysNil(t *testing.T) {
	got := (&ListOptions{TimeoutSeconds: 0}).toK8s()
	if got.TimeoutSeconds != nil {
		t.Errorf("TimeoutSeconds = %v, want nil for zero", *got.TimeoutSeconds)
	}
}

// The pointer must reference a copy, not the caller's struct field, or later
// mutation of the ListOptions would retroactively change an in-flight request.
func TestListOptions_ToK8s_TimeoutPointerIsCopy(t *testing.T) {
	opts := &ListOptions{TimeoutSeconds: 30}
	got := opts.toK8s()

	opts.TimeoutSeconds = 99

	if got.TimeoutSeconds == nil {
		t.Fatal("TimeoutSeconds is nil")
	}
	if *got.TimeoutSeconds != 30 {
		t.Errorf("*TimeoutSeconds = %d, want 30; pointer aliases the source struct", *got.TimeoutSeconds)
	}
}

func TestListOptions_ToK8s_NilReceiver(t *testing.T) {
	var opts *ListOptions
	if got := opts.toK8s(); got != (metav1.ListOptions{}) {
		t.Errorf("nil.toK8s() = %+v, want zero value", got)
	}
}

func TestNewDeleteOptions_DefersToServer(t *testing.T) {
	opts := NewDeleteOptions()
	if opts.GracePeriodSeconds != -1 {
		t.Errorf("GracePeriodSeconds = %d, want -1 to defer to the server", opts.GracePeriodSeconds)
	}

	got := opts.toK8s()
	if got.GracePeriodSeconds != nil {
		t.Errorf("GracePeriodSeconds = %v, want nil", *got.GracePeriodSeconds)
	}
	if got.PropagationPolicy != nil {
		t.Errorf("PropagationPolicy = %v, want nil", *got.PropagationPolicy)
	}
}

// A grace period of zero means "delete immediately" and is meaningfully
// different from unset, so it must survive as a non-nil pointer.
func TestDeleteOptions_ToK8s_ZeroGraceIsImmediate(t *testing.T) {
	got := (&DeleteOptions{GracePeriodSeconds: 0}).toK8s()
	if got.GracePeriodSeconds == nil {
		t.Fatal("GracePeriodSeconds is nil, want pointer to 0 for immediate deletion")
	}
	if *got.GracePeriodSeconds != 0 {
		t.Errorf("*GracePeriodSeconds = %d, want 0", *got.GracePeriodSeconds)
	}
}

func TestDeleteOptions_ToK8s_PropagationPolicy(t *testing.T) {
	got := (&DeleteOptions{GracePeriodSeconds: -1, PropagationPolicy: "Foreground"}).toK8s()
	if got.PropagationPolicy == nil {
		t.Fatal("PropagationPolicy is nil")
	}
	if *got.PropagationPolicy != metav1.DeletePropagationForeground {
		t.Errorf("*PropagationPolicy = %q, want Foreground", *got.PropagationPolicy)
	}
}

func TestDeleteOptions_ToK8s_NilReceiver(t *testing.T) {
	var opts *DeleteOptions
	got := opts.toK8s()
	if got.GracePeriodSeconds != nil || got.PropagationPolicy != nil {
		t.Errorf("nil.toK8s() = %+v, want zero value", got)
	}
}

func TestGroupVersionResource_Validate(t *testing.T) {
	tests := []struct {
		name    string
		gvr     *GroupVersionResource
		wantErr string
	}{
		{
			name: "core resource with empty group",
			gvr:  NewGroupVersionResource("", "v1", "pods"),
		},
		{
			name: "grouped resource",
			gvr:  NewGroupVersionResource("apps", "v1", "deployments"),
		},
		{
			name:    "nil identifier",
			gvr:     nil,
			wantErr: "resource identifier is required",
		},
		{
			name:    "missing version",
			gvr:     NewGroupVersionResource("apps", "", "deployments"),
			wantErr: "version is required",
		},
		{
			name:    "blank version",
			gvr:     NewGroupVersionResource("apps", "   ", "deployments"),
			wantErr: "version is required",
		},
		{
			name:    "missing resource",
			gvr:     NewGroupVersionResource("apps", "v1", ""),
			wantErr: "resource is required",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := tt.gvr.validate()

			if tt.wantErr != "" {
				if err == nil {
					t.Fatalf("validate() error = nil, want %q", tt.wantErr)
				}
				if !strings.Contains(err.Error(), tt.wantErr) {
					t.Errorf("validate() error = %q, want to contain %q", err, tt.wantErr)
				}
				return
			}

			if err != nil {
				t.Fatalf("validate() error = %v", err)
			}
			if got.Group != tt.gvr.Group || got.Version != tt.gvr.Version || got.Resource != tt.gvr.Resource {
				t.Errorf("validate() = %+v, want %+v", got, tt.gvr)
			}
		})
	}
}

func TestWellKnownResources(t *testing.T) {
	tests := []struct {
		name string
		got  *GroupVersionResource
		want string
	}{
		{"pods", PodsResource(), "pods"},
		{"namespaces", NamespacesResource(), "namespaces"},
		{"events", EventsResource(), "events"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if tt.got.Group != "" {
				t.Errorf("Group = %q, want empty for a core resource", tt.got.Group)
			}
			if tt.got.Version != "v1" {
				t.Errorf("Version = %q, want v1", tt.got.Version)
			}
			if tt.got.Resource != tt.want {
				t.Errorf("Resource = %q, want %q", tt.got.Resource, tt.want)
			}
		})
	}
}

func TestStripManagedFields(t *testing.T) {
	obj := &unstructured.Unstructured{Object: map[string]any{
		"apiVersion": "v1",
		"kind":       "Pod",
		"metadata": map[string]any{
			"name":          "nginx",
			"namespace":     "default",
			"labels":        map[string]any{"app": "nginx"},
			"managedFields": []any{map[string]any{"manager": "kubectl"}},
		},
	}}

	stripManagedFields(obj)

	metadata, ok := obj.Object["metadata"].(map[string]any)
	if !ok {
		t.Fatal("metadata is missing or not a map")
	}
	if _, present := metadata["managedFields"]; present {
		t.Error("managedFields survived stripping")
	}
	if metadata["name"] != "nginx" {
		t.Errorf("name = %v, want nginx", metadata["name"])
	}
	if _, present := metadata["labels"]; !present {
		t.Error("labels were removed; only managedFields should be stripped")
	}
}

func TestStripManagedFields_Tolerant(t *testing.T) {
	// None of these should panic.
	stripManagedFields(nil)
	stripManagedFields(&unstructured.Unstructured{})
	stripManagedFields(&unstructured.Unstructured{Object: map[string]any{}})
	stripManagedFields(&unstructured.Unstructured{Object: map[string]any{
		"metadata": map[string]any{"name": "x"},
	}})
}

// The generic methods must fail cleanly rather than panic when the dynamic
// client was never built.
func TestGenericMethods_UnconfiguredClient(t *testing.T) {
	c := &Client{timeout: defaultTimeout}
	gvr := PodsResource()

	if _, err := c.ListJSON(gvr, "default", nil); err == nil {
		t.Error("ListJSON() on unconfigured client expected error, got nil")
	}
	if _, err := c.GetJSON(gvr, "default", "nginx"); err == nil {
		t.Error("GetJSON() on unconfigured client expected error, got nil")
	}
	if err := c.DeleteResource(gvr, "default", "nginx", nil); err == nil {
		t.Error("DeleteResource() on unconfigured client expected error, got nil")
	}
	if _, err := c.EventsForJSON("default", "Pod", "nginx"); err == nil {
		t.Error("EventsForJSON() on unconfigured client expected error, got nil")
	}
}

// Argument validation must happen before any network call is attempted.
func TestGenericMethods_ArgumentValidation(t *testing.T) {
	c := &Client{timeout: defaultTimeout}

	if _, err := c.GetJSON(PodsResource(), "default", ""); err == nil {
		t.Error("GetJSON() with empty name expected error, got nil")
	}
	if err := c.DeleteResource(PodsResource(), "default", "  ", nil); err == nil {
		t.Error("DeleteResource() with blank name expected error, got nil")
	}
	if _, err := c.ListJSON(NewGroupVersionResource("", "", "pods"), "", nil); err == nil {
		t.Error("ListJSON() with empty version expected error, got nil")
	}
}

func TestEventsForJSON_RequiresInvolvedObject(t *testing.T) {
	c := &Client{timeout: defaultTimeout}

	if _, err := c.EventsForJSON("default", "", "nginx"); err == nil {
		t.Error("EventsForJSON() with empty kind expected error, got nil")
	}
	if _, err := c.EventsForJSON("default", "Pod", ""); err == nil {
		t.Error("EventsForJSON() with empty name expected error, got nil")
	}
}

func TestDeploymentsResource(t *testing.T) {
	gvr := DeploymentsResource()
	if gvr.Group != "apps" || gvr.Version != "v1" || gvr.Resource != "deployments" {
		t.Errorf("DeploymentsResource() = %+v, want apps/v1/deployments", gvr)
	}
}

func TestDeploymentMethods_UnconfiguredClient(t *testing.T) {
	c := &Client{timeout: defaultTimeout}

	if _, err := c.PatchResource(DeploymentsResource(), "default", "nginx", "{}"); err == nil {
		t.Error("PatchResource() on unconfigured client expected error, got nil")
	}
	if err := c.ScaleDeployment("default", "nginx", 3); err == nil {
		t.Error("ScaleDeployment() on unconfigured client expected error, got nil")
	}
	if err := c.RestartDeployment("default", "nginx"); err == nil {
		t.Error("RestartDeployment() on unconfigured client expected error, got nil")
	}
	if err := c.DeleteDeployment("default", "nginx"); err == nil {
		t.Error("DeleteDeployment() on unconfigured client expected error, got nil")
	}
}

func TestScaleDeployment_NegativeReplicas(t *testing.T) {
	c := &Client{timeout: defaultTimeout}
	if err := c.ScaleDeployment("default", "nginx", -1); err == nil {
		t.Error("ScaleDeployment() with negative replicas expected error, got nil")
	}
}

// Scaling must go through the scale subresource, which is what an RBAC role that only
// grants deployments/scale allows.
func TestScaleDeployment_PatchesScaleSubresource(t *testing.T) {
	var gotMethod, gotPath, gotBody string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotMethod, gotPath = r.Method, r.URL.Path
		body, _ := io.ReadAll(r.Body)
		gotBody = string(body)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"apiVersion":"autoscaling/v1","kind":"Scale","metadata":{"name":"nginx","namespace":"default"},"spec":{"replicas":3}}`))
	}))
	defer srv.Close()

	c, err := newClientFromConfig(&rest.Config{Host: srv.URL}, 15*time.Second)
	if err != nil {
		t.Fatalf("newClientFromConfig() error = %v", err)
	}
	if err := c.ScaleDeployment("default", "nginx", 3); err != nil {
		t.Fatalf("ScaleDeployment() error = %v", err)
	}
	if gotMethod != http.MethodPatch {
		t.Errorf("method = %s, want PATCH", gotMethod)
	}
	if gotPath != "/apis/apps/v1/namespaces/default/deployments/nginx/scale" {
		t.Errorf("path = %s, want the scale subresource", gotPath)
	}
	if gotBody != `{"spec":{"replicas":3}}` {
		t.Errorf("body = %s", gotBody)
	}
}
