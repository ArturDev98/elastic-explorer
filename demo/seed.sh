#!/usr/bin/env sh
# Crea los índices de demo (clientes, pedidos y productos ficticios) y carga sus documentos.
set -e
ES="${ES_URL:-http://localhost:9200}"
DIR="$(cd "$(dirname "$0")" && pwd)"

until curl -s "$ES" > /dev/null; do echo "Esperando a Elasticsearch en $ES…"; sleep 3; done

for index in customers orders products; do
  curl -s -X DELETE "$ES/$index" > /dev/null || true
  curl -s -X PUT "$ES/$index" -H 'Content-Type: application/json' --data-binary "@$DIR/mappings/$index.json" > /dev/null
  curl -s -X POST "$ES/$index/_bulk?refresh=true" -H 'Content-Type: application/x-ndjson' --data-binary "@$DIR/data/$index.ndjson" > /dev/null
  echo "$index: $(curl -s "$ES/$index/_count" | sed -E 's/.*"count":([0-9]+).*/\1/') documentos"
done
