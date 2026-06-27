import { createApi } from '@reduxjs/toolkit/query/react';
import { baseQuery } from '../../shared/api/baseQuery';

export interface Invoice {
  invoiceId: string;
  bookingId: string;
  patientId: string;
  practitionerId: string;
  amountCents: number;
  formattedAmount: string;
  status: 'ISSUED' | 'PAID' | 'OVERDUE' | 'REFUNDED' | 'CANCELLED';
  description: string;
  issuedAt: string;
  paidAt: string | null;
  cancelledAt: string | null;
  refundedAt: string | null;
  isNoShowPenalty: boolean;
  stripePaymentIntentId: string | null;
}

export interface Payment {
  paymentId: string;
  invoiceId: string;
  stripePaymentIntentId: string;
  amountCents: number;
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED';
  createdAt: string;
}

export interface Revenue {
  totalRevenueCents: number;
  formattedTotalRevenue: string;
  paidInvoiceCount: number;
  pendingRevenueCents: number;
  formattedPendingRevenue: string;
  refundedRevenueCents: number;
  formattedRefundedRevenue: string;
}

export const billingApi = createApi({
  reducerPath: 'billingApi',
  baseQuery: baseQuery,
  tagTypes: ['Invoice', 'Payment', 'Revenue'],
  endpoints: (builder) => ({
    getInvoices: builder.query<Invoice[], void>({
      query: () => '/billing/invoices',
      providesTags: ['Invoice'],
    }),
    getInvoice: builder.query<Invoice, string>({
      query: (id) => `/billing/invoices/${id}`,
      providesTags: (result, error, id) => [{ type: 'Invoice', id }],
    }),
    initiatePayment: builder.mutation<any, string>({
      query: (invoiceId) => ({
        url: `/billing/invoices/${invoiceId}/pay`,
        method: 'POST',
      }),
      invalidatesTags: ['Invoice', 'Payment'],
    }),
    simulatePayment: builder.mutation<void, string>({
      query: (invoiceId) => ({
        url: `/billing/invoices/${invoiceId}/simulate-payment`,
        method: 'POST',
      }),
      invalidatesTags: ['Invoice', 'Payment', 'Revenue'],
    }),
    getPayments: builder.query<Payment[], string>({
      query: (invoiceId) => `/billing/invoices/${invoiceId}/payments`,
      providesTags: ['Payment'],
    }),
    getRevenue: builder.query<Revenue, { from: string; to: string }>({
      query: ({ from, to }) => `/billing/revenue?from=${from}&to=${to}`,
      providesTags: ['Revenue'],
    }),
    processRefund: builder.mutation<any, { invoiceId: string; amountCents: number; reason: string }>({
      query: ({ invoiceId, amountCents, reason }) => ({
        url: `/billing/invoices/${invoiceId}/refund`,
        method: 'POST',
        body: { invoiceId, amountCents, reason },
      }),
      invalidatesTags: ['Invoice', 'Revenue'],
    }),
  }),
});

export const {
  useGetInvoicesQuery,
  useGetInvoiceQuery,
  useInitiatePaymentMutation,
  useSimulatePaymentMutation,
  useGetPaymentsQuery,
  useGetRevenueQuery,
  useProcessRefundMutation,
} = billingApi;
