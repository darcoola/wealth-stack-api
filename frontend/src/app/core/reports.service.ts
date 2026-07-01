import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MonthlyCategoryTotal } from './report';

/** Read access to reporting aggregates. The base path is proxied to the backend in dev. */
@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/reports';

  /**
   * Per (month, category) totals across all history, summed as-is. The caller splits rows by
   * `categoryType` into separate spending/income charts.
   */
  getCategoryMonthlyTotals(): Observable<MonthlyCategoryTotal[]> {
    return this.http.get<MonthlyCategoryTotal[]>(`${this.baseUrl}/category-monthly-totals`);
  }
}
