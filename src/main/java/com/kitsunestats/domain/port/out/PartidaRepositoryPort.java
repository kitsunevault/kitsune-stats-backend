package com.kitsunestats.domain.port.out;

import com.kitsunestats.domain.model.Filtro;
import com.kitsunestats.domain.model.Partida;
import java.util.List;
import java.util.Optional;

public interface PartidaRepositoryPort {

    /** Guarda una partida nueva (sin id ni creadoEn) y la regresa completa. */
    Partida crear(Partida partida);

    Optional<Partida> obtener(Long id);

    /** Cambia lo que el jugador puede corregir (líderes, quién empezó, quién ganó, fecha, ronda, notas). */
    Partida actualizar(Partida partida);

    void borrar(Long id);

    void cambiarConfirmacion(Long id, String confirmacion);

    void ocultar(Long id, boolean oculta);

    /**
     * Las partidas de un jugador, desde SU lado: las que registró y las que confirmó como rival
     * (ya volteadas con delLadoDelRival). Más recientes primero.
     */
    List<Partida> deJugador(Long usuarioId, Filtro filtro, int limite, int saltar);

    /** Partidas que otro registró con este jugador de rival y que esperan su confirmación. */
    List<Partida> porConfirmar(Long usuarioId);

    /** Cuántas registró hoy (límite diario contra registros masivos). */
    int registradasHoy(Long jugadorId);

    /** ¿Ya hay una partida (no rechazada) de esa ronda de ese torneo entre esos dos jugadores? */
    boolean existeRonda(String tiendaId, Long torneoId, int ronda, Long jugadorA, Long jugadorB);

    /** Para el administrador: las más recientes de todos, con las ocultas. */
    List<Partida> recientes(int limite, int saltar);
}
