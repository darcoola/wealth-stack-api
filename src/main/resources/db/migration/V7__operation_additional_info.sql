-- Operations gain an optional free-text note the user can supply on a manual import when the
-- description alone (often just a shop name like "Allegro") isn't enough to deduce a category.
ALTER TABLE banking_operations ADD COLUMN additional_info VARCHAR(1000);
