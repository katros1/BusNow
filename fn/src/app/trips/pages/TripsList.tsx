import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Activity, Bus, Users, Clock, ArrowRight, CheckCircle2,
  ChevronLeft, ChevronRight as ChevronRightIcon, TrendingUp, Filter, RefreshCw,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { tripsApi } from "../api/trips.api";
import { tripKeys } from "../api/trips.keys";
import type { TripStatus, TripSummaryDto } from "../api/trips.types";

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmt(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString(undefined, {
    month: "short", day: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

function duration(start: string, end: string | null): string {
  if (!end) {
    const s = Math.max(0, Math.floor((Date.now() - new Date(start).getTime()) / 1000));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    return h > 0 ? `${h}h ${m}m` : `${m}m`;
  }
  const s = Math.max(0, Math.floor((new Date(end).getTime() - new Date(start).getTime()) / 1000));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

function occupancyColor(pct: number | null) {
  if (pct == null) return "text-muted-foreground";
  if (pct >= 90) return "text-red-600";
  if (pct >= 70) return "text-yellow-600";
  return "text-[#2E6B1A]";
}

function occupancyBarColor(pct: number | null) {
  if (pct == null) return "bg-muted";
  if (pct >= 90) return "bg-red-500";
  if (pct >= 70) return "bg-yellow-500";
  return "bg-[#91D06C]";
}

// ── Summary KPI strip ─────────────────────────────────────────────────────────

function KpiCard({ icon: Icon, label, value, color, bg }: {
  icon: React.ElementType; label: string; value: React.ReactNode;
  color: string; bg: string;
}) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-border/60 bg-card px-4 py-3">
      <div className={cn("flex h-9 w-9 shrink-0 items-center justify-center rounded-xl", bg)}>
        <Icon className={cn("h-4.5 w-4.5", color)} />
      </div>
      <div>
        <p className="text-[20px] font-bold text-foreground leading-none">{value}</p>
        <p className="text-[10px] text-muted-foreground mt-0.5">{label}</p>
      </div>
    </div>
  );
}

// ── Trip row card ─────────────────────────────────────────────────────────────

function TripCard({ trip }: { trip: TripSummaryDto }) {
  const isActive = trip.status === "ACTIVE";
  const pct = trip.capacity ? Math.min(100, Math.round((trip.passengersOnBoard / trip.capacity) * 100)) : null;
  const boardings  = trip.totalBoardings  ?? (isActive ? trip.passengersOnBoard : null);
  const alightings = trip.totalAlightings ?? null;

  return (
    <div className={cn(
      "rounded-xl border bg-card px-5 py-4 space-y-3 transition-colors",
      isActive
        ? "border-[#2E6B1A]/30 bg-[#2E6B1A]/[0.02] hover:bg-[#2E6B1A]/[0.04]"
        : "border-border/60 hover:bg-muted/20"
    )}>
      {/* ── Row 1: plate + route + status ── */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3 min-w-0">
          <div className={cn(
            "flex h-9 w-9 shrink-0 items-center justify-center rounded-xl",
            isActive ? "bg-[#2E6B1A]/10" : "bg-muted"
          )}>
            <Bus className={cn("h-4 w-4", isActive ? "text-[#2E6B1A]" : "text-muted-foreground")} />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-[14px] font-bold text-foreground">{trip.plateNumber}</span>
              {trip.model && (
                <span className="text-[11px] text-muted-foreground hidden sm:block">{trip.model}</span>
              )}
              {trip.routeCode && (
                <span className="text-[9px] font-black uppercase tracking-widest rounded bg-primary/10 text-primary px-1.5 py-0.5">
                  {trip.routeCode}
                </span>
              )}
            </div>
            <p className="text-[12px] text-muted-foreground mt-0.5 truncate">{trip.routeName}</p>
          </div>
        </div>

        <div className="flex flex-col items-end gap-1.5 shrink-0">
          {isActive ? (
            <span className="flex items-center gap-1.5 rounded-full bg-[#2E6B1A]/10 border border-[#2E6B1A]/20 px-2.5 py-1">
              <span className="h-1.5 w-1.5 rounded-full bg-[#91D06C] pulse-live" />
              <span className="text-[10px] font-bold text-[#2E6B1A]">Active</span>
            </span>
          ) : (
            <span className="flex items-center gap-1.5 rounded-full bg-muted px-2.5 py-1">
              <CheckCircle2 className="h-3 w-3 text-muted-foreground" />
              <span className="text-[10px] font-medium text-muted-foreground">Completed</span>
            </span>
          )}
          {trip.direction && (
            <span className="text-[9px] text-muted-foreground">
              {trip.direction === "FORWARD" ? "→ Forward" : "← Return"}
            </span>
          )}
        </div>
      </div>

      {/* ── Row 2: time strip ── */}
      <div className="flex items-center gap-2 text-[11px] text-muted-foreground">
        <Clock className="h-3 w-3 shrink-0" />
        <span>{fmt(trip.startedAt)}</span>
        {trip.endedAt && (
          <>
            <ArrowRight className="h-3 w-3 shrink-0" />
            <span>{fmt(trip.endedAt)}</span>
          </>
        )}
        <span className="ml-auto font-semibold text-foreground tabular-nums">
          {duration(trip.startedAt, trip.endedAt)}
        </span>
      </div>

      {/* ── Row 3: passenger counts ── */}
      <div className="grid grid-cols-3 gap-3">
        {/* On board */}
        <div className="rounded-lg bg-muted/60 px-3 py-2 text-center">
          <p className="text-[18px] font-bold text-foreground leading-none">
            {isActive ? trip.passengersOnBoard : (trip.totalBoardings != null ? trip.totalBoardings - (trip.totalAlightings ?? 0) : trip.passengersOnBoard)}
          </p>
          <p className="text-[9px] text-muted-foreground mt-0.5">{isActive ? "on board" : "final on board"}</p>
        </div>

        {/* Boardings */}
        <div className="rounded-lg bg-[#2E6B1A]/8 px-3 py-2 text-center">
          <p className="text-[18px] font-bold text-[#2E6B1A] leading-none">
            {boardings ?? "—"}
          </p>
          <p className="text-[9px] text-muted-foreground mt-0.5">boarded</p>
        </div>

        {/* Alightings */}
        <div className="rounded-lg bg-primary/8 px-3 py-2 text-center">
          <p className="text-[18px] font-bold text-primary leading-none">
            {alightings ?? "—"}
          </p>
          <p className="text-[9px] text-muted-foreground mt-0.5">alighted</p>
        </div>
      </div>

      {/* ── Row 4: occupancy bar ── */}
      {trip.capacity != null && (
        <div className="space-y-1.5">
          <div className="h-1.5 rounded-full bg-muted overflow-hidden">
            <div
              className={cn("h-full rounded-full transition-all duration-700", occupancyBarColor(pct))}
              style={{ width: `${pct ?? 0}%` }}
            />
          </div>
          <div className="flex items-center justify-between text-[10px]">
            <span className="text-muted-foreground">{trip.passengersOnBoard} / {trip.capacity} seats</span>
            {pct != null && (
              <span className={cn("font-bold", occupancyColor(pct))}>{pct}% full</span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

const PAGE_SIZE = 15;

export default function TripsList() {
  const [statusFilter, setStatusFilter] = useState<TripStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);

  const params = useMemo(() => ({
    status: statusFilter !== "ALL" ? statusFilter : undefined,
    page,
    size: PAGE_SIZE,
  }), [statusFilter, page]);

  const { data, isLoading, isFetching, refetch } = useQuery({
    queryKey: tripKeys.list(params),
    queryFn: () => tripsApi.list(params),
    staleTime: 15_000,
    refetchInterval: 30_000,
  });

  const trips = data?.content ?? [];
  const totalPages = data?.totalPages ?? 1;
  const totalElements = data?.totalElements ?? 0;

  const activeCount    = trips.filter(t => t.status === "ACTIVE").length;
  const totalBoardings = trips.reduce((s, t) => s + (t.totalBoardings ?? t.passengersOnBoard ?? 0), 0);
  const totalOnBoard   = trips.filter(t => t.status === "ACTIVE")
    .reduce((s, t) => s + t.passengersOnBoard, 0);

  return (
    <div className="max-w-4xl mx-auto w-full space-y-5">

      {/* ── Header ── */}
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 shrink-0">
            <Activity className="h-5 w-5 text-primary" />
          </div>
          <div>
            <h1 className="text-[20px] font-bold text-foreground leading-tight">Trip History</h1>
            <p className="text-[11px] text-muted-foreground mt-0.5">
              {isLoading ? "Loading…" : `${totalElements} trip${totalElements !== 1 ? "s" : ""} total`}
            </p>
          </div>
        </div>

        <button
          onClick={() => refetch()}
          disabled={isFetching}
          className="flex items-center gap-1.5 rounded-lg border border-border/60 bg-card px-3 py-1.5 text-[12px] font-medium text-muted-foreground hover:bg-muted transition-colors"
        >
          <RefreshCw className={cn("h-3.5 w-3.5", isFetching && "animate-spin")} />
          Refresh
        </button>
      </div>

      {/* ── KPI strip ── */}
      {!isLoading && (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          <KpiCard icon={Activity}   label="Active trips"    value={activeCount}    color="text-[#2E6B1A]" bg="bg-[#2E6B1A]/10" />
          <KpiCard icon={Users}      label="Passengers in transit" value={totalOnBoard} color="text-primary"    bg="bg-primary/10" />
          <KpiCard icon={TrendingUp} label="Boardings (page)" value={totalBoardings} color="text-primary"    bg="bg-primary/10" />
        </div>
      )}

      {/* ── Filters ── */}
      <div className="flex items-center gap-2">
        <Filter className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
        {(["ALL", "ACTIVE", "COMPLETED"] as const).map((s) => (
          <button
            key={s}
            onClick={() => { setStatusFilter(s); setPage(0); }}
            className={cn(
              "rounded-full px-3 py-1 text-[11px] font-semibold transition-colors",
              statusFilter === s
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-muted-foreground hover:bg-muted/80"
            )}
          >
            {s === "ALL" ? "All" : s === "ACTIVE" ? "Active" : "Completed"}
          </button>
        ))}
      </div>

      {/* ── Loading skeletons ── */}
      {isLoading && (
        <div className="space-y-3">
          {[1, 2, 3].map(i => (
            <div key={i} className="h-40 rounded-xl bg-card border border-border/60 animate-pulse" />
          ))}
        </div>
      )}

      {/* ── Empty state ── */}
      {!isLoading && trips.length === 0 && (
        <div className="flex flex-col items-center justify-center gap-4 rounded-xl border border-border/60 bg-card py-20">
          <div className="flex h-14 w-14 items-center justify-center rounded-full bg-muted">
            <Activity className="h-6 w-6 text-muted-foreground opacity-40" />
          </div>
          <div className="text-center">
            <p className="text-[14px] font-bold text-foreground">No trips found</p>
            <p className="text-[12px] text-muted-foreground mt-1">
              {statusFilter !== "ALL"
                ? `No ${statusFilter.toLowerCase()} trips match this filter.`
                : "No trips recorded yet. Start a simulator to generate data."}
            </p>
          </div>
        </div>
      )}

      {/* ── Trip cards ── */}
      {!isLoading && trips.length > 0 && (
        <div className="space-y-3">
          {trips.map(trip => <TripCard key={trip.id} trip={trip} />)}
        </div>
      )}

      {/* ── Pagination ── */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <span className="text-[12px] text-muted-foreground">
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0}
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-border/60 bg-card text-muted-foreground hover:bg-muted disabled:opacity-40 transition-colors"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <button
              onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-border/60 bg-card text-muted-foreground hover:bg-muted disabled:opacity-40 transition-colors"
            >
              <ChevronRightIcon className="h-4 w-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
