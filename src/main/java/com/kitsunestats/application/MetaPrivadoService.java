package com.kitsunestats.application;

import com.kitsunestats.application.PartidaService.Jugador;
import com.kitsunestats.domain.model.Filtro;
import com.kitsunestats.domain.model.Kitsune.Cuenta;
import com.kitsunestats.domain.model.Lider;
import com.kitsunestats.domain.model.MetaPrivado;
import com.kitsunestats.domain.model.MetaPrivado.Invitacion;
import com.kitsunestats.domain.model.MetaPrivado.Miembro;
import com.kitsunestats.domain.model.Stats.DetalleLider;
import com.kitsunestats.domain.model.Stats.Meta;
import com.kitsunestats.domain.port.out.KitsuneCuentasPort;
import com.kitsunestats.domain.port.out.LiderRepositoryPort;
import com.kitsunestats.domain.port.out.MetaRepositoryPort;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metas privados. Reglas:
 * - Cualquier jugador con cuenta crea metas (hasta MAX_ACTIVOS sin archivar) y queda como dueño.
 * - Se entra con el link de invitación (el dueño lo puede cambiar si se filtra) o aceptando una
 *   invitación por usuario. Solo el dueño invita, quita miembros, cambia las reglas y archiva.
 * - Solo los miembros ven el meta y sus stats; las partidas no cuentan en el meta global.
 * - Reglas opcionales: temporada (fechas de las partidas) y líderes permitidos.
 * - Archivado: se sigue viendo, pero no se registran partidas ni entra nadie.
 */
@Service
@RequiredArgsConstructor
public class MetaPrivadoService {

    static final int MAX_ACTIVOS = 10;
    static final int MAX_MIEMBROS = 200;
    static final int MAX_LIDERES = 80;
    private static final String LETRAS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom AZAR = new SecureRandom();

    private final MetaRepositoryPort metas;
    private final LiderRepositoryPort lideres;
    private final KitsuneCuentasPort cuentas;
    private final StatsService stats;

    /** Lo que manda el dueño al crear o editar. lideres vacío = todos. */
    public record DatosMeta(String nombre, String descripcion, LocalDate inicia, LocalDate termina, List<String> lideres) {
    }

    public record MisMetas(List<MetaPrivado> metas, List<Invitacion> invitaciones) {
    }

    /** invitaciones: solo para el dueño (vacío para los demás). */
    public record DetalleMeta(MetaPrivado meta, boolean soyDueno, List<Miembro> miembros, List<Invitacion> invitaciones) {
    }

    /** Lo que ve quien abre un link de invitación antes de unirse. */
    public record VistaInvitacion(Long id, String nombre, String descripcion, String duenoUsername, int miembros, boolean archivado, boolean yaEsMiembro) {
    }

    public MisMetas misMetas(Jugador yo) {
        List<MetaPrivado> mios = metas.deMiembro(yo.id()).stream().map(m -> paraVer(m, yo)).toList();
        return new MisMetas(mios, metas.invitacionesDe(yo.id()));
    }

    @Transactional
    public MetaPrivado crear(Jugador yo, DatosMeta d) {
        if (metas.activosDe(yo.id()) >= MAX_ACTIVOS) {
            throw new IllegalArgumentException("Puedes tener hasta " + MAX_ACTIVOS + " metas activos; archiva uno para crear otro");
        }
        MetaPrivado nuevo = validar(new MetaPrivado(null, null, null, yo.id(), yo.username(), codigoNuevo(), null, null, List.of(), false, null, 0), d);
        return metas.crear(nuevo);
    }

    public DetalleMeta detalle(Jugador yo, Long id) {
        MetaPrivado m = deMiembro(yo, id);
        boolean dueno = m.duenoId().equals(yo.id());
        return new DetalleMeta(paraVer(m, yo), dueno, metas.miembros(id), dueno ? metas.invitacionesDeMeta(id) : List.of());
    }

    @Transactional
    public MetaPrivado editar(Jugador yo, Long id, DatosMeta d) {
        return metas.actualizar(validar(delDueno(yo, id), d));
    }

    @Transactional
    public MetaPrivado nuevoCodigo(Jugador yo, Long id) {
        delDueno(yo, id);
        metas.cambiarCodigo(id, codigoNuevo());
        return metas.obtener(id).orElseThrow();
    }

    @Transactional
    public MetaPrivado archivar(Jugador yo, Long id, boolean archivado) {
        delDueno(yo, id);
        metas.archivar(id, archivado);
        return metas.obtener(id).orElseThrow();
    }

    @Transactional
    public List<Invitacion> invitar(Jugador yo, Long id, String username) {
        MetaPrivado m = abierto(delDueno(yo, id));
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Escribe el usuario Kitsune a invitar");
        }
        Cuenta c = cuentas.porUsername(username.trim())
            .orElseThrow(() -> new IllegalArgumentException("No hay ninguna cuenta Kitsune con el usuario \"" + username.trim() + "\""));
        if (metas.esMiembro(id, c.id())) {
            throw new IllegalArgumentException(c.username() + " ya es miembro");
        }
        if (m.miembros() >= MAX_MIEMBROS) {
            throw new IllegalArgumentException("El meta ya tiene el máximo de " + MAX_MIEMBROS + " miembros");
        }
        if (!metas.invitar(id, c.id(), c.username())) {
            throw new IllegalArgumentException(c.username() + " ya tiene una invitación pendiente");
        }
        return metas.invitacionesDeMeta(id);
    }

    @Transactional
    public List<Invitacion> cancelarInvitacion(Jugador yo, Long id, Long usuarioId) {
        delDueno(yo, id);
        metas.borrarInvitacion(id, usuarioId);
        return metas.invitacionesDeMeta(id);
    }

    /** El dueño quita a un miembro; sus partidas se quedan en las stats del meta. */
    @Transactional
    public List<Miembro> quitarMiembro(Jugador yo, Long id, Long usuarioId) {
        delDueno(yo, id);
        if (yo.id().equals(usuarioId)) {
            throw new IllegalArgumentException("Eres el dueño: no te puedes quitar (puedes archivar el meta)");
        }
        metas.quitarMiembro(id, usuarioId);
        return metas.miembros(id);
    }

    @Transactional
    public void salir(Jugador yo, Long id) {
        MetaPrivado m = deMiembro(yo, id);
        if (m.duenoId().equals(yo.id())) {
            throw new IllegalArgumentException("Eres el dueño: no puedes salir (puedes archivar el meta)");
        }
        metas.quitarMiembro(id, yo.id());
    }

    public VistaInvitacion verInvitacion(Jugador yo, String codigo) {
        MetaPrivado m = porCodigo(codigo);
        return new VistaInvitacion(m.id(), m.nombre(), m.descripcion(), m.duenoUsername(), m.miembros(), m.archivado(), metas.esMiembro(m.id(), yo.id()));
    }

    @Transactional
    public MetaPrivado unirse(Jugador yo, String codigo) {
        return entrar(yo, porCodigo(codigo));
    }

    @Transactional
    public MetaPrivado aceptar(Jugador yo, Long id) {
        if (!metas.borrarInvitacion(id, yo.id())) {
            throw new NoEncontradoException("No tienes invitación a ese meta");
        }
        MetaPrivado m = metas.obtener(id).orElseThrow(() -> new NoEncontradoException("Ese meta ya no existe"));
        return entrar(yo, m);
    }

    @Transactional
    public void rechazar(Jugador yo, Long id) {
        metas.borrarInvitacion(id, yo.id());
    }

    public Meta stats(Jugador yo, Long id, Filtro filtro) {
        deMiembro(yo, id);
        return stats.meta(filtro.conMeta(id));
    }

    public DetalleLider statsLider(Jugador yo, Long id, String codigo, Filtro filtro) {
        deMiembro(yo, id);
        return stats.detalleLider(codigo, filtro.conMeta(id));
    }

    /**
     * Para registrar o corregir una partida en un meta: el jugador es miembro, el meta no está archivado,
     * la fecha está en la temporada, los dos líderes están permitidos y, si hay rival, también es miembro.
     */
    public MetaPrivado validarPartida(Long jugadorId, Long metaId, String lider, String rivalLider, LocalDate fecha, Long rivalId) {
        MetaPrivado m = metas.obtener(metaId).filter(x -> metas.esMiembro(metaId, jugadorId))
            .orElseThrow(() -> new IllegalArgumentException("No eres miembro de ese meta"));
        abierto(m);
        if (!m.enTemporada(fecha)) {
            throw new IllegalArgumentException("La fecha está fuera de la temporada del meta (" + temporada(m) + ")");
        }
        if (!m.permiteLider(lider) || !m.permiteLider(rivalLider)) {
            throw new IllegalArgumentException("En \"" + m.nombre() + "\" no se permite ese líder");
        }
        if (rivalId != null && !metas.esMiembro(metaId, rivalId)) {
            throw new IllegalArgumentException("Tu rival no es miembro de \"" + m.nombre() + "\"");
        }
        return m;
    }

    private MetaPrivado entrar(Jugador yo, MetaPrivado m) {
        abierto(m);
        if (!metas.esMiembro(m.id(), yo.id())) {
            if (m.miembros() >= MAX_MIEMBROS) {
                throw new IllegalArgumentException("El meta ya tiene el máximo de " + MAX_MIEMBROS + " miembros");
            }
            metas.agregarMiembro(m.id(), yo.id(), yo.username());
        }
        metas.borrarInvitacion(m.id(), yo.id());
        return paraVer(metas.obtener(m.id()).orElseThrow(), yo);
    }

    private MetaPrivado validar(MetaPrivado base, DatosMeta d) {
        if (d == null) {
            throw new IllegalArgumentException("Faltan los datos del meta");
        }
        String nombre = d.nombre() == null ? "" : d.nombre().trim();
        if (nombre.length() < 3 || nombre.length() > 60) {
            throw new IllegalArgumentException("El nombre va de 3 a 60 caracteres");
        }
        String descripcion = d.descripcion() == null || d.descripcion().isBlank() ? null : d.descripcion().trim();
        if (descripcion != null && descripcion.length() > 300) {
            throw new IllegalArgumentException("La descripción es de máximo 300 caracteres");
        }
        if (d.inicia() != null && d.termina() != null && d.termina().isBefore(d.inicia())) {
            throw new IllegalArgumentException("La temporada no puede terminar antes de empezar");
        }
        Set<String> codigos = new LinkedHashSet<>();
        for (String c : d.lideres() == null ? List.<String>of() : d.lideres()) {
            if (c == null || c.isBlank()) {
                continue;
            }
            Lider l = lideres.obtener(c.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No existe el líder " + c));
            codigos.add(l.codigo());
        }
        if (codigos.size() > MAX_LIDERES) {
            throw new IllegalArgumentException("Máximo " + MAX_LIDERES + " líderes permitidos");
        }
        return new MetaPrivado(
            base.id(), nombre, descripcion, base.duenoId(), base.duenoUsername(), base.codigoInvitacion(),
            d.inicia(), d.termina(), List.copyOf(codigos), base.archivado(), base.creadoEn(), base.miembros()
        );
    }

    /** Para quien no es miembro, el meta "no existe" (no se revela que hay uno con ese id). */
    private MetaPrivado deMiembro(Jugador yo, Long id) {
        return metas.obtener(id).filter(m -> metas.esMiembro(id, yo.id()))
            .orElseThrow(() -> new NoEncontradoException("Ese meta no existe o no eres miembro"));
    }

    private MetaPrivado delDueno(Jugador yo, Long id) {
        MetaPrivado m = deMiembro(yo, id);
        if (!m.duenoId().equals(yo.id())) {
            throw new ProhibidoException("Solo el dueño del meta puede hacer eso");
        }
        return m;
    }

    private MetaPrivado porCodigo(String codigo) {
        return metas.porCodigo(codigo == null ? "" : codigo.trim())
            .orElseThrow(() -> new NoEncontradoException("Ese link de invitación ya no sirve; pídele uno nuevo al dueño del meta"));
    }

    private static MetaPrivado abierto(MetaPrivado m) {
        if (m.archivado()) {
            throw new IllegalArgumentException("\"" + m.nombre() + "\" está archivado");
        }
        return m;
    }

    /** El link de invitación solo lo ve el dueño. */
    private static MetaPrivado paraVer(MetaPrivado m, Jugador yo) {
        if (m.duenoId().equals(yo.id())) {
            return m;
        }
        return new MetaPrivado(
            m.id(), m.nombre(), m.descripcion(), m.duenoId(), m.duenoUsername(), null, m.inicia(), m.termina(), m.lideres(),
            m.archivado(), m.creadoEn(), m.miembros()
        );
    }

    private static String temporada(MetaPrivado m) {
        return (m.inicia() == null ? "sin inicio" : "del " + m.inicia()) + (m.termina() == null ? "" : " al " + m.termina());
    }

    private static String codigoNuevo() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(LETRAS.charAt(AZAR.nextInt(LETRAS.length())));
        }
        return sb.toString();
    }
}
