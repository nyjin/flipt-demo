package com.example.fliptdemo.featureflag;

/**
 * Thrown by {@link FeatureFlagAspect} when a {@link FeatureFlag}-gated method is
 * invoked while its flag is disabled. Mapped to an HTTP response by the global
 * exception handler.
 */
public class FeatureDisabledException extends RuntimeException {

    private final String flagKey;

    public FeatureDisabledException(String flagKey) {
        super("Feature '" + flagKey + "' is disabled");
        this.flagKey = flagKey;
    }

    public String getFlagKey() {
        return flagKey;
    }
}
