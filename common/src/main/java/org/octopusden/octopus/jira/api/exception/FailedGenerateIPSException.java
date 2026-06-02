package org.octopusden.octopus.jira.api.exception;

public class FailedGenerateIPSException extends JiraApiException {
    public FailedGenerateIPSException(String message) {
        super(message, "API-40001");
    }
}
