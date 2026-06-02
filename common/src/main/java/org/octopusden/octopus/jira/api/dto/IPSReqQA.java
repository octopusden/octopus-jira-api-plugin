package org.octopusden.octopus.jira.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class IPSReqQA {

    private final String key;
    private final String summary;
    private final String status;
    private final List<String> system;
    private final List<IssueBean> cases;

    @JsonCreator
    public IPSReqQA(
            @JsonProperty("key") String key,
            @JsonProperty("summary") String summary,
            @JsonProperty("status") String status,
            @JsonProperty("system") List<String> system,
            @JsonProperty("cases") List<IssueBean> cases
    ) {
        this.key = key;
        this.summary = summary;
        this.status = status;
        this.system = system != null ? system : Collections.emptyList();
        this.cases = cases != null ? cases : Collections.emptyList();
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

    public List<String> getSystem() {
        return system;
    }

    public List<IssueBean> getCases() {
        return cases;
    }
}
