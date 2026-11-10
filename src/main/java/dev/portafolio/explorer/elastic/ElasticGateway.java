package dev.portafolio.explorer.elastic;

import java.util.List;
import java.util.Map;

/** Solo lectura por diseño: no hay ningún método que escriba o borre en el clúster. */
public interface ElasticGateway {

    /** Filas de _cat/indices: index, health, status, docs.count, store.size. */
    List<Map<String, Object>> indices(String cluster);

    /** El objeto "properties" del mapping del índice. */
    Map<String, Object> mappingProperties(String cluster, String index);

    Map<String, Object> search(String cluster, String index, Map<String, Object> body);
}
