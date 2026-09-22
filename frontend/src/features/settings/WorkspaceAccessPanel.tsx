import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Clipboard, ShieldCheck, Trash2, UserPlus } from "lucide-react";
import type { WorkspaceRole } from "../../auth/permissions";
import { getStaff } from "../staff/staffApi";
import {
    changeWorkspaceRole,
    createWorkspaceInvitation,
    getWorkspaceInvitations,
    getWorkspaceMembers,
    linkWorkspaceStaff,
    removeWorkspaceMember,
    revokeWorkspaceInvitation
} from "./workspaceAccessApi";

export default function WorkspaceAccessPanel() {
    const client = useQueryClient();
    const members = useQuery({
        queryKey: ["workspace-members"],
        queryFn: getWorkspaceMembers
    });
    const invitations = useQuery({
        queryKey: ["workspace-invitations"],
        queryFn: getWorkspaceInvitations
    });
    const staff = useQuery({ queryKey: ["staff"], queryFn: getStaff });
    const [email, setEmail] = useState("");
    const [role, setRole] = useState<WorkspaceRole>("STAFF");
    const [staffId, setStaffId] = useState("");
    const [inviteLink, setInviteLink] = useState("");
    const [copied, setCopied] = useState(false);
    const linkedStaffIds = new Set(
        (members.data ?? [])
            .map(member => member.staffMemberId)
            .filter((id): id is string => Boolean(id))
    );

    const refresh = async () => {
        await Promise.all([
            client.invalidateQueries({ queryKey: ["workspace-members"] }),
            client.invalidateQueries({ queryKey: ["workspace-invitations"] })
        ]);
    };
    const invite = useMutation({
        mutationFn: createWorkspaceInvitation,
        onSuccess: async created => {
            setInviteLink(created.acceptanceUrl ?? "");
            setEmail("");
            setStaffId("");
            await refresh();
        }
    });
    const change = useMutation({
        mutationFn: (action: () => Promise<unknown>) => action(),
        onSuccess: refresh
    });

    function submit(event: FormEvent) {
        event.preventDefault();
        invite.mutate({
            email: email.trim(),
            role,
            staffMemberId: staffId || null,
            expiresInDays: 7
        });
    }

    const error =
        members.error ??
        invitations.error ??
        staff.error ??
        invite.error ??
        change.error;

    return (
        <section className="card management-panel workspace-access-panel">
            <div className="section-heading">
                <div>
                    <h2>
                        <ShieldCheck size={20} /> Workspace access
                    </h2>
                    <p className="field-note">
                        Invite accounts, assign roles, and connect logins to
                        staff schedules. The person needs a Keycloak account
                        with the same verified email address.
                    </p>
                </div>
            </div>

            <form className="workspace-invite-form" onSubmit={submit}>
                <label className="form-field">
                    Email
                    <input
                        className="input"
                        type="email"
                        required
                        maxLength={254}
                        value={email}
                        onChange={event => setEmail(event.target.value)}
                    />
                </label>
                <label className="form-field">
                    Role
                    <select
                        className="input"
                        value={role}
                        onChange={event =>
                            setRole(event.target.value as WorkspaceRole)
                        }
                    >
                        <option value="STAFF">Staff</option>
                        <option value="TENANT_ADMIN">Administrator</option>
                    </select>
                </label>
                <label className="form-field">
                    Link staff record
                    <select
                        className="input"
                        value={staffId}
                        onChange={event => setStaffId(event.target.value)}
                    >
                        <option value="">No staff record</option>
                        {(staff.data ?? [])
                            .filter(member => !linkedStaffIds.has(member.id))
                            .map(member => (
                                <option key={member.id} value={member.id}>
                                    {member.name}
                                </option>
                            ))}
                    </select>
                </label>
                <button
                    className="button button-primary"
                    disabled={invite.isPending}
                >
                    <UserPlus size={16} />
                    {invite.isPending ? "Creating…" : "Create invitation"}
                </button>
            </form>

            {inviteLink && (
                <div className="booking-summary invite-link-result" role="status">
                    <div>
                        <strong>Invitation created</strong>
                        <p className="field-note">
                            Send this one-time link to the invited person. It is
                            shown only now.
                        </p>
                    </div>
                    <button
                        type="button"
                        className="button button-secondary"
                        onClick={async () => {
                            await navigator.clipboard.writeText(inviteLink);
                            setCopied(true);
                        }}
                    >
                        <Clipboard size={15} />
                        {copied ? "Copied" : "Copy invite link"}
                    </button>
                </div>
            )}

            {error && (
                <p className="form-error" role="alert">
                    {error.message}
                </p>
            )}

            <div className="workspace-access-section">
                <h3>Members</h3>
                {members.isPending ? (
                    <p className="field-note">Loading members…</p>
                ) : members.data?.length === 0 ? (
                    <p className="field-note">No managed members yet.</p>
                ) : (
                    <div className="workspace-access-list">
                        {members.data?.map(member => (
                            <article className="workspace-access-row" key={member.id}>
                                <div>
                                    <strong>{member.email || member.subject}</strong>
                                    <span className="field-note">
                                        {member.staffMemberId
                                            ? "Connected to a staff record"
                                            : "No staff record linked"}
                                    </span>
                                </div>
                                <select
                                    className="input compact-input"
                                    aria-label={`Role for ${member.email}`}
                                    value={member.role}
                                    disabled={change.isPending}
                                    onChange={event =>
                                        change.mutate(() =>
                                            changeWorkspaceRole(
                                                member.id,
                                                event.target.value as WorkspaceRole
                                            )
                                        )
                                    }
                                >
                                    <option value="STAFF">Staff</option>
                                    <option value="TENANT_ADMIN">Administrator</option>
                                </select>
                                <select
                                    className="input compact-input"
                                    aria-label={`Staff link for ${member.email}`}
                                    value={member.staffMemberId ?? ""}
                                    disabled={change.isPending}
                                    onChange={event =>
                                        change.mutate(() =>
                                            linkWorkspaceStaff(
                                                member.id,
                                                event.target.value || null
                                            )
                                        )
                                    }
                                >
                                    <option value="">No staff link</option>
                                    {(staff.data ?? [])
                                        .filter(person =>
                                            person.id ===
                                                member.staffMemberId ||
                                            !linkedStaffIds.has(
                                                person.id
                                            )
                                        )
                                        .map(person => (
                                            <option
                                                key={person.id}
                                                value={person.id}
                                            >
                                                {person.name}
                                            </option>
                                        ))}
                                </select>
                                <button
                                    type="button"
                                    className="button button-danger icon-action"
                                    aria-label={`Remove access for ${member.email}`}
                                    disabled={change.isPending}
                                    onClick={() => {
                                        if (
                                            window.confirm(
                                                `Remove workspace access for ${member.email}? Their staff and appointment history will remain.`
                                            )
                                        ) change.mutate(() =>
                                            removeWorkspaceMember(member.id)
                                        );
                                    }}
                                >
                                    <Trash2 size={15} />
                                </button>
                            </article>
                        ))}
                    </div>
                )}
            </div>

            <div className="workspace-access-section">
                <h3>Invitations</h3>
                <div className="workspace-access-list">
                    {(invitations.data ?? []).map(invitation => (
                        <article className="workspace-access-row" key={invitation.id}>
                            <div>
                                <strong>{invitation.email}</strong>
                                <span className="field-note">
                                    {invitation.role === "TENANT_ADMIN"
                                        ? "Administrator"
                                        : "Staff"} · {invitation.status.toLowerCase()}
                                </span>
                            </div>
                            <span className="requirement-pill">
                                {invitation.status}
                            </span>
                            {invitation.status === "PENDING" && (
                                <button
                                    type="button"
                                    className="button button-secondary"
                                    disabled={change.isPending}
                                    onClick={() =>
                                        change.mutate(() =>
                                            revokeWorkspaceInvitation(invitation.id)
                                        )
                                    }
                                >
                                    Revoke
                                </button>
                            )}
                        </article>
                    ))}
                    {!invitations.isPending && invitations.data?.length === 0 && (
                        <p className="field-note">No invitations yet.</p>
                    )}
                </div>
            </div>
        </section>
    );
}
