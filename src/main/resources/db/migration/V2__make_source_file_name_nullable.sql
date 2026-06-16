-- source_file_name is provenance only; manual/JSON-ingested rows may have no source.
ALTER TABLE banking_operations ALTER COLUMN source_file_name DROP NOT NULL;
