package com.kitsunestats.infrastructure.persistence;

import com.kitsunestats.domain.model.Filtro;
import com.kitsunestats.domain.model.Partida;
import com.kitsunestats.domain.port.out.PartidaRepositoryPort;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcPartidaRepository implements PartidaRepositoryPort {

    private final JdbcTemplate jdbc;

    private static final String COLUMNAS = """
        id, jugador_id, jugador_username, lider, rival_lider, empece, gane, jugada_en, tipo, rival_id, rival_username, confirmacion,
        tienda_id, tienda_nombre, torneo_id, torneo_nombre, ronda, notas, oculta, creado_en
        """;

    /** Las mismas columnas, vistas desde el rival (para las partidas que él confirmó). */
    private static final String COLUMNAS_DEL_RIVAL = """
        id, rival_id, rival_username, rival_lider, lider, NOT empece, NOT gane, jugada_en, tipo, jugador_id, jugador_username, confirmacion,
        tienda_id, tienda_nombre, torneo_id, torneo_nombre, ronda, NULL, oculta, creado_en
        """;

    static Partida partida(ResultSet rs, int i) throws SQLException {
        return new Partida(
            rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6), rs.getBoolean(7),
            rs.getDate(8).toLocalDate(), rs.getString(9), rs.getObject(10, Long.class), rs.getString(11), rs.getString(12),
            rs.getString(13), rs.getString(14), rs.getObject(15, Long.class), rs.getString(16), rs.getObject(17, Integer.class),
            rs.getString(18), rs.getBoolean(19), rs.getTimestamp(20).toLocalDateTime()
        );
    }

    @Override
    public Partida crear(Partida p) {
        Long id = jdbc.queryForObject("""
            INSERT INTO partidas (jugador_id, jugador_username, lider, rival_lider, empece, gane, jugada_en, tipo, rival_id, rival_username,
                confirmacion, tienda_id, tienda_nombre, torneo_id, torneo_nombre, ronda, notas)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            """, Long.class,
            p.jugadorId(), p.jugadorUsername(), p.lider(), p.rivalLider(), p.empece(), p.gane(), Date.valueOf(p.jugadaEn()), p.tipo(),
            p.rivalId(), p.rivalUsername(), p.confirmacion(), p.tiendaId(), p.tiendaNombre(), p.torneoId(), p.torneoNombre(), p.ronda(), p.notas()
        );
        return obtener(id).orElseThrow();
    }

    @Override
    public Optional<Partida> obtener(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM partidas WHERE id = ?", JdbcPartidaRepository::partida, id).stream().findFirst();
    }

    @Override
    public Partida actualizar(Partida p) {
        jdbc.update(
            "UPDATE partidas SET lider = ?, rival_lider = ?, empece = ?, gane = ?, jugada_en = ?, notas = ?, confirmacion = ? WHERE id = ?",
            p.lider(), p.rivalLider(), p.empece(), p.gane(), Date.valueOf(p.jugadaEn()), p.notas(), p.confirmacion(), p.id()
        );
        return obtener(p.id()).orElseThrow();
    }

    @Override
    public void borrar(Long id) {
        jdbc.update("DELETE FROM partidas WHERE id = ?", id);
    }

    @Override
    public void cambiarConfirmacion(Long id, String confirmacion) {
        jdbc.update("UPDATE partidas SET confirmacion = ?, confirmada_en = now() WHERE id = ?", confirmacion, id);
    }

    @Override
    public void ocultar(Long id, boolean oculta) {
        jdbc.update("UPDATE partidas SET oculta = ? WHERE id = ?", oculta, id);
    }

    @Override
    public List<Partida> deJugador(Long usuarioId, Filtro f, int limite, int saltar) {
        // Mismo filtro de los dos lados; el líder del filtro es el MÍO (lider de un lado, rival_lider del otro).
        StringBuilder propias = new StringBuilder("SELECT " + COLUMNAS + " FROM partidas WHERE jugador_id = ?");
        StringBuilder confirmadas = new StringBuilder(
            "SELECT " + COLUMNAS_DEL_RIVAL + " FROM partidas WHERE rival_id = ? AND confirmacion = 'CONFIRMADA'"
        );
        List<Object> p1 = new ArrayList<>(List.of(usuarioId));
        List<Object> p2 = new ArrayList<>(List.of(usuarioId));
        if (f.desde() != null) {
            propias.append(" AND jugada_en >= ?");
            confirmadas.append(" AND jugada_en >= ?");
            p1.add(Date.valueOf(f.desde()));
            p2.add(Date.valueOf(f.desde()));
        }
        if (f.tipo() != null) {
            propias.append(" AND tipo = ?");
            confirmadas.append(" AND tipo = ?");
            p1.add(f.tipo());
            p2.add(f.tipo());
        }
        if (f.soloVerificadas()) {
            propias.append(" AND confirmacion = 'CONFIRMADA'");
        }
        if (f.tiendaId() != null) {
            propias.append(" AND tienda_id = ?");
            confirmadas.append(" AND tienda_id = ?");
            p1.add(f.tiendaId());
            p2.add(f.tiendaId());
        }
        if (f.lider() != null) {
            propias.append(" AND lider = ?");
            confirmadas.append(" AND rival_lider = ?");
            p1.add(f.lider());
            p2.add(f.lider());
        }
        List<Object> params = new ArrayList<>(p1);
        params.addAll(p2);
        params.add(limite);
        params.add(saltar);
        return jdbc.query(
            "SELECT * FROM (" + propias + " UNION ALL " + confirmadas + ") x ORDER BY jugada_en DESC, creado_en DESC, id DESC LIMIT ? OFFSET ?",
            JdbcPartidaRepository::partida, params.toArray()
        );
    }

    @Override
    public List<Partida> porConfirmar(Long usuarioId) {
        return jdbc.query(
            "SELECT " + COLUMNAS + " FROM partidas WHERE rival_id = ? AND confirmacion = 'PENDIENTE' ORDER BY creado_en DESC",
            JdbcPartidaRepository::partida, usuarioId
        );
    }

    @Override
    public int registradasHoy(Long jugadorId) {
        Integer n = jdbc.queryForObject(
            "SELECT count(*) FROM partidas WHERE jugador_id = ? AND creado_en >= current_date", Integer.class, jugadorId
        );
        return n == null ? 0 : n;
    }

    @Override
    public boolean existeRonda(String tiendaId, Long torneoId, int ronda, Long jugadorA, Long jugadorB) {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM partidas
            WHERE tienda_id = ? AND torneo_id = ? AND ronda = ? AND confirmacion <> 'RECHAZADA'
              AND ((jugador_id = ? AND rival_id = ?) OR (jugador_id = ? AND rival_id = ?))
            """, Integer.class, tiendaId, torneoId, ronda, jugadorA, jugadorB, jugadorB, jugadorA);
        return n != null && n > 0;
    }

    @Override
    public List<Partida> recientes(int limite, int saltar) {
        return jdbc.query(
            "SELECT " + COLUMNAS + " FROM partidas ORDER BY creado_en DESC, id DESC LIMIT ? OFFSET ?", JdbcPartidaRepository::partida, limite, saltar
        );
    }
}
