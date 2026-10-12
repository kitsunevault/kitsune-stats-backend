package com.kitsunestats.domain.model;

import java.util.List;

/**
 * Un Líder de One Piece Card Game: es el "deck" en Kitsune Stats. codigo es el
 * de la carta (p. ej. OP05-119); colores en mayúsculas (RED, GREEN, BLUE, PURPLE,
 * BLACK, YELLOW). imagenUrl opcional.
 */
public record Lider(String codigo, String nombre, List<String> colores, String setCodigo, String imagenUrl, boolean activo) {
}
