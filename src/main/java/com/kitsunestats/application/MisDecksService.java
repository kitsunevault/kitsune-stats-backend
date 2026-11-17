package com.kitsunestats.application;

import com.kitsunestats.domain.model.MiDeck;
import com.kitsunestats.domain.model.MiTorneo;
import com.kitsunestats.domain.model.MiTorneo.Totales;
import com.kitsunestats.domain.port.out.LiderRepositoryPort;
import com.kitsunestats.domain.port.out.MisDecksRepositoryPort;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Mis decks: los decks que juega cada jugador. Con el líder (One Piece) sus rondas de torneo cuentan en Mis stats y en
 * el meta. Cada deck dice cómo le va con él en Mis torneos (torneos, récord, gastado y ganado).
 */
@Service
@RequiredArgsConstructor
public class MisDecksService {

    static final int MAXIMO = 100;

    private final MisDecksRepositoryPort decks;
    private final LiderRepositoryPort lideres;

    public List<MiDeck> listar(Long usuarioId) {
        return decks.listar(usuarioId);
    }

    public Optional<MiDeck> obtener(Long usuarioId, Long id) {
        return id == null ? Optional.empty() : decks.obtener(usuarioId, id);
    }

    public void crear(Long usuarioId, MiDeck nuevo) {
        MiDeck limpio = validar(nuevo);
        if (decks.listar(usuarioId).size() >= MAXIMO) {
            throw new IllegalArgumentException("Son demasiados decks (máximo " + MAXIMO + ")");
        }
        try {
            decks.crear(usuarioId, limpio);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Ya tienes un deck con ese nombre");
        }
    }

    public void actualizar(Long usuarioId, Long id, MiDeck cambios) {
        MiDeck limpio = validar(cambios);
        try {
            if (!decks.actualizar(usuarioId, id, limpio)) {
                throw new NoEncontradoException("Ese deck no existe");
            }
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Ya tienes un deck con ese nombre");
        }
    }

    /** Sus torneos con ese deck se quedan con su nombre. */
    public void borrar(Long usuarioId, Long id) {
        if (!decks.borrar(usuarioId, id)) {
            throw new NoEncontradoException("Ese deck no existe");
        }
    }

    MiDeck validar(MiDeck d) {
        if (d == null || d.nombre() == null || d.nombre().strip().length() < 2) {
            throw new IllegalArgumentException("Escribe el nombre del deck");
        }
        String nombre = d.nombre().strip();
        if (nombre.length() > 80) {
            throw new IllegalArgumentException("El nombre del deck es muy largo (máximo 80 letras)");
        }
        String juego = d.juego() == null || d.juego().isBlank() ? null : d.juego().strip();
        if (juego != null && juego.length() > 60) {
            throw new IllegalArgumentException("El nombre del juego es muy largo (máximo 60 letras)");
        }
        String lider = null;
        if (d.lider() != null && !d.lider().isBlank()) {
            lider = lideres.obtener(d.lider().strip().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Ese líder no existe"))
                .codigo();
            if (juego == null) {
                juego = "One Piece";
            }
        }
        String lista = d.lista() == null || d.lista().isBlank() ? null : d.lista().strip();
        if (lista != null && lista.length() > 4000) {
            throw new IllegalArgumentException("La lista es muy larga (máximo 4000 letras)");
        }
        return new MiDeck(null, nombre, juego, lider, lista, d.retirado(), 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    /**
     * Cómo le va con cada deck: torneos, récord (de las rondas; si un torneo no tiene rondas, el récord que tenía
     * anotado), y lo gastado y ganado en la moneda en que más jugó con él.
     */
    static List<MiDeck> conStats(List<MiDeck> decks, List<MiTorneo> torneos) {
        Map<Long, List<MiTorneo>> porDeck = torneos.stream().filter(t -> t.deckId() != null)
            .collect(Collectors.groupingBy(MiTorneo::deckId));
        return decks.stream().map(d -> {
            List<MiTorneo> suyos = porDeck.getOrDefault(d.id(), List.of());
            int v = 0;
            int p = 0;
            int e = 0;
            for (MiTorneo t : suyos) {
                if (t.rondas() != null && !t.rondas().isEmpty()) {
                    v += (int) t.rondas().stream().filter(r -> "G".equals(r.resultado())).count();
                    p += (int) t.rondas().stream().filter(r -> "P".equals(r.resultado())).count();
                    e += (int) t.rondas().stream().filter(r -> "E".equals(r.resultado())).count();
                } else {
                    v += Objects.requireNonNullElse(t.victorias(), 0);
                    p += Objects.requireNonNullElse(t.derrotas(), 0);
                    e += Objects.requireNonNullElse(t.empates(), 0);
                }
            }
            String moneda = suyos.stream().collect(Collectors.groupingBy(MiTorneo::moneda, Collectors.counting())).entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            List<MiTorneo> enMoneda = moneda == null ? List.of() : suyos.stream().filter(t -> moneda.equals(t.moneda())).toList();
            Totales tot = MisTorneosService.totales(moneda, enMoneda);
            return d.conStats(suyos.size(), v, p, e, tot.gastado(), tot.ganado(), moneda);
        }).sorted(Comparator.comparing(MiDeck::retirado).thenComparing(MiDeck::torneos, Comparator.reverseOrder())).toList();
    }
}
