package com.example.versus.network;

import com.example.versus.VersusMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class ModNetwork {
    private static final String VERSION = "1";

    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VersusMod.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void registrar() {
        CANAL.registerMessage(0, InicioPaquete.class, InicioPaquete::codificar, InicioPaquete::decodificar,
                InicioPaquete::manejar, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(1, EstadoPaquete.class, EstadoPaquete::codificar, EstadoPaquete::decodificar,
                EstadoPaquete::manejar, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(2, VictoriaPaquete.class, VictoriaPaquete::codificar, VictoriaPaquete::decodificar,
                VictoriaPaquete::manejar, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(3, FinPaquete.class, FinPaquete::codificar, FinPaquete::decodificar,
                FinPaquete::manejar, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(4, TrozoPaquete.class, TrozoPaquete::codificar, TrozoPaquete::decodificar,
                TrozoPaquete::manejar, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(5, PedirPaquete.class, PedirPaquete::codificar, PedirPaquete::decodificar,
                PedirPaquete::manejar, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
