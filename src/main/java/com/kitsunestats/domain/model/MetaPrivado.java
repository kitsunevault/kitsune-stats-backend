package com.kitsunestats.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Un meta privado: un grupo (amigos, un equipo, una liga) con sus propias estadísticas que solo ven
 * sus miembros. Sus partidas no cuentan en el meta global. Reglas opcionales: temporada (inicia y
 * termina, las fechas de las partidas) y líderes permitidos (vacío = todos). Archivado: se sigue
 * viendo, pero ya no se registran partidas ni entra nadie.
 */
public record MetaPrivado(
    Long id,
    String nombre,
    String descripcion,
    Long duenoId,
    String duenoUsername,
    String codigoInvitacion,
    LocalDate inicia,
    LocalDate termina,
    List<String> lideres,
    boolean archivado,
    LocalDateTime creadoEn,
    int miembros
) {

    public boolean permiteLider(String codigo) {
        return lideres == null || lideres.isEmpty() || lideres.contains(codigo);
    }

    public boolean enTemporada(LocalDate fecha) {
        return (inicia == null || !fecha.isBefore(inicia)) && (termina == null || !fecha.isAfter(termina));
    }

    public record Miembro(Long usuarioId, String username, LocalDateTime unidoEn) {
    }

    /** Invitación por usuario pendiente de aceptar. */
    public record Invitacion(Long metaId, String metaNombre, String duenoUsername, Long usuarioId, String username, LocalDateTime invitadoEn) {
    }
}
