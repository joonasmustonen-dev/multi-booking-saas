import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";

import App from "./App";
import keycloak from "./auth/keycloak";
import "./index.css";
import "./features/customers/CustomerStyles.css";
import "./components/InteractionStyles.css";
import "./features/assignments/AssignmentStyles.css";
import "./components/ManagementRefinement.css";

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            retry: 1,
            staleTime: 30_000
        }
    }
});

keycloak
    .init({
        onLoad: "login-required",
        pkceMethod: "S256",
        checkLoginIframe: false
    })
    .then(authenticated => {
        if (!authenticated) {
            return;
        }

        createRoot(document.getElementById("root")!).render(
            <StrictMode>
                <QueryClientProvider client={queryClient}>
                    <BrowserRouter>
                        <App />
                    </BrowserRouter>
                </QueryClientProvider>
            </StrictMode>
        );
    })
    .catch(error => {
        console.error("Keycloak initialization failed", error);
    });
