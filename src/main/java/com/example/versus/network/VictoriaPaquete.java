package com.example.versus.network;

import com.example.versus.client.ClienteVersus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: terminó la pelea. lado: 0 = rojo, 1 = azul, 2 = empate. */
public class VictoriaPaquete {
    private final int lado;

    public VictoriaPaquete(int lado) {
        this.lado = lado;
    }

    public static void codificar(VictoriaPaquete m, FriendlyByteBuf b) {
        b.writeVarInt(m.lado);
    }

    public static VictoriaPaquete decodificar(FriendlyByteBuf b) {
        return new VictoriaPaquete(b.readVarInt());
    }

    public static void manejar(VictoriaPaquete m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClienteVersus.alRecibirVictoria(m.lado)));
        ctx.get().setPacketHandled(true);
    }
}
