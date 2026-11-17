package com.kitsunestats.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Un torneo al que fue el jugador, con lo que gastó (entrada y otros gastos) y lo que ganó (premios). Los de tiendas
 * de Kitsune Vaults llegan solos (tiendaId y torneoTiendaId); los demás los captura él. Ver MisTorneosService.
 */
public record MiTorneo(
    Long id,
    LocalDate fecha,
    String nombre,
    String tiendaNombre,
    String tiendaId,
    Long torneoTiendaId,
    String juego,
    String mazo,
    Integer lugar,
    Integer jugadores,
    Integer victorias,
    Integer derrotas,
    Integer empates,
    BigDecimal entrada,
    String moneda,
    String notas,
    List<Gasto> gastos,
    List<Premio> premios,
    /** Con qué deck de Mis decks lo jugó (deckNombre solo al leer); "mazo" se queda para los de antes. */
    Long deckId,
    String deckNombre,
    List<Ronda> rondas
) {

    /** Sin deck ni rondas (los que vienen de una tienda Kitsune y los de antes). */
    public MiTorneo(
        Long id, LocalDate fecha, String nombre, String tiendaNombre, String tiendaId, Long torneoTiendaId, String juego, String mazo,
        Integer lugar, Integer jugadores, Integer victorias, Integer derrotas, Integer empates, BigDecimal entrada, String moneda,
        String notas, List<Gasto> gastos, List<Premio> premios
    ) {
        this(id, fecha, nombre, tiendaNombre, tiendaId, torneoTiendaId, juego, mazo, lugar, jugadores, victorias, derrotas, empates, entrada,
            moneda, notas, gastos, premios, null, null, List.of());
    }

    /**
     * Una ronda del torneo. rivalLider: el líder del rival (One Piece); rival: el deck del rival en texto (otros
     * juegos o una nota). resultado: G, P o E. empece: null = no lo anotó. partidaId: la partida de torneo que la
     * cuenta en Mis stats y el meta. bloqueada: viene de una partida con rival (registrada en "Registrar partida"):
     * se corrige allá, no aquí.
     */
    public record Ronda(Integer ronda, String rivalLider, String rival, String resultado, Boolean empece, Long partidaId, boolean bloqueada) {
    }

    /** true si vino de una tienda de Kitsune Vaults. */
    public boolean importado() {
        return torneoTiendaId != null;
    }

    public record Gasto(String concepto, BigDecimal monto) {
    }

    /**
     * tipo: CARTA, SOBRES, PRODUCTO, CREDITO o EFECTIVO. valor: lo que vale (aprox.) lo que conserva o cambió; en
     * EFECTIVO y CREDITO, el monto. estado: CONSERVO, VENDIDA (con precioVenta y vendidaEn) o CAMBIADA. origen: TIENDA
     * (vino del torneo de una tienda Kitsune) o MANUAL.
     */
    public record Premio(
        String tipo, String descripcion, Integer cantidad, BigDecimal valor, String estado, BigDecimal precioVenta, LocalDate vendidaEn,
        String origen
    ) {
    }

    /**
     * Cuánto se gastó y se ganó (en una moneda). cobrado: efectivo, crédito y lo vendido; conservado: lo que vale lo
     * que se quedó o cambió. sinValor: premios que conserva y todavía no tienen valor anotado.
     */
    public record Totales(
        String clave, int torneos, BigDecimal entradas, BigDecimal otrosGastos, BigDecimal gastado, BigDecimal cobrado,
        BigDecimal conservado, BigDecimal ganado, BigDecimal neto, int sinValor
    ) {
    }

    /** Totales por moneda (casi siempre una) y desgloses por mes, tienda y juego (de la moneda principal). */
    public record Resumen(List<Totales> porMoneda, String moneda, List<Totales> porMes, List<Totales> porTienda, List<Totales> porJuego) {
    }

    /**
     * Una tienda del jugador: de Kitsune Vaults (kitsune, con tiendaId) o una que agregó él (id). Desactivada no sale
     * para elegirla y, si es de Kitsune Vaults, sus torneos ya no se agregan solos.
     */
    public record TiendaJugador(Long id, String nombre, String ciudad, String tiendaId, boolean kitsune, boolean activa) {
    }

    /** tiendas: sus tiendas (de Kitsune Vaults y las que agregó), activas y no. decks: Mis decks, con cómo le va. */
    public record MisTorneos(List<MiTorneo> torneos, Resumen resumen, List<TiendaJugador> tiendas, List<MiDeck> decks) {
    }
}
