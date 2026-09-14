package com.example.booking.tenantdata.staff;
import com.example.booking.tenantdata.management.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.net.URI;
@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF')")
public class StaffMemberController {
    private final StaffMemberService service;
    public StaffMemberController(StaffMemberService service) { this.service = service; }
    @GetMapping
    public List<StaffMemberResponse> findAll() { return service.findAll(); }
    @GetMapping("/{id}")
    public StaffMemberResponse findById(@PathVariable UUID id) { return service.findById(id); }
    @PostMapping
    public ResponseEntity<StaffMemberResponse> create(@Valid @RequestBody StaffMemberRequest request) {
        var created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/staff/" + created.id())).body(created);
    }
    @PutMapping("/{id}")
    public StaffMemberResponse update(@PathVariable UUID id, @Valid @RequestBody StaffMemberRequest request) {
        return service.update(id, request);
    }
    @PatchMapping("/{id}/active")
    public StaffMemberResponse setActive(@PathVariable UUID id, @Valid @RequestBody AssignmentActiveRequest request) {
        return service.setActive(id, request.active());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) { service.remove(id); return ResponseEntity.noContent().build(); }
}
