package com.kitsunestats.infrastructure.persistence;

import com.kitsunestats.domain.model.MetaPrivado;
import com.kitsunestats.domain.model.MetaPrivado.Invitacion;
import com.kitsunestats.domain.model.MetaPrivado.Miembro;
import com.kitsunestats.domain.port.out.MetaRepositoryPort;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JdbcMetaRepository implements MetaRepositoryPort {

    private final JdbcTemplate jdbc;

    private static final String SELECT = """
        SELECT m.id, m.nombre, m.descripcion, m.dueno_id, m.dueno_username, m.codigo_invitacion, m.inicia, m.termina,
               m.lideres, m.archivado, m.creado_en, (SELECT count(*) FROM meta_miembros x WHERE x.meta_id = m.id) AS miembros
        FROM metas m
        """;

    private static MetaPrivado meta(ResultSet rs, int i) throws SQLException {
        Array lideres = rs.getArray("lideres");
        return new MetaPrivado(
            rs.getLong("id"), rs.getString("nombre"), rs.getString("descripcion"), rs.getLong("dueno_id"), rs.getString("dueno_username"),
            rs.getString("codigo_invitacion"), fecha(rs.getDate("inicia")), fecha(rs.getDate("termina")),
            lideres == null ? List.of() : Arrays.asList((String[]) lideres.getArray()),
            rs.getBoolean("archivado"), rs.getTimestamp("creado_en").toLocalDateTime(), rs.getInt("miembros")
        );
    }

    private static LocalDate fecha(Date d) {
        return d == null ? null : d.toLocalDate();
    }

    private static Date fecha(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    /** Los líderes van como texto separado por comas: string_to_array('', ',') es un arreglo vacío. */
    private static String lideres(List<String> lideres) {
        return lideres == null ? "" : String.join(",", lideres);
    }

    @Override
    @Transactional
    public MetaPrivado crear(MetaPrivado m) {
        Long id = jdbc.queryForObject("""
            INSERT INTO metas (nombre, descripcion, dueno_id, dueno_username, codigo_invitacion, inicia, termina, lideres)
            VALUES (?, ?, ?, ?, ?, ?, ?, string_to_array(?, ',')::varchar[]) RETURNING id
            """, Long.class, m.nombre(), m.descripcion(), m.duenoId(), m.duenoUsername(), m.codigoInvitacion(),
            fecha(m.inicia()), fecha(m.termina()), lideres(m.lideres()));
        agregarMiembro(id, m.duenoId(), m.duenoUsername());
        return obtener(id).orElseThrow();
    }

    @Override
    public Optional<MetaPrivado> obtener(Long id) {
        return jdbc.query(SELECT + " WHERE m.id = ?", JdbcMetaRepository::meta, id).stream().findFirst();
    }

    @Override
    public Optional<MetaPrivado> porCodigo(String codigo) {
        return jdbc.query(SELECT + " WHERE m.codigo_invitacion = ?", JdbcMetaRepository::meta, codigo).stream().findFirst();
    }

    @Override
    public List<MetaPrivado> deMiembro(Long usuarioId) {
        return jdbc.query(SELECT + """
             WHERE EXISTS (SELECT 1 FROM meta_miembros x WHERE x.meta_id = m.id AND x.usuario_id = ?)
             ORDER BY m.archivado, m.creado_en DESC
            """, JdbcMetaRepository::meta, usuarioId);
    }

    @Override
    public int activosDe(Long duenoId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM metas WHERE dueno_id = ? AND NOT archivado", Integer.class, duenoId);
        return n == null ? 0 : n;
    }

    @Override
    public MetaPrivado actualizar(MetaPrivado m) {
        jdbc.update("""
            UPDATE metas SET nombre = ?, descripcion = ?, inicia = ?, termina = ?, lideres = string_to_array(?, ',')::varchar[]
            WHERE id = ?
            """, m.nombre(), m.descripcion(), fecha(m.inicia()), fecha(m.termina()), lideres(m.lideres()), m.id());
        return obtener(m.id()).orElseThrow();
    }

    @Override
    public void cambiarCodigo(Long id, String codigo) {
        jdbc.update("UPDATE metas SET codigo_invitacion = ? WHERE id = ?", codigo, id);
    }

    @Override
    public void archivar(Long id, boolean archivado) {
        jdbc.update("UPDATE metas SET archivado = ? WHERE id = ?", archivado, id);
    }

    @Override
    public boolean esMiembro(Long metaId, Long usuarioId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM meta_miembros WHERE meta_id = ? AND usuario_id = ?", Integer.class, metaId, usuarioId);
        return n != null && n > 0;
    }

    @Override
    public List<Miembro> miembros(Long metaId) {
        return jdbc.query(
            "SELECT usuario_id, username, unido_en FROM meta_miembros WHERE meta_id = ? ORDER BY unido_en, usuario_id",
            (rs, i) -> new Miembro(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()), metaId
        );
    }

    @Override
    public void agregarMiembro(Long metaId, Long usuarioId, String username) {
        jdbc.update(
            "INSERT INTO meta_miembros (meta_id, usuario_id, username) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", metaId, usuarioId, username
        );
    }

    @Override
    public void quitarMiembro(Long metaId, Long usuarioId) {
        jdbc.update("DELETE FROM meta_miembros WHERE meta_id = ? AND usuario_id = ?", metaId, usuarioId);
    }

    private static final String SELECT_INVITACION = """
        SELECT i.meta_id, m.nombre, m.dueno_username, i.usuario_id, i.username, i.invitado_en
        FROM meta_invitaciones i JOIN metas m ON m.id = i.meta_id
        """;

    private static Invitacion invitacion(ResultSet rs, int i) throws SQLException {
        return new Invitacion(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getTimestamp(6).toLocalDateTime());
    }

    @Override
    public List<Invitacion> invitacionesDe(Long usuarioId) {
        return jdbc.query(SELECT_INVITACION + " WHERE i.usuario_id = ? AND NOT m.archivado ORDER BY i.invitado_en DESC",
            JdbcMetaRepository::invitacion, usuarioId);
    }

    @Override
    public List<Invitacion> invitacionesDeMeta(Long metaId) {
        return jdbc.query(SELECT_INVITACION + " WHERE i.meta_id = ? ORDER BY i.invitado_en", JdbcMetaRepository::invitacion, metaId);
    }

    @Override
    public boolean invitar(Long metaId, Long usuarioId, String username) {
        return jdbc.update(
            "INSERT INTO meta_invitaciones (meta_id, usuario_id, username) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", metaId, usuarioId, username
        ) == 1;
    }

    @Override
    public boolean borrarInvitacion(Long metaId, Long usuarioId) {
        return jdbc.update("DELETE FROM meta_invitaciones WHERE meta_id = ? AND usuario_id = ?", metaId, usuarioId) == 1;
    }
}
