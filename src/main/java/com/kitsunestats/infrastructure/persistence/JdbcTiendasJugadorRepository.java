package com.kitsunestats.infrastructure.persistence;

import com.kitsunestats.domain.port.out.TiendasJugadorRepositoryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ver TiendasJugadorRepositoryPort. */
@Component
@RequiredArgsConstructor
public class JdbcTiendasJugadorRepository implements TiendasJugadorRepositoryPort {

    private final JdbcTemplate jdbc;

    @Override
    public List<Fila> listar(Long usuarioId) {
        return jdbc.query("SELECT id, nombre, ciudad, tienda_id, activa FROM tiendas_jugador WHERE usuario_id = ? ORDER BY lower(nombre), id",
            (rs, i) -> new Fila(rs.getLong("id"), rs.getString("nombre"), rs.getString("ciudad"), rs.getString("tienda_id"),
                rs.getBoolean("activa")), usuarioId);
    }

    @Override
    public Long crear(Long usuarioId, String nombre, String ciudad) {
        return jdbc.queryForObject("INSERT INTO tiendas_jugador (usuario_id, nombre, ciudad) VALUES (?, ?, ?) RETURNING id", Long.class,
            usuarioId, nombre, ciudad);
    }

    @Override
    public boolean actualizar(Long usuarioId, Long id, String nombre, String ciudad, boolean activa) {
        return jdbc.update("""
            UPDATE tiendas_jugador SET nombre = ?, ciudad = ?, activa = ?
            WHERE usuario_id = ? AND id = ? AND tienda_id IS NULL
            """, nombre, ciudad, activa, usuarioId, id) > 0;
    }

    @Override
    public boolean borrar(Long usuarioId, Long id) {
        return jdbc.update("DELETE FROM tiendas_jugador WHERE usuario_id = ? AND id = ? AND tienda_id IS NULL", usuarioId, id) > 0;
    }

    @Override
    public void estadoKitsune(Long usuarioId, String tiendaId, String nombre, boolean activa) {
        jdbc.update("""
            INSERT INTO tiendas_jugador (usuario_id, nombre, tienda_id, activa) VALUES (?, ?, ?, ?)
            ON CONFLICT (usuario_id, tienda_id) WHERE tienda_id IS NOT NULL DO UPDATE SET activa = EXCLUDED.activa, nombre = EXCLUDED.nombre
            """, usuarioId, nombre, tiendaId, activa);
    }
}
