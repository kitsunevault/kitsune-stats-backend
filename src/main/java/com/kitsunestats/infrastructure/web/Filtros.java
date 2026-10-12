package com.kitsunestats.infrastructure.web;

import com.kitsunestats.domain.model.Filtro;
import java.time.LocalDate;

/** Filtro de stats desde los parámetros de la URL. periodo: 7, 30, 90, 365 (días) o "todo". */
final class Filtros {

    private Filtros() {
    }

    static Filtro de(String periodo, String tipo, Boolean verificadas, String tienda, String lider) {
        LocalDate desde = switch (periodo == null ? "todo" : periodo) {
            case "7" -> LocalDate.now().minusDays(7);
            case "30" -> LocalDate.now().minusDays(30);
            case "90" -> LocalDate.now().minusDays(90);
            case "365" -> LocalDate.now().minusDays(365);
            case "todo" -> null;
            default -> throw new IllegalArgumentException("Periodo inválido: " + periodo);
        };
        String t = tipo == null || tipo.isBlank() || "TODOS".equalsIgnoreCase(tipo) ? null : tipo.trim().toUpperCase();
        if (t != null && !t.equals("CASUAL") && !t.equals("TORNEO")) {
            throw new IllegalArgumentException("Tipo inválido: " + tipo);
        }
        return new Filtro(
            desde, t, Boolean.TRUE.equals(verificadas), tienda == null || tienda.isBlank() ? null : tienda.trim(),
            lider == null || lider.isBlank() ? null : lider.trim().toUpperCase(), null
        );
    }
}
