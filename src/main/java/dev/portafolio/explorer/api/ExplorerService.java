package dev.portafolio.explorer.api;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import dev.portafolio.explorer.api.ExplorerDtos.ClusterSummary;
import dev.portafolio.explorer.api.ExplorerDtos.Document;
import dev.portafolio.explorer.api.ExplorerDtos.IndexMapping;
import dev.portafolio.explorer.api.ExplorerDtos.IndexSummary;
import dev.portafolio.explorer.api.ExplorerDtos.SearchResult;
import dev.portafolio.explorer.config.ExplorerProperties;
import dev.portafolio.explorer.elastic.ElasticGateway;
import dev.portafolio.explorer.elastic.FieldNode;
import dev.portafolio.explorer.elastic.FieldRedactor;
import dev.portafolio.explorer.elastic.QueryBuilder;
import dev.portafolio.explorer.web.BadRequestException;
import dev.portafolio.explorer.web.ForbiddenException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ExplorerService {

    // max_result_window por defecto de Elasticsearch: más allá, from + size falla.
    static final int MAX_WINDOW = 10_000;
    static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SUGGESTED = 5;
    private static final Duration MAPPING_TTL = Duration.ofMinutes(10);
    private static final Pattern ID_FIELD = Pattern.compile("(?i)^(id|.*Id|.*_id)$");
    private static final Set<String> ID_TYPES = Set.of("keyword", "long", "integer");

    private final ElasticGateway elastic;
    private final ExplorerProperties props;
    private final JsonMapper json;
    private final Map<String, CachedMapping> mappings = new ConcurrentHashMap<>();

    public ExplorerService(ElasticGateway elastic, ExplorerProperties props, JsonMapper json) {
        this.elastic = elastic;
        this.props = props;
        this.json = json;
    }

    public List<ClusterSummary> clusters(String user) {
        return props.availableClusters().entrySet().stream()
                .filter(e -> account(user).canAccess(e.getKey()))
                .map(e -> new ClusterSummary(e.getKey(), e.getValue().name()))
                .toList();
    }

    public List<IndexSummary> indices(String user, String cluster) {
        checkAccess(user, cluster);
        return elastic.indices(cluster).stream()
                .map(row -> {
                    var name = String.valueOf(row.get("index"));
                    return new IndexSummary(name, text(row.get("health")), text(row.get("status")),
                            number(row.get("docs.count")), number(row.get("store.size")));
                })
                .toList();
    }

    public IndexMapping mapping(String user, String cluster, String index) {
        checkAccess(user, cluster);
        var tree = tree(cluster, index, true);
        var leaves = FieldNode.leavesByPath(tree);
        return new IndexMapping(index, leaves.size(), suggestedFields(index, leaves), tree);
    }

    @SuppressWarnings("unchecked")
    public SearchResult search(String user, String cluster, String index, String field, String value, int page, int size) {
        checkAccess(user, cluster);
        if (size < 1 || size > MAX_PAGE_SIZE || page < 0) {
            throw new BadRequestException("Página o tamaño fuera de rango (tamaño máximo " + MAX_PAGE_SIZE + ")");
        }
        if ((page + 1L) * size > MAX_WINDOW) {
            throw new BadRequestException("Solo se pueden recorrer los primeros 10.000 resultados: afina la búsqueda");
        }
        var leaves = FieldNode.leavesByPath(tree(cluster, index, false));
        var definition = leaves.get(field);
        if (definition == null) {
            throw new BadRequestException("El campo " + field + " no existe en " + index);
        }
        var values = QueryBuilder.values(value, definition);
        if (values.isEmpty()) {
            throw new BadRequestException("Escribe al menos un valor para buscar");
        }
        var query = QueryBuilder.query(definition, values);
        var body = new LinkedHashMap<String, Object>();
        body.put("query", query);
        body.put("from", page * size);
        body.put("size", size);
        body.put("track_total_hits", true);
        var sortField = sortField(index, leaves);
        if (sortField != null) {
            body.put("sort", List.of(Map.of(sortField, Map.of("order", "desc"))));
        }

        var response = elastic.search(cluster, index, body);
        var hits = (Map<String, Object>) response.getOrDefault("hits", Map.of());
        var total = (Map<String, Object>) hits.getOrDefault("total", Map.of());
        var documents = ((List<Map<String, Object>>) hits.getOrDefault("hits", List.of())).stream()
                .map(h -> new Document(text(h.get("_id")),
                        (Map<String, Object>) FieldRedactor.redact(h.getOrDefault("_source", Map.of()), props.hiddenFields())))
                .toList();
        return new SearchResult(number(total.get("value")), "eq".equals(total.get("relation")), page, size,
                sortField, documents, query, curl(cluster, index, body));
    }

    // Los configurados primero; si no hay, los que parecen identificadores (id, clienteId, order_id).
    private List<String> suggestedFields(String index, Map<String, FieldNode> leaves) {
        var configured = props.index(index).searchFields().stream().filter(leaves::containsKey).toList();
        if (!configured.isEmpty()) {
            return configured;
        }
        return leaves.values().stream()
                .filter(f -> ID_TYPES.contains(f.type()) && ID_FIELD.matcher(f.name()).matches())
                .map(FieldNode::path)
                .limit(MAX_SUGGESTED)
                .toList();
    }

    private String sortField(String index, Map<String, FieldNode> leaves) {
        var candidate = props.index(index).sortField() != null ? props.index(index).sortField() : props.defaultSortField();
        var field = candidate == null ? null : leaves.get(candidate);
        // Ordenar por un campo text sin keyword falla en Elasticsearch: mejor sin orden.
        return field != null && !"text".equals(field.type()) ? candidate : null;
    }

    private String curl(String cluster, String index, Map<String, Object> body) {
        var url = props.availableClusters().get(cluster).url();
        return "curl -s -X POST '" + url + "/" + index + "/_search?pretty' -H 'Content-Type: application/json' -d '"
                + json.writeValueAsString(body).replace("'", "'\\''") + "'";
    }

    private List<FieldNode> tree(String cluster, String index, boolean refresh) {
        var key = cluster + "/" + index;
        var cached = mappings.get(key);
        if (refresh || cached == null || cached.expired()) {
            cached = new CachedMapping(FieldNode.tree(elastic.mappingProperties(cluster, index), props.hiddenFields()),
                    Instant.now().plus(MAPPING_TTL));
            mappings.put(key, cached);
        }
        return cached.tree();
    }

    private void checkAccess(String user, String cluster) {
        if (!props.availableClusters().containsKey(cluster) || !account(user).canAccess(cluster)) {
            throw new ForbiddenException("No tienes acceso al clúster " + cluster);
        }
    }

    private ExplorerProperties.UserAccount account(String user) {
        return props.users().stream()
                .filter(u -> u.username().equals(user))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("Usuario sin configuración: " + user));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static long number(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return value == null ? 0 : Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record CachedMapping(List<FieldNode> tree, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
