package com.kitsunestats.infrastructure.persistence;

import com.kitsunestats.domain.model.Filtro;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/** Condiciones del filtro de stats sobre la tabla partidas (alias p), con sus parámetros en orden. */
final class FiltroSql {

    final StringBuilder sql = new StringBuilder();
    final List<Object> params = new ArrayList<>();

    private FiltroSql() {
    }

    /**
     * Las que cuentan en las stats de un meta: ni ocultas por el administrador ni rechazadas por el
     * rival, y del meta del filtro (sin metaId, el global: las que no son de ningún meta privado).
     */
    static FiltroSql globales(Filtro f) {
        FiltroSql r = new FiltroSql();
        r.sql.append(" NOT p.oculta AND p.confirmacion <> 'RECHAZADA'");
        if (f.metaId() == null) {
            r.sql.append(" AND p.meta_id IS NULL");
        } else {
            r.sql.append(" AND p.meta_id = ?");
            r.params.add(f.metaId());
        }
        if (f.desde() != null) {
            r.sql.append(" AND p.jugada_en >= ?");
            r.params.add(Date.valueOf(f.desde()));
        }
        if (f.tipo() != null) {
            r.sql.append(" AND p.tipo = ?");
            r.params.add(f.tipo());
        }
        if (f.soloVerificadas()) {
            r.sql.append(" AND p.confirmacion = 'CONFIRMADA'");
        }
        if (f.tiendaId() != null) {
            r.sql.append(" AND p.tienda_id = ?");
            r.params.add(f.tiendaId());
        }
        return r;
    }
}
