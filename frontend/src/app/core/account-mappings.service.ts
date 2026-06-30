import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AccountMapping } from './account-mapping';

/** CRUD access to account → display-name mappings. Base path is proxied to the backend in dev. */
@Injectable({ providedIn: 'root' })
export class AccountMappingsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/account-mappings';

  getAll(): Observable<AccountMapping[]> {
    return this.http.get<AccountMapping[]>(this.baseUrl);
  }

  create(rawAccount: string, displayName: string): Observable<AccountMapping> {
    return this.http.post<AccountMapping>(this.baseUrl, { rawAccount, displayName });
  }

  update(id: number, rawAccount: string, displayName: string): Observable<AccountMapping> {
    return this.http.put<AccountMapping>(`${this.baseUrl}/${id}`, { rawAccount, displayName });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
