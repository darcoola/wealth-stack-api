/** How signed amounts are collapsed for the charts. Mirrors the backend `AmountMode`. */
export type AmountMode = 'all' | 'spendings' | 'income';

/** A (month, category) aggregate, mirroring the backend `MonthlyCategoryTotalDto`. */
export interface MonthlyCategoryTotal {
  /** Bucket month, `YYYY-MM`. */
  month: string;
  /** Category dictionary id, or `null` when Uncategorized. */
  categoryId: number | null;
  /** Category name, or `null` when Uncategorized. */
  category: string | null;
  /** Total for the bucket, already adjusted for the requested mode. */
  total: number;
}
