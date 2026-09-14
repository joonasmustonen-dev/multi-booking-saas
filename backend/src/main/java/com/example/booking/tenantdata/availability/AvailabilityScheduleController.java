package com.example.booking.tenantdata.availability;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
    "/api/v1/{ownerType:staff|locations|resources}/{ownerId}/availability"
)
@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF')")
public class AvailabilityScheduleController {

    private final AvailabilityScheduleService service;

    public AvailabilityScheduleController(AvailabilityScheduleService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    public List<AvailabilityRuleResponse> findRules(
        @PathVariable String ownerType,
        @PathVariable UUID ownerId
    ) {
        return service.findRules(
            AvailabilityOwnerType.fromPath(ownerType),
            ownerId
        );
    }

    @PostMapping("/rules")
    public ResponseEntity<AvailabilityRuleResponse> createRule(
        @PathVariable String ownerType,
        @PathVariable UUID ownerId,
        @Valid @RequestBody AvailabilityRuleRequest request
    ) {
        var created = service.createRule(
            AvailabilityOwnerType.fromPath(ownerType),
            ownerId,
            request
        );

        return ResponseEntity.created(
            URI.create(
                "/api/v1/" +
                    ownerType +
                    "/" +
                    ownerId +
                    "/availability/rules/" +
                    created.id()
            )
        ).body(created);
    }

    @PutMapping("/rules")
    public List<AvailabilityRuleResponse> replaceRules(
        @PathVariable String ownerType,
        @PathVariable UUID ownerId,
        @Valid @RequestBody ReplaceAvailabilityRulesRequest request
    ) {
        return service.replaceRules(
            AvailabilityOwnerType.fromPath(ownerType),
            ownerId,
            request
        );
    }

    @PutMapping("/rules/{ruleId}")
    public AvailabilityRuleResponse updateRule(
        @PathVariable String ownerType,
        @PathVariable UUID ownerId,
        @PathVariable UUID ruleId,
        @Valid @RequestBody UpdateAvailabilityRuleRequest request
    ) {
        return service.updateRule(
            AvailabilityOwnerType.fromPath(ownerType),
            ownerId,
            ruleId,
            request
        );
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(
        @PathVariable String ownerType,
        @PathVariable UUID ownerId,
        @PathVariable UUID ruleId
    ) {
        service.deleteRule(
            AvailabilityOwnerType.fromPath(ownerType),
            ownerId,
            ruleId
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exceptions")
    public List<AvailabilityExceptionResponse> findExceptions(
        @PathVariable String ownerType,
        @PathVariable UUID ownerId
    ) {
        return service.findExceptions(
            AvailabilityOwnerType.fromPath(ownerType),
            ownerId
        );
    }

    @PostMapping("/exceptions")
    public ResponseEntity<AvailabilityExceptionResponse> createException(
        @PathVariable String ownerType,
        @PathVariable UUID ownerId,
        @Valid @RequestBody AvailabilityExceptionRequest request
    ) {
        var created = service.createException(
            AvailabilityOwnerType.fromPath(ownerType),
            ownerId,
            request
        );

        return ResponseEntity.created(
            URI.create(
                "/api/v1/" +
                    ownerType +
                    "/" +
                    ownerId +
                    "/availability/exceptions/" +
                    created.id()
            )
        ).body(created);
    }

    @PutMapping("/exceptions/{exceptionId}")
    public AvailabilityExceptionResponse updateException(
        @PathVariable String ownerType,
        @PathVariable UUID ownerId,
        @PathVariable UUID exceptionId,
        @Valid @RequestBody AvailabilityExceptionRequest request
    ) {
        return service.updateException(
            AvailabilityOwnerType.fromPath(ownerType),
            ownerId,
            exceptionId,
            request
        );
    }

    @DeleteMapping("/exceptions/{exceptionId}")
    public ResponseEntity<Void> deleteException(
        @PathVariable String ownerType,
        @PathVariable UUID ownerId,
        @PathVariable UUID exceptionId
    ) {
        service.deleteException(
            AvailabilityOwnerType.fromPath(ownerType),
            ownerId,
            exceptionId
        );

        return ResponseEntity.noContent().build();
    }
}
