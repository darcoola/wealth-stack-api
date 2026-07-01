-- Reporting aggregates group operations by month (from date) and category. Index the two columns
-- those queries filter/join on so aggregation stays cheap as history grows. Portable Postgres + H2.
CREATE INDEX idx_banking_operations_date ON banking_operations (date);
CREATE INDEX idx_banking_operations_category_id ON banking_operations (category_id);
