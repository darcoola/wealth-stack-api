import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Member, PartySummary } from './me';

/** Household (shared party) management: create a household and manage its members. */
@Injectable({ providedIn: 'root' })
export class PartiesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/parties';

  create(name: string): Observable<PartySummary> {
    return this.http.post<PartySummary>(this.baseUrl, { name });
  }

  members(partyId: number): Observable<Member[]> {
    return this.http.get<Member[]>(`${this.baseUrl}/${partyId}/members`);
  }

  addMember(partyId: number, email: string, role: 'OWNER' | 'MEMBER'): Observable<Member> {
    return this.http.post<Member>(`${this.baseUrl}/${partyId}/members`, { email, role });
  }

  changeRole(partyId: number, userId: number, role: 'OWNER' | 'MEMBER'): Observable<Member> {
    return this.http.patch<Member>(`${this.baseUrl}/${partyId}/members/${userId}`, { role });
  }

  removeMember(partyId: number, userId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${partyId}/members/${userId}`);
  }
}
