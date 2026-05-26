package com.example.fliptdemo.config;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.openfeature.contrib.providers.ofrep.OfrepProvider;
import dev.openfeature.contrib.providers.ofrep.OfrepProviderOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.flipt.client.FliptClient;
import io.flipt.client.models.ErrorStrategy;
import io.flipt.client.models.FetchMode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the OpenFeature SDK to evaluate Flipt v2 flags. The {@code @FeatureFlag}
 * aspect always talks to the same OpenFeature {@link Client}; this config picks the
 * backing provider from {@code flipt.mode}:
 *
 * <ul>
 *   <li><b>in-memory</b> (default) — wraps Flipt's client-side SDK in
 *       {@link FliptInMemoryProvider}. The SDK fetches a snapshot from the Flipt
 *       server once and evaluates flags in-process, refreshing by polling (default,
 *       {@code flipt.update-interval-seconds}) or streaming ({@code flipt.sync-mode=streaming}).</li>
 *   <li><b>ofrep</b> — the OpenFeature OFREP provider, which calls the Flipt server
 *       to evaluate each flag (server-side).</li>
 * </ul>
 *
 * <p>Either way the service boots even if Flipt is not yet reachable: the OFREP
 * provider fetches lazily per evaluation, and the in-memory client is built
 * defensively (init failures are logged and evaluations fall back to the supplied
 * default). The active provider is torn down via {@code OpenFeatureAPI.shutdown()}
 * (the bean's destroy method), which releases the in-memory SDK's background threads.
 */
@Configuration
public class OpenFeatureConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenFeatureConfig.class);

    @Bean(destroyMethod = "shutdown")
    public OpenFeatureAPI openFeatureAPI(FliptProperties props) {
        FeatureProvider provider = props.isOfrepMode() ? ofrepProvider(props) : inMemoryProvider(props);

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(provider);
        return api;
    }

    /** Server-side evaluation: the OFREP provider calls Flipt for every evaluation. */
    private FeatureProvider ofrepProvider(FliptProperties props) {
        ImmutableMap<String, ImmutableList<String>> headers = ImmutableMap.of(
                "X-Flipt-Environment", ImmutableList.of(props.environment()),
                "X-Flipt-Namespace", ImmutableList.of(props.namespace()));

        OfrepProviderOptions options = OfrepProviderOptions.builder()
                .baseUrl(props.url())
                .headers(headers)
                .build();

        log.info("Flipt evaluation mode=ofrep (server-side; url={}, environment={}, namespace={})",
                props.url(), props.environment(), props.namespace());
        return OfrepProvider.constructProvider(options);
    }

    /** Client-side evaluation: the Flipt SDK evaluates in-memory against a fetched snapshot. */
    private FeatureProvider inMemoryProvider(FliptProperties props) {
        return new FliptInMemoryProvider(buildFliptClient(props));
    }

    /** Builds the client-side SDK, or returns {@code null} if it cannot initialise (boot must not fail). */
    private FliptClient buildFliptClient(FliptProperties props) {
        try {
            var builder = FliptClient.builder()
                    .url(props.url())
                    .namespace(props.namespace())
                    .environment(props.environment())
                    // Return the snapshot's value on engine errors instead of throwing.
                    .errorStrategy(ErrorStrategy.FALLBACK);
            if (props.isStreaming()) {
                builder.fetchMode(FetchMode.STREAMING);
            } else {
                builder.updateInterval(Duration.ofSeconds(props.updateIntervalSeconds()));
            }
            FliptClient client = builder.build();
            log.info("Flipt evaluation mode=in-memory (client-side; url={}, environment={}, namespace={}, "
                    + "sync={}, interval={}s)",
                    props.url(), props.environment(), props.namespace(),
                    props.syncMode(), props.updateIntervalSeconds());
            return client;
        } catch (RuntimeException e) {
            log.warn("Flipt client-side SDK failed to initialise (url={}, environment={}) — in-memory "
                    + "evaluations will fall back to the annotation default. Is the Flipt server reachable?",
                    props.url(), props.environment(), e);
            return null;
        }
    }

    @Bean
    public Client featureClient(OpenFeatureAPI api) {
        return api.getClient();
    }
}
