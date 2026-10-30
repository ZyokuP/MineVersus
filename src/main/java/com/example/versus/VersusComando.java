package com.example.versus;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * /versus jugadores <rojo> <azul>      -> pelea jugador vs jugador
 * /versus equipos <rojo> <azul>        -> pelea equipo vs equipo (usa los /team de Minecraft)
 * /versus parar | ganador <rojo|azul> | estado | lista
 * /versus equipo <nombre> logo <archivo> | logo quitar | agregar <jugador> | quitar <jugador> | ver | borrar
 * /versus hud posicion <zona> | hud escala <50-200>
 * Solo operadores.
 */
@Mod.EventBusSubscriber(modid = VersusMod.MODID)
public class VersusComando {

    private static final SuggestionProvider<CommandSourceStack> SUG_EQUIPOS = (ctx, b) ->
            SharedSuggestionProvider.suggest(nombresEquipos(ctx.getSource().getServer()), b);

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        LiteralArgumentBuilder<CommandSourceStack> raiz = Commands.literal("versus")
                .requires(src -> src.hasPermission(2))
                .executes(c -> ayuda(c.getSource()));

        // ---- empezar
        raiz.then(Commands.literal("jugadores")
                .then(Commands.argument("rojo", EntityArgument.player())
                        .then(Commands.argument("azul", EntityArgument.player())
                                .executes(c -> jugadores(c.getSource(),
                                        EntityArgument.getPlayer(c, "rojo"), EntityArgument.getPlayer(c, "azul"))))));

        raiz.then(Commands.literal("equipos")
                .then(Commands.argument("rojo", StringArgumentType.word()).suggests(SUG_EQUIPOS)
                        .then(Commands.argument("azul", StringArgumentType.word()).suggests(SUG_EQUIPOS)
                                .executes(c -> equipos(c.getSource(),
                                        StringArgumentType.getString(c, "rojo"),
                                        StringArgumentType.getString(c, "azul"))))));

        raiz.then(Commands.literal("parar").executes(c -> parar(c.getSource())));
        raiz.then(Commands.literal("estado").executes(c -> {
            String t = Partida.resumen();
            c.getSource().sendSuccess(() -> Component.literal(t), false);
            return 1;
        }));
        raiz.then(Commands.literal("ganador")
                .then(Commands.literal("rojo").executes(c -> ganador(c.getSource(), 0)))
                .then(Commands.literal("azul").executes(c -> ganador(c.getSource(), 1))));
        raiz.then(Commands.literal("lista").executes(c -> lista(c.getSource())));

        // ---- equipos
        RequiredArgumentBuilder<CommandSourceStack, String> eq = Commands.argument("nombre", StringArgumentType.word())
                .suggests(SUG_EQUIPOS)
                .executes(c -> ver(c.getSource(), nom(c)));

        eq.then(Commands.literal("ver").executes(c -> ver(c.getSource(), nom(c))));

        eq.then(Commands.literal("logo")
                .then(Commands.literal("quitar").executes(c -> logoQuitar(c.getSource(), nom(c))))
                .then(Commands.argument("archivo", StringArgumentType.string())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Carpeta.sugerencias(), b))
                        .executes(c -> logo(c.getSource(), nom(c), StringArgumentType.getString(c, "archivo")))));

        eq.then(Commands.literal("agregar")
                .then(Commands.argument("jugador", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                ctx.getSource().getServer().getPlayerList().getPlayerNamesArray(), b))
                        .executes(c -> agregar(c.getSource(), nom(c), StringArgumentType.getString(c, "jugador")))));

        eq.then(Commands.literal("quitar")
                .then(Commands.argument("jugador", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            VersusDatos.EquipoDef d = VersusDatos.get(ctx.getSource().getServer()).equipos.get(nom(ctx));
                            return SharedSuggestionProvider.suggest(d == null ? Set.<String>of() : d.miembros, b);
                        })
                        .executes(c -> quitar(c.getSource(), nom(c), StringArgumentType.getString(c, "jugador")))));

        eq.then(Commands.literal("borrar").executes(c -> borrar(c.getSource(), nom(c))));

        raiz.then(Commands.literal("equipo").then(eq));

        // ---- marcador en pantalla
        LiteralArgumentBuilder<CommandSourceStack> hud = Commands.literal("hud");
        LiteralArgumentBuilder<CommandSourceStack> pos = Commands.literal("posicion");
        for (Posicion z : Posicion.values()) {
            pos.then(Commands.literal(z.nombre).executes(c -> hudPosicion(c.getSource(), z)));
        }
        hud.then(pos);
        hud.then(Commands.literal("escala")
                .then(Commands.argument("porcentaje", IntegerArgumentType.integer(50, 200))
                        .executes(c -> hudEscala(c.getSource(), IntegerArgumentType.getInteger(c, "porcentaje")))));
        raiz.then(hud);

        e.getDispatcher().register(raiz);
    }

    private static String nom(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        return StringArgumentType.getString(c, "nombre");
    }

    private static int fallo(CommandSourceStack src, String mensaje) {
        src.sendFailure(Component.literal(mensaje));
        return 0;
    }

    private static Set<String> nombresEquipos(MinecraftServer server) {
        Set<String> res = new LinkedHashSet<>();
        for (PlayerTeam t : server.getScoreboard().getPlayerTeams()) res.add(t.getName());
        res.addAll(VersusDatos.get(server).equipos.keySet());
        return res;
    }

    // ------------------------------------------------------------------ comandos

    private static int ayuda(CommandSourceStack src) {
        String t = """
                === Versus ===
                /versus jugadores <rojo> <azul>  -> pelea jugador vs jugador
                /versus equipos <rojo> <azul>  -> pelea equipo vs equipo (usa los /team de Minecraft)
                /versus parar  |  /versus estado  |  /versus ganador <rojo|azul>
                /versus lista  -> equipos guardados
                /versus equipo <nombre> logo <archivo>  (archivo de la carpeta imagenes/)
                /versus equipo <nombre> logo quitar
                /versus equipo <nombre> agregar <jugador>  |  quitar <jugador>  |  ver  |  borrar
                /versus hud posicion <zona>  |  /versus hud escala <50-200>
                Ejemplo: /team add rojos  ->  /team join rojos Steve  ->  /versus equipo rojos logo logo.png
                         /versus equipos rojos azules""";
        src.sendSuccess(() -> Component.literal(t), false);
        return 1;
    }

    private static int jugadores(CommandSourceStack src, ServerPlayer rojo, ServerPlayer azul) {
        String error = Partida.iniciarJugadores(src.getServer(), rojo, azul);
        if (error != null) return fallo(src, error);
        src.sendSuccess(() -> Component.literal("¡Pelea iniciada! ROJO: " + rojo.getGameProfile().getName()
                + "  vs  AZUL: " + azul.getGameProfile().getName()), true);
        return 1;
    }

    private static int equipos(CommandSourceStack src, String rojo, String azul) {
        String error = Partida.iniciarEquipos(src.getServer(), rojo, azul);
        if (error != null) return fallo(src, error);
        src.sendSuccess(() -> Component.literal("¡Pelea iniciada! ROJO: " + rojo + "  vs  AZUL: " + azul), true);
        return 1;
    }

    private static int parar(CommandSourceStack src) {
        Partida.detener(src.getServer());
        src.sendSuccess(() -> Component.literal("Marcador quitado de la pantalla."), true);
        return 1;
    }

    private static int ganador(CommandSourceStack src, int lado) {
        String error = Partida.forzarGanador(src.getServer(), lado);
        if (error != null) return fallo(src, error);
        src.sendSuccess(() -> Component.literal("Ganador forzado: " + (lado == 0 ? "ROJO" : "AZUL")), true);
        return 1;
    }

    private static int lista(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        VersusDatos datos = VersusDatos.get(server);
        StringBuilder sb = new StringBuilder("Equipos:");
        for (String n : nombresEquipos(server)) {
            PlayerTeam vt = server.getScoreboard().getPlayerTeam(n);
            VersusDatos.EquipoDef d = datos.equipos.get(n);
            sb.append("\n- ").append(n).append("  [")
                    .append(vt != null ? "/team: " + vt.getPlayers().size() + " jugadores" : "solo del mod")
                    .append(d != null && !d.miembros.isEmpty() ? ", +" + d.miembros.size() + " añadidos" : "")
                    .append(", logo: ").append(d != null && !d.logo.isEmpty() ? d.logo : "ninguno").append("]");
        }
        if (sb.toString().equals("Equipos:")) sb.append(" (ninguno todavía)");
        String texto = sb.toString();
        src.sendSuccess(() -> Component.literal(texto), false);
        return 1;
    }

    private static int ver(CommandSourceStack src, String nombre) {
        MinecraftServer server = src.getServer();
        VersusDatos.EquipoDef d = VersusDatos.get(server).equipos.get(nombre);
        PlayerTeam vt = server.getScoreboard().getPlayerTeam(nombre);
        if (d == null && vt == null) {
            return fallo(src, "No existe el equipo '" + nombre + "'. Créalo con /team add " + nombre
                    + " o dale un logo con /versus equipo " + nombre + " logo <archivo>.");
        }
        StringBuilder sb = new StringBuilder("Equipo ").append(nombre).append(":");
        sb.append("\nlogo: ").append(d != null && !d.logo.isEmpty() ? d.logo : "ninguno");
        sb.append("\njugadores del /team: ").append(vt == null ? "(no hay /team con este nombre)"
                : (vt.getPlayers().isEmpty() ? "(vacío)" : String.join(", ", vt.getPlayers())));
        sb.append("\njugadores añadidos con el mod: ").append(d == null || d.miembros.isEmpty()
                ? "(ninguno)" : String.join(", ", d.miembros));
        String texto = sb.toString();
        src.sendSuccess(() -> Component.literal(texto), false);
        return 1;
    }

    private static int logo(CommandSourceStack src, String nombre, String archivo) {
        Path ruta = Carpeta.resolver(archivo);
        if (ruta == null) return fallo(src, "No encuentro '" + archivo + "' en la carpeta imagenes/ del servidor.");
        String problema = Logos.problema(ruta);
        if (problema != null) return fallo(src, problema);

        VersusDatos datos = VersusDatos.get(src.getServer());
        String real = ruta.getFileName().toString();
        datos.equipos.computeIfAbsent(nombre, k -> new VersusDatos.EquipoDef()).logo = real;
        datos.setDirty();
        src.sendSuccess(() -> Component.literal("Logo de '" + nombre + "': " + real + " (se verá en la próxima pelea)."), true);
        return 1;
    }

    private static int logoQuitar(CommandSourceStack src, String nombre) {
        VersusDatos datos = VersusDatos.get(src.getServer());
        VersusDatos.EquipoDef d = datos.equipos.get(nombre);
        if (d == null || d.logo.isEmpty()) return fallo(src, "El equipo '" + nombre + "' no tiene logo.");
        d.logo = "";
        datos.setDirty();
        src.sendSuccess(() -> Component.literal("Logo de '" + nombre + "' quitado."), true);
        return 1;
    }

    private static int agregar(CommandSourceStack src, String nombre, String jugador) {
        VersusDatos datos = VersusDatos.get(src.getServer());
        VersusDatos.EquipoDef d = datos.equipos.computeIfAbsent(nombre, k -> new VersusDatos.EquipoDef());
        if (!d.miembros.add(jugador)) return fallo(src, jugador + " ya estaba en '" + nombre + "'.");
        datos.setDirty();
        src.sendSuccess(() -> Component.literal(jugador + " añadido a '" + nombre + "'."), true);
        return 1;
    }

    private static int quitar(CommandSourceStack src, String nombre, String jugador) {
        VersusDatos datos = VersusDatos.get(src.getServer());
        VersusDatos.EquipoDef d = datos.equipos.get(nombre);
        if (d == null || !d.miembros.remove(jugador)) {
            return fallo(src, jugador + " no está entre los jugadores añadidos con el mod a '" + nombre
                    + "'. (Si está en el /team, sácalo con /team leave " + jugador + ".)");
        }
        datos.setDirty();
        src.sendSuccess(() -> Component.literal(jugador + " quitado de '" + nombre + "'."), true);
        return 1;
    }

    private static int borrar(CommandSourceStack src, String nombre) {
        VersusDatos datos = VersusDatos.get(src.getServer());
        if (datos.equipos.remove(nombre) == null) return fallo(src, "El mod no tiene guardado el equipo '" + nombre + "'.");
        datos.setDirty();
        src.sendSuccess(() -> Component.literal("Equipo '" + nombre + "' borrado del mod (el /team de Minecraft no se toca)."), true);
        return 1;
    }

    private static int hudPosicion(CommandSourceStack src, Posicion z) {
        VersusDatos datos = VersusDatos.get(src.getServer());
        datos.hudZona = z;
        datos.setDirty();
        src.sendSuccess(() -> Component.literal("Posición del marcador: " + z.nombre + " (en la próxima pelea)."), true);
        return 1;
    }

    private static int hudEscala(CommandSourceStack src, int v) {
        VersusDatos datos = VersusDatos.get(src.getServer());
        datos.hudEscala = v;
        datos.setDirty();
        src.sendSuccess(() -> Component.literal("Tamaño del marcador: " + v + "% (en la próxima pelea)."), true);
        return 1;
    }
}
