package com.example.fliptdemo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import io.flipt.client.FliptClient;
import io.flipt.client.models.BooleanEvaluationResponse;
import io.flipt.client.models.VariantEvaluationResponse;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the OpenFeature adapter around Flipt's in-memory SDK: it should
 * surface the SDK's result, and fail safe (return the caller's default) when the
 * client is unavailable or evaluation throws.
 */
class FliptInMemoryProviderTest {

    @Test
    void returnsEnabledFromFliptClient() {
        FliptClient client = mock(FliptClient.class);
        BooleanEvaluationResponse resp = BooleanEvaluationResponse.builder()
                .enabled(true).flagKey("demo-api").reason("MATCH").build();
        when(client.evaluateBoolean(eq("demo-api"), any(), any())).thenReturn(resp);

        ProviderEvaluation<Boolean> eval = new FliptInMemoryProvider(client)
                .getBooleanEvaluation("demo-api", false, new ImmutableContext());

        assertThat(eval.getValue()).isTrue();
    }

    @Test
    void fallsBackToDefaultWhenClientUnavailable() {
        ProviderEvaluation<Boolean> eval = new FliptInMemoryProvider(null)
                .getBooleanEvaluation("demo-api", true, new ImmutableContext());

        assertThat(eval.getValue()).isTrue();
        assertThat(eval.getErrorCode()).isEqualTo(ErrorCode.PROVIDER_NOT_READY);
    }

    @Test
    void fallsBackToDefaultWhenEvaluationThrows() {
        FliptClient client = mock(FliptClient.class);
        when(client.evaluateBoolean(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        ProviderEvaluation<Boolean> eval = new FliptInMemoryProvider(client)
                .getBooleanEvaluation("demo-api", false, new ImmutableContext());

        assertThat(eval.getValue()).isFalse();
        assertThat(eval.getErrorCode()).isEqualTo(ErrorCode.GENERAL);
    }

    @Test
    void returnsVariantKeyFromFliptClient() {
        FliptClient client = mock(FliptClient.class);
        VariantEvaluationResponse resp = VariantEvaluationResponse.builder()
                .variantKey("treatment").reason("MATCH_EVALUATION_REASON").match(true).build();
        when(client.evaluateVariant(eq("ui-theme"), any(), any())).thenReturn(resp);

        ProviderEvaluation<String> eval = new FliptInMemoryProvider(client)
                .getStringEvaluation("ui-theme", "control", new ImmutableContext());

        assertThat(eval.getValue()).isEqualTo("treatment");
        assertThat(eval.getVariant()).isEqualTo("treatment");
    }

    @Test
    void variantFallsBackWhenClientUnavailable() {
        ProviderEvaluation<String> eval = new FliptInMemoryProvider(null)
                .getStringEvaluation("ui-theme", "control", new ImmutableContext());

        assertThat(eval.getValue()).isEqualTo("control");
        assertThat(eval.getErrorCode()).isEqualTo(ErrorCode.PROVIDER_NOT_READY);
    }
}
