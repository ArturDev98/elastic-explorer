import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Cluster { key: string; name: string; }
export interface Me { username: string; clusters: Cluster[]; }
export interface IndexSummary { name: string; health: string; status: string; documents: number; bytes: number; }
export interface FieldNode { name: string; path: string; type: string; subfields: string[]; children: FieldNode[]; }
export interface IndexMapping { index: string; totalFields: number; suggestedFields: string[]; fields: FieldNode[]; }
export interface Doc { id: string; source: Record<string, unknown>; }
export interface SearchResult {
  total: number;
  exactTotal: boolean;
  page: number;
  size: number;
  sortedBy: string | null;
  documents: Doc[];
  query: unknown;
  curl: string;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  me(): Observable<Me> {
    return this.http.get<Me>('/api/me');
  }

  indices(cluster: string): Observable<IndexSummary[]> {
    return this.http.get<IndexSummary[]>(`/api/clusters/${cluster}/indices`);
  }

  mapping(cluster: string, index: string): Observable<IndexMapping> {
    return this.http.get<IndexMapping>(`/api/clusters/${cluster}/indices/${index}/mapping`);
  }

  search(cluster: string, index: string, field: string, value: string, page: number, size: number): Observable<SearchResult> {
    const params = new HttpParams().set('field', field).set('value', value).set('page', page).set('size', size);
    return this.http.get<SearchResult>(`/api/clusters/${cluster}/indices/${index}/search`, { params });
  }
}

/** El back manda {"mensaje": …}; si no, un texto genérico con el código. */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { mensaje?: string } | null;
    return body?.mensaje ?? `Error ${err.status || 'de red'}`;
  }
  return 'Error inesperado';
}

/** Las hojas del árbol (los campos que se pueden buscar), en orden. */
export function leaves(fields: FieldNode[]): FieldNode[] {
  return fields.flatMap(f => (f.children.length ? leaves(f.children) : [f]));
}
