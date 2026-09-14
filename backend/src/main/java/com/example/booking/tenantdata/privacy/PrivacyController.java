package com.example.booking.tenantdata.privacy;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class PrivacyController {

    private final PrivacyService privacy;

    private final SecurityAuditService audit;

    public PrivacyController(
        PrivacyService privacy,
        SecurityAuditService audit
    ) {
        this.privacy = privacy;

        this.audit = audit;
    }

    @GetMapping("/privacy/policy")
    public PrivacyService.Policy policy() {
        return privacy.policy();
    }

    @PutMapping("/privacy/policy")
    public PrivacyService.Policy policy(
        @Valid @RequestBody PrivacyService.Policy policy
    ) {
        return privacy.savePolicy(policy);
    }

    @GetMapping("/privacy/retention")
    public PrivacyService.Preview preview() {
        return privacy.preview();
    }

    @PostMapping("/privacy/retention/apply")
    public PrivacyService.Preview apply(
        @Valid @RequestBody PrivacyService.ApplyRequest request
    ) {
        return privacy.apply(request);
    }

    @GetMapping("/privacy/audit")
    public List<SecurityAuditEvent.Summary> audit() {
        return audit.recent();
    }

    @GetMapping("/customers/{id}/export")
    public PrivacyService.Export export(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "500") int size
    ) {
        return privacy.export(id, page, size);
    }

    @PatchMapping("/customers/{id}/privacy")
    public com.example.booking.tenantdata.customer.CustomerResponse controls(
        @PathVariable UUID id,
        @Valid @RequestBody PrivacyService.Controls request
    ) {
        return privacy.controls(id, request);
    }

    @PostMapping("/customers/{id}/erase")
    public ResponseEntity<Void> erase(
        @PathVariable UUID id,
        @Valid @RequestBody PrivacyService.ErasureRequest request
    ) {
        privacy.erase(id, request);

        return ResponseEntity.noContent().build();
    }
}
