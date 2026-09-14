package com.example.booking.tenantdata.privacy;

import jakarta.persistence.*;

@Entity
@Table(name = "privacy_policy")
public class PrivacyPolicy {

    @Id
    Integer id = 1;

    int customerRetentionDays;

    int notesRetentionDays;

    int staffRetentionDays;

    int auditRetentionDays;

    boolean scheduledRetention;

    protected PrivacyPolicy() {}
}
