/** Maps a raw account/card identifier to a friendly display name, mirroring `AccountMappingDto`. */
export interface AccountMapping {
  /** Database id, used to update or delete the mapping. */
  id: number;
  /** Raw account/card identifier as it appears in bank statements. */
  rawAccount: string;
  /** Friendly name shown for operations on this account. */
  displayName: string;
}
