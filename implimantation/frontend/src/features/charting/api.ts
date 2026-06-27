import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQuery } from '../../shared/api/baseQuery';

export interface NoteContent {
  subjective: string;
  objective: string;
  assessment: string;
  plan: string;
  tags: string[];
}

export interface Note {
  noteId: string;
  patientId: string;
  practitionerId: string;
  bookingId: string;
  appointmentType: string;
  content: NoteContent;
  status: 'DRAFT' | 'IMMUTABLE';
  lockedAt: string | null;
  lockedBy: string | null;
  createdAt: string;
  updatedAt: string;
  amendments: Amendment[];
}

export interface Amendment {
  amendmentId: string;
  practitionerId: string;
  amendmentText: string;
  createdAt: string;
}

export const chartingApi = createApi({
  reducerPath: 'chartingApi',
  baseQuery: baseQuery,
  tagTypes: ['Note'],
  endpoints: (builder) => ({
    getNotesByPatient: builder.query<Note[], string>({
      query: (patientId) => `/charting/notes?patientId=${patientId}`,
      providesTags: ['Note'],
    }),
    getNote: builder.query<Note, string>({
      query: (noteId) => `/charting/notes/${noteId}`,
      providesTags: (result, error, id) => [{ type: 'Note', id }],
    }),
    saveDraft: builder.mutation<Note, { noteId: string; content: NoteContent }>({
      query: ({ noteId, content }) => ({
        url: `/charting/notes/${noteId}/draft`,
        method: 'POST',
        body: { content },
      }),
      invalidatesTags: (result, error, { noteId }) => [{ type: 'Note', id: noteId }],
    }),
    lockNote: builder.mutation<Note, string>({
      query: (noteId) => ({
        url: `/charting/notes/${noteId}/lock`,
        method: 'POST',
      }),
      invalidatesTags: (result, error, noteId) => [{ type: 'Note', id: noteId }],
    }),
    addAmendment: builder.mutation<Amendment, { noteId: string; text: string; practitionerId: string }>({
      query: ({ noteId, text, practitionerId }) => ({
        url: `/charting/notes/${noteId}/amendments`,
        method: 'POST',
        body: { practitionerId, amendmentText: text },
      }),
      invalidatesTags: (result, error, { noteId }) => [{ type: 'Note', id: noteId }],
    }),
  }),
});

export const {
  useGetNotesByPatientQuery,
  useGetNoteQuery,
  useSaveDraftMutation,
  useLockNoteMutation,
  useAddAmendmentMutation,
} = chartingApi;
