import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Operation } from './operation';

/**
 * Paginated response as serialized by Spring Data's `VIA_DTO` mode: the page metadata is nested
 * under `page` rather than being flattened onto the top-level object.
 */
export interface Page<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

/** A hand-entered cash operation, mirroring the backend `NewOperationRequest`. */
export interface NewOperation {
  /** ISO date, `yyyy-MM-dd`. */
  date: string;
  description: string;
  /** Signed: negative = spending, positive = income. */
  amount: number;
  categoryId: number | null;
  additionalInfo: string | null;
  /**
   * Confirms a save the backend flagged as a duplicate (same date, amount and description as an
   * existing operation). Without it such a save is refused with 409 — see {@link DuplicateOperationError}.
   */
  force?: boolean;
}

/** Body of the 409 the backend answers an unconfirmed duplicate entry with. */
export interface DuplicateOperationError {
  error: string;
  duplicates: Operation[];
}

/** Read access to bank operations. The base path is proxied to the backend in dev. */
@Injectable({ providedIn: 'root' })
export class OperationsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/bank-statements';

  getAll(params?: { [param: string]: string | number | boolean | readonly (string | number | boolean)[] }): Observable<Page<Operation>> {
    return this.http.get<Page<Operation>>(this.baseUrl, { params });
  }

  /**
   * Records a cash operation typed into the Add-operation form. Creates a new row rather than folding
   * onto an identical one — but an entry matching an existing operation is refused with 409 until the
   * caller re-sends it with `force`, so a double-submit doesn't quietly become a second spend.
   */
  create(operation: NewOperation): Observable<Operation> {
    return this.http.post<Operation>(`${this.baseUrl}/operations/manual`, operation);
  }

  /** Assigns a category to an operation, or clears it (Uncategorized) when `categoryId` is null. */
  assignCategory(operationId: number, categoryId: number | null): Observable<Operation> {
    return this.http.put<Operation>(`${this.baseUrl}/operations/${operationId}/category`, {
      categoryId,
    });
  }

  /** Sets (or clears, when blank/null) the free-text note on a single operation. */
  updateAdditionalInfo(operationId: number, additionalInfo: string | null): Observable<Operation> {
    return this.http.put<Operation>(`${this.baseUrl}/operations/${operationId}/additional-info`, {
      additionalInfo,
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

  /** Permanently deletes every operation, returning how many were removed. */
  deleteAll(): Observable<{ deletedCount: number }> {
    return this.http.delete<{ deletedCount: number }>(`${this.baseUrl}/operations/all`);
  }

  /** Accepts the auto-assigned category for a single operation. */
  acceptPrediction(operationId: number): Observable<Operation> {
    return this.http.put<Operation>(`${this.baseUrl}/operations/${operationId}/verify`, {});
  }

  /** Accepts the auto-assigned categories for multiple operations. */
  acceptPredictionsBulk(operationIds: number[]): Observable<Operation[]> {
    return this.http.put<Operation[]>(`${this.baseUrl}/operations/verify`, { operationIds });
  }
}
