package com.project.autonomous_api_optimizer.model;

import java.util.Map;

public class FlaskResult {
    private String endpoint_path;
    private boolean is_anomaly;
    private String reason;
    private Map<String, Double> metrics;
    private String timestamp;

    // Getters and setters

    public String getEndpoint_path() {
        return endpoint_path;
    }

    public void setEndpoint_path(String endpoint_path) {
        this.endpoint_path = endpoint_path;
    }

    public boolean isAnomaly() {
        return is_anomaly;
    }

    public void setIs_anomaly(boolean is_anomaly) {
        this.is_anomaly = is_anomaly;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Double> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Double> metrics) {
        this.metrics = metrics;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
