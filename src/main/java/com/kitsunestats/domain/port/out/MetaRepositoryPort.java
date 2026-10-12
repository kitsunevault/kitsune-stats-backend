package com.kitsunestats.domain.port.out;

import com.kitsunestats.domain.model.MetaPrivado;
import com.kitsunestats.domain.model.MetaPrivado.Invitacion;
import com.kitsunestats.domain.model.MetaPrivado.Miembro;
import java.util.List;
import java.util.Optional;

/** Metas privados, sus miembros y sus invitaciones por usuario. */
public interface MetaRepositoryPort {

    /** Crea el meta (sin id) con su dueño como primer miembro. */
    MetaPrivado crear(MetaPrivado meta);

    Optional<MetaPrivado> obtener(Long id);

    Optional<MetaPrivado> porCodigo(String codigo);

    /** Los metas en los que el jugador es miembro (activos primero, luego los más nuevos). */
    List<MetaPrivado> deMiembro(Long usuarioId);

    /** Cuántos metas sin archivar creó el jugador (tope por jugador). */
    int activosDe(Long duenoId);

    /** Cambia nombre, descripción y reglas. */
    MetaPrivado actualizar(MetaPrivado meta);

    void cambiarCodigo(Long id, String codigo);

    void archivar(Long id, boolean archivado);

    boolean esMiembro(Long metaId, Long usuarioId);

    List<Miembro> miembros(Long metaId);

    void agregarMiembro(Long metaId, Long usuarioId, String username);

    void quitarMiembro(Long metaId, Long usuarioId);

    List<Invitacion> invitacionesDe(Long usuarioId);

    List<Invitacion> invitacionesDeMeta(Long metaId);

    /** false si ya estaba invitado. */
    boolean invitar(Long metaId, Long usuarioId, String username);

    /** false si no había invitación. */
    boolean borrarInvitacion(Long metaId, Long usuarioId);
}
