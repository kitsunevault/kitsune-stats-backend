package com.kitsunestats.domain.port.out;

import com.kitsunestats.domain.model.Filtro;
import com.kitsunestats.domain.model.Stats.Matchup;
import com.kitsunestats.domain.model.Stats.Semana;
import java.util.List;

/**
 * Sumas globales sobre todas las partidas que cuentan (no ocultas ni rechazadas). Cada partida
 * aporta sus dos lados: el del que la registró y el del rival.
 */
public interface StatsRepositoryPort {

    /**
     * Por líder: lados jugados (con espejos, para la popularidad), y sin espejos: partidas y
     * victorias en total, empezando primero y yendo segundo.
     */
    record FilaLider(String codigo, int lados, int partidas, int victorias, int primeroPartidas, int primeroVictorias,
                     int segundoPartidas, int segundoVictorias) {
    }

    int totalPartidas(Filtro filtro);

    List<FilaLider> porLider(Filtro filtro);

    /** Líder contra líder (sin espejos), con las victorias del primero. */
    List<Matchup> matchups(Filtro filtro);

    /** Partidas y victorias de un líder por semana (sin espejos). */
    List<Semana> tendencia(String lider, Filtro filtro);
}
