CREATE TABLE workspace_memberships (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    identity_subject varchar(255) NOT NULL,
    email varchar(254) NOT NULL DEFAULT '',
    role varchar(32) NOT NULL CHECK (role IN ('TENANT_ADMIN', 'STAFF')),
    staff_member_id uuid,
    status varchar(32) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REMOVED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    removed_at timestamptz,
    UNIQUE (tenant_id, identity_subject)
);

CREATE UNIQUE INDEX workspace_membership_staff_link_idx
    ON workspace_memberships (tenant_id, staff_member_id)
    WHERE staff_member_id IS NOT NULL AND status <> 'REMOVED';

CREATE INDEX workspace_membership_subject_idx
    ON workspace_memberships (identity_subject, status);

CREATE UNIQUE INDEX workspace_membership_active_email_idx
    ON workspace_memberships (tenant_id, lower(email))
    WHERE status = 'ACTIVE';

CREATE TABLE workspace_invitations (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    normalized_email varchar(254) NOT NULL,
    role varchar(32) NOT NULL CHECK (role IN ('TENANT_ADMIN', 'STAFF')),
    staff_member_id uuid,
    token_hash varchar(64) NOT NULL UNIQUE,
    status varchar(32) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')),
    expires_at timestamptz NOT NULL,
    invited_by_subject varchar(255) NOT NULL,
    accepted_by_subject varchar(255),
    created_at timestamptz NOT NULL,
    accepted_at timestamptz,
    revoked_at timestamptz
);

CREATE UNIQUE INDEX workspace_invitation_pending_email_idx
    ON workspace_invitations (tenant_id, normalized_email)
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX workspace_invitation_pending_staff_idx
    ON workspace_invitations (tenant_id, staff_member_id)
    WHERE staff_member_id IS NOT NULL AND status = 'PENDING';

CREATE TABLE workspace_access_audit (
    id uuid PRIMARY KEY,
    tenant_id uuid REFERENCES tenants(id),
    occurred_at timestamptz NOT NULL,
    actor_subject varchar(255) NOT NULL,
    action varchar(80) NOT NULL,
    target_subject varchar(255),
    invitation_id uuid,
    detail varchar(500) NOT NULL DEFAULT ''
);

CREATE INDEX workspace_access_audit_tenant_time_idx
    ON workspace_access_audit (tenant_id, occurred_at DESC);
