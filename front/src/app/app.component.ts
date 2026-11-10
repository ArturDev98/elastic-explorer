import { Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ClusterStore } from './cluster.store';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header>
      <a class="brand" routerLink="/search"><span class="logo">⌕</span> Elastic Explorer</a>
      <nav>
        <a routerLink="/search" routerLinkActive="active">Consulta</a>
        <a routerLink="/mappings" routerLinkActive="active">Mappings</a>
      </nav>
      <div class="right">
        @if (store.clusters().length > 1) {
          <select [value]="store.selected()" (change)="store.select($any($event.target).value)" aria-label="Clúster">
            @for (c of store.clusters(); track c.key) {
              <option [value]="c.key">{{ c.name }}</option>
            }
          </select>
        } @else if (store.current()) {
          <span class="cluster">{{ store.current()!.name }}</span>
        }
        <span class="muted">{{ store.username() }}</span>
        <a href="/logout">Salir</a>
      </div>
    </header>
    <main>
      @if (store.selected()) {
        <router-outlet />
      } @else {
        <p class="muted">Cargando…</p>
      }
    </main>
  `,
  styles: `
    header {
      display: flex; align-items: center; gap: 24px;
      padding: 10px 24px; border-bottom: 1px solid var(--border); background: var(--panel);
      position: sticky; top: 0; z-index: 10;
    }
    .brand { color: var(--text); text-decoration: none; font-weight: 700; font-size: 16px; }
    .logo {
      display: inline-grid; place-items: center; width: 26px; height: 26px; border-radius: 7px;
      background: var(--accent); color: white; margin-right: 6px;
    }
    nav { display: flex; gap: 4px; }
    nav a { color: var(--muted); text-decoration: none; padding: 6px 12px; border-radius: 8px; }
    nav a.active { color: var(--text); background: var(--accent-soft); }
    .right { margin-left: auto; display: flex; align-items: center; gap: 14px; }
    .cluster {
      padding: 3px 10px; border-radius: 999px; font-weight: 600; font-size: 12px;
      background: rgba(34, 197, 94, .15); color: var(--green);
    }
    main { padding: 24px; max-width: 1500px; margin: 0 auto; }
  `,
})
export class AppComponent implements OnInit {
  readonly store = inject(ClusterStore);

  ngOnInit(): void {
    this.store.load();
  }
}
