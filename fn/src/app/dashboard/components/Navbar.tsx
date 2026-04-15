import { useRouterState } from "@tanstack/react-router";
import { Bell, Search, Menu } from "lucide-react";

const ROUTE_META: Record<string, { title: string; description: string }> = {
  "/":          { title: "Dashboard",     description: "Overview of your transit network" },
  "/vehicles":  { title: "Vehicles",      description: "Manage your fleet" },
  "/routes":    { title: "Routes",        description: "Configure transit routes" },
  "/stops":     { title: "Stops",         description: "Manage stops & stations" },
  "/parks":     { title: "Parks",         description: "Park & ride locations" },
  "/tracking":  { title: "Live Tracking", description: "Real-time vehicle positions" },
};

interface NavbarProps {
  onMenuClick: () => void;
}

export function Navbar({ onMenuClick }: NavbarProps) {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const meta = ROUTE_META[pathname] ?? { title: "Urban Transit", description: "" };

  return (
    <header className="flex h-[60px] shrink-0 items-center justify-between border-b border-border bg-card px-4 lg:px-6">

      {/* Left: hamburger + page title */}
      <div className="flex items-center gap-3">
        {/* Mobile menu */}
        <button
          onClick={onMenuClick}
          className="lg:hidden flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground hover:bg-muted transition-colors"
          aria-label="Open navigation"
        >
          <Menu className="h-4 w-4" />
        </button>

        {/* Page title + breadcrumb description */}
        <div className="flex flex-col leading-tight">
          <h1 className="text-sm font-semibold text-foreground">{meta.title}</h1>
          <p className="hidden sm:block text-[11px] text-muted-foreground">
            {meta.description}
          </p>
        </div>
      </div>

      {/* Right: search + actions */}
      <div className="flex items-center gap-2">

        {/* Search bar — hidden on mobile */}
        <div className="relative hidden md:flex items-center">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
          <input
            placeholder="Search anything…"
            className={[
              "h-8 w-52 rounded-lg border border-border bg-muted/40",
              "pl-9 pr-3 text-sm text-foreground placeholder:text-muted-foreground/60",
              "transition-all focus:w-64 focus:bg-card focus:outline-none focus:ring-2 focus:ring-ring/30",
            ].join(" ")}
          />
        </div>

        {/* Notification bell */}
        <button
          aria-label="Notifications"
          className="relative flex h-8 w-8 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          <Bell className="h-4 w-4" />
          {/* Amber live-indicator dot */}
          <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-secondary-container ring-[1.5px] ring-card pulse-live" />
        </button>

        {/* Divider */}
        <div className="mx-1 h-5 w-px bg-border" />

        {/* User avatar */}
        <button
          aria-label="User menu"
          className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground hover:opacity-90 transition-opacity"
        >
          A
        </button>
      </div>
    </header>
  );
}
