/** How a category groups money: going out, coming in, or neither (transfers, investments, …). */
export type CategoryType = 'SPENDING' | 'INCOME' | 'OTHERS';

/** A category dictionary entry, mirroring the backend `CategoryDto`. */
export interface Category {
  id: number;
  name: string;
  type: CategoryType;
}
