package com.example.booking.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void storesTenantId() {

        TenantContext.setTenantId("tenant-a");

        assertEquals(
                "tenant-a",
                TenantContext.getTenantId()
        );
    }

    @Test
    void clearRemovesTenantId() {

        TenantContext.setTenantId("tenant-a");

        TenantContext.clear();

        assertNull(
                TenantContext.getTenantId()
        );
    }

    @Test
    void tenantContextDoesNotLeakBetweenRequests() {

        // Simulate request A
        TenantContext.setTenantId("tenant-a");

        assertEquals(
                "tenant-a",
                TenantContext.getTenantId()
        );

        // Simulate request completion
        TenantContext.clear();

        // Simulate request B reusing the same thread
        assertNull(
                TenantContext.getTenantId()
        );
    }
}