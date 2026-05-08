import {
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  redirect,
} from "@tanstack/react-router";
import { DashboardLayout } from "./app/dashboard/layout";
import Dashboard from "./app/dashboard";
import Drivers from "./app/drivers";
import Buses from "./app/buses";
import Stops from "./app/stops";
import NewStop from "./app/stops/pages/NewStop";
import Parks from "./app/parks";
import NewPark from "./app/parks/pages/NewPark";
import Login from "./app/auth/pages/Login";

// ── Root ──────────────────────────────────────────────────────
const rootRoute = createRootRoute({ component: () => <Outlet /> });

// ── Auth ──────────────────────────────────────────────────────
const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/login",
  component: Login,
});

// ── Layout shell (protected — wraps dashboard pages) ──────────
const layoutRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "layout",
  component: DashboardLayout,
  beforeLoad: async ({ location }) => {
    // We check localStorage directly for the 'beforeLoad' check 
    // because useAuth is a hook and can't be used here easily.
    // TanStack Router context could be used for a cleaner approach.
    const user = localStorage.getItem("oidc.user:http://localhost:1001/realms/iots-client:iots-client");
    if (!user && location.pathname !== "/login") {
      throw redirect({ to: "/login" });
    }
  },
});

// ── Pages ──────────────────────────────────────────────────────
const dashboardRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/",
  component: Dashboard,
});

const driversRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/drivers",
  component: Drivers,
});

const busesRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/buses",
  component: Buses,
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

import TrackingOverview from "./app/tracking/pages/Index";
import VehicleTracking from "./app/tracking/pages/VehicleTracking";

const trackingRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/tracking",
  component: TrackingOverview,
});

const vehicleTrackingRoute = createRoute({
  getParentRoute: () => layoutRoute,
  path: "/tracking/$busId",
  component: VehicleTracking,
});

// ── Route tree ─────────────────────────────────────────────────
const routeTree = rootRoute.addChildren([
  loginRoute,
  layoutRoute.addChildren([
    dashboardRoute,
    driversRoute,
    busesRoute,
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
    vehicleTrackingRoute,
  ]),
]);

export const router = createRouter({ routeTree });

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}
