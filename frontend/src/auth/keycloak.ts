import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
    url: "http://localhost:8081",
    realm: "booking",
    clientId: "booking-frontend"
});

export default keycloak;
