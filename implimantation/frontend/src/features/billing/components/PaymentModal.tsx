import React, { useState } from 'react';
import { useSimulatePaymentMutation } from '../api';
import type { Invoice } from '../api';

interface Props {
  invoice: Invoice;
  onClose: () => void;
  onSuccess: () => void;
}

export function PaymentModal({ invoice, onClose, onSuccess }: Props) {
  const [simulatePayment, { isLoading }] = useSimulatePaymentMutation();
  const [cardNumber, setCardNumber] = useState('');
  const [expiry, setExpiry] = useState('');
  const [cvc, setCvc] = useState('');
  const [name, setName] = useState('');
  const [isSuccess, setIsSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Format card number with spaces
  const handleCardNumberChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value.replace(/\s+/g, '').replace(/[^0-9]/gi, '');
    const matches = value.match(/\d{4,16}/g);
    const match = (matches && matches[0]) || '';
    const parts = [];

    for (let i = 0, len = match.length; i < len; i += 4) {
      parts.push(match.substring(i, i + 4));
    }

    if (parts.length > 0) {
      setCardNumber(parts.join(' '));
    } else {
      setCardNumber(value);
    }
  };

  // Format expiry MM/YY
  const handleExpiryChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    let value = e.target.value.replace(/[^0-9]/g, '');
    if (value.length > 2) {
      value = value.substring(0, 2) + '/' + value.substring(2, 4);
    }
    setExpiry(value);
  };

  const handlePay = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!cardNumber || !expiry || !cvc || !name) {
      setError('Please fill in all card details.');
      return;
    }
    setError(null);

    try {
      await simulatePayment(invoice.invoiceId).unwrap();
      setIsSuccess(true);
      setTimeout(() => {
        onSuccess();
        onClose();
      }, 1500);
    } catch (err: any) {
      setError(err?.data?.detail || 'Payment simulation failed. Please try again.');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 animate-fade-in">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl border border-slate-100 transform transition-all duration-300 scale-100">
        <div className="mb-6 flex items-center justify-between">
          <h2 className="text-xl font-black text-slate-800">Secure Checkout</h2>
          <button 
            onClick={onClose} 
            disabled={isLoading || isSuccess}
            className="rounded-full p-1.5 text-slate-400 hover:bg-slate-50 hover:text-slate-600 transition-colors disabled:opacity-50"
          >
            <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Invoice Summary */}
        <div className="mb-6 rounded-xl bg-slate-50 border border-slate-100 p-4">
          <div className="flex justify-between items-center">
            <div>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Amount Due</p>
              <p className="mt-0.5 text-3xl font-black text-slate-800">{invoice.formattedAmount}</p>
            </div>
            <span className="text-xs font-semibold text-slate-500 bg-white border border-slate-200 rounded-lg px-2.5 py-1 max-w-[200px] truncate">
              {invoice.description}
            </span>
          </div>
        </div>

        {isSuccess ? (
          <div className="flex flex-col items-center justify-center py-8 text-center animate-scale-up">
            <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
              <svg className="h-10 w-10" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h3 className="text-lg font-bold text-slate-800">Payment Successful!</h3>
            <p className="mt-1.5 text-sm text-slate-400">Your invoice has been marked as paid.</p>
          </div>
        ) : (
          <form onSubmit={handlePay} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-slate-500 mb-1.5 uppercase tracking-wider">Cardholder Name</label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="John Doe"
                disabled={isLoading}
                className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm font-medium focus:border-teal-500 focus:ring-1 focus:ring-teal-500 focus:outline-none transition-all disabled:opacity-50"
                required
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-500 mb-1.5 uppercase tracking-wider">Card Number</label>
              <div className="relative">
                <input
                  type="text"
                  value={cardNumber}
                  onChange={handleCardNumberChange}
                  placeholder="4242 4242 4242 4242"
                  maxLength={19}
                  disabled={isLoading}
                  className="w-full rounded-xl border border-slate-200 pl-3.5 pr-12 py-2.5 text-sm font-medium focus:border-teal-500 focus:ring-1 focus:ring-teal-500 focus:outline-none transition-all disabled:opacity-50"
                  required
                />
                <div className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-300">
                  <svg className="h-6 w-6" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M20 4H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z" />
                  </svg>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-bold text-slate-500 mb-1.5 uppercase tracking-wider">Expiration Date</label>
                <input
                  type="text"
                  value={expiry}
                  onChange={handleExpiryChange}
                  placeholder="MM/YY"
                  maxLength={5}
                  disabled={isLoading}
                  className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm font-medium text-center focus:border-teal-500 focus:ring-1 focus:ring-teal-500 focus:outline-none transition-all disabled:opacity-50"
                  required
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-slate-500 mb-1.5 uppercase tracking-wider">CVC</label>
                <input
                  type="password"
                  value={cvc}
                  onChange={(e) => setCvc(e.target.value.replace(/[^0-9]/g, '').substring(0, 4))}
                  placeholder="•••"
                  maxLength={4}
                  disabled={isLoading}
                  className="w-full rounded-xl border border-slate-200 px-3.5 py-2.5 text-sm font-medium text-center tracking-widest focus:border-teal-500 focus:ring-1 focus:ring-teal-500 focus:outline-none transition-all disabled:opacity-50"
                  required
                />
              </div>
            </div>

            {error && (
              <p className="text-xs font-medium text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2.5">{error}</p>
            )}

            <button
              type="submit"
              disabled={isLoading}
              className="w-full mt-2 flex items-center justify-center rounded-xl bg-teal-600 px-4 py-3 text-sm font-bold text-white shadow-md hover:bg-teal-700 active:scale-98 transition-all disabled:opacity-50"
            >
              {isLoading ? (
                <div className="flex items-center gap-2">
                  <svg className="animate-spin h-5 w-5 text-white" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                  </svg>
                  <span>Processing...</span>
                </div>
              ) : (
                `Pay ${invoice.formattedAmount}`
              )}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
