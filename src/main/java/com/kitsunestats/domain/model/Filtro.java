package com.kitsunestats.domain.model;

import java.time.LocalDate;

/**
 * Filtro de las stats. desde null = desde siempre. tipo null = casual y torneo. soloVerificadas: solo
 * partidas confirmadas por los dos jugadores. tiendaId: solo partidas de torneos de esa tienda.
 * lider: solo partidas donde se jugó ese líder (historial personal).
 */
public record Filtro(LocalDate desde, String tipo, boolean soloVerificadas, String tiendaId, String lider) {

    public static Filtro todo() {
        return new Filtro(null, null, false, null, null);
    }
}
