-- Add Stripe customer ID tracking for recurring payments
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(255);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT '{}';

-- Add cancellation window tracking for refund logic
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS cancellation_window_hours INTEGER;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS is_no_show_penalty BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_invoices_stripe_customer ON invoices(stripe_customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_no_show ON invoices(is_no_show_penalty) WHERE is_no_show_penalty = TRUE;
