package dev.legatus.server.web.rest;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller forwarding standard {@code /actuator/prometheus} scrape requests to {@code /management/prometheus}.
 * Complies with {@code server-spec.md} Section 6.1 (Issue #212).
 */
@RestController
@ConditionalOnProperty(name = "management.endpoints.prometheus", havingValue = "true", matchIfMissing = false)
public class PrometheusEndpointForwarder {

    @GetMapping("/actuator/prometheus")
    public void forwardPrometheus(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.getRequestDispatcher("/management/prometheus").forward(request, response);
    }
}
