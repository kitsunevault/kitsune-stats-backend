package com.kitsunestats.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida el JWT de Kitsune Stats (lo emite users-service de Kitsune Vaults en el login único, con el
 * secreto de apps y el claim app=stats). Un token de tienda o de consola no sirve aquí: está firmado
 * con otro secreto. Si es válido deja usuarioId, username y si es administrador en el request.
 *
 * Sin token solo pasan las consultas públicas (líderes y stats globales); lo demás de /api da 401 y
 * /api/admin, 403 si no es administrador (stats.admins).
 */
@Component
public class JwtStatsFilter extends OncePerRequestFilter {

    private final SecretKey key;
    private final Set<Long> admins;

    public JwtStatsFilter(@Value("${jwt.apps-secret}") String secreto, @Value("${stats.admins:}") String admins) {
        this.key = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.admins = admins.isBlank() ? Set.of() : java.util.Arrays.stream(admins.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        // Preflight de CORS (solo en desarrollo): no trae token; lo contesta CorsConfig.
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(auth.substring(7)).getPayload();
                Long usuarioId = c.get("usuarioId", Long.class);
                if ("stats".equals(c.get("app", String.class)) && usuarioId != null) {
                    request.setAttribute("usuarioId", usuarioId);
                    request.setAttribute("username", c.getSubject());
                    request.setAttribute("admin", admins.contains(usuarioId));
                }
            } catch (JwtException | IllegalArgumentException ex) {
                // Token vencido o ajeno: como si no hubiera; abajo se decide si la ruta lo pide.
            }
        }
        boolean conSesion = request.getAttribute("usuarioId") != null;
        String ruta = request.getRequestURI();
        if (!conSesion && !esPublica(request.getMethod(), ruta)) {
            responder(response, HttpServletResponse.SC_UNAUTHORIZED, "Inicia sesión con tu cuenta Kitsune");
            return;
        }
        if (ruta.startsWith("/api/admin/") && !Boolean.TRUE.equals(request.getAttribute("admin"))) {
            responder(response, HttpServletResponse.SC_FORBIDDEN, "Solo para el administrador de Kitsune Stats");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Líderes y stats globales (y la de cada líder) se ven sin cuenta. */
    static boolean esPublica(String metodo, String ruta) {
        return "GET".equals(metodo) && (ruta.equals("/api/lideres") || ruta.startsWith("/api/lideres/") || ruta.equals("/api/meta"));
    }

    private static void responder(HttpServletResponse response, int estatus, String mensaje) throws IOException {
        response.setStatus(estatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + mensaje + "\"}");
    }
}
