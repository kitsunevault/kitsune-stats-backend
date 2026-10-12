package com.kitsunestats.infrastructure.client;

import com.kitsunestats.domain.model.Kitsune.Cuenta;
import com.kitsunestats.domain.model.Kitsune.Tienda;
import com.kitsunestats.domain.port.out.KitsuneCuentasPort;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** users-service de Kitsune Vaults: /api/interno/kitsune/usuarios (cuentas globales, sin tienda). */
@Component
public class KitsuneCuentasAdapter implements KitsuneCuentasPort {

    private static final Logger log = LoggerFactory.getLogger(KitsuneCuentasAdapter.class);

    private final RestClient restClient;
    private final String secreto;

    public KitsuneCuentasAdapter(@Value("${kitsune.users-url}") String usersUrl, @Value("${kitsune.internal-secret}") String secreto) {
        this.restClient = RestClient.create(usersUrl);
        this.secreto = secreto;
    }

    @Override
    public Optional<Cuenta> porUsername(String username) {
        try {
            return Optional.ofNullable(restClient.get()
                .uri(b -> b.path("/api/interno/kitsune/usuarios/buscar").queryParam("username", username).build())
                .header("X-Internal-Secret", secreto)
                .retrieve()
                .body(Cuenta.class));
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new IllegalStateException("No se pudo consultar la cuenta Kitsune: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Cuenta> porId(Long id) {
        try {
            return Optional.ofNullable(restClient.get()
                .uri("/api/interno/kitsune/usuarios/{id}", id)
                .header("X-Internal-Secret", secreto)
                .retrieve()
                .body(Cuenta.class));
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new IllegalStateException("No se pudo consultar la cuenta Kitsune: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Tienda> tiendas(Long usuarioId) {
        try {
            List<Tienda> r = restClient.get()
                .uri("/api/interno/kitsune/usuarios/{id}/tiendas", usuarioId)
                .header("X-Internal-Secret", secreto)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Tienda>>() {});
            return r == null ? List.of() : r;
        } catch (RestClientException ex) {
            log.warn("No se pudieron consultar las tiendas del usuario {}: {}", usuarioId, ex.getMessage());
            return List.of();
        }
    }
}
