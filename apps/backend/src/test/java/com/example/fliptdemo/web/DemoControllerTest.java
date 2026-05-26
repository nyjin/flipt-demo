package com.example.fliptdemo.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.openfeature.sdk.Client;
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
}
