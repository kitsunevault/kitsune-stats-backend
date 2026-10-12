package com.kitsunestats.application;

import com.kitsunestats.domain.model.Lider;
import com.kitsunestats.domain.port.out.LiderRepositoryPort;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Catálogo de líderes: público para consultar; solo el administrador da de alta o edita. */
@Service
@RequiredArgsConstructor
public class LiderService {

    public static final Set<String> COLORES = Set.of("RED", "GREEN", "BLUE", "PURPLE", "BLACK", "YELLOW");
    /** OP05-119, ST01-001, EB01-001, PRB01-001, P-001… */
    private static final Pattern CODIGO = Pattern.compile("^[A-Z0-9]{1,6}-[0-9]{3}$");

    private final LiderRepositoryPort repo;

    public List<Lider> listar(boolean soloActivos) {
        return repo.listar(soloActivos);
    }

    public Lider obtener(String codigo) {
        return repo.obtener(codigo == null ? "" : codigo.trim().toUpperCase())
            .orElseThrow(() -> new NoEncontradoException("No existe el líder " + codigo));
    }

    public Lider guardar(Lider l) {
        String codigo = l.codigo() == null ? "" : l.codigo().trim().toUpperCase();
        if (!CODIGO.matcher(codigo).matches()) {
            throw new IllegalArgumentException("El código va como en la carta, p. ej. OP05-119");
        }
        String nombre = l.nombre() == null ? "" : l.nombre().trim();
        if (nombre.length() < 2 || nombre.length() > 120) {
            throw new IllegalArgumentException("Escribe el nombre del líder");
        }
        List<String> colores = l.colores() == null ? List.of() : l.colores().stream().map(c -> c.trim().toUpperCase()).distinct().toList();
        if (colores.isEmpty() || colores.size() > 2 || !COLORES.containsAll(colores)) {
            throw new IllegalArgumentException("Elige uno o dos colores");
        }
        String set = codigo.substring(0, codigo.indexOf('-'));
        String imagen = l.imagenUrl() == null || l.imagenUrl().isBlank() ? null : l.imagenUrl().trim();
        if (imagen != null && !imagen.startsWith("https://")) {
            throw new IllegalArgumentException("La imagen debe ser una dirección https://");
        }
        return repo.guardar(new Lider(codigo, nombre, colores, set, imagen, l.activo()));
    }
}
