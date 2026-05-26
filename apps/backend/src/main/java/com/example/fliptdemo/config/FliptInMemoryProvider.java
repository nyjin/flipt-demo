package com.example.fliptdemo.config;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import io.flipt.client.FliptClient;
import io.flipt.client.models.BooleanEvaluationResponse;
import io.flipt.client.models.VariantEvaluationResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts Flipt's official client-side SDK ({@link FliptClient}) to the
 * OpenFeature {@link FeatureProvider} interface, so the demo can evaluate Flipt
 * flags <em>in-memory</em> (in-process, against a snapshot fetched from the Flipt
 * server) while keeping the same OpenFeature {@code Client} / {@code @FeatureFlag}
 * abstraction the OFREP path uses.
 *
 * <p>This is the in-memory counterpart to the OFREP provider; which one is active
 * is chosen by {@code flipt.mode} in {@link OpenFeatureConfig}. Boolean flags
 * (on/off, targeting, percentage rollout) go through {@link #getBooleanEvaluation};
 * multivariate flags go through {@link #getStringEvaluation} (returns the variant
 * key). Both pass the OpenFeature context's targetingKey as the Flipt entity id
 * (rollout bucketing) and its attributes as the Flipt evaluation context (segment
 * matching). Integer/Double/Object types are unused by the demo and degrade to the default.
 *
 * <p>The provider is resilient: if the client never initialised (e.g. Flipt was
 * unreachable at startup) or an evaluation throws, it returns the caller's default
 * rather than propagating the error — matching the fail-safe behaviour of the OFREP
 * and GrowthBook providers.
 */
public class FliptInMemoryProvider implements FeatureProvider {

    private static final Logger log = LoggerFactory.getLogger(FliptInMemoryProvider.class);
    private static final String NAME = "flipt-in-memory";

    /** Stable entity id for the demo; boolean flags without targeting rules ignore it. */
    private static final String DEFAULT_ENTITY_ID = "demo-user";

    /** The Flipt client-side SDK, or {@code null} if it failed to initialise. */
    private final FliptClient client;

    public FliptInMemoryProvider(FliptClient client) {
        this.client = client;
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(
            String key, Boolean defaultValue, EvaluationContext ctx) {
        if (client == null) {
            return fallback(defaultValue, "Flipt client-side SDK unavailable", ErrorCode.PROVIDER_NOT_READY);
        }
        try {
            BooleanEvaluationResponse resp = client.evaluateBoolean(key, entityId(ctx), attributes(ctx));
            return ProviderEvaluation.<Boolean>builder()
                    .value(resp.isEnabled())
                    .reason(resp.getReason())
                    .build();
        } catch (RuntimeException e) {
            log.warn("Flipt in-memory evaluation of '{}' failed — using fallback", key, e);
            return fallback(defaultValue, "evaluation error", ErrorCode.GENERAL);
        }
    }

    private static String entityId(EvaluationContext ctx) {
        if (ctx != null && ctx.getTargetingKey() != null && !ctx.getTargetingKey().isBlank()) {
            return ctx.getTargetingKey();
        }
        return DEFAULT_ENTITY_ID;
    }

    /** OpenFeature attributes → Flipt string context (used for segment matching). */
    private static Map<String, String> attributes(EvaluationContext ctx) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (ctx != null) {
            ctx.asMap().forEach((k, v) -> {
                if (v != null && !"targetingKey".equals(k)) {
                    attrs.put(k, v.isString() ? v.asString() : String.valueOf(v.asObject()));
                }
            });
        }
        return attrs;
    }

    private static ProviderEvaluation<Boolean> fallback(Boolean value, String reason, ErrorCode code) {
        return ProviderEvaluation.<Boolean>builder().value(value).reason(reason).errorCode(code).build();
    }

    /** Multivariate flags: returns the Flipt variant key (e.g. control/treatment). */
    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String def, EvaluationContext ctx) {
        if (client == null) {
            return ProviderEvaluation.<String>builder()
                    .value(def).reason("Flipt client-side SDK unavailable").errorCode(ErrorCode.PROVIDER_NOT_READY).build();
        }
        try {
            VariantEvaluationResponse resp = client.evaluateVariant(key, entityId(ctx), attributes(ctx));
            String variant = resp.getVariantKey();
            return ProviderEvaluation.<String>builder()
                    .value(variant != null && !variant.isBlank() ? variant : def)
                    .variant(variant)
                    .reason(resp.getReason())
                    .build();
        } catch (RuntimeException e) {
            log.warn("Flipt in-memory variant evaluation of '{}' failed — using fallback", key, e);
            return ProviderEvaluation.<String>builder()
                    .value(def).reason("evaluation error").errorCode(ErrorCode.GENERAL).build();
        }
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer def, EvaluationContext ctx) {
        return unsupported(def);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double def, EvaluationContext ctx) {
        return unsupported(def);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value def, EvaluationContext ctx) {
        return unsupported(def);
    }

    private static <T> ProviderEvaluation<T> unsupported(T def) {
        return ProviderEvaluation.<T>builder().value(def).reason("non-boolean flags are not used in this demo").build();
    }

    /** Releases the SDK's polling/streaming threads. Called via OpenFeatureAPI.shutdown(). */
    @Override
    public void shutdown() {
        if (client != null) {
            client.close();
        }
    }
}
