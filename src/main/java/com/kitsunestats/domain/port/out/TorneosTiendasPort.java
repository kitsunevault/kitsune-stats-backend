package com.kitsunestats.domain.port.out;

import com.kitsunestats.domain.model.Kitsune.Tienda;
import com.kitsunestats.domain.model.Kitsune.Torneo;
import java.util.List;

/** Torneos de las tiendas de Kitsune Vaults (orders-service de cada tienda, con el secreto interno). */
public interface TorneosTiendasPort {

    /** Torneos recientes de esa tienda en los que jugó el usuario; vacío si la tienda no responde. */
    List<Torneo> torneosDe(Long usuarioId, Tienda tienda);
}
