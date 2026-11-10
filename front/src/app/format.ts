export function bytes(value: number): string {
  if (value < 1024) return `${value} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let n = value / 1024;
  let i = 0;
  while (n >= 1024 && i < units.length - 1) {
    n /= 1024;
    i++;
  }
  return `${n.toFixed(1)} ${units[i]}`;
}

export function count(value: number): string {
  return value.toLocaleString('es-CO');
}

/** Fechas de Elasticsearch: epoch en ms o ISO 8601, en la zona horaria del navegador. */
export function date(value: unknown): string | null {
  const d = typeof value === 'number' ? new Date(value)
    : typeof value === 'string' && /^\d{4}-\d{2}-\d{2}/.test(value) ? new Date(value)
    : null;
  return d && !isNaN(d.getTime()) ? d.toLocaleString('es-CO') : null;
}

export async function copy(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    // Sin HTTPS el navegador bloquea la API del portapapeles: se usa el método viejo.
    const area = document.createElement('textarea');
    area.value = text;
    document.body.appendChild(area);
    area.select();
    document.execCommand('copy');
    area.remove();
  }
}
