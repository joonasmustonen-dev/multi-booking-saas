package com.example.booking.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
    properties = {
        "app.security.jwt-enabled=true",
        "app.security.requests-per-minute=10000"
    }
)
@AutoConfigureMockMvc
class BackendSecurityIntegrationTest {

    @Autowired
    MockMvc mvc;

    private org.springframework.test.web.servlet.request.RequestPostProcessor user(
        String role
    ) {
        return jwt()
            .jwt(token ->
                token.subject("security-test").claim("tenant_id", "tenant-a")
            )
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void platformRegistryIsGlobalAdminOnlyAndOmitsDatabaseNames()
        throws Exception {
        for (String role : new String[] {
            "CUSTOMER",
            "STAFF",
            "TENANT_ADMIN"
        }) {
            mvc.perform(
                get("/api/platform/tenants").with(user(role))
            ).andExpect(status().isForbidden());
        }
        mvc.perform(
            get("/api/platform/tenants").with(
                jwt().authorities(
                    new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")
                )
            )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].databaseName").doesNotExist());
    }

    @Test
    void staffCanReadSchedulesButCannotAdministerWorkspaceOrPrivacy()
        throws Exception {
        mvc.perform(get("/api/v1/staff").with(user("STAFF"))).andExpect(
            status().isOk()
        );

        mvc.perform(
            post("/api/v1/staff")
                .with(user("STAFF"))
                .contentType("application/json")
                .content("{\"name\":\"Unauthorized creation\"}")
        ).andExpect(status().isForbidden());

        mvc.perform(
            put(
                "/api/v1/staff/00000000-0000-0000-0000-000000000001/availability/rules"
            )
                .with(user("STAFF"))
                .contentType("application/json")
                .content("{\"rules\":[]}")
        ).andExpect(status().isForbidden());

        mvc.perform(
            get("/api/v1/privacy/policy").with(user("STAFF"))
        ).andExpect(status().isForbidden());

        mvc.perform(
            get("/api/v1/customers")
                .with(user("TENANT_ADMIN"))
                .param("size", "201")
        ).andExpect(status().isBadRequest());
    }

    @Test
    void invalidTenantClaimsAreRejectedWithoutCreatingAnHttpSession()
        throws Exception {
        mvc.perform(
            get("/api/v1/staff").with(
                jwt().jwt(token -> token.claim("tenant_id", "../tenant-a"))
            )
        ).andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/staff").with(jwt())).andExpect(
            status().isForbidden()
        );

        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void privacyExportsAndErasureRequireAnAdministratorAndNeverTrustARecordId()
        throws Exception {
        String customer =
            "/api/v1/customers/00000000-0000-0000-0000-000000000001";

        mvc.perform(get(customer + "/export").with(user("STAFF"))).andExpect(
            status().isForbidden()
        );

        mvc.perform(
            post(customer + "/erase")
                .with(user("STAFF"))
                .contentType("application/json")
                .content("{\"mode\":\"ALL_DATA\",\"confirmed\":true}")
        ).andExpect(status().isForbidden());

        mvc.perform(
            get(customer + "/export").with(user("TENANT_ADMIN"))
        ).andExpect(status().isNotFound());
    }
}
