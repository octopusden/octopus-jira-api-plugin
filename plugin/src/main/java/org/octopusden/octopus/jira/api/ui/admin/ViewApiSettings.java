package org.octopusden.octopus.jira.api.ui.admin;

import org.octopusden.octopus.jira.api.config.ApiSetting;
import org.octopusden.octopus.jira.api.config.ApiSettingsProvider;

import java.util.Arrays;
import java.util.List;

public class ViewApiSettings extends AbstractViewApiSettings {

    private final List<ApiSetting> availableSettings = Arrays.asList(
            ApiSetting.IPS_REPORTS_PROJECT,
            ApiSetting.SERVICE_USER
    );

    public ViewApiSettings(ApiSettingsProvider settingsProvider) {
        super("api-settings", "Api Plugin Settings", settingsProvider);
    }

    @Override
    public List<ApiSetting> getAvailableSettings() {
        return availableSettings;
    }
}
