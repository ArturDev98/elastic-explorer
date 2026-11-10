package dev.portafolio.explorer.elastic;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import dev.portafolio.explorer.web.BadRequestException;

/** Búsqueda exacta según el tipo del campo; varios valores se combinan con OR. */
public final class QueryBuilder {

    private QueryBuilder() {
    }

    // En un campo text el espacio es parte del valor ("Ana María"): ahí solo separan coma, punto y coma o salto de línea.
    public static List<String> values(String input, FieldNode field) {
        if (input == null) {
            return List.of();
        }
        var separator = "text".equals(field.type()) ? "\\s*[,;\\n]+\\s*" : "[,;\\s]+";
        var values = Arrays.stream(input.trim().split(separator))
                .filter(v -> !v.isEmpty())
                .distinct()
                .toList();
        if (field.isNumeric()) {
            values.stream().filter(v -> !v.matches("-?\\d+(\\.\\d+)?")).findFirst().ifPresent(v -> {
                throw new BadRequestException("El campo " + field.path() + " es numérico y \"" + v + "\" no es un número");
            });
        }
        return values;
    }

    public static Map<String, Object> query(FieldNode field, List<String> values) {
        var query = plainQuery(field, values);
        // Un campo dentro de un nested no se encuentra con term a secas: hay que entrar por su path.
        return field.nestedPath() == null ? query
                : Map.of("nested", Map.of("path", field.nestedPath(), "query", query));
    }

    private static Map<String, Object> plainQuery(FieldNode field, List<String> values) {
        if ("text".equals(field.type()) && !field.hasKeyword()) {
            // Sin subcampo keyword no hay búsqueda exacta: la frase completa es lo más cercano.
            var phrases = values.stream()
                    .map(v -> Map.<String, Object>of("match_phrase", Map.of(field.path(), v)))
                    .toList();
            return phrases.size() == 1 ? phrases.getFirst()
                    : Map.of("bool", Map.of("should", phrases, "minimum_should_match", 1));
        }
        var path = "text".equals(field.type()) ? field.path() + ".keyword" : field.path();
        return values.size() == 1
                ? Map.of("term", Map.of(path, values.getFirst()))
                : Map.of("terms", Map.of(path, values));
    }
}
