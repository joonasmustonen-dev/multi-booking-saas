package com.example.booking.tenant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;

@SpringBootTest(
    properties = {
        "app.security.jwt-enabled=true",
        "app.security.membership-enforcement=true",
        "app.security.requests-per-minute=10000"
    }
)
@AutoConfigureMockMvc
class WorkspaceMembershipIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired WorkspaceMembershipRepository memberships;
    @Autowired WorkspaceInvitationRepository invitations;

    private final ArrayList<WorkspaceMembership> createdMemberships =
        new ArrayList<>();
    private final ArrayList<WorkspaceInvitation> createdInvitations =
        new ArrayList<>();

    @AfterEach
    void cleanup() {
        invitations.deleteAll(createdInvitations);
        memberships.deleteAll(createdMemberships);
    }

    @Test
    void membershipRoleOverridesUntrustedRealmRole() throws Exception {
        createMembership("member-staff", "staff-member@example.test", WorkspaceRole.STAFF);

        mvc.perform(
            delete(
                "/api/v1/workspace-access/members/" +
                java.util.UUID.randomUUID()
            ).with(
                user(
                    "member-staff",
                    "TENANT_ADMIN",
                    "staff-member@example.test"
                )
            )
        ).andExpect(status().isForbidden());
    }

    @Test
    void tokenWithoutMembershipCannotEnterWorkspace() throws Exception {
        mvc.perform(
            get("/api/v1/staff").with(
                user("not-a-member", "TENANT_ADMIN", "outsider@example.test")
            )
        ).andExpect(status().isForbidden());
    }

    @Test
    void accountCanDiscoverMembershipWithoutTenantClaim() throws Exception {
        createMembership("workspace-list-user", "list@example.test", WorkspaceRole.STAFF);

        mvc.perform(
            get("/api/account/workspaces").with(
                jwt().jwt(token ->
                    token.subject("workspace-list-user").claim("email", "list@example.test")
                )
            )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value("tenant-a"))
            .andExpect(jsonPath("$[0].role").value("STAFF"));
    }

    @Test
    void verifiedAccountResolvesPreprovisionedMembershipByEmail()
        throws Exception {
        createMembership(
            "unclaimed:preprovisioned@example.test",
            "preprovisioned@example.test",
            WorkspaceRole.TENANT_ADMIN
        );

        mvc.perform(
            get("/api/account/workspaces").with(
                jwt().jwt(token ->
                    token
                        .subject("claimed-keycloak-subject")
                        .claim("email", "preprovisioned@example.test")
                        .claim("email_verified", true)
                )
            )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value("tenant-a"))
            .andExpect(jsonPath("$[0].role").value("TENANT_ADMIN"));

        Assertions.assertTrue(
            memberships
                .findByTenantSlugAndIdentitySubjectAndStatus(
                    "tenant-a",
                    "unclaimed:preprovisioned@example.test",
                    MembershipStatus.ACTIVE
                )
                .isPresent()
        );
    }

    @Test
    void verifiedAccountUsesPreprovisionedMembershipRoleForWorkspaceRequests()
        throws Exception {
        createMembership(
            "unclaimed:workspace-admin@example.test",
            "workspace-admin@example.test",
            WorkspaceRole.TENANT_ADMIN
        );

        mvc.perform(
            get("/api/v1/dashboard/summary")
                .header("X-Workspace", "tenant-a")
                .with(
                    jwt().jwt(token ->
                        token
                            .subject("claimed-keycloak-subject")
                            .claim("email", "workspace-admin@example.test")
                            .claim("email_verified", true)
                    )
                )
        ).andExpect(status().isOk());
    }

    @Test
    void unverifiedEmailCannotClaimPreprovisionedMembership()
        throws Exception {
        createMembership(
            "unclaimed:unverified@example.test",
            "unverified@example.test",
            WorkspaceRole.TENANT_ADMIN
        );

        mvc.perform(
            get("/api/account/workspaces").with(
                jwt().jwt(token ->
                    token
                        .subject("unverified-keycloak-subject")
                        .claim("email", "unverified@example.test")
                        .claim("email_verified", false)
                )
            )
        )
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void removingMembershipTakesEffectWithoutWaitingForANewToken()
        throws Exception {
        createMembership(
            "removal-admin",
            "removal-admin@example.test",
            WorkspaceRole.TENANT_ADMIN
        );
        var removed = createMembership(
            "removed-staff",
            "removed-staff@example.test",
            WorkspaceRole.STAFF
        );

        mvc.perform(
            delete("/api/v1/workspace-access/members/" + removed.getId()).with(
                user(
                    "removal-admin",
                    "STAFF",
                    "removal-admin@example.test"
                )
            )
        ).andExpect(status().isNoContent());

        mvc.perform(
            get("/api/v1/staff").with(
                user(
                    "removed-staff",
                    "TENANT_ADMIN",
                    "removed-staff@example.test"
                )
            )
        ).andExpect(status().isForbidden());
    }

    @Test
    void lastAdministratorCannotRemoveTheirOwnAccess() throws Exception {
        var administrator = createMembership(
            "only-admin",
            "only-admin@example.test",
            WorkspaceRole.TENANT_ADMIN
        );

        mvc.perform(
            delete(
                "/api/v1/workspace-access/members/" + administrator.getId()
            ).with(user("only-admin", "STAFF", "only-admin@example.test"))
        ).andExpect(status().isConflict());
    }

    @Test
    void invitationIsEmailBoundAndCreatesWorkspaceMembership() throws Exception {
        createMembership("invite-admin", "admin@example.test", WorkspaceRole.TENANT_ADMIN);

        String response = mvc.perform(
            post("/api/v1/workspace-access/invitations")
                .with(user("invite-admin", "STAFF", "admin@example.test"))
                .contentType("application/json")
                .content(
                    "{\"email\":\"new.staff@example.test\",\"role\":\"STAFF\",\"expiresInDays\":7}"
                )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acceptanceUrl").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

        var mapper = new tools.jackson.databind.ObjectMapper();
        String url = mapper.readTree(response).get("acceptanceUrl").asText();
        String token = url.substring(url.indexOf("token=") + 6);
        invitations
            .findByTokenHash(hash(token))
            .ifPresent(createdInvitations::add);

        mvc.perform(
            post("/api/invitations/" + token + "/accept").with(
                jwt().jwt(jwt ->
                    jwt.subject("wrong-email-user").claim("email", "other@example.test")
                )
            )
        ).andExpect(status().isForbidden());

        mvc.perform(
            post("/api/invitations/" + token + "/accept").with(
                jwt().jwt(jwt ->
                    jwt.subject("invited-user").claim("email", "new.staff@example.test")
                )
            )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STAFF"));

        memberships
            .findByTenantSlugAndIdentitySubjectAndStatus(
                "tenant-a",
                "invited-user",
                MembershipStatus.ACTIVE
            )
            .ifPresent(createdMemberships::add);
    }

    private WorkspaceMembership createMembership(
        String subject,
        String email,
        WorkspaceRole role
    ) {
        var tenant = tenants.findBySlug("tenant-a").orElseThrow();
        var membership = memberships.save(
            new WorkspaceMembership(tenant, subject, email, role, null)
        );
        createdMemberships.add(membership);
        return membership;
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor user(
        String subject,
        String realmRole,
        String email
    ) {
        return jwt()
            .jwt(token ->
                token
                    .subject(subject)
                    .claim("email", email)
                    .claim("tenant_id", "tenant-a")
            )
            .authorities(new SimpleGrantedAuthority("ROLE_" + realmRole));
    }

    private static String hash(String token) throws Exception {
        return java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        );
    }
}
