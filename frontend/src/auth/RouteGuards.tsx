import type { ReactNode } from "react";
import { Navigate, Outlet } from "react-router-dom";
import type { Capability } from "./permissions";
import { canAccessWorkspace, hasCapability } from "./permissions";
import UnsupportedWorkspaceRolePage from "../pages/UnsupportedWorkspaceRolePage";

export function RequireWorkspaceAccess() {
    return canAccessWorkspace() ? <Outlet /> : <UnsupportedWorkspaceRolePage />;
}

export function RequireCapability({
    capability,
    children
}: {
    capability: Capability;
    children: ReactNode;
}) {
    return hasCapability(capability) ? children : <Navigate to="/app" replace />;
}
