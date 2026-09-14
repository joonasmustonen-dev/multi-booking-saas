package com.example.booking.tenantdata.location;

import com.example.booking.tenantdata.management.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.net.URI;
@RestController
@RequestMapping("/api/v1/locations")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF')")
public class LocationController {

    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    @GetMapping
    public List<LocationResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public LocationResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public ResponseEntity<LocationResponse> create(
        @Valid @RequestBody LocationRequest request
    ) {
        var created = service.create(request);

        return ResponseEntity.created(
            URI.create("/api/v1/locations/" + created.id())
        ).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public LocationResponse update(
        @PathVariable UUID id,
        @Valid @RequestBody LocationRequest request
    ) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public LocationResponse setActive(
        @PathVariable UUID id,
        @Valid @RequestBody AssignmentActiveRequest request
    ) {
        return service.setActive(id, request.active());
    }
}
