import { useState } from 'react';
import { useGetRevenueQuery, useGetInvoicesQuery, useProcessRefundMutation } from '../api';

export function RevenueDashboard() {
  const [dateRange, setDateRange] = useState('30');
  const [refundReason, setRefundReason] = useState('');
  const [refundAmount, setRefundAmount] = useState('');
  const [selectedRefundInvoiceId, setSelectedRefundInvoiceId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  // Calculate ISO date strings
  const getDates = (days: number) => {
    const to = new Date();
    const from = new Date();
    from.setDate(to.getDate() - days);
    return {
      from: from.toISOString().substring(0, 19) + 'Z',
      to: to.toISOString().substring(0, 19) + 'Z',
    };
  };

  const { from, to } = getDates(parseInt(dateRange));
  const { data: revenue, isLoading: isRevLoading } = useGetRevenueQuery({ from, to });
  const { data: invoices, isLoading: isInvLoading } = useGetInvoicesQuery();
  const [processRefund, { isLoading: isRefundProcessing }] = useProcessRefundMutation();

  const handleRefund = async (invoiceId: string, maxAmountCents: number) => {
    const amountVal = parseFloat(refundAmount);
    if (isNaN(amountVal) || amountVal <= 0) {
      alert('Please enter a valid refund amount.');
      return;
    }

    const cents = Math.round(amountVal * 100);
    if (cents > maxAmountCents) {
      alert(`Refund amount cannot exceed the original invoice amount of $${(maxAmountCents / 100).toFixed(2)}.`);
      return;
    }

    try {
      await processRefund({
        invoiceId,
        amountCents: cents,
        reason: refundReason || 'Requested by customer',
      }).unwrap();
      alert('Refund processed successfully!');
      setSelectedRefundInvoiceId(null);
      setRefundReason('');
      setRefundAmount('');
    } catch (err: any) {
      alert(err?.data?.detail || 'Refund failed. Please try again.');
    }
  };

  // CSS Chart calculations
  const total = (revenue?.totalRevenueCents || 0) + (revenue?.pendingRevenueCents || 0) + (revenue?.refundedRevenueCents || 0);
  const paidPercent = total > 0 ? ((revenue?.totalRevenueCents || 0) / total) * 100 : 0;
  const pendingPercent = total > 0 ? ((revenue?.pendingRevenueCents || 0) / total) * 100 : 0;
  const refundedPercent = total > 0 ? ((revenue?.refundedRevenueCents || 0) / total) * 100 : 0;

  const filteredInvoices = invoices?.filter(inv => 
    inv.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
    inv.status.toLowerCase().includes(searchQuery.toLowerCase()) ||
    inv.invoiceId.substring(0, 8).includes(searchQuery.toLowerCase())
  ) || [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-2xl font-black text-slate-800">Financial Revenue</h2>
          <p className="text-sm text-slate-400">Track earnings, pending fees, and process refunds.</p>
        </div>
        <select
          value={dateRange}
          onChange={(e) => setDateRange(e.target.value)}
          className="rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-sm font-semibold text-slate-700 focus:border-teal-500 focus:outline-none shadow-sm"
        >
          <option value="7">Last 7 days</option>
          <option value="30">Last 30 days</option>
          <option value="90">Last 90 days</option>
        </select>
      </div>

      {isRevLoading ? (
        <div className="flex h-40 items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white">
          <p className="text-sm font-semibold text-slate-400 animate-pulse">Loading financial summary...</p>
        </div>
      ) : revenue ? (
        <div className="grid gap-4 sm:grid-cols-3">
          <div className="rounded-2xl border border-emerald-100 bg-emerald-50/60 p-5 shadow-sm">
            <p className="text-xs font-bold text-emerald-600 uppercase tracking-wider">Total Revenue (Paid)</p>
            <p className="mt-1.5 text-3xl font-black text-emerald-800">{revenue.formattedTotalRevenue}</p>
            <p className="mt-1 text-xs font-medium text-emerald-600">{revenue.paidInvoiceCount} invoices settled</p>
          </div>

          <div className="rounded-2xl border border-blue-100 bg-blue-50/60 p-5 shadow-sm">
            <p className="text-xs font-bold text-blue-600 uppercase tracking-wider">Pending Awaiting Payment</p>
            <p className="mt-1.5 text-3xl font-black text-blue-800">{revenue.formattedPendingRevenue}</p>
            <p className="mt-1 text-xs font-medium text-blue-600">Currently outstanding</p>
          </div>

          <div className="rounded-2xl border border-amber-100 bg-amber-50/60 p-5 shadow-sm">
            <p className="text-xs font-bold text-amber-600 uppercase tracking-wider">Total Refunded</p>
            <p className="mt-1.5 text-3xl font-black text-amber-800">{revenue.formattedRefundedRevenue}</p>
            <p className="mt-1 text-xs font-medium text-amber-600">Returned to patients</p>
          </div>
        </div>
      ) : null}

      {/* Distribution Chart (Pure CSS Stacked Bar) */}
      {!isRevLoading && revenue && total > 0 && (
        <div className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm">
          <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider mb-4">Revenue Distribution</h3>
          
          <div className="flex h-8 w-full overflow-hidden rounded-full bg-slate-100">
            {paidPercent > 0 && (
              <div 
                style={{ width: `${paidPercent}%` }} 
                className="bg-emerald-500 hover:opacity-90 transition-opacity cursor-help"
                title={`Paid: ${paidPercent.toFixed(1)}%`}
              />
            )}
            {pendingPercent > 0 && (
              <div 
                style={{ width: `${pendingPercent}%` }} 
                className="bg-blue-500 hover:opacity-90 transition-opacity cursor-help"
                title={`Pending: ${pendingPercent.toFixed(1)}%`}
              />
            )}
            {refundedPercent > 0 && (
              <div 
                style={{ width: `${refundedPercent}%` }} 
                className="bg-amber-500 hover:opacity-90 transition-opacity cursor-help"
                title={`Refunded: ${refundedPercent.toFixed(1)}%`}
              />
            )}
          </div>

          <div className="mt-4 flex flex-wrap gap-x-6 gap-y-2 text-xs font-bold text-slate-500">
            <div className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full bg-emerald-500" />
              <span>Paid ({paidPercent.toFixed(1)}%)</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full bg-blue-500" />
              <span>Pending ({pendingPercent.toFixed(1)}%)</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="h-3 w-3 rounded-full bg-amber-500" />
              <span>Refunded ({refundedPercent.toFixed(1)}%)</span>
            </div>
          </div>
        </div>
      )}

      {/* Clinic Invoices Table */}
      <div className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
          <h3 className="text-lg font-black text-slate-800">Clinic Invoices</h3>
          <div className="relative w-full sm:max-w-xs">
            <input
              type="text"
              placeholder="Search by description or status..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full rounded-xl border border-slate-200 pl-10 pr-4 py-2 text-xs font-semibold text-slate-600 focus:border-teal-500 focus:outline-none"
            />
            <svg className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>
        </div>

        {isInvLoading ? (
          <p className="text-center py-8 text-xs font-bold text-slate-400">Loading invoice ledger...</p>
        ) : filteredInvoices.length === 0 ? (
          <p className="text-center py-8 text-xs font-bold text-slate-400">No invoices match your search.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-100 text-xs font-bold text-slate-400 uppercase tracking-wider">
                  <th className="pb-3 font-bold">ID</th>
                  <th className="pb-3 font-bold">Description</th>
                  <th className="pb-3 font-bold">Issued At</th>
                  <th className="pb-3 font-bold">Status</th>
                  <th className="pb-3 font-bold text-right">Amount</th>
                  <th className="pb-3 font-bold text-center">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50 text-xs">
                {filteredInvoices.map((inv) => (
                  <tr key={inv.invoiceId} className="hover:bg-slate-50/50">
                    <td className="py-4 font-mono font-bold text-slate-400">{inv.invoiceId.substring(0, 8)}</td>
                    <td className="py-4 font-bold text-slate-800">{inv.description}</td>
                    <td className="py-4 text-slate-400">{new Date(inv.issuedAt).toLocaleDateString()}</td>
                    <td className="py-4">
                      <span className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider ${
                        inv.status === 'PAID' ? 'bg-emerald-50 text-emerald-700' :
                        inv.status === 'ISSUED' ? 'bg-blue-50 text-blue-700' :
                        inv.status === 'OVERDUE' ? 'bg-red-50 text-red-700' :
                        inv.status === 'REFUNDED' ? 'bg-amber-50 text-amber-700' :
                        'bg-slate-100 text-slate-500'
                      }`}>
                        {inv.status}
                      </span>
                    </td>
                    <td className="py-4 text-right font-bold text-slate-800">{inv.formattedAmount}</td>
                    <td className="py-4 text-center">
                      {inv.status === 'PAID' ? (
                        <div>
                          {selectedRefundInvoiceId === inv.invoiceId ? (
                            <div className="mt-2 flex flex-col gap-2 rounded-lg bg-slate-50 p-3 text-left border border-slate-100 w-64 absolute right-6 z-10 shadow-lg">
                              <p className="font-bold text-slate-800 text-xs">Process Refund</p>
                              <div>
                                <label className="block text-[10px] text-slate-400 font-bold uppercase mb-1">Refund Amount ($)</label>
                                <input
                                  type="number"
                                  step="0.01"
                                  placeholder={`Max ${(inv.amountCents / 100).toFixed(2)}`}
                                  value={refundAmount}
                                  onChange={(e) => setRefundAmount(e.target.value)}
                                  className="w-full rounded-md border border-slate-200 px-2 py-1 text-xs focus:outline-none focus:border-teal-500"
                                />
                              </div>
                              <div>
                                <label className="block text-[10px] text-slate-400 font-bold uppercase mb-1">Reason</label>
                                <input
                                  type="text"
                                  placeholder="Reason for refund..."
                                  value={refundReason}
                                  onChange={(e) => setRefundReason(e.target.value)}
                                  className="w-full rounded-md border border-slate-200 px-2 py-1 text-xs focus:outline-none focus:border-teal-500"
                                />
                              </div>
                              <div className="flex gap-2 justify-end mt-1">
                                <button
                                  onClick={() => setSelectedRefundInvoiceId(null)}
                                  className="rounded px-2 py-1 text-[10px] font-bold text-slate-500 hover:bg-slate-100"
                                >
                                  Cancel
                                </button>
                                <button
                                  onClick={() => handleRefund(inv.invoiceId, inv.amountCents)}
                                  disabled={isRefundProcessing}
                                  className="rounded bg-rose-600 px-2 py-1 text-[10px] font-bold text-white hover:bg-rose-700"
                                >
                                  {isRefundProcessing ? 'Processing...' : 'Confirm'}
                                </button>
                              </div>
                            </div>
                          ) : (
                            <button
                              onClick={() => {
                                setSelectedRefundInvoiceId(inv.invoiceId);
                                setRefundAmount((inv.amountCents / 100).toFixed(2));
                              }}
                              className="rounded bg-rose-50 px-2.5 py-1 text-[10px] font-bold text-rose-600 border border-rose-100 hover:bg-rose-100 transition-colors"
                            >
                              Refund
                            </button>
                          )}
                        </div>
                      ) : (
                        <span className="text-slate-300">-</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
