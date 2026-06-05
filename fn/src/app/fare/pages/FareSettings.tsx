import { useState, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { fareApi } from "../api/fare.api";
import { fareKeys } from "../api/fare.keys";
import { useUpdateFareSettings } from "../api/fare.mutations";
import { DollarSign, Save, Info } from "lucide-react";

export default function FareSettings() {
  const { data: settings, isLoading } = useQuery({
    queryKey: fareKeys.settings(),
    queryFn: fareApi.getSettings,
  });

  const updateMut = useUpdateFareSettings();
  const [basePrice, setBasePrice] = useState("");

  useEffect(() => {
    if (settings) setBasePrice(String(settings.basePriceFrw));
  }, [settings]);

  const handleSave = () => {
    const value = parseFloat(basePrice);
    if (isNaN(value) || value < 1) return;
    updateMut.mutate({ basePriceFrw: value });
  };

  return (
    <div className="flex-1 space-y-6 pt-6 max-w-4xl mx-auto w-full px-6 md:px-0">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between mb-8 gap-4">
        <div>
          <span className="text-[11px] font-bold uppercase tracking-[0.2em] text-primary mb-1.5 block">
            System Configuration
          </span>
          <h1 className="text-[32px] font-bold tracking-tight text-foreground leading-none">
            Fare Settings
          </h1>
        </div>
      </div>

      {/* Base Price Card */}
      <div className="bg-card rounded-xl shadow-ambient border border-border/60 overflow-hidden">
        <div className="px-6 py-4 border-b border-border bg-white flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-primary/10 border border-primary/20 flex items-center justify-center text-primary shrink-0">
            <DollarSign className="h-4 w-4" />
          </div>
          <div>
            <p className="text-[14px] font-semibold text-foreground">Base Price</p>
            <p className="text-[11px] text-muted-foreground">
              All fares are calculated as base price × tier multiplier
            </p>
          </div>
        </div>

        <div className="px-6 py-5 bg-white">
          <div className="flex items-end gap-4 max-w-sm">
            <div className="flex-1">
              <label className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground mb-1.5 block">
                Base Price (RWF)
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-[13px] font-semibold text-muted-foreground">
                  RWF
                </span>
                <input
                  type="number"
                  min="1"
                  step="50"
                  value={basePrice}
                  onChange={(e) => setBasePrice(e.target.value)}
                  className="w-full h-10 pl-12 pr-3 text-[14px] font-semibold border border-border rounded-lg focus:border-primary outline-none transition-all"
                  placeholder="500"
                />
              </div>
            </div>
            <button
              onClick={handleSave}
              disabled={updateMut.isPending || isLoading}
              className="flex items-center gap-2 h-10 bg-primary text-primary-foreground hover:bg-primary/90 px-5 rounded-lg font-medium text-[13px] shadow-sm transition-all disabled:opacity-50 cursor-pointer"
            >
              <Save className="h-4 w-4" />
              Save
            </button>
          </div>

          {settings && (
            <p className="mt-2 text-[11px] text-muted-foreground">
              Last updated:{" "}
              {new Date(settings.updatedAt).toLocaleString()}
            </p>
          )}
        </div>
      </div>

      {/* Tiers Table */}
      <div className="bg-card rounded-xl shadow-ambient border border-border/60 overflow-hidden">
        <div className="px-6 py-4 border-b border-border bg-white flex items-center gap-2">
          <Info className="h-4 w-4 text-primary" />
          <p className="text-[14px] font-semibold text-foreground">
            Distance Tiers & Multipliers
          </p>
        </div>

        <div className="overflow-x-auto bg-white">
          <table className="w-full text-[13px]">
            <thead>
              <tr className="border-b border-border">
                <th className="px-5 py-3 text-left text-[11px] font-semibold text-muted-foreground uppercase tracking-widest">
                  Tier
                </th>
                <th className="px-5 py-3 text-left text-[11px] font-semibold text-muted-foreground uppercase tracking-widest">
                  Distance Range
                </th>
                <th className="px-5 py-3 text-left text-[11px] font-semibold text-muted-foreground uppercase tracking-widest">
                  Multiplier
                </th>
                <th className="px-5 py-3 text-left text-[11px] font-semibold text-muted-foreground uppercase tracking-widest">
                  Example Fare
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {isLoading
                ? Array.from({ length: 7 }).map((_, i) => (
                    <tr key={i}>
                      <td colSpan={4} className="px-5 py-3">
                        <div className="h-4 bg-muted animate-pulse rounded" />
                      </td>
                    </tr>
                  ))
                : settings?.tiers.map((tier) => (
                    <tr key={tier.tier} className="hover:bg-primary/[0.02] transition-colors">
                      <td className="px-5 py-3 font-semibold text-foreground">
                        Tier {tier.tier}
                      </td>
                      <td className="px-5 py-3 text-foreground">
                        {tier.startKm} km
                        {tier.endKm !== null ? ` – ${tier.endKm} km` : "+"}
                      </td>
                      <td className="px-5 py-3">
                        <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-semibold bg-primary/10 text-primary">
                          ×{tier.multiplier.toFixed(2)}
                        </span>
                      </td>
                      <td className="px-5 py-3 font-semibold text-foreground">
                        {tier.exampleFare.toLocaleString()} RWF
                      </td>
                    </tr>
                  ))}
            </tbody>
          </table>
        </div>

        <div className="px-6 py-3 bg-surface-container/20 border-t border-border">
          <p className="text-[11px] text-muted-foreground">
            Tiers are fixed. Only the base price can be changed. Example fares are calculated at the midpoint of each tier range.
          </p>
        </div>
      </div>
    </div>
  );
}
