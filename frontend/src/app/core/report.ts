/** A (month, category) aggregate, mirroring the backend `MonthlyCategoryTotalDto`. */
export interface MonthlyCategoryTotal {
  /** Bucket month, `YYYY-MM`. */
  month: string;
  /** Category dictionary id, or `null` when Uncategorized. */
  categoryId: number | null;
  /** Category name, or `null` when Uncategorized. */
  category: string | null;
  /** The category's group id, or `null` when Ungrouped / Uncategorized. */
  groupId: number | null;
  /** The category's group name, or `null` when Ungrouped / Uncategorized. */
  groupName: string | null;
  /** Sum of amounts in the bucket, as-is (spending negative, income positive). */
  total: number;
}
