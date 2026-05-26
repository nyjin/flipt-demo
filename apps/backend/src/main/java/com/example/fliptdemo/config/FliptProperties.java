package com.example.fliptdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code flipt.*} configuration. Each Spring profile
 * (dev/staging/prod) points at the same Flipt server but selects a different
 * Flipt v2 environment via {@link #environment()}.
 *
 * <p>The demo can evaluate Flipt in two ways, chosen by {@link #mode()}:
 * <ul>
 *   <li>{@code in-memory} (default) — the Flipt client-side SDK fetches a
 *       snapshot from the server and evaluates flags in-process; {@link #syncMode()}
 *       and {@link #updateIntervalSeconds()} control how that snapshot refreshes.</li>
 *   <li>{@code ofrep} — the OpenFeature OFREP provider calls the Flipt server to
 *       evaluate each flag (server-side).</li>
 * </ul>
 * Both modes still need {@link #url()}: in-memory uses it to fetch the snapshot.
 *
 * @param url                   base URL of the Flipt server (e.g. http://localhost:8080)
 * @param environment           Flipt v2 environment key (X-Flipt-Environment)
 * @param namespace             Flipt namespace key (X-Flipt-Namespace)
 * @param mode                  {@code in-memory} (default) or {@code ofrep}
 * @param syncMode              in-memory refresh: {@code polling} (default) or {@code streaming}
 * @param updateIntervalSeconds polling interval in seconds (default 30; ignored when streaming)
 */
@ConfigurationProperties(prefix = "flipt")
public record FliptProperties(
        String url,
        String environment,
        String namespace,
        String mode,
        String syncMode,
        int updateIntervalSeconds) {

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
        if (mode == null || mode.isBlank()) {
            mode = "in-memory";
        }
        if (syncMode == null || syncMode.isBlank()) {
            syncMode = "polling";
        }
        if (updateIntervalSeconds <= 0) {
            updateIntervalSeconds = 30;
        }
    }

    /** True when flags should be evaluated server-side via the OFREP provider. */
    public boolean isOfrepMode() {
        return "ofrep".equalsIgnoreCase(mode);
    }

    /** True when the in-memory SDK should stream updates (SSE) instead of polling. */
    public boolean isStreaming() {
        return "streaming".equalsIgnoreCase(syncMode);
    }
}
