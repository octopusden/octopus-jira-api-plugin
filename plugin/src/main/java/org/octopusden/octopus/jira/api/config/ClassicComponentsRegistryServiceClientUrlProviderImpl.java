package org.octopusden.octopus.jira.api.config;

import org.jetbrains.annotations.NotNull;
import org.octopusden.octopus.components.registry.client.impl.ClassicComponentsRegistryServiceClientUrlProvider;

public class ClassicComponentsRegistryServiceClientUrlProviderImpl
        implements ClassicComponentsRegistryServiceClientUrlProvider {

    private final ApiSettingsProvider settingsProvider;

    public ClassicComponentsRegistryServiceClientUrlProviderImpl(ApiSettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider;
    }

    @NotNull
    @Override
    public String getApiUrl() {
        String url = settingsProvider.getString(ApiSetting.COMPONENTS_REGISTRY_URL);
        if (url == null || url.isEmpty()) {
            throw new IllegalStateException(
                    "Components Registry URL is not configured. Please set '" +
                            ApiSetting.COMPONENTS_REGISTRY_URL.getKey() +
                            "' in plugin settings."
            );
        }
        return url;
    }
}