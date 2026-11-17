package com.kitsunestats.domain.port.out;

import java.util.List;

/** Las tiendas de cada jugador para Mis torneos (tabla tiendas_jugador). Todo filtrado por su usuarioId. */
public interface TiendasJugadorRepositoryPort {

    /** tiendaId null = la agregó él; con tiendaId = estado de una tienda de Kitsune Vaults. */
    record Fila(Long id, String nombre, String ciudad, String tiendaId, boolean activa) {
    }

    List<Fila> listar(Long usuarioId);

    Long crear(Long usuarioId, String nombre, String ciudad);

    /** Solo las que agregó él. false si no es suya. */
    boolean actualizar(Long usuarioId, Long id, String nombre, String ciudad, boolean activa);

    /** Solo las que agregó él. */
    boolean borrar(Long usuarioId, Long id);

    /** Activa o desactiva una tienda de Kitsune Vaults (crea su fila la primera vez). */
    void estadoKitsune(Long usuarioId, String tiendaId, String nombre, boolean activa);
}
