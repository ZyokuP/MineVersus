package com.example.versus.network;

import com.example.versus.client.ClienteVersus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: quitar el marcador (con animación de salida). */
public class FinPaquete {

    public static void codificar(FinPaquete m, FriendlyByteBuf b) {
    }

    public static FinPaquete decodificar(FriendlyByteBuf b) {
        return new FinPaquete();
    }

    public static void manejar(FinPaquete m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClienteVersus::alRecibirFin));
        ctx.get().setPacketHandled(true);
    }
}
