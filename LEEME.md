# Versus (Forge 1.20.1)

Marcador de combates en pantalla: dos banderas (roja y azul) que entran con animación, con la vida de cada
jugador en corazones, y una transición épica cuando alguien gana. Comandos solo para operadores.

## Pelea jugador vs jugador
```
/versus jugadores Steve Alex      (Steve = bandera roja, Alex = bandera azul)
```
Cada bandera muestra la cabeza, el nick y los corazones. Gana quien queda vivo.

## Pelea equipo vs equipo (usa los /team de Minecraft)
```
/team add rojos
/team join rojos Steve
/team join rojos Notch
/team add azules
/team join azules Alex
/versus equipos rojos azules
```
Cada bandera muestra el logo y el nombre del equipo, y la cabeza + corazones de cada integrante.
Gana el equipo que se queda con alguien vivo. Se toman los jugadores conectados en ese momento.

## Logos y equipos guardados
Los logos salen de la carpeta `imagenes/` del servidor (la misma del mod de imágenes).
```
/versus equipo rojos logo dragon.png
/versus equipo rojos logo quitar
/versus equipo rojos agregar Steve        (jugadores extra, además de los del /team)
/versus equipo rojos quitar Steve
/versus equipo rojos ver
/versus equipo rojos borrar               (olvida logo y jugadores añadidos; no toca el /team)
/versus lista                             (todos los equipos que conoce)
```
Se guardan en el mundo: puedes tener todos los equipos que quieras y elegir entre cuáles es cada pelea.

## Durante la pelea
- `/versus estado`           quién sigue vivo
- `/versus parar`            quita el marcador (con animación de salida)
- `/versus ganador rojo|azul` fuerza un ganador (con la transición)
- `/versus hud posicion <zona>` y `/versus hud escala <50-200>` (se aplican en la próxima pelea)

Eliminado = muere, se desconecta o pasa a espectador. Si ambos lados caen a la vez, es empate.

## Obtener el .jar sin instalar nada
1. Sube TODO el contenido de esta carpeta (incluida `.github`) a un repositorio de GitHub.
2. Pestaña Actions -> "Compilar mod" -> descarga **versus-jar** (dentro está el .jar).
3. Ponlo en `mods/` del servidor y de cada jugador.
