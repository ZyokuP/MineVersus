package com.example.versus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Guarda en el mundo los equipos del mod (logo y jugadores extra) y los ajustes del marcador. */
public class VersusDatos extends SavedData {
    private static final String NOMBRE = "versus_datos";

    /** Datos que el mod guarda de un equipo. Los jugadores del /team de Minecraft se leen aparte, en vivo. */
    public static final class EquipoDef {
        public String logo = "";
        public final Set<String> miembros = new LinkedHashSet<>();
    }

    public final Map<String, EquipoDef> equipos = new LinkedHashMap<>();
    public Posicion hudZona = Posicion.ARRIBA_CENTRO;
    public int hudEscala = 100;

    public static VersusDatos get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(VersusDatos::cargar, VersusDatos::new, NOMBRE);
    }

    public static VersusDatos cargar(CompoundTag tag) {
        VersusDatos d = new VersusDatos();
        d.hudZona = Posicion.deIndice(tag.getInt("hudZona"));
        d.hudEscala = tag.contains("hudEscala") ? Math.max(50, Math.min(200, tag.getInt("hudEscala"))) : 100;

        ListTag lista = tag.getList("equipos", Tag.TAG_COMPOUND);
        for (int i = 0; i < lista.size(); i++) {
            CompoundTag t = lista.getCompound(i);
            EquipoDef e = new EquipoDef();
            e.logo = t.getString("logo");
            ListTag ms = t.getList("miembros", Tag.TAG_STRING);
            for (int j = 0; j < ms.size(); j++) e.miembros.add(ms.getString(j));
            d.equipos.put(t.getString("nombre"), e);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("hudZona", hudZona.ordinal());
        tag.putInt("hudEscala", hudEscala);
        ListTag lista = new ListTag();
        for (Map.Entry<String, EquipoDef> en : equipos.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("nombre", en.getKey());
            t.putString("logo", en.getValue().logo);
            ListTag ms = new ListTag();
            for (String m : en.getValue().miembros) ms.add(StringTag.valueOf(m));
            t.put("miembros", ms);
            lista.add(t);
        }
        tag.put("equipos", lista);
        return tag;
    }
}
