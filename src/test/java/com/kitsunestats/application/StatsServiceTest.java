package com.kitsunestats.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitsunestats.domain.model.Lider;
import com.kitsunestats.domain.model.Partida;
import com.kitsunestats.domain.model.Stats.Personales;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatsServiceTest {

    private static final Map<String, Lider> CATALOGO = Map.of(
        "OP01-001", new Lider("OP01-001", "Zoro", List.of("RED"), "OP01", null, true),
        "OP05-119", new Lider("OP05-119", "Luffy", List.of("PURPLE"), "OP05", null, true),
        "OP05-062", new Lider("OP05-062", "Law", List.of("PURPLE", "BLACK"), "OP05", null, true)
    );

    private static Partida p(String mio, String rival, boolean empece, boolean gane, String confirmacion) {
        return new Partida(
            1L, 1L, "yo", mio, rival, empece, gane, LocalDate.now(), Partida.CASUAL, null, null, confirmacion,
            null, null, null, null, null, null, false, null, null, null
        );
    }

    @Test
    void totalPrimeroSegundoYRacha() {
        // De la más reciente a la más vieja: G G P G
        Personales r = StatsService.calcularPersonales(List.of(
            p("OP05-119", "OP05-062", true, true, Partida.SIN_RIVAL),
            p("OP05-119", "OP01-001", false, true, Partida.SIN_RIVAL),
            p("OP01-001", "OP05-062", true, false, Partida.SIN_RIVAL),
            p("OP05-119", "OP05-062", false, true, Partida.SIN_RIVAL)
        ), CATALOGO, 2);
        assertThat(r.total().partidas()).isEqualTo(4);
        assertThat(r.total().victorias()).isEqualTo(3);
        assertThat(r.total().winrate()).isEqualByComparingTo("75.0");
        assertThat(r.primero().partidas()).isEqualTo(2);
        assertThat(r.primero().victorias()).isEqualTo(1);
        assertThat(r.segundo().victorias()).isEqualTo(2);
        assertThat(r.racha()).isEqualTo(2);
        assertThat(r.porConfirmar()).isEqualTo(2);
        // Luffy: 3 partidas, todas ganadas; es el más jugado.
        assertThat(r.porLider().get(0).lider().codigo()).isEqualTo("OP05-119");
        assertThat(r.porLider().get(0).total().victorias()).isEqualTo(3);
        // Matchup más jugado: Luffy contra Law (2 de 2).
        assertThat(r.matchups().get(0).lider()).isEqualTo("OP05-119");
        assertThat(r.matchups().get(0).rival()).isEqualTo("OP05-062");
        assertThat(r.matchups().get(0).conteo().partidas()).isEqualTo(2);
    }

    @Test
    void lasRechazadasNoCuentanYLaRachaDeDerrotasEsNegativa() {
        Personales r = StatsService.calcularPersonales(List.of(
            p("OP05-119", "OP05-062", true, true, Partida.RECHAZADA),
            p("OP05-119", "OP05-062", true, false, Partida.CONFIRMADA),
            p("OP05-119", "OP05-062", true, false, Partida.PENDIENTE),
            p("OP05-119", "OP05-062", true, true, Partida.SIN_RIVAL)
        ), CATALOGO, 0);
        assertThat(r.total().partidas()).isEqualTo(3);
        assertThat(r.racha()).isEqualTo(-2);
    }

    @Test
    void sinPartidas() {
        Personales r = StatsService.calcularPersonales(List.of(), CATALOGO, 0);
        assertThat(r.total().partidas()).isZero();
        assertThat(r.total().winrate()).isNull();
        assertThat(r.racha()).isZero();
    }

    @Test
    void elLadoDelRivalVoltea() {
        Partida mia = new Partida(
            5L, 1L, "ana", "OP05-119", "OP05-062", true, true, LocalDate.now(), Partida.CASUAL, 2L, "beto", Partida.CONFIRMADA,
            null, null, null, null, null, "nota de ana", false, null, null, null
        );
        Partida suya = mia.delLadoDelRival();
        assertThat(suya.jugadorId()).isEqualTo(2L);
        assertThat(suya.lider()).isEqualTo("OP05-062");
        assertThat(suya.rivalLider()).isEqualTo("OP05-119");
        assertThat(suya.empece()).isFalse();
        assertThat(suya.gane()).isFalse();
        assertThat(suya.notas()).isNull();
    }
}
