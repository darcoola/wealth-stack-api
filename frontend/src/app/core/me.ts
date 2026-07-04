export interface PartySummary {
  id: number;
  name: string;
  type: 'PERSON' | 'ORGANIZATION';
  role: 'OWNER' | 'MEMBER';
}

export interface MeResponse {
  email: string;
  displayName: string;
  approved: boolean;
  personalPartyId: number | null;
  parties: PartySummary[];
}

export interface Member {
  userId: number;
  email: string;
  displayName: string;
  role: 'OWNER' | 'MEMBER';
}
