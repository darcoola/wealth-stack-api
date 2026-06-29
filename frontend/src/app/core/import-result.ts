import { Operation } from './operation';

/** Outcome of importing a statement, mirroring the backend `ImportResult`. */
export interface ImportResult {
  message: string;
  bankName: string;
  fileName: string | null;
  /** Newly inserted rows. */
  operationsImported: number;
  /** Existing rows updated because they matched an already-imported operation. */
  operationsOverwritten: number;
  operations: Operation[];
}
