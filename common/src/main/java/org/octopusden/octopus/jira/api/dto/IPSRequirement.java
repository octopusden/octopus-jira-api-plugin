package org.octopusden.octopus.jira.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class IPSRequirement {

    private final String key;
    private final String name;
    private final String status;
    private final List<String> labels;
    private final String region;
    private final String license;
    private final String ipsCode;
    private final List<IPSReqDev> development;
    private final List<IPSReqQA> testing;

    @JsonCreator
    public IPSRequirement(
            @JsonProperty("key") String key,
            @JsonProperty("name") String name,
            @JsonProperty("status") String status,
            @JsonProperty("labels") List<String> labels,
            @JsonProperty("region") String region,
            @JsonProperty("license") String license,
            @JsonProperty("ipsCode") String ipsCode,
            @JsonProperty("development") List<IPSReqDev> development,
            @JsonProperty("testing") List<IPSReqQA> testing
    ) {
        this.key = key;
        this.name = name;
        this.status = status;
        this.labels = labels != null ? labels : Collections.emptyList();
        this.region = region;
        this.license = license;
        this.ipsCode = ipsCode;
        this.development = development != null ? development : Collections.emptyList();
        this.testing = testing != null ? testing : Collections.emptyList();
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getLabels() {
        return labels;
    }

    public String getRegion() {
        return region;
    }

    public String getLicense() {
        return license;
    }

    public String getIpsCode() {
        return ipsCode;
    }

    public List<IPSReqDev> getDevelopment() {
        return development;
    }

    public List<IPSReqQA> getTesting() {
        return testing;
    }
}
