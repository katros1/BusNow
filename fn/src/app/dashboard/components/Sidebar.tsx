import { Link, useRouterState } from "@tanstack/react-router";
import {
  LayoutDashboard,
  MapPin,
  ParkingCircle,
  Bus,
  Route,
  RadioTower,
  HelpCircle,
  LogOut,
  X,
  Zap,
} from "lucide-react";
import { cn } from "@/lib/utils";

const NAV_MAIN = [
  { to: "/",          label: "Dashboard",     icon: LayoutDashboard },
  { to: "/stops",     label: "Stops",         icon: MapPin          },
  { to: "/parks",     label: "Parks",         icon: ParkingCircle   },
  { to: "/vehicles",  label: "Vehicles",      icon: Bus             },
  { to: "/routes",    label: "Routes",        icon: Route           },
  { to: "/tracking",  label: "Live Tracking", icon: RadioTower      },
] as const;

const NAV_BOTTOM = [
  { label: "Support",  icon: HelpCircle },
  { label: "Sign Out", icon: LogOut     },
] as const;

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

export function Sidebar({ open, onClose }: SidebarProps) {
  const pathname = useRouterState({ select: (s) => s.location.pathname });

  return (
    <>
      {/* Mobile backdrop */}
      {open && (
        <div
          className="fixed inset-0 z-20 bg-black/40 lg:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside
        className={cn(
          // Base
          "fixed inset-y-0 left-0 z-30 flex w-64 flex-col bg-sidebar",
          "border-r border-sidebar-border",
          // Transition
          "transition-transform duration-200 ease-in-out",
          // Mobile: slide in/out. Desktop: always visible
          open ? "translate-x-0" : "-translate-x-full",
          "lg:static lg:translate-x-0"
        )}
      >
        {/* ── Brand ─────────────────────────────────────────── */}
        <div className="flex h-16 shrink-0 items-center justify-between px-5 border-b border-sidebar-border">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary">
              <Zap className="h-4 w-4 text-primary-foreground" />
            </div>
            <div className="leading-tight">
              <p className="text-xs font-bold uppercase tracking-widest text-sidebar-foreground">
                Urban Kinetic
              </p>
              <p className="text-[10px] text-muted-foreground">Transit Admin</p>
            </div>
          </div>
          {/* Close — mobile only */}
          <button
            onClick={onClose}
            className="lg:hidden rounded-md p-1 text-muted-foreground hover:bg-muted"
            aria-label="Close sidebar"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* ── Main nav ──────────────────────────────────────── */}
        <nav className="flex flex-1 flex-col gap-0.5 overflow-y-auto px-3 py-4">
          {NAV_MAIN.map(({ to, label, icon: Icon }) => {
            const active = to === "/" ? pathname === "/" : pathname.startsWith(to);
            return (
              <Link
                key={to}
                to={to}
                onClick={onClose}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                  active
                    ? "bg-primary/10 text-primary"
                    : "text-sidebar-foreground hover:bg-muted hover:text-foreground"
                )}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span>{label}</span>
              </Link>
            );
          })}
        </nav>

        {/* ── Bottom section ─────────────────────────────────── */}
        <div className="shrink-0 border-t border-sidebar-border px-3 py-3 space-y-0.5">
          {NAV_BOTTOM.map(({ label, icon: Icon }) => (
            <button
              key={label}
              className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-sidebar-foreground transition-colors hover:bg-muted hover:text-foreground"
            >
              <Icon className="h-4 w-4 shrink-0" />
              <span>{label}</span>
            </button>
          ))}
        </div>
      </aside>
    </>
  );
}