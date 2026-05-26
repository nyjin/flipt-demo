package com.example.fliptdemo.config;

import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the GrowthBook native Java SDK. This is the GrowthBook counterpart to
 * {@link OpenFeatureConfig} (which wires Flipt) so the two providers can be
 * compared from the same backend.
 *
 * <p>A single {@link GrowthBookClient} is shared across requests (multi-user
 * mode); per-request user attributes are supplied at evaluation time by
 * {@code GrowthBookFlagAspect}. The client is initialised (one synchronous fetch
 * plus background polling) only when a client key is configured; otherwise it is
 * left uninitialised and evaluations fall back to off. Initialisation failures
 * are logged but never block startup, mirroring the lazy/resilient behaviour of
 * the Flipt OFREP provider.
 */
@Configuration
public class GrowthBookConfig {

    private static final Logger log = LoggerFactory.getLogger(GrowthBookConfig.class);

    @Bean(destroyMethod = "shutdown")
    public GrowthBookClient growthBookClient(GrowthBookProperties props) {
        Options options = Options.builder()
                .apiHost(props.apiHost())
                .clientKey(props.clientKey())
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .swrTtlSeconds(60)
                .build();

        GrowthBookClient client = new GrowthBookClient(options);

        if (!props.isConfigured()) {
            log.warn("GrowthBook client key not configured (GROWTHBOOK_CLIENT_KEY is empty) — "
                    + "GrowthBook evaluations will fall back to off. Set up GrowthBook at its UI, "
                    + "create an SDK Connection, and put the key in .env to enable it.");
            return client;
        }

        try {
            boolean ready = client.initialize();
            if (ready) {
                log.info("GrowthBook SDK initialised (apiHost={})", props.apiHost());
            } else {
                log.warn("GrowthBook SDK failed to load features (apiHost={}) — check the client key "
                        + "and that GrowthBook is reachable. Evaluations will fall back to off.",
                        props.apiHost());
            }
        } catch (RuntimeException e) {
            log.warn("GrowthBook SDK initialisation threw — continuing with evaluations falling back "
                    + "to off (apiHost={})", props.apiHost(), e);
        }
        return client;
    }
}
