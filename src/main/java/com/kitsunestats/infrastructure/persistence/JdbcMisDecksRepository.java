package com.kitsunestats.infrastructure.persistence;

import com.kitsunestats.domain.model.MiDeck;
import com.kitsunestats.domain.port.out.MisDecksRepositoryPort;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Ver MisDecksRepositoryPort. */
@Component
@RequiredArgsConstructor
public class JdbcMisDecksRepository implements MisDecksRepositoryPort {

    private final JdbcTemplate jdbc;

    private static MiDeck deck(ResultSet rs, int i) throws SQLException {
        return new MiDeck(rs.getLong("id"), rs.getString("nombre"), rs.getString("juego"), rs.getString("lider"), rs.getString("lista"),
            rs.getBoolean("retirado"), 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    @Override
    public List<MiDeck> listar(Long usuarioId) {
        return jdbc.query("SELECT id, nombre, juego, lider, lista, retirado FROM mis_decks WHERE usuario_id = ? ORDER BY retirado, lower(nombre)",
            JdbcMisDecksRepository::deck, usuarioId);
    }

    @Override
    public Optional<MiDeck> obtener(Long usuarioId, Long id) {
        return jdbc.query("SELECT id, nombre, juego, lider, lista, retirado FROM mis_decks WHERE usuario_id = ? AND id = ?",
            JdbcMisDecksRepository::deck, usuarioId, id).stream().findFirst();
    }

    @Override
    public Long crear(Long usuarioId, MiDeck d) {
        return jdbc.queryForObject("""
            INSERT INTO mis_decks (usuario_id, nombre, juego, lider, lista, retirado) VALUES (?, ?, ?, ?, ?, ?) RETURNING id
            """, Long.class, usuarioId, d.nombre(), d.juego(), d.lider(), d.lista(), d.retirado());
    }

    @Override
    public boolean actualizar(Long usuarioId, Long id, MiDeck d) {
        return jdbc.update("UPDATE mis_decks SET nombre = ?, juego = ?, lider = ?, lista = ?, retirado = ? WHERE usuario_id = ? AND id = ?",
            d.nombre(), d.juego(), d.lider(), d.lista(), d.retirado(), usuarioId, id) > 0;
    }

    @Override
    @Transactional
    public boolean borrar(Long usuarioId, Long id) {
        jdbc.update("""
            UPDATE mis_torneos SET mazo = coalesce(mazo, (SELECT nombre FROM mis_decks WHERE id = ?))
            WHERE usuario_id = ? AND deck_id = ?
            """, id, usuarioId, id);
        return jdbc.update("DELETE FROM mis_decks WHERE usuario_id = ? AND id = ?", usuarioId, id) > 0;
    }
}
