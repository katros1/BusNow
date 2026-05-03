import { QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import { queryClient } from "./lib/query-client";
import { router } from "./router.tsx";
import { Toaster } from "@/components/ui/sonner";
import { AuthProvider } from "./lib/auth/AuthContext";
import "./App.css";

function App() {
  return (
    <AuthProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        <Toaster richColors closeButton />
      </QueryClientProvider>
    </AuthProvider>
  );
}

export default App;
