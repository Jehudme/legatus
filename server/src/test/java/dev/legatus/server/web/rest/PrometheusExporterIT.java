package dev.legatus.server.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.legatus.server.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for Prometheus actuator exporter (server-spec.md Section 6.1 and Issue #212).
 */
@AutoConfigureMockMvc
@IntegrationTest
@TestPropertySource(properties = {
    "management.endpoints.prometheus=true",
    "management.endpoints.web.exposure.include=*",
    "management.endpoints.web.base-path=/management",
    "management.endpoint.prometheus.access=unrestricted",
    "management.prometheus.metrics.export.enabled=true"
})
class PrometheusExporterIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should scrape Prometheus metrics endpoint anonymously when enabled")
    void testScrapePrometheusMetricsEndpoint() throws Exception {
        String response = mockMvc.perform(get("/management/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/plain"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(response).contains("jvm_");
    }

    @Test
    @DisplayName("Should forward /actuator/prometheus to /management/prometheus when enabled")
    void testForwardActuatorPrometheus() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk());
    }
}
