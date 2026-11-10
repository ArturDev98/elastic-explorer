package dev.portafolio.explorer.elastic;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.portafolio.explorer.config.ExplorerProperties;
import dev.portafolio.explorer.config.ExplorerProperties.Cluster;
import dev.portafolio.explorer.web.BadRequestException;
import dev.portafolio.explorer.web.NotFoundException;

@Component
public class HttpElasticGateway implements ElasticGateway {

    // Sin comodines, comas ni índices de sistema (".kibana", "_all"): una búsqueda nunca cruza índices.
    private static final Pattern INDEX_NAME = Pattern.compile("[a-z0-9][a-z0-9_.-]*");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    private final Map<String, RestClient> clients = new LinkedHashMap<>();

    public HttpElasticGateway(ExplorerProperties props) {
        props.availableClusters().forEach((key, cluster) -> clients.put(key, create(cluster)));
    }

    @Override
    public List<Map<String, Object>> indices(String cluster) {
        var rows = client(cluster).get()
                .uri("/_cat/indices?format=json&bytes=b&s=index&h=index,health,status,docs.count,store.size")
                .retrieve()
                .body(LIST);
        return rows == null ? List.of() : rows.stream()
                .filter(r -> INDEX_NAME.matcher(String.valueOf(r.get("index"))).matches())
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> mappingProperties(String cluster, String index) {
        var response = client(cluster).get()
                .uri("/{index}/_mapping", validate(index))
                .retrieve()
                .body(MAP);
        if (response == null || response.isEmpty()) {
            return Map.of();
        }
        var byIndex = (Map<String, Object>) response.values().iterator().next();
        var mappings = (Map<String, Object>) byIndex.get("mappings");
        var properties = mappings == null ? null : (Map<String, Object>) mappings.get("properties");
        return properties == null ? Map.of() : properties;
    }

    @Override
    public Map<String, Object> search(String cluster, String index, Map<String, Object> body) {
        return client(cluster).post()
                .uri("/{index}/_search", validate(index))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(MAP);
    }

    static String validate(String index) {
        if (index == null || index.startsWith(".") || !INDEX_NAME.matcher(index).matches()) {
            throw new BadRequestException("Nombre de índice no válido: " + index);
        }
        return index;
    }

    private RestClient client(String cluster) {
        var client = clients.get(cluster);
        if (client == null) {
            throw new NotFoundException("Clúster desconocido: " + cluster);
        }
        return client;
    }

    private static RestClient create(Cluster cluster) {
        var http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(30));
        var builder = RestClient.builder().requestFactory(factory).baseUrl(cluster.url());
        if (cluster.hasCredentials()) {
            builder.defaultHeaders(h -> h.setBasicAuth(cluster.username(), cluster.password()));
        }
        return builder.build();
    }
}
