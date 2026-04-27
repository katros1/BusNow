import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { PlusCircle, Edit, Trash, UserRound } from "lucide-react";
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
import { DriverModal } from "./components/DriverModal";

import { driversApi } from "./api/drivers.api";
import { driverKeys } from "./api/drivers.keys";
import { useDeleteDriver } from "./api/drivers.mutations";
import type { Driver } from "./api/drivers.types";

export default function Drivers() {
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [modalDriver, setModalDriver] = useState<Driver | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const deleteMut = useDeleteDriver();

  const [search, setSearch] = useState("");
  const [gender, setGender] = useState("");
  const [licenseCategory, setLicenseCategory] = useState("");
  const [{ pageIndex, pageSize }, setPagination] = useState({ pageIndex: 0, pageSize: 7 });

  const { data: response, isLoading, isError, error } = useQuery({
    queryKey: [...driverKeys.lists(), { search, gender, licenseCategory, pageIndex, pageSize }],
    queryFn: () => driversApi.getAll({ search, gender, licenseCategory, page: pageIndex, size: pageSize }),
  });

  const drivers = response?.content || [];
  const totalPages = response?.totalPages || 0;

  const columns = useMemo<ColumnDef<Driver>[]>(
    () => [
      {
        accessorKey: "name",
        header: "Driver Info",
        cell: ({ row }) => {
          const d = row.original;
          return (
            <div className="flex items-center gap-3 py-1">
              <div className="w-10 h-10 rounded-full bg-primary/10 border border-primary/20 flex items-center justify-center text-primary shrink-0 transition-colors group-hover:bg-primary group-hover:text-on-primary">
                <UserRound className="h-[18px] w-[18px] opacity-75 group-hover:opacity-100 transition-opacity" />
              </div>
              <div className="flex flex-col gap-0.5">
                <p className="text-[14px] font-semibold text-foreground tracking-tight">{d.firstName} {d.lastName}</p>
                <p className="text-[11px] text-muted-foreground font-medium tracking-wide">ID: UK-{d.id.slice(0, 8).toUpperCase()}</p>
              </div>
            </div>
          );
        },
      },
      {
        accessorKey: "license",
        header: "Licensing",
        cell: ({ row }) => (
          <div className="flex flex-col gap-0.5">
            <p className="text-[13px] font-semibold text-foreground tracking-tight">{row.original.licenseNumber}</p>
            <p className="text-[11px] text-muted-foreground font-medium tracking-wide">Cat: <span className="font-bold text-foreground">{row.original.licenseCategory}</span></p>
          </div>
        ),
      },
      {
        accessorKey: "phoneNumber",
        header: "Contact",
        cell: ({ row }) => (
          <p className="text-[13px] font-medium text-foreground">{row.original.phoneNumber}</p>
        ),
      },
      {
        id: "actions",
        header: () => <div className="text-right">Actions</div>,
        cell: ({ row }) => {
          const d = row.original;
          return (
            <div className="flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity duration-200">
              <button
                onClick={() => {
                  setModalDriver(d);
                  setIsModalOpen(true);
                }}
                className="p-2 hover:bg-primary/10 rounded-lg text-muted-foreground hover:text-primary transition-colors cursor-pointer"
                title="Edit Driver"
              >
                <Edit className="h-[15px] w-[15px]" />
              </button>
              <button
                onClick={() => setDeleteId(d.id)}
                className="p-2 hover:bg-error-container rounded-lg text-muted-foreground hover:text-error transition-colors cursor-pointer"
                title="Delete Driver"
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
    data: drivers,
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
          <h1 className="text-[32px] font-bold tracking-tight text-foreground leading-none">Driver Logistics</h1>
        </div>
        <div className="flex items-center space-x-3">
          <button
            onClick={() => {
              setModalDriver(null);
              setIsModalOpen(true);
            }}
            className="flex items-center gap-2 bg-primary text-primary-foreground hover:bg-primary/90 px-5 py-2.5 rounded-lg font-medium text-[13px] shadow-sm transition-all outline-none cursor-pointer"
          >
            <PlusCircle className="h-[18px] w-[18px]" />
            <span>Register Driver</span>
          </button>
        </div>
      </div>

      <div className="bg-card rounded-xl shadow-ambient border border-border/60 overflow-hidden">
        <div className="px-5 py-3 border-b border-border flex flex-wrap items-center gap-3 bg-white">
            <input 
              type="text" 
              placeholder="Search drivers..." 
              value={search} 
              onChange={e => {
                  setSearch(e.target.value);
                  setPagination(prev => ({ ...prev, pageIndex: 0 }));
              }} 
              className="w-full md:max-w-xs h-9 px-3 text-[13px] border border-border rounded-md focus:border-primary focus:ring-1 focus:ring-primary/20 outline-none transition-all"
            />
            <select 
               value={gender} 
               onChange={e => {
                   setGender(e.target.value);
                   setPagination(prev => ({ ...prev, pageIndex: 0 }));
               }} 
               className="h-9 px-3 text-[13px] border border-border rounded-md focus:border-primary outline-none transition-all cursor-pointer bg-white"
            >
                <option value="">All Genders</option>
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
            </select>
            <select 
               value={licenseCategory} 
               onChange={e => {
                   setLicenseCategory(e.target.value);
                   setPagination(prev => ({ ...prev, pageIndex: 0 }));
               }} 
               className="h-9 px-3 text-[13px] border border-border rounded-md focus:border-primary outline-none transition-all cursor-pointer bg-white"
            >
                <option value="">All Categories</option>
                <option value="A">Cat A</option>
                <option value="B">Cat B</option>
                <option value="C">Cat C</option>
                <option value="D">Cat D</option>
            </select>
        </div>
        <div className="overflow-x-auto bg-white">
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id} className="border-b border-border bg-white hover:bg-white">
                  {headerGroup.headers.map((header) => (
                    <TableHead
                      key={header.id}
                      className="h-11 px-5 text-left text-[11px] font-semibold text-muted-foreground uppercase tracking-widest bg-white"
                    >
                      {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody className="divide-y divide-border bg-white">
              {isLoading ? (
                <TableRow className="hover:bg-white border-transparent">
                  <TableCell colSpan={columns.length} className="h-32 text-center text-[13px] text-muted-foreground">
                    <div className="w-5 h-5 border-2 border-primary/20 border-t-primary rounded-full animate-spin mx-auto mb-2"></div>
                    Loading drivers...
                  </TableCell>
                </TableRow>
              ) : isError ? (
                <TableRow className="hover:bg-white border-transparent">
                  <TableCell colSpan={columns.length} className="h-32 text-center text-[13px] text-error">
                    Error loading drivers: {(error as Error)?.message}
                  </TableCell>
                </TableRow>
              ) : table.getRowModel().rows?.length ? (
                table.getRowModel().rows.map((row) => (
                  <TableRow
                    key={row.id}
                    className="group transition-colors hover:bg-primary/[0.02] border-border"
                  >
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id} className="px-5 py-3">
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </TableCell>
                    ))}
                  </TableRow>
                ))
              ) : (
                <TableRow className="hover:bg-white border-transparent">
                  <TableCell colSpan={columns.length} className="h-40 text-center text-muted-foreground">
                    <p className="text-[13px] font-medium text-foreground">No drivers found.</p>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
        <div className="px-5 py-3 bg-white flex items-center justify-between border-t border-border">
          <p className="text-[12px] text-muted-foreground font-medium">
            Page <span className="font-semibold text-primary">{pageIndex + 1}</span> of <span className="font-semibold text-primary">{totalPages || 1}</span>
          </p>
          <div className="flex gap-1.5">
            <button 
                onClick={() => table.previousPage()}
                disabled={!table.getCanPreviousPage()}
                className="h-8 w-8 flex items-center justify-center rounded-md border border-border text-muted-foreground hover:bg-surface-container-low hover:text-primary hover:border-primary/30 transition-colors disabled:opacity-30 disabled:hover:bg-transparent cursor-pointer"
            >
                &lt;
            </button>
            <button 
                onClick={() => table.nextPage()}
                disabled={!table.getCanNextPage()}
                className="h-8 w-8 flex items-center justify-center rounded-md border border-border text-muted-foreground hover:bg-surface-container-low hover:text-primary hover:border-primary/30 transition-colors disabled:opacity-30 disabled:hover:bg-transparent cursor-pointer"
            >
                &gt;
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
        entityName="this driver record"
        entityType="Driver"
      />

      {isModalOpen && (
        <DriverModal
          driver={modalDriver}
          onClose={() => setIsModalOpen(false)}
        />
      )}
    </div>
  );
}
