package com.kitsunestats.domain.model;

import java.time.LocalDate;
import java.util.List;

/** Lo que Kitsune Stats lee de Kitsune Vaults: cuentas, tiendas y torneos. */
public final class Kitsune {

    private Kitsune() {
    }

    /** Cuenta Kitsune (global): solo id y username, nunca datos personales. */
    public record Cuenta(Long id, String username) {
    }

    public record Tienda(String tenantId, String nombre) {
    }

    /** Participante de un torneo: usuarioId null si jugó sin cuenta (viene del CSV de Bandai). */
    public record Participante(Long usuarioId, String nombre, int posicion) {
    }

    /**
     * Cómo le fue al usuario en un torneo de una tienda (para Mis torneos): su lugar, los sobres y puntos que ganó, la
     * entrada del calendario y la moneda de la tienda. Los campos pueden venir null (torneos viejos o abiertos).
     */
    public record ResultadoTorneo(String tiendaId, String tiendaNombre, Long id, String nombre, LocalDate fecha, String juego,
                                  boolean cerrado, java.math.BigDecimal entrada, String moneda, Integer lugar, Integer jugadores,
                                  Integer sobres, Integer puntosTienda) {
    }

    /** Un torneo de una tienda en el que jugó el usuario. */
    public record Torneo(String tiendaId, String tiendaNombre, Long id, String nombre, LocalDate fecha, String juego, boolean cerrado,
                         List<Participante> participantes) {
    }
}
