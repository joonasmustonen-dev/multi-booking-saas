package com.example.booking.tenantdata;

import com.example.booking.tenant.TenantContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        properties = "app.security.jwt-enabled=false"
)
class CustomerRepositoryIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    private UUID tenantACustomerId;
    private UUID tenantBCustomerId;

    @AfterEach
    void cleanup() {

        if (tenantACustomerId != null) {

            TenantContext.setTenantId("tenant-a");

            customerRepository
                    .findById(tenantACustomerId)
                    .ifPresent(
                            customerRepository::delete
                    );
        }

        if (tenantBCustomerId != null) {

            TenantContext.setTenantId("tenant-b");

            customerRepository
                    .findById(tenantBCustomerId)
                    .ifPresent(
                            customerRepository::delete
                    );
        }

        TenantContext.clear();
    }

    @Test
    void customerDataIsIsolatedBetweenTenantDatabases() {

        String unique =
                UUID.randomUUID().toString();

        String aliceEmail =
                "alice-" + unique + "@tenant-a.test";

        String bobEmail =
                "bob-" + unique + "@tenant-b.test";

        /*
         * TENANT A
         */
        TenantContext.setTenantId("tenant-a");

        Customer alice =
                new Customer(
                        "Alice",
                        "TenantA",
                        aliceEmail,
                        null
                );

        customerRepository.saveAndFlush(alice);

        tenantACustomerId = alice.getId();

        assertTrue(
                customerRepository
                        .findByEmail(aliceEmail)
                        .isPresent()
        );

        /*
         * TENANT B
         */
        TenantContext.setTenantId("tenant-b");

        assertTrue(
                customerRepository
                        .findByEmail(aliceEmail)
                        .isEmpty(),
                "Tenant B must not see Tenant A's customer"
        );

        Customer bob =
                new Customer(
                        "Bob",
                        "TenantB",
                        bobEmail,
                        null
                );

        customerRepository.saveAndFlush(bob);

        tenantBCustomerId = bob.getId();

        assertTrue(
                customerRepository
                        .findByEmail(bobEmail)
                        .isPresent()
        );

        /*
         * BACK TO TENANT A
         */
        TenantContext.setTenantId("tenant-a");

        Customer stored =
                customerRepository
                        .findByEmail(aliceEmail)
                        .orElseThrow();

        assertEquals(
                "Alice",
                stored.getFirstName()
        );

        assertTrue(
                customerRepository
                        .findByEmail(bobEmail)
                        .isEmpty(),
                "Tenant A must not see Tenant B's customer"
        );
    }
}