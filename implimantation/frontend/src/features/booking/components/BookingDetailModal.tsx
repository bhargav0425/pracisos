import { X, Calendar, User, FileText, AlertTriangle } from 'lucide-react';
import { Booking } from '../api';
import { StatusBadge } from './StatusBadge';
import { useState } from 'react';

interface Props {
  booking: Booking | null;
  isOpen: boolean;
  onClose: () => void;
  onCancel: (reason: string) => void;
  onComplete: () => void;
  onNoShow: () => void;
  userRole: string;
}

export function BookingDetailModal({ booking, isOpen, onClose, onCancel, onComplete, onNoShow, userRole }: Props) {
  const [showCancelInput, setShowCancelInput] = useState(false);
  const [cancelReason, setCancelReason] = useState('');

  if (!isOpen || !booking) return null;

  const formatDateTime = (timeStr: string) => {
    if (!timeStr) return '';
    const d = new Date(timeStr);
    const date = d.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
    const time = d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true });
    return `${date} at ${time}`;
  };

  const handleCancelSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!cancelReason.trim()) return;
    onCancel(cancelReason);
    setShowCancelInput(false);
    setCancelReason('');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4">
      <div className="w-full max-w-lg bg-white rounded-2xl border border-slate-200 shadow-2xl overflow-hidden animate-in fade-in zoom-in duration-200">
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center space-x-2.5">
            <h3 className="text-lg font-bold text-slate-800">Appointment Details</h3>
            <StatusBadge status={booking.status} />
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 p-1 rounded-lg hover:bg-slate-100 transition-all">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 space-y-5">
          <div className="flex items-start space-x-3.5">
            <Calendar className="w-5 h-5 text-teal-600 shrink-0 mt-0.5" />
            <div>
              <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">Date & Time</div>
              <div className="text-sm font-bold text-slate-700 mt-0.5">{formatDateTime(booking.startTime)}</div>
            </div>
          </div>

          <div className="flex items-start space-x-3.5">
            <User className="w-5 h-5 text-teal-600 shrink-0 mt-0.5" />
            <div>
              <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">Practitioner / Patient</div>
              <div className="text-sm font-bold text-slate-700 mt-0.5">
                Type: <span className="font-semibold text-teal-600">{booking.appointmentType.replace('_', ' ')}</span>
              </div>
            </div>
          </div>

          {booking.notes && (
            <div className="flex items-start space-x-3.5">
              <FileText className="w-5 h-5 text-teal-600 shrink-0 mt-0.5" />
              <div className="flex-1">
                <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">Notes</div>
                <p className="text-sm text-slate-600 bg-slate-50 border border-slate-100 p-3 rounded-xl mt-1 leading-relaxed">
                  {booking.notes}
                </p>
              </div>
            </div>
          )}

          {booking.status === 'CANCELLED' && booking.cancellationReason && (
            <div className="flex items-start space-x-3.5 p-3 bg-red-50/50 border border-red-100 rounded-xl">
              <AlertTriangle className="w-5 h-5 text-rose-500 shrink-0 mt-0.5" />
              <div>
                <div className="text-xs font-bold text-rose-700 uppercase tracking-wider">Cancellation Reason</div>
                <p className="text-sm text-rose-600 mt-0.5">{booking.cancellationReason}</p>
              </div>
            </div>
          )}

          {showCancelInput && (
            <form onSubmit={handleCancelSubmit} className="border-t border-slate-100 pt-4 space-y-3">
              <label htmlFor="reason" className="block text-xs font-semibold uppercase tracking-wider text-slate-500">Provide Cancellation Reason</label>
              <input
                id="reason"
                type="text"
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                placeholder="Patient request, emergency, rescheduling..."
                className="block w-full px-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
                required
              />
              <div className="flex justify-end space-x-2">
                <button
                  type="button"
                  onClick={() => setShowCancelInput(false)}
                  className="px-3.5 py-1.5 bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 rounded-lg text-xs font-semibold"
                >
                  Back
                </button>
                <button
                  type="submit"
                  className="px-3.5 py-1.5 bg-rose-600 text-white hover:bg-rose-700 rounded-lg text-xs font-semibold shadow-sm"
                >
                  Confirm Cancel
                </button>
              </div>
            </form>
          )}
        </div>

        {!showCancelInput && booking.status === 'CONFIRMED' && (
          <div className="px-6 py-4 border-t border-slate-100 bg-slate-50/50 flex justify-end space-x-2">
            {(userRole === 'PATIENT' || userRole === 'RECEPTIONIST') && (
              <button
                onClick={() => setShowCancelInput(true)}
                className="px-4 py-2 bg-white border border-rose-200 hover:border-rose-300 text-rose-600 hover:bg-rose-50/40 rounded-xl text-xs font-semibold"
              >
                Cancel Appointment
              </button>
            )}
            {(userRole === 'PRACTITIONER' || userRole === 'RECEPTIONIST') && (
              <>
                <button
                  onClick={onNoShow}
                  className="px-4 py-2 bg-amber-500 hover:bg-amber-600 text-white rounded-xl text-xs font-semibold shadow-sm"
                >
                  Mark No Show
                </button>
                <button
                  onClick={onComplete}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-semibold shadow-sm"
                >
                  Mark Completed
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
