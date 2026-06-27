import { ChevronLeft, ChevronRight, Calendar } from 'lucide-react';

interface Props {
  currentDate: Date;
  onNavigate: (newDate: Date) => void;
}

export function CalendarNavigation({ currentDate, onNavigate }: Props) {
  const handlePrevWeek = () => {
    const next = new Date(currentDate);
    next.setDate(currentDate.getDate() - 7);
    onNavigate(next);
  };

  const handleNextWeek = () => {
    const next = new Date(currentDate);
    next.setDate(currentDate.getDate() + 7);
    onNavigate(next);
  };

  const handleToday = () => {
    onNavigate(new Date());
  };

  const formatWeekRange = (date: Date) => {
    const start = new Date(date);
    // Find previous Sunday or Monday (let's use Monday as start of week)
    const day = start.getDay();
    const diff = start.getDate() - day + (day === 0 ? -6 : 1);
    const startOfWeek = new Date(start.setDate(diff));
    
    const endOfWeek = new Date(startOfWeek);
    endOfWeek.setDate(startOfWeek.getDate() + 6);

    const formatMonthDay = (d: Date) => {
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    };
    const formatYear = (d: Date) => {
      return d.getFullYear();
    };

    return `${formatMonthDay(startOfWeek)} – ${formatMonthDay(endOfWeek)}, ${formatYear(endOfWeek)}`;
  };

  return (
    <div className="flex items-center justify-between bg-white border border-slate-200/60 p-4 rounded-xl shadow-sm">
      <div className="flex items-center space-x-2">
        <button
          onClick={handleToday}
          className="px-3.5 py-1.5 bg-slate-50 border border-slate-200 text-slate-700 hover:bg-slate-100 font-semibold rounded-lg text-xs transition-all active:scale-95 flex items-center"
        >
          <Calendar className="w-3.5 h-3.5 mr-1.5 text-slate-500" /> Today
        </button>
        <div className="flex items-center border border-slate-200 rounded-lg overflow-hidden">
          <button
            onClick={handlePrevWeek}
            className="p-2 bg-slate-50 hover:bg-slate-100 text-slate-600 transition-all border-r border-slate-200"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <button
            onClick={handleNextWeek}
            className="p-2 bg-slate-50 hover:bg-slate-100 text-slate-600 transition-all"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>
      <span className="text-sm font-bold text-slate-700">{formatWeekRange(currentDate)}</span>
    </div>
  );
}
