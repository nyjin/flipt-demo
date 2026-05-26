package com.example.fliptdemo.featureflag;

import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Evaluates the {@link GrowthBookFlag} on a method before it runs — the
 * GrowthBook counterpart to {@link FeatureFlagAspect}. If the flag is enabled the
 * method proceeds; otherwise a {@link FeatureDisabledException} is thrown (reused
 * from the Flipt side, so disabled GrowthBook endpoints also map to HTTP 404).
 */
@Aspect
@Component
public class GrowthBookFlagAspect {

    private static final Logger log = LoggerFactory.getLogger(GrowthBookFlagAspect.class);

    // Minimal demo user. GrowthBook needs attributes for targeting/experiments;
    // for the simple on/off flags in this demo a stable id is enough.
    private static final String DEMO_USER_ATTRIBUTES = "{\"id\": \"demo-user\"}";

    private final GrowthBookClient growthBookClient;

    public GrowthBookFlagAspect(GrowthBookClient growthBookClient) {
        this.growthBookClient = growthBookClient;
    }

    @Around("@annotation(growthBookFlag)")
    public Object evaluate(ProceedingJoinPoint joinPoint, GrowthBookFlag growthBookFlag) throws Throwable {
        String flagKey = growthBookFlag.value();
        UserContext user = UserContext.builder()
                .attributesJson(DEMO_USER_ATTRIBUTES)
                .build();

        Boolean enabled = growthBookClient.getFeatureValue(
                flagKey, growthBookFlag.fallback(), Boolean.class, user);

        if (!Boolean.TRUE.equals(enabled)) {
            log.debug("GrowthBook feature '{}' is disabled — blocking {}", flagKey, joinPoint.getSignature());
            throw new FeatureDisabledException(flagKey);
        }
        return joinPoint.proceed();
    }
}
