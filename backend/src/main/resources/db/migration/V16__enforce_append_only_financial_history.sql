-- Financial history is corrected with compensating entries, never rewritten.
-- Hibernate already marks ledger columns non-updatable; these triggers preserve
-- the invariant for SQL clients, maintenance scripts, and future integrations too.

CREATE FUNCTION reject_financial_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; append a compensating record instead', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_financial_history_mutation();

CREATE TRIGGER payment_events_append_only
    BEFORE UPDATE OR DELETE ON payment_events
    FOR EACH ROW EXECUTE FUNCTION reject_financial_history_mutation();
