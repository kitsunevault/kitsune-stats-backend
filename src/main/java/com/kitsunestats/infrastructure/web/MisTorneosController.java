package com.kitsunestats.infrastructure.web;

import com.kitsunestats.application.MisTorneosService;
import com.kitsunestats.domain.model.MiDeck;
import com.kitsunestats.domain.model.MiTorneo;
import com.kitsunestats.domain.model.MiTorneo.MisTorneos;
import com.kitsunestats.domain.model.MiTorneo.TiendaJugador;
import java.util.List;
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
        var yo = JugadorController.jugador(request);
        return misTorneosService.actualizar(yo.id(), yo.username(), id, torneo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> borrar(HttpServletRequest request, @PathVariable Long id) {
        misTorneosService.borrar(JugadorController.jugador(request).id(), id);
        return ResponseEntity.noContent().build();
    }

    // ---------- Mis decks (todas responden con la lista y cómo le va con cada uno) ----------

    @GetMapping("/decks")
    public List<MiDeck> decks(HttpServletRequest request) {
        return misTorneosService.misDecks(JugadorController.jugador(request).id());
    }

    @PostMapping("/decks")
    public ResponseEntity<List<MiDeck>> crearDeck(HttpServletRequest request, @RequestBody MiDeck deck) {
        return ResponseEntity.status(HttpStatus.CREATED).body(misTorneosService.crearDeck(JugadorController.jugador(request).id(), deck));
    }

    @PutMapping("/decks/{id}")
    public List<MiDeck> actualizarDeck(HttpServletRequest request, @PathVariable Long id, @RequestBody MiDeck deck) {
        return misTorneosService.actualizarDeck(JugadorController.jugador(request).id(), id, deck);
    }

    @DeleteMapping("/decks/{id}")
    public List<MiDeck> borrarDeck(HttpServletRequest request, @PathVariable Long id) {
        return misTorneosService.borrarDeck(JugadorController.jugador(request).id(), id);
    }

    // ---------- Sus tiendas (todas responden con la lista completa) ----------

    public record DatosTienda(String nombre, String ciudad, Boolean activa) {
    }

    /** Una tienda que no está en Kitsune Vaults (o que no tiene en Mis tiendas). */
    @PostMapping("/tiendas")
    public ResponseEntity<List<TiendaJugador>> crearTienda(HttpServletRequest request, @RequestBody DatosTienda datos) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(misTorneosService.crearTienda(JugadorController.jugador(request).id(), datos.nombre(), datos.ciudad()));
    }

    @PutMapping("/tiendas/{id}")
    public List<TiendaJugador> actualizarTienda(HttpServletRequest request, @PathVariable Long id, @RequestBody DatosTienda datos) {
        return misTorneosService.actualizarTienda(JugadorController.jugador(request).id(), id, datos.nombre(), datos.ciudad(),
            !Boolean.FALSE.equals(datos.activa()));
    }

    @DeleteMapping("/tiendas/{id}")
    public List<TiendaJugador> borrarTienda(HttpServletRequest request, @PathVariable Long id) {
        return misTorneosService.borrarTienda(JugadorController.jugador(request).id(), id);
    }

    /** Activar o desactivar una de sus tiendas de Kitsune Vaults. */
    @PutMapping("/tiendas/kitsune/{tiendaId}")
    public List<TiendaJugador> estadoTiendaKitsune(HttpServletRequest request, @PathVariable String tiendaId, @RequestBody DatosTienda datos) {
        return misTorneosService.estadoTiendaKitsune(JugadorController.jugador(request).id(), tiendaId, !Boolean.FALSE.equals(datos.activa()));
    }
}
