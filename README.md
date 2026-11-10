# elastic-explorer

[![CI](https://github.com/ArturDev98/elastic-explorer/actions/workflows/ci.yml/badge.svg)](https://github.com/ArturDev98/elastic-explorer/actions/workflows/ci.yml) ![Licencia MIT](https://img.shields.io/badge/licencia-MIT-blue)

Interfaz web de **solo lectura** para consultar índices de Elasticsearch y ver sus mappings como
árbol, sin escribir URLs ni JSON a mano. Nació de un problema común: soporte y QA necesitan revisar
qué quedó indexado en cada ambiente, Kibana no está disponible para todos y abrir el 9200 a la red
no es opción.

## Qué hace

- **Consulta**: índice + campo + valor(es). Ofrece primero los campos de búsqueda (configurados o
  detectados: `id`, `clienteId`, `order_id`…), pagina de 20 a 100, muestra cada documento como
  tabla filtrable o JSON, formatea las fechas y da el **comando `curl` equivalente** para copiarlo.
- **Mappings**: todos los índices con salud, documentos y tamaño; el árbol de campos con tipo y
  subcampos (`.keyword`), filtros por nombre y por tipo, y una vista JSON resumida.
- **Varios clústeres** (local, staging, prod…) con **permisos por usuario**: un usuario puede ver
  solo los clústeres que tiene asignados.

## La búsqueda es exacta según el tipo del campo

| Tipo | Consulta |
|---|---|
| `keyword`, numéricos, `boolean`, `date` | `term` / `terms` (los numéricos validan que el valor sea un número) |
| `text` con subcampo `.keyword` | `term` sobre el `.keyword` |
| `text` sin `.keyword` | `match_phrase` (lo más parecido a exacto) |
| Campo dentro de un `nested` | La consulta anterior envuelta en `nested` con su `path` |

En `keyword` varios valores se separan con coma o espacio; en `text` solo con coma o salto de
línea, porque el espacio es parte del valor ("Ana María Rojas").

## Seguridad

- El navegador **nunca habla con Elasticsearch**: el back hace de proxy y solo usa `_cat/indices`,
  `_mapping` y `_search`. No hay ningún método que escriba o borre (`ElasticGateway` no lo permite).
- No acepta comodines, comas ni índices de sistema (`.kibana`, `_all`): una consulta no cruza índices.
- `explorer.hidden-fields` (`passwordHash`, `apiToken`…) se quitan de documentos y mappings a
  cualquier profundidad antes de responder.
- Login con Spring Security; en `/api` responde 401 y el front redirige al login. Las claves
  aceptan `{bcrypt}…`.
- Paginación limitada a los primeros 10.000 resultados (`max_result_window`), con mensaje claro.

## Probarlo

Demo completa con Docker (Elasticsearch de un nodo + datos ficticios de clientes, pedidos y productos):

```bash
docker compose up -d --build
# http://localhost:8090  →  admin / admin  (o soporte / soporte, que solo ve el clúster local)
```

Desarrollo local:

```bash
mvn spring-boot:run                      # back en :8090 (ELASTIC_LOCAL_URL apunta al Elasticsearch)
cd front && npm install && npm start     # front en :4200 con proxy al back
sh demo/seed.sh                          # carga los datos de demo en ES_URL (por defecto localhost:9200)
```

## Configuración

`src/main/resources/application.yml`:

| Clave | Uso |
|---|---|
| `explorer.clusters` | Un Elasticsearch por ambiente: nombre, URL y credenciales opcionales |
| `explorer.active-cluster` | Si tiene valor, el proceso solo expone ese clúster (un contenedor por ambiente) |
| `explorer.indices.<índice>` | `search-fields` sugeridos y `sort-field` propio |
| `explorer.default-sort-field` | Orden descendente por defecto, si el índice tiene ese campo |
| `explorer.hidden-fields` | Campos que nunca salen del back |
| `explorer.users` | Usuarios, claves y clústeres permitidos (vacío = todos) |

## API

| Endpoint | Uso |
|---|---|
| `GET /api/me` | Usuario y clústeres a los que tiene acceso |
| `GET /api/clusters/{c}/indices` | Índices con salud, documentos y tamaño |
| `GET /api/clusters/{c}/indices/{i}/mapping` | Árbol de campos y campos sugeridos |
| `GET /api/clusters/{c}/indices/{i}/search?field=&value=&page=&size=` | Búsqueda |

## Stack

Java 21 · Spring Boot 4 (`RestClient`, Spring Security, records) · Angular 18 (componentes
standalone, signals, control flow `@if`/`@for`) · JUnit 5 + MockMvc · Docker Compose.

```bash
mvn test                       # 20 tests: consultas por tipo, árbol, permisos, seguridad
cd front && npx ng build
```

## Licencia

MIT. Ver [LICENSE](LICENSE).
