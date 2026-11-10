package dev.portafolio.explorer.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Errores como {"mensaje": …} para que el front los muestre tal cual. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<Map<String, String>> badRequest(BadRequestException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<Map<String, String>> forbidden(ForbiddenException e) {
        return body(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<Map<String, String>> elasticRejected(RestClientResponseException e) {
        // 404 de Elasticsearch = el índice no existe; lo demás es un problema del clúster.
        if (e.getStatusCode().value() == 404) {
            return body(HttpStatus.NOT_FOUND, "El índice no existe en este clúster");
        }
        log.warn("Elasticsearch respondió {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        return body(HttpStatus.BAD_GATEWAY, "Elasticsearch rechazó la consulta (" + e.getStatusCode().value() + ")");
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<Map<String, String>> elasticDown(RestClientException e) {
        log.warn("Elasticsearch no respondió: {}", e.getMessage());
        return body(HttpStatus.BAD_GATEWAY, "Elasticsearch no respondió. ¿Está arriba el clúster?");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("mensaje", message));
    }
}
