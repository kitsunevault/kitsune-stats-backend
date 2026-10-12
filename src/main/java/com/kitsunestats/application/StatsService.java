package com.kitsunestats.application;

import com.kitsunestats.domain.model.Filtro;
import com.kitsunestats.domain.model.Lider;
import com.kitsunestats.domain.model.Partida;
import com.kitsunestats.domain.model.Stats.Conteo;
import com.kitsunestats.domain.model.Stats.DetalleLider;
import com.kitsunestats.domain.model.Stats.Matchup;
import com.kitsunestats.domain.model.Stats.Meta;
import com.kitsunestats.domain.model.Stats.Personales;
import com.kitsunestats.domain.model.Stats.StatsLider;
import com.kitsunestats.domain.port.out.LiderRepositoryPort;
import com.kitsunestats.domain.port.out.PartidaRepositoryPort;
import com.kitsunestats.domain.port.out.StatsRepositoryPort;
import com.kitsunestats.domain.port.out.StatsRepositoryPort.FilaLider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Estadísticas. Globales: cada partida cuenta sus dos lados (el del que la registró y el de su rival),
 * así una partida "Luffy le gana a Law" es una victoria de Luffy contra Law y una derrota de Law contra
 * Luffy, una sola vez. Los espejos (mismo líder de los dos lados) cuentan para la popularidad pero no
 * para el winrate ni los matchups. Personales: todas las partidas del jugador, desde su lado.
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    /** Por debajo de estas partidas un matchup se marca como "muestra chica". */
    public static final int MUESTRA_MINIMA = 10;
    /** Tope de partidas propias que se suman para las stats personales. */
    static final int MAXIMO_PERSONALES = 5000;

    private final StatsRepositoryPort stats;
    private final PartidaRepositoryPort partidas;
    private final LiderRepositoryPort lideres;

    public Meta meta(Filtro filtro) {
        int total = stats.totalPartidas(filtro);
        Map<String, Lider> porCodigo = catalogo();
        List<StatsLider> lista = stats.porLider(filtro).stream()
            .filter(f -> porCodigo.containsKey(f.codigo()))
            .map(f -> statsLider(f, porCodigo.get(f.codigo()), total))
            .sorted(Comparator.comparing((StatsLider s) -> s.popularidad()).reversed())
            .toList();
        return new Meta(total, lista, stats.matchups(filtro), MUESTRA_MINIMA);
    }

    public DetalleLider detalleLider(String codigo, Filtro filtro) {
        Lider lider = lideres.obtener(codigo == null ? "" : codigo.trim().toUpperCase())
            .orElseThrow(() -> new NoEncontradoException("No existe el líder " + codigo));
        int total = stats.totalPartidas(filtro);
        StatsLider resumen = stats.porLider(filtro).stream()
            .filter(f -> f.codigo().equals(lider.codigo()))
            .findFirst()
            .map(f -> statsLider(f, lider, total))
            .orElse(new StatsLider(lider, Conteo.VACIO, BigDecimal.ZERO, Conteo.VACIO, Conteo.VACIO));
        // Mejores primero; con pocas partidas al final, para no presumir con 1 de 1.
        List<Matchup> suyos = stats.matchups(filtro).stream()
            .filter(m -> m.lider().equals(lider.codigo()))
            .sorted(Comparator
                .comparing((Matchup m) -> m.conteo().partidas() < MUESTRA_MINIMA)
                .thenComparing(m -> m.conteo().winrate(), Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        return new DetalleLider(resumen, suyos, stats.tendencia(lider.codigo(), filtro), MUESTRA_MINIMA);
    }

    public Personales personales(Long usuarioId, Filtro filtro) {
        List<Partida> mias = partidas.deJugador(usuarioId, filtro, MAXIMO_PERSONALES, 0);
        return calcularPersonales(mias, catalogo(), partidas.porConfirmar(usuarioId).size());
    }

    /**
     * Las partidas ya vienen desde el lado del jugador y de la más reciente a la más vieja. Las rechazadas
     * por el rival no cuentan (están en disputa). En lo personal los espejos sí cuentan: son partidas reales.
     */
    static Personales calcularPersonales(List<Partida> mias, Map<String, Lider> catalogo, int porConfirmar) {
        List<Partida> cuentan = mias.stream().filter(p -> !Partida.RECHAZADA.equals(p.confirmacion())).toList();
        Conteo total = conteo(cuentan);
        Conteo primero = conteo(cuentan.stream().filter(Partida::empece).toList());
        Conteo segundo = conteo(cuentan.stream().filter(p -> !p.empece()).toList());

        int racha = 0;
        if (!cuentan.isEmpty()) {
            boolean gane = cuentan.get(0).gane();
            for (Partida p : cuentan) {
                if (p.gane() != gane) {
                    break;
                }
                racha += gane ? 1 : -1;
            }
        }

        Map<String, List<Partida>> porLider = cuentan.stream()
            .collect(Collectors.groupingBy(Partida::lider, LinkedHashMap::new, Collectors.toList()));
        List<StatsLider> lideres = new ArrayList<>();
        porLider.forEach((codigo, lista) -> {
            Lider l = catalogo.get(codigo);
            if (l == null) {
                return;
            }
            lideres.add(new StatsLider(
                l, conteo(lista), porcentaje(lista.size(), cuentan.size()),
                conteo(lista.stream().filter(Partida::empece).toList()), conteo(lista.stream().filter(p -> !p.empece()).toList())
            ));
        });
        lideres.sort(Comparator.comparing((StatsLider s) -> s.total().partidas()).reversed());

        Map<String, List<Partida>> porMatchup = cuentan.stream()
            .collect(Collectors.groupingBy(p -> p.lider() + "|" + p.rivalLider(), LinkedHashMap::new, Collectors.toList()));
        List<Matchup> matchups = porMatchup.entrySet().stream()
            .map(e -> {
                String[] par = e.getKey().split("\\|");
                return new Matchup(par[0], par[1], conteo(e.getValue()));
            })
            .sorted(Comparator.comparing((Matchup m) -> m.conteo().partidas()).reversed())
            .toList();
        return new Personales(total, primero, segundo, racha, lideres, matchups, porConfirmar);
    }

    private static Conteo conteo(List<Partida> lista) {
        return new Conteo(lista.size(), (int) lista.stream().filter(Partida::gane).count());
    }

    private static StatsLider statsLider(FilaLider f, Lider lider, int totalPartidas) {
        return new StatsLider(
            lider,
            new Conteo(f.partidas(), f.victorias()),
            // Cada partida tiene dos lados: la popularidad es la parte de los lados jugados.
            porcentaje(f.lados(), totalPartidas * 2),
            new Conteo(f.primeroPartidas(), f.primeroVictorias()),
            new Conteo(f.segundoPartidas(), f.segundoVictorias())
        );
    }

    private static BigDecimal porcentaje(int parte, int total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(parte * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private Map<String, Lider> catalogo() {
        return lideres.listar(false).stream().collect(Collectors.toMap(Lider::codigo, Function.identity()));
    }
}
