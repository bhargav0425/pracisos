interface Props {
  status: 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW' | string;
}

const statusConfig = {
  CONFIRMED: { bg: 'bg-teal-50 text-teal-700 border-teal-100', label: 'Confirmed' },
  COMPLETED: { bg: 'bg-emerald-50 text-emerald-700 border-emerald-100', label: 'Completed' },
  CANCELLED: { bg: 'bg-rose-50 text-rose-700 border-rose-100', label: 'Cancelled' },
  NO_SHOW: { bg: 'bg-amber-50 text-amber-700 border-amber-100', label: 'No Show' },
};

export function StatusBadge({ status }: Props) {
  const config = statusConfig[status as keyof typeof statusConfig] || {
    bg: 'bg-slate-50 text-slate-700 border-slate-100',
    label: status,
  };

  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wider ${config.bg}`}>
      {config.label}
    </span>
  );
}
