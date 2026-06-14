import { useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { PlusCircle, Edit, Trash } from "lucide-react";
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

import { routeCodesApi } from "../api/route-codes.api";
import { routeCodeKeys } from "../api/route-codes.keys";
import { useDeleteRouteCode } from "../api/route-codes.mutations";
import type { RouteCode } from "../api/route-codes.types";
import { RouteCodeModal } from "./RouteCodeModal";

export function RouteCodesTab() {
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [modalRouteCode, setModalRouteCode] = useState<RouteCode | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const deleteMut = useDeleteRouteCode();

  const [search, setSearch] = useState("");
  const [{ pageIndex, pageSize }, setPagination] = useState({ pageIndex: 0, pageSize: 7 });

  const { data: response, isLoading, isError, error } = useQuery({
    queryKey: [...routeCodeKeys.lists(), { search, pageIndex, pageSize }],
    queryFn: () => routeCodesApi.getAll({ search, page: pageIndex, size: pageSize }),
  });

  const routeCodes = response?.content || [];
  const totalPages = response?.totalPages || 0;

  const columns = useMemo<ColumnDef<RouteCode>[]>(
    () => [
      {
        accessorKey: "code",
        header: "Route Code",
        cell: ({ row }) => (
          <div className="font-semibold text-foreground">
            {row.getValue("code")}
          </div>
        ),
      },
      {
        id: "paths",
        header: "Forward / Backward Paths",
        cell: ({ row }) => {
          const { forwardRoute, backwardRoute } = row.original;
          return (
            <div className="flex flex-col gap-1 text-[13px] text-muted-foreground">
              <div>
                <span className="font-medium text-foreground mr-1">FWD:</span>
                {forwardRoute?.name || "None"}
              </div>
              <div>
                <span className="font-medium text-foreground mr-1">BWD:</span>
                {backwardRoute?.name || "None"}
              </div>
            </div>
          );
        },
      },
      {
        id: "actions",
        header: () => <div className="text-right">Actions</div>,
        cell: ({ row }) => {
          const routeCode = row.original;
          return (
            <div className="flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity duration-200">
              <button
                onClick={() => {
                  setModalRouteCode(routeCode);
                  setIsModalOpen(true);
                }}
                className="p-2 hover:bg-primary/10 rounded-lg text-muted-foreground hover:text-primary transition-colors cursor-pointer"
                title="Edit Route Code"
              >
                <Edit className="h-[15px] w-[15px]" />
              </button>
              <button
                onClick={() => setDeleteId(routeCode.id)}
                className="p-2 hover:bg-error-container rounded-lg text-muted-foreground hover:text-error transition-colors cursor-pointer"
                title="Delete Route Code"
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

  // eslint-disable-next-line react-hooks/incompatible-library
  const table = useReactTable({
    data: routeCodes,
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
    <div className="space-y-4">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-bold text-foreground">Manage Route Codes</h2>
          <p className="text-[13px] text-muted-foreground">Map forward and backward relationships to route codes.</p>
        </div>
        <button
          onClick={() => {
            setModalRouteCode(null);
            setIsModalOpen(true);
          }}
          className="flex items-center gap-2 bg-primary text-primary-foreground hover:bg-primary/90 px-4 py-2 rounded-lg font-medium text-[13px] transition-all outline-none cursor-pointer"
        >
          <PlusCircle className="h-[16px] w-[16px]" />
          Add Route Code
        </button>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-border overflow-hidden">
        <div className="px-5 py-3 border-b border-border flex items-center bg-white">
            <input 
              type="text" 
              placeholder="Search route codes..." 
              value={search} 
              onChange={e => {
                  setSearch(e.target.value);
                  setPagination(prev => ({ ...prev, pageIndex: 0 }));
              }} 
              className="w-full md:max-w-xs h-9 px-3 text-[13px] border border-border rounded-md focus:border-primary focus:ring-1 focus:ring-primary/20 outline-none transition-all"
            />
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
                    Loading route codes...
                  </TableCell>
                </TableRow>
              ) : isError ? (
                <TableRow className="hover:bg-white border-transparent">
                  <TableCell colSpan={columns.length} className="h-32 text-center text-[13px] text-error">
                    Error loading route codes: {(error as Error)?.message}
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
                    <p className="text-[13px] font-medium text-foreground">No route codes found.</p>
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
        entityName="this route code"
        entityType="Route Code"
      />

      {isModalOpen && (
        <RouteCodeModal
          routeCode={modalRouteCode}
          onClose={() => setIsModalOpen(false)}
        />
      )}
    </div>
  );
}
