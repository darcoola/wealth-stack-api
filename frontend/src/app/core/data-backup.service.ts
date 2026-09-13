import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

/** Outcome of loading a backup, mirroring the backend `DataImportResult`. */
export interface DataImportResult {
  /** Whether all existing data was wiped before the backup was loaded. */
  replaced: boolean;
  categoryGroupsCreated: number;
  categoriesCreated: number;
  accountMappingsCreated: number;
  /** Newly inserted operations. */
  operationsImported: number;
  /** Existing operations updated because the backup carried the same operation. */
  operationsOverwritten: number;
}

/** Whole-dataset export / import (backend `/api/v1/data`): one JSON file with everything. */
@Injectable({ providedIn: 'root' })
export class DataBackupService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/data';

  /** Fetches the export as a file; its name comes from the server's `Content-Disposition` header. */
  export(): Observable<{ blob: Blob; fileName: string }> {
    return this.http.get(`${this.baseUrl}/export`, { observe: 'response', responseType: 'blob' }).pipe(
      map((response: HttpResponse<Blob>) => ({
        blob: response.body ?? new Blob(),
        fileName: DataBackupService.fileName(response.headers.get('Content-Disposition')),
      }))
    );
  }

  /** Sends a backup file's JSON as-is. `replace` wipes all existing data before loading it. */
  import(content: string, replace: boolean): Observable<DataImportResult> {
    return this.http.post<DataImportResult>(`${this.baseUrl}/import`, content, {
      params: { replace },
      headers: { 'Content-Type': 'application/json' },
    });
  }

  private static fileName(disposition: string | null): string {
    const match = disposition?.match(/filename="?([^";]+)"?/);
    return match?.[1] ?? `wealthstack-backup-${new Date().toISOString().slice(0, 10)}.json`;
  }
}
