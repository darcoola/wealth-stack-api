import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Operation } from './operation';

/** Read access to bank operations. The base path is proxied to the backend in dev. */
@Injectable({ providedIn: 'root' })
export class OperationsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/bank-statements';

  getAll(): Observable<Operation[]> {
    return this.http.get<Operation[]>(this.baseUrl);
  }

  /** Assigns a category to an operation, or clears it (Uncategorized) when `categoryId` is null. */
  assignCategory(operationId: number, categoryId: number | null): Observable<Operation> {
    return this.http.put<Operation>(`${this.baseUrl}/operations/${operationId}/category`, {
      categoryId,
    });
  }

  /** Assigns a category to many operations at once, or clears it when `categoryId` is null. */
  assignCategoryBulk(operationIds: number[], categoryId: number | null): Observable<Operation[]> {
    return this.http.put<Operation[]>(`${this.baseUrl}/operations/category`, {
      operationIds,
      categoryId,
    });
  }

  /** Permanently deletes the given operations. */
  deleteBulk(operationIds: number[]): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/operations`, { body: { operationIds } });
  }
}
