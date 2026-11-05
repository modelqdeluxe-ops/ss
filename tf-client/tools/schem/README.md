# Una construcción de un mapa → `.schem` para Forge/Mohist 1.20.1

El dueño manda un mapa (un mundo entero en un zip) y pide «haz schematic esta casa». Esto saca la casa a un `.schem`
(Sponge v2: lo leen WorldEdit 7.2.15 de Forge y de Bukkit, y FAWE) en la 1.20.1, la valida y la pega de verdad en un
Mohist con WorldEdit para comparar bloque a bloque. No toca el mod ni compila nada.

**Los mapas y los `.schem` no van al repositorio** (son del dueño o de quien hizo el mapa): se trabajan en una carpeta
temporal y los `.schem` se le mandan al dueño.

## Pasos

1. **Bajar el mapa** (Google Drive grande: la primera URL devuelve un aviso «no se puede analizar»; hay que repetir con
   `confirm=t`):

   ```bash
   curl -sSL -o mapa.zip "https://drive.usercontent.google.com/download?id=<id>&export=download&confirm=t"
   ```

2. **Encontrar la casa**: `level.dat` (`Data.Version.Name`, `SpawnX/Z`) y buscar los chunks con más bloques «hechos»
   (tablones, escaleras, cristal, hormigón…); suele estar junto al spawn. Ojo: hay mapas con dos copias pegadas.

3. **Pasar el mundo a la 1.20.1** con el servidor oficial (deja carteles, bloques y cuadros como el juego). Solo hacen
   falta `level.dat` y los `.mca` de `region/` y `entities/` de la zona (`r.X.Z`, X = x>>9, Z = z>>9):

   ```bash
   tf-client/tools/schem/actualizar.sh <copia del mundo> <server.jar 1.20.1>
   ```

   El `server.jar` y el `client.jar` (este, para las vistas) salen del manifiesto de Mojang:
   `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json` → la 1.20.1 → `downloads.server/client.url`.

4. **Sacar los `.schem`**:

   ```bash
   python3 -I tf-client/tools/schem/casa_a_schem.py <mundo> --caja x0 y0 z0 x1 y1 z1 --semilla x y z \
     --nombre Nombre --salida carpeta [--margen 14] [--pies x y z]
   ```

   Saca `Nombre.schem` (la casa sola con una base de 3 bloques bajo su planta) y `Nombre_terreno.schem` (con
   `--margen` bloques de paisaje alrededor y los árboles decorados enteros). La semilla es un bloque de la casa (una
   pared); la caja, la zona donde buscar (que la casa no toque su borde). Lo que es «terreno» (piedra, tierra, hierba,
   plantas naturales) está en `TERRENO`; lo demás, si toca la casa (26 vecinos), es casa. Rellena con agua cayendo el
   aire bajo el agua «colgada» (el juego lo haría en cuanto algo la actualice).

5. **Validar** contra los bloques de la 1.20.1 (`PROBLEMAS 0`) y **mirarlo**:

   ```bash
   java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports   # generated/reports/blocks.json
   python3 -I tf-client/tools/schem/validar.py generated/reports/blocks.json carpeta/*.schem
   python3 -I tf-client/tools/schem/vista.py carpeta/Nombre.schem client.jar carpeta/vista [--escala 4]
   ```

6. **Pegarlo en un Mohist de verdad** con WorldEdit (Forge y Bukkit) y comparar (`WORLDEDIT …: TODO BIEN`):

   ```bash
   tf-client/tools/schem/probar_worldedit.sh /tmp/tf-mohist <tfclient.jar> carpeta forge
   tf-client/tools/schem/probar_worldedit.sh /tmp/tf-mohist <tfclient.jar> carpeta bukkit
   ```

   Usa `tools/mohist/` (README de allí); el jar es el de la Release
   (`https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient/tfclient.jar`).

## Cómo se pega (para el dueño)

- Forge (mod WorldEdit): el `.schem` en `config/worldedit/schematics/`. Bukkit/Mohist (plugin): en
  `plugins/WorldEdit/schematics/`. Sin reiniciar.
- Antes de pegar, **una vez por sesión**: `//perf neighbors off`, `//perf update off`, `//perf validation off`. Si no,
  WorldEdit «arregla» las vallas, muros y paneles que el constructor dejó sueltos (se conectan solos) y se caen algunas
  alfombras de musgo (en PentaLodge: ~600 bloques cambiados y 109 alfombras al suelo). Con los tres apagados queda
  idéntico al mapa (comprobado).
- `//schem load Nombre` y `//paste -e` (`-e` = con los cuadros; sin él no se pegan entidades). El jugador queda en la
  entrada (el final del camino), con los pies a la altura del suelo: la casa sale delante, hacia el norte.
  `//paste -a -e` no pega el aire (la casa se mete en el terreno que haya). `//rotate 90` antes de pegar la gira.

## PentaLodge (5 nov 2026)

Mapa del dueño `PentaLodge.zip` (Drive, 107 MB, mundo de la 1.19.2; el zip lleva un comentario de NullForums). Casa
A-frame de pentágonos con piscina en el tejado y cascada, en un paisaje decorado de ~450×450 bloques; hay dos copias
iguales (x 100…158, z −1294…−1228 junto al spawn y otra en −1265, −4, +1048 de esa). Hecho con:

```bash
python3 -I tf-client/tools/schem/casa_a_schem.py <mundo 1.20.1> --caja 70 60 -1330 190 150 -1190 \
  --semilla 109 100 -1280 --nombre PentaLodge --salida carpeta
```

- `PentaLodge.schem`: 59×51×69, 28.902 bloques, 112 entidades de bloque, 4 cuadros, 14 KB.
- `PentaLodge_terreno.schem`: 99×68×109, 212.611 bloques, 136 entidades de bloque, 4 cuadros, 51 KB.
- `validar.py`: 0 problemas. WorldEdit 7.2.15 Forge y Bukkit en Mohist 1.20.1 (la última de `api.mohistmc.com`,
  Forge 47.4.23) con el TF Client 1.3.174: 0 bloques distintos (solo hierba ↔ tierra que hace el juego), entidades de bloque y
  cuadros iguales.
- El agua de encima del tejado es una lámina colgada sobre un hueco de aire (puesta sin física): el juego la deja caer
  al hueco en cuanto se actualiza; el `.schem` ya trae el hueco lleno (233 bloques de agua cayendo), así se queda quieta.
