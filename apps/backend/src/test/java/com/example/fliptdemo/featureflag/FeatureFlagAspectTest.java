package com.example.fliptdemo.featureflag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.openfeature.sdk.Client;
import java.lang.annotation.Annotation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

class FeatureFlagAspectTest {

    private final Client client = mock(Client.class);
    private final FeatureFlagAspect aspect = new FeatureFlagAspect(client);

    @Test
    void proceedsWhenFlagEnabled() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("result");
        when(client.getBooleanValue(eq("demo-api"), anyBoolean(), any())).thenReturn(true);

        Object result = aspect.evaluate(joinPoint, featureFlag("demo-api", false));

        assertThat(result).isEqualTo("result");
        verify(joinPoint).proceed();
    }

    @Test
    void throwsAndBlocksWhenFlagDisabled() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(client.getBooleanValue(eq("demo-api"), anyBoolean(), any())).thenReturn(false);

        assertThatThrownBy(() -> aspect.evaluate(joinPoint, featureFlag("demo-api", false)))
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining("demo-api");

        verify(joinPoint, never()).proceed();
    }

    private static FeatureFlag featureFlag(String value, boolean fallback) {
        return new FeatureFlag() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return FeatureFlag.class;
            }

            @Override
            public String value() {
                return value;
            }

            @Override
            public boolean fallback() {
                return fallback;
            }
        };
    }
}
