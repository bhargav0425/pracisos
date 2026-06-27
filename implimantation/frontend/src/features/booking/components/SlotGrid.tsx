import { TimeSlot } from '../api';

interface Props {
  slots: TimeSlot[];
  selectedSlotId?: string;
  onSelect: (slot: TimeSlot) => void;
}

export function SlotGrid({ slots, selectedSlotId, onSelect }: Props) {
  // Group slots by date (local date YYYY-MM-DD)
  const grouped = slots.reduce((acc, slot) => {
    const d = new Date(slot.startTime);
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const dateStr = `${year}-${month}-${day}`;
    
    if (!acc[dateStr]) acc[dateStr] = [];
    acc[dateStr].push(slot);
    return acc;
  }, {} as Record<string, TimeSlot[]>);

  // Helper to format date header: "Monday, June 27"
  const formatDateHeader = (dateStr: string) => {
    const parts = dateStr.split('-');
    const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
    return d.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
  };

  // Helper to format time slot: "9:30 AM"
  const formatTime = (timeStr: string) => {
    return new Date(timeStr).toLocaleTimeString('en-US', {
      hour: 'numeric',
      minute: '2-digit',
      hour12: true
    });
  };

  return (
    <div className="space-y-6">
      {Object.entries(grouped).map(([date, daySlots]) => (
        <div key={date}>
          <h4 className="mb-3 text-sm font-semibold text-slate-600 uppercase tracking-wide">
            {formatDateHeader(date)}
          </h4>
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-4 md:grid-cols-6">
            {daySlots.map((slot) => (
              <button
                key={slot.slotId}
                onClick={() => onSelect(slot)}
                disabled={slot.status !== 'AVAILABLE'}
                className={`rounded border px-3 py-2 text-sm font-medium transition-all ${
                  selectedSlotId === slot.slotId
                    ? 'border-teal-500 bg-teal-600 text-white shadow-md hover:bg-teal-700'
                    : slot.status === 'AVAILABLE'
                    ? 'border-teal-200 bg-teal-50/50 text-teal-700 hover:bg-teal-100/50'
                    : 'border-slate-200 bg-slate-100 text-slate-400 cursor-not-allowed'
                }`}
              >
                {formatTime(slot.startTime)}
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
