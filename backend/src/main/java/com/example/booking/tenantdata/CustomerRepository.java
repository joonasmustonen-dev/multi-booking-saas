package com.example.booking.tenantdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository
        extends JpaRepository<Customer, UUID> {

    @org.springframework.data.jpa.repository.Query(value = """
        SELECT c.* FROM customers c
        WHERE lower(concat_ws(' ', c.first_name, c.last_name)) LIKE :text ESCAPE '\\'
           OR lower(coalesce(c.email, '')) LIKE :text ESCAPE '\\'
           OR (:phone <> '' AND regexp_replace(coalesce(c.phone, ''), '[^0-9]', '', 'g') LIKE :phone)
        ORDER BY lower(c.last_name), lower(c.first_name), c.id
        """, nativeQuery = true)
    java.util.List<Customer> search(@org.springframework.data.repository.query.Param("text") String text,
            @org.springframework.data.repository.query.Param("phone") String phone,
            org.springframework.data.domain.Pageable pageable);

    Optional<Customer> findByEmail(String email);
}