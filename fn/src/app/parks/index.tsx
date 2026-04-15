import { useState } from "react"
import { PlusCircle, Edit, Trash, MapPin, ChevronLeft, ChevronRight, SlidersHorizontal, } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { parksApi } from "./api/parks.api"
import { useDeletePark, } from "./api/parks.mutations"
import { parkKeys } from "./api/parks.keys"
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

import { ConfirmDeleteModal } from "@/components/modals/ConfirmDeleteModal"

import type { Park } from "./api/parks.types"

export default function Parks() {
    const navigate = useNavigate()
    
    const [deletePark, setDeletePark] = useState<Park | null>(null);

    const deleteParkMut = useDeletePark()

    const { data: parks = [], isLoading, isError, error } = useQuery({
        queryKey: parkKeys.lists(),
        queryFn: parksApi.getAll,
    })

    const columns: ColumnDef<Park>[] = [
        {
            accessorKey: "name",
            header: "Park Name",
            cell: ({ row }) => {
                const name = row.getValue("name") as string
                const id = row.original.id.slice(0, 8).toUpperCase()
                return (
                    <div className="flex items-center gap-3.5 py-1">
                        <div className="w-10 h-10 rounded-lg bg-primary/10 border border-primary/20 flex items-center justify-center text-primary shrink-0 transition-colors group-hover:bg-primary group-hover:text-on-primary">
                            <MapPin className="h-[18px] w-[18px]" />
                        </div>
                        <div className="flex flex-col gap-0.5">
                            <p className="text-[14px] font-semibold text-foreground tracking-tight">{name}</p>
                            <p className="text-[11px] text-muted-foreground font-medium tracking-wide">UK-{id}</p>
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
                    <div className="flex flex-col gap-0.5">
                        <p className="text-[14px] font-medium text-foreground tracking-tight">{shapeCount} Vertices Mapped</p>
                        <p className="text-[11px] text-muted-foreground tracking-wide">Zone Coordinate Area</p>
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
                
                const isNew = (new Date().getTime() - date.getTime()) < 86400000;
                
                if (isNew) {
                   return (
                       <div className="flex justify-center">
                           <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[10px] font-semibold bg-amber-50 text-amber-700 ring-1 ring-inset ring-amber-600/20">
                             <span className="relative flex h-1.5 w-1.5">
                                 <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75"></span>
                                 <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-amber-500"></span>
                             </span>
                             NEWLY MAPPED
                           </span>
                       </div>
                   ) 
                }

                return (
                    <div className="flex justify-center">
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[10px] font-semibold bg-emerald-50 text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
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
                const park = row.original

                return (
                    <div className="flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity duration-200">
                        <button 
                            className="p-2 hover:bg-primary/10 rounded-lg text-muted-foreground hover:text-primary transition-colors cursor-pointer"
                            onClick={() => {
                                navigate({ to: "/parks/$parkId/edit", params: { parkId: park.id } });
                            }}
                        >
                            <Edit className="h-[15px] w-[15px]" />
                        </button>
                        <button 
                            className="p-2 hover:bg-error-container rounded-lg text-muted-foreground hover:text-error transition-colors cursor-pointer"
                            onClick={() => setDeletePark(park)}
                        >
                            <Trash className="h-[15px] w-[15px]" />
                        </button>
                    </div>
                )
            },
        },
    ]

    const table = useReactTable({
        data: parks,
        columns,
        getCoreRowModel: getCoreRowModel(),
        getPaginationRowModel: getPaginationRowModel(),
        initialState: {
            pagination: {
                pageSize: 7,
            },
        },
    })

    return (
        <div className="flex-1 space-y-6 pt-6 max-w-7xl mx-auto w-full px-6 md:px-0">
            {/* Header Area */}
            <div className="flex flex-col md:flex-row md:items-end justify-between mb-8 gap-4">
                <div>
                  <span className="text-[11px] font-bold uppercase tracking-[0.2em] text-primary mb-1.5 block">Network Overview</span>
                  <h1 className="text-[32px] font-bold tracking-tight text-foreground leading-none">Parks Management</h1>
                </div>
                <div className="flex items-center space-x-3">
                    <button 
                        className="flex items-center gap-2 bg-primary text-primary-foreground hover:bg-primary/90 px-5 py-2.5 rounded-lg font-medium text-[13px] shadow-sm transition-all outline-none cursor-pointer"
                        onClick={() => navigate({ to: "/parks/new" })}
                    >
                        <PlusCircle className="h-[18px] w-[18px]" />
                        <span>Add New Park</span>
                    </button>
                </div>
            </div>

            {/* Main Data Container */}
            <div className="bg-white rounded-lg shadow-sm border border-border overflow-hidden">
                
                {/* Tabs & Controls */}
                <div className="px-5 py-3 border-b border-border flex items-center justify-between bg-white">
                    <div className="flex items-center p-1 bg-surface-container-lowest border border-border rounded-lg">
                        <button className="bg-primary text-primary-foreground shadow-sm px-3.5 py-1.5 rounded-md text-[11px] font-bold tracking-wide cursor-default transition-all">ALL PARKS</button>
                        <button className="text-muted-foreground hover:text-primary px-4 py-1.5 rounded-md text-[11px] font-semibold tracking-wide cursor-pointer transition-colors">INACTIVE</button>
                    </div>
                    <button className="group flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border hover:bg-surface-container-lowest hover:border-primary/30 transition-colors text-[12px] font-semibold text-primary cursor-pointer">
                        <SlidersHorizontal className="h-3 w-3 text-primary transition-colors" />
                        Filter
                    </button>
                </div>

                <div className="overflow-x-auto bg-white">
                    <Table>
                        <TableHeader>
                            {table.getHeaderGroups().map((headerGroup) => (
                                <TableRow key={headerGroup.id} className="border-b border-border bg-white hover:bg-white">
                                    {headerGroup.headers.map((header) => {
                                        return (
                                            <TableHead 
                                                key={header.id} 
                                                className="h-11 px-5 text-left text-[11px] font-semibold text-muted-foreground uppercase tracking-widest bg-white"
                                            >
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
                        <TableBody className="divide-y divide-border bg-white">
                            {isLoading ? (
                                <TableRow className="hover:bg-white border-transparent">
                                    <TableCell colSpan={columns.length} className="h-32 text-center text-[13px] text-muted-foreground">
                                        <div className="flex flex-col items-center justify-center gap-3">
                                            <div className="w-5 h-5 border-2 border-primary/20 border-t-primary rounded-full animate-spin"></div>
                                            Loading park infrastructure...
                                        </div>
                                    </TableCell>
                                </TableRow>
                            ) : isError ? (
                                <TableRow className="hover:bg-white border-transparent">
                                    <TableCell colSpan={columns.length} className="h-32 text-center text-[13px] text-error font-medium">
                                        Error loading parks. {(error as Error)?.message}
                                    </TableCell>
                                </TableRow>
                            ) : table.getRowModel().rows?.length ? (
                                table.getRowModel().rows.map((row) => (
                                    <TableRow
                                        key={row.id}
                                        data-state={row.getIsSelected() && "selected"}
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
                                        <div className="flex flex-col items-center justify-center gap-2">
                                            <MapPin className="h-8 w-8 text-primary/20 mb-1" />
                                            <p className="text-[13px] font-medium text-foreground">No parks defined</p>
                                            <p className="text-[12px]">Tap "Add New Park" to map your first transit zone.</p>
                                        </div>
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </div>
                
                <div className="px-5 py-3 bg-white flex items-center justify-between border-t border-border">
                    <p className="text-[12px] text-muted-foreground font-medium">
                        Showing <span className="font-semibold text-primary">{parks.length === 0 ? 0 : table.getState().pagination.pageIndex * table.getState().pagination.pageSize + 1}</span> to <span className="font-semibold text-primary">{Math.min((table.getState().pagination.pageIndex + 1) * table.getState().pagination.pageSize, parks.length)}</span> of <span className="font-semibold text-primary">{parks.length}</span>
                    </p>
                    <div className="flex gap-1.5">
                        <button 
                            onClick={() => table.previousPage()}
                            disabled={!table.getCanPreviousPage()}
                            className="h-8 w-8 flex items-center justify-center rounded-md border border-border text-muted-foreground hover:bg-surface-container-low hover:text-primary hover:border-primary/30 transition-colors disabled:opacity-30 disabled:hover:bg-transparent cursor-pointer"
                        >
                            <ChevronLeft className="h-[16px] w-[16px]" strokeWidth={2} />
                        </button>

                        {Array.from({ length: table.getPageCount() }).map((_, i) => (
                            <button
                                key={i}
                                onClick={() => table.setPageIndex(i)}
                                className={`h-8 min-w-[32px] px-2 flex items-center justify-center rounded-md border text-[12px] font-bold cursor-pointer transition-all ${
                                    table.getState().pagination.pageIndex === i 
                                      ? "bg-primary text-primary-foreground border-primary" 
                                      : "border-border text-muted-foreground hover:bg-surface-container-low hover:text-primary hover:border-primary/30"
                                }`}
                            >
                                {i + 1}
                            </button>
                        ))}

                        <button 
                            onClick={() => table.nextPage()}
                            disabled={!table.getCanNextPage()}
                            className="h-8 w-8 flex items-center justify-center rounded-md border border-border text-muted-foreground hover:bg-surface-container-low hover:text-primary hover:border-primary/30 transition-colors disabled:opacity-30 disabled:hover:bg-transparent cursor-pointer"
                        >
                            <ChevronRight className="h-[16px] w-[16px]" strokeWidth={2} />
                        </button>
                    </div>
                </div>
            </div>

            {/* DELETE MODAL */}
            <ConfirmDeleteModal 
                isOpen={!!deletePark}
                onClose={() => setDeletePark(null)}
                onConfirm={() => deletePark && deleteParkMut.mutate(deletePark.id)}
                entityName={deletePark?.name}
                entityType="Park"
            />
        </div>
    )
}