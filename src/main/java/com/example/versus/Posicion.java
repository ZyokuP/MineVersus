package com.example.versus;

/** Zonas de la pantalla donde puede ir el marcador. */
public enum Posicion {
    ARRIBA_CENTRO("arriba-centro", 1, 0),
    ARRIBA_IZQUIERDA("arriba-izquierda", 0, 0),
    ARRIBA_DERECHA("arriba-derecha", 2, 0),
    IZQUIERDA("izquierda", 0, 1),
    CENTRO("centro", 1, 1),
    DERECHA("derecha", 2, 1),
    ABAJO_IZQUIERDA("abajo-izquierda", 0, 2),
    ABAJO_CENTRO("abajo-centro", 1, 2),
    ABAJO_DERECHA("abajo-derecha", 2, 2);

    public final String nombre;
    /** 0 = izquierda, 1 = centro, 2 = derecha */
    public final int horizontal;
    /** 0 = arriba, 1 = medio, 2 = abajo */
    public final int vertical;

    Posicion(String nombre, int horizontal, int vertical) {
        this.nombre = nombre;
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public static Posicion deIndice(int i) {
        Posicion[] v = values();
        return (i >= 0 && i < v.length) ? v[i] : ARRIBA_CENTRO;
    }
}
