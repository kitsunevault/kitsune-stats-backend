package com.kitsunestats.infrastructure.web;

import com.kitsunestats.application.MisTorneosService;
import com.kitsunestats.domain.model.MiTorneo;
import com.kitsunestats.domain.model.MiTorneo.MisTorneos;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RestController;

/** Mis torneos del jugador con sesión: lo que gasta y gana en torneos (ver MisTorneosService). Solo ve los suyos. */
@RestController
@RequestMapping("/api/registro-torneos")
@RequiredArgsConstructor
public class MisTorneosController {

    private final MisTorneosService misTorneosService;

    /** Sus torneos (con los de tiendas Kitsune que falten ya agregados), el resumen y tiendas para sugerir. */
    @GetMapping
    public MisTorneos listar(HttpServletRequest request) {
        return misTorneosService.misTorneos(JugadorController.jugador(request).id());
    }

    @PostMapping
    public ResponseEntity<MiTorneo> crear(HttpServletRequest request, @RequestBody MiTorneo torneo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(misTorneosService.crear(JugadorController.jugador(request).id(), torneo));
    }

    @PutMapping("/{id}")
    public MiTorneo actualizar(HttpServletRequest request, @PathVariable Long id, @RequestBody MiTorneo torneo) {
        return misTorneosService.actualizar(JugadorController.jugador(request).id(), id, torneo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> borrar(HttpServletRequest request, @PathVariable Long id) {
        misTorneosService.borrar(JugadorController.jugador(request).id(), id);
        return ResponseEntity.noContent().build();
    }
}
