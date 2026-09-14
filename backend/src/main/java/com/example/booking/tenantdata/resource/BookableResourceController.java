package com.example.booking.tenantdata.resource;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resources")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF')")
public class BookableResourceController {

    private final BookableResourceService service;

    public BookableResourceController(BookableResourceService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public ResponseEntity<ResourceResponse> create(
        @Valid @RequestBody ResourceRequest request
    ) {
        BookableResource resource = service.create(request);

        return ResponseEntity.created(
            URI.create("/api/v1/resources/" + resource.getId())
        ).body(ResourceResponse.from(resource));
    }

    @GetMapping
    public List<ResourceResponse> findAll() {
        return service.findAll().stream().map(ResourceResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResourceResponse findById(@PathVariable UUID id) {
        return ResourceResponse.from(service.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public ResourceResponse update(
        @PathVariable UUID id,
        @Valid @RequestBody ResourceRequest request
    ) {
        return ResourceResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole(\'TENANT_ADMIN\')")
    public ResourceResponse setActive(
        @PathVariable UUID id,
        @Valid @RequestBody com.example.booking.tenantdata.management.AssignmentActiveRequest request
    ) {
        return ResourceResponse.from(service.setActive(id, request.active()));
    }
}
