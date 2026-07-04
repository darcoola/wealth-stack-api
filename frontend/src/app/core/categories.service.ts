import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Category } from './category';

/** CRUD access to the editable category dictionary. Base path is proxied to the backend in dev. */
@Injectable({ providedIn: 'root' })
export class CategoriesService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/categories';

  getAll(): Observable<Category[]> {
    return this.http.get<Category[]>(this.baseUrl);
  }

  create(name: string, groupId: number | null): Observable<Category> {
    return this.http.post<Category>(this.baseUrl, { name, groupId });
  }

  update(id: number, name: string, groupId: number | null): Observable<Category> {
    return this.http.put<Category>(`${this.baseUrl}/${id}`, { name, groupId });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
