import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
} from "@tanstack/react-router";
import { DashboardLayout } from "./app/dashboard/layout";
import Dashboard from "./app/dashboard";
import { Placeholder } from "@/components/feedback/Placeholder";
import Vehicles from "./app/vehicles";

// ── Root ──────────────────────────────────────────────────────
const rootRoute = createRootRoute({ component: () => <Outlet /> });

// ── Layout shell (pathless — wraps all pages) ──────────────────
const layoutRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "layout",
  component: DashboardLayout,
});

// ── Pages ──────────────────────────────────────────────────────
const dashboardRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/",
  component: Dashboard,
});

const vehiclesRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/vehicles",
  component: Vehicles,
});

const routesRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/routes",
  component: () => <Placeholder name="Routes" />,
});

const stopsRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/stops",
  component: () => <Placeholder name="Stops" />,
});

const parksRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/parks",
  component: () => <Placeholder name="Parks" />,
});

const trackingRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/tracking",
  component: () => <Placeholder name="Live Tracking" />,
});

// ── Route tree ─────────────────────────────────────────────────
const routeTree = rootRoute.addChildren([
  layoutRoute.addChildren([
    dashboardRoute,
    vehiclesRoute,
    routesRoute,
    stopsRoute,
    parksRoute,
    trackingRoute,
  ]),
]);

export const router = createRouter({ routeTree });

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}