import { Component, computed, input, signal } from '@angular/core';
import { Doc } from '../api.service';
import { copy, date } from '../format';

interface Row { path: string; value: string; type: string; hint: string | null; }

/** Un documento: tabla de campos aplanados (con filtro) o el JSON tal cual. */
@Component({
  selector: 'app-document',
  standalone: true,
  template: `
    <article class="panel">
      <header>
        <span class="mono">_id: {{ doc().id }}</span>
        <div class="tools">
          @if (view() === 'table') {
            <input placeholder="Filtrar campos" [value]="filter()" (input)="filter.set($any($event.target).value)">
          }
          <button (click)="copyJson()">{{ copied() ? '¡Copiado!' : 'Copiar JSON' }}</button>
        </div>
      </header>
      @if (view() === 'table') {
        <table>
          @for (row of visibleRows(); track row.path) {
            <tr>
              <td class="mono key">{{ row.path }}</td>
              <td class="mono" [class]="'type-' + row.type">{{ row.value }}
                @if (row.hint) {
                  <span class="muted hint">{{ row.hint }}</span>
                }
              </td>
            </tr>
          }
        </table>
      } @else {
        <pre>{{ json() }}</pre>
      }
    </article>
  `,
  styles: `
    article { margin-bottom: 12px; padding: 12px 16px; }
    header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .tools { display: flex; gap: 8px; }
    .tools input { width: 200px; padding: 5px 9px; }
    .key { width: 32%; color: var(--muted); word-break: break-all; }
    td { word-break: break-word; }
    .hint { margin-left: 10px; font-family: system-ui; font-size: 12px; }
  `,
})
export class DocumentComponent {
  readonly doc = input.required<Doc>();
  readonly types = input.required<Map<string, string>>();
  readonly view = input<'table' | 'json'>('table');

  readonly filter = signal('');
  readonly copied = signal(false);

  readonly json = computed(() => JSON.stringify(this.doc().source, null, 2));

  readonly rows = computed<Row[]>(() => {
    const rows: Row[] = [];
    this.flatten(this.doc().source, '', rows);
    return rows;
  });

  readonly visibleRows = computed(() => {
    const f = this.filter().trim().toLowerCase();
    return f ? this.rows().filter(r => r.path.toLowerCase().includes(f) || r.value.toLowerCase().includes(f)) : this.rows();
  });

  async copyJson(): Promise<void> {
    await copy(this.json());
    this.copied.set(true);
    setTimeout(() => this.copied.set(false), 1500);
  }

  // Los objetos se aplanan por ruta; los arreglos de objetos quedan como JSON en una sola fila.
  private flatten(value: unknown, prefix: string, rows: Row[]): void {
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      for (const [key, v] of Object.entries(value)) {
        this.flatten(v, prefix ? `${prefix}.${key}` : key, rows);
      }
      return;
    }
    const type = this.types().get(prefix) ?? 'object';
    const text = Array.isArray(value)
      ? value.every(v => typeof v !== 'object') ? value.join(', ') : JSON.stringify(value)
      : value === null || value === undefined ? '—' : String(value);
    rows.push({ path: prefix, value: text, type, hint: type === 'date' ? date(value) : null });
  }
}
