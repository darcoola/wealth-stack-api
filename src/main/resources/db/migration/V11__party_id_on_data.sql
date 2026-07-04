-- Every domain table becomes party-owned (see V10). Pre-existing rows are assigned to the
-- well-known bootstrap party (id 1), which the configured owner adopts on first login.
-- Global unique constraints become per-party composites: two parties may each have a category
-- named "Groceries" or import the same statement without colliding.

ALTER TABLE banking_operations ADD COLUMN party_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE banking_operations ALTER COLUMN party_id DROP DEFAULT;
ALTER TABLE banking_operations
    ADD CONSTRAINT fk_banking_operations_party FOREIGN KEY (party_id) REFERENCES party (id);
ALTER TABLE banking_operations DROP CONSTRAINT uk_banking_operation_identity;
ALTER TABLE banking_operations
    ADD CONSTRAINT uk_banking_operation_identity UNIQUE (party_id, fingerprint, occurrence);
CREATE INDEX idx_banking_operations_party ON banking_operations (party_id);

ALTER TABLE categories ADD COLUMN party_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE categories ALTER COLUMN party_id DROP DEFAULT;
ALTER TABLE categories
    ADD CONSTRAINT fk_categories_party FOREIGN KEY (party_id) REFERENCES party (id);
ALTER TABLE categories DROP CONSTRAINT uk_categories_name;
ALTER TABLE categories ADD CONSTRAINT uk_categories_name UNIQUE (party_id, name);

ALTER TABLE category_groups ADD COLUMN party_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE category_groups ALTER COLUMN party_id DROP DEFAULT;
ALTER TABLE category_groups
    ADD CONSTRAINT fk_category_groups_party FOREIGN KEY (party_id) REFERENCES party (id);
ALTER TABLE category_groups DROP CONSTRAINT uk_category_groups_name;
ALTER TABLE category_groups ADD CONSTRAINT uk_category_groups_name UNIQUE (party_id, name);

ALTER TABLE account_mappings ADD COLUMN party_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE account_mappings ALTER COLUMN party_id DROP DEFAULT;
ALTER TABLE account_mappings
    ADD CONSTRAINT fk_account_mappings_party FOREIGN KEY (party_id) REFERENCES party (id);
ALTER TABLE account_mappings DROP CONSTRAINT uk_account_mappings_raw_account;
ALTER TABLE account_mappings
    ADD CONSTRAINT uk_account_mappings_raw_account UNIQUE (party_id, raw_account);
