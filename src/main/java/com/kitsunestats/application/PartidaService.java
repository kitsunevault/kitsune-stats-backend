package com.kitsunestats.application;

import com.kitsunestats.domain.model.Filtro;
import com.kitsunestats.domain.model.Kitsune.Cuenta;
import com.kitsunestats.domain.model.Kitsune.Participante;
import com.kitsunestats.domain.model.Kitsune.Tienda;
import com.kitsunestats.domain.model.Kitsune.Torneo;
import com.kitsunestats.domain.model.Lider;
import com.kitsunestats.domain.model.NuevaPartida;
import com.kitsunestats.domain.model.Partida;
import com.kitsunestats.domain.port.out.KitsuneCuentasPort;
import com.kitsunestats.domain.port.out.LiderRepositoryPort;
import com.kitsunestats.domain.port.out.PartidaRepositoryPort;
import com.kitsunestats.domain.port.out.TorneosTiendasPort;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registrar, corregir y confirmar partidas. Reglas:
 * - Los dos líderes deben existir y estar activos; quién empezó y quién ganó, obligatorios.
 * - Casual: fecha de hoy o del último año (nunca futura). Torneo: de un torneo de una tienda en el que
 *   el jugador participó (la fecha es la del torneo) y el rival, si se indica, de sus participantes.
 * - Con rival (cuenta Kitsune) la partida queda pendiente hasta que él la confirme o la rechace;
 *   ya confirmada no se cambia ni se borra (es de los dos).
 * - Máximo LIMITE_DIARIO registros por jugador al día, para que nadie deforme las globales.
 */
@Service
@RequiredArgsConstructor
public class PartidaService {

    static final int LIMITE_DIARIO = 50;
    static final int RONDAS_MAXIMAS = 20;

    private final PartidaRepositoryPort repo;
    private final LiderRepositoryPort lideres;
    private final KitsuneCuentasPort cuentas;
    private final TorneosTiendasPort torneos;

    /** Quién hace la petición (viene del JWT de Kitsune Stats). */
    public record Jugador(Long id, String username) {
    }

    @Transactional
    public Partida registrar(Jugador yo, NuevaPartida n) {
        if (repo.registradasHoy(yo.id()) >= LIMITE_DIARIO) {
            throw new IllegalArgumentException("Llegaste al máximo de " + LIMITE_DIARIO + " partidas por día");
        }
        String lider = liderActivo(n.lider(), "tu líder");
        String rivalLider = liderActivo(n.rivalLider(), "el líder de tu rival");
        if (n.empece() == null || n.gane() == null) {
            throw new IllegalArgumentException("Indica quién empezó y quién ganó");
        }
        String notas = notas(n.notas());
        String tipo = n.tipo() == null || n.tipo().isBlank() ? Partida.CASUAL : n.tipo().trim().toUpperCase();

        if (Partida.TORNEO.equals(tipo)) {
            if (n.tiendaId() == null || n.torneoId() == null || n.ronda() == null) {
                throw new IllegalArgumentException("Elige el torneo y la ronda");
            }
            if (n.ronda() < 1 || n.ronda() > RONDAS_MAXIMAS) {
                throw new IllegalArgumentException("La ronda va de 1 a " + RONDAS_MAXIMAS);
            }
            Tienda tienda = cuentas.tiendas(yo.id()).stream()
                .filter(t -> t.tenantId().equals(n.tiendaId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No perteneces a esa tienda"));
            Torneo torneo = torneos.torneosDe(yo.id(), tienda).stream()
                .filter(t -> t.id().equals(n.torneoId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No jugaste ese torneo (o ya pasaron más de 60 días)"));
            Cuenta rival = null;
            if (n.rivalId() != null) {
                if (n.rivalId().equals(yo.id())) {
                    throw new IllegalArgumentException("Tu rival no puedes ser tú");
                }
                Participante p = torneo.participantes().stream()
                    .filter(x -> n.rivalId().equals(x.usuarioId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Ese jugador no está en el torneo"));
                rival = new Cuenta(p.usuarioId(), p.nombre());
                if (repo.existeRonda(tienda.tenantId(), torneo.id(), n.ronda(), yo.id(), rival.id())) {
                    throw new ConflictoException("Esa ronda contra ese jugador ya está registrada. Si la registró tu rival, confírmala en \"Por confirmar\".");
                }
            }
            return repo.crear(new Partida(
                null, yo.id(), yo.username(), lider, rivalLider, n.empece(), n.gane(), torneo.fecha(), Partida.TORNEO,
                rival == null ? null : rival.id(), rival == null ? null : rival.username(),
                rival == null ? Partida.SIN_RIVAL : Partida.PENDIENTE,
                tienda.tenantId(), tienda.nombre(), torneo.id(), torneo.nombre(), n.ronda(), notas, false, null
            ));
        }
        if (!Partida.CASUAL.equals(tipo)) {
            throw new IllegalArgumentException("Tipo de partida inválido");
        }
        LocalDate fecha = fecha(n.jugadaEn());
        Cuenta rival = null;
        if (n.rivalUsername() != null && !n.rivalUsername().isBlank()) {
            rival = cuentas.porUsername(n.rivalUsername().trim())
                .orElseThrow(() -> new IllegalArgumentException("No hay ninguna cuenta Kitsune con el usuario \"" + n.rivalUsername().trim() + "\""));
            if (rival.id().equals(yo.id())) {
                throw new IllegalArgumentException("Tu rival no puedes ser tú");
            }
        }
        return repo.crear(new Partida(
            null, yo.id(), yo.username(), lider, rivalLider, n.empece(), n.gane(), fecha, Partida.CASUAL,
            rival == null ? null : rival.id(), rival == null ? null : rival.username(),
            rival == null ? Partida.SIN_RIVAL : Partida.PENDIENTE,
            null, null, null, null, null, notas, false, null
        ));
    }

    /**
     * Corrige una partida propia que el rival todavía no confirma: líderes, quién empezó, quién ganó,
     * notas y (en casual) la fecha. Si el rival la había rechazado, vuelve a pedirle confirmación.
     */
    @Transactional
    public Partida corregir(Jugador yo, Long id, NuevaPartida n) {
        Partida actual = propia(yo, id);
        if (Partida.CONFIRMADA.equals(actual.confirmacion())) {
            throw new ConflictoException("Tu rival ya la confirmó: ya no se puede cambiar");
        }
        if (n.empece() == null || n.gane() == null) {
            throw new IllegalArgumentException("Indica quién empezó y quién ganó");
        }
        LocalDate fecha = Partida.CASUAL.equals(actual.tipo()) ? fecha(n.jugadaEn() != null ? n.jugadaEn() : actual.jugadaEn()) : actual.jugadaEn();
        String confirmacion = Partida.RECHAZADA.equals(actual.confirmacion()) ? Partida.PENDIENTE : actual.confirmacion();
        Partida nueva = new Partida(
            actual.id(), actual.jugadorId(), actual.jugadorUsername(), liderActivo(n.lider(), "tu líder"), liderActivo(n.rivalLider(), "el líder de tu rival"),
            n.empece(), n.gane(), fecha, actual.tipo(), actual.rivalId(), actual.rivalUsername(), confirmacion,
            actual.tiendaId(), actual.tiendaNombre(), actual.torneoId(), actual.torneoNombre(), actual.ronda(), notas(n.notas()), actual.oculta(),
            actual.creadoEn()
        );
        return repo.actualizar(nueva);
    }

    @Transactional
    public void borrar(Jugador yo, Long id) {
        Partida actual = propia(yo, id);
        if (Partida.CONFIRMADA.equals(actual.confirmacion())) {
            throw new ConflictoException("Tu rival ya la confirmó: ya no se puede borrar");
        }
        repo.borrar(id);
    }

    @Transactional
    public Partida confirmar(Jugador yo, Long id) {
        return responder(yo, id, Partida.CONFIRMADA);
    }

    @Transactional
    public Partida rechazar(Jugador yo, Long id) {
        return responder(yo, id, Partida.RECHAZADA);
    }

    public List<Partida> misPartidas(Jugador yo, Filtro filtro, int pagina, int tamano) {
        int t = Math.min(Math.max(tamano, 1), 100);
        return repo.deJugador(yo.id(), filtro, t, Math.max(pagina, 0) * t);
    }

    public List<Partida> porConfirmar(Jugador yo) {
        return repo.porConfirmar(yo.id());
    }

    /** Administrador: las más recientes de todos (con las ocultas). */
    public List<Partida> recientes(int pagina) {
        return repo.recientes(50, Math.max(pagina, 0) * 50);
    }

    /** Administrador: una partida que deforma las globales (spam, datos falsos) deja de contar. */
    @Transactional
    public void ocultar(Long id, boolean oculta) {
        repo.obtener(id).orElseThrow(() -> new NoEncontradoException("La partida " + id + " no existe"));
        repo.ocultar(id, oculta);
    }

    private Partida responder(Jugador yo, Long id, String respuesta) {
        Partida p = repo.obtener(id).orElseThrow(() -> new NoEncontradoException("La partida " + id + " no existe"));
        if (!yo.id().equals(p.rivalId())) {
            throw new NoEncontradoException("La partida " + id + " no existe");
        }
        if (!Partida.PENDIENTE.equals(p.confirmacion())) {
            throw new ConflictoException("Esta partida ya no está pendiente");
        }
        repo.cambiarConfirmacion(id, respuesta);
        return repo.obtener(id).orElseThrow();
    }

    private Partida propia(Jugador yo, Long id) {
        Partida p = repo.obtener(id).orElseThrow(() -> new NoEncontradoException("La partida " + id + " no existe"));
        if (!p.jugadorId().equals(yo.id())) {
            // Al rival tampoco se le deja cambiarla: él la confirma o la rechaza.
            throw new ProhibidoException("Solo quien registró la partida la puede cambiar");
        }
        return p;
    }

    private String liderActivo(String codigo, String cual) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Elige " + cual);
        }
        Lider l = lideres.obtener(codigo.trim().toUpperCase())
            .filter(Lider::activo)
            .orElseThrow(() -> new IllegalArgumentException("No existe el líder " + codigo));
        return l.codigo();
    }

    private static LocalDate fecha(LocalDate f) {
        LocalDate hoy = LocalDate.now();
        if (f == null) {
            return hoy;
        }
        if (f.isAfter(hoy)) {
            throw new IllegalArgumentException("La fecha no puede ser futura");
        }
        if (f.isBefore(hoy.minusYears(1))) {
            throw new IllegalArgumentException("Solo se registran partidas del último año");
        }
        return f;
    }

    private static String notas(String notas) {
        if (notas == null || notas.isBlank()) {
            return null;
        }
        String n = notas.trim();
        if (n.length() > 300) {
            throw new IllegalArgumentException("Las notas son de máximo 300 caracteres");
        }
        return n;
    }
}
