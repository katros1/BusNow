import { Link, useRouterState } from "@tanstack/react-router";
import {
  LayoutDashboard,
  MapPin,
  ParkingCircle,
  Bus,
  UserRound,
  Route,
  RadioTower,
  HelpCircle,
  Settings,
  LogOut,
  X,
  Zap,
  PanelLeftClose,
  PanelLeftOpen,
  ChevronRight,
} from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";

// ── Nav structure ─────────────────────────────────────────────

const NAV_GROUPS = [
  {
    label: "Overview",
    items: [
      { to: "/",         label: "Dashboard",     icon: LayoutDashboard, badge: null },
      { to: "/tracking", label: "Live Tracking", icon: RadioTower,      badge: "live" },
    ],
  },
  {
    label: "Management",
    items: [
      { to: "/buses",    label: "Buses",     icon: Bus,           badge: null },
      { to: "/drivers",  label: "Drivers",   icon: UserRound,     badge: null },
      { to: "/routes",   label: "Routes",    icon: Route,         badge: null },
      { to: "/stops",    label: "Stops",     icon: MapPin,        badge: null },
      { to: "/parks",    label: "Parks",     icon: ParkingCircle, badge: null },
    ],
  },
] as const;

// ── Tooltip (CSS-only) ────────────────────────────────────────

function NavTooltip({ label, show, children }: {
  label: string;
  show: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="relative group/tip">
      {children}
      {show && (
        <span className={cn(
          "pointer-events-none absolute left-full top-1/2 z-50 ml-3 -translate-y-1/2",
          "whitespace-nowrap rounded-lg bg-[#252A38] px-2.5 py-1.5",
          "text-[11px] font-medium text-white shadow-ambient-md",
          "opacity-0 transition-opacity duration-150 group-hover/tip:opacity-100"
        )}>
          {label}
        </span>
      )}
    </div>
  );
}

// ── Props ─────────────────────────────────────────────────────

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

// ── Component ─────────────────────────────────────────────────

export function Sidebar({ open, onClose }: SidebarProps) {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const [collapsed, setCollapsed] = useState(false);

  const isActive = (to: string) =>
    to === "/" ? pathname === "/" : pathname.startsWith(to);

  return (
    <>
      {/* Mobile backdrop */}
      {open && (
        <div
          className="fixed inset-0 z-20 bg-black/25 backdrop-blur-[2px] lg:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-30 flex flex-col",
          // Pure white — simple, clean
          "bg-sidebar border-r border-border/60",
          "shadow-[1px_0_0_0_rgba(64,96,147,0.06)]",
          "transition-[width] duration-200 ease-in-out",
          collapsed ? "lg:w-[68px]" : "lg:w-[240px]",
          "w-[240px]",
          open ? "translate-x-0" : "-translate-x-full",
          "lg:static lg:translate-x-0"
        )}
      >

        {/* ── Brand header ──────────────────────────────────── */}
        <div className="flex h-[60px] shrink-0 items-center border-b border-border/60 px-3 gap-2.5">
          {/* Logo mark — solid primary, no gradient */}
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary shadow-ambient">
            <Zap className="h-4 w-4 text-white" />
          </div>

          {/* App name */}
          <div className={cn(
            "flex-1 overflow-hidden transition-all duration-200",
            collapsed ? "lg:w-0 lg:opacity-0" : "opacity-100"
          )}>
            <p className="truncate text-sm font-bold tracking-tight text-primary">
              iots
            </p>
            <p className="truncate text-[10px] font-medium text-muted-foreground">
              Transit Admin
            </p>
          </div>

          {/* Desktop collapse button */}
          <button
            onClick={() => setCollapsed((c) => !c)}
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            className={cn(
              "hidden lg:flex h-7 w-7 shrink-0 items-center justify-center rounded-md",
              "text-muted-foreground/70 transition-colors hover:bg-muted hover:text-foreground",
            )}
          >
            {collapsed
              ? <PanelLeftOpen  className="h-3.5 w-3.5" />
              : <PanelLeftClose className="h-3.5 w-3.5" />
            }
          </button>

          {/* Mobile close */}
          <button
            onClick={onClose}
            aria-label="Close navigation"
            className="lg:hidden flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground hover:bg-muted"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* ── Main nav ──────────────────────────────────────── */}
        <nav className="flex flex-1 flex-col gap-5 overflow-y-auto px-2 py-4">
          {NAV_GROUPS.map(({ label, items }) => (
            <div key={label} className="flex flex-col gap-0.5">

              {/* Section label */}
              <p className={cn(
                "mb-1 px-3 text-[9.5px] font-bold uppercase tracking-[0.12em] text-primary/35",
                "transition-all duration-200 overflow-hidden",
                collapsed ? "lg:h-0 lg:opacity-0 lg:mb-0" : "h-auto opacity-100"
              )}>
                {label}
              </p>

              {items.map(({ to, label: itemLabel, icon: Icon, badge }) => {
                const active = isActive(to);
                return (
                  <NavTooltip key={to} label={itemLabel} show={collapsed}>
                    <Link
                      to={to}
                      onClick={onClose}
                      className={cn(
                        "relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-[13px] font-medium",
                        "transition-all duration-150",
                        active
                          ? "bg-primary text-primary-foreground shadow-ambient"
                          : "text-foreground/65 hover:bg-[#4C8CE4]/[0.07] hover:text-foreground",
                        collapsed && "lg:justify-center lg:px-0 lg:w-10 lg:mx-auto"
                      )}
                    >
                      <Icon className={cn("h-[15px] w-[15px] shrink-0", active && "opacity-90")} />

                      <span className={cn(
                        "flex-1 truncate transition-all duration-200",
                        collapsed && "lg:hidden"
                      )}>
                        {itemLabel}
                      </span>

                      {/* Live pulse badge */}
                      {badge === "live" && !collapsed && (
                        <span className="flex items-center gap-1">
                          <span className={cn(
                            "h-1.5 w-1.5 rounded-full pulse-live",
                            active ? "bg-white/70" : "bg-secondary-container"
                          )} />
                          {!active && (
                            <span className="text-[9.5px] font-bold tracking-wide text-secondary-container">
                              LIVE
                            </span>
                          )}
                        </span>
                      )}

                      {/* Collapsed live dot */}
                      {badge === "live" && collapsed && (
                        <span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-secondary-container pulse-live" />
                      )}

                      {/* Active chevron */}
                      {active && !collapsed && (
                        <ChevronRight className="h-3 w-3 shrink-0 opacity-50" />
                      )}
                    </Link>
                  </NavTooltip>
                );
              })}
            </div>
          ))}
        </nav>

        {/* ── Footer ────────────────────────────────────────── */}
        <div className="shrink-0 border-t border-border/60 bg-surface-container/20">
          {/* Support link */}
          <div className="px-2 pt-2.5">
            <NavTooltip label="Support" show={collapsed}>
              <button className={cn(
                "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-[13px] font-medium",
                "text-foreground/50 transition-colors hover:bg-muted hover:text-foreground",
                collapsed && "lg:justify-center lg:px-0 lg:w-10 lg:mx-auto"
              )}>
                <HelpCircle className="h-[15px] w-[15px] shrink-0" />
                <span className={cn("truncate", collapsed && "lg:hidden")}>Support</span>
              </button>
            </NavTooltip>
          </div>

          {/* User profile strip */}
          <div className={cn(
            "flex items-center gap-2.5 p-3",
            collapsed && "lg:justify-center lg:px-2"
          )}>
            {/* Avatar */}
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-[11px] font-bold text-white ring-2 ring-primary/20">
              A
            </div>

            {/* Name + role */}
            <div className={cn(
              "flex-1 overflow-hidden transition-all duration-200",
              collapsed ? "lg:hidden" : ""
            )}>
              <p className="truncate text-[13px] font-semibold text-foreground leading-tight">
                Admin User
              </p>
              <p className="truncate text-[10px] text-muted-foreground">
                Transit Manager
              </p>
            </div>

            {/* Settings + logout */}
            <div className={cn(
              "flex items-center gap-0.5 transition-all duration-200",
              collapsed ? "lg:hidden" : ""
            )}>
              <button
                aria-label="Settings"
                className="flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
              >
                <Settings className="h-3.5 w-3.5" />
              </button>
              <button
                aria-label="Sign out"
                className="flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground hover:bg-error-container hover:text-error transition-colors"
              >
                <LogOut className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        </div>
      </aside>
    </>
  );
}
