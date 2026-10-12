package com.kitsunestats.application;

import com.kitsunestats.domain.port.out.KitsuneCuentasPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Quién administra Kitsune Stats. La lista se maneja en el panel de la plataforma de
 * Kitsune Vaults (sección Apps) y se consulta a users-service, guardada un minuto: un
 * cambio en el panel aplica en menos de eso, sin volver a iniciar sesión.
 *
 * stats.admins (STATS_ADMINS en el servidor) es el respaldo de emergencia: esas cuentas
 * son admin siempre, aunque users-service no responda. Si no responde, se sigue usando
 * la última lista que llegó.
 */
@Service
public class AdminsService {

    static final Duration VIGENCIA = Duration.ofMinutes(1);
    /** Tras un fallo se reintenta antes, para no quedarse un minuto con la lista vieja. */
    static final Duration REINTENTO = Duration.ofSeconds(15);

    private static final Logger log = LoggerFactory.getLogger(AdminsService.class);

    private final KitsuneCuentasPort cuentas;
    private final Set<Long> respaldo;
    private final Clock clock;

    private volatile Set<Long> lista = Set.of();
    private volatile Instant vence = Instant.EPOCH;

    @Autowired
    public AdminsService(KitsuneCuentasPort cuentas, @Value("${stats.admins:}") String respaldo) {
        this(cuentas, respaldo, Clock.systemUTC());
    }

    AdminsService(KitsuneCuentasPort cuentas, String respaldo, Clock clock) {
        this.cuentas = cuentas;
        this.clock = clock;
        this.respaldo = respaldo == null || respaldo.isBlank() ? Set.of() : Arrays.stream(respaldo.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    public boolean esAdmin(Long usuarioId) {
        return usuarioId != null && (respaldo.contains(usuarioId) || vigente().contains(usuarioId));
    }

    private Set<Long> vigente() {
        Instant ahora = clock.instant();
        if (ahora.isBefore(vence)) {
            return lista;
        }
        synchronized (this) {
            if (ahora.isBefore(vence)) {
                return lista;
            }
            try {
                lista = Set.copyOf(cuentas.adminsDeStats());
                vence = ahora.plus(VIGENCIA);
            } catch (RuntimeException ex) {
                log.warn("No se pudo consultar los admins de Kitsune Stats; sigue la última lista: {}", ex.getMessage());
                vence = ahora.plus(REINTENTO);
            }
            return lista;
        }
    }
}
