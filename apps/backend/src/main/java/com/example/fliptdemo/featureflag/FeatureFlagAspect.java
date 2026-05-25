package com.example.fliptdemo.featureflag;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.MutableContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Evaluates the {@link FeatureFlag} on a method before it runs. If the flag is
 * enabled the method proceeds; otherwise a {@link FeatureDisabledException} is
 * thrown and the method is never invoked.
 */
@Aspect
@Component
public class FeatureFlagAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagAspect.class);

    private final Client featureClient;

    public FeatureFlagAspect(Client featureClient) {
        this.featureClient = featureClient;
    }

    @Around("@annotation(featureFlag)")
    public Object evaluate(ProceedingJoinPoint joinPoint, FeatureFlag featureFlag) throws Throwable {
        String flagKey = featureFlag.value();
        boolean enabled = featureClient.getBooleanValue(
                flagKey, featureFlag.fallback(), new MutableContext());

        if (!enabled) {
            log.debug("Feature '{}' is disabled — blocking {}", flagKey, joinPoint.getSignature());
            throw new FeatureDisabledException(flagKey);
        }
        return joinPoint.proceed();
    }
}
