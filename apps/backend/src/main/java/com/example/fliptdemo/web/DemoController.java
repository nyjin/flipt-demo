package com.example.fliptdemo.web;

import com.example.fliptdemo.featureflag.FeatureFlag;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demonstrates toggling individual API endpoints on/off via {@link FeatureFlag}.
 *
 * <ul>
 *   <li>{@code /api/demo/hello} — gated by the {@code demo-api} flag</li>
 *   <li>{@code /api/demo/beta}  — gated by the {@code beta-feature} flag
 *       (enabled in dev only, per the seeded flag state)</li>
 *   <li>{@code /api/health}     — not gated; always available (control case)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    @Value("${flipt.environment:dev}")
    private String environment;

    @GetMapping("/demo/hello")
    @FeatureFlag("demo-api")
    public Map<String, String> hello() {
        return Map.of(
                "message", "Hello from the demo API!",
                "environment", environment);
    }

    @GetMapping("/demo/beta")
    @FeatureFlag("beta-feature")
    public Map<String, String> beta() {
        return Map.of(
                "message", "You are seeing the beta feature.",
                "environment", environment);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "environment", environment);
    }
}
