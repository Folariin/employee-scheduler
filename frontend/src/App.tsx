import { BrowserRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  Landing,
  Login,
  Protected,
  SessionLifecycle,
} from "./features/auth/Auth";
import { Layout } from "./components/layout/Layout";
import { AdminDashboard, EmployeeDashboard } from "./pages/Dashboards";
import { Employees } from "./pages/Employees";
import { AdminSchedules, ScheduleDetail } from "./pages/AdminSchedules";
import { EmployeeSchedule } from "./pages/EmployeeSchedule";
import { Availability } from "./pages/Availability";
import { Link } from "react-router-dom";
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 30_000, refetchOnWindowFocus: true },
    mutations: { retry: false },
  },
});
export function AppRoutes() {
  return (
    <>
      <SessionLifecycle />
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        <Route element={<Protected role="ADMIN" />}>
          <Route element={<Layout />}>
            <Route path="/admin/dashboard" element={<AdminDashboard />} />
            <Route path="/admin/employees" element={<Employees />} />
            <Route path="/admin/schedules" element={<AdminSchedules />} />
            <Route path="/admin/schedules/:id" element={<ScheduleDetail />} />
          </Route>
        </Route>
        <Route element={<Protected role="EMPLOYEE" />}>
          <Route element={<Layout />}>
            <Route path="/employee/dashboard" element={<EmployeeDashboard />} />
            <Route path="/employee/schedule" element={<EmployeeSchedule />} />
            <Route path="/employee/availability" element={<Availability />} />
          </Route>
        </Route>
        <Route
          path="*"
          element={
            <main id="main-content" className="not-found">
              <h1>This page isn’t on the schedule.</h1>
              <p>Let’s get you back to your workspace.</p>
              <Link className="button primary" to="/">
                Back to workspace
              </Link>
            </main>
          }
        />
      </Routes>
    </>
  );
}
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  );
}
