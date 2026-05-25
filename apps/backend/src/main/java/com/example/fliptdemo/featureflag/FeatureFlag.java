package com.example.fliptdemo.featureflag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates a controller method behind a Flipt boolean feature flag.
 *
 * <p>When applied to a handler method, {@link FeatureFlagAspect} evaluates the
 * flag (in the environment selected by the active Spring profile) before the
 * method runs. If the flag is disabled the method is not invoked and the
 * request is rejected — by default with HTTP 404.
 *
 * <pre>{@code
 * @GetMapping("/api/demo/hello")
 * @FeatureFlag("demo-api")
 * public String hello() { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureFlag {

    /** The Flipt flag key to evaluate. */
    String value();

    /**
     * Value used when the flag cannot be evaluated (e.g. Flipt unreachable or
     * the flag does not exist). Defaults to {@code false} (fail closed).
     */
    boolean fallback() default false;
}
