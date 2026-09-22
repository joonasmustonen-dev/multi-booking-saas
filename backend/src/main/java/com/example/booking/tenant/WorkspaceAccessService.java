package com.example.booking.tenant;

import static com.example.booking.tenant.WorkspaceAccessDtos.*;

import com.example.booking.tenantdata.staff.StaffMemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class WorkspaceAccessService {

    private final WorkspaceMembershipRepository memberships;
    private final WorkspaceInvitationRepository invitations;
    private final WorkspaceAccessAuditRepository audit;
    private final TenantRepository tenants;
    private final StaffMemberRepository staff;
    private final String frontendOrigin;
    private final SecureRandom random = new SecureRandom();

    public WorkspaceAccessService(
        WorkspaceMembershipRepository memberships,
        WorkspaceInvitationRepository invitations,
        WorkspaceAccessAuditRepository audit,
        TenantRepository tenants,
        StaffMemberRepository staff,
        @Value("${app.security.frontend-origin:http://localhost:5173}")
        String frontendOrigin
    ) {
        this.memberships = memberships;
        this.invitations = invitations;
        this.audit = audit;
        this.tenants = tenants;
        this.staff = staff;
        this.frontendOrigin = frontendOrigin.replaceAll("/+$", "");
    }

    @Transactional(value = "platformTransactionManager", readOnly = true)
    public Optional<WorkspaceMembership> activeMembership(
        String tenantSlug,
        String subject
    ) {
        return memberships.findByTenantSlugAndIdentitySubjectAndStatus(
            tenantSlug,
            subject,
            MembershipStatus.ACTIVE
        );
    }

    @Transactional(value = "platformTransactionManager", readOnly = true)
    public List<WorkspaceSummary> workspaces(String subject) {
        return memberships
            .findByIdentitySubjectAndStatusOrderByCreatedAt(
                subject,
                MembershipStatus.ACTIVE
            )
            .stream()
            .filter(membership ->
                "ACTIVE".equals(membership.getTenant().getStatus())
            )
            .map(membership ->
                new WorkspaceSummary(
                    membership.getTenant().getId(),
                    membership.getTenant().getSlug(),
                    membership.getTenant().getName(),
                    membership.getRole(),
                    membership.getStaffMemberId()
                )
            )
            .toList();
    }

    @Transactional(value = "platformTransactionManager", readOnly = true)
    public List<MemberResponse> members(String tenantSlug) {
        return memberships
            .findByTenantSlugAndStatusOrderByCreatedAt(
                tenantSlug,
                MembershipStatus.ACTIVE
            )
            .stream()
            .map(this::memberResponse)
            .toList();
    }

    @Transactional("platformTransactionManager")
    public List<InvitationResponse> invitations(String tenantSlug) {
        var now = OffsetDateTime.now();
        return invitations
            .findByTenantSlugOrderByCreatedAtDesc(tenantSlug)
            .stream()
            .peek(invitation -> invitation.expireIfNeeded(now))
            .map(invitation -> invitationResponse(invitation, null))
            .toList();
    }

    @Transactional("platformTransactionManager")
    public InvitationResponse invite(
        String tenantSlug,
        String actor,
        CreateInvitationRequest request
    ) {
        var tenant = activeTenant(tenantSlug);
        String email = normalizeEmail(request.email());
        validateStaffLink(request.staffMemberId());

        if (
            memberships.existsByTenantIdAndEmailIgnoreCaseAndStatus(
                tenant.getId(),
                email,
                MembershipStatus.ACTIVE
            )
        ) {
            throw conflict("That email already has workspace access");
        }

        invitations
            .findByTenantIdAndNormalizedEmailAndStatus(
                tenant.getId(),
                email,
                InvitationStatus.PENDING
            )
            .ifPresent(existing -> {
                if (!existing.expireIfNeeded(OffsetDateTime.now())) {
                    throw conflict("A pending invitation already exists");
                }
                invitations.saveAndFlush(existing);
            });

        if (request.staffMemberId() != null) {
            invitations
                .findByTenantIdAndStaffMemberIdAndStatus(
                    tenant.getId(),
                    request.staffMemberId(),
                    InvitationStatus.PENDING
                )
                .ifPresent(existing -> {
                    if (!existing.expireIfNeeded(OffsetDateTime.now())) {
                        throw conflict(
                            "That staff record already has a pending invitation"
                        );
                    }
                    invitations.saveAndFlush(existing);
                });
            if (
                memberships
                    .findByTenantIdAndStaffMemberIdAndStatus(
                        tenant.getId(),
                        request.staffMemberId(),
                        MembershipStatus.ACTIVE
                    )
                    .isPresent()
            ) {
                throw conflict("That staff record is already linked to an account");
            }
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var invitation = invitations.save(
            new WorkspaceInvitation(
                tenant,
                email,
                request.role(),
                request.staffMemberId(),
                hash(token),
                OffsetDateTime.now().plusDays(
                    request.expiresInDays() == null
                        ? 7
                        : request.expiresInDays()
                ),
                actor
            )
        );
        record(
            tenant.getId(),
            actor,
            "INVITATION_CREATED",
            null,
            invitation.getId(),
            email + ":" + request.role()
        );
        return invitationResponse(
            invitation,
            frontendOrigin + "/accept-invitation?token=" + token
        );
    }

    @Transactional("platformTransactionManager")
    public InvitationPreview preview(String token) {
        var invitation = validInvitation(token);
        return new InvitationPreview(
            invitation.getTenant().getName(),
            invitation.getNormalizedEmail(),
            invitation.getRole(),
            invitation.getStatus(),
            invitation.getExpiresAt()
        );
    }

    @Transactional("platformTransactionManager")
    public WorkspaceSummary accept(
        String token,
        String subject,
        String authenticatedEmail
    ) {
        var invitation = validInvitation(token);
        String email = normalizeEmail(authenticatedEmail);
        if (!MessageDigest.isEqual(
            email.getBytes(StandardCharsets.UTF_8),
            invitation.getNormalizedEmail().getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Sign in with the email address that was invited"
            );
        }
        var existing = memberships.findByTenantIdAndIdentitySubject(
            invitation.getTenant().getId(),
            subject
        );
        if (
            existing
                .filter(member ->
                    member.getStatus() == MembershipStatus.ACTIVE
                )
                .isPresent()
        ) {
            throw conflict("This account already belongs to the workspace");
        }
        if (
            existing
                .filter(member ->
                    member.getStatus() == MembershipStatus.SUSPENDED
                )
                .isPresent()
        ) {
            throw conflict("This account is suspended in the workspace");
        }
        var membership = existing.orElseGet(() ->
            new WorkspaceMembership(
                invitation.getTenant(),
                subject,
                email,
                invitation.getRole(),
                invitation.getStaffMemberId()
            )
        );
        if (existing.isPresent()) {
            membership.reactivate(
                email,
                invitation.getRole(),
                invitation.getStaffMemberId()
            );
        }
        memberships.save(membership);
        invitation.accept(subject);
        record(
            invitation.getTenant().getId(),
            subject,
            "INVITATION_ACCEPTED",
            subject,
            invitation.getId(),
            invitation.getNormalizedEmail()
        );
        return new WorkspaceSummary(
            membership.getTenant().getId(),
            membership.getTenant().getSlug(),
            membership.getTenant().getName(),
            membership.getRole(),
            membership.getStaffMemberId()
        );
    }

    @Transactional("platformTransactionManager")
    public void revokeInvitation(String tenantSlug, UUID id, String actor) {
        var invitation = invitationForTenant(tenantSlug, id);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw conflict("Only pending invitations can be revoked");
        }
        invitation.revoke();
        record(
            invitation.getTenant().getId(),
            actor,
            "INVITATION_REVOKED",
            null,
            id,
            invitation.getNormalizedEmail()
        );
    }

    @Transactional("platformTransactionManager")
    public MemberResponse changeRole(
        String tenantSlug,
        UUID id,
        WorkspaceRole role,
        String actor
    ) {
        var membership = membershipForTenant(tenantSlug, id);
        if (
            membership.getRole() == WorkspaceRole.TENANT_ADMIN &&
            role != WorkspaceRole.TENANT_ADMIN
        ) requireAnotherAdministrator(membership);
        membership.changeRole(role);
        record(
            membership.getTenant().getId(),
            actor,
            "MEMBERSHIP_ROLE_CHANGED",
            membership.getIdentitySubject(),
            null,
            role.name()
        );
        return memberResponse(membership);
    }

    @Transactional("platformTransactionManager")
    public MemberResponse linkStaff(
        String tenantSlug,
        UUID id,
        UUID staffMemberId,
        String actor
    ) {
        var membership = membershipForTenant(tenantSlug, id);
        validateStaffLink(staffMemberId);
        if (staffMemberId != null) {
            memberships
                .findByTenantIdAndStaffMemberIdAndStatus(
                    membership.getTenant().getId(),
                    staffMemberId,
                    MembershipStatus.ACTIVE
                )
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw conflict("That staff record is already linked to an account");
                });
        }
        membership.linkStaff(staffMemberId);
        record(
            membership.getTenant().getId(),
            actor,
            "MEMBERSHIP_STAFF_LINK_CHANGED",
            membership.getIdentitySubject(),
            null,
            staffMemberId == null ? "unlinked" : staffMemberId.toString()
        );
        return memberResponse(membership);
    }

    @Transactional("platformTransactionManager")
    public void removeMember(String tenantSlug, UUID id, String actor) {
        var membership = membershipForTenant(tenantSlug, id);
        if (membership.getRole() == WorkspaceRole.TENANT_ADMIN) {
            requireAnotherAdministrator(membership);
        }
        membership.remove();
        record(
            membership.getTenant().getId(),
            actor,
            "MEMBERSHIP_REMOVED",
            membership.getIdentitySubject(),
            null,
            membership.getEmail()
        );
    }

    private void requireAnotherAdministrator(WorkspaceMembership membership) {
        if (
            memberships.countByTenantIdAndRoleAndStatus(
                membership.getTenant().getId(),
                WorkspaceRole.TENANT_ADMIN,
                MembershipStatus.ACTIVE
            ) <= 1
        ) throw conflict("The workspace must retain an administrator");
    }

    private void validateStaffLink(UUID staffMemberId) {
        if (staffMemberId == null) return;
        if (
            staff.findById(staffMemberId).filter(member -> !member.isRemoved()).isEmpty()
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "The linked staff member was not found in this workspace"
            );
        }
    }

    private WorkspaceInvitation validInvitation(String token) {
        if (token == null || token.length() < 32 || token.length() > 100) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var invitation = invitations
            .findLockedByTokenHash(hash(token))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        invitation.expireIfNeeded(OffsetDateTime.now());
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResponseStatusException(
                HttpStatus.GONE,
                "This invitation is no longer available"
            );
        }
        return invitation;
    }

    private Tenant activeTenant(String slug) {
        return tenants
            .findBySlug(slug)
            .filter(tenant -> "ACTIVE".equals(tenant.getStatus()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private WorkspaceMembership membershipForTenant(String slug, UUID id) {
        return memberships
            .findById(id)
            .filter(membership ->
                membership.getTenant().getSlug().equals(slug) &&
                membership.getStatus() == MembershipStatus.ACTIVE
            )
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private WorkspaceInvitation invitationForTenant(String slug, UUID id) {
        return invitations
            .findById(id)
            .filter(invitation -> invitation.getTenant().getSlug().equals(slug))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private MemberResponse memberResponse(WorkspaceMembership membership) {
        return new MemberResponse(
            membership.getId(),
            membership.getIdentitySubject(),
            membership.getEmail(),
            membership.getRole(),
            membership.getStaffMemberId(),
            membership.getStatus(),
            membership.getCreatedAt()
        );
    }

    private InvitationResponse invitationResponse(
        WorkspaceInvitation invitation,
        String acceptanceUrl
    ) {
        return new InvitationResponse(
            invitation.getId(),
            invitation.getNormalizedEmail(),
            invitation.getRole(),
            invitation.getStaffMemberId(),
            invitation.getStatus(),
            invitation.getExpiresAt(),
            invitation.getCreatedAt(),
            acceptanceUrl
        );
    }

    private void record(
        UUID tenantId,
        String actor,
        String action,
        String target,
        UUID invitationId,
        String detail
    ) {
        audit.save(
            new WorkspaceAccessAudit(
                tenantId,
                actor,
                action,
                target,
                invitationId,
                detail
            )
        );
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "An email address is required"
            );
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    token.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public static String subject(JwtAuthenticationToken authentication) {
        return authentication.getToken().getSubject();
    }

    public static String email(JwtAuthenticationToken authentication) {
        String email = authentication.getToken().getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "The identity provider did not supply an email address"
            );
        }
        return email;
    }
}
