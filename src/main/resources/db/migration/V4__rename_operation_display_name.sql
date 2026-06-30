-- The operation's display_name is really the mapped account's display name (copied from
-- account_mappings). Rename it to make that explicit; account_mappings.display_name is unchanged.
ALTER TABLE banking_operations RENAME COLUMN display_name TO account_display_name;
