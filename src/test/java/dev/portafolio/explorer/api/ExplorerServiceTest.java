package dev.portafolio.explorer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.portafolio.explorer.config.ExplorerProperties;
import dev.portafolio.explorer.config.ExplorerProperties.Cluster;
import dev.portafolio.explorer.config.ExplorerProperties.IndexSettings;
import dev.portafolio.explorer.config.ExplorerProperties.UserAccount;
import dev.portafolio.explorer.elastic.ElasticGateway;
import dev.portafolio.explorer.web.BadRequestException;
import dev.portafolio.explorer.web.ForbiddenException;
import tools.jackson.databind.json.JsonMapper;

class ExplorerServiceTest {

    private StubElastic elastic;
    private ExplorerService service;

    @BeforeEach
    void setUp() {
        var props = new ExplorerProperties(
                Map.of("local", new Cluster("Local", "http://localhost:9200", null, null),
                       "prod", new Cluster("Prod", "http://prod:9200", null, null)),
                null, Set.of("passwordHash"),
                Map.of("orders", new IndexSettings(List.of("orderId"), null)),
                "updatedAt",
                List.of(new UserAccount("admin", "x", null), new UserAccount("soporte", "x", List.of("local"))));
        elastic = new StubElastic();
        service = new ExplorerService(elastic, props, JsonMapper.builder().build());
    }

    @Test
    void usersOnlySeeTheirClusters() {
        assertThat(service.clusters("admin")).extracting(c -> c.key()).containsExactlyInAnyOrder("local", "prod");
        assertThat(service.clusters("soporte")).extracting(c -> c.key()).containsExactly("local");
        assertThatThrownBy(() -> service.indices("soporte", "prod")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void suggestsConfiguredFieldsOrDetectsIdentifiers() {
        assertThat(service.mapping("admin", "local", "orders").suggestedFields()).containsExactly("orderId");
        assertThat(service.mapping("admin", "local", "customers").suggestedFields())
                .containsExactlyInAnyOrder("customerId", "address.cityId");
    }

    @Test
    void searchBuildsTheQueryRedactsAndSortsByTheDefaultField() {
        var result = service.search("admin", "local", "customers", "fullName", "Ana Rojas", 1, 20);

        assertThat(elastic.lastBody).containsEntry("from", 20).containsEntry("size", 20)
                .containsEntry("query", Map.of("term", Map.of("fullName.keyword", "Ana Rojas")))
                .containsKey("sort");
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.documents().getFirst().source()).doesNotContainKey("passwordHash").containsKey("fullName");
        assertThat(result.curl()).startsWith("curl -s -X POST 'http://localhost:9200/customers/_search?pretty'");
    }

    @Test
    void rejectsPagesBeyondTheResultWindow() {
        assertThatThrownBy(() -> service.search("admin", "local", "customers", "customerId", "C-1", 500, 100))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnknownFields() {
        assertThatThrownBy(() -> service.search("admin", "local", "customers", "passwordHash", "x", 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no existe");
    }

    private static final class StubElastic implements ElasticGateway {
        Map<String, Object> lastBody;

        @Override
        public List<Map<String, Object>> indices(String cluster) {
            return List.of(Map.of("index", "customers", "health", "green", "docs.count", "3", "store.size", "1024"));
        }

        @Override
        public Map<String, Object> mappingProperties(String cluster, String index) {
            if (index.equals("orders")) {
                return Map.of("orderId", Map.of("type", "long"), "status", Map.of("type", "keyword"));
            }
            return Map.of(
                    "customerId", Map.of("type", "keyword"),
                    "fullName", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))),
                    "passwordHash", Map.of("type", "keyword"),
                    "updatedAt", Map.of("type", "date"),
                    "address", Map.of("properties", Map.of("cityId", Map.of("type", "keyword"))));
        }

        @Override
        public Map<String, Object> search(String cluster, String index, Map<String, Object> body) {
            lastBody = body;
            return Map.of("hits", Map.of(
                    "total", Map.of("value", 1, "relation", "eq"),
                    "hits", List.of(Map.of("_id", "1", "_source",
                            Map.of("fullName", "Ana Rojas", "passwordHash", "secreto")))));
        }
    }
}
