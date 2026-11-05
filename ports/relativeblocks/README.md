# Olaf's Relatively Placed Blocks para Forge 1.20.1 (port)

Port a **Forge 1.20.1** (y Mohist) del mod de Fabric **Olaf's Relatively Placed Blocks 2.0.0** de **LukeolafP**
(`olafsrelativeblocks-2.0.0mc1.20.1.jar`, licencia **CC0-1.0**, modid `relativeblocks`). Lo pidió el dueño el 5 nov 2026:
«Portea este mod a Forge 1.20.1, sin errores en Mohist».

Qué hace el mod (igual que el original):

- **Grupos de bloques** (`relativeblocks:block_cluster`): varios bloques pequeños en el mismo hueco (flores, antorchas,
  botones, palancas, vallas, rieles…), cada uno donde se apunta (mitades, cuartos, arriba/abajo). Se usa cada uno por
  separado (una palanca dentro de un grupo se puede accionar) y al romper solo se va el que se mira.
- **Losas mezcladas**: una losa de roble abajo y una de piedra arriba en el mismo bloque.
- **Losas verticales** («ortostatos»): una por cada losa de **cualquier** mod (`relativeblocks:vertical_slab/<mod>/<losa>`),
  con su receta (tres losas en columna), en la pestaña «Bloques de construcción» detrás de su losa; también se mezclan
  dos verticales distintas en un hueco.

## Descarga

- Siempre la última: **https://github.com/modelqdeluxe-ops/ss/releases/download/relativeblocks/relativeblocks.jar**
- Una versión concreta: `https://github.com/modelqdeluxe-ops/ss/releases/download/relativeblocks-forge-v<versión>/relativeblocks-forge-1.20.1-<versión>.jar`

Va **en el servidor y en los clientes** (registra bloques: un cliente sin el mod no puede entrar, Forge lo avisa).
Necesita Forge 47.x (1.20.1); probado en Forge 47.3.0 (juego) y Mohist 1.20.1 build con Forge 47.4.23 (servidor).

## Cómo se hizo

1. El jar de Fabric se pasó de los nombres «intermediary» a los de Mojang (tiny-remapper) y se decompiló con
   Vineflower. Lo que el decompilador no supo (los `finally` de `BlockClusterBlock`: `withColumnAsCell`, `peekAs`,
   `runAsMember`) se reescribió mirando el bytecode original (`javap -c`), para que haga exactamente lo mismo.
2. Lo de Fabric se cambió por lo de Forge:

| Fabric | Forge (este port) |
|---|---|
| `ModInitializer` / `ClientModInitializer` | `@Mod` `RelativeBlocks` + `RelativeBlocksClient` (solo cliente) |
| `Registry.register` en el arranque | `RegisterEvent` (`init/BlockInit`, `init/BlockEntityInit`) |
| `RegistryEntryAddedCallback` (una losa vertical al registrarse cada losa) | `mixin/GameDataMixin`: justo antes de congelar el registro de bloques, cuando ya están **todos** los mods; sus objetos en el `RegisterEvent` de los objetos |
| `UseBlockCallback` / `PlayerBlockBreakEvents.BEFORE` | `PlayerInteractEvent.RightClickBlock` y `BlockEvent.BreakEvent` con prioridad `LOWEST` (las protecciones van antes) |
| — | antes de tocar el mundo, `ForgeEventFactory.onBlockPlace` en los bloques que cambia (`RelativePlacement.mayBuild`): así lo ven las protecciones del TF Client y, en Mohist, los plugins de Bukkit (`BlockPlaceEvent`) |
| `ItemGroupEvents` | `BuildCreativeModeTabContentsEvent` |
| `FabricBakedModel` + `RenderAttachedBlockEntity` | `BakedModelWrapper` + `ModelData` (`BlockClusterBakedModel`, `VerticalSlabModel`): vale con Embeddium/Oculus, que piden `getModelData` |
| `ModelLoadingPlugin` | `ModelEvent.ModifyBakingResult` |
| Recursos de las losas verticales generados en memoria | un paquete de recursos que se escribe al arrancar en `<juego>/relativeblocks/generated` (`client/GeneratedResources`): sin avisos de modelos que faltan |
| El contorno al apuntar (`WorldRenderEvents.BLOCK_OUTLINE`) | `RenderHighlightEvent.Block` (`ClusterOutlineRenderer`) |
| El sonido al romper (redirect en `ClientPlayerInteractionManager`) | `getSoundType(state, level, pos, entity)` de Forge en `BlockClusterBlock` |
| El daño al romper (`renderBreakingTexture` de 5 argumentos) | la sobrecarga de Forge de 6 argumentos con `ModelData` (`ClusterDamageMixin`, `remap = false`) |

3. Las recetas de las losas verticales se sacan de las de sus losas al cargar las recetas (`RecipeManagerMixin`), con
   las `conditions` de Forge de la receta de la losa.

## Cómo se prueba (sin abrir el juego a mano)

- **Compilar**: solo en GitHub Actions (`.github/workflows/relativeblocks.yml`, en cada push que toque
  `ports/relativeblocks/`). Revisa además que el refmap va en el jar, que ningún mixin se cite a sí mismo y que la
  prueba no va en el jar. Artefacto `relativeblocks-jar`.
- **En el juego**: un commit con `[prueba-relativeblocks]` (en una rama `claude/**`) abre Minecraft en una pantalla
  virtual con la prueba (`src/prueba/.../PruebaRelativeBlocks.java`): mundo plano, clics de verdad (cliente → servidor)
  que juntan flores y antorchas, mezclan losas, ponen losas verticales y rompen uno de un grupo, y 3 capturas. El
  informe y las capturas, en la rama `pruebas-relativeblocks`. Con `[shaders]` además, Embeddium + Oculus.
- **En Mohist** (el servidor del dueño), en el contenedor con jugadores bot:

```bash
ports/relativeblocks/tools/probar_mohist.sh /tmp/rb-mohist relativeblocks-forge-1.20.1-<versión>.jar tfclient.jar
```

  Monta Mohist + EssentialsX + LuckPerms + TF Client + el port (`tf-client/tools/mohist/montar.sh`) y comprueba: arranca
  sin errores ni mixins rotos, 57 losas verticales y sus recetas; Ana junta flores, antorchas, losas mezcladas y
  verticales; Carlos (fuera del grupo de la zona protegida de Ana) no puede añadir ni romper nada; Ana rompe uno de un
  grupo y queda el otro; tras reiniciar todo sigue igual. Termina con `RELATIVE BLOCKS EN MOHIST: TODO BIEN`.

## Publicar una versión nueva

Sube `mod_version` en `gradle.properties` (`2.0.0-forge.N`: 2.0.0 es la del original) y fusiona en `main`: el workflow
crea la Release `relativeblocks-forge-v<versión>` y cambia `relativeblocks.jar` de la Release fija `relativeblocks`.
Ninguna se marca como «latest».

## Lo que hay que saber

- Los bloques dentro de un grupo no son bloques normales para los plugins de Bukkit: un plugin que busque «una antorcha
  en x y z» verá un `relativeblocks:block_cluster`. Poner y romper sí pasan por los eventos de Bukkit (en Mohist).
- Las losas verticales de mods que se registren **después** de congelarse el registro (ninguno normal lo hace) no salen.
- La licencia del original es CC0 (dominio público); se mantiene en el jar (`LICENSE_relativeblocks`) y se da el
  crédito a LukeolafP en `mods.toml`.
