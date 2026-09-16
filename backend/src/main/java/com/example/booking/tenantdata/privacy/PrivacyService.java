package com.example.booking.tenantdata.privacy;

import com.example.booking.tenantdata.*;
import com.example.booking.tenantdata.customer.CustomerResponse;
import com.example.booking.tenantdata.appointment.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Service
@Transactional("tenantTransactionManager")
public class PrivacyService {

    @PersistenceContext(unitName = "tenant")
    private EntityManager entityManager;

    private final CustomerRepository customers;

    private final AppointmentRepository appointments;

    private final PrivacyPolicyRepository policies;

    public PrivacyService(
        CustomerRepository customers,
        AppointmentRepository appointments,
        PrivacyPolicyRepository policies
    ) {
        this.customers = customers;

        this.appointments = appointments;

        this.policies = policies;
    }

    public record Policy(
        @Min(0) @Max(36500) int customerRetentionDays,
        @Min(0) @Max(36500) int notesRetentionDays,
        @Min(0) @Max(36500) int staffRetentionDays,
        @Min(0) @Max(36500) int auditRetentionDays,
        boolean scheduledRetention
    ) {
        public Policy(int customers, int notes, int staff, int audit) {
            this(customers, notes, staff, audit, false);
        }
    }

    public record Controls(
        @NotNull Boolean processingRestricted,
        @NotNull Boolean legalHold
    ) {}

    public enum ErasureMode {
        CONTACT_DETAILS,
        ALL_DATA
    }

    public record ErasureRequest(
        @NotNull ErasureMode mode,
        @AssertTrue boolean confirmed
    ) {}

    public record Export(
        CustomerResponse customer,
        OffsetDateTime lastActivityAt,
        List<AppointmentResponse> appointments,
        int page,
        int totalPages,
        long totalAppointments,
        String revision
    ) {}

    public record Preview(
        Policy policy,
        int customers,
        int notes,
        int staffContacts,
        int auditEvents,
        String token,
        int customerBatchLimit
    ) {}

    public record ApplyRequest(
        @NotBlank String token,
        @AssertTrue boolean confirmed
    ) {}

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Policy policy() {
        PrivacyPolicy policy = policies.findById(1).orElseThrow();

        return new Policy(
            policy.customerRetentionDays,
            policy.notesRetentionDays,
            policy.staffRetentionDays,
            policy.auditRetentionDays,
            policy.scheduledRetention
        );
    }

    public Policy savePolicy(Policy request) {
        PrivacyPolicy policy = policies.lockPolicy();

        policy.customerRetentionDays = request.customerRetentionDays();

        policy.notesRetentionDays = request.notesRetentionDays();

        policy.staffRetentionDays = request.staffRetentionDays();

        policy.auditRetentionDays = request.auditRetentionDays();

        policy.scheduledRetention = request.scheduledRetention();

        return request;
    }

    public CustomerResponse controls(UUID id, Controls request) {
        policies.lockPolicy();

        Customer customer = lock(id);

        if (customer.getErasedAt() != null && !request.processingRestricted()) {
            throw conflict("Erased records cannot be reopened for processing");
        }
        customer.setPrivacy(
            request.processingRestricted(),
            request.legalHold()
        );

        customer.touch();

        return CustomerResponse.from(customer);
    }

    @Transactional(
        value = "tenantTransactionManager",
        readOnly = true,
        isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ
    )
    public Export export(UUID id, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 500) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid export page or size (1–500)"
            );
        }
        Customer customer = customers
            .findById(id)
            .orElseThrow(() -> notFound());

        var history = appointments.findByCustomer_Id(
            id,
            PageRequest.of(
                page,
                size,
                org.springframework.data.domain.Sort.by("id")
            )
        );

        String historyRevision = (String) entityManager
            .createNativeQuery(
                """
                SELECT md5(coalesce(string_agg(id::text || ':' || version::text, ',' ORDER BY id), ''))
                FROM appointments WHERE customer_id = :id
                """
            )
            .setParameter("id", id)
            .getSingleResult();

        String revision;

        try {
            revision = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    (
                        historyRevision +
                        CustomerResponse.from(customer) +
                        customer.getLastActivityAt()
                    ).getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        return new Export(
            CustomerResponse.from(customer),
            customer.getLastActivityAt(),
            history.stream().map(AppointmentResponse::from).toList(),
            page,
            history.getTotalPages(),
            history.getTotalElements(),
            revision
        );
    }

    public void erase(UUID id, ErasureRequest request) {
        policies.lockPolicy();

        Customer customer = lock(id);

        if (!request.confirmed()) throw conflict(
            "Confirm the erasure operation first"
        );
        if (customer.isLegalHold()) throw conflict(
            "Release the legal hold after review before erasing data"
        );
        // Serialize with booking creation/rescheduling through the customer row lock.
        // Increment appointment versions so concurrent updates cannot restore notes/statuses.
        entityManager
            .createQuery(
                "update Appointment a set a.status = :cancelled, a.version = a.version + 1 " +
                    "where a.customer.id = :id and a.status in :statuses"
            )
            .setParameter("cancelled", AppointmentStatus.CANCELLED)
            .setParameter("id", id)
            .setParameter(
                "statuses",
                Set.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)
            )
            .executeUpdate();

        if (request.mode() == ErasureMode.ALL_DATA) {
            entityManager
                .createQuery(
                    "delete from Appointment a where a.customer.id = :id"
                )
                .setParameter("id", id)
                .executeUpdate();

            // Bulk deletion leaves loaded appointments stale. Detach them before
            // deleting their customer so Hibernate cannot flush those references.
            entityManager.clear();

            entityManager
                .createQuery("delete from Customer c where c.id = :id")
                .setParameter("id", id)
                .executeUpdate();
        } else {
            entityManager
                .createNativeQuery(
                    """
                    UPDATE waitlist_offers SET status = 'REMOVED'
                    WHERE entry_id IN (
                        SELECT id FROM waitlist_entries
                        WHERE customer_id = :id AND status IN ('WAITING', 'OFFERED')
                    ) AND status = 'OFFERED'
                    """
                )
                .setParameter("id", id)
                .executeUpdate();

            entityManager
                .createNativeQuery(
                    """
                    UPDATE waitlist_entries SET status = 'REMOVED', updated_at = now(), version = version + 1
                    WHERE customer_id = :id AND status IN ('WAITING', 'OFFERED')
                    """
                )
                .setParameter("id", id)
                .executeUpdate();

            entityManager
                .createQuery(
                    "update Appointment a set a.notes = null, a.version = a.version + 1 where a.customer.id = :id"
                )
                .setParameter("id", id)
                .executeUpdate();

            customer.eraseContactDetails();
        }
        entityManager.flush();
    }

    private Customer lock(UUID id) {
        return customers.findForUpdate(id).orElseThrow(() -> notFound());
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Customer not found"
        );
    }

    private ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }

    private static final String ELIGIBLE_CUSTOMERS = """
    SELECT c.id FROM customers c
    WHERE c.erased_at IS NULL AND c.legal_hold = false
      AND c.last_activity_at < :cutoff AND c.created_at < :cutoff
      AND NOT EXISTS (SELECT 1 FROM appointments a WHERE a.customer_id = c.id
          AND (a.end_at >= :cutoff OR a.status IN ('PENDING', 'CONFIRMED')))
      AND NOT EXISTS (SELECT 1 FROM waitlist_entries w WHERE w.customer_id = c.id
          AND w.status IN ('WAITING', 'OFFERED'))
    ORDER BY c.id LIMIT 100
    """;

    private List<UUID> eligible(Policy policy) {
        if (policy.customerRetentionDays() == 0) return List.of();
        return entityManager
            .createNativeQuery(ELIGIBLE_CUSTOMERS, UUID.class)
            .setParameter(
                "cutoff",
                OffsetDateTime.now().minusDays(policy.customerRetentionDays())
            )
            .getResultList();
    }

    private List<UUID> noteIds(Policy policy) {
        if (policy.notesRetentionDays() == 0) return List.of();
        return entityManager
            .createNativeQuery(
                """
                SELECT a.id FROM appointments a JOIN customers c ON c.id = a.customer_id
                WHERE a.notes IS NOT NULL AND c.legal_hold = false
                  AND a.status NOT IN ('PENDING', 'CONFIRMED') AND a.end_at < :cutoff
                ORDER BY a.id LIMIT 1000
                """,
                UUID.class
            )
            .setParameter(
                "cutoff",
                OffsetDateTime.now().minusDays(policy.notesRetentionDays())
            )
            .getResultList();
    }

    private List<UUID> staffIds(Policy policy) {
        if (policy.staffRetentionDays() == 0) return List.of();
        return entityManager
            .createNativeQuery(
                """
                SELECT s.id FROM staff_members s WHERE s.removed = true AND s.removed_at < :cutoff
                  AND (s.email <> '' OR s.phone <> '')
                  AND NOT EXISTS (SELECT 1 FROM appointments a JOIN customers c ON c.id = a.customer_id
                      WHERE a.staff_id = s.id AND c.legal_hold = true)
                ORDER BY s.id LIMIT 1000
                """,
                UUID.class
            )
            .setParameter(
                "cutoff",
                OffsetDateTime.now().minusDays(policy.staffRetentionDays())
            )
            .getResultList();
    }

    private List<UUID> auditIds(Policy policy) {
        if (policy.auditRetentionDays() == 0) return List.of();
        return entityManager
            .createNativeQuery(
                """
                SELECT e.id FROM security_audit_event e WHERE e.occurred_at < :cutoff
                  AND NOT EXISTS (SELECT 1 FROM customers c WHERE c.id = e.record_id AND c.legal_hold = true)
                  AND NOT EXISTS (
                      SELECT 1 FROM waitlist_entries w
                      JOIN customers c ON c.id = w.customer_id
                      WHERE w.id = e.record_id AND c.legal_hold = true
                  )
                ORDER BY e.id LIMIT 1000
                """,
                UUID.class
            )
            .setParameter(
                "cutoff",
                OffsetDateTime.now().minusDays(policy.auditRetentionDays())
            )
            .getResultList();
    }

    private String token(
        Policy policy,
        List<UUID> customerIds,
        List<UUID> notes,
        List<UUID> staff,
        List<UUID> audit
    ) {
        try {
            String input =
                com.example.booking.tenant.TenantContext.getTenantId() +
                ":" +
                LocalDate.now() +
                policy +
                customerIds +
                notes +
                staff +
                audit;

            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    input.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Preview preview() {
        Policy policy = policy();

        var ids = eligible(policy);

        var notes = noteIds(policy);

        var staff = staffIds(policy);

        var audit = auditIds(policy);

        return new Preview(
            policy,
            ids.size(),
            notes.size(),
            staff.size(),
            audit.size(),
            token(policy, ids, notes, staff, audit),
            100
        );
    }

    public Preview apply(ApplyRequest request) {
        policies.lockPolicy();

        Policy policy = policy();

        var ids = eligible(policy);

        var notes = noteIds(policy);

        var staff = staffIds(policy);

        var audit = auditIds(policy);

        if (
            !request.confirmed() ||
            !token(policy, ids, notes, staff, audit).equals(request.token())
        ) {
            throw conflict(
                "Retention candidates or policy changed. Preview again before applying."
            );
        }
        int erased = 0;

        for (UUID id : ids) {
            Customer customer = lock(id);

            // Revalidate after locking against a concurrent booking, edit or legal hold.
            if (
                customer.isLegalHold() ||
                customer.getErasedAt() != null ||
                customer
                    .getLastActivityAt()
                    .isAfter(
                        OffsetDateTime.now().minusDays(
                            policy.customerRetentionDays()
                        )
                    )
            ) continue;
            boolean recent =
                (
                    (Number) entityManager
                        .createNativeQuery(
                            "SELECT count(*) FROM appointments " +
                                "WHERE customer_id = :id AND (end_at >= :cutoff OR status IN ('PENDING', 'CONFIRMED'))"
                        )
                        .setParameter("id", id)
                        .setParameter(
                            "cutoff",
                            OffsetDateTime.now().minusDays(
                                policy.customerRetentionDays()
                            )
                        )
                        .getSingleResult()
                ).longValue() > 0;

            if (recent) continue;
            erase(id, new ErasureRequest(ErasureMode.CONTACT_DETAILS, true));

            erased++;
        }
        int clearedNotes = 0;

        for (UUID id : notes) {
            // Lock the same customer row used by legal-hold changes before deleting notes.
            var booking = appointments.findById(id).orElse(null);

            if (booking == null) continue;
            Customer customer = lock(booking.getCustomer().getId());

            if (!customer.isLegalHold()) {
                clearedNotes += entityManager
                    .createQuery(
                        "update Appointment a set a.notes = null, a.version = a.version + 1 where a.id = :id"
                    )
                    .setParameter("id", id)
                    .executeUpdate();
            }
        }
        int clearedStaff = staff.isEmpty()
            ? 0
            : entityManager
                  .createQuery(
                      "update StaffMember s set s.email = '', s.phone = '' where s.id in :ids"
                  )
                  .setParameter("ids", staff)
                  .executeUpdate();

        int clearedAudit = audit.isEmpty()
            ? 0
            : entityManager
                  .createQuery(
                      "delete from SecurityAuditEvent e where e.id in :ids"
                  )
                  .setParameter("ids", audit)
                  .executeUpdate();

        return new Preview(
            policy,
            erased,
            clearedNotes,
            clearedStaff,
            clearedAudit,
            request.token(),
            100
        );
    }
}
