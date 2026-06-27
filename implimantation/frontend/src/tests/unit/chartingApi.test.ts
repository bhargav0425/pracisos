import { describe, it, expect } from 'vitest';
import { chartingApi } from '../../features/charting/api';

describe('chartingApi', () => {
  it('should have the correct reducer path', () => {
    expect(chartingApi.reducerPath).toBe('chartingApi');
  });

  it('should define the expected endpoints', () => {
    expect(chartingApi.endpoints.getNotesByPatient).toBeDefined();
    expect(chartingApi.endpoints.getNote).toBeDefined();
    expect(chartingApi.endpoints.saveDraft).toBeDefined();
    expect(chartingApi.endpoints.lockNote).toBeDefined();
    expect(chartingApi.endpoints.addAmendment).toBeDefined();
  });
});
