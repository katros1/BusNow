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

import RoutesModule from "./app/routes";
import NewRoute from "./app/routes/pages/NewRoute";
import EditRoute from "./app/routes/pages/EditRoute";

const routesRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/routes",
  component: RoutesModule,
});

const newRouteRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/routes/new",
  component: NewRoute,
});

const editRouteRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/routes/$routeId/edit",
  component: EditRoute,
});

import EditStop from "./app/stops/pages/EditStop";
import EditPark from "./app/parks/pages/EditPark";

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

const editStopRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/stops/$stopId/edit",
  component: EditStop,
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

const editParkRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/parks/$parkId/edit",
  component: EditPark,
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
    newRouteRoute,
    editRouteRoute,
    stopsRoute,
    newStopRoute,
    editStopRoute,
    parksRoute,
    newParkRoute,
    editParkRoute,
    trackingRoute,
  ]),
]);

export const router = createRouter({ routeTree });

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}