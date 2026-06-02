package org.octopusden.octopus.jira.api.exception;

public class BadRequestException extends JiraApiException {
    public BadRequestException(String message) {
        super(message, "API-40000");
    }
}
