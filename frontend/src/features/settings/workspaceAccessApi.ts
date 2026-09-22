import { apiFetch } from "../../api/apiClient";
import type { WorkspaceRole } from "../../auth/permissions";

export interface WorkspaceMember {
    id: string;
    subject: string;
    email: string;
    role: WorkspaceRole;
    staffMemberId: string | null;
    status: "ACTIVE" | "SUSPENDED" | "REMOVED";
    createdAt: string;
}

export interface WorkspaceInvitation {
    id: string;
    email: string;
    role: WorkspaceRole;
    staffMemberId: string | null;
    status: "PENDING" | "ACCEPTED" | "EXPIRED" | "REVOKED";
    expiresAt: string;
    createdAt: string;
    acceptanceUrl: string | null;
}

export interface InvitationPreview {
    workspaceName: string;
    email: string;
    role: WorkspaceRole;
    status: string;
    expiresAt: string;
}

export const getWorkspaceMembers = () =>
    apiFetch<WorkspaceMember[]>("/api/v1/workspace-access/members");

export const getWorkspaceInvitations = () =>
    apiFetch<WorkspaceInvitation[]>(
        "/api/v1/workspace-access/invitations"
    );

export const createWorkspaceInvitation = (request: {
    email: string;
    role: WorkspaceRole;
    staffMemberId: string | null;
    expiresInDays: number;
}) =>
    apiFetch<WorkspaceInvitation>("/api/v1/workspace-access/invitations", {
        method: "POST",
        body: JSON.stringify(request)
    });

export const revokeWorkspaceInvitation = (id: string) =>
    apiFetch<void>(`/api/v1/workspace-access/invitations/${id}`, {
        method: "DELETE"
    });

export const changeWorkspaceRole = (id: string, role: WorkspaceRole) =>
    apiFetch<WorkspaceMember>(
        `/api/v1/workspace-access/members/${id}/role`,
        { method: "PATCH", body: JSON.stringify({ role }) }
    );

export const linkWorkspaceStaff = (
    id: string,
    staffMemberId: string | null
) =>
    apiFetch<WorkspaceMember>(
        `/api/v1/workspace-access/members/${id}/staff-link`,
        { method: "PATCH", body: JSON.stringify({ staffMemberId }) }
    );

export const removeWorkspaceMember = (id: string) =>
    apiFetch<void>(`/api/v1/workspace-access/members/${id}`, {
        method: "DELETE"
    });

export const getInvitationPreview = (token: string) =>
    apiFetch<InvitationPreview>(`/api/invitations/${token}`);

export const acceptWorkspaceInvitation = (token: string) =>
    apiFetch<{
        id: string;
        slug: string;
        name: string;
        role: WorkspaceRole;
        staffMemberId: string | null;
    }>(`/api/invitations/${token}/accept`, { method: "POST" });
