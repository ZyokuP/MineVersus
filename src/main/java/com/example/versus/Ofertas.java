package com.example.versus;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Logos que el servidor ha ofrecido a los jugadores (para validar sus peticiones de descarga). */
public final class Ofertas {
    private Ofertas() {}

    public record Oferta(int id, Path ruta, long tam, String clave, Set<UUID> destinatarios) {}

    private static final AtomicInteger SIGUIENTE = new AtomicInteger(1);

    private static final Map<Integer, Oferta> MAPA = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Oferta> eldest) {
            return size() > 512;
        }
    };

    public static Oferta crear(Path ruta, long tam, String clave, Set<UUID> destinatarios) {
        Oferta o = new Oferta(SIGUIENTE.getAndIncrement(), ruta, tam, clave, destinatarios);
        MAPA.put(o.id(), o);
        return o;
    }

    public static Oferta obtener(int id) {
        return MAPA.get(id);
    }

    public static void limpiar() {
        MAPA.clear();
    }
}
