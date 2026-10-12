package com.kitsunestats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitsunestats.domain.port.out.KitsuneCuentasPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Admins: lista del panel de la plataforma (guardada un minuto) más el respaldo stats.admins. */
class AdminsServiceTest {

    private final KitsuneCuentasPort cuentas = mock(KitsuneCuentasPort.class);
    private Instant ahora = Instant.parse("2026-10-12T12:00:00Z");
    private final Clock reloj = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    };
    private final AdminsService admins = new AdminsService(cuentas, "7, 8", reloj);

    @Test
    void usaLaListaDelPanelYLaGuardaUnMinuto() {
        when(cuentas.adminsDeStats()).thenReturn(List.of(9L));
        assertThat(admins.esAdmin(9L)).isTrue();
        assertThat(admins.esAdmin(3L)).isFalse();
        verify(cuentas, times(1)).adminsDeStats();

        // Se quita en el panel: aplica cuando vence el minuto.
        when(cuentas.adminsDeStats()).thenReturn(List.of());
        ahora = ahora.plus(Duration.ofSeconds(30));
        assertThat(admins.esAdmin(9L)).isTrue();
        ahora = ahora.plus(Duration.ofSeconds(31));
        assertThat(admins.esAdmin(9L)).isFalse();
        verify(cuentas, times(2)).adminsDeStats();
    }

    @Test
    void elRespaldoSiempreEsAdminAunqueElPanelNoResponda() {
        when(cuentas.adminsDeStats()).thenThrow(new IllegalStateException("users-service caído"));
        assertThat(admins.esAdmin(7L)).isTrue();
        assertThat(admins.esAdmin(8L)).isTrue();
        assertThat(admins.esAdmin(9L)).isFalse();
        assertThat(admins.esAdmin(null)).isFalse();
    }

    @Test
    void siElPanelFallaSigueLaUltimaListaYReintentaPronto() {
        when(cuentas.adminsDeStats()).thenReturn(List.of(9L));
        assertThat(admins.esAdmin(9L)).isTrue();

        when(cuentas.adminsDeStats()).thenThrow(new IllegalStateException("users-service caído"));
        ahora = ahora.plus(Duration.ofMinutes(2));
        assertThat(admins.esAdmin(9L)).isTrue();

        ahora = ahora.plus(Duration.ofSeconds(16));
        admins.esAdmin(9L);
        verify(cuentas, times(3)).adminsDeStats();
    }
}
