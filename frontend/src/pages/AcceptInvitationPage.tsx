import { useMutation, useQuery } from "@tanstack/react-query";
import { ShieldCheck } from "lucide-react";
import { selectWorkspace } from "../auth/workspaceSession";
import {
    acceptWorkspaceInvitation,
    getInvitationPreview
} from "../features/settings/workspaceAccessApi";

export default function AcceptInvitationPage() {
    const token = new URLSearchParams(window.location.search).get("token") ?? "";
    const preview = useQuery({
        queryKey: ["invitation-preview", token],
        queryFn: () => getInvitationPreview(token),
        enabled: token.length > 0,
        retry: false
    });
    const accept = useMutation({
        mutationFn: () => acceptWorkspaceInvitation(token),
        onSuccess: workspace => {
            selectWorkspace(workspace);
            window.location.assign("/app");
        }
    });

    return (
        <main className="access-denied-page">
            <section className="access-denied-card invitation-accept-card">
                <span className="access-denied-icon">
                    <ShieldCheck size={30} />
                </span>
                <p className="page-eyebrow">Workspace invitation</p>
                <h1>Join the workspace</h1>
                {!token ? (
                    <p className="form-error">The invitation link is incomplete.</p>
                ) : preview.isPending ? (
                    <p>Checking your invitation…</p>
                ) : preview.error ? (
                    <p className="form-error" role="alert">
                        {preview.error.message}
                    </p>
                ) : (
                    <>
                        <p>
                            You have been invited to <strong>{preview.data.workspaceName}</strong>{" "}
                            as {preview.data.role === "TENANT_ADMIN" ? "an administrator" : "staff"}.
                        </p>
                        <p className="field-note">
                            This invitation is for {preview.data.email}. You must
                            be signed in with that address.
                        </p>
                        <button
                            className="button button-primary"
                            disabled={accept.isPending}
                            onClick={() => accept.mutate()}
                        >
                            {accept.isPending ? "Joining…" : "Accept invitation"}
                        </button>
                    </>
                )}
                {accept.error && (
                    <p className="form-error" role="alert">
                        {accept.error.message}
                    </p>
                )}
            </section>
        </main>
    );
}
