import { describe, it, expect } from 'vitest';
import bookingReducer, { 
  setSelectedPractitionerId, 
  setSelectedDate, 
  clearBookingState 
} from '../../features/booking/slice';

describe('bookingSlice', () => {
  const initialState = {
    selectedPractitionerId: null,
    selectedDate: null,
  };

  it('should return initial state when passed an empty action', () => {
    expect(bookingReducer(undefined, { type: '' })).toEqual(initialState);
  });

  it('should set selected practitioner ID', () => {
    const practitionerId = 'prac-123';
    const nextState = bookingReducer(initialState, setSelectedPractitionerId(practitionerId));
    expect(nextState.selectedPractitionerId).toBe(practitionerId);
  });

  it('should set selected date', () => {
    const date = '2026-06-27';
    const nextState = bookingReducer(initialState, setSelectedDate(date));
    expect(nextState.selectedDate).toBe(date);
  });

  it('should clear booking state', () => {
    const populatedState = {
      selectedPractitionerId: 'prac-123',
      selectedDate: '2026-06-27',
    };

    const nextState = bookingReducer(populatedState, clearBookingState());
    expect(nextState.selectedPractitionerId).toBeNull();
    expect(nextState.selectedDate).toBeNull();
  });
});
