package com.kitsunestats.infrastructure.web;

import com.kitsunestats.application.ConflictoException;
import com.kitsunestats.application.NoEncontradoException;
import com.kitsunestats.application.ProhibidoException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Errores con un mensaje para el jugador: {"message": "..."}. */
@RestControllerAdvice
public class ManejoErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejoErrores.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalido(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<Map<String, String>> malFormado(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "Datos inválidos");
    }

    @ExceptionHandler(NoEncontradoException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(NoEncontradoException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ProhibidoException.class)
    public ResponseEntity<Map<String, String>> prohibido(ProhibidoException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<Map<String, String>> conflicto(ConflictoException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> dependencia(IllegalStateException ex) {
        log.warn("Kitsune Vaults no respondió: {}", ex.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Kitsune Vaults no responde en este momento; intenta de nuevo.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus estatus, String mensaje) {
        return ResponseEntity.status(estatus).body(Map.of("message", mensaje == null ? "Error" : mensaje));
    }
}
