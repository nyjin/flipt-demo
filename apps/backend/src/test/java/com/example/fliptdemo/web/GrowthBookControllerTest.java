package com.example.fliptdemo.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.multiusermode.GrowthBookClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the GrowthBook scenario endpoints map the SDK's evaluation result (and
 * the per-request headers) into the response. The {@link GrowthBookClient} is mocked
 * so no GrowthBook server is required.
 */
@SpringBootTest(properties = "flipt.mode=ofrep")
@AutoConfigureMockMvc
class GrowthBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GrowthBookClient growthBookClient;

    @Test
    void variantReturnsResolvedValue() throws Exception {
        FeatureResult<String> result = FeatureResult.<String>builder()
                .value("treatment").source(FeatureResultSource.EXPERIMENT).build();
        when(growthBookClient.evalFeature(eq("ui-theme"), eq(String.class), any())).thenReturn(result);

        mockMvc.perform(get("/api/growthbook/variant").header("X-User-Id", "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value("ui-theme"))
                .andExpect(jsonPath("$.variant").value("treatment"))
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.provider").value("growthbook"));
    }

    @Test
    void targetedReflectsEvaluationResult() throws Exception {
        FeatureResult<Boolean> result = FeatureResult.<Boolean>builder()
                .value(true).source(FeatureResultSource.FORCE).build();
        when(growthBookClient.evalFeature(eq("premium-feature"), eq(Boolean.class), any())).thenReturn(result);

        mockMvc.perform(get("/api/growthbook/targeted").header("X-User-Tier", "premium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.attributes.tier").value("premium"));
    }
}
