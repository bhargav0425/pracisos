import { Practitioner } from '../api';

interface Props {
  practitioner: Practitioner;
  onSelect: (practitioner: Practitioner) => void;
  isSelected?: boolean;
}

export function PractitionerCard({ practitioner, onSelect, isSelected }: Props) {
  return (
    <button
      onClick={() => onSelect(practitioner)}
      data-testid="practitioner-card"
      className={`w-full rounded-lg border p-4 text-left transition-all hover:shadow-md ${
        isSelected
          ? 'border-teal-500 bg-teal-50/50 ring-2 ring-teal-200'
          : 'border-slate-200 bg-white hover:border-teal-300'
      }`}
    >
      <div className="flex items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-teal-100 text-teal-700 font-semibold text-lg">
          {practitioner.firstName[0]}{practitioner.lastName[0]}
        </div>
        <div className="flex-1">
          <h3 className="font-semibold text-slate-800">{practitioner.fullName}</h3>
          <p className="text-sm text-slate-500">{practitioner.email}</p>
        </div>
        <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${
          practitioner.status === 'ACTIVE'
            ? 'bg-emerald-100 text-emerald-600'
            : 'bg-slate-200 text-slate-500'
        }`}>
          {practitioner.status}
        </span>
      </div>
    </button>
  );
}
