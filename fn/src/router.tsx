import {createRootRoute, createRoute, createRouter, Outlet} from "@tanstack/react-router";
import Vehicles from "./app/vehicles";
import Dashboard from "./app/dashboard";

const rootRouter = createRootRoute({
    component: () => (
        <div>
            <Outlet/>
        </div>
    ),
})

const dashboardRoute = createRoute({
    getParentRoute: () => rootRouter,
    path:"/",
    component: Dashboard,
})

const vehiclesRoute = createRoute({
    getParentRoute: () => rootRouter,
    path:"/vehicle",
    component: Vehicles,
})

const routeTree = rootRouter.addChildren([dashboardRoute, vehiclesRoute])

export const router = createRouter({
    routeTree,
})

declare module "@tanstack/react-router" {
    export interface Register {
        router: typeof router
    }
}