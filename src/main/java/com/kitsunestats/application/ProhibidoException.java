package com.kitsunestats.application;

/** 403: existe, pero quien lo pide no puede hacer eso. */
public class ProhibidoException extends RuntimeException {

    public ProhibidoException(String mensaje) {
        super(mensaje);
    }
}
