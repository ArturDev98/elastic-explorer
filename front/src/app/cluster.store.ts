import { Injectable, computed, inject, signal } from '@angular/core';
import { ApiService, Cluster } from './api.service';

const STORAGE_KEY = 'elastic-explorer.cluster';

/** Usuario y clúster elegido, compartidos por todas las pantallas. */
@Injectable({ providedIn: 'root' })
export class ClusterStore {
  private readonly api = inject(ApiService);

  readonly username = signal('');
  readonly clusters = signal<Cluster[]>([]);
  readonly selected = signal<string | null>(null);
  readonly current = computed(() => this.clusters().find(c => c.key === this.selected()) ?? null);

  load(): void {
    this.api.me().subscribe(me => {
      this.username.set(me.username);
      this.clusters.set(me.clusters);
      const saved = localStorage.getItem(STORAGE_KEY);
      const initial = me.clusters.find(c => c.key === saved) ?? me.clusters[0];
      this.selected.set(initial?.key ?? null);
    });
  }

  select(key: string): void {
    this.selected.set(key);
    localStorage.setItem(STORAGE_KEY, key);
  }
}
