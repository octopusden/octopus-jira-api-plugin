package org.octopusden.octopus.jira.api.config;

import java.util.Collections;
import java.util.List;

public enum ApiSetting {

    IPS_REPORTS_PROJECT("ips-reports.project", "IPS Reports Project", SettingType.TEXT, Collections.emptyList()),
    SERVICE_USER("service-user.username", "Service User", SettingType.TEXT, Collections.emptyList()),
    COMPONENTS_REGISTRY_URL("components-registry-service.url", "Components Registry Service URL", SettingType.TEXT, Collections.emptyList());

    private final String key;
    private final String displayName;
    private final SettingType type;
    private final List<String> values;

    ApiSetting(String key, String displayName, SettingType type, List<String> values) {
        this.key = key;
        this.displayName = displayName;
        this.type = type;
        this.values = values;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SettingType getType() {
        return type;
    }

    public List<String> getValues() {
        return values;
    }

    public enum SettingType {
        TEXT, PASSWORD, SELECT
    }
}
