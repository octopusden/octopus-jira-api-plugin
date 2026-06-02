package org.octopusden.octopus.jira.api.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public abstract class JiraApiException extends RuntimeException {

    public static final Map<String, Function<String, JiraApiException>> CODE_EXCEPTION_MAP;

    static {
        Map<String, Function<String, JiraApiException>> map = new HashMap<>();
        map.put("API-40000", BadRequestException::new);
        map.put("API-40001", FailedGenerateIPSException::new);
        CODE_EXCEPTION_MAP = Collections.unmodifiableMap(map);
    }

    private final String code;

    protected JiraApiException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
