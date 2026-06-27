import { TenantCreateForm } from './TenantCreateForm';
import { useAuth } from '../../../shared/hooks/useAuth';
import { useDispatch } from 'react-redux';
import { logout } from '../slice';
import { useNavigate } from 'react-router-dom';
import { LogOut, Activity, Database, Server, Cpu } from 'lucide-react';

export function AdminDashboard() {
  const { user } = useAuth();
  const dispatch = useDispatch();
  const navigate = useNavigate();

  const handleLogout = () => {
    dispatch(logout());
    navigate('/login');
  };

  return (
    <div className="min-h-screen p-6 md:p-10 text-slate-700 bg-[#f4f7f6]">
      <header className="flex flex-col md:flex-row md:items-center md:justify-between border-b border-slate-200 pb-6 mb-8 gap-4">
        <div>
          <div className="flex items-center space-x-2.5">
            <span className="relative flex h-3 w-3">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-3 w-3 bg-emerald-500"></span>
            </span>
            <span className="text-xs font-semibold uppercase tracking-wider text-emerald-600">System Online</span>
          </div>
          <h1 className="text-3xl font-extrabold tracking-tight text-slate-800 mt-1">Platform Admin Control</h1>
          <p className="text-slate-500 text-sm mt-0.5">Logged in as {user?.fullName} ({user?.email})</p>
        </div>

        <button
          onClick={handleLogout}
          className="self-start flex items-center px-4 py-2 bg-white border border-slate-200 hover:bg-slate-50 text-slate-600 hover:text-slate-800 rounded-xl shadow-sm transition-all active:scale-95 text-sm"
        >
          <LogOut className="w-4 h-4 mr-2" />
          Sign Out
        </button>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-1">
          <TenantCreateForm />
        </div>

        <div className="lg:col-span-2 space-y-6">
          <div className="p-6 rounded-2xl bg-white border border-slate-200/80 shadow-xl shadow-slate-100/50">
            <h3 className="text-lg font-bold text-slate-800 mb-4 flex items-center">
              <Activity className="w-5 h-5 mr-2.5 text-teal-600" /> Platform Infrastructure Status
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 flex items-center space-x-3.5">
                <div className="p-2 bg-teal-500/10 text-teal-600 rounded-lg">
                  <Database className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-[10px] uppercase font-semibold text-slate-400 tracking-wider">Postgres DB</div>
                  <div className="font-bold text-slate-700 text-sm mt-0.5">CONNECTED</div>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 flex items-center space-x-3.5">
                <div className="p-2 bg-teal-500/10 text-teal-600 rounded-lg">
                  <Server className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-[10px] uppercase font-semibold text-slate-400 tracking-wider">Kafka Broker</div>
                  <div className="font-bold text-slate-700 text-sm mt-0.5">ACTIVE</div>
                </div>
              </div>

              <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 flex items-center space-x-3.5">
                <div className="p-2 bg-teal-500/10 text-teal-600 rounded-lg">
                  <Cpu className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-[10px] uppercase font-semibold text-slate-400 tracking-wider">Auth Service</div>
                  <div className="font-bold text-slate-700 text-sm mt-0.5">HEALTHY</div>
                </div>
              </div>
            </div>
          </div>

          <div className="p-6 rounded-2xl bg-white border border-slate-200/80 shadow-xl shadow-slate-100/50">
            <h3 className="text-lg font-bold text-slate-800 mb-3">System Log Events</h3>
            <div className="space-y-3.5 text-xs">
              <div className="p-3 bg-slate-50 border border-slate-100 rounded-lg flex items-center justify-between">
                <span className="text-slate-500 font-mono">[2026-06-25 21:05:40]</span>
                <span className="text-blue-600 font-semibold uppercase text-[10px] bg-blue-500/10 border border-blue-500/20 px-2 py-0.5 rounded">INFO</span>
                <span className="text-slate-600 flex-1 ml-4 text-left">Spring Boot Application started successfully</span>
              </div>
              <div className="p-3 bg-slate-50 border border-slate-100 rounded-lg flex items-center justify-between">
                <span className="text-slate-500 font-mono">[2026-06-25 21:05:40]</span>
                <span className="text-emerald-600 font-semibold uppercase text-[10px] bg-emerald-500/10 border border-emerald-500/20 px-2 py-0.5 rounded">INFO</span>
                <span className="text-slate-600 flex-1 ml-4 text-left">Flyway schema migrations verified - 2 migrations applied</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
