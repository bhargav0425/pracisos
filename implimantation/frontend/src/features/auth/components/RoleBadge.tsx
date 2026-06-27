import React from 'react';

const roleStyles: Record<string, string> = {
  SYSTEM_ADMIN: 'bg-red-500/10 text-red-400 border-red-500/20',
  CLINIC_OWNER: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  PRACTITIONER: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
  RECEPTIONIST: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
  PATIENT: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
};

export function RoleBadge({ role }: { role: string }) {
  const style = roleStyles[role] || 'bg-slate-500/10 text-slate-400 border-slate-500/20';
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${style}`}>
      {role.replace('_', ' ')}
    </span>
  );
}
