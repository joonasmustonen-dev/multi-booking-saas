package com.example.booking.security;

import com.example.booking.tenant.*;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantSuspensionTest {

    @Test
    void cachedPoolsCannotBypassTenantSuspension() {
        TenantRepository repository = mock(TenantRepository.class);

        Tenant tenant = new Tenant();

        tenant.setSlug("tenant-a");

        tenant.setDatabaseName("tenant_a");

        tenant.setStatus("ACTIVE");

        when(repository.findBySlug("tenant-a")).thenReturn(Optional.of(tenant));

        HikariDataSource pool = mock(HikariDataSource.class);

        when(pool.getJdbcUrl()).thenReturn(
            "jdbc:postgresql://localhost:5432/tenant_a"
        );

        TenantDataSourceManager manager = new TenantDataSourceManager(
            repository,
            "localhost",
            "unused",
            "unused"
        );

        Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

        pools.put("tenant-a", pool);

        ReflectionTestUtils.setField(manager, "dataSources", pools);

        assertSame(pool, manager.getDataSource("tenant-a"));

        tenant.setStatus("SUSPENDED");

        assertEquals(
            403,
            assertThrows(ResponseStatusException.class, () ->
                manager.getDataSource("tenant-a")
            )
                .getStatusCode()
                .value()
        );

        verify(pool).close();

        assertTrue(pools.isEmpty());
    }
}
