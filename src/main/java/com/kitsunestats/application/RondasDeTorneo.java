package com.kitsunestats.application;

import com.kitsunestats.domain.model.MiDeck;
import com.kitsunestats.domain.model.MiTorneo;
import com.kitsunestats.domain.model.MiTorneo.Ronda;
import com.kitsunestats.domain.model.Partida;
import com.kitsunestats.domain.port.out.LiderRepositoryPort;
import com.kitsunestats.domain.port.out.MisTorneosRepositoryPort;
import com.kitsunestats.domain.port.out.MisTorneosRepositoryPort.PartidaDeTienda;
import com.kitsunestats.domain.port.out.PartidaRepositoryPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las rondas de un torneo de Mis torneos y su enlace con las partidas de Stats: una ronda de One Piece (con el líder
 * del deck y el del rival, ganada o perdida y con quién empezó) también es una partida de torneo, así cuenta en Mis
 * stats y en el meta. Los empates y las rondas de otros juegos solo se quedan en el torneo. En torneos de tiendas
 * Kitsune, las partidas registradas en "Registrar partida" salen como rondas (las que tienen rival se corrigen allá).
 */
@Component
@RequiredArgsConstructor
public class RondasDeTorneo {

    static final int MAXIMO = 20;
    private static final Set<String> RESULTADOS = Set.of("G", "P", "E");

    private final PartidaRepositoryPort partidas;
    private final LiderRepositoryPort lideres;
    private final MisTorneosRepositoryPort repo;

    /**
     * Guarda las rondas del torneo (ya guardado con sus datos nuevos; sus rondas son las de antes) y pone su récord.
     * deck: el deck con que lo jugó (null si ninguno).
     */
    @Transactional
    public void guardar(Long usuarioId, String username, MiTorneo torneo, MiDeck deck, List<Ronda> pedidas) {
        if (pedidas.size() > MAXIMO) {
            throw new IllegalArgumentException("Son demasiadas rondas (máximo " + MAXIMO + ")");
        }
        List<Ronda> anteriores = torneo.rondas() == null ? List.of() : torneo.rondas();
        Map<Long, Ronda> anteriorPorPartida = new HashMap<>();
        anteriores.stream().filter(r -> r.partidaId() != null).forEach(r -> anteriorPorPartida.put(r.partidaId(), r));
        String liderDeck = deck == null ? null : deck.lider();

        // Primero se valida todo; luego se tocan las partidas.
        List<Ronda> limpias = new ArrayList<>();
        int numero = 0;
        for (Ronda r : pedidas) {
            if (r == null) {
                continue;
            }
            numero++;
            Ronda anterior = r.partidaId() == null ? null : anteriorPorPartida.get(r.partidaId());
            if (anterior != null && anterior.bloqueada()) {
                limpias.add(new Ronda(numero, anterior.rivalLider(), anterior.rival(), anterior.resultado(), anterior.empece(),
                    anterior.partidaId(), true));
                continue;
            }
            if (r.resultado() == null || !RESULTADOS.contains(r.resultado())) {
                throw new IllegalArgumentException("Indica si ganaste, perdiste o empataste cada ronda");
            }
            String rivalLider = null;
            if (r.rivalLider() != null && !r.rivalLider().isBlank()) {
                rivalLider = lideres.obtener(r.rivalLider().strip().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Ese líder no existe"))
                    .codigo();
            }
            String rival = r.rival() == null || r.rival().isBlank() ? null : r.rival().strip();
            if (rival != null && rival.length() > 80) {
                throw new IllegalArgumentException("El deck del rival es muy largo (máximo 80 letras)");
            }
            limpias.add(new Ronda(numero, rivalLider, rival, r.resultado(), r.empece(), anterior == null ? null : anterior.partidaId(), false));
        }

        Set<Long> siguen = new HashSet<>();
        List<Ronda> guardadas = new ArrayList<>();
        for (Ronda r : limpias) {
            if (r.bloqueada()) {
                siguen.add(r.partidaId());
                guardadas.add(r);
                continue;
            }
            boolean cuenta = liderDeck != null && r.rivalLider() != null && !"E".equals(r.resultado()) && r.empece() != null;
            Long partidaId = r.partidaId();
            if (cuenta) {
                Partida p = new Partida(
                    partidaId, usuarioId, username, liderDeck, r.rivalLider(), r.empece(), "G".equals(r.resultado()), torneo.fecha(),
                    Partida.TORNEO, null, null, Partida.SIN_RIVAL, torneo.tiendaId(), torneo.tiendaNombre(), torneo.torneoTiendaId(),
                    torneo.nombre() == null ? torneo.tiendaNombre() : torneo.nombre(), Math.min(r.ronda(), MAXIMO), null, false, null, null, null
                );
                partidaId = partidaId == null ? partidas.crear(p).id() : partidas.actualizar(p).id();
                siguen.add(partidaId);
            } else {
                partidaId = null;
            }
            guardadas.add(new Ronda(r.ronda(), r.rivalLider(), r.rival(), r.resultado(), r.empece(), partidaId, false));
        }
        // Las que dejaron de contar (o se quitaron) ya no son partida.
        for (Ronda anterior : anteriores) {
            if (anterior.partidaId() != null && !anterior.bloqueada() && !siguen.contains(anterior.partidaId())) {
                partidas.borrar(anterior.partidaId());
            }
        }
        repo.guardarRondas(torneo.id(), guardadas);
        if (!guardadas.isEmpty()) {
            ponerRecord(torneo.id(), guardadas);
        }
    }

    /** Al borrar un torneo propio: sus partidas (las que no tienen rival) también. */
    public void borrarPartidas(MiTorneo torneo) {
        for (Ronda r : torneo.rondas() == null ? List.<Ronda>of() : torneo.rondas()) {
            if (r.partidaId() != null && !r.bloqueada()) {
                partidas.borrar(r.partidaId());
            }
        }
    }

    /**
     * Las partidas que registró en "Registrar partida" para torneos de tiendas Kitsune salen como rondas de esos
     * torneos (si esa ronda todavía no está). true si agregó alguna.
     */
    public boolean sincronizarDeTiendas(Long usuarioId, List<MiTorneo> torneos) {
        if (torneos.stream().noneMatch(MiTorneo::importado)) {
            return false;
        }
        Map<String, List<PartidaDeTienda>> porTorneo = repo.partidasDeTorneosTienda(usuarioId).stream()
            .collect(Collectors.groupingBy(p -> p.tiendaId() + ":" + p.torneoId()));
        boolean cambio = false;
        for (MiTorneo t : torneos) {
            List<PartidaDeTienda> suyas = t.importado() ? porTorneo.get(t.tiendaId() + ":" + t.torneoTiendaId()) : null;
            if (suyas == null) {
                continue;
            }
            Set<Long> ligadas = t.rondas().stream().map(Ronda::partidaId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            Set<Integer> numeros = t.rondas().stream().map(Ronda::ronda).collect(Collectors.toSet());
            List<Ronda> nuevas = new ArrayList<>(t.rondas());
            for (PartidaDeTienda p : suyas) {
                if (!ligadas.contains(p.partidaId()) && numeros.add(p.ronda())) {
                    nuevas.add(new Ronda(p.ronda(), p.rivalLider(), null, p.gane() ? "G" : "P", p.empece(), p.partidaId(), p.conRival()));
                }
            }
            if (nuevas.size() != t.rondas().size()) {
                nuevas.sort(Comparator.comparing(Ronda::ronda));
                repo.guardarRondas(t.id(), nuevas);
                ponerRecord(t.id(), nuevas);
                cambio = true;
            }
        }
        return cambio;
    }

    private void ponerRecord(Long torneoId, List<Ronda> rondas) {
        int v = (int) rondas.stream().filter(r -> "G".equals(r.resultado())).count();
        int p = (int) rondas.stream().filter(r -> "P".equals(r.resultado())).count();
        int e = (int) rondas.stream().filter(r -> "E".equals(r.resultado())).count();
        repo.actualizarRecord(torneoId, v, p, e);
    }
}
