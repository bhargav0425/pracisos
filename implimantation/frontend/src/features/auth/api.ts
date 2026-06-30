import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQuery } from '../../shared/api/baseQuery';
import { setCredentials, logout } from './slice';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  fullName: string;
  role: string;
  tenantId: string | null;
  tenantSlug: string | null;
}

export interface TenantCreateRequest {
  slug: string;
  name: string;
  ownerEmail: string;
  ownerPassword: string;
  ownerFirstName: string;
  ownerLastName: string;
}

export interface TenantResponse {
  tenantId: string;
  slug: string;
  name: string;
  status: string;
  createdAt: string;
}

export interface UserInviteRequest {
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}

export interface UserResponse {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  role: string;
  status: string;
  tenantId: string;
  createdAt: string;
}

export const authApi = createApi({
  reducerPath: 'authApi',
  baseQuery: baseQuery,
  tagTypes: ['Users', 'Tenants'],
  endpoints: (builder) => ({
    login: builder.mutation<LoginResponse, LoginRequest>({
      query: (credentials) => ({
        url: '/auth/login',
        method: 'POST',
        body: credentials,
      }),
      async onQueryStarted(_, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          dispatch(
            setCredentials({
              user: {
                userId: data.userId,
                email: data.email,
                fullName: data.fullName,
                role: data.role,
                tenantId: data.tenantId,
                tenantSlug: data.tenantSlug,
              },
              accessToken: data.accessToken,
              refreshToken: data.refreshToken,
            })
          );
        } catch {
          dispatch(logout());
        }
      },
    }),
    registerTenant: builder.mutation<TenantResponse, TenantCreateRequest>({
      query: (tenantData) => ({
        url: '/auth/register',
        method: 'POST',
        body: tenantData,
      }),
      invalidatesTags: ['Tenants'],
    }),
    getTenant: builder.query<TenantResponse, string>({
      query: (slug) => `/auth/tenants/${slug}`,
      providesTags: (result, error, slug) => [{ type: 'Tenants', id: slug }],
    }),
    getTenants: builder.query<TenantResponse[], void>({
      query: () => '/auth/tenants',
      providesTags: ['Tenants'],
    }),
    inviteUser: builder.mutation<UserResponse, UserInviteRequest>({
      query: (userData) => ({
        url: '/auth/users',
        method: 'POST',
        body: userData,
      }),
      invalidatesTags: ['Users'],
    }),
    getUsers: builder.query<UserResponse[], void>({
      query: () => '/auth/users',
      providesTags: ['Users'],
    }),
  }),
});

export const {
  useLoginMutation,
  useRegisterTenantMutation,
  useGetTenantQuery,
  useGetTenantsQuery,
  useInviteUserMutation,
  useGetUsersQuery,
} = authApi;
