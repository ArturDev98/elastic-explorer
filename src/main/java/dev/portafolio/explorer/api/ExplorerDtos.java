package dev.portafolio.explorer.api;

import java.util.List;
import java.util.Map;

import dev.portafolio.explorer.elastic.FieldNode;

public final class ExplorerDtos {

    private ExplorerDtos() {
    }

    public record ClusterSummary(String key, String name) {
    }

    public record IndexSummary(String name, String health, String status, long documents, long bytes) {
    }

    public record IndexMapping(String index, int totalFields, List<String> suggestedFields, List<FieldNode> fields) {
    }

    public record Document(String id, Map<String, Object> source) {
    }

    public record SearchResult(long total, boolean exactTotal, int page, int size, String sortedBy,
                               List<Document> documents, Map<String, Object> query, String curl) {
    }
}
