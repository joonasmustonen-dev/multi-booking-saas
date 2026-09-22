import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";

import App from "./App";
import keycloak from "./auth/keycloak";
import { initializeWorkspaceSession } from "./auth/workspaceSession";
import "./index.css";
import "./features/customers/CustomerStyles.css";
import "./components/InteractionStyles.css";
import "./features/assignments/AssignmentStyles.css";
import "./components/ManagementRefinement.css";
import "./theme-dark.css";

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            retry: 1,
            staleTime: 30_000
        }
    }
});

function render() {
    createRoot(document.getElementById("root")!).render(
        <StrictMode>
            <QueryClientProvider client={queryClient}>
                <BrowserRouter>
                    <App />
                </BrowserRouter>
            </QueryClientProvider>
        </StrictMode>
    );
}

if (window.location.pathname === "/") {
    render();
} else {
    keycloak
        .init({
            onLoad: "login-required",
            pkceMethod: "S256",
            checkLoginIframe: false
        })
        .then(async authenticated => {
            if (authenticated) {
                await initializeWorkspaceSession();
                render();
            }
        })
        .catch(error => {
            console.error("Keycloak initialization failed", error);
        });
}
