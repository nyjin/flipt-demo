package com.example.fliptdemo.web;

import com.example.fliptdemo.featureflag.GrowthBookFlag;
import com.example.fliptdemo.featureflag.RequestUserContext;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GrowthBook comparison endpoints — a 1:1 mirror of {@link DemoController}.
 *
 * <p>On/off gating via {@link GrowthBookFlag} (flag off → HTTP 404):
 * {@code /hello} ({@code demo-api}), {@code /beta} ({@code beta-feature}),
 * {@code /health} (not gated).
 *
 * <p>Scenario endpoints returning the evaluation result (read the same
 * {@code X-User-*} headers as the Flipt side): {@code /variant} ({@code ui-theme}),
 * {@code /targeted} ({@code premium-feature}), {@code /rollout} ({@code gradual-rollout}).
 *
 * <p>Flag keys deliberately match the Flipt side. The features, targeting rules and
 * rollout %s must be created in the GrowthBook UI (MongoDB) — see README. Until the
 * SDK client key is configured, these evaluate to the default (off/control).
 */
@RestController
@RequestMapping("/api/growthbook")
public class GrowthBookController {

    @Value("${flipt.environment:dev}")
    private String environment;

    private final GrowthBookClient growthBookClient;

    public GrowthBookController(GrowthBookClient growthBookClient) {
        this.growthBookClient = growthBookClient;
    }

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

    // --- 시나리오 엔드포인트 (평가 결과 반환) ---------------------------------

    @GetMapping("/variant")
    public Map<String, Object> variant(HttpServletRequest request) {
        RequestUserContext uc = RequestUserContext.from(request);
        FeatureResult<String> r = growthBookClient.evalFeature("ui-theme", String.class, userContext(uc));
        Map<String, Object> result = base("ui-theme", uc);
        Object value = r.getValue();
        result.put("variant", value != null ? String.valueOf(value) : "control");
        result.put("reason", String.valueOf(r.getSource()));
        return result;
    }

    @GetMapping("/targeted")
    public Map<String, Object> targeted(HttpServletRequest request) {
        RequestUserContext uc = RequestUserContext.from(request);
        FeatureResult<Boolean> r = growthBookClient.evalFeature("premium-feature", Boolean.class, userContext(uc));
        Map<String, Object> result = base("premium-feature", uc);
        result.put("enabled", Boolean.TRUE.equals(r.isOn()));
        result.put("reason", String.valueOf(r.getSource()));
        return result;
    }

    @GetMapping("/rollout")
    public Map<String, Object> rollout(HttpServletRequest request) {
        RequestUserContext uc = RequestUserContext.from(request);
        FeatureResult<Boolean> r = growthBookClient.evalFeature("gradual-rollout", Boolean.class, userContext(uc));
        Map<String, Object> result = base("gradual-rollout", uc);
        result.put("enabled", Boolean.TRUE.equals(r.isOn()));
        result.put("reason", String.valueOf(r.getSource()));
        return result;
    }

    private static UserContext userContext(RequestUserContext uc) {
        return UserContext.builder().attributesJson(uc.toAttributesJson()).build();
    }

    private Map<String, Object> base(String flag, RequestUserContext uc) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", "growthbook");
        result.put("flag", flag);
        result.put("environment", environment);
        result.put("userId", uc.userId());
        result.put("attributes", uc.attributes());
        return result;
    }
}
