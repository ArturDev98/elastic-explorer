package dev.portafolio.explorer.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * clusters: un Elasticsearch por ambiente. Si active-cluster tiene valor, el proceso solo expone ese
 * (un contenedor por ambiente); vacío, expone todos y el front muestra un selector.
 */
@ConfigurationProperties(prefix = "explorer")
public record ExplorerProperties(Map<String, Cluster> clusters, String activeCluster, Set<String> hiddenFields,
                                 Map<String, IndexSettings> indices, String defaultSortField, List<UserAccount> users) {

    public ExplorerProperties {
        clusters = clusters != null ? clusters : Map.of();
        hiddenFields = hiddenFields != null ? Set.copyOf(hiddenFields) : Set.of();
        indices = indices != null ? indices : Map.of();
        users = users != null ? users : List.of();
        if (StringUtils.hasText(activeCluster) && !clusters.containsKey(activeCluster)) {
            throw new IllegalStateException("explorer.active-cluster=" + activeCluster + " no está en explorer.clusters");
        }
    }

    public Map<String, Cluster> availableClusters() {
        if (!StringUtils.hasText(activeCluster)) {
            return clusters;
        }
        var only = new LinkedHashMap<String, Cluster>();
        only.put(activeCluster, clusters.get(activeCluster));
        return only;
    }

    public IndexSettings index(String name) {
        return indices.getOrDefault(name, IndexSettings.EMPTY);
    }

    public record Cluster(String name, String url, String username, String password) {
        public boolean hasCredentials() {
            return StringUtils.hasText(username);
        }
    }

    /** search-fields: los que se ofrecen primero al buscar; sort-field: orden descendente de resultados. */
    public record IndexSettings(List<String> searchFields, String sortField) {
        static final IndexSettings EMPTY = new IndexSettings(List.of(), null);

        public IndexSettings {
            searchFields = searchFields != null ? searchFields : List.of();
        }
    }

    /** clusters vacío = acceso a todos. */
    public record UserAccount(String username, String password, List<String> clusters) {
        public UserAccount {
            clusters = clusters != null ? clusters : List.of();
        }

        public boolean canAccess(String cluster) {
            return clusters.isEmpty() || clusters.contains(cluster);
        }
    }
}
