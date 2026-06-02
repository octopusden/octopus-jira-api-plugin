package org.octopusden.octopus.jira.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class IssueBean {

    private final String key;
    private final String summary;
    private final String issueType;
    private final String status;
    private final String priority;
    private final List<String> labels;
    private final List<String> fixVersions;
    private final List<String> components;
    private final String resolution;

    @JsonCreator
    public IssueBean(
            @JsonProperty("key") String key,
            @JsonProperty("summary") String summary,
            @JsonProperty("issueType") String issueType,
            @JsonProperty("status") String status,
            @JsonProperty("priority") String priority,
            @JsonProperty("labels") List<String> labels,
            @JsonProperty("fixVersions") List<String> fixVersions,
            @JsonProperty("components") List<String> components,
            @JsonProperty("resolution") String resolution
    ) {
        this.key = key;
        this.summary = summary;
        this.issueType = issueType;
        this.status = status;
        this.priority = priority;
        this.labels = labels;
        this.fixVersions = fixVersions;
        this.components = components;
        this.resolution = resolution;
    }

    public String getKey() {
        return key;
    }

    public String getSummary() {
        return summary;
    }

    public String getIssueType() {
        return issueType;
    }

    public String getStatus() {
        return status;
    }

    public String getPriority() {
        return priority;
    }

    public List<String> getLabels() {
        return labels;
    }

    public List<String> getFixVersions() {
        return fixVersions;
    }

    public List<String> getComponents() {
        return components;
    }

    public String getResolution() {
        return resolution;
    }
}
