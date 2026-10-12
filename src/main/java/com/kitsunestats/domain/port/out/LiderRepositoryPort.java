package com.kitsunestats.domain.port.out;

import com.kitsunestats.domain.model.Lider;
import java.util.List;
import java.util.Optional;

public interface LiderRepositoryPort {

    /** Ordenados por set y código. */
    List<Lider> listar(boolean soloActivos);

    Optional<Lider> obtener(String codigo);

    /** Alta o edición (por código). */
    Lider guardar(Lider lider);
}
