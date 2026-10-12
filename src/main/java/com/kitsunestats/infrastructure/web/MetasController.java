package com.kitsunestats.infrastructure.web;

import static com.kitsunestats.infrastructure.web.JugadorController.jugador;

import com.kitsunestats.application.MetaPrivadoService;
import com.kitsunestats.application.MetaPrivadoService.DatosMeta;
import com.kitsunestats.application.MetaPrivadoService.DetalleMeta;
import com.kitsunestats.application.MetaPrivadoService.MisMetas;
import com.kitsunestats.application.MetaPrivadoService.VistaInvitacion;
import com.kitsunestats.domain.model.MetaPrivado;
import com.kitsunestats.domain.model.MetaPrivado.Invitacion;
import com.kitsunestats.domain.model.MetaPrivado.Miembro;
import com.kitsunestats.domain.model.Stats.DetalleLider;
import com.kitsunestats.domain.model.Stats.Meta;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Metas privados (con sesión): crear, invitar, unirse y sus stats, que solo ven los miembros. */
@RestController
@RequestMapping("/api/metas")
@RequiredArgsConstructor
public class MetasController {

    private final MetaPrivadoService metas;

    public record InvitarRequest(String username) {
    }

    public record ArchivarRequest(boolean archivado) {
    }

    @GetMapping
    public MisMetas misMetas(HttpServletRequest request) {
        return metas.misMetas(jugador(request));
    }

    @PostMapping
    public ResponseEntity<MetaPrivado> crear(HttpServletRequest request, @RequestBody DatosMeta datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(metas.crear(jugador(request), datos));
    }

    @GetMapping("/{id}")
    public DetalleMeta detalle(HttpServletRequest request, @PathVariable Long id) {
        return metas.detalle(jugador(request), id);
    }

    @PutMapping("/{id}")
    public MetaPrivado editar(HttpServletRequest request, @PathVariable Long id, @RequestBody DatosMeta datos) {
        return metas.editar(jugador(request), id, datos);
    }

    @PostMapping("/{id}/codigo")
    public MetaPrivado nuevoCodigo(HttpServletRequest request, @PathVariable Long id) {
        return metas.nuevoCodigo(jugador(request), id);
    }

    @PostMapping("/{id}/archivar")
    public MetaPrivado archivar(HttpServletRequest request, @PathVariable Long id, @RequestBody ArchivarRequest r) {
        return metas.archivar(jugador(request), id, r.archivado());
    }

    @PostMapping("/{id}/invitaciones")
    public List<Invitacion> invitar(HttpServletRequest request, @PathVariable Long id, @RequestBody InvitarRequest r) {
        return metas.invitar(jugador(request), id, r.username());
    }

    @DeleteMapping("/{id}/invitaciones/{usuarioId}")
    public List<Invitacion> cancelarInvitacion(HttpServletRequest request, @PathVariable Long id, @PathVariable Long usuarioId) {
        return metas.cancelarInvitacion(jugador(request), id, usuarioId);
    }

    @DeleteMapping("/{id}/miembros/{usuarioId}")
    public List<Miembro> quitarMiembro(HttpServletRequest request, @PathVariable Long id, @PathVariable Long usuarioId) {
        return metas.quitarMiembro(jugador(request), id, usuarioId);
    }

    @PostMapping("/{id}/salir")
    public ResponseEntity<Void> salir(HttpServletRequest request, @PathVariable Long id) {
        metas.salir(jugador(request), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/aceptar")
    public MetaPrivado aceptar(HttpServletRequest request, @PathVariable Long id) {
        return metas.aceptar(jugador(request), id);
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<Void> rechazar(HttpServletRequest request, @PathVariable Long id) {
        metas.rechazar(jugador(request), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unirse/{codigo}")
    public VistaInvitacion verInvitacion(HttpServletRequest request, @PathVariable String codigo) {
        return metas.verInvitacion(jugador(request), codigo);
    }

    @PostMapping("/unirse/{codigo}")
    public MetaPrivado unirse(HttpServletRequest request, @PathVariable String codigo) {
        return metas.unirse(jugador(request), codigo);
    }

    @GetMapping("/{id}/stats")
    public Meta stats(
        HttpServletRequest request,
        @PathVariable Long id,
        @RequestParam(required = false) String periodo,
        @RequestParam(required = false) Boolean verificadas
    ) {
        return metas.stats(jugador(request), id, Filtros.de(periodo, null, verificadas, null, null));
    }

    @GetMapping("/{id}/lideres/{codigo}/stats")
    public DetalleLider statsLider(
        HttpServletRequest request,
        @PathVariable Long id,
        @PathVariable String codigo,
        @RequestParam(required = false) String periodo,
        @RequestParam(required = false) Boolean verificadas
    ) {
        return metas.statsLider(jugador(request), id, codigo, Filtros.de(periodo, null, verificadas, null, null));
    }
}
