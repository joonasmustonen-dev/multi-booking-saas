package com.example.booking.tenantdata.service;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/services")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF')")
public class ServiceOfferingController {

    private final ServiceOfferingService service;

    public ServiceOfferingController(ServiceOfferingService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public ResponseEntity<ServiceResponse> create(
        @Valid @RequestBody CreateServiceRequest request
    ) {
        ServiceResponse created = service.create(request);

        return ResponseEntity.created(
            URI.create("/api/v1/services/" + created.id())
        ).body(created);
    }

    @GetMapping
    public List<ServiceResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ServiceResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public ServiceResponse update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateServiceRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);

        return ResponseEntity.noContent().build();
    }
}
