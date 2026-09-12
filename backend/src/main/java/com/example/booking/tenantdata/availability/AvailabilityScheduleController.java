package com.example.booking.tenantdata.availability;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resources/{resourceId}/availability")
public class AvailabilityScheduleController {

    private final AvailabilityScheduleService service;

    public AvailabilityScheduleController(
            AvailabilityScheduleService service) {

        this.service = service;
    }

    @PostMapping("/rules")
    public AvailabilityRule createRule(
            @PathVariable UUID resourceId,
            @Valid @RequestBody AvailabilityRuleRequest request) {

        return service.createRule(resourceId, request);
    }

    @PostMapping("/exceptions")
    public AvailabilityException createException(
            @PathVariable UUID resourceId,
            @Valid @RequestBody AvailabilityExceptionRequest request) {

        return service.createException(resourceId, request);
    }
}