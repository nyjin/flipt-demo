package com.example.fliptdemo.web;

import com.example.fliptdemo.featureflag.FeatureFlag;
import com.example.fliptdemo.featureflag.RequestUserContext;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Flipt demo endpoints.
 *
 * <p>On/off gating via {@link FeatureFlag} (flag off → HTTP 404):
 * <ul>
 *   <li>{@code /api/demo/hello} — gated by {@code demo-api}</li>
 *   <li>{@code /api/demo/beta}  — gated by {@code beta-feature} (dev only)</li>
 *   <li>{@code /api/health}     — not gated (control case)</li>
 * </ul>
 *
 * <p>Scenario endpoints that <b>return the evaluation result</b> (so targeting,
 * rollout and variants are observable in the response). All read the per-request
 * user from {@code X-User-Id}/{@code X-User-Tier}/{@code X-User-Country} headers:
 * <ul>
 *   <li>{@code /api/demo/variant}  — multivariate ({@code ui-theme} → control/treatment)</li>
 *   <li>{@code /api/demo/targeted} — segment targeting ({@code premium-feature}, on for tier=premium)</li>
 *   <li>{@code /api/demo/rollout}  — percentage rollout ({@code gradual-rollout}, bucketed by user id)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class DemoController {

    @Value("${flipt.environment:dev}")
    private String environment;

    private final Client featureClient;

    public DemoController(Client featureClient) {
        this.featureClient = featureClient;
    }

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

    // --- 시나리오 엔드포인트 (평가 결과 반환) ---------------------------------

    @GetMapping("/demo/variant")
    public Map<String, Object> variant(HttpServletRequest request) {
        RequestUserContext uc = RequestUserContext.from(request);
        FlagEvaluationDetails<String> d =
                featureClient.getStringDetails("ui-theme", "control", uc.toEvaluationContext());
        Map<String, Object> result = base("ui-theme", uc);
        result.put("variant", d.getValue());
        result.put("reason", d.getReason());
        return result;
    }

    @GetMapping("/demo/targeted")
    public Map<String, Object> targeted(HttpServletRequest request) {
        RequestUserContext uc = RequestUserContext.from(request);
        FlagEvaluationDetails<Boolean> d =
                featureClient.getBooleanDetails("premium-feature", false, uc.toEvaluationContext());
        Map<String, Object> result = base("premium-feature", uc);
        result.put("enabled", d.getValue());
        result.put("reason", d.getReason());
        return result;
    }

    @GetMapping("/demo/rollout")
    public Map<String, Object> rollout(HttpServletRequest request) {
        RequestUserContext uc = RequestUserContext.from(request);
        FlagEvaluationDetails<Boolean> d =
                featureClient.getBooleanDetails("gradual-rollout", false, uc.toEvaluationContext());
        Map<String, Object> result = base("gradual-rollout", uc);
        result.put("enabled", d.getValue());
        result.put("reason", d.getReason());
        return result;
    }

    private Map<String, Object> base(String flag, RequestUserContext uc) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", "flipt");
        result.put("flag", flag);
        result.put("environment", environment);
        result.put("userId", uc.userId());
        result.put("attributes", uc.attributes());
        return result;
    }
}
