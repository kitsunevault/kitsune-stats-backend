package com.kitsunestats.domain.port.out;

import com.kitsunestats.domain.model.MiDeck;
import java.util.List;
import java.util.Optional;

/** Mis decks de cada jugador (tabla mis_decks). Todo filtrado por su usuarioId. */
public interface MisDecksRepositoryPort {

    /** Sin stats (en cero): las calcula MisTorneosService. */
    List<MiDeck> listar(Long usuarioId);

    Optional<MiDeck> obtener(Long usuarioId, Long id);

    Long crear(Long usuarioId, MiDeck deck);

    boolean actualizar(Long usuarioId, Long id, MiDeck deck);

    /** Sus torneos con ese deck se quedan con el nombre del deck en "mazo". */
    boolean borrar(Long usuarioId, Long id);
}
