import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { PlusCircle, Edit, Trash, Bus as BusIcon, RadioTower } from "lucide-react";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";
import type { ColumnDef } from "@tanstack/react-table";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import { ConfirmDeleteModal } from "@/components/modals/ConfirmDeleteModal";
import { BusModal } from "./components/BusModal";

import { busesApi } from "./api/buses.api";
import { busKeys } from "./api/buses.keys";
import { useDeleteBus } from "./api/buses.mutations";
import type { Bus } from "./api/buses.types";

export default function Buses() {
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [modalBus, setModalBus] = useState<Bus | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const deleteMut = useDeleteBus();

  const [search, setSearch] = useState("");
  const [{ pageIndex, pageSize }, setPagination] = useState({ pageIndex: 0, pageSize: 7 });

  const { data: response, isLoading, isError, error } = useQuery({
    queryKey: [...busKeys.lists(), { search, pageIndex, pageSize }],
    queryFn: () => busesApi.getAll({ search, page: pageIndex, size: pageSize }),
  });

  const buses = response?.content || [];
  const totalPages = response?.totalPages || 0;

  const columns = useMemo<ColumnDef<Bus>[]>(
    () => [
      {
        accessorKey: "plateNumber",
        header: "Designation",
        cell: ({ row }) => {
          const b = row.original;
          return (
            <div className="flex items-center gap-3 py-1">
              <div className="w-10 h-10 rounded-full bg-primary/10 border border-primary/20 flex items-center justify-center text-primary shrink-0 transition-colors group-hover:bg-primary group-hover:text-on-primary">
                <BusIcon className="h-[18px] w-[18px] opacity-75 group-hover:opacity-100 transition-opacity" />
              </div>
              <div className="flex flex-col gap-0.5">
                <p className="text-[14px] font-semibold text-foreground tracking-tight">{b.plateNumber}</p>
                <div className="flex items-center gap-1.5">
                    <span className="h-1.5 w-1.5 rounded-full bg-emerald-500"></span>
                    <p className="text-[11px] text-muted-foreground font-medium tracking-wide">ID: UK-{b.id.slice(0, 8).toUpperCase()}</p>
                </div>
              </div>
            </div>
          );
        },
      },
      {
        accessorKey: "model",
        header: "Vehicle Spec",
        cell: ({ row }) => (
          <div className="flex flex-col gap-0.5">
            <p className="text-[13px] font-semibold text-foreground tracking-tight">{row.original.model}</p>
            <p className="text-[11px] text-muted-foreground font-medium tracking-wide">Capacity: <span className="font-bold text-foreground">{row.original.capacity} Pax</span></p>
          </div>
        ),
      },
      {
        accessorKey: "assignments",
        header: "Field Assignments",
        cell: ({ row }) => {
          const { currentDriver, routeCode } = row.original;
          return (
            <div className="flex flex-col gap-0.5 text-[12px]">
               <p><span className="text-muted-foreground mr-1">Code:</span><span className="font-bold text-foreground">{routeCode?.code || "Unassigned"}</span></p>
               <p><span className="text-muted-foreground mr-1">Driver:</span><span className="font-medium text-foreground">{currentDriver?.fullName || "No driver"}</span></p>
            </div>
          );
        },
      },
      {
        accessorKey: "gpsImei",
        header: "Tracking",
        cell: ({ row }) => (
            <div className="flex items-center gap-2">
                <RadioTower className="h-4 w-4 text-emerald-500" />
                <span className="text-[13px] font-medium font-mono text-muted-foreground">{row.original.gpsImei}</span>
            </div>
        )
      },
      {
        id: "actions",
        header: () => <div className="text-right">Actions</div>,
        cell: ({ row }) => {
          const b = row.original;
          return (
            <div className="flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity duration-200">
              <button
                onClick={() => {
                  setModalBus(b);
                  setIsModalOpen(true);
                }}
                className="p-2 hover:bg-primary/10 rounded-lg text-muted-foreground hover:text-primary transition-colors cursor-pointer"
                title="Edit Bus"
              >
                <Edit className="h-[15px] w-[15px]" />
              </button>
              <button
                onClick={() => setDeleteId(b.id)}
                className="p-2 hover:bg-error-container rounded-lg text-muted-foreground hover:text-error transition-colors cursor-pointer"
                title="Delete Bus"
              >
                <Trash className="h-[15px] w-[15px]" />
              </button>
            </div>
          );
        },
      },
    ],
    []
  );

  const table = useReactTable({
    data: buses,
    columns,
    pageCount: totalPages,
    state: {
      pagination: { pageIndex, pageSize }
    },
    onPaginationChange: setPagination,
    manualPagination: true,
    getCoreRowModel: getCoreRowModel(),
  });

  return (
    <div className="flex-1 space-y-6 pt-6 max-w-7xl mx-auto w-full px-6 md:px-0">
      <div className="flex flex-col md:flex-row md:items-end justify-between mb-8 gap-4">
        <div>
          <span className="text-[11px] font-bold uppercase tracking-[0.2em] text-primary mb-1.5 block">Fleet Management</span>
          <h1 className="text-[32px] font-bold tracking-tight text-foreground leading-none">Buses Inventory</h1>
        </div>
        <div className="flex items-center space-x-3">
          <button
            onClick={() => {
              setModalBus(null);
              setIsModalOpen(true);
            }}
            className="flex items-center gap-2 bg-primary text-primary-foreground hover:bg-primary/90 px-5 py-2.5 rounded-lg font-medium text-[13px] shadow-sm transition-all outline-none cursor-pointer"
          >
            <PlusCircle className="h-[18px] w-[18px]" />
            <span>Register New Bus</span>
          </button>
        </div>
      </div>

      <div className="bg-card rounded-xl shadow-ambient border border-border/60 overflow-hidden">
        <div className="px-5 py-3.5 border-b border-border/60 flex items-center bg-surface-container/40">
            <input
              type="text"
              placeholder="Search buses..."
              value={search}
              onChange={e => {
                  setSearch(e.target.value);
                  setPagination(prev => ({ ...prev, pageIndex: 0 }));
              }}
              className="w-full md:max-w-xs h-9 px-3 text-[13px] border border-border/70 rounded-lg bg-card focus:border-primary/40 focus:ring-2 focus:ring-primary/15 outline-none transition-all placeholder:text-muted-foreground/50"
            />
        </div>
        <div className="overflow-x-auto bg-card">
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id} className="hover:bg-transparent">
                  {headerGroup.headers.map((header) => (
                    <TableHead key={header.id}>
                      {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow className="hover:bg-transparent border-transparent">
                  <TableCell colSpan={columns.length} className="h-36 text-center text-[13px] text-muted-foreground">
                    <div className="w-5 h-5 border-2 border-primary/20 border-t-primary rounded-full animate-spin mx-auto mb-2.5" />
                    Loading fleet data…
                  </TableCell>
                </TableRow>
              ) : isError ? (
                <TableRow className="hover:bg-transparent border-transparent">
                  <TableCell colSpan={columns.length} className="h-36 text-center text-[13px] text-error">
                    Error loading buses: {(error as Error)?.message}
                  </TableCell>
                </TableRow>
              ) : table.getRowModel().rows?.length ? (
                table.getRowModel().rows.map((row) => (
                  <TableRow key={row.id}>
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id}>
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </TableCell>
                    ))}
                  </TableRow>
                ))
              ) : (
                <TableRow className="hover:bg-transparent border-transparent">
                  <TableCell colSpan={columns.length} className="h-44 text-center">
                    <p className="text-[13px] font-semibold text-foreground">No buses recorded.</p>
                    <p className="text-[12px] text-muted-foreground mt-1">Register your first bus to get started.</p>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
        <div className="px-5 py-3.5 bg-surface-container/30 flex items-center justify-between border-t border-border/60">
          <p className="text-[12px] text-muted-foreground font-medium">
            Page <span className="font-semibold text-primary">{pageIndex + 1}</span> of <span className="font-semibold text-primary">{totalPages || 1}</span>
          </p>
          <div className="flex gap-1.5">
            <button
              onClick={() => table.previousPage()}
              disabled={!table.getCanPreviousPage()}
              className="h-8 w-8 flex items-center justify-center rounded-lg border border-border/70 text-[13px] text-muted-foreground hover:bg-card hover:text-primary hover:border-primary/30 transition-colors disabled:opacity-30 disabled:pointer-events-none cursor-pointer"
            >
              ‹
            </button>
            <button
              onClick={() => table.nextPage()}
              disabled={!table.getCanNextPage()}
              className="h-8 w-8 flex items-center justify-center rounded-lg border border-border/70 text-[13px] text-muted-foreground hover:bg-card hover:text-primary hover:border-primary/30 transition-colors disabled:opacity-30 disabled:pointer-events-none cursor-pointer"
            >
              ›
            </button>
          </div>
        </div>
      </div>

      <ConfirmDeleteModal
        isOpen={!!deleteId}
        onClose={() => setDeleteId(null)}
        onConfirm={() => {
          if (deleteId) deleteMut.mutate(deleteId);
        }}
        entityName="this bus record"
        entityType="Bus"
      />

      {isModalOpen && (
        <BusModal
          bus={modalBus}
          onClose={() => setIsModalOpen(false)}
        />
      )}
    </div>
  );
}
