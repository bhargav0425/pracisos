import { UserInviteForm } from './UserInviteForm';
import { UserList } from './UserList';
import { useAuth } from '../../../shared/hooks/useAuth';
import { useDispatch } from 'react-redux';
import { logout } from '../slice';
import { useNavigate, useParams } from 'react-router-dom';
import { LogOut, LayoutDashboard, Calendar, ShieldCheck, CreditCard } from 'lucide-react';
import { useGetTenantQuery } from '../api';

export function ClinicDashboard() {
  const { user } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const dispatch = useDispatch();
  const navigate = useNavigate();

  const { data: tenant } = useGetTenantQuery(tenantSlug || '', {
    skip: !tenantSlug || user?.role === 'SYSTEM_ADMIN',
  });

  const handleLogout = () => {
    dispatch(logout());
    navigate('/login');
  };

  const isOwner = user?.role === 'CLINIC_OWNER';

  return (
    <div className="min-h-screen p-6 md:p-10 text-slate-700 bg-[#f4f7f6]">
      <header className="flex flex-col md:flex-row md:items-center md:justify-between border-b border-slate-200 pb-6 mb-8 gap-4">
        <div>
          <div className="flex items-center space-x-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-teal-600">Clinic Portal</span>
            <span className="text-slate-400">•</span>
            <span className="text-xs font-semibold text-slate-500 tracking-wider">/{tenantSlug}</span>
          </div>
          <h1 className="text-3xl font-extrabold tracking-tight text-slate-800 mt-1">
            {tenant ? tenant.name : user?.tenantSlug ? user.tenantSlug.toUpperCase().replace('-', ' ') : 'Practice Management'}
          </h1>
          <p className="text-slate-500 text-sm mt-0.5">Welcome back, {user?.fullName} ({user?.role})</p>
        </div>

        <button
          onClick={handleLogout}
          className="self-start flex items-center px-4 py-2 bg-white border border-slate-200 hover:bg-slate-50 text-slate-600 hover:text-slate-800 rounded-xl shadow-sm transition-all active:scale-95 text-sm"
        >
          <LogOut className="w-4 h-4 mr-2" />
          Sign Out
        </button>
      </header>

      {isOwner ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-1">
            <UserInviteForm />
          </div>
          <div className="lg:col-span-2">
            <UserList />
          </div>
        </div>
      ) : (
        <div className="max-w-4xl mx-auto grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="md:col-span-3 p-8 rounded-2xl bg-white border border-slate-200/80 shadow-xl shadow-slate-100/50 flex flex-col items-center justify-center text-center space-y-4">
            <div className="p-4 bg-teal-50 border border-teal-100 text-teal-600 rounded-full">
              <LayoutDashboard className="w-10 h-10" />
            </div>
            <div className="max-w-md">
              <h2 className="text-2xl font-bold text-slate-800">Dashboard Under Development</h2>
              <p className="text-slate-500 text-sm mt-2">
                You have successfully authenticated as a <span className="font-semibold text-teal-600">{user?.role}</span>. Subsequent development phases will build your custom dashboard slice.
              </p>
            </div>
          </div>

          <div className="p-6 rounded-xl bg-white border border-slate-200/60 shadow-sm flex items-start space-x-4">
            <Calendar className="w-6 h-6 text-teal-500 shrink-0 mt-1" />
            <div>
              <h4 className="font-bold text-slate-800">Booking Service</h4>
              <p className="text-slate-500 text-xs mt-1">Availability templates, practitioner caches, and appointment bookings will run here in Phase 2.</p>
            </div>
          </div>

          <div className="p-6 rounded-xl bg-white border border-slate-200/60 shadow-sm flex items-start space-x-4">
            <ShieldCheck className="w-6 h-6 text-teal-500 shrink-0 mt-1" />
            <div>
              <h4 className="font-bold text-slate-800">Charting Service</h4>
              <p className="text-slate-400 text-xs mt-1">Clinical notes, draft saving with autosave, and immutable document locking will run in Phase 4.</p>
            </div>
          </div>

          <div className="p-6 rounded-xl bg-white border border-slate-200/60 shadow-sm flex items-start space-x-4">
            <CreditCard className="w-6 h-6 text-teal-500 shrink-0 mt-1" />
            <div>
              <h4 className="font-bold text-slate-800">Billing Service</h4>
              <p className="text-slate-500 text-xs mt-1">Invoices, Stripe payments integration, and revenue analytics dashboard will run in Phase 5.</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
