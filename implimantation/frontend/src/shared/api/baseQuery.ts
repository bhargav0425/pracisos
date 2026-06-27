import { fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import type { RootState } from '../../app/store';

const isLocalDev = typeof window !== 'undefined' && window.location.port === '5173';

export const baseQuery = fetchBaseQuery({
  baseUrl: import.meta.env.VITE_API_URL || (isLocalDev ? 'http://localhost:8080/api/v1' : '/api/v1'),
  prepareHeaders: (headers, { getState }) => {
    const token = (getState() as RootState).auth.accessToken;
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  },
});
