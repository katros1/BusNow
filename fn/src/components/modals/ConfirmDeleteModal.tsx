import { AlertTriangle } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface ConfirmDeleteModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  entityName?: string;
  entityType?: string;
}

export function ConfirmDeleteModal({
  isOpen,
  onClose,
  onConfirm,
  entityName,
  entityType = "Item",
}: ConfirmDeleteModalProps) {
  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[425px] border-border bg-white shadow-xl rounded-xl">
        <DialogHeader>
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-50 border border-red-500/10 mb-4">
            <AlertTriangle className="h-8 w-8 text-red-600" />
          </div>
          <DialogTitle className="text-center font-bold text-2xl text-foreground">
            Delete {entityType}
          </DialogTitle>
          <DialogDescription className="text-center text-[15px] font-medium pt-2">
            Are you sure you want to permanently remove{entityName ? <span> <strong className="text-foreground">{entityName}</strong></span> : " this item"}? This action will destroy the geographic zones and cannot be undone.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="sm:justify-center gap-3 w-full sm:w-full flex-col sm:flex-row mt-4">
          <button
            onClick={onClose}
            className="h-11 px-6 rounded-lg font-bold text-muted-foreground border border-border hover:bg-surface-container hover:text-foreground transition-colors w-full sm:w-auto"
          >
            Cancel
          </button>
          <button
            onClick={() => {
              onConfirm();
              onClose();
            }}
            className="h-11 px-8 rounded-lg bg-red-600 text-white font-bold hover:bg-red-700 transition-colors w-full sm:w-auto shadow-sm"
          >
            Yes, Destroy Zone
          </button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
