import { Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from '../layouts/AppLayout';
import PublicLayout from '../layouts/PublicLayout';
import Dashboard from '../pages/app/Dashboard';
import ScansList from '../pages/app/ScansList';
import TargetsList from '../pages/app/TargetsList';
import SchedulesList from '../pages/app/SchedulesList';
import ScanDetail from '../pages/app/ScanDetail';
import FindingsList from '../pages/app/FindingsList';
import ScanComparison from '../pages/app/ScanComparison';
import Notifications from '../pages/app/Notifications';
import UserProfile from '../pages/app/UserProfile';
import ReportsPage from '../pages/app/ReportsPage';
import Login from '../pages/auth/Login';
import Register from '../pages/auth/Register';
import AdminDashboard from '../pages/admin/AdminDashboard';
import LandingPage from '../pages/public/LandingPage';
import TermsPage from '../pages/public/TermsPage';
import ProtectedRoute from '../components/common/ProtectedRoute';
import RoleRoute from '../components/common/RoleRoute';

const AppRoutes = () => {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route element={<PublicLayout />}>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/terms" element={<TermsPage />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/scans" element={<ScansList />} />
          <Route path="/scans/:id" element={<ScanDetail />} />
          <Route path="/compare" element={<ScanComparison />} />
          <Route path="/notifications" element={<Notifications />} />
          <Route path="/targets" element={<TargetsList />} />
          <Route path="/schedules" element={<SchedulesList />} />
          <Route path="/findings" element={<FindingsList />} />
          <Route path="/profile" element={<UserProfile />} />
          <Route
            path="/admin"
            element={(
              <RoleRoute allowedRoles={['ADMIN']}>
                <AdminDashboard />
              </RoleRoute>
            )}
          />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/settings" element={<Navigate to="/admin" replace />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
};
export default AppRoutes;
