import { createBrowserRouter, Navigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import type { RootState } from './store';
import { LoginForm } from '../features/auth/components/LoginForm';
import { AdminDashboard } from '../features/auth/components/AdminDashboard';
import { ClinicDashboard } from '../features/auth/components/ClinicDashboard';

function ProtectedRoute({ children, allowedRoles }: { 
  children: React.ReactNode; 
  allowedRoles?: string[];
}) {
  const { isAuthenticated, user } = useSelector((state: RootState) => state.auth);

  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (allowedRoles && user && !allowedRoles.includes(user.role)) {
    return <Navigate to="/unauthorized" replace />;
  }
  return <>{children}</>;
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: (
      <div className="min-h-screen flex items-center justify-center p-4">
        <LoginForm />
      </div>
    ),
  },
  {
    path: '/admin/dashboard',
    element: (
      <ProtectedRoute allowedRoles={['SYSTEM_ADMIN']}>
        <AdminDashboard />
      </ProtectedRoute>
    ),
  },
  {
    path: '/:tenantSlug/dashboard',
    element: (
      <ProtectedRoute allowedRoles={['CLINIC_OWNER', 'PRACTITIONER', 'RECEPTIONIST', 'PATIENT']}>
        <ClinicDashboard />
      </ProtectedRoute>
    ),
  },
  {
    path: '/unauthorized',
    element: (
      <div className="min-h-screen flex flex-col items-center justify-center p-4 text-center">
        <h2 className="text-3xl font-extrabold text-red-500">403 - Access Forbidden</h2>
        <p className="text-slate-400 text-sm mt-2">You do not have permissions to access this page.</p>
        <Navigate to="/login" replace />
      </div>
    ),
  },
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '*', element: <Navigate to="/login" replace /> },
]);
