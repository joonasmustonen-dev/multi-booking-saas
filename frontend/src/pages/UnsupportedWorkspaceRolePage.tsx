import { LogOut, ShieldX } from "lucide-react";
import keycloak from "../auth/keycloak";
import { primaryWorkspaceRole } from "../auth/permissions";

export default function UnsupportedWorkspaceRolePage() {
    const customer = primaryWorkspaceRole() === "CUSTOMER";

    return (
        <main className="access-denied-page">
            <section className="access-denied-card">
                <span className="access-denied-icon">
                    <ShieldX size={30} />
                </span>
                <p className="page-eyebrow">Operational workspace</p>
                <h1>Workspace access unavailable</h1>
                <p>
                    {customer
                        ? "Customer accounts cannot use this operational application. It is reserved for tenant administrators and staff."
                        : "Your account does not have an administrator or staff role for this workspace."}
                </p>
                <button
                    className="button button-primary"
                    onClick={() =>
                        keycloak.logout({ redirectUri: window.location.origin })
                    }
                >
                    <LogOut size={17} />
                    Sign out
                </button>
            </section>
        </main>
    );
}
