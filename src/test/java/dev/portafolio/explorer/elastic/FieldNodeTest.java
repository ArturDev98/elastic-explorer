package dev.portafolio.explorer.elastic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FieldNodeTest {

    private static final Map<String, Object> PROPERTIES = Map.of(
            "customerId", Map.of("type", "keyword"),
            "fullName", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))),
            "passwordHash", Map.of("type", "keyword"),
            "address", Map.of("properties", Map.of(
                    "city", Map.of("type", "keyword"),
                    "geo", Map.of("type", "geo_point"))),
            "items", Map.of("type", "nested", "properties", Map.of("sku", Map.of("type", "keyword"))));

    @Test
    void buildsTheTreeSortedAndWithoutHiddenFields() {
        var tree = FieldNode.tree(PROPERTIES, Set.of("passwordHash"));

        assertThat(tree).extracting(FieldNode::name).containsExactly("address", "customerId", "fullName", "items");
        var address = tree.getFirst();
        assertThat(address.type()).isEqualTo("object");
        assertThat(address.children()).extracting(FieldNode::path).containsExactly("address.city", "address.geo");
        assertThat(tree.get(2).hasKeyword()).isTrue();
        assertThat(tree.get(3).children().getFirst().nestedPath()).isEqualTo("items");
        assertThat(address.children().getFirst().nestedPath()).isNull();
    }

    @Test
    void leavesAreIndexedByFullPath() {
        var leaves = FieldNode.leavesByPath(FieldNode.tree(PROPERTIES, Set.of()));

        assertThat(leaves).containsOnlyKeys("address.city", "address.geo", "customerId", "fullName", "items.sku", "passwordHash");
    }

    @Test
    void redactorRemovesHiddenFieldsAtAnyDepth() {
        var document = Map.<String, Object>of(
                "customerId", "C-1",
                "passwordHash", "x",
                "contacts", List.of(Map.of("phone", "300", "apiToken", "y")));

        var clean = FieldRedactor.redact(document, Set.of("passwordHash", "apiToken"));

        assertThat(clean).isEqualTo(Map.of("customerId", "C-1", "contacts", List.of(Map.of("phone", "300"))));
    }
}
