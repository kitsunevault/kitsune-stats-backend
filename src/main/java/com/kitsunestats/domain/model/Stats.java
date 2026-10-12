package com.kitsunestats.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Tipos de las estadísticas (personales y globales). */
public final class Stats {

    private Stats() {
    }

    /** Partidas y victorias; winrate en % con un decimal (null si no hay partidas), siempre calculado de las dos. */
    public record Conteo(int partidas, int victorias, BigDecimal winrate) {

        public Conteo(int partidas, int victorias) {
            this(partidas, victorias, partidas == 0 ? null
                : BigDecimal.valueOf(victorias * 100L).divide(BigDecimal.valueOf(partidas), 1, RoundingMode.HALF_UP));
        }

        public static final Conteo VACIO = new Conteo(0, 0);
    }

    /**
     * Un líder: cuánto se jugó (popularidad = % de los lados jugados), su winrate (sin espejos) y
     * cómo le va empezando y yendo segundo.
     */
    public record StatsLider(Lider lider, Conteo total, BigDecimal popularidad, Conteo primero, Conteo segundo) {
    }

    /** Líder contra líder rival: partidas y victorias del primero. Espejos (mismo líder) no se cuentan. */
    public record Matchup(String lider, String rival, Conteo conteo) {
    }

    /** Partidas y winrate de un líder por semana (lunes). */
    public record Semana(LocalDate desde, Conteo conteo) {
    }

    /** Stats globales: cada partida cuenta sus dos lados (popularidad sobre lados jugados). */
    public record Meta(int partidas, List<StatsLider> lideres, List<Matchup> matchups, int muestraMinima) {
    }

    /** Página de un líder: su resumen, sus matchups (mejor a peor) y su tendencia semanal. */
    public record DetalleLider(StatsLider stats, List<Matchup> matchups, List<Semana> tendencia, int muestraMinima) {
    }

    /** Racha: positiva = victorias seguidas, negativa = derrotas seguidas (de la más reciente hacia atrás). */
    public record Personales(
        Conteo total, Conteo primero, Conteo segundo, int racha, List<StatsLider> porLider, List<Matchup> matchups, int porConfirmar
    ) {
    }
}
