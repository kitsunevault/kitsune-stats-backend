package com.kitsunestats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitsunestats.application.PartidaService.Jugador;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PartidaServiceTest {

    private final List<Partida> guardadas = new ArrayList<>();
    private PartidaService servicio;
    private final Jugador ana = new Jugador(1L, "ana");
    private final Jugador beto = new Jugador(2L, "beto");
    private final Torneo torneo = new Torneo("mc", "Magic Cauldron", 9L, "Semanal", LocalDate.now().minusDays(2), "One Piece", true,
        List.of(new Participante(1L, "ana", 1), new Participante(2L, "beto", 2)));

    /** Repositorio en memoria: lo justo para las reglas del servicio. */
    private class RepoMemoria implements PartidaRepositoryPort {
        @Override
        public Partida crear(Partida p) {
            Partida c = new Partida((long) guardadas.size() + 1, p.jugadorId(), p.jugadorUsername(), p.lider(), p.rivalLider(), p.empece(), p.gane(),
                p.jugadaEn(), p.tipo(), p.rivalId(), p.rivalUsername(), p.confirmacion(), p.tiendaId(), p.tiendaNombre(), p.torneoId(),
                p.torneoNombre(), p.ronda(), p.notas(), false, LocalDateTime.now());
            guardadas.add(c);
            return c;
        }

        @Override
        public Optional<Partida> obtener(Long id) {
            return guardadas.stream().filter(p -> p.id().equals(id)).findFirst();
        }

        @Override
        public Partida actualizar(Partida p) {
            guardadas.replaceAll(x -> x.id().equals(p.id()) ? p : x);
            return p;
        }

        @Override
        public void borrar(Long id) {
            guardadas.removeIf(p -> p.id().equals(id));
        }

        @Override
        public void cambiarConfirmacion(Long id, String c) {
            guardadas.replaceAll(p -> !p.id().equals(id) ? p : new Partida(p.id(), p.jugadorId(), p.jugadorUsername(), p.lider(), p.rivalLider(),
                p.empece(), p.gane(), p.jugadaEn(), p.tipo(), p.rivalId(), p.rivalUsername(), c, p.tiendaId(), p.tiendaNombre(), p.torneoId(),
                p.torneoNombre(), p.ronda(), p.notas(), p.oculta(), p.creadoEn()));
        }

        @Override
        public void ocultar(Long id, boolean oculta) {
        }

        @Override
        public List<Partida> deJugador(Long usuarioId, Filtro filtro, int limite, int saltar) {
            return List.of();
        }

        @Override
        public List<Partida> porConfirmar(Long usuarioId) {
            return guardadas.stream().filter(p -> usuarioId.equals(p.rivalId()) && Partida.PENDIENTE.equals(p.confirmacion())).toList();
        }

        @Override
        public int registradasHoy(Long jugadorId) {
            return (int) guardadas.stream().filter(p -> p.jugadorId().equals(jugadorId)).count();
        }

        @Override
        public boolean existeRonda(String tiendaId, Long torneoId, int ronda, Long a, Long b) {
            return guardadas.stream().anyMatch(p -> tiendaId.equals(p.tiendaId()) && torneoId.equals(p.torneoId()) && p.ronda() == ronda
                && !Partida.RECHAZADA.equals(p.confirmacion())
                && ((p.jugadorId().equals(a) && b.equals(p.rivalId())) || (p.jugadorId().equals(b) && a.equals(p.rivalId()))));
        }

        @Override
        public List<Partida> recientes(int limite, int saltar) {
            return guardadas;
        }
    }

    @BeforeEach
    void setUp() {
        LiderRepositoryPort lideres = new LiderRepositoryPort() {
            @Override
            public List<Lider> listar(boolean soloActivos) {
                return List.of();
            }

            @Override
            public Optional<Lider> obtener(String codigo) {
                return switch (codigo) {
                    case "OP05-119", "OP05-062" -> Optional.of(new Lider(codigo, codigo, List.of("PURPLE"), "OP05", null, true));
                    case "OP01-001" -> Optional.of(new Lider(codigo, codigo, List.of("RED"), "OP01", null, false));
                    default -> Optional.empty();
                };
            }

            @Override
            public Lider guardar(Lider lider) {
                return lider;
            }
        };
        KitsuneCuentasPort cuentas = new KitsuneCuentasPort() {
            @Override
            public Optional<Cuenta> porUsername(String username) {
                return switch (username) {
                    case "ana" -> Optional.of(new Cuenta(1L, "ana"));
                    case "beto" -> Optional.of(new Cuenta(2L, "beto"));
                    default -> Optional.empty();
                };
            }

            @Override
            public Optional<Cuenta> porId(Long id) {
                return Optional.empty();
            }

            @Override
            public List<Tienda> tiendas(Long usuarioId) {
                return List.of(new Tienda("mc", "Magic Cauldron"));
            }
        };
        TorneosTiendasPort torneos = (usuarioId, tienda) -> List.of(torneo);
        servicio = new PartidaService(new RepoMemoria(), lideres, cuentas, torneos);
    }

    private static NuevaPartida casual(String rival) {
        return new NuevaPartida("op05-119", "OP05-062", true, true, null, null, rival, null, null, null, null, null);
    }

    @Test
    void casualSinRival() {
        Partida p = servicio.registrar(ana, casual(null));
        assertThat(p.lider()).isEqualTo("OP05-119");
        assertThat(p.confirmacion()).isEqualTo(Partida.SIN_RIVAL);
        assertThat(p.jugadaEn()).isEqualTo(LocalDate.now());
    }

    @Test
    void conRivalQuedaPendienteYElRivalConfirma() {
        Partida p = servicio.registrar(ana, casual("beto"));
        assertThat(p.confirmacion()).isEqualTo(Partida.PENDIENTE);
        assertThat(servicio.porConfirmar(beto)).hasSize(1);
        // Ana no puede confirmar su propia partida (no es la rival).
        assertThatThrownBy(() -> servicio.confirmar(ana, p.id())).isInstanceOf(NoEncontradoException.class);
        assertThat(servicio.confirmar(beto, p.id()).confirmacion()).isEqualTo(Partida.CONFIRMADA);
        // Ya confirmada: ni se corrige, ni se borra, ni se vuelve a responder.
        assertThatThrownBy(() -> servicio.corregir(ana, p.id(), casual(null))).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.borrar(ana, p.id())).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.rechazar(beto, p.id())).isInstanceOf(ConflictoException.class);
    }

    @Test
    void corregirTrasRechazoVuelveAPedirConfirmacion() {
        Partida p = servicio.registrar(ana, casual("beto"));
        servicio.rechazar(beto, p.id());
        Partida corregida = servicio.corregir(ana, p.id(), new NuevaPartida("OP05-119", "OP05-062", false, false, null, null, null, null, null, null, null, null));
        assertThat(corregida.confirmacion()).isEqualTo(Partida.PENDIENTE);
        assertThat(corregida.gane()).isFalse();
        // El rival no puede corregirla: solo confirmar o rechazar.
        assertThatThrownBy(() -> servicio.corregir(beto, p.id(), casual(null))).isInstanceOf(ProhibidoException.class);
    }

    @Test
    void validaciones() {
        assertThatThrownBy(() -> servicio.registrar(ana, casual("ana"))).hasMessageContaining("no puedes ser tú");
        assertThatThrownBy(() -> servicio.registrar(ana, casual("nadie"))).hasMessageContaining("No hay ninguna cuenta");
        assertThatThrownBy(() -> servicio.registrar(ana, new NuevaPartida("OP01-001", "OP05-062", true, true, null, null, null, null, null, null, null, null)))
            .hasMessageContaining("No existe el líder");
        assertThatThrownBy(() -> servicio.registrar(ana, new NuevaPartida("OP05-119", "OP05-062", null, true, null, null, null, null, null, null, null, null)))
            .hasMessageContaining("quién empezó");
        assertThatThrownBy(() -> servicio.registrar(ana, new NuevaPartida("OP05-119", "OP05-062", true, true, LocalDate.now().plusDays(1), null, null, null, null, null, null, null)))
            .hasMessageContaining("futura");
    }

    @Test
    void limiteDiario() {
        for (int i = 0; i < PartidaService.LIMITE_DIARIO; i++) {
            servicio.registrar(ana, casual(null));
        }
        assertThatThrownBy(() -> servicio.registrar(ana, casual(null))).hasMessageContaining("máximo");
    }

    @Test
    void torneoConRivalDeLosParticipantesYSinRondaDuplicada() {
        NuevaPartida ronda1 = new NuevaPartida("OP05-119", "OP05-062", true, true, null, "TORNEO", null, 2L, "mc", 9L, 1, null);
        Partida p = servicio.registrar(ana, ronda1);
        assertThat(p.tipo()).isEqualTo(Partida.TORNEO);
        assertThat(p.jugadaEn()).isEqualTo(torneo.fecha());
        assertThat(p.rivalUsername()).isEqualTo("beto");
        assertThat(p.confirmacion()).isEqualTo(Partida.PENDIENTE);
        // La misma ronda la intenta registrar el rival: debe confirmarla, no duplicarla.
        assertThatThrownBy(() -> servicio.registrar(beto, new NuevaPartida("OP05-062", "OP05-119", false, false, null, "TORNEO", null, 1L, "mc", 9L, 1, null)))
            .isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.registrar(ana, new NuevaPartida("OP05-119", "OP05-062", true, true, null, "TORNEO", null, 99L, "mc", 9L, 2, null)))
            .hasMessageContaining("no está en el torneo");
        assertThatThrownBy(() -> servicio.registrar(ana, new NuevaPartida("OP05-119", "OP05-062", true, true, null, "TORNEO", null, null, "otra", 9L, 2, null)))
            .hasMessageContaining("No perteneces");
        assertThatThrownBy(() -> servicio.registrar(ana, new NuevaPartida("OP05-119", "OP05-062", true, true, null, "TORNEO", null, null, "mc", 77L, 2, null)))
            .hasMessageContaining("No jugaste");
    }
}
