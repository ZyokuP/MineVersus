package com.example.versus.network;

import com.example.versus.client.ClienteVersus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Servidor -> cliente: empieza (o se retoma) una pelea. Trae los dos lados completos y los ajustes del marcador. */
public class InicioPaquete {
    public record MiembroInfo(UUID uuid, String nombre, float hp, float max, boolean eliminado) {}

    public record LadoInfo(String nombre, List<MiembroInfo> miembros, int ofertaId, String clave, long tam, String ext) {}

    private final int modo;
    private final int zona;
    private final int escala;
    private final LadoInfo a;
    private final LadoInfo b;

    public InicioPaquete(int modo, int zona, int escala, LadoInfo a, LadoInfo b) {
        this.modo = modo;
        this.zona = zona;
        this.escala = escala;
        this.a = a;
        this.b = b;
    }

    private static void escribirLado(FriendlyByteBuf buf, LadoInfo l) {
        buf.writeUtf(l.nombre(), 64);
        buf.writeVarInt(l.miembros().size());
        for (MiembroInfo m : l.miembros()) {
            buf.writeUUID(m.uuid());
            buf.writeUtf(m.nombre(), 32);
            buf.writeFloat(m.hp());
            buf.writeFloat(m.max());
            buf.writeBoolean(m.eliminado());
        }
        buf.writeVarInt(l.ofertaId());
        buf.writeUtf(l.clave(), 64);
        buf.writeLong(l.tam());
        buf.writeUtf(l.ext(), 8);
    }

    private static LadoInfo leerLado(FriendlyByteBuf buf) {
        String nombre = buf.readUtf(64);
        int n = Math.min(buf.readVarInt(), 64);
        List<MiembroInfo> ms = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ms.add(new MiembroInfo(buf.readUUID(), buf.readUtf(32), buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        }
        int oferta = buf.readVarInt();
        String clave = buf.readUtf(64);
        long tam = buf.readLong();
        String ext = buf.readUtf(8);
        return new LadoInfo(nombre, ms, oferta, clave, tam, ext);
    }

    public static void codificar(InicioPaquete m, FriendlyByteBuf b) {
        b.writeVarInt(m.modo);
        b.writeVarInt(m.zona);
        b.writeVarInt(m.escala);
        escribirLado(b, m.a);
        escribirLado(b, m.b);
    }

    public static InicioPaquete decodificar(FriendlyByteBuf b) {
        int modo = b.readVarInt();
        int zona = b.readVarInt();
        int escala = b.readVarInt();
        LadoInfo a = leerLado(b);
        LadoInfo bb = leerLado(b);
        return new InicioPaquete(modo, zona, escala, a, bb);
    }

    public static void manejar(InicioPaquete m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClienteVersus.alRecibirInicio(m.modo, m.zona, m.escala, m.a, m.b)));
        ctx.get().setPacketHandled(true);
    }
}
