package com.kitsunestats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitsunestats.application.MetaPrivadoService.DatosMeta;
import com.kitsunestats.application.PartidaService.Jugador;
import com.kitsunestats.domain.model.Kitsune.Cuenta;
import com.kitsunestats.domain.model.Lider;
import com.kitsunestats.domain.model.MetaPrivado;
import com.kitsunestats.domain.model.MetaPrivado.Invitacion;
import com.kitsunestats.domain.model.MetaPrivado.Miembro;
import com.kitsunestats.domain.port.out.KitsuneCuentasPort;
import com.kitsunestats.domain.port.out.LiderRepositoryPort;
import com.kitsunestats.domain.port.out.MetaRepositoryPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Metas privados: quién entra, quién ve, quién manda y las reglas de las partidas. */
class MetaPrivadoServiceTest {

    private final Jugador ana = new Jugador(1L, "ana");
    private final Jugador beto = new Jugador(2L, "beto");
    private final Jugador caro = new Jugador(3L, "caro");
    private MetaPrivadoService servicio;
    private RepoMemoria repo;

    /** Repositorio en memoria con el mismo comportamiento que JdbcMetaRepository. */
    static class RepoMemoria implements MetaRepositoryPort {
        final Map<Long, MetaPrivado> metas = new LinkedHashMap<>();
        final Map<Long, Map<Long, Miembro>> miembros = new HashMap<>();
        final Map<Long, Map<Long, Invitacion>> invitaciones = new HashMap<>();

        private MetaPrivado conConteo(MetaPrivado m) {
            return new MetaPrivado(m.id(), m.nombre(), m.descripcion(), m.duenoId(), m.duenoUsername(), m.codigoInvitacion(), m.inicia(),
                m.termina(), m.lideres(), m.archivado(), m.creadoEn(), miembros.getOrDefault(m.id(), Map.of()).size());
        }

        @Override
        public MetaPrivado crear(MetaPrivado m) {
            long id = metas.size() + 1L;
            metas.put(id, new MetaPrivado(id, m.nombre(), m.descripcion(), m.duenoId(), m.duenoUsername(), m.codigoInvitacion(), m.inicia(),
                m.termina(), m.lideres(), false, LocalDateTime.now(), 0));
            agregarMiembro(id, m.duenoId(), m.duenoUsername());
            return obtener(id).orElseThrow();
        }

        @Override
        public Optional<MetaPrivado> obtener(Long id) {
            return Optional.ofNullable(metas.get(id)).map(this::conConteo);
        }

        @Override
        public Optional<MetaPrivado> porCodigo(String codigo) {
            return metas.values().stream().filter(m -> m.codigoInvitacion().equals(codigo)).findFirst().map(this::conConteo);
        }

        @Override
        public List<MetaPrivado> deMiembro(Long usuarioId) {
            return metas.values().stream().filter(m -> esMiembro(m.id(), usuarioId)).map(this::conConteo).toList();
        }

        @Override
        public int activosDe(Long duenoId) {
            return (int) metas.values().stream().filter(m -> m.duenoId().equals(duenoId) && !m.archivado()).count();
        }

        @Override
        public MetaPrivado actualizar(MetaPrivado m) {
            MetaPrivado a = metas.get(m.id());
            metas.put(m.id(), new MetaPrivado(a.id(), m.nombre(), m.descripcion(), a.duenoId(), a.duenoUsername(), a.codigoInvitacion(),
                m.inicia(), m.termina(), m.lideres(), a.archivado(), a.creadoEn(), 0));
            return obtener(m.id()).orElseThrow();
        }

        @Override
        public void cambiarCodigo(Long id, String codigo) {
            MetaPrivado a = metas.get(id);
            metas.put(id, new MetaPrivado(a.id(), a.nombre(), a.descripcion(), a.duenoId(), a.duenoUsername(), codigo, a.inicia(), a.termina(),
                a.lideres(), a.archivado(), a.creadoEn(), 0));
        }

        @Override
        public void archivar(Long id, boolean archivado) {
            MetaPrivado a = metas.get(id);
            metas.put(id, new MetaPrivado(a.id(), a.nombre(), a.descripcion(), a.duenoId(), a.duenoUsername(), a.codigoInvitacion(), a.inicia(),
                a.termina(), a.lideres(), archivado, a.creadoEn(), 0));
        }

        @Override
        public boolean esMiembro(Long metaId, Long usuarioId) {
            return miembros.getOrDefault(metaId, Map.of()).containsKey(usuarioId);
        }

        @Override
        public List<Miembro> miembros(Long metaId) {
            return new ArrayList<>(miembros.getOrDefault(metaId, Map.of()).values());
        }

        @Override
        public void agregarMiembro(Long metaId, Long usuarioId, String username) {
            miembros.computeIfAbsent(metaId, k -> new LinkedHashMap<>()).putIfAbsent(usuarioId, new Miembro(usuarioId, username, LocalDateTime.now()));
        }

        @Override
        public void quitarMiembro(Long metaId, Long usuarioId) {
            miembros.getOrDefault(metaId, new HashMap<>()).remove(usuarioId);
        }

        @Override
        public List<Invitacion> invitacionesDe(Long usuarioId) {
            return invitaciones.values().stream().flatMap(m -> m.values().stream()).filter(i -> i.usuarioId().equals(usuarioId)).toList();
        }

        @Override
        public List<Invitacion> invitacionesDeMeta(Long metaId) {
            return new ArrayList<>(invitaciones.getOrDefault(metaId, Map.of()).values());
        }

        @Override
        public boolean invitar(Long metaId, Long usuarioId, String username) {
            MetaPrivado m = metas.get(metaId);
            return invitaciones.computeIfAbsent(metaId, k -> new LinkedHashMap<>())
                .putIfAbsent(usuarioId, new Invitacion(metaId, m.nombre(), m.duenoUsername(), usuarioId, username, LocalDateTime.now())) == null;
        }

        @Override
        public boolean borrarInvitacion(Long metaId, Long usuarioId) {
            return invitaciones.getOrDefault(metaId, new HashMap<>()).remove(usuarioId) != null;
        }
    }

    @BeforeEach
    void preparar() {
        repo = new RepoMemoria();
        LiderRepositoryPort lideres = mock(LiderRepositoryPort.class);
        for (String c : List.of("OP05-119", "OP05-062", "OP01-001")) {
            when(lideres.obtener(c)).thenReturn(Optional.of(new Lider(c, c, List.of("RED"), "OP05", null, true)));
        }
        KitsuneCuentasPort cuentas = mock(KitsuneCuentasPort.class);
        when(cuentas.porUsername("beto")).thenReturn(Optional.of(new Cuenta(2L, "beto")));
        when(cuentas.porUsername("caro")).thenReturn(Optional.of(new Cuenta(3L, "caro")));
        servicio = new MetaPrivadoService(repo, lideres, cuentas, null);
    }

    private MetaPrivado crearLiga() {
        return servicio.crear(ana, new DatosMeta(
            "  Liga de noviembre ", "Solo amigos", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), List.of("op05-119", "OP05-062")
        ));
    }

    @Test
    void creaConElDuenoComoMiembroYValidaLosDatos() {
        MetaPrivado m = crearLiga();
        assertThat(m.nombre()).isEqualTo("Liga de noviembre");
        assertThat(m.lideres()).containsExactly("OP05-119", "OP05-062");
        assertThat(m.miembros()).isEqualTo(1);
        assertThat(m.codigoInvitacion()).hasSize(12);

        assertThatThrownBy(() -> servicio.crear(ana, new DatosMeta("ab", null, null, null, null))).hasMessageContaining("nombre");
        assertThatThrownBy(() -> servicio.crear(ana, new DatosMeta("Liga", null, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1), null)))
            .hasMessageContaining("terminar antes");
        assertThatThrownBy(() -> servicio.crear(ana, new DatosMeta("Liga", null, null, null, List.of("XX-000")))).hasMessageContaining("No existe");
    }

    @Test
    void topeDeMetasActivosPorJugador() {
        for (int i = 0; i < MetaPrivadoService.MAX_ACTIVOS; i++) {
            servicio.crear(ana, new DatosMeta("Meta " + i, null, null, null, null));
        }
        assertThatThrownBy(() -> servicio.crear(ana, new DatosMeta("Uno más", null, null, null, null))).hasMessageContaining("archiva uno");
        servicio.archivar(ana, 1L, true);
        assertThat(servicio.crear(ana, new DatosMeta("Ahora sí", null, null, null, null)).id()).isNotNull();
    }

    @Test
    void conElLinkEntraYSoloElDuenoVeElLink() {
        MetaPrivado m = crearLiga();
        // Quien no es miembro: el meta "no existe".
        assertThatThrownBy(() -> servicio.detalle(beto, m.id())).isInstanceOf(NoEncontradoException.class);

        assertThat(servicio.verInvitacion(beto, m.codigoInvitacion()).yaEsMiembro()).isFalse();
        MetaPrivado visto = servicio.unirse(beto, m.codigoInvitacion());
        assertThat(visto.codigoInvitacion()).isNull();
        assertThat(visto.miembros()).isEqualTo(2);
        assertThat(servicio.detalle(beto, m.id()).soyDueno()).isFalse();
        assertThat(servicio.detalle(ana, m.id()).meta().codigoInvitacion()).isEqualTo(m.codigoInvitacion());

        // El dueño cambia el link: el viejo ya no sirve.
        servicio.nuevoCodigo(ana, m.id());
        assertThatThrownBy(() -> servicio.unirse(caro, m.codigoInvitacion())).isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void invitacionPorUsuarioSoloDelDueno() {
        MetaPrivado m = crearLiga();
        servicio.unirse(beto, m.codigoInvitacion());
        assertThatThrownBy(() -> servicio.invitar(beto, m.id(), "caro")).isInstanceOf(ProhibidoException.class);
        assertThatThrownBy(() -> servicio.invitar(ana, m.id(), "beto")).hasMessageContaining("ya es miembro");

        servicio.invitar(ana, m.id(), "caro");
        assertThatThrownBy(() -> servicio.invitar(ana, m.id(), "caro")).hasMessageContaining("pendiente");
        assertThat(servicio.misMetas(caro).invitaciones()).extracting(Invitacion::metaNombre).containsExactly("Liga de noviembre");

        servicio.aceptar(caro, m.id());
        assertThat(servicio.misMetas(caro).metas()).hasSize(1);
        assertThat(servicio.misMetas(caro).invitaciones()).isEmpty();
        assertThatThrownBy(() -> servicio.aceptar(caro, m.id())).isInstanceOf(NoEncontradoException.class);
    }

    @Test
    void quitarSalirYArchivar() {
        MetaPrivado m = crearLiga();
        servicio.unirse(beto, m.codigoInvitacion());
        assertThatThrownBy(() -> servicio.salir(ana, m.id())).hasMessageContaining("dueño");
        assertThatThrownBy(() -> servicio.quitarMiembro(ana, m.id(), ana.id())).hasMessageContaining("dueño");
        assertThatThrownBy(() -> servicio.quitarMiembro(beto, m.id(), ana.id())).isInstanceOf(ProhibidoException.class);

        assertThat(servicio.quitarMiembro(ana, m.id(), beto.id())).extracting(Miembro::username).containsExactly("ana");
        servicio.unirse(beto, m.codigoInvitacion());
        servicio.salir(beto, m.id());
        assertThat(servicio.misMetas(beto).metas()).isEmpty();

        servicio.archivar(ana, m.id(), true);
        assertThatThrownBy(() -> servicio.unirse(beto, m.codigoInvitacion())).hasMessageContaining("archivado");
    }

    @Test
    void reglasDeLasPartidas() {
        MetaPrivado m = crearLiga();
        servicio.unirse(beto, m.codigoInvitacion());
        LocalDate enTemporada = LocalDate.of(2026, 11, 10);

        assertThat(servicio.validarPartida(ana.id(), m.id(), "OP05-119", "OP05-062", enTemporada, beto.id()).id()).isEqualTo(m.id());
        assertThatThrownBy(() -> servicio.validarPartida(caro.id(), m.id(), "OP05-119", "OP05-062", enTemporada, null))
            .hasMessageContaining("No eres miembro");
        assertThatThrownBy(() -> servicio.validarPartida(ana.id(), m.id(), "OP05-119", "OP05-062", LocalDate.of(2026, 10, 31), null))
            .hasMessageContaining("fuera de la temporada");
        assertThatThrownBy(() -> servicio.validarPartida(ana.id(), m.id(), "OP01-001", "OP05-062", enTemporada, null))
            .hasMessageContaining("no se permite ese líder");
        assertThatThrownBy(() -> servicio.validarPartida(ana.id(), m.id(), "OP05-119", "OP05-062", enTemporada, caro.id()))
            .hasMessageContaining("rival no es miembro");

        servicio.archivar(ana, m.id(), true);
        assertThatThrownBy(() -> servicio.validarPartida(ana.id(), m.id(), "OP05-119", "OP05-062", enTemporada, null))
            .hasMessageContaining("archivado");
    }
}
