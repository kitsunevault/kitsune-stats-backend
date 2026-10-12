package com.kitsunestats.infrastructure.web;

import com.kitsunestats.application.LiderService;
import com.kitsunestats.application.PartidaService;
import com.kitsunestats.domain.model.Lider;
import com.kitsunestats.domain.model.Partida;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrador de Kitsune Stats (stats.admins): líderes nuevos y ocultar partidas que deforman las globales. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final LiderService liderService;
    private final PartidaService partidaService;

    public record OcultarRequest(boolean oculta) {
    }

    @PutMapping("/lideres")
    public Lider guardarLider(@RequestBody Lider lider) {
        return liderService.guardar(lider);
    }

    @GetMapping("/partidas")
    public List<Partida> partidas(@RequestParam(defaultValue = "0") int pagina) {
        return partidaService.recientes(pagina);
    }

    @PostMapping("/partidas/{id}/ocultar")
    public ResponseEntity<Void> ocultar(@PathVariable Long id, @RequestBody OcultarRequest r) {
        partidaService.ocultar(id, r.oculta());
        return ResponseEntity.noContent().build();
    }
}
