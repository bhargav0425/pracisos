import { useGetUsersQuery } from '../api';
import { RoleBadge } from './RoleBadge';
import { Users, RefreshCw, Loader2, Inbox } from 'lucide-react';

export function UserList() {
  const { data: users, isLoading, error, refetch } = useGetUsersQuery();

  const getInitials = (name: string) => {
    return name
      .split(' ')
      .map((n) => n[0])
      .join('')
      .toUpperCase();
  };

  return (
    <div className="w-full p-8 rounded-2xl bg-white border border-slate-200/80 shadow-xl shadow-slate-100/50">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center space-x-3">
          <div className="p-2.5 bg-teal-50 border border-teal-100 text-teal-600 rounded-xl">
            <Users className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-xl font-bold text-slate-800">Clinic Staff</h3>
            <p className="text-slate-500 text-xs mt-0.5">Manage access, roles, and status of users</p>
          </div>
        </div>

        <button
          onClick={refetch}
          disabled={isLoading}
          className="p-2 text-slate-500 hover:text-slate-800 rounded-lg border border-slate-200 hover:bg-slate-50 transition-all active:scale-95 disabled:opacity-50"
        >
          {isLoading ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <RefreshCw className="w-4 h-4" />
          )}
        </button>
      </div>

      {isLoading && (
        <div className="flex flex-col items-center justify-center py-12 text-slate-400 space-y-3">
          <Loader2 className="w-8 h-8 animate-spin text-teal-500" />
          <span className="text-sm">Fetching clinic users...</span>
        </div>
      )}

      {error && (
        <div className="p-4 bg-red-50 border border-red-100 rounded-xl text-red-600 text-sm">
          <span>Failed to load users: {(error as any).data?.message || 'Check your permissions.'}</span>
        </div>
      )}

      {!isLoading && !error && (!users || users.length === 0) && (
        <div className="flex flex-col items-center justify-center py-12 text-slate-400 border border-dashed border-slate-200 rounded-xl">
          <Inbox className="w-10 h-10 mb-2.5 text-slate-300" />
          <span className="text-sm">No staff members have been invited yet.</span>
        </div>
      )}

      {!isLoading && !error && users && users.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse">
            <thead>
              <tr className="border-b border-slate-100 text-left">
                <th className="pb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Staff Member</th>
                <th className="pb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Email</th>
                <th className="pb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Role</th>
                <th className="pb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {users.map((user) => (
                <tr key={user.userId} className="group hover:bg-slate-50/50 transition-colors">
                  <td className="py-3.5 flex items-center space-x-3">
                    <div className="w-9 h-9 rounded-full bg-teal-50 border border-teal-100 flex items-center justify-center text-xs font-bold text-teal-600">
                      {getInitials(user.fullName)}
                    </div>
                    <div>
                      <div className="font-semibold text-slate-800 text-sm group-hover:text-teal-600 transition-colors">{user.fullName}</div>
                      <div className="text-[10px] text-slate-400">ID: {user.userId.substring(0, 8)}...</div>
                    </div>
                  </td>
                  <td className="py-3.5 text-slate-600 text-sm">{user.email}</td>
                  <td className="py-3.5">
                    <RoleBadge role={user.role} />
                  </td>
                  <td className="py-3.5">
                    <span className="inline-flex items-center text-xs font-semibold space-x-1.5">
                      <span className={`w-1.5 h-1.5 rounded-full ${user.status === 'ACTIVE' ? 'bg-emerald-500' : 'bg-amber-500'}`} />
                      <span className={user.status === 'ACTIVE' ? 'text-emerald-600' : 'text-amber-600'}>
                        {user.status}
                      </span>
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
