package com.example.booking.tenantdata.appointment;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF')")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(
        @Valid @RequestBody CreateAppointmentRequest request
    ) {
        AppointmentResponse appointment = service.create(request);

        return ResponseEntity.created(
            URI.create("/api/v1/appointments/" + appointment.id())
        ).body(appointment);
    }

    @GetMapping
    public List<AppointmentResponse> findAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size
    ) {
        return service.findPage(page, size);
    }

    @GetMapping("/{id}")
    public AppointmentResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/reschedule")
    public AppointmentResponse reschedule(
        @PathVariable UUID id,
        @Valid @RequestBody RescheduleAppointmentRequest request
    ) {
        return service.reschedule(id, request);
    }

    @PatchMapping("/{id}/status")
    public AppointmentResponse updateStatus(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateAppointmentStatusRequest request
    ) {
        return service.updateStatus(id, request.status());
    }

    @GetMapping("/calendar")
    public List<AppointmentCalendarResponse> findCalendar(
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate from,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate to,
        @RequestParam(required = false) UUID resourceId,
        @RequestParam(required = false) UUID staffId,
        @RequestParam(required = false) UUID locationId,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) UUID serviceId,
        @RequestParam(required = false) AppointmentStatus status
    ) {
        return service.findCalendar(
            from,
            to,
            resourceId,
            staffId,
            locationId,
            customerId,
            serviceId,
            status
        );
    }

    @GetMapping("/{id}/availability")
    public List<com.example.booking.tenantdata.availability.AvailabilitySlotResponse> findRescheduleAvailability(
        @PathVariable UUID id,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate from,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate to,
        @RequestParam(required = false) UUID staffId,
        @RequestParam(required = false) UUID locationId,
        @RequestParam(required = false) UUID resourceId
    ) {
        return service.findRescheduleAvailability(
            id,
            from,
            to,
            staffId,
            locationId,
            resourceId
        );
    }
}
