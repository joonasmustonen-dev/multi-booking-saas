package com.example.booking.tenantdata.availability;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/availability")
public class AvailabilityController {

    private final AvailabilityService service;

    public AvailabilityController(
            AvailabilityService service) {

        this.service = service;
    }

    @GetMapping
    public List<AvailabilitySlotResponse> findAvailability(

            @RequestParam UUID serviceId,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(required = false)
            UUID resourceId) {

        return service.findAvailability(
                serviceId,
                from,
                to,
                resourceId
        );
    }
}