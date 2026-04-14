import { useRouterState } from "@tanstack/react-router";
import { Bell, Search } from "lucide-react";

const ROUTE_LABELS: Record<string, string> = {
  "/":          "Dashboard",
  "/vehicles":  "Vehicles",
  "/routes":    "Routes",
  "/stops":     "Stops",
  "/parks":     "Parks",
  "/tracking":  "Live Tracking",
};

export function Navbar() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const title = ROUTE_LABELS[pathname] ?? "Urban Transit";

  return (
    <header className="flex h-16 shrink-0 items-center justify-between border-b border-border bg-card px-6">
      <h1 className="text-base font-semibold text-foreground">{title}</h1>

      <div className="flex items-center gap-3">
        {/* Search */}
        <div className="relative hidden sm:flex items-center">
          <Search className="absolute left-3 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
          <input
            placeholder="Search…"
            className="h-8 w-52 rounded-md border border-border bg-background pl-8 pr-3 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring/50"
          />
        </div>

        {/* Notifications */}
        <button
          aria-label="Notifications"
          className="relative flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          <Bell className="h-4 w-4" />
          <span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-secondary-container" />
        </button>

        {/* Avatar */}
        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-xs font-semibold text-primary-foreground select-none">
          A
        </div>
      </div>
    </header>
  );
}
