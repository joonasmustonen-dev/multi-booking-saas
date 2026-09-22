package com.example.booking.tenant;

import static com.example.booking.tenant.WorkspaceAccessDtos.*;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class WorkspaceAccessController {

    private final WorkspaceAccessService access;

    public WorkspaceAccessController(WorkspaceAccessService access) {
        this.access = access;
    }

    @GetMapping("/api/account/workspaces")
    public List<WorkspaceSummary> workspaces(
        JwtAuthenticationToken authentication
    ) {
        return access.workspaces(WorkspaceAccessService.subject(authentication));
    }

    @GetMapping("/api/invitations/{token}")
    public InvitationPreview invitation(@PathVariable String token) {
        return access.preview(token);
    }

    @PostMapping("/api/invitations/{token}/accept")
    public WorkspaceSummary accept(
        @PathVariable String token,
        JwtAuthenticationToken authentication
    ) {
        return access.accept(
            token,
            WorkspaceAccessService.subject(authentication),
            WorkspaceAccessService.email(authentication)
        );
    }

    @GetMapping("/api/v1/workspace-access/members")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public List<MemberResponse> members() {
        return access.members(TenantContext.getRequiredTenantId());
    }

    @GetMapping("/api/v1/workspace-access/invitations")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public List<InvitationResponse> invitations() {
        return access.invitations(TenantContext.getRequiredTenantId());
    }

    @PostMapping("/api/v1/workspace-access/invitations")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public InvitationResponse invite(
        @Valid @RequestBody CreateInvitationRequest request,
        JwtAuthenticationToken authentication
    ) {
        return access.invite(
            TenantContext.getRequiredTenantId(),
            WorkspaceAccessService.subject(authentication),
            request
        );
    }

    @DeleteMapping("/api/v1/workspace-access/invitations/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Void> revoke(
        @PathVariable UUID id,
        JwtAuthenticationToken authentication
    ) {
        access.revokeInvitation(
            TenantContext.getRequiredTenantId(),
            id,
            WorkspaceAccessService.subject(authentication)
        );
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/v1/workspace-access/members/{id}/role")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public MemberResponse changeRole(
        @PathVariable UUID id,
        @Valid @RequestBody ChangeRoleRequest request,
        JwtAuthenticationToken authentication
    ) {
        return access.changeRole(
            TenantContext.getRequiredTenantId(),
            id,
            request.role(),
            WorkspaceAccessService.subject(authentication)
        );
    }

    @PatchMapping("/api/v1/workspace-access/members/{id}/staff-link")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public MemberResponse linkStaff(
        @PathVariable UUID id,
        @Valid @RequestBody LinkStaffRequest request,
        JwtAuthenticationToken authentication
    ) {
        return access.linkStaff(
            TenantContext.getRequiredTenantId(),
            id,
            request.staffMemberId(),
            WorkspaceAccessService.subject(authentication)
        );
    }

    @DeleteMapping("/api/v1/workspace-access/members/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Void> removeMember(
        @PathVariable UUID id,
        JwtAuthenticationToken authentication
    ) {
        access.removeMember(
            TenantContext.getRequiredTenantId(),
            id,
            WorkspaceAccessService.subject(authentication)
        );
        return ResponseEntity.noContent().build();
    }
}
