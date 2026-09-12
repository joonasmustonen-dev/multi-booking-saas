package com.example.booking.tenantdata.appointment;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@PreAuthorize(
        "hasAnyRole('TENANT_ADMIN', 'STAFF')"
)
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(
            AppointmentService service) {

        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(
            @Valid
            @RequestBody
            CreateAppointmentRequest request) {

        AppointmentResponse appointment =
                service.create(request);

        return ResponseEntity
                .created(
                        URI.create(
                                "/api/v1/appointments/"
                                        + appointment.id()
                        )
                )
                .body(appointment);
    }

    @GetMapping
    public List<AppointmentResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AppointmentResponse findById(
            @PathVariable UUID id) {

        return service.findById(id);
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(
            @PathVariable UUID id) {

        return service.cancel(id);
    }

    @PostMapping("/{id}/reschedule")
    public AppointmentResponse reschedule(
            @PathVariable UUID id,
            @Valid
            @RequestBody
            RescheduleAppointmentRequest request) {

        return service.reschedule(
                id,
                request
        );
    }

    @PatchMapping("/{id}/status")
    public AppointmentResponse updateStatus(
           @PathVariable UUID id,
           @Valid
           @RequestBody
           UpdateAppointmentStatusRequest request) {

        return service.updateStatus(
                id,
                request.status()
        );
        }


        @GetMapping("/calendar")
public List<AppointmentCalendarResponse> findCalendar(

        @RequestParam
        @DateTimeFormat(
                iso = DateTimeFormat.ISO.DATE_TIME
        )
        OffsetDateTime from,

        @RequestParam
        @DateTimeFormat(
                iso = DateTimeFormat.ISO.DATE_TIME
        )
        OffsetDateTime to,

        @RequestParam(required = false)
        UUID resourceId,

        @RequestParam(required = false)
        UUID customerId,

        @RequestParam(required = false)
        UUID serviceId,

        @RequestParam(required = false)
        AppointmentStatus status) {

    return service.findCalendar(
            from,
            to,
            resourceId,
            customerId,
            serviceId,
            status
        );
        }

}