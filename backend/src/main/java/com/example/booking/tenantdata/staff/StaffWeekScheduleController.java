package com.example.booking.tenantdata.staff;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/staff/{id}/schedule")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF')")
public class StaffWeekScheduleController {

    private final StaffWeekScheduleService service;

    public StaffWeekScheduleController(StaffWeekScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public StaffWeekScheduleResponse find(
        @PathVariable UUID id,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate weekStart
    ) {
        return service.find(id, weekStart);
    }

    @PutMapping
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public StaffWeekScheduleResponse save(
        @PathVariable UUID id,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate weekStart,
        @Valid @RequestBody StaffWeekScheduleRequest request
    ) {
        return service.save(id, weekStart, request);
    }

    @DeleteMapping
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public ResponseEntity<Void> reset(
        @PathVariable UUID id,
        @RequestParam @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate weekStart
    ) {
        service.reset(id, weekStart);

        return ResponseEntity.noContent().build();
    }
}
