package com.kitsunestats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Kitsune Stats: los jugadores registran sus partidas (su líder, el del rival,
 * quién empezó y quién ganó) y se ven stats personales y globales de qué líder
 * le gana a cuál. Usa la cuenta Kitsune (login único de Kitsune Vaults).
 */
@SpringBootApplication
public class KitsuneStatsApplication {

    public static void main(String[] args) {
        SpringApplication.run(KitsuneStatsApplication.class, args);
    }
}
