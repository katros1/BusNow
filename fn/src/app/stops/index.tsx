import { PlusCircle, Edit, Trash, MapPin, ChevronLeft, ChevronRight } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { stopsApi } from "./api/stops.api"
import { useDeleteStop } from "./api/stops.mutations"
import { stopKeys } from "./api/stops.keys"
import { useNavigate } from "@tanstack/react-router"
import {
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  useReactTable,
} from "@tanstack/react-table"
import type { ColumnDef } from "@tanstack/react-table"

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"

import type { Stop } from "./api/stops.types"

export default function Stops() {
    const navigate = useNavigate()
    const deleteStop = useDeleteStop()

    const { data: stops = [], isLoading, isError, error } = useQuery({
        queryKey: stopKeys.lists(),
        queryFn: stopsApi.getAll,
    })

    const columns: ColumnDef<Stop>[] = [
        {
            accessorKey: "name",
            header: "Stop Name",
            cell: ({ row }) => {
                const name = row.getValue("name") as string
                const id = row.original.id.slice(0, 8).toUpperCase()
                return (
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-2xl bg-surface-container flex items-center justify-center text-primary shrink-0 transition-transform group-hover:scale-105">
                            <MapPin className="h-5 w-5" />
                        </div>
                        <div>
                            <p className="text-[13px] font-bold text-on-surface">{name}</p>
                            <p className="text-[10px] text-muted-foreground font-medium">ID: UK-{id}</p>
                        </div>
                    </div>
                )
            },
        },
        {
            accessorKey: "coordinates",
            header: "Location / Zone",
            cell: ({ row }) => {
                const coordinates: number[][] = row.getValue("coordinates")
                const shapeCount = coordinates?.length || 0
                return (
                    <div>
                        <p className="text-[13px] font-bold text-on-surface">{shapeCount} Vertices Mapped</p>
                        <p className="text-[10px] text-muted-foreground font-medium">Zone Coordinate Area</p>
                    </div>
                )
            },
        },
        {
            accessorKey: "createdAt",
            header: () => <div className="text-center">Status</div>,
            cell: ({ row }) => {
                const dateRaw = row.getValue("createdAt") as string
                const date = new Date(dateRaw)
                
                // Pretend stops created in the last 24h are "NEW", others are "ACTIVE"
                const isNew = (new Date().getTime() - date.getTime()) < 86400000;
                
                if (isNew) {
                   return (
                       <div className="flex justify-center">
                           <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-secondary-container text-on-secondary-container text-[10px] font-bold">
                             <span className="w-1.5 h-1.5 rounded-full bg-secondary animate-pulse"></span>
                             NEWLY MAPPED
                           </span>
                       </div>
                   ) 
                }

                return (
                    <div className="flex justify-center">
                        <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-green-50 text-green-700 text-[10px] font-bold dark:bg-green-900/30 dark:text-green-400">
                          <span className="w-1.5 h-1.5 rounded-full bg-green-500"></span>
                          ACTIVE
                        </span>
                    </div>
                )
            },
        },
        {
            id: "actions",
            header: () => <div className="text-right">Actions</div>,
            cell: ({ row }) => {
                const stop = row.original

                return (
                    <div className="flex items-center justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button 
                            className="p-2.5 hover:bg-surface-container-highest rounded-2xl text-muted-foreground hover:text-primary transition-all cursor-pointer hover:shadow-sm"
                        >
                            <Edit className="h-4 w-4" />
                        </button>
                        <button 
                            className="p-2.5 hover:bg-error-container rounded-2xl text-muted-foreground hover:text-error transition-all cursor-pointer hover:shadow-sm"
                            onClick={() => {
                                if (confirm(`Are you sure you want to delete ${stop.name}?`)) {
                                    deleteStop.mutate(stop.id)
                                }
                            }}
                        >
                            <Trash className="h-4 w-4" />
                        </button>
                    </div>
                )
            },
        },
    ]

    const table = useReactTable({
        data: stops,
        columns,
        getCoreRowModel: getCoreRowModel(),
        getPaginationRowModel: getPaginationRowModel(),
        initialState: {
            pagination: {
                pageSize: 7, // Adjust based on aesthetic density
            },
        },
    })

    return (
        <div className="flex-1 space-y-6 pt-6">
            <div className="flex flex-col md:flex-row md:items-end justify-between mb-10 gap-4">
                <div>
                  <span className="text-[12px] font-bold uppercase tracking-[0.2em] text-primary mb-2 block">Network Overview</span>
                  <h1 className="text-3xl font-extrabold tracking-tighter text-on-surface">Stops Management</h1>
                </div>
                <div className="flex items-center space-x-2">
                    <button 
                        className="flex items-center gap-2 bg-primary text-on-primary px-7 py-3.5 rounded-full font-bold shadow-lg shadow-primary/20 hover:scale-[1.02] active:scale-95 transition-all outline-none cursor-pointer"
                        onClick={() => navigate({ to: "/stops/new" })}
                    >
                        <PlusCircle className="h-5 w-5" />
                        Add New Stop
                    </button>
                </div>
            </div>

            <div className="bg-surface-container-lowest rounded-4xl shadow-sm overflow-hidden border border-border">
                <div className="px-6 py-5 border-b border-border flex items-center justify-between bg-surface-container-lowest">
                    <div className="flex gap-2">
                        <span className="bg-primary-fixed text-on-primary-fixed px-3 py-1 rounded-full text-[10px] font-bold">ALL STOPS</span>
                        <span className="bg-surface-container text-on-surface-variant px-3 py-1 rounded-full text-[10px] font-bold">INACTIVE</span>
                    </div>
                    <button className="text-primary text-[12px] font-bold flex items-center gap-1 cursor-pointer">
                        Filter
                    </button>
                </div>

                <Table>
                    <TableHeader>
                        {table.getHeaderGroups().map((headerGroup) => (
                            <TableRow key={headerGroup.id}>
                                {headerGroup.headers.map((header) => {
                                    return (
                                        <TableHead key={header.id}>
                                            {header.isPlaceholder
                                                ? null
                                                : flexRender(
                                                      header.column.columnDef.header,
                                                      header.getContext()
                                                  )}
                                        </TableHead>
                                    )
                                })}
                            </TableRow>
                        ))}
                    </TableHeader>
                    <TableBody className="divide-y divide-border">
                        {isLoading ? (
                            <TableRow>
                                <TableCell colSpan={columns.length} className="h-24 text-center text-muted-foreground">
                                    Loading transit networks...
                                </TableCell>
                            </TableRow>
                        ) : isError ? (
                            <TableRow>
                                <TableCell colSpan={columns.length} className="h-24 text-center text-red-500 font-medium">
                                    Error loading stops. {(error as Error)?.message}
                                </TableCell>
                            </TableRow>
                        ) : table.getRowModel().rows?.length ? (
                            table.getRowModel().rows.map((row) => (
                                <TableRow
                                    key={row.id}
                                    data-state={row.getIsSelected() && "selected"}
                                >
                                    {row.getVisibleCells().map((cell) => (
                                        <TableCell key={cell.id}>
                                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                        </TableCell>
                                    ))}
                                </TableRow>
                            ))
                        ) : (
                            <TableRow>
                                <TableCell colSpan={columns.length} className="h-24 text-center text-muted-foreground">
                                    No stops defined yet. Tap "Add New Stop" to mark zones on the map.
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
                
                <div className="px-6 py-4 bg-surface-container-lowest/50 flex items-center justify-between border-t border-border">
                    <p className="text-[11px] text-muted-foreground font-medium">
                        Showing {stops.length === 0 ? 0 : table.getState().pagination.pageIndex * table.getState().pagination.pageSize + 1} to {Math.min((table.getState().pagination.pageIndex + 1) * table.getState().pagination.pageSize, stops.length)} of {stops.length} entries
                    </p>
                    <div className="flex gap-1.5">
                        <button 
                            onClick={() => table.previousPage()}
                            disabled={!table.getCanPreviousPage()}
                            className="w-9 h-9 flex items-center justify-center rounded-2xl bg-surface-container-lowest border border-border text-muted-foreground hover:text-primary hover:bg-surface-container transition-all disabled:opacity-50 cursor-pointer"
                        >
                            <ChevronLeft className="h-4 w-4" />
                        </button>

                        {Array.from({ length: table.getPageCount() }).map((_, i) => (
                            <button
                                key={i}
                                onClick={() => table.setPageIndex(i)}
                                className={`w-9 h-9 flex items-center justify-center rounded-2xl border text-[11px] font-bold cursor-pointer transition-all ${
                                    table.getState().pagination.pageIndex === i 
                                      ? "bg-primary text-on-primary border-primary shadow-sm" 
                                      : "bg-surface-container-lowest border-border text-muted-foreground hover:text-primary hover:bg-surface-container"
                                }`}
                            >
                                {i + 1}
                            </button>
                        ))}

                        <button 
                            onClick={() => table.nextPage()}
                            disabled={!table.getCanNextPage()}
                            className="w-9 h-9 flex items-center justify-center rounded-2xl bg-surface-container-lowest border border-border text-muted-foreground hover:text-primary hover:bg-surface-container transition-all disabled:opacity-50 cursor-pointer"
                        >
                            <ChevronRight className="h-4 w-4" />
                        </button>
                    </div>
                </div>
            </div>
        </div>
    )
}