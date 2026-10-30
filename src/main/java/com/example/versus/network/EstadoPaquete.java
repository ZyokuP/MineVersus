package com.example.versus.network;

import com.example.versus.client.ClienteVersus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: vida y estado de todos los participantes (primero los del lado rojo, luego los del azul). */
public class EstadoPaquete {
    private final float[] hp;
    private final float[] max;
    private final boolean[] eliminado;

    public EstadoPaquete(float[] hp, float[] max, boolean[] eliminado) {
        this.hp = hp;
        this.max = max;
        this.eliminado = eliminado;
    }

    public static void codificar(EstadoPaquete m, FriendlyByteBuf b) {
        b.writeVarInt(m.hp.length);
        for (int i = 0; i < m.hp.length; i++) {
            b.writeFloat(m.hp[i]);
            b.writeFloat(m.max[i]);
            b.writeBoolean(m.eliminado[i]);
        }
    }

    public static EstadoPaquete decodificar(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 128);
        float[] hp = new float[n];
        float[] max = new float[n];
        boolean[] el = new boolean[n];
        for (int i = 0; i < n; i++) {
            hp[i] = b.readFloat();
            max[i] = b.readFloat();
            el[i] = b.readBoolean();
        }
        return new EstadoPaquete(hp, max, el);
    }

    public static void manejar(EstadoPaquete m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClienteVersus.alRecibirEstado(m.hp, m.max, m.eliminado)));
        ctx.get().setPacketHandled(true);
    }
}
