package com.example.versus.client;

/** Funciones de animación y de color (sin clases de Minecraft, para poder probarlas solas). */
public final class Anim {
    private Anim() {}

    public static float limitar(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Cae y rebota hasta quedarse en 1. */
    public static float rebote(float p) {
        p = limitar(p);
        float n1 = 7.5625f, d1 = 2.75f;
        if (p < 1f / d1) return n1 * p * p;
        if (p < 2f / d1) {
            p -= 1.5f / d1;
            return n1 * p * p + 0.75f;
        }
        if (p < 2.5f / d1) {
            p -= 2.25f / d1;
            return n1 * p * p + 0.9375f;
        }
        p -= 2.625f / d1;
        return n1 * p * p + 0.984375f;
    }

    /** Se pasa un poco de 1 y vuelve (efecto "golpe"). */
    public static float atras(float p) {
        p = limitar(p);
        float c1 = 1.70158f, c3 = c1 + 1f;
        float q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }

    /** Arranca rápido y frena suave. */
    public static float cubica(float p) {
        p = limitar(p);
        float q = 1f - p;
        return 1f - q * q * q;
    }

    /** Arranca lento y acelera. */
    public static float acelerar(float p) {
        p = limitar(p);
        return p * p;
    }

    public static int argb(int rgb, float alfa) {
        int a = Math.max(8, Math.min(255, Math.round(alfa * 255f))); // (con alfa < 4 el texto se dibuja opaco)
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int oscurecer(int rgb, float f) {
        int r = Math.round(((rgb >> 16) & 255) * f);
        int g = Math.round(((rgb >> 8) & 255) * f);
        int b = Math.round((rgb & 255) * f);
        return (Math.min(r, 255) << 16) | (Math.min(g, 255) << 8) | Math.min(b, 255);
    }

    /** Mezcla con blanco: 0 = igual, 1 = blanco. */
    public static int aclarar(int rgb, float f) {
        return mezclar(rgb, 0xFFFFFF, f);
    }

    public static int mezclar(int c1, int c2, float t) {
        t = limitar(t);
        int r = Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }
}
