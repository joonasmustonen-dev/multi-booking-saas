package com.example.booking.tenant;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class WorkspaceAccessDtos {

    private WorkspaceAccessDtos() {}

    public record WorkspaceSummary(
        UUID id,
        String slug,
        String name,
        WorkspaceRole role,
        UUID staffMemberId
    ) {}

    public record MemberResponse(
        UUID id,
        String subject,
        String email,
        WorkspaceRole role,
        UUID staffMemberId,
        MembershipStatus status,
        OffsetDateTime createdAt
    ) {}

    public record InvitationResponse(
        UUID id,
        String email,
        WorkspaceRole role,
        UUID staffMemberId,
        InvitationStatus status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        String acceptanceUrl
    ) {}

    public record CreateInvitationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull WorkspaceRole role,
        UUID staffMemberId,
        @Min(1) @Max(30) Integer expiresInDays
    ) {}

    public record ChangeRoleRequest(@NotNull WorkspaceRole role) {}

    public record LinkStaffRequest(UUID staffMemberId) {}

    public record InvitationPreview(
        String workspaceName,
        String email,
        WorkspaceRole role,
        InvitationStatus status,
        OffsetDateTime expiresAt
    ) {}
}
