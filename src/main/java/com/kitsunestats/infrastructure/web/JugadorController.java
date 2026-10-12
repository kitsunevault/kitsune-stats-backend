package com.kitsunestats.infrastructure.web;

import com.kitsunestats.application.PartidaService;
import com.kitsunestats.application.PartidaService.Jugador;
import com.kitsunestats.application.StatsService;
import com.kitsunestats.application.TorneoService;
import com.kitsunestats.domain.model.Kitsune.Torneo;
import com.kitsunestats.domain.model.NuevaPartida;
import com.kitsunestats.domain.model.Partida;
import com.kitsunestats.domain.model.Stats.Personales;
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

/** Lo del jugador con sesión (cuenta Kitsune): registrar y confirmar partidas, sus stats y sus torneos. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JugadorController {

    private final PartidaService partidaService;
    private final StatsService statsService;
    private final TorneoService torneoService;

    public record Yo(Long usuarioId, String username, boolean admin) {
    }

    static Jugador jugador(HttpServletRequest request) {
        return new Jugador((Long) request.getAttribute("usuarioId"), (String) request.getAttribute("username"));
    }

    @GetMapping("/yo")
    public Yo yo(HttpServletRequest request) {
        Jugador j = jugador(request);
        return new Yo(j.id(), j.username(), Boolean.TRUE.equals(request.getAttribute("admin")));
    }

    @GetMapping("/mis-stats")
    public Personales misStats(
        HttpServletRequest request,
        @RequestParam(required = false) String periodo,
        @RequestParam(required = false) String tipo,
        @RequestParam(required = false) String lider
    ) {
        return statsService.personales(jugador(request).id(), Filtros.de(periodo, tipo, false, null, lider));
    }

    @GetMapping("/partidas")
    public List<Partida> partidas(
        HttpServletRequest request,
        @RequestParam(defaultValue = "0") int pagina,
        @RequestParam(defaultValue = "30") int tamano,
        @RequestParam(required = false) String periodo,
        @RequestParam(required = false) String tipo,
        @RequestParam(required = false) String lider
    ) {
        return partidaService.misPartidas(jugador(request), Filtros.de(periodo, tipo, false, null, lider), pagina, tamano);
    }

    @PostMapping("/partidas")
    public ResponseEntity<Partida> registrar(HttpServletRequest request, @RequestBody NuevaPartida n) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partidaService.registrar(jugador(request), n));
    }

    @PutMapping("/partidas/{id}")
    public Partida corregir(HttpServletRequest request, @PathVariable Long id, @RequestBody NuevaPartida n) {
        return partidaService.corregir(jugador(request), id, n);
    }

    @DeleteMapping("/partidas/{id}")
    public ResponseEntity<Void> borrar(HttpServletRequest request, @PathVariable Long id) {
        partidaService.borrar(jugador(request), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/partidas/por-confirmar")
    public List<Partida> porConfirmar(HttpServletRequest request) {
        return partidaService.porConfirmar(jugador(request));
    }

    @PostMapping("/partidas/{id}/confirmar")
    public Partida confirmar(HttpServletRequest request, @PathVariable Long id) {
        return partidaService.confirmar(jugador(request), id);
    }

    @PostMapping("/partidas/{id}/rechazar")
    public Partida rechazar(HttpServletRequest request, @PathVariable Long id) {
        return partidaService.rechazar(jugador(request), id);
    }

    @GetMapping("/mis-torneos")
    public List<Torneo> misTorneos(HttpServletRequest request) {
        return torneoService.misTorneos(jugador(request).id());
    }
}
