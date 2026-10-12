package com.kitsunestats.domain.model;

import java.time.LocalDate;

/**
 * Lo que manda el jugador al registrar una partida. rivalUsername (opcional): cuenta Kitsune del
 * rival, que la tendrá que confirmar. En una de torneo: tiendaId, torneoId y ronda; el rival se
 * elige de los participantes (rivalId) y la fecha es la del torneo. metaId: un meta privado del que
 * es miembro (solo casuales); null = meta global.
 */
public record NuevaPartida(
    String lider,
    String rivalLider,
    Boolean empece,
    Boolean gane,
    LocalDate jugadaEn,
    String tipo,
    String rivalUsername,
    Long rivalId,
    String tiendaId,
    Long torneoId,
    Integer ronda,
    String notas,
    Long metaId
) {
}
