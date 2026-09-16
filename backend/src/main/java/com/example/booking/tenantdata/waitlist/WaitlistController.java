package com.example.booking.tenantdata.waitlist;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/waitlist")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF')")
public class WaitlistController {
    private final WaitlistService service;
    public WaitlistController(WaitlistService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<WaitlistEntryResponse> create(
        @Valid @RequestBody CreateWaitlistEntryRequest request
    ) {
        WaitlistEntryResponse entry = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/waitlist/" + entry.id())).body(entry);
    }

    @GetMapping
    public List<WaitlistEntryResponse> find(
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) WaitlistStatus status
    ) { return service.find(customerId, status); }

    @PostMapping("/{id}/accept")
    public WaitlistEntryResponse accept(@PathVariable UUID id) { return service.accept(id); }

    @PostMapping("/{id}/expire")
    public WaitlistEntryResponse expire(@PathVariable UUID id) { return service.expire(id); }

    @DeleteMapping("/{id}")
    public WaitlistEntryResponse remove(@PathVariable UUID id) { return service.remove(id); }
}
