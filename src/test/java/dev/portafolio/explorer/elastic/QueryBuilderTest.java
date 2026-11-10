package dev.portafolio.explorer.elastic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.portafolio.explorer.web.BadRequestException;

class QueryBuilderTest {

    private static final FieldNode KEYWORD = field("status", "keyword", List.of());
    private static final FieldNode TEXT_WITH_KEYWORD = field("fullName", "text", List.of("keyword"));
    private static final FieldNode TEXT_ONLY = field("notes", "text", List.of());
    private static final FieldNode LONG = field("orderId", "long", List.of());

    @Test
    void keywordSplitsBySpacesCommasAndRemovesDuplicates() {
        assertThat(QueryBuilder.values(" PAID, SHIPPED  PAID;CANCELLED ", KEYWORD))
                .containsExactly("PAID", "SHIPPED", "CANCELLED");
    }

    @Test
    void textKeepsSpacesInsideEachValue() {
        assertThat(QueryBuilder.values("Ana María Rojas, Luis Pérez", TEXT_WITH_KEYWORD))
                .containsExactly("Ana María Rojas", "Luis Pérez");
    }

    @Test
    void numericFieldsRejectNonNumbers() {
        assertThatThrownBy(() -> QueryBuilder.values("1001 abc", LONG))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("\"abc\" no es un número");
    }

    @Test
    void oneValueIsATermAndSeveralAreTerms() {
        assertThat(QueryBuilder.query(KEYWORD, List.of("PAID")))
                .isEqualTo(Map.of("term", Map.of("status", "PAID")));
        assertThat(QueryBuilder.query(LONG, List.of("1", "2")))
                .isEqualTo(Map.of("terms", Map.of("orderId", List.of("1", "2"))));
    }

    @Test
    void textWithKeywordSearchesExactOnTheSubfield() {
        assertThat(QueryBuilder.query(TEXT_WITH_KEYWORD, List.of("Ana María Rojas")))
                .isEqualTo(Map.of("term", Map.of("fullName.keyword", "Ana María Rojas")));
    }

    @Test
    void textWithoutKeywordFallsBackToPhrases() {
        assertThat(QueryBuilder.query(TEXT_ONLY, List.of("pago duplicado")))
                .isEqualTo(Map.of("match_phrase", Map.of("notes", "pago duplicado")));
        assertThat(QueryBuilder.query(TEXT_ONLY, List.of("a", "b")))
                .containsKey("bool");
    }

    @Test
    void fieldsInsideNestedAreWrappedInANestedQuery() {
        var sku = new FieldNode("sku", "items.sku", "keyword", List.of(), List.of(), "items");

        assertThat(QueryBuilder.query(sku, List.of("SKU-100")))
                .isEqualTo(Map.of("nested", Map.of("path", "items", "query", Map.of("term", Map.of("items.sku", "SKU-100")))));
    }

    private static FieldNode field(String name, String type, List<String> subfields) {
        return new FieldNode(name, name, type, subfields, List.of(), null);
    }
}
