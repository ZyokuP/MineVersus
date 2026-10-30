package com.example.versus.client;

import com.example.versus.Posicion;
import com.example.versus.network.InicioPaquete;
import com.example.versus.network.ModNetwork;
import com.example.versus.network.PedirPaquete;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lado cliente del marcador versus: dos banderas (roja y azul) con animación de entrada, corazones en vivo,
 * y una transición de victoria. Todo se dibuja en el HUD y solo existe mientras hay una pelea.
 */
public class ClienteVersus {
    private static final Logger LOG = LogUtils.getLogger();
    private static final ResourceLocation ICONOS = new ResourceLocation("textures/gui/icons.png");

    private static final int ROJO = 0xE53935;
    private static final int AZUL = 0x1E6FD9;
    private static final int ORO = 0xFFD54A;

    private static final float ENTRADA_MS = 900f;
    private static final float SALIDA_MS = 600f;
    private static final float VICTORIA_MS = 7000f;
    private static final int HUECO = 40;      // espacio entre banderas (aquí va el "VS")
    private static final int VARILLA = 5;
    private static final int MUESCA = 12;     // corte en V del borde inferior
    private static final int MAX_FILAS = 8;
    private static final int MARGEN = 6;
    private static final int ANCHO_JUGADOR = 112;
    private static final int ANCHO_EQUIPO = 150;

    // ------------------------------------------------------------------ estado

    private static final class Miembro {
        final UUID uuid;
        final String nombre;
        float hp;
        float max;
        boolean eliminado;

        Miembro(InicioPaquete.MiembroInfo i) {
            this.uuid = i.uuid();
            this.nombre = i.nombre();
            this.hp = i.hp();
            this.max = i.max();
            this.eliminado = i.eliminado();
        }
    }

    private static final class Textura {
        final ResourceLocation loc;
        final int w;
        final int h;

        Textura(ResourceLocation loc, int w, int h) {
            this.loc = loc;
            this.w = w;
            this.h = h;
        }
    }

    private static final class Lado {
        final String nombre;
        final List<Miembro> miembros = new ArrayList<>();
        Textura logo;

        Lado(InicioPaquete.LadoInfo info) {
            this.nombre = info.nombre();
            for (InicioPaquete.MiembroInfo m : info.miembros()) miembros.add(new Miembro(m));
        }
    }

    private static final class Descarga {
        final Lado lado;
        final Path tmp;
        final Path destino;
        final OutputStream out;
        final long tam;
        long recibido = 0;

        Descarga(Lado lado, Path tmp, Path destino, OutputStream out, long tam) {
            this.lado = lado;
            this.tmp = tmp;
            this.destino = destino;
            this.out = out;
            this.tam = tam;
        }
    }

    private static Lado ladoA;
    private static Lado ladoB;
    private static int modo;
    private static Posicion zona = Posicion.ARRIBA_CENTRO;
    private static int escala = 100;
    private static long inicioNs;
    private static boolean saliendo;
    private static long salidaNs;
    private static int victoria = -1; // 0 = rojo, 1 = azul, 2 = empate
    private static long victoriaNs;
    private static final Map<Integer, Descarga> DESCARGAS = new HashMap<>();

    // ------------------------------------------------------------------ recepción

    public static void alRecibirInicio(int modoN, int zonaN, int escalaN,
                                       InicioPaquete.LadoInfo a, InicioPaquete.LadoInfo b) {
        limpiar();
        modo = modoN;
        zona = Posicion.deIndice(zonaN);
        escala = Math.max(50, Math.min(200, escalaN));
        ladoA = new Lado(a);
        ladoB = new Lado(b);
        inicioNs = System.nanoTime();
        saliendo = false;
        victoria = -1;
        sonido(SoundEvents.BEACON_ACTIVATE);
        pedirLogo(ladoA, a);
        pedirLogo(ladoB, b);
    }

    public static void alRecibirEstado(float[] hp, float[] max, boolean[] eliminado) {
        if (ladoA == null) return;
        int i = 0;
        for (Lado l : new Lado[]{ladoA, ladoB}) {
            for (Miembro m : l.miembros) {
                if (i >= hp.length) return;
                m.hp = hp[i];
                m.max = max[i];
                m.eliminado = eliminado[i];
                i++;
            }
        }
    }

    public static void alRecibirVictoria(int lado) {
        if (ladoA == null) return;
        victoria = lado;
        victoriaNs = System.nanoTime();
        sonido(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public static void alRecibirFin() {
        if (ladoA == null || saliendo) return;
        saliendo = true;
        salidaNs = System.nanoTime();
    }

    public static void limpiar() {
        for (Descarga d : DESCARGAS.values()) cerrar(d);
        DESCARGAS.clear();
        liberarLogo(ladoA);
        liberarLogo(ladoB);
        ladoA = null;
        ladoB = null;
        victoria = -1;
        saliendo = false;
    }

    private static void sonido(SoundEvent ev) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, 1.0f, 1.0f));
    }

    // ------------------------------------------------------------------ logos

    private static void pedirLogo(Lado lado, InicioPaquete.LadoInfo info) {
        if (info.ofertaId() == 0 || !info.ext().matches("[a-z0-9]{1,5}")) return;
        Path cache = FMLPaths.GAMEDIR.get().resolve("versus_cache");
        Path archivo = cache.resolve(info.clave() + "." + info.ext());
        try {
            Files.createDirectories(cache);
            if (Files.isRegularFile(archivo) && Files.size(archivo) == info.tam()) {
                cargarLogo(lado, archivo);
                return;
            }
            Path tmp = cache.resolve(info.clave() + "-" + info.ofertaId() + ".part");
            OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp));
            DESCARGAS.put(info.ofertaId(), new Descarga(lado, tmp, archivo, out, info.tam()));
        } catch (IOException ex) {
            LOG.warn("No se pudo preparar la descarga del logo de {}", lado.nombre, ex);
            return;
        }
        ModNetwork.CANAL.sendToServer(new PedirPaquete(info.ofertaId()));
    }

    public static void alRecibirTrozo(int ofertaId, byte[] datos, boolean fin) {
        Descarga ds = DESCARGAS.get(ofertaId);
        if (ds == null) return;
        try {
            ds.out.write(datos);
            ds.recibido += datos.length;
            if (fin) {
                ds.out.close();
                DESCARGAS.remove(ofertaId);
                if (ds.recibido != ds.tam) {
                    Files.deleteIfExists(ds.tmp);
                    LOG.warn("Descarga incompleta del logo de {}", ds.lado.nombre);
                    return;
                }
                Files.move(ds.tmp, ds.destino, StandardCopyOption.REPLACE_EXISTING);
                cargarLogo(ds.lado, ds.destino);
            }
        } catch (IOException ex) {
            LOG.warn("Error al guardar el logo de {}", ds.lado.nombre, ex);
            cerrar(ds);
            DESCARGAS.remove(ofertaId);
        }
    }

    /** Lee y reduce el logo en un hilo aparte (máx. 128 px: ahorra memoria); la textura se crea en el hilo del juego. */
    private static void cargarLogo(Lado lado, Path archivo) {
        Minecraft mc = Minecraft.getInstance();
        Util.backgroundExecutor().execute(() -> {
            try {
                Decodificador.Resultado r = Decodificador.leer(archivo, 128);
                mc.execute(() -> {
                    if (lado != ladoA && lado != ladoB) return; // la pelea ya terminó o cambió
                    liberarLogo(lado);
                    lado.logo = crearTextura(mc, r);
                });
            } catch (Exception ex) {
                LOG.warn("No se pudo cargar el logo de {}", lado.nombre, ex);
            }
        });
    }

    private static Textura crearTextura(Minecraft mc, Decodificador.Resultado r) {
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, r.w(), r.h(), false);
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                int c = r.px()[y * r.w() + x];
                int a = c >>> 24;
                int rr = (c >> 16) & 255;
                int g = (c >> 8) & 255;
                int b = c & 255;
                ni.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | rr);
            }
        }
        DynamicTexture dt = new DynamicTexture(ni);
        dt.setFilter(true, false);
        ResourceLocation loc = mc.getTextureManager().register("versus_logo", dt);
        return new Textura(loc, r.w(), r.h());
    }

    private static void liberarLogo(Lado lado) {
        if (lado == null || lado.logo == null) return;
        Minecraft.getInstance().getTextureManager().release(lado.logo.loc);
        lado.logo = null;
    }

    private static void cerrar(Descarga d) {
        try {
            d.out.close();
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(d.tmp);
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ dibujo

    public static void dibujar(GuiGraphics g, int sw, int sh) {
        if (ladoA == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return; // respeta F1

        long ahora = System.nanoTime();
        if (victoria >= 0 && !saliendo && (ahora - victoriaNs) / 1e6f > VICTORIA_MS) {
            saliendo = true;
            salidaNs = ahora;
        }
        float salida = 0f;
        if (saliendo) {
            salida = (ahora - salidaNs) / 1e6f / SALIDA_MS;
            if (salida >= 1f) {
                limpiar();
                return;
            }
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        dibujarBanderas(g, mc, sw, sh, ahora, salida);
        if (victoria >= 0) dibujarVictoria(g, mc, sw, sh, ahora);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static int anchoBandera() {
        return modo == 1 ? ANCHO_EQUIPO : ANCHO_JUGADOR;
    }

    private static int altoBandera(Lado l) {
        if (modo != 1) return 38 + 9 + 8 + MUESCA;
        int filas = Math.min(l.miembros.size(), MAX_FILAS);
        int extra = l.miembros.size() > MAX_FILAS ? 11 : 0;
        return 34 + filas * 11 + extra + 4 + MUESCA;
    }

    private static void dibujarBanderas(GuiGraphics g, Minecraft mc, int sw, int sh, long ahora, float salida) {
        Font font = mc.font;
        int w = anchoBandera();
        int h = Math.max(altoBandera(ladoA), altoBandera(ladoB));
        int bloqueW = w + HUECO + w;
        int bloqueH = VARILLA + h;
        float esc = escala / 100f;
        int rw = Math.round(bloqueW * esc);
        int rh = Math.round(bloqueH * esc);

        int px = switch (zona.horizontal) {
            case 0 -> MARGEN;
            case 2 -> sw - rw - MARGEN;
            default -> (sw - rw) / 2;
        };
        int py = switch (zona.vertical) {
            case 0 -> MARGEN;
            case 2 -> sh - rh - MARGEN;
            default -> (sh - rh) / 2;
        };
        int dir = zona.vertical == 2 ? 1 : -1; // desde dónde entran las banderas
        float ms = (ahora - inicioNs) / 1e6f;

        for (int i = 0; i < 2; i++) {
            float p = Anim.limitar((ms - i * 180f) / ENTRADA_MS);
            float alfa = Math.min(1f, p * 3f) * (1f - salida);
            if (alfa < 0.04f) continue;
            float desplaz = dir * (rh + 24) * ((1f - Anim.rebote(p)) + Anim.acelerar(salida));

            g.pose().pushPose();
            g.pose().translate(px, py + desplaz, 0);
            g.pose().scale(esc, esc, 1f);
            g.pose().translate(i == 0 ? 0 : w + HUECO, VARILLA, 0);
            dibujarBandera(g, mc, font, i == 0 ? ladoA : ladoB, w, h, i == 0 ? ROJO : AZUL, alfa);
            g.pose().popPose();
        }

        // "VS" en el centro, con un pequeño latido
        float alfaVs = Anim.limitar((ms - 300f) / 400f) * (1f - salida);
        if (alfaVs >= 0.04f) {
            float latido = 1f + 0.07f * (float) Math.sin(ms / 220f);
            g.pose().pushPose();
            g.pose().translate(px + (w + HUECO / 2f) * esc, py + (VARILLA + h / 2f) * esc, 0);
            g.pose().scale(esc * 1.7f * latido, esc * 1.7f * latido, 1f);
            Component vs = Component.literal("VS").withStyle(ChatFormatting.BOLD);
            g.drawString(font, vs, -font.width(vs) / 2, -4, Anim.argb(ORO, alfaVs), true);
            g.pose().popPose();
        }
    }

    private static void dibujarBandera(GuiGraphics g, Minecraft mc, Font font, Lado l, int w, int h, int rgb, float alfa) {
        trapo(g, w, h, rgb, alfa);
        if (modo != 1) dibujarJugador(g, mc, font, l, w, alfa);
        else dibujarEquipo(g, mc, font, l, w, rgb, alfa);
    }

    private static void dibujarJugador(GuiGraphics g, Minecraft mc, Font font, Lado l, int w, float alfa) {
        Miembro m = l.miembros.get(0);
        float a = m.eliminado ? alfa * 0.45f : alfa;

        cabeza(g, mc, m.uuid, 8, 9, 22, a);
        Component nombre = Component.literal(font.plainSubstrByWidth(m.nombre, w - 36 - 6)).withStyle(ChatFormatting.BOLD);
        g.drawString(font, nombre, 36, 12, Anim.argb(0xFFFFFF, a), true);
        if (m.eliminado) g.drawString(font, "ELIMINADO", 36, 23, Anim.argb(0xFF5555, alfa), true);
        corazones(g, 8, 38, fraccion(m), !m.eliminado, a);
    }

    private static void dibujarEquipo(GuiGraphics g, Minecraft mc, Font font, Lado l, int w, int rgb, float alfa) {
        int x0;
        if (l.logo != null) {
            logo(g, l.logo, 8, 6, 22, alfa);
            x0 = 36;
        } else {
            g.fill(8, 6, 30, 28, Anim.argb(0x000000, alfa * 0.35f));
            String ini = l.nombre.isEmpty() ? "?" : l.nombre.substring(0, 1).toUpperCase();
            g.drawString(font, ini, 8 + (22 - font.width(ini)) / 2, 13, Anim.argb(0xFFFFFF, alfa), true);
            x0 = 36;
        }
        Component nombre = Component.literal(font.plainSubstrByWidth(l.nombre, w - x0 - 8)).withStyle(ChatFormatting.BOLD);
        g.drawString(font, nombre, x0, 12, Anim.argb(0xFFFFFF, alfa), true);

        int y = 34;
        int filas = Math.min(l.miembros.size(), MAX_FILAS);
        for (int i = 0; i < filas; i++) {
            Miembro m = l.miembros.get(i);
            float a = m.eliminado ? alfa * 0.4f : alfa;
            cabeza(g, mc, m.uuid, 8, y, 8, a);
            g.drawString(font, font.plainSubstrByWidth(m.nombre, 56), 19, y, Anim.argb(m.eliminado ? 0x888888 : 0xFFFFFF, a), true);

            g.pose().pushPose();
            g.pose().translate(79, y + 1, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            corazones(g, 0, 0, fraccion(m), !m.eliminado, a);
            g.pose().popPose();
            y += 11;
        }
        if (l.miembros.size() > MAX_FILAS) {
            g.drawString(font, "+" + (l.miembros.size() - MAX_FILAS) + " más", 8, y, Anim.argb(0xCCCCCC, alfa), true);
        }
    }

    /** La tela de la bandera: degradado, borde brillante, franja de color arriba, corte en V abajo y varilla. */
    private static void trapo(GuiGraphics g, int w, int h, int rgb, float alfa) {
        for (int r = 0; r < h; r++) {
            int col = Anim.argb(Anim.mezclar(Anim.oscurecer(rgb, 0.9f), Anim.oscurecer(rgb, 0.4f), r / (float) h), alfa * 0.93f);
            if (r < h - MUESCA) {
                g.fill(0, r, w, r + 1, col);
            } else {
                int k = r - (h - MUESCA) + 1;
                int hueco = Math.round(k * (w / 2f) / MUESCA);
                if (hueco < w / 2) {
                    g.fill(0, r, w / 2 - hueco, r + 1, col);
                    g.fill(w / 2 + hueco, r, w, r + 1, col);
                }
            }
        }
        int borde = Anim.argb(Anim.aclarar(rgb, 0.35f), alfa);
        g.fill(0, 0, 1, h - MUESCA, borde);
        g.fill(w - 1, 0, w, h - MUESCA, borde);
        g.fill(0, 0, w, 3, Anim.argb(rgb, alfa));

        g.fill(-3, -4, w + 3, -1, Anim.argb(0xC9CED6, alfa));
        g.fill(-3, -4, w + 3, -3, Anim.argb(0xFFFFFF, alfa));
        int oro = Anim.argb(ORO, alfa);
        g.fill(-6, -5, -3, 0, oro);
        g.fill(w + 3, -5, w + 6, 0, oro);
    }

    private static float fraccion(Miembro m) {
        return m.max > 0f ? Math.max(0f, Math.min(1f, m.hp / m.max)) : 0f;
    }

    /** 10 corazones del juego (cada uno vale una décima parte de la vida máxima, con medios corazones). */
    private static void corazones(GuiGraphics g, int x, int y, float fraccion, boolean vivo, float alfa) {
        int medios = vivo ? Math.round(fraccion * 20f) : 0;
        if (vivo && medios == 0) medios = 1;
        g.setColor(1f, 1f, 1f, alfa);
        for (int i = 0; i < 10; i++) {
            int xx = x + i * 8;
            g.blit(ICONOS, xx, y, 16, 0, 9, 9);
            int lleno = medios - i * 2;
            if (lleno >= 2) g.blit(ICONOS, xx, y, 52, 0, 9, 9);
            else if (lleno == 1) g.blit(ICONOS, xx, y, 61, 0, 9, 9);
        }
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static void cabeza(GuiGraphics g, Minecraft mc, UUID uuid, int x, int y, int tam, float alfa) {
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(uuid) : null;
        ResourceLocation skin = info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(uuid);
        g.setColor(1f, 1f, 1f, alfa);
        PlayerFaceRenderer.draw(g, skin, x, y, tam);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static void logo(GuiGraphics g, Textura t, int x, int y, int caja, float alfa) {
        float s = Math.min(caja / (float) t.w, caja / (float) t.h);
        int dw = Math.max(1, Math.round(t.w * s));
        int dh = Math.max(1, Math.round(t.h * s));
        g.setColor(1f, 1f, 1f, alfa);
        g.blit(t.loc, x + (caja - dw) / 2, y + (caja - dh) / 2, dw, dh, 0f, 0f, t.w, t.h, t.w, t.h);
        g.setColor(1f, 1f, 1f, 1f);
    }

    // ------------------------------------------------------------------ transición de victoria

    private static void dibujarVictoria(GuiGraphics g, Minecraft mc, int sw, int sh, long ahora) {
        Font font = mc.font;
        float t = (ahora - victoriaNs) / 1e6f;
        float final_ = Anim.limitar((VICTORIA_MS - t) / 700f);
        float salidaGlobal = saliendo ? 1f - Anim.limitar((ahora - salidaNs) / 1e6f / SALIDA_MS) : 1f;
        float k = Math.min(final_, salidaGlobal);
        if (k <= 0.03f) return;

        int rgb = victoria == 0 ? ROJO : (victoria == 1 ? AZUL : ORO);

        // 1) pantalla se oscurece y destello blanco
        int oscuro = Math.round(Anim.limitar(t / 400f) * 0.55f * k * 255f) & 255;
        g.fill(0, 0, sw, sh, oscuro << 24);
        float destello = Math.max(0f, 1f - t / 380f) * 0.85f * k;
        if (destello > 0.03f) g.fill(0, 0, sw, sh, Anim.argb(0xFFFFFF, destello));

        // 2) banda central del color del ganador
        int cy = (int) (sh * 0.40f);
        int bh = Math.round(100 * Anim.cubica(t / 500f));
        if (bh > 2) {
            g.fillGradient(0, cy - bh / 2, sw, cy + bh / 2,
                    Anim.argb(Anim.oscurecer(rgb, 0.40f), 0.92f * k), Anim.argb(Anim.oscurecer(rgb, 0.80f), 0.92f * k));
            int brillo = Anim.argb(Anim.aclarar(rgb, 0.5f), k);
            g.fill(0, cy - bh / 2 - 1, sw, cy - bh / 2 + 1, brillo);
            g.fill(0, cy + bh / 2 - 1, sw, cy + bh / 2 + 1, brillo);
        }

        // 3) confeti (posiciones calculadas sin guardar nada)
        if (t > 250f) {
            int[] paleta = {rgb, ORO, 0xFFFFFF, Anim.aclarar(rgb, 0.5f)};
            float aparece = Anim.limitar((t - 250f) / 500f);
            for (int i = 0; i < 70; i++) {
                int hsh = i * 0x9E3779B1;
                hsh ^= (hsh >>> 15);
                hsh *= 0x85EBCA6B;
                hsh ^= (hsh >>> 13);
                int x = Math.floorMod(hsh, Math.max(1, sw));
                float vel = 40f + Math.floorMod(hsh >>> 8, 90);
                float y0 = Math.floorMod(hsh >>> 16, Math.max(1, sh));
                int tam = 2 + Math.floorMod(hsh >>> 24, 3);
                float y = ((y0 + (t - 250f) / 1000f * vel) % (sh + 12f)) - 6f;
                int dx = x + Math.round((float) Math.sin(t / 400f + i) * 6f);
                g.fill(dx, Math.round(y), dx + tam, Math.round(y) + tam, Anim.argb(paleta[i % 4], k * aparece));
            }
        }

        if (bh < 60) return;

        // 4) título que entra con golpe y un pequeño temblor
        float pt = Anim.limitar((t - 120f) / 650f);
        float st = 3.4f * Anim.atras(pt);
        if (st > 0.05f) {
            float sx = 0f, sy = 0f;
            if (t < 800f) {
                float amort = 1f - t / 800f;
                sx = (float) Math.sin(t * 0.09f) * amort * 4f;
                sy = (float) Math.cos(t * 0.11f) * amort * 3f;
            }
            String texto = victoria == 2 ? "¡EMPATE!" : "¡VICTORIA!";
            Component titulo = Component.literal(texto).withStyle(ChatFormatting.BOLD);
            g.pose().pushPose();
            g.pose().translate(sw / 2f + sx, cy - 24 + sy, 0);
            g.pose().scale(st, st, 1f);
            g.drawString(font, titulo, -font.width(titulo) / 2, -4, Anim.argb(0xFFFFFF, k), true);
            g.pose().popPose();
        }

        // 5) quién ganó
        float an = Anim.limitar((t - 450f) / 400f) * k;
        if (an < 0.04f) return;
        Lado gan = victoria == 0 ? ladoA : (victoria == 1 ? ladoB : null);
        String nombre = gan != null ? gan.nombre : "Nadie sobrevivió";
        float sn = 1.7f;
        Component nom = Component.literal(nombre).withStyle(ChatFormatting.BOLD);
        int icono = gan != null ? 26 : 0;
        int anchoNombre = Math.round(font.width(nom) * sn);
        int total = icono + (icono > 0 ? 8 : 0) + anchoNombre;
        int x0 = (sw - total) / 2;
        int yFila = cy + 12;

        if (gan != null) {
            if (modo == 1 && gan.logo != null) logo(g, gan.logo, x0, yFila, icono, an);
            else if (modo == 0) cabeza(g, mc, gan.miembros.get(0).uuid, x0, yFila, icono, an);
            else icono = 0;
        }
        int xNombre = x0 + (icono > 0 ? icono + 8 : 0);
        g.pose().pushPose();
        g.pose().translate(xNombre, yFila + (26 - 9 * sn) / 2f, 0);
        g.pose().scale(sn, sn, 1f);
        g.drawString(font, nom, 0, 0, Anim.argb(Anim.aclarar(rgb, 0.55f), an), true);
        g.pose().popPose();

        String sub = gan != null ? (modo == 1 ? "ha ganado el combate" : "ha ganado el combate")
                : "el combate termina en empate";
        g.drawString(font, sub, (sw - font.width(sub)) / 2, yFila + 32, Anim.argb(0xDDDDDD, an), true);
    }
}
