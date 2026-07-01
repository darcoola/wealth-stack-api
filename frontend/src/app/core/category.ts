/** Whether a category groups money going out (spending) or money coming in (income). */
export type CategoryType = 'SPENDING' | 'INCOME';

/** A category dictionary entry, mirroring the backend `CategoryDto`. */
export interface Category {
  id: number;
  name: string;
  type: CategoryType;
}
