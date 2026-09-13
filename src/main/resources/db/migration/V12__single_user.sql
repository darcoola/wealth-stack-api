-- Back to a single-user app: the party model from V10/V11 is removed. Only the bootstrap party
-- (id 1, the original owner's data) is kept; rows owned by any other party are deleted. The
-- per-party composite uniques go back to the global ones they replaced.

-- Drop foreign parties' data. References from kept rows into deleted ones are cleared first so
-- the foreign keys never block a delete.
DELETE FROM banking_operations WHERE party_id <> 1;
UPDATE banking_operations SET category_id = NULL
    WHERE category_id IN (SELECT id FROM categories WHERE party_id <> 1);
DELETE FROM categories WHERE party_id <> 1;
UPDATE categories SET group_id = NULL
    WHERE group_id IN (SELECT id FROM category_groups WHERE party_id <> 1);
DELETE FROM category_groups WHERE party_id <> 1;
DELETE FROM account_mappings WHERE party_id <> 1;

-- Constraints and the index are dropped explicitly before the column (H2 refuses to drop a column
-- a constraint still references).
ALTER TABLE banking_operations DROP CONSTRAINT uk_banking_operation_identity;
ALTER TABLE banking_operations DROP CONSTRAINT fk_banking_operations_party;
DROP INDEX idx_banking_operations_party;
ALTER TABLE banking_operations DROP COLUMN party_id;
ALTER TABLE banking_operations
    ADD CONSTRAINT uk_banking_operation_identity UNIQUE (fingerprint, occurrence);

ALTER TABLE categories DROP CONSTRAINT uk_categories_name;
ALTER TABLE categories DROP CONSTRAINT fk_categories_party;
ALTER TABLE categories DROP COLUMN party_id;
ALTER TABLE categories ADD CONSTRAINT uk_categories_name UNIQUE (name);

ALTER TABLE category_groups DROP CONSTRAINT uk_category_groups_name;
ALTER TABLE category_groups DROP CONSTRAINT fk_category_groups_party;
ALTER TABLE category_groups DROP COLUMN party_id;
ALTER TABLE category_groups ADD CONSTRAINT uk_category_groups_name UNIQUE (name);

ALTER TABLE account_mappings DROP CONSTRAINT uk_account_mappings_raw_account;
ALTER TABLE account_mappings DROP CONSTRAINT fk_account_mappings_party;
ALTER TABLE account_mappings DROP COLUMN party_id;
ALTER TABLE account_mappings ADD CONSTRAINT uk_account_mappings_raw_account UNIQUE (raw_account);

DROP TABLE party_membership;
DROP TABLE app_user;
DROP TABLE party;
