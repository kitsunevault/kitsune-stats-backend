package com.kitsunestats.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtStatsFilterTest {

    private static final String APPS = "secreto-de-apps-para-pruebas-0123456789abcdef0123456789abcdef";
    private static final String TIENDAS = "secreto-de-tiendas-para-pruebas-0123456789abcdef0123456789abcd";
    private final JwtStatsFilter filtro = new JwtStatsFilter(APPS, "7");

    private static String token(String secreto, Long usuarioId, String app) {
        var b = Jwts.builder().subject("ana").claim("usuarioId", usuarioId).issuedAt(new Date())
            .expiration(Date.from(Instant.now().plusSeconds(600)));
        if (app != null) {
            b.claim("app", app);
        } else {
            b.claim("tenantId", "magiccauldron");
        }
        return b.signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private MockHttpServletResponse correr(String metodo, String ruta, String token, MockHttpServletRequest[] salida) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, ruta);
        req.setRequestURI(ruta);
        if (token != null) {
            req.addHeader("Authorization", "Bearer " + token);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(req, res, new MockFilterChain());
        if (salida != null) {
            salida[0] = req;
        }
        return res;
    }

    @Test
    void tokenDeStatsPasaYDejaAlJugador() throws Exception {
        MockHttpServletRequest[] req = new MockHttpServletRequest[1];
        MockHttpServletResponse res = correr("GET", "/api/mis-stats", token(APPS, 3L, "stats"), req);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(req[0].getAttribute("usuarioId")).isEqualTo(3L);
        assertThat(req[0].getAttribute("admin")).isEqualTo(false);
    }

    @Test
    void tokenDeTiendaNoSirve() throws Exception {
        assertThat(correr("GET", "/api/mis-stats", token(TIENDAS, 3L, null), null).getStatus()).isEqualTo(401);
        // Firmado con el secreto correcto pero de otra app: tampoco.
        assertThat(correr("GET", "/api/mis-stats", token(APPS, 3L, "otra"), null).getStatus()).isEqualTo(401);
    }

    @Test
    void lasStatsGlobalesSonPublicas() throws Exception {
        assertThat(correr("GET", "/api/meta", null, null).getStatus()).isEqualTo(200);
        assertThat(correr("GET", "/api/lideres", null, null).getStatus()).isEqualTo(200);
        assertThat(correr("GET", "/api/lideres/OP05-119/stats", null, null).getStatus()).isEqualTo(200);
        assertThat(correr("POST", "/api/partidas", null, null).getStatus()).isEqualTo(401);
    }

    @Test
    void adminSoloParaLosConfigurados() throws Exception {
        assertThat(correr("GET", "/api/admin/partidas", token(APPS, 3L, "stats"), null).getStatus()).isEqualTo(403);
        assertThat(correr("GET", "/api/admin/partidas", token(APPS, 7L, "stats"), null).getStatus()).isEqualTo(200);
    }
}
