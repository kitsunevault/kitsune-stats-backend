package com.kitsunestats.infrastructure.client;

import com.kitsunestats.domain.model.Kitsune.Participante;
import com.kitsunestats.domain.model.Kitsune.Tienda;
import com.kitsunestats.domain.model.Kitsune.Torneo;
import com.kitsunestats.domain.port.out.TorneosTiendasPort;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** orders-service de Kitsune Vaults: /api/interno/torneos/de-usuario, una tienda a la vez (X-Tenant-Id). */
@Component
public class TorneosTiendasAdapter implements TorneosTiendasPort {

    private static final Logger log = LoggerFactory.getLogger(TorneosTiendasAdapter.class);

    private final RestClient restClient;
    private final String secreto;

    public TorneosTiendasAdapter(@Value("${kitsune.orders-url}") String ordersUrl, @Value("${kitsune.internal-secret}") String secreto) {
        this.restClient = RestClient.create(ordersUrl);
        this.secreto = secreto;
    }

    private record TorneoJugado(Long id, String nombre, LocalDate fecha, String juego, boolean cerrado, List<Participante> participantes) {
    }

    @Override
    public List<Torneo> torneosDe(Long usuarioId, Tienda tienda) {
        try {
            List<TorneoJugado> r = restClient.get()
                .uri(b -> b.path("/api/interno/torneos/de-usuario").queryParam("usuarioId", usuarioId).build())
                .header("X-Internal-Secret", secreto)
                .header("X-Tenant-Id", tienda.tenantId())
                .retrieve()
                .body(new ParameterizedTypeReference<List<TorneoJugado>>() {});
            return r == null ? List.of() : r.stream()
                .map(t -> new Torneo(tienda.tenantId(), tienda.nombre(), t.id(), t.nombre(), t.fecha(), t.juego(), t.cerrado(), t.participantes()))
                .toList();
        } catch (RestClientException ex) {
            log.warn("No se pudieron consultar los torneos de {} en {}: {}", usuarioId, tienda.tenantId(), ex.getMessage());
            return List.of();
        }
    }
}
