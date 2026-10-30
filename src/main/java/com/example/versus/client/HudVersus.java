package com.example.versus.client;

import com.example.versus.VersusMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registro del HUD (justo debajo del chat: el chat siempre queda por encima) y limpieza al salir. */
public class HudVersus {

    @Mod.EventBusSubscriber(modid = VersusMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class Registro {
        @SubscribeEvent
        public static void hud(RegisterGuiOverlaysEvent e) {
            e.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(), "versus",
                    (gui, g, parcial, ancho, alto) -> ClienteVersus.dibujar(g, ancho, alto));
        }
    }

    @Mod.EventBusSubscriber(modid = VersusMod.MODID, value = Dist.CLIENT)
    public static class Juego {
        @SubscribeEvent
        public static void alSalir(ClientPlayerNetworkEvent.LoggingOut e) {
            ClienteVersus.limpiar();
        }
    }
}
