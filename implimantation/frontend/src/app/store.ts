import { configureStore } from '@reduxjs/toolkit';
import { authApi } from '../features/auth/api';
import authReducer from '../features/auth/slice';
import { bookingApi } from '../features/booking/api';
import bookingReducer from '../features/booking/slice';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    booking: bookingReducer,
    [authApi.reducerPath]: authApi.reducer,
    [bookingApi.reducerPath]: bookingApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(authApi.middleware, bookingApi.middleware),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
