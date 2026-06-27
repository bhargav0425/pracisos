import { configureStore } from '@reduxjs/toolkit';
import { authApi } from '../features/auth/api';
import authReducer from '../features/auth/slice';
import { bookingApi } from '../features/booking/api';
import bookingReducer from '../features/booking/slice';
import { chartingApi } from '../features/charting/api';
import { billingApi } from '../features/billing/api';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    booking: bookingReducer,
    [authApi.reducerPath]: authApi.reducer,
    [bookingApi.reducerPath]: bookingApi.reducer,
    [chartingApi.reducerPath]: chartingApi.reducer,
    [billingApi.reducerPath]: billingApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(
      authApi.middleware,
      bookingApi.middleware,
      chartingApi.middleware,
      billingApi.middleware
    ),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
