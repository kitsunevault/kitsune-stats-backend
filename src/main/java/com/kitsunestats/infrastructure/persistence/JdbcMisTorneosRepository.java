package com.kitsunestats.infrastructure.persistence;

import com.kitsunestats.domain.model.MiTorneo;
import com.kitsunestats.domain.model.MiTorneo.Gasto;
import com.kitsunestats.domain.model.MiTorneo.Premio;
import com.kitsunestats.domain.port.out.MisTorneosRepositoryPort;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Ver MisTorneosRepositoryPort. */
@Component
@RequiredArgsConstructor
public class JdbcMisTorneosRepository implements MisTorneosRepositoryPort {

    private final JdbcTemplate jdbc;

    private static final String SELECT = """
        SELECT t.id, t.fecha, t.nombre, t.tienda_nombre, t.tienda_id, t.torneo_tienda_id, t.juego, t.mazo, t.lugar, t.jugadores, t.victorias,
               t.derrotas, t.empates, t.entrada, t.moneda, t.notas, t.deck_id, d.nombre AS deck_nombre
        FROM mis_torneos t
        LEFT JOIN mis_decks d ON d.id = t.deck_id
        """;

    private static MiTorneo torneo(ResultSet rs) throws SQLException {
        return new MiTorneo(
            rs.getLong("id"), rs.getDate("fecha").toLocalDate(), rs.getString("nombre"), rs.getString("tienda_nombre"),
            rs.getString("tienda_id"), rs.getObject("torneo_tienda_id", Long.class), rs.getString("juego"), rs.getString("mazo"),
            rs.getObject("lugar", Integer.class), rs.getObject("jugadores", Integer.class), rs.getObject("victorias", Integer.class),
            rs.getObject("derrotas", Integer.class), rs.getObject("empates", Integer.class), rs.getBigDecimal("entrada"),
            rs.getString("moneda"), rs.getString("notas"), new ArrayList<>(), new ArrayList<>(), rs.getObject("deck_id", Long.class),
            rs.getString("deck_nombre"), new ArrayList<>()
        );
    }

    @Override
    public List<MiTorneo> listar(Long usuarioId) {
        List<MiTorneo> torneos = jdbc.query(SELECT + " WHERE t.usuario_id = ? AND NOT t.oculto ORDER BY t.fecha DESC, t.id DESC",
            (rs, i) -> torneo(rs), usuarioId);
        return conDetalle(torneos);
    }

    @Override
    public Optional<MiTorneo> obtener(Long usuarioId, Long id) {
        List<MiTorneo> torneos = jdbc.query(SELECT + " WHERE t.usuario_id = ? AND t.id = ? AND NOT t.oculto", (rs, i) -> torneo(rs), usuarioId, id);
        return conDetalle(torneos).stream().findFirst();
    }

    /** Gastos y premios de todos los torneos de la lista en dos consultas. */
    private List<MiTorneo> conDetalle(List<MiTorneo> torneos) {
        if (torneos.isEmpty()) {
            return torneos;
        }
        Map<Long, MiTorneo> porId = new HashMap<>();
        torneos.forEach(t -> porId.put(t.id(), t));
        Long[] ids = porId.keySet().toArray(Long[]::new);
        jdbc.query("SELECT torneo_id, concepto, monto FROM mis_torneos_gastos WHERE torneo_id = ANY (?) ORDER BY orden, id",
            rs -> {
                porId.get(rs.getLong("torneo_id")).gastos().add(new Gasto(rs.getString("concepto"), rs.getBigDecimal("monto")));
            }, (Object) ids);
        jdbc.query("""
            SELECT torneo_id, tipo, descripcion, cantidad, valor, estado, precio_venta, vendida_en, origen
            FROM mis_torneos_premios WHERE torneo_id = ANY (?) ORDER BY orden, id
            """, rs -> {
                porId.get(rs.getLong("torneo_id")).premios().add(new Premio(
                    rs.getString("tipo"), rs.getString("descripcion"), rs.getInt("cantidad"), rs.getBigDecimal("valor"),
                    rs.getString("estado"), rs.getBigDecimal("precio_venta"), fecha(rs.getDate("vendida_en")), rs.getString("origen")));
            }, (Object) ids);
        // Bloqueada: la partida tiene rival (o él es el rival): se corrige en "Mis stats", no aquí.
        jdbc.query("""
            SELECT r.torneo_id, r.ronda, r.rival_lider, r.rival, r.resultado, r.empece, r.partida_id,
                   (p.id IS NOT NULL AND (p.rival_id IS NOT NULL OR p.jugador_id <> t.usuario_id)) AS bloqueada
            FROM mis_torneos_rondas r
            JOIN mis_torneos t ON t.id = r.torneo_id
            LEFT JOIN partidas p ON p.id = r.partida_id
            WHERE r.torneo_id = ANY (?) ORDER BY r.ronda
            """, rs -> {
                porId.get(rs.getLong("torneo_id")).rondas().add(new MiTorneo.Ronda(
                    rs.getInt("ronda"), rs.getString("rival_lider"), rs.getString("rival"), rs.getString("resultado"),
                    rs.getObject("empece", Boolean.class), rs.getObject("partida_id", Long.class), rs.getBoolean("bloqueada")));
            }, (Object) ids);
        return torneos;
    }

    @Override
    @Transactional
    public Long crear(Long usuarioId, MiTorneo t) {
        Long id = jdbc.queryForObject("""
            INSERT INTO mis_torneos (usuario_id, fecha, nombre, tienda_nombre, tienda_id, torneo_tienda_id, juego, mazo, lugar, jugadores,
                                     victorias, derrotas, empates, entrada, moneda, notas, deck_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            """, Long.class, usuarioId, Date.valueOf(t.fecha()), t.nombre(), t.tiendaNombre(), t.tiendaId(), t.torneoTiendaId(), t.juego(),
            t.mazo(), t.lugar(), t.jugadores(), t.victorias(), t.derrotas(), t.empates(), t.entrada(), t.moneda(), t.notas(), t.deckId());
        guardarDetalle(id, t);
        return id;
    }

    @Override
    @Transactional
    public boolean actualizar(Long usuarioId, Long id, MiTorneo t) {
        int filas = jdbc.update("""
            UPDATE mis_torneos SET fecha = ?, nombre = ?, tienda_nombre = ?, juego = ?, mazo = ?, lugar = ?, jugadores = ?, victorias = ?,
                                   derrotas = ?, empates = ?, entrada = ?, moneda = ?, notas = ?, deck_id = ?
            WHERE usuario_id = ? AND id = ? AND NOT oculto
            """, Date.valueOf(t.fecha()), t.nombre(), t.tiendaNombre(), t.juego(), t.mazo(), t.lugar(), t.jugadores(), t.victorias(),
            t.derrotas(), t.empates(), t.entrada(), t.moneda(), t.notas(), t.deckId(), usuarioId, id);
        if (filas == 0) {
            return false;
        }
        jdbc.update("DELETE FROM mis_torneos_gastos WHERE torneo_id = ?", id);
        jdbc.update("DELETE FROM mis_torneos_premios WHERE torneo_id = ?", id);
        guardarDetalle(id, t);
        return true;
    }

    private void guardarDetalle(Long id, MiTorneo t) {
        int orden = 0;
        for (Gasto g : t.gastos()) {
            jdbc.update("INSERT INTO mis_torneos_gastos (torneo_id, concepto, monto, orden) VALUES (?, ?, ?, ?)", id, g.concepto(), g.monto(),
                orden++);
        }
        orden = 0;
        for (Premio p : t.premios()) {
            jdbc.update("""
                INSERT INTO mis_torneos_premios (torneo_id, tipo, descripcion, cantidad, valor, estado, precio_venta, vendida_en, origen, orden)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, p.tipo(), p.descripcion(), p.cantidad(), p.valor(), p.estado(), p.precioVenta(), fecha(p.vendidaEn()), p.origen(),
                orden++);
        }
    }

    @Override
    public boolean borrar(Long usuarioId, Long id) {
        return jdbc.update("DELETE FROM mis_torneos WHERE usuario_id = ? AND id = ?", usuarioId, id) > 0;
    }

    @Override
    public boolean ocultar(Long usuarioId, Long id) {
        return jdbc.update("UPDATE mis_torneos SET oculto = true WHERE usuario_id = ? AND id = ?", usuarioId, id) > 0;
    }

    @Override
    public Set<String> importados(Long usuarioId) {
        return new HashSet<>(jdbc.query(
            "SELECT tienda_id || ':' || torneo_tienda_id FROM mis_torneos WHERE usuario_id = ? AND torneo_tienda_id IS NOT NULL",
            (rs, i) -> rs.getString(1), usuarioId));
    }

    @Override
    @Transactional
    public void guardarRondas(Long torneoId, List<MiTorneo.Ronda> rondas) {
        jdbc.update("DELETE FROM mis_torneos_rondas WHERE torneo_id = ?", torneoId);
        for (MiTorneo.Ronda r : rondas) {
            jdbc.update("""
                INSERT INTO mis_torneos_rondas (torneo_id, ronda, rival_lider, rival, resultado, empece, partida_id) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, torneoId, r.ronda(), r.rivalLider(), r.rival(), r.resultado(), r.empece(), r.partidaId());
        }
    }

    @Override
    public void actualizarRecord(Long torneoId, Integer victorias, Integer derrotas, Integer empates) {
        jdbc.update("UPDATE mis_torneos SET victorias = ?, derrotas = ?, empates = ? WHERE id = ?", victorias, derrotas, empates, torneoId);
    }

    @Override
    public List<PartidaDeTienda> partidasDeTorneosTienda(Long usuarioId) {
        return jdbc.query("""
            SELECT id, tienda_id, torneo_id, ronda, rival_lider, empece, gane, rival_id IS NOT NULL AS con_rival
            FROM partidas
            WHERE jugador_id = ? AND tipo = 'TORNEO' AND tienda_id IS NOT NULL AND NOT oculta AND confirmacion <> 'RECHAZADA'
            UNION ALL
            SELECT id, tienda_id, torneo_id, ronda, lider, NOT empece, NOT gane, true
            FROM partidas
            WHERE rival_id = ? AND confirmacion = 'CONFIRMADA' AND tipo = 'TORNEO' AND tienda_id IS NOT NULL AND NOT oculta
            """, (rs, i) -> new PartidaDeTienda(rs.getLong("id"), rs.getString("tienda_id"), rs.getLong("torneo_id"), rs.getInt("ronda"),
                rs.getString("rival_lider"), rs.getBoolean("empece"), rs.getBoolean("gane"), rs.getBoolean("con_rival")),
            usuarioId, usuarioId);
    }

    private static LocalDate fecha(Date d) {
        return d == null ? null : d.toLocalDate();
    }

    private static Date fecha(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }
}
