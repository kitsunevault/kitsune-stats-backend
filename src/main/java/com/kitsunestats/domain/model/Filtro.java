package com.kitsunestats.domain.model;

import java.time.LocalDate;

/**
 * Filtro de las stats. desde null = desde siempre. tipo null = casual y torneo. soloVerificadas: solo
 * partidas confirmadas por los dos jugadores. tiendaId: solo partidas de torneos de esa tienda.
 * lider: solo partidas donde se jugó ese líder (historial personal).
 *
 * metaId: en las stats de un meta, null = el meta global (solo partidas sin meta privado) y un id =
 * solo las de ese meta privado. En lo personal, null = todas las del jugador.
 */
public record Filtro(LocalDate desde, String tipo, boolean soloVerificadas, String tiendaId, String lider, Long metaId) {

    public static Filtro todo() {
        return new Filtro(null, null, false, null, null, null);
    }

    public Filtro conMeta(Long meta) {
        return new Filtro(desde, tipo, soloVerificadas, tiendaId, lider, meta);
    }
}
