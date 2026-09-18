-- Purpose: serve the queue of FPS payments escalated for manual investigation.
--
-- Operations work through these payments oldest first, and monitoring counts
-- them on every metrics scrape. They are a tiny fraction of all transactions,
-- so a partial index keeps both queries independent of the table size.

CREATE INDEX idx_transactions_needs_investigation ON transactions (created_at)
    WHERE status = 'NEEDS_INVESTIGATION';
