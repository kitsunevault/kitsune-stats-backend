package com.kitsunestats.domain.port.out;

import com.kitsunestats.domain.model.MiTorneo;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Mis torneos de cada jugador (tablas mis_torneos*). Todo filtrado por su usuarioId: nadie ve los de otro. */
public interface MisTorneosRepositoryPort {

    /** Los visibles, del más nuevo al más viejo, con sus gastos y premios. */
    List<MiTorneo> listar(Long usuarioId);

    Optional<MiTorneo> obtener(Long usuarioId, Long id);

    Long crear(Long usuarioId, MiTorneo torneo);

    /** Reemplaza todo (también la lista de gastos y la de premios). false si no es suyo. */
    boolean actualizar(Long usuarioId, Long id, MiTorneo torneo);

    boolean borrar(Long usuarioId, Long id);

    /** Uno importado que el jugador quitó: se oculta para no volver a importarlo. */
    boolean ocultar(Long usuarioId, Long id);

    /** "tiendaId:torneoTiendaId" de los ya importados (también los ocultos). */
    Set<String> importados(Long usuarioId);
}
