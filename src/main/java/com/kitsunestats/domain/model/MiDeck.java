package com.kitsunestats.domain.model;

import java.math.BigDecimal;

/**
 * Un deck de Mis decks: nombre, juego, líder (si es One Piece: con él cuentan sus rondas en Mis stats y el meta), la
 * lista (texto o enlace) y si ya lo retiró. Al leer trae cómo le va con él en Mis torneos: torneos, récord de sus
 * rondas, lo gastado y lo ganado (en la moneda en que más jugó).
 */
public record MiDeck(
    Long id, String nombre, String juego, String lider, String lista, boolean retirado,
    int torneos, int victorias, int derrotas, int empates, BigDecimal gastado, BigDecimal ganado, String moneda
) {

    public MiDeck conStats(int torneos, int victorias, int derrotas, int empates, BigDecimal gastado, BigDecimal ganado, String moneda) {
        return new MiDeck(id, nombre, juego, lider, lista, retirado, torneos, victorias, derrotas, empates, gastado, ganado, moneda);
    }

    public boolean esOnePiece() {
        return juego != null && juego.toLowerCase().contains("one piece");
    }
}
