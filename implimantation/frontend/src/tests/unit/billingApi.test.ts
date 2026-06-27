import { describe, it, expect } from 'vitest';
import { billingApi } from '../../features/billing/api';

describe('billingApi', () => {
  it('should have the correct reducer path', () => {
    expect(billingApi.reducerPath).toBe('billingApi');
  });

  it('should define the expected endpoints', () => {
    expect(billingApi.endpoints.getInvoices).toBeDefined();
    expect(billingApi.endpoints.getInvoice).toBeDefined();
    expect(billingApi.endpoints.initiatePayment).toBeDefined();
    expect(billingApi.endpoints.simulatePayment).toBeDefined();
    expect(billingApi.endpoints.getPayments).toBeDefined();
    expect(billingApi.endpoints.getRevenue).toBeDefined();
    expect(billingApi.endpoints.processRefund).toBeDefined();
  });
});
