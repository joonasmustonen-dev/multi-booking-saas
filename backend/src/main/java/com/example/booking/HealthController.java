package com.example.booking;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final ApplicationAvailability availability;

    public HealthController(ApplicationAvailability availability) {
        this.availability = availability;
    }

    @GetMapping("/api/health")
    public ResponseEntity<String> health() {
        if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return ResponseEntity.status(503).body("STARTING");
        }

        return ResponseEntity.ok("OK");
    }
}
