import { CategoryType } from './category';

/** A (month, category) aggregate, mirroring the backend `MonthlyCategoryTotalDto`. */
export interface MonthlyCategoryTotal {
  /** Bucket month, `YYYY-MM`. */
  month: string;
  /** Category dictionary id, or `null` when Uncategorized. */
  categoryId: number | null;
  /** Category name, or `null` when Uncategorized. */
  category: string | null;
  /** Category type, or `null` when Uncategorized. */
  categoryType: CategoryType | null;
  /** Sum of amounts in the bucket, as-is (spending negative, income positive). */
  total: number;
}
