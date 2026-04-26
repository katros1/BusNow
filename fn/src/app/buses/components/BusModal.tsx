import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { X, Save } from "lucide-react";
import { useCreateBus, useUpdateBus } from "../api/buses.mutations";
import type { Bus } from "../api/buses.types";
import { driversApi } from "../../drivers/api/drivers.api";
import { driverKeys } from "../../drivers/api/drivers.keys";
import { routeCodesApi } from "../../routes/api/route-codes.api";
import { routeCodeKeys } from "../../routes/api/route-codes.keys";

interface BusModalProps {
  bus: Bus | null;
  onClose: () => void;
}

export function BusModal({ bus, onClose }: BusModalProps) {
  const isEditing = !!bus;
  
  const [plateNumber, setPlateNumber] = useState(bus?.plateNumber || "");
  const [gpsImei, setGpsImei] = useState(bus?.gpsImei || "");
  const [model, setModel] = useState(bus?.model || "");
  const [capacity, setCapacity] = useState(bus?.capacity || 0);
  const [driverId, setDriverId] = useState(bus?.currentDriver?.id || "");
  const [routeCodeId, setRouteCodeId] = useState(bus?.routeCode?.id || "");

  const { data: driversResponse, isLoading: isLoadingDrivers } = useQuery(driverKeys.lists(), {
    queryFn: () => driversApi.getAll(),
  });

  const { data: routeCodesResponse, isLoading: isLoadingCodes } = useQuery(routeCodeKeys.lists(), {
    queryFn: () => routeCodesApi.getAll(),
  });
  const drivers = driversResponse?.content ?? [];
  const routeCodes = routeCodesResponse?.content ?? [];

  const createMut = useCreateBus();
  const updateMut = useUpdateBus(bus?.id || "");

  const handleSave = () => {
    if (!plateNumber || !gpsImei || !model || capacity <= 0 || !driverId || !routeCodeId) return;

    const payload = { plateNumber, gpsImei, model, capacity, driverId, routeCodeId };

    if (isEditing) {
      updateMut.mutate(payload, { onSuccess: onClose });
    } else {
      createMut.mutate(payload, { onSuccess: onClose });
    }
  };

  const isPending = createMut.isPending || updateMut.isPending;
  const isComplete = plateNumber && gpsImei && model && capacity > 0 && driverId && routeCodeId;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div 
        className="bg-white w-full max-w-lg rounded-2xl shadow-xl border border-border overflow-hidden flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        <div className="px-6 py-5 border-b border-border flex items-center justify-between bg-surface-container-lowest">
          <div>
            <h2 className="text-xl font-bold text-foreground">
              {isEditing ? "Edit Bus" : "Register New Bus"}
            </h2>
            <p className="text-[13px] text-muted-foreground mt-0.5">
              Enter bus information, driver, and route assignment.
            </p>
          </div>
          <button 
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center rounded-lg text-muted-foreground hover:bg-surface-container hover:text-foreground transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="p-6 grid grid-cols-2 gap-5">
          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">Plate Number</label>
            <input 
              value={plateNumber} onChange={(e) => setPlateNumber(e.target.value.toUpperCase())}
              placeholder="e.g. RAA 123 A"
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium uppercase"
            />
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">GPS IMEI</label>
            <input 
              value={gpsImei} onChange={(e) => setGpsImei(e.target.value)}
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium"
            />
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">Model</label>
            <input 
              value={model} onChange={(e) => setModel(e.target.value)}
              placeholder="e.g. Yutong 2024"
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium"
            />
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">Passenger Capacity</label>
            <input 
              type="number"
              value={capacity || ""} onChange={(e) => setCapacity(parseInt(e.target.value) || 0)}
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium"
            />
          </div>

          <div className="col-span-2 grid grid-cols-2 gap-5 pt-3 border-t border-border">
            <div>
              <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">Assigned Driver</label>
              <select
                value={driverId} onChange={(e) => setDriverId(e.target.value)}
                disabled={isLoadingDrivers}
                className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium cursor-pointer disabled:opacity-50"
              >
                <option value="" disabled>Select active driver...</option>
                {drivers.map((d) => (
                  <option key={d.id} value={d.id}>{d.firstName} {d.lastName}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">Route Code</label>
              <select
                value={routeCodeId} onChange={(e) => setRouteCodeId(e.target.value)}
                disabled={isLoadingCodes}
                className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium cursor-pointer disabled:opacity-50"
              >
                <option value="" disabled>Select assigned code...</option>
                {routeCodes.map((c) => (
                  <option key={c.id} value={c.id}>{c.code}</option>
                ))}
              </select>
            </div>
          </div>
        </div>

        <div className="px-6 py-4 border-t border-border bg-surface-container-lowest flex justify-end gap-3">
          <button 
            onClick={onClose}
            className="px-5 h-10 text-[13px] font-bold text-muted-foreground hover:text-foreground transition-colors"
          >
            Cancel
          </button>
          <button 
            onClick={handleSave}
            disabled={isPending || !isComplete}
            className="flex items-center gap-2 px-6 h-10 bg-primary text-primary-foreground text-[13px] font-bold rounded-lg hover:bg-primary/90 shadow-sm transition-all disabled:opacity-50"
          >
            {isPending ? "Saving..." : (
               <>
                 <Save className="h-4 w-4" />
                 Save Bus
               </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
