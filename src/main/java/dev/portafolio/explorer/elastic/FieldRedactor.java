package dev.portafolio.explorer.elastic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Quita los campos ocultos de un documento a cualquier profundidad, antes de que salga del back. */
public final class FieldRedactor {

    private FieldRedactor() {
    }

    @SuppressWarnings("unchecked")
    public static Object redact(Object value, Set<String> hidden) {
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            ((Map<String, Object>) map).forEach((key, v) -> {
                if (!hidden.contains(key)) {
                    copy.put(key, redact(v, hidden));
                }
            });
            return copy;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(v -> redact(v, hidden)).toList();
        }
        return value;
    }
}
