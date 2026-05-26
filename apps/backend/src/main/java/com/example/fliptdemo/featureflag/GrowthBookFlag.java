package com.example.fliptdemo.featureflag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates a controller method behind a GrowthBook boolean feature flag — the
 * GrowthBook counterpart to {@link FeatureFlag} (which gates on Flipt).
 *
 * <p>When applied to a handler method, {@link GrowthBookFlagAspect} evaluates the
 * flag via the GrowthBook SDK before the method runs. If the flag is disabled the
 * method is not invoked and the request is rejected — by default with HTTP 404
 * (same {@link FeatureDisabledException} mapping as the Flipt side).
 *
 * <pre>{@code
 * @GetMapping("/api/growthbook/hello")
 * @GrowthBookFlag("demo-api")
 * public String hello() { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GrowthBookFlag {

    /** The GrowthBook feature key to evaluate. */
    String value();

    /**
     * Value used when the flag cannot be evaluated (e.g. GrowthBook unreachable,
     * unconfigured, or the feature does not exist). Defaults to {@code false}
     * (fail closed).
     */
    boolean fallback() default false;
}
