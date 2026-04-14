import { useRouterState } from "@tanstack/react-router";
import { Bell, Search, Menu } from "lucide-react";

const ROUTE_LABELS: Record<string, string> = {
  "/":          "Dashboard",
  "/vehicles":  "Vehicles",
  "/routes":    "Routes",
  "/stops":     "Stops",
  "/parks":     "Parks",
  "/tracking":  "Live Tracking",
};

interface NavbarProps {
  onMenuClick: () => void;
}

export function Navbar({ onMenuClick }: NavbarProps) {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const title = ROUTE_LABELS[pathname] ?? "Urban Transit";

  return (
    <header className="flex h-16 shrink-0 items-center justify-between border-b border-border bg-card px-4 lg:px-6">
      {/* Left: hamburger (mobile) + page title */}
      <div className="flex items-center gap-3">
        <button
          onClick={onMenuClick}
          className="lg:hidden flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground hover:bg-muted"
          aria-label="Open navigation"
        >
          <Menu className="h-5 w-5" />
        </button>
        <h1 className="text-base font-semibold text-foreground">{title}</h1>
      </div>

      {/* Right: search + bell + avatar */}
      <div className="flex items-center gap-2">
        {/* Search — hidden on small screens */}
        <div className="relative hidden md:flex items-center">
          <Search className="absolute left-3 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
          <input
            placeholder="Search…"
            className={[
              "h-8 w-48 rounded-md border border-border bg-muted/50",
              "pl-8 pr-3 text-sm text-foreground placeholder:text-muted-foreground",
              "focus:outline-none focus:ring-2 focus:ring-ring/40",
            ].join(" ")}
          />
        </div>

        {/* Bell */}
        <button
          aria-label="Notifications"
          className="relative flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted"
        >
          <Bell className="h-4 w-4" />
          {/* Live indicator dot */}
          <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-secondary-container ring-2 ring-card" />
        </button>

        {/* Avatar */}
        <div className="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground select-none">
          A
        </div>
      </div>
    </header>
  );
}
