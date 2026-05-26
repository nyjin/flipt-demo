package com.example.fliptdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code growthbook.*} configuration used by the GrowthBook native
 * Java SDK (the Flipt comparison side of this demo).
 *
 * <p>Unlike Flipt, GrowthBook has no declarative/git-native config: flags and the
 * SDK client key are created at runtime in the GrowthBook UI (stored in MongoDB).
 * The client key is therefore supplied out-of-band via {@code GROWTHBOOK_CLIENT_KEY}
 * (see README). When it is blank the SDK is left uninitialised and evaluations
 * fall back to off, so the service still boots.
 *
 * @param apiHost   base URL of the GrowthBook API (e.g. http://localhost:3100, or
 *                  http://growthbook:3100 under docker-compose)
 * @param clientKey SDK Connection client key issued by the GrowthBook UI; blank
 *                  until GrowthBook has been set up
 */
@ConfigurationProperties(prefix = "growthbook")
public record GrowthBookProperties(String apiHost, String clientKey) {

    public GrowthBookProperties {
        if (apiHost == null || apiHost.isBlank()) {
            apiHost = "http://localhost:3100";
        }
        if (clientKey == null) {
            clientKey = "";
        }
    }

    /** True once a non-blank SDK client key has been configured. */
    public boolean isConfigured() {
        return !clientKey.isBlank();
    }
}
