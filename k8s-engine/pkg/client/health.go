// Package client provides health check capabilities for Kubernetes clusters.
package client

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
)

// ClusterHealth contains health probe results and version metadata.
type ClusterHealth struct {
	Livez         bool   `json:"livez"`
	Readyz        bool   `json:"readyz"`
	Healthz       bool   `json:"healthz"`
	ServerVersion string `json:"serverVersion"`
	StatusMessage string `json:"statusMessage"`
}

// CheckLivez performs a liveness probe on /livez.
func (c *Client) CheckLivez() (bool, error) {
	ctx, cancel := context.WithTimeout(context.Background(), c.timeout)
	defer cancel()

	data, err := c.clientset.Discovery().RESTClient().Get().AbsPath("/livez").DoRaw(ctx)
	if err != nil {
		return false, fmt.Errorf("livez check failed: %w", err)
	}
	return strings.TrimSpace(string(data)) == "ok", nil
}

// CheckReadyz performs a readiness probe on /readyz.
func (c *Client) CheckReadyz() (bool, error) {
	ctx, cancel := context.WithTimeout(context.Background(), c.timeout)
	defer cancel()

	data, err := c.clientset.Discovery().RESTClient().Get().AbsPath("/readyz").DoRaw(ctx)
	if err != nil {
		return false, fmt.Errorf("readyz check failed: %w", err)
	}
	return strings.TrimSpace(string(data)) == "ok", nil
}

// CheckHealthz performs a legacy health check on /healthz.
func (c *Client) CheckHealthz() (bool, error) {
	ctx, cancel := context.WithTimeout(context.Background(), c.timeout)
	defer cancel()

	data, err := c.clientset.Discovery().RESTClient().Get().AbsPath("/healthz").DoRaw(ctx)
	if err != nil {
		return false, fmt.Errorf("healthz check failed: %w", err)
	}
	return strings.TrimSpace(string(data)) == "ok", nil
}

// ServerVersion returns the Kubernetes server version string (e.g. v1.30.0).
func (c *Client) ServerVersion() (string, error) {
	versionInfo, err := c.clientset.Discovery().ServerVersion()
	if err != nil {
		return "", fmt.Errorf("getting server version: %w", err)
	}
	return versionInfo.GitVersion, nil
}

// Ping checks cluster connectivity and health endpoints, returning a descriptive summary.
func (c *Client) Ping() (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), c.timeout)
	defer cancel()

	var versionStr string
	if ver, err := c.clientset.Discovery().ServerVersion(); err == nil && ver != nil {
		versionStr = ver.GitVersion
	}

	readyzData, readyzErr := c.clientset.Discovery().RESTClient().Get().AbsPath("/readyz").DoRaw(ctx)
	if readyzErr == nil && strings.TrimSpace(string(readyzData)) == "ok" {
		if versionStr != "" {
			return fmt.Sprintf("Cluster ready & healthy (Kubernetes %s)", versionStr), nil
		}
		return "Cluster ready & healthy (readyz OK)", nil
	}

	livezData, livezErr := c.clientset.Discovery().RESTClient().Get().AbsPath("/livez").DoRaw(ctx)
	if livezErr == nil && strings.TrimSpace(string(livezData)) == "ok" {
		if versionStr != "" {
			return fmt.Sprintf("Cluster live & responding (Kubernetes %s)", versionStr), nil
		}
		return "Cluster live & responding (livez OK)", nil
	}

	healthzData, healthzErr := c.clientset.Discovery().RESTClient().Get().AbsPath("/healthz").DoRaw(ctx)
	if healthzErr == nil && strings.TrimSpace(string(healthzData)) == "ok" {
		if versionStr != "" {
			return fmt.Sprintf("Cluster healthy (Kubernetes %s)", versionStr), nil
		}
		return "Cluster healthy (healthz OK)", nil
	}

	if versionStr != "" {
		return fmt.Sprintf("API Server reachable (Kubernetes %s)", versionStr), nil
	}

	if readyzErr != nil {
		return "", fmt.Errorf("health check failed: %w", readyzErr)
	}
	if livezErr != nil {
		return "", fmt.Errorf("health check failed: %w", livezErr)
	}
	if healthzErr != nil {
		return "", fmt.Errorf("health check failed: %w", healthzErr)
	}
	return "", fmt.Errorf("cluster is unreachable")
}

// CheckHealthJSON returns detailed health status and version as a JSON string.
func (c *Client) CheckHealthJSON() (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), c.timeout)
	defer cancel()

	var health ClusterHealth
	// firstErr keeps the reason when nothing answers, so a TLS failure or a 401 is not
	// reported the same way as a cluster that is down.
	var firstErr error
	noteErr := func(err error) {
		if firstErr == nil {
			firstErr = err
		}
	}

	if ver, err := c.clientset.Discovery().ServerVersion(); err == nil && ver != nil {
		health.ServerVersion = ver.GitVersion
	} else if err != nil {
		noteErr(err)
	}

	if livezData, err := c.clientset.Discovery().RESTClient().Get().AbsPath("/livez").DoRaw(ctx); err == nil {
		health.Livez = strings.TrimSpace(string(livezData)) == "ok"
	} else {
		noteErr(err)
	}

	if readyzData, err := c.clientset.Discovery().RESTClient().Get().AbsPath("/readyz").DoRaw(ctx); err == nil {
		health.Readyz = strings.TrimSpace(string(readyzData)) == "ok"
	} else {
		noteErr(err)
	}

	if healthzData, err := c.clientset.Discovery().RESTClient().Get().AbsPath("/healthz").DoRaw(ctx); err == nil {
		health.Healthz = strings.TrimSpace(string(healthzData)) == "ok"
	} else {
		noteErr(err)
	}

	if health.Readyz {
		health.StatusMessage = "Ready"
	} else if health.Livez {
		health.StatusMessage = "Live (not ready)"
	} else if health.Healthz {
		health.StatusMessage = "Healthy"
	} else if health.ServerVersion != "" {
		health.StatusMessage = "Reachable"
	} else if firstErr != nil {
		health.StatusMessage = "Unhealthy: " + firstErr.Error()
	} else {
		health.StatusMessage = "Unhealthy"
	}

	bytes, err := json.Marshal(health)
	if err != nil {
		return "", fmt.Errorf("marshaling health json: %w", err)
	}
	return string(bytes), nil
}
