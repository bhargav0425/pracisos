import { useState } from 'react';
import { useGetInvoicesQuery } from '../api';
import { InvoiceCard } from './InvoiceCard';
import { PaymentModal } from './PaymentModal';
import type { Invoice } from '../api';

interface Props {
  userRole: string;
}

export function InvoiceList({ userRole }: Props) {
  const { data: invoices, isLoading, refetch } = useGetInvoicesQuery();
  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-black text-slate-800">Invoices & Billing</h2>
        <p className="text-sm text-slate-400">
          {userRole === 'PATIENT' 
            ? 'View and settle your outstanding clinic fees.' 
            : 'Manage and track patient billing records.'}
        </p>
      </div>

      {isLoading ? (
        <div className="flex h-40 items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white">
          <p className="text-sm font-semibold text-slate-400 animate-pulse">Loading billing ledger...</p>
        </div>
      ) : !invoices || invoices.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-white p-8 text-center">
          <p className="text-sm font-bold text-slate-400">No invoices found.</p>
        </div>
      ) : (
        <div className="grid gap-4">
          {invoices.map((invoice) => (
            <InvoiceCard
              key={invoice.invoiceId}
              invoice={invoice}
              userRole={userRole}
              onPay={(inv) => setSelectedInvoice(inv)}
            />
          ))}
        </div>
      )}

      {selectedInvoice && (
        <PaymentModal
          invoice={selectedInvoice}
          onClose={() => setSelectedInvoice(null)}
          onSuccess={() => {
            refetch();
            setSelectedInvoice(null);
          }}
        />
      )}
    </div>
  );
}
