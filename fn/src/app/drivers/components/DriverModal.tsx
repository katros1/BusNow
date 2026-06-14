import React, { useState } from "react";
import { X, Save } from "lucide-react";
import { useCreateDriver, useUpdateDriver } from "../api/drivers.mutations";
import type { Driver } from "../api/drivers.types";

interface DriverModalProps {
  driver: Driver | null;
  onClose: () => void;
}

export function DriverModal({ driver, onClose }: DriverModalProps) {
  const isEditing = !!driver;
  
  const [firstName, setFirstName] = useState(driver?.firstName || "");
  const [lastName, setLastName] = useState(driver?.lastName || "");
  const [gender, setGender] = useState<"MALE" | "FEMALE">(driver?.gender || "MALE");
  const [phoneNumber, setPhoneNumber] = useState(driver?.phoneNumber || "");
  const [licenseNumber, setLicenseNumber] = useState(driver?.licenseNumber || "");
  const [licenseCategory, setLicenseCategory] = useState(driver?.licenseCategory || "A");

  const createMut = useCreateDriver();
  const updateMut = useUpdateDriver(driver?.id || "");

  const handleSave = () => {
    if (!firstName || !lastName || !phoneNumber || !licenseNumber || !licenseCategory) return;

    const payload = { firstName, lastName, gender, phoneNumber, licenseNumber, licenseCategory };

    if (isEditing) {
      updateMut.mutate(payload, { onSuccess: onClose });
    } else {
      createMut.mutate(payload, { onSuccess: onClose });
    }
  };

  const isPending = createMut.isPending || updateMut.isPending;
  const isComplete = firstName && lastName && phoneNumber && licenseNumber && licenseCategory;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div 
        className="bg-white w-full max-w-lg rounded-2xl shadow-xl border border-border overflow-hidden flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        <div className="px-6 py-5 border-b border-border flex items-center justify-between bg-surface-container-lowest">
          <div>
            <h2 className="text-xl font-bold text-foreground">
              {isEditing ? "Edit Driver" : "Register New Driver"}
            </h2>
            <p className="text-[13px] text-muted-foreground mt-0.5">
              Enter driver's basic and licensing information.
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
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">First Name</label>
            <input 
              value={firstName} onChange={(e) => setFirstName(e.target.value)}
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium"
            />
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">Last Name</label>
            <input 
              value={lastName} onChange={(e) => setLastName(e.target.value)}
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium"
            />
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">Gender</label>
            <select
              value={gender} onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setGender(e.target.value as "MALE" | "FEMALE")}
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium cursor-pointer"
            >
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
            </select>
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">Phone Number</label>
            <input 
              value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)}
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium"
            />
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">License Number</label>
            <input 
              value={licenseNumber} onChange={(e) => setLicenseNumber(e.target.value)}
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium uppercase"
            />
          </div>

          <div>
            <label className="text-[12px] font-bold text-muted-foreground uppercase tracking-wider block mb-2">License Category</label>
            <input 
              value={licenseCategory} onChange={(e) => setLicenseCategory(e.target.value.toUpperCase())}
              placeholder="e.g. A, B, C, D"
              className="w-full h-10 px-3 rounded-lg border border-border outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 text-[13px] font-medium uppercase"
            />
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
                 Save Driver
               </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
