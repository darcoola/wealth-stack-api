-- Categories gain a spending/income type that drives reporting (charts split by type, totals are
-- summed as-is instead of by amount sign). Existing categories are backfilled as SPENDING; the user
-- re-tags income ones (Salary, etc.) in the Categories page.
ALTER TABLE categories ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'SPENDING';
