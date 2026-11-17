package com.kitsunestats.application;

import com.kitsunestats.domain.model.Kitsune.ResultadoTorneo;
import com.kitsunestats.domain.model.Kitsune.Tienda;
import com.kitsunestats.domain.model.MiDeck;
import com.kitsunestats.domain.model.MiTorneo;
import com.kitsunestats.domain.model.MiTorneo.Gasto;
import com.kitsunestats.domain.model.MiTorneo.MisTorneos;
import com.kitsunestats.domain.model.MiTorneo.Premio;
import com.kitsunestats.domain.model.MiTorneo.Resumen;
import com.kitsunestats.domain.model.MiTorneo.TiendaJugador;
import com.kitsunestats.domain.model.MiTorneo.Totales;
import com.kitsunestats.domain.port.out.KitsuneCuentasPort;
import com.kitsunestats.domain.port.out.MisTorneosRepositoryPort;
import com.kitsunestats.domain.port.out.TiendasJugadorRepositoryPort;
import com.kitsunestats.domain.port.out.TorneosTiendasPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Mis torneos: lo que cada jugador gasta (entradas y otros gastos) y gana (premios) en torneos, para saber si le
 * conviene. Los torneos de tiendas de Kitsune Vaults donde jugó se agregan solos (con su lugar, la entrada del
 * calendario y los sobres o puntos que ganó); los de otras tiendas los captura él. Es privado: cada quien ve lo suyo.
 *
 * Lo ganado = lo cobrado (efectivo, crédito y lo que vendió) + lo que vale lo que conserva o cambió (valor
 * aproximado que anota él).
 */
@Service
public class MisTorneosService {

    private static final Logger log = LoggerFactory.getLogger(MisTorneosService.class);

    static final Set<String> TIPOS = Set.of("CARTA", "SOBRES", "PRODUCTO", "CREDITO", "EFECTIVO");
    static final Set<String> ESTADOS = Set.of("CONSERVO", "VENDIDA", "CAMBIADA");
    static final Set<String> MONEDAS = Set.of("MXN", "USD");
    /** Hasta dónde atrás se buscan torneos de las tiendas Kitsune. */
    static final int DIAS_IMPORTAR = 365;
    /** Cada cuánto se vuelve a preguntar a las tiendas (no en cada vez que abre la página). */
    static final Duration CADA = Duration.ofMinutes(5);

    private final MisTorneosRepositoryPort repo;
    private final TiendasJugadorRepositoryPort tiendasJugador;
    private final KitsuneCuentasPort cuentas;
    private final TorneosTiendasPort torneos;
    private final MisDecksService decks;
    private final RondasDeTorneo rondas;
    private final Clock reloj;
    private final Map<Long, Instant> ultimaImportacion = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public MisTorneosService(
        MisTorneosRepositoryPort repo, TiendasJugadorRepositoryPort tiendasJugador, KitsuneCuentasPort cuentas, TorneosTiendasPort torneos,
        MisDecksService decks, RondasDeTorneo rondas
    ) {
        this(repo, tiendasJugador, cuentas, torneos, decks, rondas, Clock.systemDefaultZone());
    }

    MisTorneosService(
        MisTorneosRepositoryPort repo, TiendasJugadorRepositoryPort tiendasJugador, KitsuneCuentasPort cuentas, TorneosTiendasPort torneos,
        MisDecksService decks, RondasDeTorneo rondas, Clock reloj
    ) {
        this.repo = repo;
        this.tiendasJugador = tiendasJugador;
        this.cuentas = cuentas;
        this.torneos = torneos;
        this.decks = decks;
        this.rondas = rondas;
        this.reloj = reloj;
    }

    public MisTorneos misTorneos(Long usuarioId) {
        List<Tienda> tiendasKitsune = importar(usuarioId);
        List<MiTorneo> lista = repo.listar(usuarioId);
        if (rondas.sincronizarDeTiendas(usuarioId, lista)) {
            lista = repo.listar(usuarioId);
        }
        return new MisTorneos(lista, resumen(lista), tiendas(usuarioId, tiendasKitsune), MisDecksService.conStats(decks.listar(usuarioId), lista));
    }

    /** Se crea sin rondas: cómo le fue se registra después, al abrirlo. */
    public MiTorneo crear(Long usuarioId, MiTorneo nuevo) {
        MiTorneo limpio = validar(usuarioId, nuevo, null);
        Long id = repo.crear(usuarioId, limpio);
        recordarTienda(usuarioId, limpio.tiendaNombre());
        return repo.obtener(usuarioId, id).orElseThrow();
    }

    /** rondas null = no las cambia (las deja como estaban). */
    public MiTorneo actualizar(Long usuarioId, String username, Long id, MiTorneo cambios) {
        MiTorneo actual = repo.obtener(usuarioId, id).orElseThrow(() -> new NoEncontradoException("Ese torneo no existe"));
        MiTorneo limpio = validar(usuarioId, cambios, actual);
        repo.actualizar(usuarioId, id, limpio);
        if (cambios.rondas() != null) {
            MiDeck deck = decks.obtener(usuarioId, limpio.deckId()).orElse(null);
            MiTorneo guardado = new MiTorneo(id, limpio.fecha(), limpio.nombre(), limpio.tiendaNombre(), limpio.tiendaId(),
                limpio.torneoTiendaId(), limpio.juego(), limpio.mazo(), limpio.lugar(), limpio.jugadores(), limpio.victorias(),
                limpio.derrotas(), limpio.empates(), limpio.entrada(), limpio.moneda(), limpio.notas(), limpio.gastos(), limpio.premios(),
                limpio.deckId(), null, actual.rondas());
            rondas.guardar(usuarioId, username, guardado, deck, cambios.rondas());
        }
        if (!actual.importado()) {
            recordarTienda(usuarioId, limpio.tiendaNombre());
        }
        return repo.obtener(usuarioId, id).orElseThrow();
    }

    // ---------- Mis decks (todas responden con la lista y cómo le va con cada uno) ----------

    public List<MiDeck> misDecks(Long usuarioId) {
        return MisDecksService.conStats(decks.listar(usuarioId), repo.listar(usuarioId));
    }

    public List<MiDeck> crearDeck(Long usuarioId, MiDeck deck) {
        decks.crear(usuarioId, deck);
        return misDecks(usuarioId);
    }

    public List<MiDeck> actualizarDeck(Long usuarioId, Long id, MiDeck deck) {
        decks.actualizar(usuarioId, id, deck);
        return misDecks(usuarioId);
    }

    public List<MiDeck> borrarDeck(Long usuarioId, Long id) {
        decks.borrar(usuarioId, id);
        return misDecks(usuarioId);
    }

    // ---------- Sus tiendas ----------

    /** Sus tiendas de Kitsune Vaults (activas salvo que las haya desactivado) y las que agregó él. */
    List<TiendaJugador> tiendas(Long usuarioId, List<Tienda> tiendasKitsune) {
        List<TiendasJugadorRepositoryPort.Fila> filas = tiendasJugador.listar(usuarioId);
        Map<String, TiendasJugadorRepositoryPort.Fila> estados = filas.stream().filter(f -> f.tiendaId() != null)
            .collect(Collectors.toMap(TiendasJugadorRepositoryPort.Fila::tiendaId, Function.identity(), (a, b) -> a));
        List<TiendaJugador> lista = new ArrayList<>();
        for (Tienda t : tiendasKitsune) {
            var estado = estados.get(t.tenantId());
            lista.add(new TiendaJugador(null, t.nombre() == null ? t.tenantId() : t.nombre(), null, t.tenantId(), true,
                estado == null || estado.activa()));
        }
        filas.stream().filter(f -> f.tiendaId() == null)
            .forEach(f -> lista.add(new TiendaJugador(f.id(), f.nombre(), f.ciudad(), null, false, f.activa())));
        return lista;
    }

    public List<TiendaJugador> crearTienda(Long usuarioId, String nombre, String ciudad) {
        String limpio = texto(nombre, 120, "el nombre de la tienda");
        if (limpio == null || limpio.length() < 2) {
            throw new IllegalArgumentException("Escribe el nombre de la tienda");
        }
        if (tiendasJugador.listar(usuarioId).stream().filter(f -> f.tiendaId() == null).count() >= 100) {
            throw new IllegalArgumentException("Son demasiadas tiendas (máximo 100)");
        }
        try {
            tiendasJugador.crear(usuarioId, limpio, texto(ciudad, 80, "la ciudad"));
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Ya tienes una tienda con ese nombre");
        }
        return tiendas(usuarioId, tiendasKitsune(usuarioId));
    }

    public List<TiendaJugador> actualizarTienda(Long usuarioId, Long id, String nombre, String ciudad, boolean activa) {
        String limpio = texto(nombre, 120, "el nombre de la tienda");
        if (limpio == null || limpio.length() < 2) {
            throw new IllegalArgumentException("Escribe el nombre de la tienda");
        }
        try {
            if (!tiendasJugador.actualizar(usuarioId, id, limpio, texto(ciudad, 80, "la ciudad"), activa)) {
                throw new NoEncontradoException("Esa tienda no existe");
            }
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Ya tienes una tienda con ese nombre");
        }
        return tiendas(usuarioId, tiendasKitsune(usuarioId));
    }

    /** Sus torneos de esa tienda se quedan (guardan el nombre). */
    public List<TiendaJugador> borrarTienda(Long usuarioId, Long id) {
        if (!tiendasJugador.borrar(usuarioId, id)) {
            throw new NoEncontradoException("Esa tienda no existe");
        }
        return tiendas(usuarioId, tiendasKitsune(usuarioId));
    }

    /** Activa o desactiva una de sus tiendas de Kitsune Vaults. */
    public List<TiendaJugador> estadoTiendaKitsune(Long usuarioId, String tiendaId, boolean activa) {
        List<Tienda> suyas = tiendasKitsune(usuarioId);
        Tienda tienda = suyas.stream().filter(t -> t.tenantId().equals(tiendaId)).findFirst()
            .orElseThrow(() -> new NoEncontradoException("Esa tienda no está en tus tiendas de Kitsune Vaults"));
        tiendasJugador.estadoKitsune(usuarioId, tiendaId, tienda.nombre() == null ? tiendaId : tienda.nombre(), activa);
        if (activa) {
            // Que la siguiente visita traiga ya sus torneos.
            ultimaImportacion.remove(usuarioId);
        }
        return tiendas(usuarioId, suyas);
    }

    private List<Tienda> tiendasKitsune(Long usuarioId) {
        try {
            return cuentas.tiendas(usuarioId);
        } catch (RuntimeException ex) {
            log.warn("No se pudieron leer las tiendas de {}: {}", usuarioId, ex.getMessage());
            return List.of();
        }
    }

    /** Una tienda que escribió a mano y no tenía: se agrega a sus tiendas para la próxima vez. */
    private void recordarTienda(Long usuarioId, String nombre) {
        if (nombre == null) {
            return;
        }
        boolean yaEsta = tiendas(usuarioId, tiendasKitsune(usuarioId)).stream().anyMatch(t -> t.nombre().equalsIgnoreCase(nombre));
        if (!yaEsta) {
            try {
                tiendasJugador.crear(usuarioId, nombre, null);
            } catch (DuplicateKeyException ex) {
                // Otra pestaña la agregó al mismo tiempo.
            }
        }
    }

    /** Los que vinieron de una tienda Kitsune solo se ocultan (si no, se volverían a importar). */
    public void borrar(Long usuarioId, Long id) {
        MiTorneo actual = repo.obtener(usuarioId, id).orElseThrow(() -> new NoEncontradoException("Ese torneo no existe"));
        if (actual.importado()) {
            repo.ocultar(usuarioId, id);
        } else {
            rondas.borrarPartidas(actual);
            repo.borrar(usuarioId, id);
        }
    }

    // ---------- Importar de las tiendas de Kitsune Vaults ----------

    /** Agrega los torneos cerrados de sus tiendas Kitsune que todavía no tiene. Devuelve sus tiendas. */
    List<Tienda> importar(Long usuarioId) {
        List<Tienda> tiendas = tiendasKitsune(usuarioId);
        if (tiendas.isEmpty()) {
            return tiendas;
        }
        Instant ahora = reloj.instant();
        Instant ultima = ultimaImportacion.get(usuarioId);
        if (ultima != null && Duration.between(ultima, ahora).compareTo(CADA) < 0) {
            return tiendas;
        }
        ultimaImportacion.put(usuarioId, ahora);
        Set<String> ya = repo.importados(usuarioId);
        // Las que desactivó: sus torneos ya no se agregan solos.
        Set<String> desactivadas = tiendasJugador.listar(usuarioId).stream()
            .filter(f -> f.tiendaId() != null && !f.activa())
            .map(TiendasJugadorRepositoryPort.Fila::tiendaId)
            .collect(Collectors.toSet());
        for (Tienda tienda : tiendas) {
            if (desactivadas.contains(tienda.tenantId())) {
                continue;
            }
            for (ResultadoTorneo r : torneos.resultadosDe(usuarioId, tienda, DIAS_IMPORTAR)) {
                // Solo los cerrados (ya tienen lugares y premios) en los que de verdad jugó.
                if (!r.cerrado() || r.lugar() == null || r.fecha() == null || ya.contains(r.tiendaId() + ":" + r.id())) {
                    continue;
                }
                try {
                    repo.crear(usuarioId, desdeTienda(r));
                    ya.add(r.tiendaId() + ":" + r.id());
                } catch (DuplicateKeyException ex) {
                    // Otra pestaña lo importó al mismo tiempo.
                }
            }
        }
        return tiendas;
    }

    static MiTorneo desdeTienda(ResultadoTorneo r) {
        List<Premio> premios = new ArrayList<>();
        if (r.sobres() != null && r.sobres() > 0) {
            premios.add(new Premio("SOBRES", r.sobres() == 1 ? "1 sobre" : r.sobres() + " sobres", r.sobres(), null, "CONSERVO", null, null,
                "TIENDA"));
        }
        if (r.puntosTienda() != null && r.puntosTienda() > 0) {
            premios.add(new Premio("CREDITO", r.puntosTienda() + " puntos de la tienda", 1, null, "CONSERVO", null, null, "TIENDA"));
        }
        String moneda = r.moneda() != null && MONEDAS.contains(r.moneda()) ? r.moneda() : "MXN";
        return new MiTorneo(null, r.fecha(), r.nombre(), r.tiendaNombre() == null ? r.tiendaId() : r.tiendaNombre(), r.tiendaId(), r.id(),
            r.juego(), null, r.lugar(), r.jugadores(), null, null, null, r.entrada() == null ? BigDecimal.ZERO : r.entrada(), moneda, null,
            List.of(), premios);
    }

    // ---------- Validación ----------

    /** actual: el guardado (al editar). De uno importado no cambia de qué tienda/torneo vino. */
    MiTorneo validar(Long usuarioId, MiTorneo t, MiTorneo actual) {
        if (t == null) {
            throw new IllegalArgumentException("Faltan los datos del torneo");
        }
        MiDeck deck = null;
        if (t.deckId() != null) {
            deck = decks.obtener(usuarioId, t.deckId()).orElseThrow(() -> new IllegalArgumentException("Ese deck no está en tus decks"));
        }
        LocalDate hoy = LocalDate.now(reloj);
        if (t.fecha() == null || t.fecha().isAfter(hoy.plusDays(60)) || t.fecha().isBefore(hoy.minusYears(15))) {
            throw new IllegalArgumentException("Escribe la fecha del torneo");
        }
        String tienda = texto(t.tiendaNombre(), 120, "el nombre de la tienda");
        if (actual != null && actual.importado()) {
            tienda = actual.tiendaNombre();
        }
        if (tienda == null) {
            throw new IllegalArgumentException("Escribe en qué tienda fue");
        }
        Integer lugar = positivo(t.lugar(), "El lugar");
        Integer jugadores = positivo(t.jugadores(), "Los jugadores");
        if (lugar != null && jugadores != null && lugar > jugadores) {
            throw new IllegalArgumentException("El lugar no puede ser mayor que el número de jugadores");
        }
        BigDecimal entrada = monto(t.entrada(), "La entrada");
        String moneda = t.moneda() != null && MONEDAS.contains(t.moneda()) ? t.moneda() : "MXN";

        List<Gasto> gastos = new ArrayList<>();
        for (Gasto g : t.gastos() == null ? List.<Gasto>of() : t.gastos()) {
            if (g == null) {
                continue;
            }
            String concepto = texto(g.concepto(), 60, "el gasto");
            if (concepto == null && (g.monto() == null || g.monto().signum() == 0)) {
                continue;
            }
            if (concepto == null) {
                throw new IllegalArgumentException("Escribe en qué fue cada gasto");
            }
            BigDecimal m = monto(g.monto(), "Un gasto");
            gastos.add(new Gasto(concepto, m == null ? BigDecimal.ZERO : m));
        }
        if (gastos.size() > 20) {
            throw new IllegalArgumentException("Son demasiados gastos para un torneo (máximo 20)");
        }

        List<Premio> premios = new ArrayList<>();
        for (Premio p : t.premios() == null ? List.<Premio>of() : t.premios()) {
            if (p == null) {
                continue;
            }
            String descripcion = texto(p.descripcion(), 150, "el premio");
            if (descripcion == null) {
                throw new IllegalArgumentException("Escribe qué ganaste en cada premio");
            }
            if (p.tipo() == null || !TIPOS.contains(p.tipo())) {
                throw new IllegalArgumentException("Elige el tipo de cada premio");
            }
            boolean dinero = "EFECTIVO".equals(p.tipo()) || "CREDITO".equals(p.tipo());
            String estado = dinero || p.estado() == null || !ESTADOS.contains(p.estado()) ? "CONSERVO" : p.estado();
            boolean vendida = "VENDIDA".equals(estado);
            Integer cantidad = p.cantidad() == null ? 1 : positivo(p.cantidad(), "La cantidad");
            String origen = actual != null && actual.importado() && "TIENDA".equals(p.origen()) ? "TIENDA" : "MANUAL";
            premios.add(new Premio(p.tipo(), descripcion, cantidad, monto(p.valor(), "El valor de un premio"), estado,
                vendida ? monto(p.precioVenta(), "El precio de venta") : null, vendida ? p.vendidaEn() : null, origen));
        }
        if (premios.size() > 50) {
            throw new IllegalArgumentException("Son demasiados premios para un torneo (máximo 50)");
        }

        String juego = texto(t.juego(), 60, "el juego");
        if (juego == null && deck != null) {
            juego = deck.juego();
        }
        return new MiTorneo(
            actual == null ? null : actual.id(), t.fecha(), texto(t.nombre(), 150, "el nombre del torneo"), tienda,
            actual == null ? null : actual.tiendaId(), actual == null ? null : actual.torneoTiendaId(),
            juego, texto(t.mazo(), 80, "el deck"), lugar, jugadores,
            noNegativo(t.victorias()), noNegativo(t.derrotas()), noNegativo(t.empates()),
            entrada == null ? BigDecimal.ZERO : entrada, moneda, texto(t.notas(), 500, "las notas"), gastos, premios,
            deck == null ? null : deck.id(), null, List.of()
        );
    }

    private static String texto(String v, int maximo, String que) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String limpio = v.strip();
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException("Es demasiado largo " + que + " (máximo " + maximo + " letras)");
        }
        return limpio;
    }

    private static Integer positivo(Integer v, String que) {
        if (v == null) {
            return null;
        }
        if (v < 1 || v > 100_000) {
            throw new IllegalArgumentException(que + " tiene que ser un número mayor a 0");
        }
        return v;
    }

    private static Integer noNegativo(Integer v) {
        if (v == null) {
            return null;
        }
        if (v < 0 || v > 1000) {
            throw new IllegalArgumentException("El récord no puede tener números negativos");
        }
        return v;
    }

    private static BigDecimal monto(BigDecimal v, String que) {
        if (v == null) {
            return null;
        }
        if (v.signum() < 0 || v.compareTo(new BigDecimal("10000000")) > 0) {
            throw new IllegalArgumentException(que + " no puede ser negativo");
        }
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // ---------- Resumen ----------

    static Resumen resumen(List<MiTorneo> lista) {
        Map<String, List<MiTorneo>> porMoneda = agrupar(lista, MiTorneo::moneda);
        List<Totales> monedas = porMoneda.entrySet().stream()
            .map(e -> totales(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingInt(Totales::torneos).reversed())
            .toList();
        String principal = monedas.isEmpty() ? "MXN" : monedas.get(0).clave();
        List<MiTorneo> deLaPrincipal = porMoneda.getOrDefault(principal, List.of());
        List<Totales> porMes = agrupar(deLaPrincipal, t -> t.fecha().toString().substring(0, 7)).entrySet().stream()
            .map(e -> totales(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(Totales::clave).reversed())
            .toList();
        List<Totales> porTienda = agrupar(deLaPrincipal, MiTorneo::tiendaNombre).entrySet().stream()
            .map(e -> totales(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingInt(Totales::torneos).reversed().thenComparing(Totales::clave))
            .toList();
        List<Totales> porJuego = agrupar(deLaPrincipal, t -> t.juego() == null ? "Sin juego" : t.juego()).entrySet().stream()
            .map(e -> totales(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingInt(Totales::torneos).reversed().thenComparing(Totales::clave))
            .toList();
        return new Resumen(monedas, principal, porMes, porTienda, porJuego);
    }

    private static Map<String, List<MiTorneo>> agrupar(List<MiTorneo> lista, Function<MiTorneo, String> clave) {
        return lista.stream().collect(Collectors.groupingBy(clave, LinkedHashMap::new, Collectors.toList()));
    }

    static Totales totales(String clave, List<MiTorneo> lista) {
        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal otros = BigDecimal.ZERO;
        BigDecimal cobrado = BigDecimal.ZERO;
        BigDecimal conservado = BigDecimal.ZERO;
        int sinValor = 0;
        for (MiTorneo t : lista) {
            entradas = entradas.add(nz(t.entrada()));
            for (Gasto g : t.gastos()) {
                otros = otros.add(nz(g.monto()));
            }
            for (Premio p : t.premios()) {
                if ("EFECTIVO".equals(p.tipo()) || "CREDITO".equals(p.tipo())) {
                    cobrado = cobrado.add(nz(p.valor()));
                    if (p.valor() == null) {
                        sinValor++;
                    }
                } else if ("VENDIDA".equals(p.estado())) {
                    cobrado = cobrado.add(nz(p.precioVenta()));
                } else {
                    conservado = conservado.add(nz(p.valor()));
                    if (p.valor() == null) {
                        sinValor++;
                    }
                }
            }
        }
        BigDecimal gastado = entradas.add(otros);
        BigDecimal ganado = cobrado.add(conservado);
        return new Totales(clave, lista.size(), entradas, otros, gastado, cobrado, conservado, ganado, ganado.subtract(gastado), sinValor);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
