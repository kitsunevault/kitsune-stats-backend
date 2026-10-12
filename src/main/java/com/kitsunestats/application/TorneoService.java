package com.kitsunestats.application;

import com.kitsunestats.domain.model.Kitsune.Tienda;
import com.kitsunestats.domain.model.Kitsune.Torneo;
import com.kitsunestats.domain.port.out.KitsuneCuentasPort;
import com.kitsunestats.domain.port.out.TorneosTiendasPort;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Torneos de One Piece de las tiendas de Kitsune Vaults en los que jugó el usuario (últimos 60 días),
 * para ligar sus partidas a un torneo y elegir a su rival de los participantes.
 */
@Service
@RequiredArgsConstructor
public class TorneoService {

    private final KitsuneCuentasPort cuentas;
    private final TorneosTiendasPort torneos;

    public List<Torneo> misTorneos(Long usuarioId) {
        return cuentas.tiendas(usuarioId).stream()
            .flatMap((Tienda t) -> torneos.torneosDe(usuarioId, t).stream())
            .filter(t -> t.juego() == null || t.juego().toLowerCase().contains("one piece"))
            .sorted(Comparator.comparing(Torneo::fecha).reversed())
            .toList();
    }
}
