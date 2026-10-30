package com.example.versus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Prepara el envío de los logos de los equipos (desde la carpeta imagenes/). */
public final class Logos {
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public record Info(int ofertaId, String clave, long tam, String ext) {}

    public static final Info NINGUNO = new Info(0, "", 0, "");

    private Logos() {}

    /** null si el archivo sirve como logo; si no, el motivo. */
    public static String problema(Path ruta) {
        try {
            long tam = Files.size(ruta);
            if (tam <= 0) return "El archivo está vacío.";
            if (tam > MAX_BYTES) return "El logo pesa más de 5 MB. Usa una imagen más pequeña.";
            return null;
        } catch (IOException ex) {
            return "No se pudo leer el archivo.";
        }
    }

    /** Ofrece el logo a esos jugadores; ellos lo descargan solo si no lo tienen en caché. */
    public static Info ofrecer(Collection<ServerPlayer> jugadores, String archivo) {
        if (archivo == null || archivo.isEmpty()) return NINGUNO;
        Path ruta = Carpeta.resolver(archivo);
        if (ruta == null || problema(ruta) != null) return NINGUNO;
        long tam;
        long fecha;
        try {
            tam = Files.size(ruta);
            fecha = Files.getLastModifiedTime(ruta).toMillis();
        } catch (IOException ex) {
            return NINGUNO;
        }
        String nombre = ruta.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        String ext = punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
        if (!ext.matches("[a-z0-9]{1,5}")) return NINGUNO;

        Set<UUID> dest = new HashSet<>();
        for (ServerPlayer j : jugadores) dest.add(j.getUUID());
        String clave = clave(nombre, tam, fecha);
        Ofertas.Oferta o = Ofertas.crear(ruta, tam, clave, dest);
        return new Info(o.id(), clave, tam, ext);
    }

    private static String clave(String nombre, long tam, long fecha) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest((nombre + "|" + tam + "|" + fecha).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException ex) {
            return Long.toHexString(tam) + Long.toHexString(fecha);
        }
    }
}
