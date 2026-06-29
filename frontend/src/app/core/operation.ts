/** A single bank operation, mirroring the backend `OperationDto`. */
export interface Operation {
  /** ISO date, `yyyy-MM-dd`. */
  date: string;
  description: string;
  /** Raw account/card identifier. */
  account: string;
  /** Friendly name resolved from account mappings (falls back to `account`). */
  displayName: string;
  /** Signed amount; negative = debit, non-negative = credit. */
  amount: number;
  /** The bank's own transaction category. */
  category: string;
}
