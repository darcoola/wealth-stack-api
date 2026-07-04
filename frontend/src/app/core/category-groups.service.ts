import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CategoryGroup } from './category-group';

/** CRUD access to the editable category-group dictionary. Base path is proxied to the backend in dev. */
@Injectable({ providedIn: 'root' })
export class CategoryGroupsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/category-groups';

  getAll(): Observable<CategoryGroup[]> {
    return this.http.get<CategoryGroup[]>(this.baseUrl);
  }

  create(name: string): Observable<CategoryGroup> {
    return this.http.post<CategoryGroup>(this.baseUrl, { name });
  }

  update(id: number, name: string): Observable<CategoryGroup> {
    return this.http.put<CategoryGroup>(`${this.baseUrl}/${id}`, { name });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
