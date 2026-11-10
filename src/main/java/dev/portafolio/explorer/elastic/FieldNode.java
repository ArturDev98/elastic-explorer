package dev.portafolio.explorer.elastic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Un campo del mapping como árbol. Los objetos no traen "type" en Elasticsearch: aquí se marcan
 * como "object" (o "nested" si lo declaran). nestedPath es el ancestro nested más cercano, o null.
 */
public record FieldNode(String name, String path, String type, List<String> subfields, List<FieldNode> children,
                        String nestedPath) {

    private static final Set<String> NUMERIC = Set.of("long", "integer", "short", "byte", "double", "float",
            "half_float", "scaled_float", "unsigned_long");

    public boolean hasKeyword() {
        return subfields.contains("keyword");
    }

    public boolean isObject() {
        return !children.isEmpty();
    }

    public boolean isNumeric() {
        return NUMERIC.contains(type);
    }

    public static List<FieldNode> tree(Map<String, Object> properties, Set<String> hidden) {
        return tree(properties, "", null, hidden);
    }

    @SuppressWarnings("unchecked")
    private static List<FieldNode> tree(Map<String, Object> properties, String prefix, String nestedPath,
                                        Set<String> hidden) {
        var fields = new ArrayList<FieldNode>();
        properties.forEach((name, value) -> {
            if (hidden.contains(name) || !(value instanceof Map<?, ?>)) {
                return;
            }
            var definition = (Map<String, Object>) value;
            var path = prefix + name;
            var type = String.valueOf(definition.getOrDefault("type", "object"));
            var childNested = "nested".equals(type) ? path : nestedPath;
            var children = definition.get("properties") instanceof Map<?, ?> p
                    ? tree((Map<String, Object>) p, path + ".", childNested, hidden)
                    : List.<FieldNode>of();
            var subfields = definition.get("fields") instanceof Map<?, ?> f
                    ? List.copyOf(((Map<String, Object>) f).keySet())
                    : List.<String>of();
            fields.add(new FieldNode(name, path, type, subfields, children, nestedPath));
        });
        fields.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return fields;
    }

    /** Todos los campos hoja por ruta completa ("customer.address.city"). */
    public static Map<String, FieldNode> leavesByPath(List<FieldNode> tree) {
        var result = new LinkedHashMap<String, FieldNode>();
        collect(tree, result);
        return result;
    }

    private static void collect(List<FieldNode> fields, Map<String, FieldNode> result) {
        for (var field : fields) {
            if (field.isObject()) {
                collect(field.children(), result);
            } else {
                result.put(field.path(), field);
            }
        }
    }
}
