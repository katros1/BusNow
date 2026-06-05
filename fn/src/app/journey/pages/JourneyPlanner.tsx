import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { journeyApi } from "../api/journey.api";
import { journeyKeys } from "../api/journey.keys";
import type { JourneyPlanRequest, JourneySuggestion } from "../api/journey.types";
import {
  MapPin,
  Navigation,
  Footprints,
  CreditCard,
  Banknote,
  Route,
  ChevronDown,
  ChevronUp,
} from "lucide-react";

function SuggestionCard({ s, index }: { s: JourneySuggestion; index: number }) {
  const [expanded, setExpanded] = useState(index === 0);

  return (
    <div className="bg-card rounded-xl border border-border/60 shadow-ambient overflow-hidden">
      {/* Card header */}
      <button
        onClick={() => setExpanded((e) => !e)}
        className="w-full flex items-center justify-between px-5 py-4 hover:bg-primary/[0.02] transition-colors text-left"
      >
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-primary/10 border border-primary/20 flex items-center justify-center text-primary shrink-0 text-[13px] font-bold">
            {index + 1}
          </div>
          <div>
            <p className="text-[14px] font-semibold text-foreground">{s.routeName}</p>
            <p className="text-[11px] text-muted-foreground">
              {s.boardingPoint.pointName} → {s.destinationPoint.pointName}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-4">
          {/* Fare pill */}
          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-50 border border-emerald-200">
            <Banknote className="h-3.5 w-3.5 text-emerald-600" />
            <span className="text-[12px] font-bold text-emerald-700">
              {s.fareAmount.toLocaleString()} RWF
            </span>
          </div>
          {expanded ? (
            <ChevronUp className="h-4 w-4 text-muted-foreground" />
          ) : (
            <ChevronDown className="h-4 w-4 text-muted-foreground" />
          )}
        </div>
      </button>

      {expanded && (
        <div className="px-5 pb-5 border-t border-border bg-white space-y-4 pt-4">
          {/* Key metrics row */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <MetricBox
              icon={<Route className="h-4 w-4 text-primary" />}
              label="Ride Distance"
              value={`${s.rideDistanceKm.toFixed(2)} km`}
              sub="along route"
            />
            <MetricBox
              icon={<Footprints className="h-4 w-4 text-amber-500" />}
              label="Total Walking"
              value={`${s.totalWalkingMinutes} min`}
              sub={`${s.totalWalkingKm.toFixed(2)} km`}
            />
            <MetricBox
              icon={<Banknote className="h-4 w-4 text-emerald-600" />}
              label="Fare to Pay"
              value={`${s.fareAmount.toLocaleString()} RWF`}
              sub="for this trip"
              highlight
            />
            <MetricBox
              icon={<CreditCard className="h-4 w-4 text-blue-600" />}
              label="Required Balance"
              value={`${s.requiredCardBalance.toLocaleString()} RWF`}
              sub="minimum on card"
              infoColor="blue"
            />
          </div>

          {/* Walk legs */}
          <div className="grid md:grid-cols-2 gap-3">
            <WalkLeg
              label="Walk to boarding"
              pointName={s.boardingPoint.pointName}
              pointType={s.boardingPoint.pointType}
              km={s.walkToBoardingKm}
              minutes={s.walkToBoardingMinutes}
            />
            <WalkLeg
              label="Walk to destination"
              pointName={s.destinationPoint.pointName}
              pointType={s.destinationPoint.pointType}
              km={s.walkToDestinationKm}
              minutes={s.walkToDestinationMinutes}
            />
          </div>

          {/* Card balance note */}
          <div className="flex items-start gap-2 px-3 py-2.5 rounded-lg bg-blue-50 border border-blue-200">
            <CreditCard className="h-3.5 w-3.5 text-blue-600 mt-0.5 shrink-0" />
            <p className="text-[11px] text-blue-700">
              Your tap card needs at least{" "}
              <strong>{s.requiredCardBalance.toLocaleString()} RWF</strong> to board. This covers
              the full ride to the end of the route, so you can exit at any stop along the way.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

function MetricBox({
  icon,
  label,
  value,
  sub,
  highlight,
  infoColor,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  sub: string;
  highlight?: boolean;
  infoColor?: "blue";
}) {
  const bg = highlight
    ? "bg-emerald-50 border-emerald-200"
    : infoColor === "blue"
    ? "bg-blue-50 border-blue-200"
    : "bg-surface-container/30 border-border";

  return (
    <div className={`rounded-lg border p-3 ${bg}`}>
      <div className="flex items-center gap-1.5 mb-1">{icon}</div>
      <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground mb-0.5">
        {label}
      </p>
      <p className="text-[15px] font-bold text-foreground leading-tight">{value}</p>
      <p className="text-[10px] text-muted-foreground">{sub}</p>
    </div>
  );
}

function WalkLeg({
  label,
  pointName,
  pointType,
  km,
  minutes,
}: {
  label: string;
  pointName: string;
  pointType: string;
  km: number;
  minutes: number;
}) {
  return (
    <div className="flex items-start gap-3 p-3 rounded-lg border border-border bg-surface-container/20">
      <MapPin className="h-4 w-4 text-primary mt-0.5 shrink-0" />
      <div>
        <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
          {label}
        </p>
        <p className="text-[13px] font-semibold text-foreground">
          {pointName}
          <span className="ml-1.5 text-[10px] font-medium text-muted-foreground">
            ({pointType === "BUS_PARK" ? "Bus Park" : "Stop"})
          </span>
        </p>
        <p className="text-[11px] text-muted-foreground">
          {km.toFixed(2)} km · ~{minutes} min walk
        </p>
      </div>
    </div>
  );
}

export default function JourneyPlanner() {
  const [form, setForm] = useState({ fromLat: "", fromLng: "", toLat: "", toLng: "" });
  const [submitted, setSubmitted] = useState<JourneyPlanRequest | null>(null);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: submitted ? journeyKeys.plan(submitted) : ["journey", "idle"],
    queryFn: () => (submitted ? journeyApi.plan(submitted) : Promise.resolve(null)),
    enabled: !!submitted,
  });

  const handleSearch = () => {
    const fromLat = parseFloat(form.fromLat);
    const fromLng = parseFloat(form.fromLng);
    const toLat   = parseFloat(form.toLat);
    const toLng   = parseFloat(form.toLng);
    if ([fromLat, fromLng, toLat, toLng].some(isNaN)) return;
    setSubmitted({ fromLat, fromLng, toLat, toLng });
  };

  return (
    <div className="flex-1 space-y-6 pt-6 max-w-4xl mx-auto w-full px-6 md:px-0">
      {/* Header */}
      <div className="mb-8">
        <span className="text-[11px] font-bold uppercase tracking-[0.2em] text-primary mb-1.5 block">
          Passenger Tools
        </span>
        <h1 className="text-[32px] font-bold tracking-tight text-foreground leading-none">
          Journey Planner
        </h1>
        <p className="mt-1 text-[13px] text-muted-foreground">
          Find routes between two coordinates. Results include fare and required card balance.
        </p>
      </div>

      {/* Search form */}
      <div className="bg-card rounded-xl shadow-ambient border border-border/60 overflow-hidden">
        <div className="px-6 py-4 border-b border-border bg-white flex items-center gap-2">
          <Navigation className="h-4 w-4 text-primary" />
          <p className="text-[14px] font-semibold text-foreground">Plan a Journey</p>
        </div>

        <div className="px-6 py-5 bg-white">
          <div className="grid md:grid-cols-2 gap-4 mb-4">
            <CoordPair
              label="From (current location)"
              latValue={form.fromLat}
              lngValue={form.fromLng}
              onLatChange={(v) => setForm((f) => ({ ...f, fromLat: v }))}
              onLngChange={(v) => setForm((f) => ({ ...f, fromLng: v }))}
              placeholder={["-1.9537", "30.1245"]}
            />
            <CoordPair
              label="To (destination)"
              latValue={form.toLat}
              lngValue={form.toLng}
              onLatChange={(v) => setForm((f) => ({ ...f, toLat: v }))}
              onLngChange={(v) => setForm((f) => ({ ...f, toLng: v }))}
              placeholder={["-1.9888", "30.0985"]}
            />
          </div>

          <button
            onClick={handleSearch}
            disabled={isLoading}
            className="flex items-center gap-2 bg-primary text-primary-foreground hover:bg-primary/90 px-6 py-2.5 rounded-lg font-medium text-[13px] shadow-sm transition-all disabled:opacity-50 cursor-pointer"
          >
            {isLoading ? (
              <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : (
              <Navigation className="h-4 w-4" />
            )}
            Find Routes
          </button>
        </div>
      </div>

      {/* Results */}
      {isError && (
        <p className="text-[13px] text-error font-medium">
          {(error as Error)?.message ?? "Could not fetch journey suggestions."}
        </p>
      )}

      {data && data.suggestions.length === 0 && (
        <div className="text-center py-12 text-muted-foreground">
          <MapPin className="h-8 w-8 mx-auto mb-2 text-primary/20" />
          <p className="text-[13px] font-medium text-foreground">No routes found</p>
          <p className="text-[12px]">Try adjusting the coordinates or check if stops are mapped nearby.</p>
        </div>
      )}

      {data && data.suggestions.length > 0 && (
        <div className="space-y-3">
          <p className="text-[12px] font-semibold text-muted-foreground uppercase tracking-wide">
            {data.suggestions.length} suggestion{data.suggestions.length !== 1 ? "s" : ""} found
          </p>
          {data.suggestions.map((s, i) => (
            <SuggestionCard key={s.routeId + i} s={s} index={i} />
          ))}
        </div>
      )}
    </div>
  );
}

function CoordPair({
  label,
  latValue,
  lngValue,
  onLatChange,
  onLngChange,
  placeholder,
}: {
  label: string;
  latValue: string;
  lngValue: string;
  onLatChange: (v: string) => void;
  onLngChange: (v: string) => void;
  placeholder: [string, string];
}) {
  return (
    <div>
      <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground mb-2">
        {label}
      </p>
      <div className="grid grid-cols-2 gap-2">
        <div>
          <p className="text-[10px] text-muted-foreground mb-1">Latitude</p>
          <input
            type="number"
            step="any"
            value={latValue}
            onChange={(e) => onLatChange(e.target.value)}
            placeholder={placeholder[0]}
            className="w-full h-9 px-3 text-[13px] border border-border rounded-lg focus:border-primary outline-none transition-all"
          />
        </div>
        <div>
          <p className="text-[10px] text-muted-foreground mb-1">Longitude</p>
          <input
            type="number"
            step="any"
            value={lngValue}
            onChange={(e) => onLngChange(e.target.value)}
            placeholder={placeholder[1]}
            className="w-full h-9 px-3 text-[13px] border border-border rounded-lg focus:border-primary outline-none transition-all"
          />
        </div>
      </div>
    </div>
  );
}
