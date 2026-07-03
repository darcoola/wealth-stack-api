import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ImportResult } from './import-result';

/** A bank statement format the backend can parse (mirrors the registered `StatementParser`s). */
export interface Bank {
  /** Value sent as the `bankName` request param. */
  id: string;
  label: string;
}

/** Write side: upload bank statement files for import. */
@Injectable({ providedIn: 'root' })
export class ImportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/bank-statements';

  /** Banks with a parser. Keep in sync with `BankStatementConfig`'s registered parsers. */
  readonly banks: Bank[] = [
    { id: 'mbank', label: 'mBank' },
    { id: 'pkobp', label: 'PKO BP' },
    { id: 'revolut', label: 'Revolut' },
    { id: 'manual', label: 'Manual (WealthStack CSV)' },
  ];

  /** Omit `bankName` to let the backend auto-detect the parser from the file content. */
  importStatement(bankName: string | null, file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    if (bankName) {
      form.append('bankName', bankName);
    }
    return this.http.post<ImportResult>(this.baseUrl, form);
  }
}
