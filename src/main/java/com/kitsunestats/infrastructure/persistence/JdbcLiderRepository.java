package com.kitsunestats.infrastructure.persistence;

import com.kitsunestats.domain.model.Lider;
import com.kitsunestats.domain.port.out.LiderRepositoryPort;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcLiderRepository implements LiderRepositoryPort {

    private final JdbcTemplate jdbc;

    static Lider lider(ResultSet rs, int i) throws SQLException {
        Array colores = rs.getArray("colores");
        return new Lider(
            rs.getString("codigo"), rs.getString("nombre"),
            colores == null ? List.of() : Arrays.stream((Object[]) colores.getArray()).map(String::valueOf).toList(),
            rs.getString("set_codigo"), rs.getString("imagen_url"), rs.getBoolean("activo")
        );
    }

    @Override
    public List<Lider> listar(boolean soloActivos) {
        return jdbc.query(
            "SELECT * FROM lideres" + (soloActivos ? " WHERE activo" : "") + " ORDER BY set_codigo, codigo", JdbcLiderRepository::lider
        );
    }

    @Override
    public Optional<Lider> obtener(String codigo) {
        return jdbc.query("SELECT * FROM lideres WHERE codigo = ?", JdbcLiderRepository::lider, codigo).stream().findFirst();
    }

    @Override
    public Lider guardar(Lider l) {
        jdbc.update("""
            INSERT INTO lideres (codigo, nombre, colores, set_codigo, imagen_url, activo) VALUES (?, ?, ?::text[], ?, ?, ?)
            ON CONFLICT (codigo) DO UPDATE SET nombre = EXCLUDED.nombre, colores = EXCLUDED.colores, set_codigo = EXCLUDED.set_codigo,
                imagen_url = EXCLUDED.imagen_url, activo = EXCLUDED.activo
            """, l.codigo(), l.nombre(), "{" + String.join(",", l.colores()) + "}", l.setCodigo(), l.imagenUrl(), l.activo());
        return obtener(l.codigo()).orElseThrow();
    }
}
