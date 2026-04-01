package net.jrodolfo.aws.bedrock.chat.journal.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    @Operation(summary = "Health check", description = "Returns a simple OK status for local verification.")
    public Map<String, String> health() {
        return Map.of("status", "OK");
    }
}
