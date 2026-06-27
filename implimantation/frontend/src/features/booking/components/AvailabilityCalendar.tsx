import { TimeSlot } from '../api';

interface Props {
  slots: TimeSlot[];
  currentDate: Date;
  selectedSlotId?: string;
  onSelectSlot: (slot: TimeSlot) => void;
}

export function AvailabilityCalendar({ slots, currentDate, selectedSlotId, onSelectSlot }: Props) {
  // Generate the 7 days of the week starting from Monday
  const getDaysOfWeek = (baseDate: Date) => {
    const start = new Date(baseDate);
    const day = start.getDay();
    const diff = start.getDate() - day + (day === 0 ? -6 : 1); // adjust when day is sunday
    const monday = new Date(start.setDate(diff));
    
    const days = [];
    for (int i = 0; i < 7; i++) {
      const d = new Date(monday);
      d.setDate(monday.getDate() + i);
      days.push(d);
    }
    return days;
  };

  const days = getDaysOfWeek(currentDate);

  // Helper to format date as YYYY-MM-DD
  const formatDateKey = (d: Date) => {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  };

  // Helper to format time: "9:30 AM"
  const formatTime = (timeStr: string) => {
    return new Date(timeStr).toLocaleTimeString('en-US', {
      hour: 'numeric',
      minute: '2-digit',
      hour12: true
    });
  };

  // Group slots by date key
  const slotsByDate = slots.reduce((acc, slot) => {
    const d = new Date(slot.startTime);
    const key = formatDateKey(d);
    if (!acc[key]) acc[key] = [];
    acc[key].push(slot);
    return acc;
  }, {} as Record<string, TimeSlot[]>);

  return (
    <div className="grid grid-cols-7 gap-3 border border-slate-200/60 bg-white p-4 rounded-2xl shadow-sm overflow-x-auto min-w-[700px]">
      {days.map((day) => {
        const dateKey = formatDateKey(day);
        const daySlots = slotsByDate[dateKey] || [];
        // Sort slots by start time
        const sortedSlots = [...daySlots].sort((a, b) => a.startTime.localeCompare(b.startTime));

        const isToday = formatDateKey(new Date()) === dateKey;

        return (
          <div key={dateKey} className="flex flex-col min-h-[250px]">
            <div className={`text-center pb-3 border-b border-slate-100 mb-3 ${isToday ? 'bg-teal-50/50 rounded-xl p-1.5' : ''}`}>
              <div className="text-[11px] font-bold uppercase text-slate-400 tracking-wider">
                {day.toLocaleDateString('en-US', { weekday: 'short' })}
              </div>
              <div className={`text-lg font-extrabold mt-0.5 ${isToday ? 'text-teal-600' : 'text-slate-800'}`}>
                {day.getDate()}
              </div>
            </div>

            <div className="flex-1 space-y-2 max-h-[350px] overflow-y-auto pr-1">
              {sortedSlots.length > 0 ? (
                sortedSlots.map((slot) => {
                  const isAvailable = slot.status === 'AVAILABLE';
                  const isSelected = selectedSlotId === slot.slotId;
                  
                  return (
                    <button
                      key={slot.slotId}
                      onClick={() => onSelectSlot(slot)}
                      disabled={!isAvailable}
                      className={`w-full py-2 px-2.5 text-center text-xs font-semibold rounded-lg border transition-all ${
                        isSelected
                          ? 'border-teal-500 bg-teal-600 text-white shadow-sm hover:bg-teal-700'
                          : isAvailable
                          ? 'border-teal-100 bg-teal-50/40 text-teal-700 hover:bg-teal-50 hover:border-teal-300'
                          : 'border-slate-100 bg-slate-50 text-slate-400 cursor-not-allowed line-through'
                      }`}
                    >
                      {formatTime(slot.startTime)}
                    </button>
                  );
                })
              ) : (
                <div className="text-center py-6 text-[10px] text-slate-400 font-medium">
                  No Slots
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
