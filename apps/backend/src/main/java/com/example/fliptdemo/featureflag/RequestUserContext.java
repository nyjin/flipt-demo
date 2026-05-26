package com.example.fliptdemo.featureflag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.MutableContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Per-request user context for flag evaluation, extracted from HTTP headers:
 * {@code X-User-Id} (default {@code "anonymous"}), {@code X-User-Tier},
 * {@code X-User-Country}.
 *
 * <p>This is what makes the targeting/segment and percentage-rollout scenarios
 * work: the {@code userId} is the entity used for consistent rollout bucketing,
 * and the attributes drive segment matching. The same context maps to both
 * providers — Flipt via an OpenFeature {@link MutableContext} (targetingKey +
 * attributes) and GrowthBook via an attributes JSON.
 */
public record RequestUserContext(String userId, Map<String, String> attributes) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static RequestUserContext from(HttpServletRequest request) {
        String userId = headerOrDefault(request, "X-User-Id", "anonymous");
        Map<String, String> attrs = new LinkedHashMap<>();
        putIfPresent(attrs, request, "X-User-Tier", "tier");
        putIfPresent(attrs, request, "X-User-Country", "country");
        return new RequestUserContext(userId, attrs);
    }

    /** Reads the current request via Spring's holder — for use inside aspects (no method args). */
    public static RequestUserContext current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            return from(sra.getRequest());
        }
        return new RequestUserContext("anonymous", Map.of());
    }

    /** OpenFeature context for Flipt: targetingKey = userId (rollout bucketing) + attributes (segments). */
    public MutableContext toEvaluationContext() {
        MutableContext ctx = new MutableContext(userId);
        attributes.forEach(ctx::add);
        return ctx;
    }

    /** GrowthBook attributes JSON: {@code {"id": userId, ...attributes}} ("id" is GrowthBook's default hash attribute). */
    public String toAttributesJson() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", userId);
        map.putAll(attributes);
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{\"id\":\"" + userId + "\"}";
        }
    }

    private static String headerOrDefault(HttpServletRequest request, String header, String fallback) {
        String value = request.getHeader(header);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static void putIfPresent(Map<String, String> map, HttpServletRequest request, String header, String key) {
        String value = request.getHeader(header);
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
