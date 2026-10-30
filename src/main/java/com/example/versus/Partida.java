package com.example.versus;

import com.example.versus.network.EstadoPaquete;
import com.example.versus.network.FinPaquete;
import com.example.versus.network.InicioPaquete;
import com.example.versus.network.ModNetwork;
import com.example.versus.network.VictoriaPaquete;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * La pelea en curso (solo existe una a la vez y vive en memoria). Es la única lógica que trabaja en el servidor:
 *  - revisa la vida de los participantes cada 5 ticks (0,25 s) y solo manda datos si algo cambió;
 *  - detecta muertes y desconexiones al instante;
 *  - cuando un lado se queda sin nadie vivo, anuncia al ganador.
 * Si no hay pelea, no hace nada.
 */
@Mod.EventBusSubscriber(modid = VersusMod.MODID)
public final class Partida {
    private Partida() {}

    public static final class Miembro {
        public final UUID uuid;
        public final String nombre;
        public boolean eliminado = false;
        public float hp;
        public float max;

        Miembro(ServerPlayer p) {
            this.uuid = p.getUUID();
            this.nombre = p.getGameProfile().getName();
            this.hp = p.getHealth();
            this.max = p.getMaxHealth();
        }
    }

    public static final class Lado {
        public final String nombre;
        public final String logo;
        public final List<Miembro> miembros = new ArrayList<>();

        Lado(String nombre, String logo) {
            this.nombre = nombre;
            this.logo = logo;
        }

        boolean vivo() {
            for (Miembro m : miembros) if (!m.eliminado) return true;
            return false;
        }

        int vivos() {
            int n = 0;
            for (Miembro m : miembros) if (!m.eliminado) n++;
            return n;
        }
    }

    private static Lado a;
    private static Lado b;
    private static int modo; // 0 = jugador vs jugador, 1 = equipo vs equipo
    private static boolean activa = false;
    private static int contador = 0;

    public static boolean activa() {
        return activa;
    }

    // ------------------------------------------------------------------ iniciar / terminar

    /** Devuelve null si todo bien, o el motivo del error. */
    public static String iniciarJugadores(MinecraftServer server, ServerPlayer rojo, ServerPlayer azul) {
        if (rojo.getUUID().equals(azul.getUUID())) return "Elige dos jugadores distintos.";
        if (rojo.isSpectator() || azul.isSpectator()) return "Los jugadores no pueden estar en modo espectador.";
        Lado la = new Lado(rojo.getGameProfile().getName(), "");
        la.miembros.add(new Miembro(rojo));
        Lado lb = new Lado(azul.getGameProfile().getName(), "");
        lb.miembros.add(new Miembro(azul));
        arrancar(server, 0, la, lb);
        return null;
    }

    /** Devuelve null si todo bien, o el motivo del error. */
    public static String iniciarEquipos(MinecraftServer server, String nombreRojo, String nombreAzul) {
        if (nombreRojo.equals(nombreAzul)) return "Elige dos equipos distintos.";
        VersusDatos datos = VersusDatos.get(server);
        if (!existe(server, datos, nombreRojo)) return mensajeNoExiste(nombreRojo);
        if (!existe(server, datos, nombreAzul)) return mensajeNoExiste(nombreAzul);

        Lado la = ladoDeEquipo(server, datos, nombreRojo);
        Lado lb = ladoDeEquipo(server, datos, nombreAzul);
        if (la.miembros.isEmpty()) return "El equipo '" + nombreRojo + "' no tiene jugadores conectados.";
        if (lb.miembros.isEmpty()) return "El equipo '" + nombreAzul + "' no tiene jugadores conectados.";

        Set<UUID> rojos = new HashSet<>();
        for (Miembro m : la.miembros) rojos.add(m.uuid);
        for (Miembro m : lb.miembros) {
            if (rojos.contains(m.uuid)) return m.nombre + " está en los dos equipos.";
        }
        arrancar(server, 1, la, lb);
        return null;
    }

    private static String mensajeNoExiste(String nombre) {
        return "No existe el equipo '" + nombre + "'. Créalo con /team add " + nombre
                + " (y /team join) o con /versus equipo " + nombre + " agregar <jugador>.";
    }

    private static boolean existe(MinecraftServer server, VersusDatos datos, String nombre) {
        return server.getScoreboard().getPlayerTeam(nombre) != null || datos.equipos.containsKey(nombre);
    }

    /** Jugadores del equipo = los del /team de Minecraft + los que se añadieron con /versus equipo ... agregar. */
    private static Lado ladoDeEquipo(MinecraftServer server, VersusDatos datos, String nombre) {
        Set<String> nombres = new LinkedHashSet<>();
        PlayerTeam vt = server.getScoreboard().getPlayerTeam(nombre);
        if (vt != null) nombres.addAll(vt.getPlayers());
        VersusDatos.EquipoDef def = datos.equipos.get(nombre);
        if (def != null) nombres.addAll(def.miembros);

        String visible = vt != null ? vt.getDisplayName().getString() : nombre;
        if (visible.length() > 32) visible = visible.substring(0, 32);
        Lado lado = new Lado(visible, def != null ? def.logo : "");

        Set<UUID> vistos = new HashSet<>();
        for (String n : nombres) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(n);
            if (p != null && !p.isSpectator() && vistos.add(p.getUUID())) lado.miembros.add(new Miembro(p));
        }
        return lado;
    }

    private static void arrancar(MinecraftServer server, int modoNuevo, Lado la, Lado lb) {
        a = la;
        b = lb;
        modo = modoNuevo;
        activa = true;
        contador = 0;
        enviarInicio(server.getPlayerList().getPlayers());
    }

    public static void detener(MinecraftServer server) {
        activa = false;
        a = null;
        b = null;
        ModNetwork.CANAL.send(PacketDistributor.ALL.noArg(), new FinPaquete());
    }

    /** lado: 0 = rojo, 1 = azul. Devuelve null si todo bien. */
    public static String forzarGanador(MinecraftServer server, int lado) {
        if (!activa) return "No hay ninguna pelea en curso.";
        activa = false;
        ModNetwork.CANAL.send(PacketDistributor.ALL.noArg(), new VictoriaPaquete(lado));
        return null;
    }

    public static String resumen() {
        if (!activa) return "No hay ninguna pelea en curso.";
        return (modo == 0 ? "Jugador vs jugador" : "Equipo vs equipo") + ":\n"
                + "ROJO - " + a.nombre + ": " + a.vivos() + "/" + a.miembros.size() + " con vida\n"
                + "AZUL - " + b.nombre + ": " + b.vivos() + "/" + b.miembros.size() + " con vida";
    }

    // ------------------------------------------------------------------ envío

    private static InicioPaquete.LadoInfo info(Lado l, Logos.Info logo) {
        List<InicioPaquete.MiembroInfo> ms = new ArrayList<>();
        for (Miembro m : l.miembros) {
            ms.add(new InicioPaquete.MiembroInfo(m.uuid, m.nombre, m.hp, m.max, m.eliminado));
        }
        return new InicioPaquete.LadoInfo(l.nombre, ms, logo.ofertaId(), logo.clave(), logo.tam(), logo.ext());
    }

    private static void enviarInicio(Collection<ServerPlayer> jugadores) {
        if (jugadores.isEmpty() || a == null) return;
        MinecraftServer server = jugadores.iterator().next().getServer();
        if (server == null) return;
        VersusDatos datos = VersusDatos.get(server);

        Logos.Info la = Logos.ofrecer(jugadores, a.logo);
        Logos.Info lb = Logos.ofrecer(jugadores, b.logo);
        InicioPaquete p = new InicioPaquete(modo, datos.hudZona.ordinal(), datos.hudEscala, info(a, la), info(b, lb));
        for (ServerPlayer j : jugadores) {
            ModNetwork.CANAL.send(PacketDistributor.PLAYER.with(() -> j), p);
        }
    }

    private static void enviarEstado() {
        int n = a.miembros.size() + b.miembros.size();
        float[] hp = new float[n];
        float[] max = new float[n];
        boolean[] el = new boolean[n];
        int i = 0;
        for (Lado l : new Lado[]{a, b}) {
            for (Miembro m : l.miembros) {
                hp[i] = m.hp;
                max[i] = m.max;
                el[i] = m.eliminado;
                i++;
            }
        }
        ModNetwork.CANAL.send(PacketDistributor.ALL.noArg(), new EstadoPaquete(hp, max, el));
    }

    private static Miembro buscar(UUID uuid) {
        for (Lado l : new Lado[]{a, b}) {
            for (Miembro m : l.miembros) if (m.uuid.equals(uuid)) return m;
        }
        return null;
    }

    private static void eliminar(Miembro m) {
        m.eliminado = true;
        m.hp = 0f;
    }

    private static void comprobarVictoria() {
        boolean va = a.vivo();
        boolean vb = b.vivo();
        if (va && vb) return;
        int ganador = va ? 0 : (vb ? 1 : 2); // 2 = empate
        activa = false;
        ModNetwork.CANAL.send(PacketDistributor.ALL.noArg(), new VictoriaPaquete(ganador));
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public static void alTick(TickEvent.ServerTickEvent e) {
        if (!activa || e.phase != TickEvent.Phase.END) return;
        if (++contador % 5 != 0) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        boolean cambio = false;
        for (Lado l : new Lado[]{a, b}) {
            for (Miembro m : l.miembros) {
                if (m.eliminado) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(m.uuid);
                if (p == null || p.isSpectator()) {
                    eliminar(m);
                    cambio = true;
                    continue;
                }
                float hp = p.getHealth();
                float max = p.getMaxHealth();
                if (hp != m.hp || max != m.max) {
                    m.hp = hp;
                    m.max = max;
                    cambio = true;
                }
            }
        }
        if (cambio) {
            enviarEstado();
            comprobarVictoria();
        }
    }

    /** Al final del todo (MONITOR) y solo si nadie canceló la muerte (por ejemplo un tótem). */
    @SubscribeEvent(priority = EventPriority.MONITOR)
    public static void alMorir(LivingDeathEvent e) {
        if (!activa || !(e.getEntity() instanceof ServerPlayer p)) return;
        Miembro m = buscar(p.getUUID());
        if (m == null || m.eliminado) return;
        eliminar(m);
        enviarEstado();
        comprobarVictoria();
    }

    @SubscribeEvent
    public static void alSalir(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!activa) return;
        Miembro m = buscar(e.getEntity().getUUID());
        if (m == null || m.eliminado) return;
        eliminar(m);
        enviarEstado();
        comprobarVictoria();
    }

    /** Quien entra a mitad de una pelea recibe el marcador tal como está. */
    @SubscribeEvent
    public static void alEntrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (!activa || !(e.getEntity() instanceof ServerPlayer p)) return;
        enviarInicio(List.of(p));
    }

    @SubscribeEvent
    public static void alCerrarServidor(ServerStoppedEvent e) {
        activa = false;
        a = null;
        b = null;
        Transferencias.cancelarTodo();
        Ofertas.limpiar();
    }
}
