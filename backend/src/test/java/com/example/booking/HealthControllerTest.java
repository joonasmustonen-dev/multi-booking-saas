package com.example.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;

class HealthControllerTest {

    private final ApplicationAvailability availability =
        mock(ApplicationAvailability.class);

    private final HealthController controller =
        new HealthController(availability);

    @Test
    void reportsReadyOnlyAfterApplicationStartupCompletes() {
        when(availability.getReadinessState()).thenReturn(
            ReadinessState.REFUSING_TRAFFIC
        );

        var starting = controller.health();

        assertEquals(503, starting.getStatusCode().value());
        assertEquals("STARTING", starting.getBody());

        when(availability.getReadinessState()).thenReturn(
            ReadinessState.ACCEPTING_TRAFFIC
        );

        var ready = controller.health();

        assertEquals(200, ready.getStatusCode().value());
        assertEquals("OK", ready.getBody());
    }
}
