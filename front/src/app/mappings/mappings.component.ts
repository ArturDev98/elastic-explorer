import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { ApiService, FieldNode, IndexMapping, IndexSummary, errorMessage } from '../api.service';
import { ClusterStore } from '../cluster.store';
import { bytes, count } from '../format';

@Component({
  selector: 'app-mappings',
  standalone: true,
  imports: [NgTemplateOutlet],
  templateUrl: './mappings.component.html',
  styleUrl: './mappings.component.css',
})
export class MappingsComponent {
  private readonly api = inject(ApiService);
  private readonly store = inject(ClusterStore);

  readonly bytes = bytes;
  readonly count = count;

  readonly indices = signal<IndexSummary[]>([]);
  readonly indexFilter = signal('');
  readonly selected = signal<IndexSummary | null>(null);
  readonly mapping = signal<IndexMapping | null>(null);
  readonly fieldFilter = signal('');
  readonly typeFilter = signal('');
  readonly expanded = signal(new Set<string>());
  readonly showJson = signal(false);
  readonly error = signal('');

  readonly visibleIndices = computed(() => {
    const f = this.indexFilter().trim().toLowerCase();
    return f ? this.indices().filter(i => i.name.includes(f)) : this.indices();
  });

  readonly types = computed(() => {
    const all = new Set<string>();
    const walk = (fields: FieldNode[]) => fields.forEach(f => (f.children.length ? walk(f.children) : all.add(f.type)));
    walk(this.mapping()?.fields ?? []);
    return [...all].sort();
  });

  /** Con filtro se muestran solo las ramas que tienen alguna hoja que coincide, ya abiertas. */
  readonly tree = computed(() => {
    const name = this.fieldFilter().trim().toLowerCase();
    const type = this.typeFilter();
    const fields = this.mapping()?.fields ?? [];
    if (!name && !type) return fields;
    const prune = (list: FieldNode[]): FieldNode[] =>
      list.flatMap(f => {
        if (f.children.length) {
          const children = prune(f.children);
          return children.length ? [{ ...f, children }] : [];
        }
        const matches = (!name || f.path.toLowerCase().includes(name)) && (!type || f.type === type);
        return matches ? [f] : [];
      });
    return prune(fields);
  });

  readonly filtering = computed(() => !!this.fieldFilter().trim() || !!this.typeFilter());

  readonly summaryJson = computed(() => {
    const toJson = (fields: FieldNode[]): Record<string, unknown> =>
      Object.fromEntries(fields.map(f => [f.name, f.children.length ? toJson(f.children)
        : f.subfields.length ? `${f.type} (+${f.subfields.join(', ')})` : f.type]));
    return JSON.stringify(toJson(this.mapping()?.fields ?? []), null, 2);
  });

  constructor() {
    effect(() => {
      const cluster = this.store.selected();
      if (!cluster) return;
      untracked(() => {
        this.selected.set(null);
        this.mapping.set(null);
        this.api.indices(cluster).subscribe({
          next: list => this.indices.set(list),
          error: err => this.error.set(errorMessage(err)),
        });
      });
    });
  }

  open(index: IndexSummary): void {
    this.selected.set(index);
    this.mapping.set(null);
    this.expanded.set(new Set());
    this.error.set('');
    this.api.mapping(this.store.selected()!, index.name).subscribe({
      next: m => this.mapping.set(m),
      error: err => this.error.set(errorMessage(err)),
    });
  }

  isOpen(field: FieldNode): boolean {
    return this.filtering() || this.expanded().has(field.path);
  }

  toggle(field: FieldNode): void {
    const next = new Set(this.expanded());
    next.has(field.path) ? next.delete(field.path) : next.add(field.path);
    this.expanded.set(next);
  }

  isSuggested(field: FieldNode): boolean {
    return this.mapping()?.suggestedFields.includes(field.path) ?? false;
  }

  healthClass(health: string): string {
    return health === 'green' || health === 'yellow' || health === 'red' ? health : '';
  }
}
