import { Invoice } from '../api';

interface Props {
  invoice: Invoice;
  onPay: (invoice: Invoice) => void;
  userRole: string;
}

const statusConfig = {
  ISSUED: { bg: 'bg-blue-50 text-blue-700 border-blue-200', label: 'Issued' },
  PAID: { bg: 'bg-emerald-50 text-emerald-700 border-emerald-200', label: 'Paid' },
  OVERDUE: { bg: 'bg-red-50 text-red-700 border-red-200', label: 'Overdue' },
  REFUNDED: { bg: 'bg-amber-50 text-amber-700 border-amber-200', label: 'Refunded' },
  CANCELLED: { bg: 'bg-slate-100 text-slate-500 border-slate-200', label: 'Cancelled' },
};

export function InvoiceCard({ invoice, onPay, userRole }: Props) {
  const status = statusConfig[invoice.status] || { bg: 'bg-slate-50 text-slate-600 border-slate-200', label: invoice.status };
  const isPayable = invoice.status === 'ISSUED' || invoice.status === 'OVERDUE';

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  };

  return (
    <div className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm transition-all hover:shadow-md">
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-2.5">
            <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${status.bg}`}>
              {status.label}
            </span>
            {invoice.isNoShowPenalty && (
              <span className="rounded-full bg-orange-50 px-2.5 py-0.5 text-xs font-semibold text-orange-600 border border-orange-100">
                No-Show Penalty
              </span>
            )}
          </div>
          <h3 className="font-bold text-slate-800 text-base">{invoice.description}</h3>
          <p className="mt-1.5 text-xs text-slate-400">
            Issued on {formatDate(invoice.issuedAt)}
          </p>
          {invoice.paidAt && (
            <p className="mt-1 text-xs font-medium text-emerald-600">
              Paid on {formatDate(invoice.paidAt)}
            </p>
          )}
          {invoice.refundedAt && (
            <p className="mt-1 text-xs font-medium text-amber-600">
              Refunded on {formatDate(invoice.refundedAt)}
            </p>
          )}
        </div>
        <div className="flex sm:flex-col items-center sm:items-end justify-between sm:justify-start gap-2">
          <p className="text-2xl font-black text-slate-800">{invoice.formattedAmount}</p>
          {isPayable && userRole === 'PATIENT' && (
            <button
              onClick={() => onPay(invoice)}
              className="rounded-lg bg-teal-600 px-4 py-2 text-xs font-bold text-white shadow-sm transition-all hover:bg-teal-700 active:scale-95"
            >
              Pay Now
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
