package com.kitsunestats.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una partida registrada por un jugador, desde su punto de vista: su líder, el
 * del rival, si él empezó y si él ganó. Si indicó a su rival (cuenta Kitsune),
 * el rival la confirma o la rechaza: confirmada es UNA partida con dos lados
 * (cuenta para los dos y, en las globales, una sola vez y como verificada).
 *
 * confirmacion: SIN_RIVAL (no se indicó rival con cuenta), PENDIENTE, CONFIRMADA o RECHAZADA.
 * tipo: CASUAL o TORNEO (torneo de una tienda de Kitsune Vaults).
 */
public record Partida(
    Long id,
    Long jugadorId,
    String jugadorUsername,
    String lider,
    String rivalLider,
    boolean empece,
    boolean gane,
    LocalDate jugadaEn,
    String tipo,
    Long rivalId,
    String rivalUsername,
    String confirmacion,
    String tiendaId,
    String tiendaNombre,
    Long torneoId,
    String torneoNombre,
    Integer ronda,
    String notas,
    boolean oculta,
    LocalDateTime creadoEn
) {

    public static final String SIN_RIVAL = "SIN_RIVAL";
    public static final String PENDIENTE = "PENDIENTE";
    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String CASUAL = "CASUAL";
    public static final String TORNEO = "TORNEO";

    /** La partida vista desde el otro jugador (para el rival que la confirmó). */
    public Partida delLadoDelRival() {
        return new Partida(
            id, rivalId, rivalUsername, rivalLider, lider, !empece, !gane, jugadaEn, tipo, jugadorId, jugadorUsername,
            confirmacion, tiendaId, tiendaNombre, torneoId, torneoNombre, ronda, null, oculta, creadoEn
        );
    }
}
