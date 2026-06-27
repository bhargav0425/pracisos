import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQuery } from '../../shared/api/baseQuery';

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

export const bookingApi = createApi({
  reducerPath: 'bookingApi',
  baseQuery: baseQuery,
  tagTypes: ['Practitioner', 'Slot', 'Booking'],
  endpoints: (builder) => ({
    getPractitioners: builder.query<Practitioner[], void>({
      query: () => '/booking/practitioners',
      providesTags: ['Practitioner'],
    }),
    getPractitioner: builder.query<Practitioner, string>({
      query: (id) => `/booking/practitioners/${id}`,
      providesTags: (result, error, id) => [{ type: 'Practitioner', id }],
    }),
    getAvailableSlots: builder.query<TimeSlot[], { practitionerId: string; from: string; to: string }>({
      query: ({ practitionerId, from, to }) =>
        `/booking/practitioners/${practitionerId}/slots?from=${from}&to=${to}`,
      providesTags: ['Slot'],
    }),
    createBooking: builder.mutation<Booking, Partial<Booking>>({
      query: (body) => ({
        url: '/booking/appointments',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Slot', 'Booking'],
    }),
    getBookings: builder.query<Booking[], void>({
      query: () => '/booking/appointments',
      providesTags: ['Booking'],
    }),
    cancelBooking: builder.mutation<Booking, { bookingId: string; reason: string }>({
      query: ({ bookingId, reason }) => ({
        url: `/booking/appointments/${bookingId}/cancel`,
        method: 'PUT',
        body: { status: 'CANCELLED', reason },
      }),
      invalidatesTags: ['Slot', 'Booking'],
    }),
    completeBooking: builder.mutation<Booking, string>({
      query: (bookingId) => ({
        url: `/booking/appointments/${bookingId}/complete`,
        method: 'PUT',
      }),
      invalidatesTags: ['Booking'],
    }),
    markNoShow: builder.mutation<Booking, string>({
      query: (bookingId) => ({
        url: `/booking/appointments/${bookingId}/no-show`,
        method: 'PUT',
      }),
      invalidatesTags: ['Booking'],
    }),
  }),
});

export const {
  useGetPractitionersQuery,
  useGetPractitionerQuery,
  useGetAvailableSlotsQuery,
  useCreateBookingMutation,
  useGetBookingsQuery,
  useCancelBookingMutation,
  useCompleteBookingMutation,
  useMarkNoShowMutation,
} = bookingApi;
