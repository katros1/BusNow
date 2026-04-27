import { useState } from "react"
import { PlusCircle, Edit, Trash, MapPin, ChevronLeft, ChevronRight, SlidersHorizontal, ListPlus } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { routesApi } from "./api/routes.api"
import { useDeleteRoute, } from "./api/routes.mutations"
import { routeKeys } from "./api/routes.keys"
import { useNavigate } from "@tanstack/react-router"
import {
  flexRender,
  getCoreRowModel,
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
import { ManageRouteStopsModal } from "./components/ManageRouteStopsModal"
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs"
import { RouteCodesTab } from "./components/RouteCodesTab"

import type { Route } from "./api/routes.types"

export default function Routes() {
    const navigate = useNavigate()
    
    const [deleteRoute, setDeleteRoute] = useState<Route | null>(null);
    const [manageStopsRoute, setManageStopsRoute] = useState<Route | null>(null);

    const deleteRouteMut = useDeleteRoute()

    const [search, setSearch] = useState("");
    const [{ pageIndex, pageSize }, setPagination] = useState({ pageIndex: 0, pageSize: 7 });

    const { data: response, isLoading, isError, error } = useQuery({
        queryKey: [...routeKeys.lists(), { search, pageIndex, pageSize }],
        queryFn: () => routesApi.getAll({ search, page: pageIndex, size: pageSize }),
    })
    
    const routes = response?.content || [];
    const totalPages = response?.totalPages || 0;

    const columns: ColumnDef<Route>[] = [
        {
            accessorKey: "name",
            header: "Route Name",
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
                const route = row.original

                return (
                    <div className="flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity duration-200">
                        <button 
                            className="p-2 hover:bg-emerald-100 rounded-lg text-muted-foreground hover:text-emerald-600 transition-colors cursor-pointer"
                            title="Manage Stops"
                            onClick={() => setManageStopsRoute(route)}
                        >
                            <ListPlus className="h-[15px] w-[15px]" />
                        </button>
                        <button 
                            className="p-2 hover:bg-primary/10 rounded-lg text-muted-foreground hover:text-primary transition-colors cursor-pointer"
                            title="Edit Route Boundaries"
                            onClick={() => {
                                navigate({ to: "/routes/$routeId/edit", params: { routeId: route.id } });
                            }}
                        >
                            <Edit className="h-[15px] w-[15px]" />
                        </button>
                        <button 
                            className="p-2 hover:bg-error-container rounded-lg text-muted-foreground hover:text-error transition-colors cursor-pointer"
                            onClick={() => setDeleteRoute(route)}
                        >
                            <Trash className="h-[15px] w-[15px]" />
                        </button>
                    </div>
                )
            },
        },
    ]

    const table = useReactTable({
        data: routes,
        columns,
        pageCount: totalPages,
        state: {
            pagination: { pageIndex, pageSize }
        },
        onPaginationChange: setPagination,
        manualPagination: true,
        getCoreRowModel: getCoreRowModel(),
    })

    return (
        <div className="flex-1 space-y-6 pt-6 max-w-7xl mx-auto w-full px-6 md:px-0">
            {/* Header Area */}
            <div className="flex flex-col md:flex-row md:items-end justify-between mb-8 gap-4">
                <div>
                  <span className="text-[11px] font-bold uppercase tracking-[0.2em] text-primary mb-1.5 block">Network Overview</span>
                  <h1 className="text-[32px] font-bold tracking-tight text-foreground leading-none">Routes Management</h1>
                </div>
                <div className="flex items-center space-x-3">
                    <button 
                        className="flex items-center gap-2 bg-primary text-primary-foreground hover:bg-primary/90 px-5 py-2.5 rounded-lg font-medium text-[13px] shadow-sm transition-all outline-none cursor-pointer"
                        onClick={() => navigate({ to: "/routes/new" })}
                    >
                        <PlusCircle className="h-[18px] w-[18px]" />
                        <span>Add New Route</span>
                    </button>
                </div>
            </div>

            <Tabs defaultValue="routes" className="w-full">
              <div className="flex items-center mb-6">
                <TabsList className="bg-surface-container-low h-10 border border-border rounded-lg p-1 space-x-1 shadow-sm">
                  <TabsTrigger value="routes" className="text-[13px] px-6 h-8 rounded-md font-semibold data-[state=active]:bg-white data-[state=active]:text-primary data-[state=active]:shadow-sm transition-all">Physical Trajectories</TabsTrigger>
                  <TabsTrigger value="codes" className="text-[13px] px-6 h-8 rounded-md font-semibold data-[state=active]:bg-white data-[state=active]:text-primary data-[state=active]:shadow-sm transition-all">Route Codes</TabsTrigger>
                </TabsList>
              </div>

              <TabsContent value="routes" className="outline-none m-0">
                {/* Main Data Container */}
                <div className="bg-card rounded-xl shadow-ambient border border-border/60 overflow-hidden">
                
                {/* Tabs & Controls */}
                <div className="px-5 py-3 border-b border-border flex items-center justify-between bg-white">
                    <div className="flex items-center p-1 bg-surface-container-lowest border border-border rounded-lg">
                        <button className="bg-primary text-primary-foreground shadow-sm px-3.5 py-1.5 rounded-md text-[11px] font-bold tracking-wide cursor-default transition-all">ALL ROUTES</button>
                        <button className="text-muted-foreground hover:text-primary px-4 py-1.5 rounded-md text-[11px] font-semibold tracking-wide cursor-pointer transition-colors">INACTIVE</button>
                    </div>
                    <div className="flex items-center gap-3">
                        <input 
                            type="text" 
                            placeholder="Search routes..." 
                            value={search} 
                            onChange={e => {
                                setSearch(e.target.value);
                                setPagination(prev => ({ ...prev, pageIndex: 0 }));
                            }} 
                            className="w-full md:w-64 h-8 px-3 text-[12px] border border-border rounded-md focus:border-primary outline-none transition-all"
                        />
                        <button className="group flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-border hover:bg-surface-container-lowest hover:border-primary/30 transition-colors text-[12px] font-semibold text-primary cursor-pointer">
                            <SlidersHorizontal className="h-3 w-3 text-primary transition-colors" />
                            Filter
                        </button>
                    </div>
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
                                            Loading route infrastructure...
                                        </div>
                                    </TableCell>
                                </TableRow>
                            ) : isError ? (
                                <TableRow className="hover:bg-white border-transparent">
                                    <TableCell colSpan={columns.length} className="h-32 text-center text-[13px] text-error font-medium">
                                        Error loading routes. {(error as Error)?.message}
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
                                            <p className="text-[13px] font-medium text-foreground">No routes defined</p>
                                            <p className="text-[12px]">Tap "Add New Route" to map your first transit zone.</p>
                                        </div>
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
                            <ChevronLeft className="h-[16px] w-[16px]" strokeWidth={2} />
                        </button>

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
          </TabsContent>
          <TabsContent value="codes" className="outline-none m-0">
             <RouteCodesTab />
          </TabsContent>
        </Tabs>

        <ConfirmDeleteModal 
                isOpen={!!deleteRoute}
                onClose={() => setDeleteRoute(null)}
                onConfirm={() => deleteRoute && deleteRouteMut.mutate(deleteRoute.id)}
                entityName={deleteRoute?.name}
                entityType="Route"
            />

            {/* MANAGE STOPS MODAL */}
            {manageStopsRoute && (
                <ManageRouteStopsModal
                     route={manageStopsRoute}
                     onClose={() => setManageStopsRoute(null)}
                />
            )}
        </div>
    )
}