package com.example.booking.tenantdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    @org.springframework.data.jpa.repository.Lock(
        jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
    )
    @org.springframework.data.jpa.repository.Query(
        "select c from Customer c where c.id = :id"
    )
    Optional<Customer> findForUpdate(
        @org.springframework.data.repository.query.Param("id") UUID id
    );

    @org.springframework.data.jpa.repository.Query(
        value = """
        SELECT c.* FROM customers c
        WHERE c.erased_at IS NULL AND (:includeRestricted OR c.processing_restricted = false)
          AND (lower(concat_ws(' ', c.first_name, c.last_name)) LIKE :text ESCAPE '\\'
           OR lower(coalesce(c.email, '')) LIKE :text ESCAPE '\\'
           OR (:phone <> '' AND regexp_replace(coalesce(c.phone, ''), '[^0-9]', '', 'g') LIKE :phone))
        ORDER BY lower(c.last_name), lower(c.first_name), c.id
        """,
        nativeQuery = true
    )
    java.util.List<Customer> search(
        @org.springframework.data.repository.query.Param("text") String text,
        @org.springframework.data.repository.query.Param("phone") String phone,
        @org.springframework.data.repository.query.Param(
            "includeRestricted"
        ) boolean includeRestricted,
        org.springframework.data.domain.Pageable pageable
    );

    Optional<Customer> findByEmail(String email);

    @org.springframework.data.jpa.repository.Query(
        "select c from Customer c where c.erasedAt is null " +
            "and (:includeRestricted = true or c.processingRestricted = false)"
    )
    org.springframework.data.domain.Page<Customer> findDirectory(
        @org.springframework.data.repository.query.Param(
            "includeRestricted"
        ) boolean includeRestricted,
        org.springframework.data.domain.Pageable pageable
    );
}
