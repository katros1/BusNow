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
import Stops from "./app/stops";
import NewStop from "./app/stops/pages/NewStop";
import Parks from "./app/parks";
import NewPark from "./app/parks/pages/NewPark";

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
  component: Stops,
});

const newStopRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/stops/new",
  component: NewStop,
});

const parksRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/parks",
  component: Parks,
});

const newParkRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/parks/new",
  component: NewPark,
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
    newStopRoute,
    newParkRoute,
    trackingRoute,
  ]),
]);

export const router = createRouter({ routeTree });

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}