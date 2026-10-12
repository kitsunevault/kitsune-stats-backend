package com.kitsunestats.infrastructure.web;

import com.kitsunestats.application.LiderService;
import com.kitsunestats.application.StatsService;
import com.kitsunestats.domain.model.Lider;
import com.kitsunestats.domain.model.Stats.DetalleLider;
import com.kitsunestats.domain.model.Stats.Meta;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lo que se ve sin cuenta: el catálogo de líderes y las stats globales (el meta). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PublicoController {

    private final LiderService liderService;
    private final StatsService statsService;

    @GetMapping("/lideres")
    public List<Lider> lideres(@RequestParam(defaultValue = "false") boolean todos) {
        return liderService.listar(!todos);
    }

    @GetMapping("/meta")
    public Meta meta(
        @RequestParam(required = false) String periodo,
        @RequestParam(required = false) String tipo,
        @RequestParam(required = false) Boolean verificadas,
        @RequestParam(required = false) String tienda
    ) {
        return statsService.meta(Filtros.de(periodo, tipo, verificadas, tienda, null));
    }

    @GetMapping("/lideres/{codigo}/stats")
    public DetalleLider lider(
        @PathVariable String codigo,
        @RequestParam(required = false) String periodo,
        @RequestParam(required = false) String tipo,
        @RequestParam(required = false) Boolean verificadas,
        @RequestParam(required = false) String tienda
    ) {
        return statsService.detalleLider(codigo, Filtros.de(periodo, tipo, verificadas, tienda, null));
    }
}
