package org.octopusden.octopus.jira.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class IPSResponse {

    private final String key;
    private final String ips;
    private final String release;
    private final String status;
    private final List<String> labels;
    private final List<IPSRequirement> requirements;

    @JsonCreator
    public IPSResponse(
            @JsonProperty("key") String key,
            @JsonProperty("ips") String ips,
            @JsonProperty("release") String release,
            @JsonProperty("status") String status,
            @JsonProperty("labels") List<String> labels,
            @JsonProperty("requirements") List<IPSRequirement> requirements
    ) {
        this.key = key;
        this.ips = ips;
        this.release = release;
        this.status = status;
        this.labels = labels != null ? labels : Collections.emptyList();
        this.requirements = requirements != null ? requirements : Collections.emptyList();
    }

    public String getKey() {
        return key;
    }

    public String getIps() {
        return ips;
    }

    public String getRelease() {
        return release;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getLabels() {
        return labels;
    }

    public List<IPSRequirement> getRequirements() {
        return requirements;
    }
}
