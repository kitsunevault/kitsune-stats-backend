package com.kitsunestats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitsunestats.domain.model.Kitsune.ResultadoTorneo;
import com.kitsunestats.domain.model.Kitsune.Tienda;
import com.kitsunestats.domain.model.MiTorneo;
import com.kitsunestats.domain.model.MiTorneo.Gasto;
import com.kitsunestats.domain.model.MiTorneo.Premio;
import com.kitsunestats.domain.model.MiTorneo.Totales;
import com.kitsunestats.domain.port.out.KitsuneCuentasPort;
import com.kitsunestats.domain.port.out.MisTorneosRepositoryPort;
import com.kitsunestats.domain.port.out.TiendasJugadorRepositoryPort;
import com.kitsunestats.domain.port.out.TorneosTiendasPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Mis torneos: lo de las tiendas Kitsune llega solo, lo capturado se valida y el resumen cuadra. */
class MisTorneosServiceTest {

    private final MisTorneosRepositoryPort repo = mock(MisTorneosRepositoryPort.class);
    private final KitsuneCuentasPort cuentas = mock(KitsuneCuentasPort.class);
    private final TorneosTiendasPort tiendas = mock(TorneosTiendasPort.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-17T18:00:00Z"), ZoneOffset.UTC);
    private final TiendasJugadorRepositoryPort tiendasJugador = mock(TiendasJugadorRepositoryPort.class);
    private final MisTorneosService service = new MisTorneosService(repo, tiendasJugador, cuentas, tiendas, reloj);

    private static final Tienda MAGIC = new Tienda("magiccauldron", "Magic Cauldron");

    private static ResultadoTorneo resultado(long id, boolean cerrado, Integer lugar, Integer sobres, Integer puntos) {
        return new ResultadoTorneo("magiccauldron", "Magic Cauldron", id, "Store Championship", LocalDate.of(2026, 11, 8), "One Piece",
            cerrado, new BigDecimal("150.00"), "MXN", lugar, 24, sobres, puntos);
    }

    private static MiTorneo torneo(String moneda, BigDecimal entrada, List<Gasto> gastos, List<Premio> premios) {
        return new MiTorneo(null, LocalDate.of(2026, 11, 1), null, "Otra tienda", null, null, "One Piece", null, null, null, null, null,
            null, entrada, moneda, null, gastos, premios);
    }

    private static Premio premio(String tipo, BigDecimal valor, String estado, BigDecimal venta) {
        return new Premio(tipo, "algo", 1, valor, estado, venta, null, "MANUAL");
    }

    @Test
    void losTorneosCerradosDeLasTiendasKitsuneSeAgreganSolosConSusPremios() {
        when(cuentas.tiendas(7L)).thenReturn(List.of(MAGIC));
        when(repo.importados(7L)).thenReturn(new HashSet<>(Set.of("magiccauldron:3")));
        when(tiendas.resultadosDe(7L, MAGIC, 365)).thenReturn(List.of(
            resultado(1, true, 2, 3, 40),   // nuevo: entra
            resultado(2, false, null, null, null), // abierto: todavía no
            resultado(3, true, 5, 1, null)  // ya importado (o lo quitó): no se repite
        ));

        service.misTorneos(7L);

        ArgumentCaptor<MiTorneo> captor = ArgumentCaptor.forClass(MiTorneo.class);
        verify(repo, times(1)).crear(eq(7L), captor.capture());
        MiTorneo t = captor.getValue();
        assertThat(t.torneoTiendaId()).isEqualTo(1L);
        assertThat(t.tiendaId()).isEqualTo("magiccauldron");
        assertThat(t.lugar()).isEqualTo(2);
        assertThat(t.entrada()).isEqualByComparingTo("150");
        assertThat(t.premios()).extracting(Premio::tipo, Premio::descripcion, Premio::origen).containsExactly(
            org.assertj.core.groups.Tuple.tuple("SOBRES", "3 sobres", "TIENDA"),
            org.assertj.core.groups.Tuple.tuple("CREDITO", "40 puntos de la tienda", "TIENDA"));
    }

    @Test
    void noPreguntaALasTiendasEnCadaVisita() {
        when(cuentas.tiendas(7L)).thenReturn(List.of(MAGIC));
        when(repo.importados(7L)).thenReturn(new HashSet<>());
        when(tiendas.resultadosDe(anyLong(), any(), anyInt())).thenReturn(List.of());

        service.misTorneos(7L);
        service.misTorneos(7L);

        verify(tiendas, times(1)).resultadosDe(anyLong(), any(), anyInt());
    }

    @Test
    void validaLoQueCaptura() {
        MiTorneo sinTienda = new MiTorneo(null, LocalDate.of(2026, 11, 1), null, " ", null, null, null, null, null, null, null, null, null,
            null, "MXN", null, List.of(), List.of());
        assertThatThrownBy(() -> service.crear(7L, sinTienda)).hasMessageContaining("tienda");

        MiTorneo lugarImposible = new MiTorneo(null, LocalDate.of(2026, 11, 1), null, "Otra", null, null, null, null, 9, 8, null, null,
            null, null, "MXN", null, List.of(), List.of());
        assertThatThrownBy(() -> service.crear(7L, lugarImposible)).hasMessageContaining("lugar");

        MiTorneo premioSinTipo = torneo("MXN", BigDecimal.TEN, List.of(), List.of(new Premio("OTRO", "algo", 1, null, null, null, null, null)));
        assertThatThrownBy(() -> service.crear(7L, premioSinTipo)).hasMessageContaining("tipo");

        MiTorneo entradaNegativa = torneo("MXN", new BigDecimal("-1"), List.of(), List.of());
        assertThatThrownBy(() -> service.crear(7L, entradaNegativa)).hasMessageContaining("entrada");

        verify(repo, never()).crear(anyLong(), any());
    }

    @Test
    void loCapturadoSeGuardaLimpio() {
        when(repo.crear(eq(7L), any())).thenReturn(1L);
        when(repo.obtener(7L, 1L)).thenReturn(Optional.of(torneo("MXN", BigDecimal.ZERO, List.of(), List.of())));
        MiTorneo nuevo = torneo("EUR", null,
            List.of(new Gasto(" Gasolina ", new BigDecimal("300")), new Gasto("", null)),
            List.of(
                // El efectivo no se "vende": se queda como cobrado.
                new Premio("EFECTIVO", "1er lugar", null, new BigDecimal("500"), "VENDIDA", new BigDecimal("1"), null, "TIENDA"),
                // Una carta que no se vendió no guarda precio de venta.
                new Premio("CARTA", "Luffy alt art", 1, new BigDecimal("800"), "CONSERVO", new BigDecimal("900"), LocalDate.of(2026, 11, 2),
                    null)));

        service.crear(7L, nuevo);

        ArgumentCaptor<MiTorneo> captor = ArgumentCaptor.forClass(MiTorneo.class);
        verify(repo).crear(eq(7L), captor.capture());
        MiTorneo t = captor.getValue();
        assertThat(t.moneda()).isEqualTo("MXN");
        assertThat(t.entrada()).isEqualByComparingTo("0");
        assertThat(t.gastos()).singleElement().satisfies(g -> assertThat(g.concepto()).isEqualTo("Gasolina"));
        assertThat(t.premios().get(0).estado()).isEqualTo("CONSERVO");
        assertThat(t.premios().get(0).origen()).isEqualTo("MANUAL");
        assertThat(t.premios().get(1).precioVenta()).isNull();
        assertThat(t.premios().get(1).vendidaEn()).isNull();
    }

    @Test
    void elResumenSeparaLoCobradoDeLoQueConserva() {
        MiTorneo uno = torneo("MXN", new BigDecimal("200"), List.of(new Gasto("Comida", new BigDecimal("150"))), List.of(
            premio("EFECTIVO", new BigDecimal("500"), "CONSERVO", null),
            premio("CARTA", new BigDecimal("1000"), "VENDIDA", new BigDecimal("900")),
            premio("CARTA", new BigDecimal("300"), "CONSERVO", null),
            premio("SOBRES", null, "CONSERVO", null)));
        MiTorneo dos = torneo("MXN", new BigDecimal("150"), List.of(), List.of(premio("PRODUCTO", new BigDecimal("50"), "CAMBIADA", null)));

        Totales t = MisTorneosService.totales("MXN", List.of(uno, dos));

        assertThat(t.torneos()).isEqualTo(2);
        assertThat(t.gastado()).isEqualByComparingTo("500");
        assertThat(t.cobrado()).isEqualByComparingTo("1400");
        assertThat(t.conservado()).isEqualByComparingTo("350");
        assertThat(t.ganado()).isEqualByComparingTo("1750");
        assertThat(t.neto()).isEqualByComparingTo("1250");
        assertThat(t.sinValor()).isEqualTo(1);
    }

    @Test
    void unaTiendaKitsuneDesactivadaYaNoImportaNiSaleActiva() {
        Tienda otra = new Tienda("otra", "Otra Tienda");
        when(cuentas.tiendas(7L)).thenReturn(List.of(MAGIC, otra));
        when(repo.importados(7L)).thenReturn(new HashSet<>());
        when(tiendasJugador.listar(7L)).thenReturn(List.of(
            new TiendasJugadorRepositoryPort.Fila(1L, "Magic Cauldron", null, "magiccauldron", false),
            new TiendasJugadorRepositoryPort.Fila(2L, "Card Shop Centro", "CDMX", null, true)));
        when(tiendas.resultadosDe(anyLong(), any(), anyInt())).thenReturn(List.of());

        var r = service.misTorneos(7L);

        verify(tiendas, never()).resultadosDe(eq(7L), eq(MAGIC), anyInt());
        verify(tiendas).resultadosDe(eq(7L), eq(otra), anyInt());
        assertThat(r.tiendas()).extracting(t -> t.nombre() + "|" + t.kitsune() + "|" + t.activa())
            .containsExactly("Magic Cauldron|true|false", "Otra Tienda|true|true", "Card Shop Centro|false|true");
    }

    @Test
    void unaTiendaNuevaAlCapturarSeAgregaASusTiendas() {
        when(cuentas.tiendas(7L)).thenReturn(List.of(MAGIC));
        when(tiendasJugador.listar(7L)).thenReturn(List.of());
        when(repo.crear(eq(7L), any())).thenReturn(1L);
        when(repo.obtener(7L, 1L)).thenReturn(Optional.of(torneo("MXN", BigDecimal.ZERO, List.of(), List.of())));

        service.crear(7L, torneo("MXN", BigDecimal.TEN, List.of(), List.of()));
        verify(tiendasJugador).crear(7L, "Otra tienda", null);

        // Una de Kitsune Vaults (aunque cambie mayúsculas) no se duplica como propia.
        MiTorneo enMagic = new MiTorneo(null, LocalDate.of(2026, 11, 1), null, "magic cauldron", null, null, null, null, null, null, null,
            null, null, BigDecimal.ONE, "MXN", null, List.of(), List.of());
        service.crear(7L, enMagic);
        verify(tiendasJugador, times(1)).crear(anyLong(), any(), any());
    }

    @Test
    void soloSePuedeDesactivarUnaTiendaKitsuneQueEsSuya() {
        when(cuentas.tiendas(7L)).thenReturn(List.of(MAGIC));
        assertThatThrownBy(() -> service.estadoTiendaKitsune(7L, "ajena", false)).isInstanceOf(NoEncontradoException.class);
        service.estadoTiendaKitsune(7L, "magiccauldron", false);
        verify(tiendasJugador).estadoKitsune(7L, "magiccauldron", "Magic Cauldron", false);
    }

    @Test
    void quitarUnoDeTiendaSoloLoOcultaParaQueNoRegrese() {
        MiTorneo deTienda = MisTorneosService.desdeTienda(resultado(1, true, 2, null, null));
        MiTorneo guardado = new MiTorneo(5L, deTienda.fecha(), deTienda.nombre(), deTienda.tiendaNombre(), deTienda.tiendaId(),
            deTienda.torneoTiendaId(), null, null, null, null, null, null, null, BigDecimal.ZERO, "MXN", null, List.of(), List.of());
        when(repo.obtener(7L, 5L)).thenReturn(Optional.of(guardado));

        service.borrar(7L, 5L);

        verify(repo).ocultar(7L, 5L);
        verify(repo, never()).borrar(anyLong(), anyLong());
    }
}
