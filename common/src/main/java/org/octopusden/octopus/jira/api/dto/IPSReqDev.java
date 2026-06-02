package org.octopusden.octopus.jira.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class IPSReqDev {

    private final String key;
    private final String summary;
    private final String status;
    private final List<String> labels;
    private final String license;
    private final List<String> system;
    private final List<DevComponent> components;

    @JsonCreator
    public IPSReqDev(
            @JsonProperty("key") String key,
            @JsonProperty("summary") String summary,
            @JsonProperty("status") String status,
            @JsonProperty("labels") List<String> labels,
            @JsonProperty("license") String license,
            @JsonProperty("system") List<String> system,
            @JsonProperty("components") List<DevComponent> components
    ) {
        this.key = key;
        this.summary = summary;
        this.status = status;
        this.labels = labels != null ? labels : Collections.emptyList();
        this.license = license;
        this.system = system != null ? system : Collections.emptyList();
        this.components = components != null ? components : Collections.emptyList();
    }

    public String getKey() {
        return key;
    }

    public String getSummary() {
        return summary;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getLabels() {
        return labels;
    }

    public String getLicense() {
        return license;
    }

    public List<String> getSystem() {
        return system;
    }

    public List<DevComponent> getComponents() {
        return components;
    }
}
