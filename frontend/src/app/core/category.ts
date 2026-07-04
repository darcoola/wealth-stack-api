/** A category dictionary entry, mirroring the backend `CategoryDto`. */
export interface Category {
  id: number;
  name: string;
  /** The group this category belongs to, or `null` when Ungrouped. */
  groupId: number | null;
  /** The group's name, or `null` when Ungrouped. */
  groupName: string | null;
}
