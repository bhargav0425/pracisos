export interface Practitioner {
  practitionerId: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  status: string;
}

export interface TimeSlot {
  slotId: string;
  practitionerId: string;
  startTime: string;
  endTime: string;
  status: string;
}

export interface Booking {
  bookingId: string;
  slotId: string;
  patientId: string;
  practitionerId: string;
  appointmentType: string;
  status: 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
  notes: string;
  startTime: string;
  endTime: string;
  createdAt: string;
  cancelledAt: string | null;
  completedAt: string | null;
}
