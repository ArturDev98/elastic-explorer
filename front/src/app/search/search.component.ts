import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ApiService, IndexMapping, IndexSummary, SearchResult, errorMessage, leaves } from '../api.service';
import { ClusterStore } from '../cluster.store';
import { copy, count } from '../format';
import { DocumentComponent } from './document.component';

const PAGE_SIZES = [20, 50, 100];

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [FormsModule, DocumentComponent],
  templateUrl: './search.component.html',
  styleUrl: './search.component.css',
})
export class SearchComponent {
  private readonly api = inject(ApiService);
  private readonly store = inject(ClusterStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly pageSizes = PAGE_SIZES;
  readonly count = count;

  readonly indices = signal<IndexSummary[]>([]);
  readonly index = signal('');
  readonly mapping = signal<IndexMapping | null>(null);
  readonly field = signal('');
  readonly value = signal('');
  readonly size = signal(20);
  readonly result = signal<SearchResult | null>(null);
  readonly error = signal('');
  readonly loading = signal(false);
  readonly view = signal<'table' | 'json'>('table');
  readonly copied = signal(false);

  /** Campos sugeridos primero; después el resto, en orden alfabético por ruta. */
  readonly fields = computed(() => {
    const m = this.mapping();
    if (!m) return [];
    const all = leaves(m.fields);
    const suggested = all.filter(f => m.suggestedFields.includes(f.path));
    const rest = all.filter(f => !m.suggestedFields.includes(f.path)).sort((a, b) => a.path.localeCompare(b.path));
    return [...suggested, ...rest];
  });

  readonly types = computed(() => new Map(this.fields().map(f => [f.path, f.type])));
  readonly selectedField = computed(() => this.fields().find(f => f.path === this.field()) ?? null);
  readonly totalPages = computed(() => {
    const r = this.result();
    return r ? Math.max(1, Math.ceil(Math.min(r.total, 10_000) / r.size)) : 0;
  });

  private pendingSearch: { field: string; value: string } | null = null;

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    if (params.get('index')) {
      this.pendingSearch = { field: params.get('field') ?? '', value: params.get('value') ?? '' };
    }

    // Al cambiar de clúster se recargan los índices y se limpia la búsqueda.
    effect(() => {
      const cluster = this.store.selected();
      if (!cluster) return;
      untracked(() => this.loadIndices(cluster, params.get('index')));
    });
  }

  selectIndex(name: string): void {
    this.index.set(name);
    this.mapping.set(null);
    this.result.set(null);
    this.error.set('');
    if (!name) return;
    this.api.mapping(this.store.selected()!, name).subscribe({
      next: mapping => {
        this.mapping.set(mapping);
        const pending = this.pendingSearch;
        this.pendingSearch = null;
        if (pending?.field && pending.value) {
          this.field.set(pending.field);
          this.value.set(pending.value);
          this.search(0);
        } else {
          this.field.set(this.fields()[0]?.path ?? '');
        }
      },
      error: err => this.error.set(errorMessage(err)),
    });
  }

  search(page = 0): void {
    if (!this.index() || !this.field() || !this.value().trim()) return;
    this.loading.set(true);
    this.error.set('');
    this.api.search(this.store.selected()!, this.index(), this.field(), this.value(), page, this.size()).subscribe({
      next: result => {
        this.result.set(result);
        this.loading.set(false);
        // La búsqueda queda en la URL para poder compartirla.
        this.router.navigate([], {
          queryParams: { index: this.index(), field: this.field(), value: this.value() },
          replaceUrl: true,
        });
      },
      error: err => {
        this.error.set(errorMessage(err));
        this.result.set(null);
        this.loading.set(false);
      },
    });
  }

  async copyCurl(): Promise<void> {
    const r = this.result();
    if (!r) return;
    await copy(r.curl);
    this.copied.set(true);
    setTimeout(() => this.copied.set(false), 1500);
  }

  private loadIndices(cluster: string, initialIndex: string | null): void {
    this.indices.set([]);
    this.selectIndex('');
    this.api.indices(cluster).subscribe({
      next: indices => {
        this.indices.set(indices);
        if (initialIndex && indices.some(i => i.name === initialIndex)) {
          this.selectIndex(initialIndex);
        }
      },
      error: err => this.error.set(errorMessage(err)),
    });
  }
}
