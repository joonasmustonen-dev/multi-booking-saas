package com.example.booking.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "app.security.jwt-enabled=false")
public class TenantRoutingIntegrationTest {

    @Autowired
    private TenantDataSourceManager dataSourceManager;

    @Autowired
    private TenantRoutingDataSource routingDataSource;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void tenantAAndTenantBUseDifferentDataSources() throws Exception {
        DataSource tenantA = dataSourceManager.getDataSource("tenant-a");

        DataSource tenantB = dataSourceManager.getDataSource("tenant-b");

        assertNotSame(
            tenantA,
            tenantB,
            "Tenant A and Tenant B must never share a DataSource"
        );

        try (
            Connection connectionA = tenantA.getConnection();
            Connection connectionB = tenantB.getConnection()
        ) {
            assertEquals(
                "jdbc:postgresql://localhost:5432/tenant_a",
                connectionA.getMetaData().getURL()
            );

            assertEquals(
                "jdbc:postgresql://localhost:5432/tenant_b",
                connectionB.getMetaData().getURL()
            );
        }
    }

    @Test
    void tenantAResolvesToTenantA() {
        TenantContext.setTenantId("tenant-a");

        assertEquals("tenant-a", routingDataSource.currentLookupKeyForTest());
    }

    @Test
    void tenantBResolvesToTenantB() {
        TenantContext.setTenantId("tenant-b");

        assertEquals("tenant-b", routingDataSource.currentLookupKeyForTest());
    }

    @Test
    void tenantAConnectsToTenantADatabase() throws Exception {
        TenantContext.setTenantId("tenant-a");

        try (Connection connection = routingDataSource.getConnection()) {
            assertEquals(
                "jdbc:postgresql://localhost:5432/tenant_a",
                connection.getMetaData().getURL()
            );
        }
    }

    @Test
    void tenantBConnectsToTenantBDatabase() throws Exception {
        TenantContext.setTenantId("tenant-b");

        try (Connection connection = routingDataSource.getConnection()) {
            assertEquals(
                "jdbc:postgresql://localhost:5432/tenant_b",
                connection.getMetaData().getURL()
            );
        }
    }

    @Test
    void tenantAReadsOnlyTenantAData() throws Exception {
        TenantContext.setTenantId("tenant-a");

        try (
            Connection connection = routingDataSource.getConnection();
            var statement = connection.createStatement();
            var result = statement.executeQuery(
                "SELECT tenant_marker FROM routing_test_data"
            )
        ) {
            assertTrue(result.next());

            assertEquals("THIS_IS_TENANT_A", result.getString("tenant_marker"));
        }
    }

    @Test
    void tenantBReadsOnlyTenantBData() throws Exception {
        TenantContext.setTenantId("tenant-b");

        try (
            Connection connection = routingDataSource.getConnection();
            var statement = connection.createStatement();
            var result = statement.executeQuery(
                "SELECT tenant_marker FROM routing_test_data"
            )
        ) {
            assertTrue(result.next());

            assertEquals("THIS_IS_TENANT_B", result.getString("tenant_marker"));
        }
    }

    @Test
    void tenantContextIsClearedAfterRequest() {
        TenantContext.setTenantId("tenant-a");

        assertEquals("tenant-a", TenantContext.getTenantId());

        TenantContext.clear();

        assertNull(
            TenantContext.getTenantId(),
            "Tenant Context must not survive the request"
        );
    }

    @Test
    void noTenantUsesPlatformDatabase() throws Exception {
        TenantContext.clear();

        try (Connection connection = routingDataSource.getConnection()) {
            assertEquals(
                "jdbc:postgresql://localhost:5432/platform_db",
                connection.getMetaData().getURL()
            );
        }
    }

    @Test
    void tenantPoolsAreDifferent() {
        var tenantA =
            (com.zaxxer.hikari.HikariDataSource) dataSourceManager.getDataSource(
                "tenant-a"
            );

        var tenantB =
            (com.zaxxer.hikari.HikariDataSource) dataSourceManager.getDataSource(
                "tenant-b"
            );

        assertEquals("tenant-tenant-a", tenantA.getPoolName());

        assertEquals("tenant-tenant-b", tenantB.getPoolName());

        assertNotSame(tenantA, tenantB);
    }
}
