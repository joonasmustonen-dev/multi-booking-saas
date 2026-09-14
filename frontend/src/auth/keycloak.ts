import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
    url: import.meta.env?.VITE_KEYCLOAK_URL ?? "http://localhost:8081",
    realm: import.meta.env?.VITE_KEYCLOAK_REALM ?? "booking",
    clientId: import.meta.env?.VITE_KEYCLOAK_CLIENT_ID ?? "booking-frontend"
});

export default keycloak;
