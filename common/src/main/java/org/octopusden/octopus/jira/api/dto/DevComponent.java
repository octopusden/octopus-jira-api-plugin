package org.octopusden.octopus.jira.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class DevComponent {

    private final String name;
    private final List<String> fixVersions;
    private final List<IssueBean> issues;

    @JsonCreator
    public DevComponent(
            @JsonProperty("name") String name,
            @JsonProperty("fixVersions") List<String> fixVersions,
            @JsonProperty("issues") List<IssueBean> issues
    ) {
        this.name = name;
        this.fixVersions = fixVersions != null ? fixVersions : Collections.emptyList();
        this.issues = issues != null ? issues : Collections.emptyList();
    }

    public String getName() {
        return name;
    }

    public List<String> getFixVersions() {
        return fixVersions;
    }

    public List<IssueBean> getIssues() {
        return issues;
    }
}
