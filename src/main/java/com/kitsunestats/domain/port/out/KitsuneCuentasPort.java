package com.kitsunestats.domain.port.out;

import com.kitsunestats.domain.model.Kitsune.Cuenta;
import com.kitsunestats.domain.model.Kitsune.Tienda;
import java.util.List;
import java.util.Optional;

/** Cuentas Kitsune (users-service de Kitsune Vaults, con el secreto interno). */
public interface KitsuneCuentasPort {

    Optional<Cuenta> porUsername(String username);

    Optional<Cuenta> porId(Long id);

    /** Tiendas de Kitsune Vaults a las que se unió el jugador. */
    List<Tienda> tiendas(Long usuarioId);
}
