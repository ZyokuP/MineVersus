package com.example.versus;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Los logos salen de la MISMA carpeta "imagenes" del mod de imágenes (raíz del servidor). */
public final class Carpeta {
    public static final String[] EXTENSIONES = {"png", "jpg", "jpeg", "gif", "bmp"};

    private Carpeta() {}

    public static Path dir() {
        return FMLPaths.GAMEDIR.get().resolve("imagenes");
    }

    public static void crear() {
        try {
            Files.createDirectories(dir());
        } catch (IOException ignored) {
        }
    }

    private static boolean valida(String nombreArchivo) {
        String n = nombreArchivo.toLowerCase(Locale.ROOT);
        for (String e : EXTENSIONES) {
            if (n.endsWith("." + e)) return true;
        }
        return false;
    }

    public static List<String> archivos() {
        List<String> res = new ArrayList<>();
        Path d = dir();
        if (!Files.isDirectory(d)) return res;
        try (Stream<Path> s = Files.list(d)) {
            s.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(Carpeta::valida)
                    .sorted()
                    .forEach(res::add);
        } catch (IOException ignored) {
        }
        return res;
    }

    public static List<String> sugerencias() {
        List<String> res = new ArrayList<>();
        for (String n : archivos()) {
            res.add(n.contains(" ") ? "\"" + n + "\"" : n);
        }
        return res;
    }

    /** Busca el archivo (con o sin extensión). Solo dentro de la carpeta imagenes. */
    public static Path resolver(String nombre) {
        String n = nombre.trim();
        if (n.isEmpty() || n.contains("/") || n.contains("\\") || n.contains("..")) return null;
        try {
            Path base = dir().toAbsolutePath().normalize();
            Path p = base.resolve(n).normalize();
            if (!Files.isRegularFile(p) || !valida(n)) {
                p = null;
                for (String e : EXTENSIONES) {
                    Path c = base.resolve(n + "." + e).normalize();
                    if (Files.isRegularFile(c)) {
                        p = c;
                        break;
                    }
                }
            }
            if (p == null || !p.startsWith(base)) return null;
            if (!Files.isRegularFile(p) || !valida(p.getFileName().toString())) return null;
            return p;
        } catch (InvalidPathException ex) {
            return null;
        }
    }
}
