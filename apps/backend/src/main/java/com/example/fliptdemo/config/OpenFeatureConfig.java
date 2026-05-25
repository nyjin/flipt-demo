package com.example.fliptdemo.config;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.openfeature.contrib.providers.ofrep.OfrepProvider;
import dev.openfeature.contrib.providers.ofrep.OfrepProviderOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the OpenFeature SDK to talk to Flipt v2 over the OFREP protocol.
 *
 * <p>The provider is configured with static {@code X-Flipt-Environment} and
 * {@code X-Flipt-Namespace} headers, so the active Spring profile alone
 * (dev/preview/prod) decides which Flipt environment this service evaluates
 * against. The OFREP provider performs no network call on startup — flags are
 * fetched lazily per evaluation — so the service boots even if Flipt is not yet
 * reachable (evaluations then fall back to the supplied default).
 */
@Configuration
public class OpenFeatureConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenFeatureConfig.class);

    @Bean
    public OpenFeatureAPI openFeatureAPI(FliptProperties props) {
        ImmutableMap<String, ImmutableList<String>> headers = ImmutableMap.of(
                "X-Flipt-Environment", ImmutableList.of(props.environment()),
                "X-Flipt-Namespace", ImmutableList.of(props.namespace()));

        OfrepProviderOptions options = OfrepProviderOptions.builder()
                .baseUrl(props.url())
                .headers(headers)
                .build();

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(OfrepProvider.constructProvider(options));

        log.info("OpenFeature OFREP provider initialised (url={}, environment={}, namespace={})",
                props.url(), props.environment(), props.namespace());
        return api;
    }

    @Bean
    public Client featureClient(OpenFeatureAPI api) {
        return api.getClient();
    }
}
