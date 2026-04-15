import { useState } from "react";
import { Outlet } from "@tanstack/react-router";
import { Sidebar } from "./components/Sidebar";
import { Navbar } from "./components/Navbar";

export function DashboardLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      <div className="flex flex-1 flex-col overflow-hidden min-w-0">
        <Navbar onMenuClick={() => setSidebarOpen(true)} />
        <main className="flex-1 overflow-y-auto p-4 lg:p-5">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
