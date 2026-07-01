import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AmountMode, MonthlyCategoryTotal } from './report';

/** Read access to reporting aggregates. The base path is proxied to the backend in dev. */
@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/reports';

  /** Per (month, category) totals across all history, each total collapsed per `mode`. */
  getCategoryMonthlyTotals(mode: AmountMode): Observable<MonthlyCategoryTotal[]> {
    const params = new HttpParams().set('mode', mode);
    return this.http.get<MonthlyCategoryTotal[]>(`${this.baseUrl}/category-monthly-totals`, {
      params,
    });
  }
}
