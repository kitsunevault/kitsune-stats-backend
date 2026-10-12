package com.kitsunestats.infrastructure.persistence;

import com.kitsunestats.domain.model.Filtro;
import com.kitsunestats.domain.model.Stats.Conteo;
import com.kitsunestats.domain.model.Stats.Matchup;
import com.kitsunestats.domain.model.Stats.Semana;
import com.kitsunestats.domain.port.out.StatsRepositoryPort;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sumas globales en SQL. "lados": cada partida aparece dos veces, una desde quien la registró y otra
 * desde su rival (con empece y gane al revés), así cada partida cuenta una sola vez por lado.
 */
@Component
@RequiredArgsConstructor
public class JdbcStatsRepository implements StatsRepositoryPort {

    private final JdbcTemplate jdbc;

    private static String lados(FiltroSql f) {
        return """
            WITH base AS (SELECT p.* FROM partidas p WHERE %s),
            lados AS (
                SELECT lider AS mio, rival_lider AS rival, empece, gane, jugada_en FROM base
                UNION ALL
                SELECT rival_lider, lider, NOT empece, NOT gane, jugada_en FROM base
            )
            """.formatted(f.sql);
    }

    /** Los parámetros del filtro van una vez (el CTE base se usa dos veces sin repetirlos). */
    private static Object[] params(FiltroSql f, Object... extra) {
        List<Object> todos = new ArrayList<>(f.params);
        todos.addAll(List.of(extra));
        return todos.toArray();
    }

    @Override
    public int totalPartidas(Filtro filtro) {
        FiltroSql f = FiltroSql.globales(filtro);
        Integer n = jdbc.queryForObject("SELECT count(*) FROM partidas p WHERE" + f.sql, Integer.class, f.params.toArray());
        return n == null ? 0 : n;
    }

    @Override
    public List<FilaLider> porLider(Filtro filtro) {
        FiltroSql f = FiltroSql.globales(filtro);
        return jdbc.query(lados(f) + """
            SELECT mio,
                   count(*) AS lados,
                   count(*) FILTER (WHERE mio <> rival) AS partidas,
                   count(*) FILTER (WHERE mio <> rival AND gane) AS victorias,
                   count(*) FILTER (WHERE mio <> rival AND empece) AS pp,
                   count(*) FILTER (WHERE mio <> rival AND empece AND gane) AS pv,
                   count(*) FILTER (WHERE mio <> rival AND NOT empece) AS sp,
                   count(*) FILTER (WHERE mio <> rival AND NOT empece AND gane) AS sv
            FROM lados GROUP BY mio
            """, (rs, i) -> new FilaLider(
                rs.getString("mio"), rs.getInt("lados"), rs.getInt("partidas"), rs.getInt("victorias"),
                rs.getInt("pp"), rs.getInt("pv"), rs.getInt("sp"), rs.getInt("sv")
            ), params(f));
    }

    @Override
    public List<Matchup> matchups(Filtro filtro) {
        FiltroSql f = FiltroSql.globales(filtro);
        return jdbc.query(lados(f) + """
            SELECT mio, rival, count(*) AS partidas, count(*) FILTER (WHERE gane) AS victorias
            FROM lados WHERE mio <> rival GROUP BY mio, rival
            """, (rs, i) -> new Matchup(rs.getString("mio"), rs.getString("rival"), new Conteo(rs.getInt("partidas"), rs.getInt("victorias"))),
            params(f));
    }

    @Override
    public List<Semana> tendencia(String lider, Filtro filtro) {
        FiltroSql f = FiltroSql.globales(filtro);
        return jdbc.query(lados(f) + """
            SELECT date_trunc('week', jugada_en)::date AS semana, count(*) AS partidas, count(*) FILTER (WHERE gane) AS victorias
            FROM lados WHERE mio = ? AND mio <> rival GROUP BY 1 ORDER BY 1
            """, (rs, i) -> new Semana(rs.getDate("semana").toLocalDate(), new Conteo(rs.getInt("partidas"), rs.getInt("victorias"))),
            params(f, lider));
    }
}
