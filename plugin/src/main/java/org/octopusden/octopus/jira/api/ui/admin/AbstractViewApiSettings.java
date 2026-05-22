package org.octopusden.octopus.jira.api.ui.admin;

import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.jira.web.ExecutingHttpRequest;
import org.octopusden.octopus.jira.api.config.ApiSetting;
import org.octopusden.octopus.jira.api.config.ApiSettingsProvider;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public abstract class AbstractViewApiSettings extends JiraWebActionSupport {

    protected final String tab;
    protected final String title;
    protected final ApiSettingsProvider settingsProvider;

    protected AbstractViewApiSettings(String tab, String title, ApiSettingsProvider settingsProvider) {
        this.tab = tab;
        this.title = title;
        this.settingsProvider = settingsProvider;
    }

    public abstract List<ApiSetting> getAvailableSettings();

    public String getTab() {
        return tab;
    }

    public String getTitle() {
        return title;
    }

    public ApiSettingsProvider getSettingsProvider() {
        return settingsProvider;
    }

    @Override
    public String doDefault() {
        final HttpServletRequest request = ExecutingHttpRequest.get();
        for (ApiSetting setting : getAvailableSettings()) {
            final String value = request.getParameter(setting.getKey());
            if (value != null) {
                settingsProvider.set(setting, value);
            }
        }
        return SUCCESS;
    }
}
