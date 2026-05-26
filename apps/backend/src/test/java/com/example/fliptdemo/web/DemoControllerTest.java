package com.example.fliptdemo.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.anyString;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

/**
 * End-to-end test of the @FeatureFlag flow: the aspect gates the controller
 * method and the global handler maps a disabled feature to HTTP 404. The
 * OpenFeature {@link Client} is mocked so no Flipt server is required.
 */
// Force the OFREP provider for the test context so loading it doesn't spin up the
// in-memory SDK's native engine or try to fetch a snapshot over the network. The
// OpenFeature Client is mocked anyway, so this doesn't affect what's under test.
@SpringBootTest(properties = "flipt.mode=ofrep")
@AutoConfigureMockMvc
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Client featureClient;

    @Test
    void helloReturns200WhenFlagEnabled() throws Exception {
        when(featureClient.getBooleanValue(eq("demo-api"), anyBoolean(), any())).thenReturn(true);

        mockMvc.perform(get("/api/demo/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void helloReturns404WhenFlagDisabled() throws Exception {
        when(featureClient.getBooleanValue(eq("demo-api"), anyBoolean(), any())).thenReturn(false);

        mockMvc.perform(get("/api/demo/hello"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.flag").value("demo-api"));
    }

    @Test
    void healthIsAlwaysAvailable() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void variantReturnsResolvedVariant() throws Exception {
        FlagEvaluationDetails<String> details = new FlagEvaluationDetails<>();
        details.setValue("treatment");
        details.setReason("TARGETING_MATCH");
        when(featureClient.getStringDetails(eq("ui-theme"), anyString(), any())).thenReturn(details);

        mockMvc.perform(get("/api/demo/variant").header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value("ui-theme"))
                .andExpect(jsonPath("$.variant").value("treatment"))
                .andExpect(jsonPath("$.userId").value("u1"));
    }

    @Test
    void targetedReflectsEvaluationResult() throws Exception {
        FlagEvaluationDetails<Boolean> details = new FlagEvaluationDetails<>();
        details.setValue(true);
        details.setReason("TARGETING_MATCH");
        when(featureClient.getBooleanDetails(eq("premium-feature"), any(), any())).thenReturn(details);

        mockMvc.perform(get("/api/demo/targeted").header("X-User-Tier", "premium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.attributes.tier").value("premium"));
    }
}
