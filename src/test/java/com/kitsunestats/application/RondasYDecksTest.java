package com.kitsunestats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitsunestats.domain.model.Lider;
import com.kitsunestats.domain.model.MiDeck;
import com.kitsunestats.domain.model.MiTorneo;
import com.kitsunestats.domain.model.MiTorneo.Premio;
import com.kitsunestats.domain.model.MiTorneo.Ronda;
import com.kitsunestats.domain.model.Partida;
import com.kitsunestats.domain.port.out.LiderRepositoryPort;
import com.kitsunestats.domain.port.out.MisTorneosRepositoryPort;
import com.kitsunestats.domain.port.out.MisTorneosRepositoryPort.PartidaDeTienda;
import com.kitsunestats.domain.port.out.PartidaRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Rondas de Mis torneos: las de One Piece son partidas de torneo; el récord sale de ellas; cada deck dice cómo le va. */
class RondasYDecksTest {

    private final PartidaRepositoryPort partidas = mock(PartidaRepositoryPort.class);
    private final LiderRepositoryPort lideres = mock(LiderRepositoryPort.class);
    private final MisTorneosRepositoryPort repo = mock(MisTorneosRepositoryPort.class);
    private final RondasDeTorneo rondas = new RondasDeTorneo(partidas, lideres, repo);

    private static final MiDeck LUFFY = new MiDeck(3L, "Luffy RG", "One Piece", "OP05-060", null, false, 0, 0, 0, 0, BigDecimal.ZERO,
        BigDecimal.ZERO, null);

    @BeforeEach
    void lideres() {
        for (String c : List.of("OP05-060", "OP07-001", "OP09-061")) {
            when(lideres.obtener(c)).thenReturn(Optional.of(new Lider(c, c, List.of(), "OP", null, true)));
        }
        when(partidas.crear(any())).thenAnswer(i -> conId(i.getArgument(0), 90L));
        when(partidas.actualizar(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Partida conId(Partida p, Long id) {
        return new Partida(id, p.jugadorId(), p.jugadorUsername(), p.lider(), p.rivalLider(), p.empece(), p.gane(), p.jugadaEn(), p.tipo(),
            p.rivalId(), p.rivalUsername(), p.confirmacion(), p.tiendaId(), p.tiendaNombre(), p.torneoId(), p.torneoNombre(), p.ronda(),
            p.notas(), p.oculta(), null, null, null);
    }

    private static MiTorneo torneo(String tiendaId, Long torneoTienda, List<Ronda> rondas) {
        return new MiTorneo(10L, LocalDate.of(2026, 11, 15), "Regional", "Card Shop", tiendaId, torneoTienda, "One Piece", null, null, null,
            null, null, null, BigDecimal.valueOf(300), "MXN", null, List.of(), List.of(), 3L, null, rondas);
    }

    @Test
    void lasRondasDeOnePieceGanadasOPerdidasSonPartidasDeTorneo() {
        rondas.guardar(7L, "kody", torneo(null, null, List.of()), LUFFY, List.of(
            new Ronda(null, "op07-001", null, "G", true, null, false),
            new Ronda(null, "OP09-061", null, "E", false, null, false),   // empate: solo en el torneo
            new Ronda(null, "OP09-061", "Enel azul", "P", null, null, false) // sin quién empezó: solo en el torneo
        ));

        ArgumentCaptor<Partida> captor = ArgumentCaptor.forClass(Partida.class);
        verify(partidas, times(1)).crear(captor.capture());
        Partida p = captor.getValue();
        assertThat(p.tipo()).isEqualTo(Partida.TORNEO);
        assertThat(p.lider()).isEqualTo("OP05-060");
        assertThat(p.rivalLider()).isEqualTo("OP07-001");
        assertThat(p.gane()).isTrue();
        assertThat(p.tiendaId()).isNull();
        assertThat(p.torneoId()).isNull();
        assertThat(p.ronda()).isEqualTo(1);
        assertThat(p.torneoNombre()).isEqualTo("Regional");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ronda>> guardadas = ArgumentCaptor.forClass(List.class);
        verify(repo).guardarRondas(eq(10L), guardadas.capture());
        assertThat(guardadas.getValue()).extracting(Ronda::ronda, Ronda::partidaId).containsExactly(
            org.assertj.core.groups.Tuple.tuple(1, 90L), org.assertj.core.groups.Tuple.tuple(2, null), org.assertj.core.groups.Tuple.tuple(3, null));
        verify(repo).actualizarRecord(10L, 1, 1, 1);
    }

    @Test
    void enTorneosDeTiendaKitsuneLaPartidaVaLigadaAlTorneoDeLaTienda() {
        rondas.guardar(7L, "kody", torneo("magiccauldron", 23L, List.of()), LUFFY, List.of(new Ronda(null, "OP07-001", null, "P", false, null, false)));

        ArgumentCaptor<Partida> captor = ArgumentCaptor.forClass(Partida.class);
        verify(partidas).crear(captor.capture());
        assertThat(captor.getValue().tiendaId()).isEqualTo("magiccauldron");
        assertThat(captor.getValue().torneoId()).isEqualTo(23L);
    }

    @Test
    void sinLiderEnElDeckNingunaRondaEsPartida() {
        MiDeck sinLider = new MiDeck(4L, "Charizard", "Pokémon", null, null, false, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
        rondas.guardar(7L, "kody", torneo(null, null, List.of()), sinLider, List.of(new Ronda(null, null, "Lugia", "G", true, null, false)));
        verify(partidas, never()).crear(any());
        verify(repo).actualizarRecord(10L, 1, 0, 0);
    }

    @Test
    void cambiarOQuitarRondasCorrigeOBorraSusPartidasPeroNoLasQueTienenRival() {
        List<Ronda> antes = List.of(
            new Ronda(1, "OP07-001", null, "G", true, 50L, false),
            new Ronda(2, "OP09-061", null, "P", false, 51L, false),
            new Ronda(3, "OP09-061", null, "G", true, 52L, true));  // con rival: bloqueada
        rondas.guardar(7L, "kody", torneo(null, null, antes), LUFFY, List.of(
            new Ronda(1, "OP07-001", null, "P", true, 50L, false),   // corregida
            new Ronda(3, "OP05-060", null, "P", false, 52L, true)    // bloqueada: se queda como estaba
        ));

        ArgumentCaptor<Partida> captor = ArgumentCaptor.forClass(Partida.class);
        verify(partidas).actualizar(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(50L);
        assertThat(captor.getValue().gane()).isFalse();
        verify(partidas).borrar(51L);
        verify(partidas, never()).borrar(52L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ronda>> guardadas = ArgumentCaptor.forClass(List.class);
        verify(repo).guardarRondas(eq(10L), guardadas.capture());
        assertThat(guardadas.getValue().get(1).resultado()).isEqualTo("G");
        assertThat(guardadas.getValue().get(1).bloqueada()).isTrue();
    }

    @Test
    void validaLasRondas() {
        assertThatThrownBy(() -> rondas.guardar(7L, "kody", torneo(null, null, List.of()), LUFFY,
            List.of(new Ronda(null, null, null, "X", null, null, false)))).hasMessageContaining("ganaste");
        when(lideres.obtener("NO-EXISTE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> rondas.guardar(7L, "kody", torneo(null, null, List.of()), LUFFY,
            List.of(new Ronda(null, "no-existe", null, "G", true, null, false)))).hasMessageContaining("líder");
        verify(partidas, never()).crear(any());
    }

    @Test
    void lasPartidasDeRegistrarPartidaSalenComoRondasDelTorneoDeLaTienda() {
        MiTorneo importado = torneo("magiccauldron", 23L, new ArrayList<>(List.of(new Ronda(1, "OP07-001", null, "G", true, 60L, false))));
        when(repo.partidasDeTorneosTienda(7L)).thenReturn(List.of(
            new PartidaDeTienda(60L, "magiccauldron", 23L, 1, "OP07-001", true, true, false),   // ya ligada
            new PartidaDeTienda(61L, "magiccauldron", 23L, 2, "OP09-061", false, false, true),  // con rival: bloqueada
            new PartidaDeTienda(62L, "otra", 23L, 1, "OP09-061", false, true, false)));         // de otra tienda

        assertThat(rondas.sincronizarDeTiendas(7L, List.of(importado))).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ronda>> guardadas = ArgumentCaptor.forClass(List.class);
        verify(repo).guardarRondas(eq(10L), guardadas.capture());
        assertThat(guardadas.getValue()).extracting(Ronda::ronda, Ronda::partidaId, Ronda::resultado, Ronda::bloqueada).containsExactly(
            org.assertj.core.groups.Tuple.tuple(1, 60L, "G", false), org.assertj.core.groups.Tuple.tuple(2, 61L, "P", true));
        verify(repo).actualizarRecord(10L, 1, 1, 0);
    }

    @Test
    void cadaDeckDiceComoLeVa() {
        MiTorneo conRondas = new MiTorneo(1L, LocalDate.of(2026, 11, 1), null, "A", null, null, "One Piece", null, null, null, null, null,
            null, new BigDecimal("200"), "MXN", null, List.of(), List.of(new Premio("EFECTIVO", "1er", 1, new BigDecimal("500"), "CONSERVO",
            null, null, "MANUAL")), 3L, "Luffy RG", List.of(new Ronda(1, null, null, "G", true, null, false),
            new Ronda(2, null, null, "P", true, null, false), new Ronda(3, null, null, "E", null, null, false)));
        MiTorneo sinRondas = new MiTorneo(2L, LocalDate.of(2026, 10, 1), null, "B", null, null, "One Piece", null, null, null, 4, 1, 0,
            new BigDecimal("100"), "MXN", null, List.of(), List.of(), 3L, "Luffy RG", List.of());
        MiTorneo otroDeck = new MiTorneo(3L, LocalDate.of(2026, 10, 1), null, "B", null, null, "One Piece", null, null, null, 9, 0, 0,
            BigDecimal.ONE, "MXN", null, List.of(), List.of(), null, null, List.of());

        List<MiDeck> conStats = MisDecksService.conStats(List.of(LUFFY), List.of(conRondas, sinRondas, otroDeck));

        MiDeck d = conStats.get(0);
        assertThat(d.torneos()).isEqualTo(2);
        assertThat(d.victorias()).isEqualTo(5);
        assertThat(d.derrotas()).isEqualTo(2);
        assertThat(d.empates()).isEqualTo(1);
        assertThat(d.gastado()).isEqualByComparingTo("300");
        assertThat(d.ganado()).isEqualByComparingTo("500");
        assertThat(d.moneda()).isEqualTo("MXN");
    }

    @Test
    void elDeckSeValida() {
        MisDecksService decks = new MisDecksService(mock(com.kitsunestats.domain.port.out.MisDecksRepositoryPort.class), lideres);
        assertThatThrownBy(() -> decks.validar(new MiDeck(null, " ", null, null, null, false, 0, 0, 0, 0, null, null, null)))
            .hasMessageContaining("nombre");
        when(lideres.obtener("X")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> decks.validar(new MiDeck(null, "Mi deck", null, "x", null, false, 0, 0, 0, 0, null, null, null)))
            .hasMessageContaining("líder");
        MiDeck ok = decks.validar(new MiDeck(null, " Luffy RG ", null, "op05-060", " ", false, 0, 0, 0, 0, null, null, null));
        assertThat(ok.nombre()).isEqualTo("Luffy RG");
        assertThat(ok.lider()).isEqualTo("OP05-060");
        assertThat(ok.juego()).isEqualTo("One Piece");
        assertThat(ok.lista()).isNull();
    }
}
