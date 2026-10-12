package com.kitsunestats.application;

/** 409: choca con algo que ya existe (p. ej. esa ronda del torneo ya está registrada). */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
