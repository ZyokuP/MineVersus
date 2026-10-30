package com.example.versus.network;

import com.example.versus.client.ClienteVersus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: un trozo del archivo de un logo. */
public class TrozoPaquete {
    private final int ofertaId;
    private final byte[] datos;
    private final boolean fin;

    public TrozoPaquete(int ofertaId, byte[] datos, boolean fin) {
        this.ofertaId = ofertaId;
        this.datos = datos;
        this.fin = fin;
    }

    public static void codificar(TrozoPaquete m, FriendlyByteBuf b) {
        b.writeInt(m.ofertaId);
        b.writeBoolean(m.fin);
        b.writeByteArray(m.datos);
    }

    public static TrozoPaquete decodificar(FriendlyByteBuf b) {
        int id = b.readInt();
        boolean fin = b.readBoolean();
        byte[] datos = b.readByteArray();
        return new TrozoPaquete(id, datos, fin);
    }

    public static void manejar(TrozoPaquete m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClienteVersus.alRecibirTrozo(m.ofertaId, m.datos, m.fin)));
        ctx.get().setPacketHandled(true);
    }
}
