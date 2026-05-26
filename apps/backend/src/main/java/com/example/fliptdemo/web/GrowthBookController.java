package com.example.fliptdemo.web;

import com.example.fliptdemo.featureflag.GrowthBookFlag;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GrowthBook comparison endpoints — a 1:1 mirror of {@link DemoController}
 * (which gates on Flipt), but gated on GrowthBook via {@link GrowthBookFlag}.
 * Call the matching pairs side by side to compare the two providers:
 *
 * <ul>
 *   <li>{@code /api/growthbook/hello} — gated by the {@code demo-api} feature
 *       (vs {@code /api/demo/hello} on Flipt)</li>
 *   <li>{@code /api/growthbook/beta}  — gated by the {@code beta-feature} feature
 *       (vs {@code /api/demo/beta} on Flipt)</li>
 *   <li>{@code /api/growthbook/health} — not gated; always available (control case)</li>
 * </ul>
 *
 * <p>Flag keys deliberately match the Flipt side. Create them (and an SDK
 * Connection) in the GrowthBook UI first — see README.
 */
@RestController
@RequestMapping("/api/growthbook")
public class GrowthBookController {

    // Reuses the active environment selected by the Spring profile, so both the
    // Flipt and GrowthBook responses report the same environment.
    @Value("${flipt.environment:dev}")
    private String environment;

    @GetMapping("/hello")
    @GrowthBookFlag("demo-api")
    public Map<String, String> hello() {
        return Map.of(
                "message", "Hello from the demo API!",
                "environment", environment,
                "provider", "growthbook");
    }

    @GetMapping("/beta")
    @GrowthBookFlag("beta-feature")
    public Map<String, String> beta() {
        return Map.of(
                "message", "You are seeing the beta feature.",
                "environment", environment,
                "provider", "growthbook");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "environment", environment,
                "provider", "growthbook");
    }
}
