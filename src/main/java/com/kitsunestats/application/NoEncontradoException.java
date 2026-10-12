package com.kitsunestats.application;

/** 404: lo que se pidió no existe (o no es visible para quien lo pide). */
public class NoEncontradoException extends RuntimeException {

    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
