package org.octopusden.octopus.jira.api.client.impl;

public interface JiraApiClientParametersProvider {
    String getApiUrl();

    String getBearerToken();

    String getBasicCredentials();
}
