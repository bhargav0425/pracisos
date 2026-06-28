import { Booking } from '../api';

interface Props {
  bookings: Booking[];
  onCancel: (booking: Booking) => void;
  onComplete: (booking: Booking) => void;
  onNoShow: (booking: Booking) => void;
  userRole: string;
}

const statusConfig = {
  CONFIRMED: { bg: 'bg-teal-50 text-teal-700', label: 'Confirmed', dot: 'bg-teal-500' },
  COMPLETED: { bg: 'bg-emerald-50 text-emerald-700', label: 'Completed', dot: 'bg-emerald-500' },
  CANCELLED: { bg: 'bg-rose-50 text-rose-700', label: 'Cancelled', dot: 'bg-rose-500' },
  NO_SHOW: { bg: 'bg-amber-50 text-amber-700', label: 'No Show', dot: 'bg-amber-500' },
};

export function BookingList({ bookings, onCancel, onComplete, onNoShow, userRole }: Props) {
  const formatDateTime = (timeStr: string) => {
    if (!timeStr) return '';
    const d = new Date(timeStr);
    const date = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    const time = d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true });
    return `${date} at ${time}`;
  };

  if (!bookings.length) {
    return (
      <div className="rounded-xl border border-slate-200 bg-slate-50/50 p-8 text-center">
        <p className="text-slate-500 text-sm">No appointments found</p>
      </div>
    );
  }

  return (
    <div className="space-y-3.5">
      {bookings.map((booking) => {
        const status = statusConfig[booking.status] || { bg: 'bg-slate-100 text-slate-700', label: booking.status, dot: 'bg-slate-400' };
        return (
          <div
            key={booking.bookingId}
            data-testid="appointment-card"
            className="rounded-xl border border-slate-200/80 bg-white p-5 hover:shadow-md hover:border-slate-300/80 transition-all"
          >
            <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
              <div className="flex-1">
                <div className="flex flex-wrap items-center gap-2 mb-2">
                  <span className={`h-2 w-2 rounded-full ${status.dot}`} />
                  <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold tracking-wide uppercase ${status.bg}`}>
                    {status.label}
                  </span>
                  <span className="text-xs text-slate-500 font-medium">
                    {formatDateTime(booking.startTime)}
                  </span>
                </div>
                <h4 className="font-bold text-slate-800 text-base">{booking.appointmentType.replace('_', ' ')}</h4>
                {booking.notes && (
                  <p className="mt-1.5 text-sm text-slate-500 bg-slate-50 p-2.5 rounded-lg border border-slate-100">{booking.notes}</p>
                )}
              </div>
              <div className="flex gap-2 self-end sm:self-start">
                {booking.status === 'CONFIRMED' && (userRole === 'PATIENT' || userRole === 'RECEPTIONIST') && (
                  <button
                    onClick={() => onCancel(booking)}
                    className="rounded-lg border border-rose-200 hover:border-rose-300 px-3 py-1.5 text-xs font-semibold text-rose-600 hover:bg-rose-50/50 transition-all"
                  >
                    Cancel
                  </button>
                )}
                {booking.status === 'CONFIRMED' && (userRole === 'PRACTITIONER' || userRole === 'RECEPTIONIST') && (
                  <>
                    <button
                      onClick={() => onComplete(booking)}
                      className="rounded-lg bg-emerald-600 hover:bg-emerald-700 px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm transition-all"
                    >
                      Complete
                    </button>
                    <button
                      onClick={() => onNoShow(booking)}
                      className="rounded-lg bg-amber-500 hover:bg-amber-600 px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm transition-all"
                    >
                      No Show
                    </button>
                  </>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}
