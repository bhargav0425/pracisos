import { useState, useMemo } from 'react';
import { useCreateBookingMutation, useGetPractitionersQuery, useGetAvailableSlotsQuery } from '../api';
import { PractitionerCard } from './PractitionerCard';
import { SlotGrid } from './SlotGrid';
import { useAuth } from '../../../shared/hooks/useAuth';
import { Loader2, CalendarRange, UserCheck, AlertCircle, CheckCircle2 } from 'lucide-react';

export function BookingForm() {
  const { user } = useAuth();
  const { data: practitioners, isLoading: loadingPracs } = useGetPractitionersQuery();
  const [selectedPractitioner, setSelectedPractitioner] = useState<string | null>(null);
  const [selectedSlot, setSelectedSlot] = useState<string | null>(null);
  const [appointmentType, setAppointmentType] = useState('CONSULTATION');
  const [notes, setNotes] = useState('');
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  
  const [createBooking, { isLoading: isBooking }] = useCreateBookingMutation();

  // Get slots for next 7 days in UTC ISO format (stable across renders)
  const { from, to } = useMemo(() => {
    return {
      from: new Date().toISOString(),
      to: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString()
    };
  }, [selectedPractitioner]);
  
  const { data: slots, isLoading: loadingSlots } = useGetAvailableSlotsQuery(
    { practitionerId: selectedPractitioner!, from, to },
    { skip: !selectedPractitioner }
  );

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedPractitioner || !selectedSlot || !user) return;
    
    try {
      setSuccessMsg(null);
      setErrorMsg(null);
      
      // If receptionist, we can book for a mock patient, otherwise use logged in user
      const targetPatientId = user.role === 'PATIENT' ? user.userId : 'c8e88edb-1f85-48c7-b8dc-1926d27b0437'; // seeded/mock patient ID

      await createBooking({
        slotId: selectedSlot,
        practitionerId: selectedPractitioner,
        patientId: targetPatientId,
        appointmentType,
        notes,
      }).unwrap();

      setSuccessMsg('Appointment booked successfully! Your slot has been reserved.');
      setSelectedSlot(null);
      setNotes('');
    } catch (err: any) {
      setErrorMsg(err?.data?.message || 'Failed to confirm booking. The slot may have already been taken.');
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      {successMsg && (
        <div data-testid="success-banner" className="p-4 bg-emerald-50 border border-emerald-100 text-emerald-600 rounded-xl flex items-start text-sm">
          <CheckCircle2 className="w-5 h-5 mr-3 shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      {errorMsg && (
        <div data-testid="error-banner" className="p-4 bg-rose-50 border border-rose-100 text-rose-600 rounded-xl flex items-start text-sm">
          <AlertCircle className="w-5 h-5 mr-3 shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      <div className="rounded-2xl border border-slate-200/80 bg-white p-6 md:p-8 shadow-sm">
        <h2 className="text-xl font-bold text-slate-800 mb-5 flex items-center">
          <UserCheck className="w-5 h-5 mr-2.5 text-teal-600" /> Select a Practitioner
        </h2>
        {loadingPracs ? (
          <div className="flex justify-center py-6">
            <Loader2 className="w-6 h-6 animate-spin text-teal-600" />
          </div>
        ) : practitioners && practitioners.length > 0 ? (
          <div className="grid gap-4 sm:grid-cols-2">
            {practitioners.map((p) => (
              <PractitionerCard
                key={p.practitionerId}
                practitioner={p}
                isSelected={selectedPractitioner === p.practitionerId}
                onSelect={(prac) => {
                  setSelectedPractitioner(prac.practitionerId);
                  setSelectedSlot(null);
                  setSuccessMsg(null);
                  setErrorMsg(null);
                }}
              />
            ))}
          </div>
        ) : (
          <p className="text-slate-400 text-sm text-center py-4">No active practitioners found in this clinic.</p>
        )}
      </div>

      {selectedPractitioner && (
        <div className="rounded-2xl border border-slate-200/80 bg-white p-6 md:p-8 shadow-sm">
          <h2 className="text-xl font-bold text-slate-800 mb-5 flex items-center">
            <CalendarRange className="w-5 h-5 mr-2.5 text-teal-600" /> Choose an Available Time
          </h2>
          {loadingSlots ? (
            <div className="flex justify-center py-6">
              <Loader2 className="w-6 h-6 animate-spin text-teal-600" />
            </div>
          ) : slots && slots.length > 0 ? (
            <SlotGrid
              slots={slots}
              selectedSlotId={selectedSlot || undefined}
              onSelect={(slot) => {
                setSelectedSlot(slot.slotId);
                setSuccessMsg(null);
                setErrorMsg(null);
              }}
            />
          ) : (
            <p className="text-slate-400 text-sm text-center py-4">No available time slots found for the next 7 days.</p>
          )}
        </div>
      )}

      {selectedSlot && (
        <form onSubmit={handleSubmit} className="rounded-2xl border border-slate-200/80 bg-white p-6 md:p-8 shadow-sm space-y-5">
          <h2 className="text-xl font-bold text-slate-800 border-b border-slate-100 pb-3">Appointment Details</h2>
          
          <div className="space-y-4">
            <div>
              <label htmlFor="appointmentType" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Appointment Type</label>
              <select
                id="appointmentType"
                value={appointmentType}
                onChange={(e) => setAppointmentType(e.target.value)}
                className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
              >
                <option value="CONSULTATION">Initial Consultation</option>
                <option value="FOLLOW_UP">Standard Follow-up</option>
                <option value="TREATMENT">Therapy / Treatment Session</option>
              </select>
            </div>
            
            <div>
              <label htmlFor="notes" className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">Reason for Visit / Notes (Optional)</label>
              <textarea
                id="notes"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
                className="block w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-teal-500/30 focus:border-teal-500 transition-all"
                placeholder="Briefly describe the symptoms or reason for booking..."
              />
            </div>
            
            <button
              type="submit"
              disabled={isBooking}
              className="w-full flex items-center justify-center py-3 px-4 bg-teal-600 hover:bg-teal-700 text-white font-semibold rounded-xl transition-all shadow-md shadow-teal-500/10 active:scale-[0.98] disabled:opacity-50"
            >
              {isBooking ? (
                <>
                  <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                  Reserving Slot...
                </>
              ) : (
                'Confirm Booking'
              )}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
