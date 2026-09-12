package com.example.booking.tenantdata;

import com.example.booking.tenant.TenantContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        properties = "app.security.jwt-enabled=false"
)
class CustomerRepositoryIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @AfterEach
    void cleanup() {

        TenantContext.clear();
    }

    @Test
    void customerDataIsIsolatedBetweenTenantDatabases() {

        /*
         * TENANT A
         */
        TenantContext.setTenantId("tenant-a");

        customerRepository.deleteAll();

        Customer alice = new Customer(
                "Alice",
                "TenantA",
                "alice@tenant-a.test",
                null
        );

        customerRepository.saveAndFlush(alice);

        assertEquals(
                1,
                customerRepository.count()
        );

        /*
         * TENANT B
         */
        TenantContext.setTenantId("tenant-b");

        customerRepository.deleteAll();

        assertEquals(
                0,
                customerRepository.count(),
                "Tenant B must not see Tenant A's customer"
        );

        Customer bob = new Customer(
                "Bob",
                "TenantB",
                "bob@tenant-b.test",
                null
        );

        customerRepository.saveAndFlush(bob);

        assertEquals(
                1,
                customerRepository.count()
        );

        /*
         * BACK TO TENANT A
         */
        TenantContext.setTenantId("tenant-a");

        assertEquals(
                1,
                customerRepository.count()
        );

        Customer stored =
                customerRepository
                        .findByEmail("alice@tenant-a.test")
                        .orElseThrow();

        assertEquals(
                "Alice",
                stored.getFirstName()
        );

        assertTrue(
                customerRepository
                        .findByEmail("bob@tenant-b.test")
                        .isEmpty(),
                "Tenant A must not see Tenant B's customer"
        );
    }
}