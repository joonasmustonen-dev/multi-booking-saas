import { Navigate, useSearchParams } from "react-router-dom";
export default function AvailabilityRedirect() {
    const [params] = useSearchParams(); const kind = params.get("kind"); const target = kind === "locations" || kind === "resources" ? kind : "staff"; const owner = params.get("ownerId");
    return <Navigate replace to={`/${target}${owner ? `?${new URLSearchParams({ ownerId: owner })}` : ""}`} />;
}
