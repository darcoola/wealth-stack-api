/** A single bank operation, mirroring the backend `OperationDto`. */
export interface Operation {
  /** Database id, used to (re)assign a category. */
  id: number;
  /** ISO date, `yyyy-MM-dd`. */
  date: string;
  description: string;
  /** Raw account/card identifier. */
  account: string;
  /** Friendly name resolved from account mappings (falls back to `account`). */
  displayName: string;
  /** Signed amount; negative = debit, non-negative = credit. */
  amount: number;
  /** Assigned category dictionary id, or `null` when Uncategorized. */
  categoryId: number | null;
  /** Assigned category name, or `null` when Uncategorized. */
  category: string | null;
}
