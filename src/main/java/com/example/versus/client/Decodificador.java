package com.example.versus.client;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/** Lee png/jpg/gif/bmp y, si hace falta, lo reduce con promedio de área (sin usar Graphics2D). */
public final class Decodificador {
    private static final long MAX_PIXELES = 40_000_000L;

    private Decodificador() {}

    public record Resultado(int[] px, int w, int h) {}

    public static Resultado leer(Path archivo, int anchoMax) throws IOException {
        BufferedImage img;
        try (ImageInputStream iis = ImageIO.createImageInputStream(archivo.toFile())) {
            if (iis == null) throw new IOException("No se pudo abrir el archivo");
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) throw new IOException("Formato de imagen no soportado");
            ImageReader r = it.next();
            try {
                r.setInput(iis, true, true);
                long w0 = r.getWidth(0);
                long h0 = r.getHeight(0);
                if (w0 * h0 > MAX_PIXELES) throw new IOException("Imagen demasiado grande");
                img = r.read(0);
            } finally {
                r.dispose();
            }
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        int lado = Math.max(w, h);
        if (lado > anchoMax) {
            double f = (double) anchoMax / lado;
            int nw = Math.max(1, (int) Math.round(w * f));
            int nh = Math.max(1, (int) Math.round(h * f));
            px = reducir(px, w, h, nw, nh);
            w = nw;
            h = nh;
        }
        return new Resultado(px, w, h);
    }

    /** Promedio de área ponderado por transparencia (evita bordes oscuros en logos con fondo transparente). */
    static int[] reducir(int[] s, int sw, int sh, int dw, int dh) {
        int[] d = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            int y0 = (int) ((long) y * sh / dh);
            int y1 = Math.max(y0 + 1, (int) ((long) (y + 1) * sh / dh));
            for (int x = 0; x < dw; x++) {
                int x0 = (int) ((long) x * sw / dw);
                int x1 = Math.max(x0 + 1, (int) ((long) (x + 1) * sw / dw));
                long a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (int yy = y0; yy < y1 && yy < sh; yy++) {
                    for (int xx = x0; xx < x1 && xx < sw; xx++) {
                        int c = s[yy * sw + xx];
                        int ca = c >>> 24;
                        a += ca;
                        r += (long) ((c >> 16) & 255) * ca;
                        g += (long) ((c >> 8) & 255) * ca;
                        b += (long) (c & 255) * ca;
                        n++;
                    }
                }
                int oa = n == 0 ? 0 : (int) (a / n);
                int or = 0, og = 0, ob = 0;
                if (a > 0) {
                    or = (int) (r / a);
                    og = (int) (g / a);
                    ob = (int) (b / a);
                }
                d[y * dw + x] = (oa << 24) | (or << 16) | (og << 8) | ob;
            }
        }
        return d;
    }
}
