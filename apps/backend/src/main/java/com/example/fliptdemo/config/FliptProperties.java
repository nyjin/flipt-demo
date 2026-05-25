package com.example.fliptdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code flipt.*} configuration. Each Spring profile
 * (dev/preview/prod) points at the same Flipt server but selects a different
 * Flipt v2 environment via {@link #environment()}.
 *
 * @param url         base URL of the Flipt server (e.g. http://localhost:8080)
 * @param environment Flipt v2 environment key, sent as the X-Flipt-Environment header
 * @param namespace   Flipt namespace key, sent as the X-Flipt-Namespace header
 */
@ConfigurationProperties(prefix = "flipt")
public record FliptProperties(String url, String environment, String namespace) {

    public FliptProperties {
        if (url == null || url.isBlank()) {
            url = "http://localhost:8080";
        }
        if (environment == null || environment.isBlank()) {
            environment = "dev";
        }
        if (namespace == null || namespace.isBlank()) {
            namespace = "default";
        }
    }
}
