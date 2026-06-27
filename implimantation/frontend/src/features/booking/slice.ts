import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface BookingState {
  selectedPractitionerId: string | null;
  selectedDate: string | null;
}

const initialState: BookingState = {
  selectedPractitionerId: null,
  selectedDate: null,
};

const bookingSlice = createSlice({
  name: 'booking',
  initialState,
  reducers: {
    setSelectedPractitionerId(state, action: PayloadAction<string | null>) {
      state.selectedPractitionerId = action.payload;
    },
    setSelectedDate(state, action: PayloadAction<string | null>) {
      state.selectedDate = action.payload;
    },
    clearBookingState(state) {
      state.selectedPractitionerId = null;
      state.selectedDate = null;
    },
  },
});

export const { setSelectedPractitionerId, setSelectedDate, clearBookingState } = bookingSlice.actions;
export default bookingSlice.reducer;
