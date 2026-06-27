import { describe, it, expect } from 'vitest';
import authReducer, { setCredentials, logout } from '../../features/auth/slice';

describe('authSlice', () => {
  const initialState = {
    user: null,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    isLoading: false,
  };

  it('should return initial state when passed an empty action', () => {
    expect(authReducer(undefined, { type: '' })).toEqual(initialState);
  });

  it('should set credentials', () => {
    const user = {
      userId: '123',
      email: 'test@clinic.com',
      fullName: 'Test User',
      role: 'CLINIC_OWNER',
      tenantId: 'tenant-123',
      tenantSlug: 'test-clinic',
    };
    const nextState = authReducer(initialState, setCredentials({
      user,
      accessToken: 'access-123',
      refreshToken: 'refresh-123',
    }));

    expect(nextState.isAuthenticated).toBe(true);
    expect(nextState.accessToken).toBe('access-123');
    expect(nextState.user).toEqual(user);
  });

  it('should clear credentials on logout', () => {
    const populatedState = {
      user: {
        userId: '123',
        email: 'test@clinic.com',
        fullName: 'Test User',
        role: 'CLINIC_OWNER',
        tenantId: 'tenant-123',
        tenantSlug: 'test-clinic',
      },
      accessToken: 'access-123',
      refreshToken: 'refresh-123',
      isAuthenticated: true,
      isLoading: false,
    };

    const nextState = authReducer(populatedState, logout());
    expect(nextState.isAuthenticated).toBe(false);
    expect(nextState.accessToken).toBeNull();
    expect(nextState.user).toBeNull();
  });
});
