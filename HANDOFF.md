# Traspaso — Tierras Fantásticas (léelo entero antes de tocar nada)

Última actualización: **8 de octubre de 2026**. Repo `modelqdeluxe-ops/ss`, rama de trabajo
`claude/amazing-wozniak-gtw9ll`. Este documento es para que otra IA (o persona) pueda seguir exactamente donde se
quedó el trabajo: qué es el proyecto, qué reglas puso el dueño, **qué estábamos haciendo ahora mismo**, cómo
funciona Cloudflare/Stripe/Discord/el puente con Minecraft y cómo publicar.

---

## 0. Lo que estábamos haciendo AHORA MISMO (empieza por aquí)

**Prioridades del dueño:** 1) la web (diseño y ahora también **lo legal**), 2) el mod TF Client (cambios que vaya
pidiendo: cada uno sube versión y se le manda el `.jar`), 3) **Stripe conectado** por el dueño (ya no está en pausa); PayPal listo para cuando ponga sus claves.

### Última entrega (8 de octubre de 2026): menú de inicio como antes, con el paisaje de noche — TF Client 1.3.19
No le gustó el menú de 1.3.17 (el cielo animado salía como un óvalo pixelado: la viñeta y las capas estiradas, y los
botones nuevos): *«regresa al formato anterior, botones… pon el fondo de oscuridad que tiene la página»*, y luego
**sin nebulosa**: el **paisaje de noche que la web tiene detrás de la nebulosa** (`img/night-1672.webp`). Se restauraron de la 1.3.16 `TFMenuButton`, `TFButtonTheme`, `TitleScreenMixin`, `TFClientEvents`,
`TFTextures`, `TFMissingModsScreen`, `TFServerCheckScreen`, `gen_buttons.py` y las texturas de los botones (Cinzel, placas
en ángulo azul noche/oro). Fuera `TFSky`, la letra Unbounded y `textures/gui/sky`. `menu_background.png` es ese paisaje de noche
(1920x1080, tal cual). **No vuelvas a poner la nebulosa ni el cielo animado en el menú.**

### Entrega anterior (8 de octubre de 2026): TF Claims (protecciones) dentro de TF Client — 1.3.18
Pidió meter su mod **Fantastic Claims 7.9.2** (es suyo; jar subido, sin código fuente) en TF Client como **TF Claims**,
con la textura nueva de la piedra (su imagen: piedra oscura, marco de neón, tornillos y «TF»), el resto de caras
dibujadas pixel perfect, el color de cada tamaño y la protección **infinita hacia arriba** (hacia abajo igual que antes).

- **Cómo se portó**: se decompiló el jar con CFR, se pasaron los nombres SRG a los oficiales con el
  `build/createMcpToSrg/output.tsrg` de ForgeGradle (64 225 nombres, 0 conflictos) y se movió a `tfclient.claims.*`
  (mixins en `mixin/claims`). Se arreglaron los fallos típicos de CFR (casts de mixin, `case` de enums cualificados, un
  `switch` mal decompilado en `ClaimMenuHandler.flagLore` y un ternario en `EntityProtectionEvents.onProjectileImpact`).
  Comprobación: se volvió a decompilar lo compilado y se comparó con el original → solo cambian las cosas hechas a
  propósito (refmap de los mixins idéntico al del jar original).
- **Mismos datos**: `claimblocks_data.json`, `claimblocks_config.json` y `global_flags.json` en la carpeta del mundo
  (las zonas y la configuración de Fantastic Claims siguen igual). Los objetos `claimblocks:proteccion_*` pasan solos a
  `tfclient:proteccion_*` (`ClaimItems.onMissingMappings`) y el concreto de las zonas viejas se cambia por la piedra nueva
  cuando su chunk está cargado (`TFClaims.upgradeLegacyStones`, una vez por zona y arranque). **Hay que quitar
  Fantastic Claims** del servidor y de los clientes.
- **Altura**: `Claim.contains` protege desde `y − altura` hasta el techo del mundo; dos zonas que se pisan en planta
  siempre se solapan. Bordes, partículas y la vista previa al llevar la piedra van hasta `getMaxBuildHeight()`.
- **Piedras** (`ProtectionBlock`, `tools/gen_claim_blocks.py`): 10 bloques `tfclient:proteccion_<tamaño>` (32x32: front
  con las letras mirando a quien la pone, side con el rombo, top con el anillo del centro, bottom apagado) más una capa
  `_glow` a plena luz (`forge_data`, se ve de noche). Color = el del concreto que tenía cada tamaño, en neón. No sueltan
  nada (la devuelve el evento al dueño), los pistones no las mueven y aguantan explosiones. El objeto no es BlockItem
  (lo coloca `BlockProtectionEvents`, como en Fantastic Claims). Pestaña creativa de TF.
- **Comandos** (sin raíces nuevas): `/tf claims [menu|info|list|remove|addmember|delmember|members|merge …]` y, del
  staff, `/tf claims give|clear|ban|unban|transfer|removemember` y `/tf web claims [bypass|list|stats|reload|globalflag]`.
  Los textos del menú y la configuración ya dicen esos comandos.

### Entrega anterior (8 de octubre de 2026): menú de inicio con el cielo de la web y armario del perfil — TF Client 1.3.17
Lo que faltaba de la petición de 1.3.16 («el mismo fondo animado que hay en la web en el menú de inicio, con las mismas
letras, auroras, botones con efectos bonitos» y «en el perfil una especie de armario para equipar y desequipar todo lo
comprado»):

- **Menú de inicio** (`TFSky`, `TitleScreenMixin`, `TFClientEvents.drawMenuBackground`): el fondo de la web capa a
  capa con sus tiempos (nebulosa base 60 s, A 24 s, B 15 s, auroras 18 s sumando luz, estrellas que bajan y titilan
  solo en el cielo, estrellas fugaces, oscurecido hacia abajo y viñeta). Imágenes en `textures/gui/sky` (sacadas de las
  de la web); `menu_background.png` borrado. Letra **Unbounded** (`font/unbounded.ttf`, OFL, recortada a latín) en
  botones y textos del menú. Botones (`tools/gen_buttons.py`, `TFMenuButton.Style`): oro (servidor, Web), Discord,
  pizarra con filo cian (el resto, también los de Minecraft fuera de partida vía `TFButtonTheme`); al pasar el ratón
  suben 1 px y los cruza un destello (`drawHoverSweep`).
- **Armario** — web: tabla `wardrobe` (uuid → `{hueco: "set/pieza"}`), `GET/POST /api/account/wardrobe` (solo piezas
  que tiene: pedidos pagados sin reembolso + el set de su rango; `productPieces`/`wardrobeSlot` en `src/app.js`), sección
  «Armario» en «Mi cuenta» (personaje 3D con lo puesto mezclando sets —`wardrobe.js` acepta `armorLayers` por pieza—,
  5 huecos y las piezas de cada uno; tocar otra vez o «Quitar» la quita). Puente: `wardrobe: [{uuid, items, at}]` de los
  conectados. Mod: `TFWardrobe` valida (`tfclient:<set>_<pieza>` del tipo del hueco), guarda en
  `<mundo>/tfclient/wardrobe.json` y sincroniza a todos; los mixins `HumanoidArmorLayerMixin`/`CustomHeadLayerMixin` y
  `BackLayer` lo dibujan encima de lo puesto. Solo apariencia (sin defensa ni planeo).
- Test nuevo en `test/app.test.js` (armario: 401 sin sesión, 403 si no es tuyo o va en otro hueco, poner/quitar, puente).

### Entrega anterior (8 de octubre de 2026): skills más fuertes, kills completas, planeo de 10 s, paleta nueva, página VFX con vistas previas y TF Client 1.3.16
**Lo que pidió el dueño (resumen):** quitar el paquete de mago y dejar los 2 de cuerpo a cuerpo con mucho más daño;
revisar los efectos de kill («algunos no hacían bien la kill»); quitar el icono de la barra; planeo de alas con
cronómetro de 10 s y contador junto al último hueco; rework serio de la página VFX con una animación de lo que se
compra; rework de colores de toda la web (todo se veía igual). **Lo que quedaba de esa petición** (menú de
inicio y armario) salió en 1.3.17, abajo.

- **Skills** (`vfx_skills.py`, `VfxServer.affect`): fuera *Hechizos del Alma*; el daño es base × (2 + ataque del jugador
  / 4) × `multiplicadorDanoSkills` (×4 con espada de netherita, más con las armas de los sets).
- **Kills**: la vida de cada efecto venía del `remove` de MythicMobs (50 ticks) y cortaba la animación 0,5 s antes de
  terminar en 45 de los 50 (en jade y espectral el cuerpo seguía visible y desaparecía de golpe). Ahora dura lo que la
  animación (`build_vfx.py`). Auditoría: `killaudit.py` (scratch) mira duración, visibilidad del cuerpo en el tiempo.
- **Sin indicador** en la barra (`VfxHud` borrado y sus iconos `textures/gui/vfx` ya no se generan).
- **Alas** (`TFWings.canGlide`, `TFGlideHud`): 10 s de planeo, luego se pliegan hasta tocar suelo/agua; en el suelo no
  se planea (arregla el «me quedo planeando en el suelo»). Contador a la derecha de la barra mientras planeas.
- **Vistas previas animadas** (`tools/vfx_preview.py`): simula la línea de tiempo de `fx.json` como `VfxClient`
  (modelos, swap, vis, estados, tintes, proyectiles, temporizadores y partículas aproximadas) y la dibuja con
  `vfx_render` (refactorizado en `model_tris` + `raster`) en WebP animado: kills con un zombi haciendo el efecto,
  skills con Steve (skin del `client.jar`, nunca en el repo). `public/img/vfx/prev_*.webp`; `preview` en
  `config/vfx.json` (kills y cada skill, que ahora lleva `id`).
- **Página VFX** (`renderVfx` en app.js, sección VFX de styles.css): escenario grande con la animación de lo elegido
  (pantalla con suelo de rejilla y «Vista previa»), ficha con disparador/cooldown y botón, lo que llevas puesto; mosaico
  de kills por categoría (se animan al pasar el ratón; tocar → al escenario); paquetes con cada skill clicable.
- **Paleta** (tokens al principio de styles.css): tarjetas en pizarra azul (no violeta como el cielo); botones por
  función: `.btn-buy` oro (comprar, mejorar, pagar, «Tienda» de la cabecera), `.btn-claim` esmeralda (reclamar,
  obtener), `.btn-ghost` cian (secundarios), `.btn-primary`/`.btn-theme` el destello rosa de la marca (confirmar,
  equipar, entrar); precios en oro, «Gratis» en esmeralda; cada pestaña de la tienda con su tono (`--tone-*`: gratis
  esmeralda, rangos oro, cosméticos rosa, crates violeta, VFX cian, monedas ámbar) y las tarjetas de la sección con su
  filo de ese color (`--tone` en `#products`).

### Entrega anterior (8 de octubre de 2026): ventajas de los rangos puestas solas en LuckPerms/EssentialsX y TF Client 1.3.15
**Lo que pidió el dueño:** la lista exacta de lo que trae cada rango, añadida a la web y enlazada con el servidor
(rangos con el plugin LuckPerms; el TF Client hace las llamadas y pone los permisos), «súper preciso, sin errores».

| Rango | Hogares | Comandos |
| --- | --- | --- |
| Mortal | 5 | /craft (= /workbench), /anvil, /loom, /hat |
| Inmortal | 7 | /craft, /anvil, /loom, /hat |
| Mágico | 8 | + /enderchest |
| Eterno | 10 | /craft, /anvil, /loom, /hat, /enderchest, /heal |
| Cósmico | 12 | /craft, /anvil, /loom, /hat, /enderchest, /feed |
| Celestial | 15 | /craft, /anvil, /loom, /hat, /enderchest, /repair, /fly |
| Fantástico | 20 | /craft, /anvil, /loom, /hat, /enderchest, /repair, /heal, /feed, /fly |

(«crafting» y «workbench» de la lista son el mismo comando de EssentialsX, `/workbench` con su alias `/craft`, y
«Anvil» venía repetido: va una vez. El dueño corrigió después: de Mágico en adelante todos tienen /enderchest. Celestial
no tiene /heal ni /feed, tal como lo mandó.)

- **Web**: `RANK_PERK_INFO`, `HOME_PERMS` y `RANK_SERVER_PERKS` en `tools/crates.py` → `serverPerks` (lo que se ve
  debajo del escaparate, en el orden del dueño, con «Nuevo» en lo que no tenía el rango anterior) y
  `rank.homes`/`rank.permissions` en products.json. El puente manda `rankList` con `homes` y `permissions`
  (`rankSetup` en src/app.js; los datos públicos de un jugador no los llevan). Test en app.test.js.
- **Mod** (`server/TFRankPerms.java`): al llegar `rankList`, si cambió desde lo último aplicado
  (`<mundo>/tfclient/rank_perms.json`): `lp creategroup <rango>`, `lp group <rango> permission set <permiso> true`, y
  `permission unset` de lo que puso el mod y ya no está (lo que ponga el staff a mano no se toca). Hogares en
  `sethome-multiple` del `config.yml` de EssentialsX (`server/EssentialsHomes.java`: solo cambia/añade esas líneas;
  copia `config.yml.antes-de-tfclient`) y `essentials reload`. `/tf web rango permisos` lo fuerza y dice qué hizo.
  Ajustes `ranks.permissions` y `ranks.essentials` en `tfclient-server.properties`.
- **Arreglo**: en Mohist, LuckPerms contesta por el mismo canal que el mod usaba para detectar errores (también cuando
  va bien), así que un `lp user … parent add` podía contarse como entrega fallida. `TFBridge.run` ya solo comprueba
  que exista `/lp` para los comandos de LuckPerms.
- **Ojo Mojang**: /fly, /heal, /feed y /repair dan ventaja en el juego; las normas de uso de Minecraft no permiten
  vender ventajas de juego. Lo decidió el dueño; queda avisado.
- Lo de la tabla vieja de «Rangos: comandos y permisos» (más abajo: /nick, /ptime, /pweather, /pp, cola…) ya no se
  anuncia; si el staff lo puso a mano en LuckPerms, sigue ahí hasta que lo quite.

### Entrega anterior (8 de octubre de 2026): el propio mob hace el efecto de kill, VFX ordenado en la web y TF Client 1.3.14
**Lo que pidió el dueño (textual):** *«haz que los mobs también sean afectados por la animación de kills, busca la
forma, que no salga el muñequito obvio sino que el mismo mob haga la animación, todos sin excepción. Y ordena mejor esa
parte de los VFX, ordénalas más bonitas por favor.»*

**Mod 1.3.14 — la víctima hace la animación (`VfxActor` + `LivingEntityRendererMixin`):**
- El muñeco gris de los packs ya no se dibuja: es la víctima de verdad (cualquier mob o jugador) la que se mueve.
  `vfx_bb.py` saca las **figuras** del muñeco (`figures` en el modelo: rig = hueso raíz + cabeza, cuerpo, brazos y
  piernas; y texturas). Todo el mob sigue al torso: matriz `vista·efecto·D·(giro·escala del efecto)⁻¹` con
  `D = torso(t)·torso(reposo)⁻¹`, mirando hacia donde mira el efecto (`yBodyRot` = giro del efecto). El efecto se
  escala a la altura del mob (`bbHeight/1.8`, entre 0,6 y 3).
- Mobs humanoides (zombis, esqueletos, jugadores, piglins…): tras `setupAnim` (el mixin gana a los brazos del zombi o
  al arco del esqueleto) cada pieza toma el giro ZYX y la escala de la pieza del muñeco (Blockbench → Minecraft: giro
  (−x, −y, z)) y su pivote se mueve con el movimiento entero de esa pieza (`MOTION`), así da igual dónde tenga el
  pivote el pack. Comprobado en Python con los 50 efectos: error mediano 0 px (máx. <2 px solo con escalas no
  uniformes, que Minecraft no puede hacer). La segunda capa de la skin del jugador va con su pieza.
- Materiales: si el cuerpo del efecto es de otro material (óxido, piedra, agua, jade) el mob se dibuja con esa
  textura (rellenada para cualquier reparto de UV, `mob_texture`); si el muñeco tiene un doble (holograma, fantasma
  espectral, estrella del norte, contorno, forma pura) es otra vez el mob con esa textura; las capas encima del
  cuerpo (bendición angelical) son una segunda pasada. En esas pasadas solo se dibuja el cuerpo (ni armadura ni
  objetos). Sin caída de lado ni rojo de muerte; sin sombra; tu propio cuerpo no se dibuja en primera persona.
- Servidor: hasta 4 efectos cada 5 ticks por jugador (antes 1), para que en un barrido salgan todos.
- Miniaturas de la web: un zombi (skin sacada del `client.jar`, nunca en el repo) haciendo el efecto, con los
  materiales tal cual (caras `-3` con reparto de skin en los modelos solo para esto; el mod las ignora).

**Web — VFX ordenado:** arriba lo que llevas puesto (efecto de kill y paquete, con su miniatura) y una línea de
ayuda; pestañas **Efectos de kill (50) / Paquetes de skills (3)**; los efectos agrupados por categorías con su color
y filtro (Elementos, Cielo y cosmos, Energía y tecnología, Naturaleza, Arte y papel, Divertidos: `KILL_CATS` en
`build_vfx.py`, van en `config/vfx.json` → `cats` y cada kill con `cat`); tarjetas más compactas (descripción en 2
líneas) y la equipada resaltada; paquetes en horizontal en escritorio (imagen a la izquierda, skills en 3 columnas).
En el móvil los filtros se deslizan en una fila.

### Entrega anterior (8 de octubre de 2026): sección VFX (efectos de kill y paquetes de skills) y TF Client 1.3.13
**Lo que pidió el dueño (textual):** *«añadirás ahora una sección de VFX, estos efectos, algunas son skills… que todas
las animaciones, sonidos, todo sea correcto dentro del juego sin errores… el icono se pondrá al ladito del último slot
derecho, no donde pones el escudo, un indicador de que está activado… ponlos gratis de momento, son muchos así que
serán presentados por individual, no por pack, solo las skills vienen en paquete… solo quiero las animaciones,
efectos, nada de armas o ítems, deben ser pasivas, y las skills deben tener cooldown»*. Packs que mandó (comprados, **no
se suben al repo**): EC Kill Effects vol. 1–5 (50 efectos), samus2002 Gale Glaive y Heroes Thunder Ronin, NeiCore
Dynamic Player VFX. De los packs solo se usan modelos, animaciones, texturas y sonidos (nada de armas ni objetos).

**Cómo está hecho (mod 1.3.13, paquete `vfx/`):**
- `tools/build_vfx.py <carpeta con los packs descomprimidos>` lo genera todo: convierte los `.bbmodel` de ModelEngine
  (`tools/vfx_bb.py`: huesos, cubos ya horneados, animaciones con las reglas exactas de Blockbench: giros ZYX, la
  animación suma (−x, −y, +z) al giro y (−x, y, z) a la posición, catmullrom uniforme con vecinos, lineal y escalón;
  frente del modelo = −Z), compila el aspecto de las skills desde los YAML de MythicMobs (`tools/vfx_compile.py`:
  summon de mobs de VFX, state, changepart y auras que iteran fotogramas, partvis, tint, partículas en huesos,
  temporizadores, proyectiles, órbitas, sonidos con los de 1.21 cambiados por otros de 1.20.1 —
  `tools/sounds_1201.txt`) y la jugabilidad está escrita a mano en `tools/vfx_skills.py`. Escribe
  `assets/tfclient/vfx/{models/*,fx.json,catalog.json}`, `textures/vfx/` (sin repetir), `textures/gui/vfx/` (iconos),
  `sounds/vfx/` + `sounds.json`, `tierras-fantasticas/config/vfx.json` y las miniaturas `public/img/vfx/*.webp`
  (sacadas del propio modelo con `tools/vfx_render.py`, el mismo cálculo de pose que el mod).
- Cliente (`VfxClient`): recibe `VfxNet.Play` (efecto, posición, giro, quién lanza, víctima, skin) y sigue la línea de
  tiempo de `fx.json`: modelos animados (`VfxModel` + `VfxPose`, comprobado número a número contra Python), sonidos y
  partículas. Dibuja en `AFTER_TRANSLUCENT_BLOCKS` con `entityTranslucentCull` y luz máxima (como el brillo 15 de
  ModelEngine). Los huesos `phead` llevan la cabeza con la skin (la víctima en los kills, el jugador en los hechizos).
- Efectos de kill: al matar (jugadores y mobs, `efectosDeKillConMobs`), en la víctima salía un cuerpo gris con su cabeza (desde 1.3.14 lo hace el propio mob)
  que hace el efecto; el cuerpo de verdad no se dibuja esos 20 ticks.
- Skills pasivas con cooldown (`VfxServer`, sin comandos ni teclas): disparadores `golpe` (golpe cargado),
  `golpe_critico`, `golpe_corriendo`, `golpe_agachado`, `dano` (con «vida»: solo con poca vida; o con probabilidad)
  y `combate`. Combos de 3 fases, daño en zona/línea/proyectil/zona que dura, empujes, pociones, paralizar, fuego,
  curar, esquivar. Solo dañan monstruos, al que golpeas y jugadores si hay PvP; nunca tus mascotas. Daño pensado para
  survival (config `config/tfclient-vfx.json`: activado, efectosDeKillConMobs, skills, skillsEnPvP,
  multiplicadorDanoSkills). Datos por jugador en `<mundo>/tfclient/vfx.json`.
- Paquetes: **Filo del Vendaval** (7 skills de viento), **Ronin del Trueno** (7 de rayo), (Hechizos del Alma, quitado en 1.3.16) (5
  hechizos con cinemática: el jugador se convierte en mago con su cara y no se ve mientras dura).
- Indicador (`VfxHud`, quitado en 1.3.16): a la derecha de la barra, con el marco del slot de la mano secundaria (si eres zurdo y llevas
  algo en esa mano, o tienes el indicador de ataque en la barra, se aparta). Hueco con el paquete (y su cooldown, como
  el de los objetos) y otro con el efecto de kill.
- Staff: `/tf web vfx lista`, `/tf web vfx kill <jugadores> <id|ninguno>`, `/tf web vfx skills <jugadores>
  <id|ninguno>`, `/tf web vfx probar <id>` (un efecto de kill delante o `paquete/skill`).

**Web:** pestaña **VFX** en la tienda (`renderVfx` en app.js, sección «VFX» de styles.css): los 3 paquetes con sus
skills (disparador y cooldown) y los 50 efectos uno a uno con filtro por volumen (desde 1.3.14, por categorías). **Gratis**: «Obtener» lo guarda en
la cuenta y lo equipa; «Equipar» / «Quitar». Tablas `vfx_owned` y `vfx_equip`; rutas `GET /api/vfx`,
`POST /api/vfx/claim`, `POST /api/vfx/equip`; el puente recibe `vfx: [{uuid, kill, pack, at}]` de los conectados y el
mod solo aplica lo que tenga una hora más nueva (así un cambio del staff no se pisa). Para cobrarlos más adelante:
`VFX_FREE` en `src/app.js`.

### Entrega anterior (8 de octubre de 2026): /tf shop, techo de netherita, Fantastic Coin, móvil fluido, Discord sin invitador y TF Client 1.3.12
**Lo que pidió el dueño (textual):** *«en teléfono la página va bugueada, parpadea, se traba, se pone lenta horrible…
no me gusta el diseño de algunos botones de la tarjeta de tienda, el color esmeralda hazlo más oscuro… el botón de
discord me manda a discord como si yo lo invite… haz que sea el link directo… armoniza colores, efectos… agrega soporte
para pagar con paypal y dime cómo saco las keys… asegúrate que todo item al ser comprado vaya linkeado con nbt al uuid…
al lado del nombre en la página pon un icono de monedas… saca las monedas de oro de mi mod, esa moneda será la de oro,
la Fantastic Coin… quiero una tienda automatizada, el comando es tf shop, full configurable como el de los jobs»*. Y:
*«debes hacer que tf client lea todos los mods del server… y ponga un límite a todas, el techo es a nivel netherita…
para que tengamos un techo fijo y que las mejorcitas sean las de la tienda web»*. Y: *«no vas a meter el mod de
fantastic currency, solo saca la textura y métela en tf client, tf client es nuestro mod nodriza a todo»*.

**Mod 1.3.12:**
- **`/tf shop`** (`shop/TFShop.java`, `TFShopConfig.java`, `TFShopMenu.java`): tienda automatizada de compra y venta
  con las monedas del servidor (TFEconomy). Cofre de 6 filas (vanilla `GENERIC_9x6`, todo lo decide el servidor en
  cada clic: nada se mueve): categorías → objetos por páginas → ventana de cantidad. Clic izq. comprar, clic der.
  vender un lote, Mayús + der. vender todo, pulsar objetos del inventario los vende, botón «Vender todo». Solo acepta
  objetos limpios y nunca los de los sets. Configuración completa en `config/tfclient-shop.json` (por defecto 7
  categorías, 73 objetos; precios, lotes, NBT, comandos, límites diarios, nivel de permiso, textos con `&`):
  detalle en `tf-client/README.md` («Tienda del servidor»). `/tf shop recargar` (nivel 3).
- **Techo de netherita** (`items/TFLimits.java`, config `config/tfclient-limits.json`): ItemAttributeModifierEvent con
  prioridad LOWEST recorta en **todos los objetos de todos los mods** daño (8; 10 si es lento como un hacha), armadura
  por pieza (3/8/6/3), dureza 3 y empuje 0,1, y quita multiplicadores positivos; BreakSpeed recorta la velocidad de
  minar a 9. Exentos: los objetos de los sets (`TFItem`) y las `excepciones`.
- **Fantastic Coin** (`tfclient:fantastic_coin`): la textura de la moneda de oro del mod Fantastic Currency del dueño
  (con «TF» grabado), solo como icono del saldo en `/tf shop` y `/tf jobs`. **El mod Fantastic Currency NO se usa.**
- **Saldo a la web**: el puente manda `coins` de cada jugador conectado (`TFEconomy.balance`).
- Vinculación: comprobado que todo lo que se compra (rangos, crates, recompensas con objetos de set) se entrega con
  `tf web sets give`, que pone dueño (UUID) y pedido en el NBT. Los `give` normales solo son regalos gratis (manzanas,
  pan, diamantes). Los cosméticos no se vinculan (lo decidió el dueño).

**Web:**
- **Móvil fluido**: en teléfonos y tabletas (`max-width: 760px` o `hover: none` + `pointer: coarse`, sección «Móvil»
  de styles.css) no hay capas animadas detrás (`.sky` oculto) y el fondo es `img/nebula-mobile.webp` (vertical,
  quieto, con la nebulosa y las estrellas dentro; lo hace `mobile()` de `tools/nebula_bg.py`). Las capas fijas miden
  `100lvh` para que la barra del navegador no las obligue a redibujarse (eso era el parpadeo). Sin bucles en tarjetas ni
  pestañas, la entrada de página solo con opacidad y el scroll con un `requestAnimationFrame`.
- **Discord sin «X te ha invitado»**: `/discord` usa la invitación del **widget** del servidor (no lleva invitador) o,
  si no hay widget, una invitación permanente creada por el **bot**; se guarda 6 h en `settings` (`discord_invite`).
  Para el dueño: Discord → Ajustes del servidor → **Widget** → activar «Habilitar widget del servidor» y elegir el
  canal de invitación. Sin eso se usa el bot (necesita el permiso «Crear invitación»), y si falla, `DISCORD_URL`.
- **Monedas junto al nombre**: la cabecera enseña la Fantastic Coin (`img/coin.png`) y el saldo (columna
  `players.coins`, que llega por el puente). Las monedas de la web usan la misma moneda.
- **Diseño**: «Visitar la tienda» en esmeralda oscuro → azul (`--shimmer-sea`); las pestañas de la tienda al pasar el
  ratón se llenan suave (el destello fuerte es solo de la elegida); botones de las tarjetas más grandes que se encienden
  al pasar por la tarjeta; todos los botones deslizan su degradado (`--c3` por variante); pestañas que caben en el
  móvil.
- PayPal ya estaba (entrega anterior): ver ahí cómo sacar las claves.

### Entrega anterior (7 de octubre de 2026, noche): reembolsos que retiran lo comprado, PayPal, efecto de relleno suave, 404 y TF Client 1.3.11
**Lo que pidió el dueño (textual):** *«el efecto del botón de izquierda a derecha cuando desaparece se va bien feo
como una línea fea… mejora ese efecto tanto de inicio como de final. Y revisa, ya tengo stripe conectado. También
¿cómo conecto paypal?? Y otra cosa, asegúrate que si alguien pide un reembolso todo lo que compró se le quite del
server incluso si lo guarda en cofres, ya que están vinculados a su uuid. Y en temas de la página me gustaría que la
auditaras y si falta algo ponlo, diseños…»*

- **Stripe ya NO está en pausa**: el dueño lo conectó. Comprobado en producción: `paymentsEnabled: true` y el webhook
  `/webhook/stripe` tiene su secreto (rechaza avisos sin firma con 400). En Stripe (Desarrolladores → Webhooks) el
  endpoint `https://xn--tierrasfantsticas-hpb.store/webhook/stripe` debe tener estos eventos:
  `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`,
  `checkout.session.expired`, `charge.refunded` y `charge.dispute.created` (los dos últimos activan la retirada).
- **Relleno de izquierda a derecha** (botones secundarios y pestañas de la tienda): ya no se anima el tamaño del fondo
  (dejaba una línea al salir). Ahora es una capa `::before` («Relleno de neón» en styles.css) que entra desde la
  izquierda con un fundido rápido y al salir se recoge hacia la derecha mientras se desvanece.
- **Reembolsos** (web + mod 1.3.11): reembolso completo o disputa (Stripe o PayPal) → `revoke()` en `src/app.js`:
  cancela la entrega si no se había hecho y encola una retirada (`deliveries.revoke_of`, `order_id = R<pedido>`) que
  el puente recibe aunque el jugador esté desconectado: `tf web sets revoke {uuid} <pedido> <set> [pieza]`,
  `lp user {uuid} parent remove <grupo>` y, si era una mejora, `parent add <grupo anterior>`; en la web el rango
  vuelve al anterior y se quitan sus roles de Discord (`discord.removeRoles`). Si el jugador ya tiene un rango más
  alto, el rango no se toca (solo se retiran los objetos del set). En el mod, `items/TFRevocations.java`: cada objeto
  entregado por el puente lleva `TFOrder` (el pedido); la retirada se guarda en `data/tfclient_revocations.dat` y
  quita esos objetos de jugadores conectados, suelo, marcos, soportes, cofres con ruedas y contenedores de los chunks
  cargados; lo demás al cargarse, al abrir un contenedor, al entrar y cada segundo del inventario (también dentro de
  shulkers y sacos, y en inventarios de otros mods). Los objetos de antes (sin `TFOrder`) se reconocen por dueño y set.
  Los cosméticos de antes de 1.3.11 no tienen dueño ni pedido: esos no se pueden retirar (los nuevos sí).
  Términos («Cancelaciones y reembolsos») actualizados; `config/legal.json` sube a la versión `2026-10-07.2`, así
  que se vuelve a pedir aceptar a quien ya los aceptó.
- **PayPal** (`src/paypal.js`, Orders v2): botón «Pagar con PayPal» en la ventana de compra cuando están los Secrets.
  Flujo: la web crea el pedido de PayPal → el comprador lo aprueba → vuelve a `/success`, que llama a
  `/api/order/:id/sync` y la web lo cobra (`capturePayPal`) y entrega. Webhook `/webhook/paypal` (verificado con la
  API de PayPal): `CHECKOUT.ORDER.APPROVED`, `PAYMENT.CAPTURE.COMPLETED`/`DENIED`, `PAYMENT.CAPTURE.REFUNDED`,
  `PAYMENT.CAPTURE.REVERSED`, `CUSTOMER.DISPUTE.CREATED`. **Para conectarlo (lo hace el dueño):**
  1. Cuenta PayPal **Business** (México) → https://developer.paypal.com → Apps & Credentials → modo **Live** →
     «Create App» → copiar **Client ID** y **Secret**.
  2. En esa app, «Add Webhook»: URL `https://xn--tierrasfantsticas-hpb.store/webhook/paypal` con los 6 eventos de
     arriba → copiar el **Webhook ID**.
  3. Cloudflare → Workers & Pages → tierras-fantasticas → Settings → Variables and Secrets: Secrets
     `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `PAYPAL_WEBHOOK_ID` (y, solo para probar con el sandbox,
     la variable `PAYPAL_ENV = sandbox`). Al guardar, el botón aparece solo.
  Stripe no ofrece PayPal a cuentas de México (solo Europa), por eso va aparte.
- **Auditoría**: página **404** propia (`public/404.html`; la sirve `src/index.js`: NO usar `not_found_handling` de
  Cloudflare, que dejaría de llamar al Worker en las navegaciones y rompería `/discord`, `/whatsapp` y el login con
  Discord), botón **volver arriba**, el bloque de Discord con **WhatsApp**, iconos para el móvil
  (`img/icon-180.png`, `img/icon-32.png`), `og:image`/`og:url` con la dirección completa (las vistas previas de
  WhatsApp y Discord no aceptan rutas relativas), `og:site_name`/`og:locale` y `sitemap.xml`. Pregunta de Ayuda
  sobre cancelar con lo de la retirada. `tools/preview.mjs`: `PREVIEW_PAYPAL=1` enseña el botón de PayPal.
- Pruebas: 44 (nuevas: retirada por reembolso, PayPal con webhook y API simulados). Mod 1.3.11 compilado.

### Entrega anterior (7 de octubre de 2026, tarde): portada con Discord/WhatsApp, nebulosa que se nota, botón esmeralda y aceptación de los Términos (solo web)
**Lo que pidió el dueño (textual):** *«quita eso de tu aventura comienza hoy, dejemos solo eso de tierras fantásticas
con eso de ip, ver tienda, etc, mira la descripción no es un reino de castillos, es un servidor survival, aventura,
fantasía y rol. Y no veo tan animada la nebulosa… y ahí abajo en forge 1.20.1 dice gratis, quita esa palabra, y debajo
de esas letras de forge pon el entra a nuestro discord el botón, grupo de whatsapp (https://chat.whatsapp.com/CqULG1UFixUJFuN3xwtXrm)
haz que ese link sea permanente… y pon el botón de cómo entrar al server, el botón de visitar la tienda ponlo en una
paleta de esmeralda azulada, el mismo efecto que tiene… La tarjeta de la tienda de los botones el efecto del botón va
de abajo hacia arriba, debe ir de izquierda a derecha, como los demás… Otra cosa, con lo legal, ¿es correcto hacer que
lean y acepten los términos y condiciones? ¿en qué me ayuda eso? ¿cómo se guarda eso? ¿firma?… hazlo tú»*.

- **Portada**: fuera «Tu aventura empieza hoy» (`#unete`) y la flecha de bajar. Queda el emblema, el título (ahora lo
  arma `hero_title()` de pages.py), la descripción nueva («Servidor de Minecraft de survival, aventura, fantasía y
  rol…»), la IP, «Visitar la tienda», «Minecraft Java 1.20.1 · Forge» (sin «Gratis») y debajo `.hero-links`: «Entra a
  nuestro Discord», «Grupo de WhatsApp» y «Cómo entrar al server» (`/ayuda#como-entrar`).
- **Enlaces permanentes**: los botones van a `/discord` y `/whatsapp` (rutas del Worker que redirigen con 302 a
  `DISCORD_URL`/`WHATSAPP_URL` de `wrangler.jsonc`). El enlace de un grupo de WhatsApp no caduca solo: deja de valer
  únicamente si un administrador lo «restablece» en WhatsApp; entonces basta con poner el nuevo en `WHATSAPP_URL` y la
  web sigue funcionando sin tocar nada más.
- **«Visitar la tienda»** (`.btn-shop`): el mismo destello deslizante y halo que respira del botón principal, con
  `--shimmer-sea` (esmeralda vivo → turquesa → azul) y `btn-breathe-sea`.
- **Relleno de izquierda a derecha**: las pestañas de la tienda (`.tab`) y los botones secundarios (`.btn-ghost`) ya
  no se llenan de abajo arriba.
- **Nebulosa que se nota**: la A tarda 24 s (antes 46) y se mueve más (desplazamiento, zoom y un giro leve), la B 15 s
  (antes 29), y una capa nueva `.neb-glow` (manchas de luz rosa, cian y violeta que cruzan el cielo en 18 s). Medido:
  en 6 segundos la portada cambia a simple vista. Sigue siendo solo transform/opacity y sigue con «reducir movimiento».
- **Aceptación de los Términos** (no había nada hecho):
  - Casilla obligatoria «He leído y acepto los Términos y condiciones y el Aviso de privacidad» al **crear la cuenta**
    y en la **ventana de pago** (sin marcarla no se habilita «Pagar»; el servidor también lo exige: 400 `terms`).
  - **Prueba guardada** en D1, tabla `terms_acceptances`: cuenta/UUID, nombre, dónde (`cuenta`, `compra`,
    `actualizacion`), versión, pedido, IP (`CF-Connecting-IP`), navegador y fecha. La cuenta guarda `terms_version`
    y `terms_at`; cada pedido, `terms_version`.
  - **Versión** = `config/legal.json` (`version` y `date`; `pages.py` saca de ahí la fecha que muestran las páginas
    legales). Si se cambian los textos legales, se sube la versión: las cuentas con una versión vieja ven en «Mi cuenta»
    un aviso y, al reclamar recompensas o comprar con monedas, la ventana «Actualizamos los Términos»
    (`POST /api/account/terms`). «Mi cuenta» muestra «✓ Aceptaste los Términos… el <fecha>».
  - Términos («Aceptación» y «Cambios») y Aviso de privacidad (datos, finalidades y plazo) explican la casilla y el
    registro (con IP, que antes decía que no se guardaba).
  - Para el dueño: en México la casilla marcada es consentimiento expreso por medios electrónicos (Código de Comercio,
    arts. 89 y siguientes; Código Civil Federal, art. 1803): no hace falta firma. Lo que da valor es poder demostrar qué
    versión aceptó cada uno y cuándo, que es lo que guarda la tabla. Recomendable que un abogado revise los textos.
- Pruebas: 41 (nueva: «los Términos se aceptan con una casilla…»). `tools/preview.mjs` registra a Notch aceptándolos.

### Entrega anterior (7 de octubre de 2026, mediodía): portada mínima, fondo vivo, destello en los botones y más vida en las crates (solo web)
**Lo que pidió el dueño (textual):** *«la landing page… te dije que no me la llenes de cosas… ni lo de crates, solo lo
principal y lo de tu aventura comienza hoy. Me gustaría que la nebulosa y las estrellitas las animes, que sea vivo el
fondo. La tarjeta de botones de la tienda se confunden, no hay armonía en los colores, me gusta el efecto que tiene
el botón ese tipo cambio de morado a azul destello, impleméntalo en más botones… donde dices que solo es para ti y
permanente mencionas ruleta, no tenemos ruleta… quita lo que dice de «sin letra pequeña», no pongas textos
redundantes o ambiguos… las crates u otras cositas les falta animación, detallitos visuales»*.

- **Portada mínima**: solo el héroe (emblema, título, IP, «Visitar la tienda») y «Tu aventura empieza hoy» (`#unete`).
  Fuera las cifras, los pilares «Una aventura sin final» y las crates (y su JS `renderHome` y su CSS). La portada ya no
  carga los productos (`sells` = tienda y crates). **No volver a llenarla.**
- **Fondo vivo** (`.sky` en cada página, `SKY` de pages.py; CSS «Fondo vivo»): la base fija sigue en
  `body::before` (`nebula-*.webp`, ahora con la nebulosa suave); encima la nebulosa A (`nebula-a-*`) se desplaza y
  respira (46 s), la B (`nebula-b-*`, nubes en otro sitio) aparece y se apaga (29 s), dos mosaicos de estrellas
  (`stars-a/b.webp`, puntos pequeños, **sin destellos en cruz: el dueño no quiere «sparkles»**) bajan muy despacio y
  titilan a destiempo, y en pantallas grandes cruza alguna estrella fugaz. Solo transform/opacity. En el móvil, solo
  la nebulosa A y un mosaico. Todo sigue con «reducir movimiento» (exento). Lo genera `tools/nebula_bg.py`.
- **Destello rosa → azul** (token `--shimmer`, el que le gustó del botón principal) también en: los botones
  secundarios (`btn-ghost`, se llenan desde abajo con el destello que se desliza), las **pestañas de la tienda** (cada
  una con fondo y filo propios; al pasar el ratón se llenan; la elegida lo lleva fluyendo, `shimmer-flow`), las
  pestañas de entrar/registrarse, los botones «Mi personaje / Objeto» del probador, los filtros de cosméticos y los
  «Copiar» de la IP.
- **Crates y tarjetas**: en el ordenador cada crate flota despacio a su ritmo (`crate-float`; al pasar el ratón se
  acerca con `scale`/`translate`, que no pisan la flotación); un destello cruza la tarjeta al pasar el ratón (crates,
  productos, tienda de monedas, cosméticos); la entrada de las tarjetas ya no bloquea la elevación suave al pasar el
  ratón (`animation-fill-mode: backwards`).
- **Textos sin ruleta y sin relleno**: bloque «Tus compras son para siempre» (`#vinculados`), pregunta de Ayuda y
  apartado de los Términos reescritos: hablan de «las armas, herramientas y armaduras de los rangos y de las crates».
  **No hay ruleta en la web: no la menciones.**

### Entrega anterior (7 de octubre de 2026, mañana): nebulosa de neón, Necros ⇄ Oni, objetos vinculados, alas que planean y TF Client 1.3.10
**Lo que pidió el dueño (textual):** *«la crate Necros la vas a quitar de crates y la vas a intercambiar con el set del
rango cósmico, el set de rango cósmico pasa a ser crate. Y con respecto al fondo, hazlo más opaco, haz más como
nebulosa neón el fondo, las tarjetas de botones no se distinguen… no hay armonía con el diseño y colores, no hay
transiciones de página suaves, no veo nada de eso, los botones como que les falta animación al colocar mi ratón…
Haz que todas las alas cosméticas sean como elytras, que nunca se gasten ni tengan durabilidad… estos kits de los
rangos y los que compran son permanentes por lo tanto son indestructibles, no son transferibles… debes explicarlo
con transparencia, estos trajes y todo lo que se compre en la tienda a excepción de cosméticos o ropa estarán
vinculados a la uuid del jugador, es decir que al dropearlos o ponerlos en cofres ninguna persona podrá usarlos ni
ponérselos que no sea su dueño… has más despacio la animación de girar al mostrar el modelo»*. Y después: *«ojo,
solo alas, mochilas y otras cosas no, solo ALAS»*.

**Necros ⇄ Oni** (`tools/crates.py`): el rango **Cósmico** da ahora el set **Necros** (netherita +1,5) y **Oni** es
una crate (la 2.ª de la lista, 4,49 USD, netherita +1, colores `#fda4af`/`#e11d48`, portada `img/crates/oni.webp`).
`RANK_PITCH['cosmico']` describe Necros sin nombrarlo. En el mod, `tf_sets.json` lleva los niveles y colores nuevos
(sin rehacer modelos). Las pruebas usan `crate-oni`.

**Web:**
- **Fondo «nebulosa de neón»**: `tools/nebula_bg.py` (semilla fija, reproducible) oscurece la imagen nocturna y le
  pone encima una nebulosa rosa, violeta y cian con estrellas → `public/img/nebula-{768,1280,1672}.webp` (12–45 KB).
  Es la misma capa fija de antes (`body::before`, con el acercamiento lento); `body::after` ya solo es la viñeta. Las
  `night-*.webp` siguen en el repo (el pilar «Reinos y castillos» usa `night-768`).
- **Tarjetas que se distinguen**: `.panel`/`.frame` son opacas y más claras que el fondo (token `--surface`), con
  filo claro (`--edge` 0,30), línea de neón arriba, sombra que las despega y halo violeta. La cabecera fija es opaca.
- **Armonía**: una sola paleta (rosa neón, violeta, cian sobre índigo). Los botones de comprar de cada crate
  (`btn-theme`) ya no son del color de la crate: son iguales que `btn-primary`; el color de cada crate queda en su
  franja de arriba, su brillo y el texto de piezas.
- **Botones**: al pasar el ratón se elevan y crecen (muelle), el degradado se desliza del rosa al cian y el halo
  «respira» (`@keyframes btn-breathe`); `btn-ghost` se llena de rosa desde abajo; los iconos se mueven.
- **Transiciones de página en todos los navegadores** (antes `@view-transition`, solo Chrome, y además apagada con
  «reducir movimiento», que es como tiene el dueño Windows: por eso no las veía). Ahora `app.js` intercepta los
  enlaces internos, pone `.leaving` en `<html>` (el contenido se desvanece hacia arriba y una barra de neón
  `.page-bar` corre arriba) y navega a los 280 ms; la página nueva entra subiendo (`page-in`). Están exentas en el
  bloque `prefers-reduced-motion`, igual que el halo de los botones y las apariciones al bajar.
- **Filo de neón** bajo cada cabecera de sección (`.section-head::after`, se dibuja al aparecer).
- **Transparencia** (textos rehechos en la entrega siguiente): bloque al final de la tienda (`#vinculados`), dos
  preguntas nuevas en Ayuda (regalar/intercambiar y alas), apartado **«Objetos permanentes y vinculados a tu
  cuenta»** en los Términos (`tools/legal.py`, `LEGAL_DATE` = 7 de octubre de 2026), una línea en el probador de
  crates y rangos y en la introducción de las crates.
- **Giro del modelo 3D más lento** y por tiempo (igual a 60 o 144 Hz): probador/rangos ~35 s por vuelta
  (`wardrobe.js`), visor de objetos ~29 s (`viewer.js`).

**Mod 1.3.10:**
- **Objetos permanentes** (`items/TFItem.java`, interfaz que implementan todos los tipos de `TFItemTypes`): sin
  durabilidad (`isDamageable` → false: no se gastan, no hay barra, Reparación no gasta XP en ellos) y, en el suelo,
  invulnerables, sin desaparecer nunca y rescatados del vacío (`TFBinding.protect`).
- **Vinculados a la UUID** (`items/TFBinding.java`): NBT `TFOwner` (UUID) y `TFOwnerName`. Se vinculan los objetos
  de los sets que **no** son cosméticos: ni `Cosmetic` (cabeza/espalda) ni `Held` (de mano/globos) ni los sets de
  cosméticos (`"cosmetic": true` en `tf_sets.json`). `/tf web sets give` vincula al entregar (la ruleta usa ese
  comando). Cada tick: lo que no tiene dueño se vincula a quien lo lleva (fuera de creativo; así se migran los de
  antes) y lo que es de otro se le quita y **vuelve a su dueño** (a su inventario o, si no está conectado, se guarda
  en `data/tfclient_returns.dat` del mundo y se le da al entrar). Solo el dueño puede cogerlo del suelo
  (`EntityItemPickupEvent` + `ItemEntity.setTarget`); atacar, usar y romper bloques con algo ajeno se cancela.
  Creativo y espectador están exentos (staff). En la descripción: «Irrompible · vinculado a <nombre>».
- **Alas que planean como élitros, solo las alas** (49): `"glide": true` en `tf_sets.json` (lo pone
  `build_mod_items.py`: `glides()` = la clase de `KIND_BY_TAG` o el nombre termina en `wing`/`wings`; el «wing» de
  Pascua es un peluche-mochila y no planea, la «frozen_backpack» son alas y sí). Mochilas, capas, colas, carcajes y el
  caldero no planean. En el pecho lo hace el propio objeto (`Cosmetic.canElytraFly`/`elytraFlightTick` → sin gasto);
  en el hueco de la espalda de Accessories/Curios (con la pechera puesta), `mixin/ItemStackMixin` (común, cliente y
  servidor) declara en `ItemStack` los métodos de Forge `canElytraFly`/`elytraFlightTick` y, si el objeto del pecho no
  planea, mira `TFWings.fromBackSlot` (lee los huecos con `TFAccessoryLookup`, la reflexión que antes estaba en
  `TFAccessorySlots`, ahora común).
- `build_mod_items.py`: `mark_flags()` pone `cosmetic`/`glide` también en los sets que no se rehacen, y en ese modo
  actualiza el color además del nivel.
- `check_mod_items.py`: 0 errores. Compilado (bytecode del mixin revisado: `getItem` remapeado a `m_41720_`).
  **No se ha probado dentro del juego** (regla: no se lanza el juego): si el dueño ve algo raro al planear con las alas
  en el hueco de la espalda, mirar el log por «TF Client: alas».

### Entrega anterior (7 de octubre de 2026, madrugada): fondo nocturno, neón en bordes, comandos de los rangos y TF Client 1.3.9
Termina el checkpoint de la otra IA (rama `claude/amazing-ritchie-68hm6n`, commit `b421e19`: trajes de espalda
centrados en el cuerpo, alas de Eagle pegadas, armas dobles de Oni visibles e intercambio de sets Celestial ⇄
Fantástico; detalle en el mensaje de ese commit y en «Herramientas de revisión» abajo).

**Lo que pidió el dueño (textual):** *«las alas del Fantástico siguen un poco separaditas… el diseño neón de las
letras no me gusta, pon ese fondo [una imagen nocturna: luna, castillo, río, islas flotantes] y mejora el efecto de
neón, algo muy profesional… revisa con seriedad todos los modelos… debajo del rango no me pusiste lo que trae el rango
como comandos… algunas armas y poses se ponen mal, unas armas no aparecen… el logotipo no tiene animación en mi PC,
mejora las animaciones, transiciones, transiciones de letras… en la leyenda abajo pon copyright, nadie puede
distribuir esto fuera del server, y en una esquina abajo «By Pewez777», discreto»*. Y: *«cambia el set fantástico al
set celestial»* (hecho en el checkpoint).

**Web:**
- **Fondo**: la imagen nocturna del dueño (`public/img/night-{768,1280,1672}.webp`) es el fondo fijo de toda la web
  (`body::before`: imagen + velo oscuro, con un acercamiento muy lento, `@keyframes night-drift`; `body::after`: luz
  de neón suave en las esquinas y viñeta). En el móvil usa la de 768 px. Las cabeceras (portada, tienda, páginas
  interiores) ya no llevan imagen propia: `hero_art()` de `pages.py` devuelve vacío y dejan ver el fondo. La banda
  final usa `night-1280`. El pilar «Reinos y castillos» usa un recorte del castillo de esa imagen. El arte de día
  (`hero-*.webp`) solo queda en `/mundo`.
- **Sin neón en las letras**: el título de la portada solo tiene una sombra oscura; `.text-grad` es un color liso
  (`#e8cdfc`), sin degradado ni animación; el nombre del rango tampoco brilla.
- **Neón profesional en bordes y botones**: tokens `--edge`, `--edge-hot`, `--haze`, `--haze-hot`. Las tarjetas
  (`.panel`/`.frame`) tienen un filo violeta fino, una línea de luz arriba y un halo suave; al pasar el ratón se
  encienden con su color (crates, rangos, pilares, productos, monedas). `.btn-primary` lleva filo rosa claro y halo;
  `.btn-ghost`, filo violeta que se vuelve rosa. Todo son sombras estáticas (nada animado caro).
- **Emblema**: entra con un salto (`logo-in`), flota, su halo late y un destello lo cruza recortado a su silueta
  (`.logo-mark .shine`, máscara con `logo.webp`). Con «reducir movimiento» (Windows con animaciones apagadas, el PC
  del dueño) **siguen** el emblema, su halo y el destello, el fondo que se acerca y los brillos: están exentos en el
  bloque `prefers-reduced-motion` del final de `styles.css`.
- **Letras**: el título de la portada entra letra a letra (`pages.py` parte «Tierras Fantásticas» en `<span
  class="ch" style="--i:N">` dentro de `.w`, con `aria-label` para lectores de pantalla); los títulos de sección
  (`.section-head.reveal`) se descubren de abajo arriba al aparecer y la etiqueta y el texto entran escalonados.
- **Pie**: `.copyright` («© año Tierras Fantásticas. Todos los derechos reservados. Prohibida la copia, reventa o
  distribución del contenido del servidor… fuera de Tierras Fantásticas. Las marcas y contenidos de terceros
  pertenecen a sus dueños.») y `.credit` «By Pewez777» pequeño en la esquina inferior derecha.
- **Rangos: comandos debajo del escaparate** (`#rk-cmds` en `renderRanks`/`showRank` de app.js; datos en
  `RANK_SERVER_PERKS` de `tools/crates.py` → `serverPerks` en products.json). Acumulado: cada rango trae lo del
  anterior y marca «Nuevo» lo suyo (sale primero). Solo comodidad y aspecto (normas de Mojang). Ver «Rangos: comandos
  y permisos» abajo: **el dueño tiene que dar esos permisos en LuckPerms**.
- `pages.py` necesita **Python 3.12** (usa barras invertidas dentro de f-strings): `python3.12 tools/pages.py`.

**Mod 1.3.9** (además de lo del checkpoint):
- Revisadas las **759 armas y objetos de mano** en el personaje (`tools/review/hands.cjs` + `hsheet.py`) y los **52
  cascos y sombreros** de frente y de perfil (`tools/review/heads.cjs` + `headsheet.py`, nuevos). Los cascos están
  todos bien colocados. En las armas, casi todo es como lo diseñó cada pack (las ballestas, por ejemplo, se llevan
  verticales por la empuñadura), con estos fallos, ya corregidos:
  - **Caña de pescar de Cyber** (`cyber_rod`): estaba registrada como espada (no pescaba y tenía pose de espada).
    `item_type()` reconoce ahora `rod` como caña (con su modelo al lanzar, `rod_cast`). Se rehizo solo el set
    `cyber` con `build_mod_items.py <packs> cyber`.
  - **Ballestas de Eagle, Mecha y Pirata**: el pack las empujaba 10, 5 y 4,5 píxeles hacia delante y en tercera
    persona colgaban a la altura de los pies. Regla nueva `tame_held()` (máximo 3 píxeles, `HELD_MAX_FORWARD`) para
    las ballestas y sus modelos de carga; ya aplicada a los modelos actuales sin rehacer los sets.
  - **Espadón de Dark World** (`bigsword`): tenía los combos de espada normal; ahora los de espadón (`claymore`).
- `check_mod_items.py`: 0 errores. Mixins correctos. Compilado con Java 21.

**Rangos: comandos y permisos (ANTIGUO, sustituido en 1.3.15: ahora los pone el TF Client solo)** (lo que había que dar en LuckPerms/EssentialsX;
si algo no se va a dar, quitarlo de `RANK_SERVER_PERKS` en crates.py y regenerar):
| Rango | Nuevo en este rango | Permisos (además de los del rango anterior) |
| --- | --- | --- |
| Mortal | 2 hogares, prefijo, rol de Discord | `essentials.sethome`, `essentials.home`, `essentials.sethome.multiple` + en `config.yml` de EssentialsX `sethome-multiple: {mortal: 2, inmortal: 3, magico: 5, eterno: 8, cosmico: 12, celestial: 20}` y `essentials.sethome.multiple.<grupo>` en cada grupo |
| Inmortal | 3 hogares, `/hat` | `essentials.hat` |
| Mágico | 5 hogares, `/fly` en el lobby, `/pp` | `essentials.fly` solo en el mundo del lobby (`lp group magico permission set essentials.fly true world=<lobby>`); plugin **PlayerParticles** con sus permisos |
| Eterno | 8 hogares, `/nick` con colores, chat de colores | `essentials.nick`, `essentials.nick.color`, `essentials.chat.color` |
| Cósmico | 12 hogares, `/ptime`, cola prioritaria | `essentials.ptime`; la cola depende del plugin de cola que use el servidor |
| Celestial | 20 hogares, `/pweather`, aviso al entrar | `essentials.pweather`; el aviso de entrada necesita un plugin de mensajes por grupo |
| Fantástico | hogares ilimitados, título propio, nombre dorado | `essentials.sethome.multiple.unlimited`; el título es un sufijo de LuckPerms que pone el staff (`lp user <jugador> meta setsuffix 100 "&6[Título]"`) |

**Herramientas de revisión** (`tierras-fantasticas/tools/review/`; necesitan `node tools/preview.mjs` en el puerto
8788 y se lanzan con `PW=$(npm root -g)/playwright node …`; no regeneres `public/wear/` mientras corren):
- `backs.cjs <carpeta> backs.json` → perfil/espalda/3-4 de cada cosmético de espalda; `bsheet.py` → hojas.
- `hands.cjs <carpeta> hands.json` → cada arma en la mano; `hsheet.py <carpeta> hands.json <prefijo> 6 5` → hojas.
- `heads.cjs <carpeta> heads.json` → cada casco de frente y de perfil; `headsheet.py <carpeta> heads.json <prefijo>`.
- `audit_hand.py` → auditoría de datos de los objetos de mano del mod.
- `tf-client/tools/check_backs.py [--fix]` mide la separación de lo que va en la espalda.

**Pendiente / ideas** (nada urgente): el dueño tiene que crear en LuckPerms los grupos de rango y los permisos de la
tabla; Stripe sigue en pausa.

### Última entrega (7 de octubre de 2026, tarde): paleta de neón, fondo animado, alas revisadas y TF Client 1.3.8
El dueño: *«las alas del fantástico quedan un poco separadas… revisa todas hasta los cosméticos… un libro está mal
puesto… en los rangos no quiero ver esa descripción debajo [la tabla], solo los beneficios, y no pongas cuántas piezas
traen, y haz que pueda equipar las piezas para el preview… no me está gustando el fondo… no veo nada en mi PC
animándose… pon un degradado bonito neón… dale rework a todo, paletas, todo»*.
- **Paleta nueva «noche de neón»** (tokens en `:root` de styles.css): fondo índigo casi negro, superficies violeta
  oscuro, acento rosa neón (`--a-*`, antes `--g-*` verde), violeta y cian; el verde solo queda para «en línea»
  (`--ok`). Los colores con transparencia usan `rgb(var(--…-rgb) / x)`. Botón principal y pestaña activa con el
  degradado `--pri-grad` (rosa → violeta → índigo). Tarjetas algo transparentes con un filo de neón arriba; cabecera
  con línea de neón; título de la portada con brillo de neón; textos `.text-grad` con el degradado moviéndose.
- **Fondo animado** (permitido por el dueño, que antes no quería animaciones de fondo): `body::before` es UNA capa
  fija con una aurora rosa/violeta/cian/azul que se mueve muy despacio (`@keyframes aurora`, solo transform y
  opacidad); `body::after`, la rejilla de bloques. **«Reducir movimiento»** (Windows con «efectos de animación»
  apagados, probablemente el PC del dueño): antes apagaba TODO; ahora quita los desplazamientos pero deja la aurora
  respirando (opacidad), el brillo de crates/rangos y las transiciones de color.
- **Rangos**: fuera la tabla comparativa y el número de piezas; las piezas del set se tocan para ponérselas o
  quitárselas en el personaje («Set completo» lo restablece).
- **Cosméticos de espalda revisados uno a uno** (los 67): `tf-client/tools/check_backs.py` mide la separación de la
  parte central con la espalda; 16 quedaban separados (Eagle/Fantástico, Necros, Luminite, Mecha, Wither, Frostbite,
  Beats, Ifrit, el hielo de Aventura…) y se acercaron. La regla está en `build_mod_items.py` (`snap_to_back`), así
  que no se pierde al regenerar. Los de mano (libro de mago, katanas ninja) usan en las dos manos la posición de la
  izquierda, que es la que diseñó el pack (`held_display`).

### Entrega anterior (7 de octubre de 2026): pestaña de rangos nueva, pestaña de cosméticos y TF Client 1.3.7
El dueño: *«no me gusta esa pestaña de rangos… no quiero que se vea como la pestaña de crates… sus beneficios no digas
el nombre del kit, tampoco en la tienda de monedas digas actualizado en tiempo real… presenta el set completo animado
como si lo tuviera equipado y la foto de mi skin… vende mejor los rangos… y agrega una pestaña de cosméticos… véndelos
bonito, con sus animaciones»*.
- **Rangos** (`renderRanks`/`showRank` en app.js, estilos «Rangos: la escalera y el escaparate»): escalera de los 7
  rangos arriba y un escaparate con UN solo lienzo 3D: el personaje con la skin del jugador (la de su cuenta o la que
  escriba en «Skin de») y el set del rango puesto, girando; el nombre `[Prefijo] Jugador` flota sobre la cabeza y la
  sombra sigue los pies (`onAnchors` de wardrobe.js). Al lado: «Rango N de 7», nombre en su color, frase, texto de
  venta (`RANK_PITCH` en crates.py), cómo se ve en el chat, ventajas, piezas y comprar/mejorar. **Nunca se nombra el
  kit** (ni en ventajas ni en la tabla). `/tienda#rangos-<clave>` elige un rango.
- **Tienda de monedas**: quitado «Actualizado en vivo desde el servidor».
- **Cosméticos** (pestaña nueva, `renderCosmetics`): 45 piezas de 5 packs (Halloween 2023, Halloween Bundle,
  Cosmetics Expansion Vol. 1, Unicornio y las alas + corona del Spring Season), en `COSMETICS` de crates.py (colección,
  tema, tipo y precio por pieza, **precios provisionales**: cabeza 1,99, espalda 2,49/2,99, mano 1,49, globo 0,99).
  Escenario 3D con lo que te pruebas (se combinan piezas de distintas colecciones), filtros por colección, «Probar
  conjunto» y compra por pieza (`tf web sets give {player} <set> <pieza>`). Solo aspecto, sin atributos.
- Del pack **Spring Season** solo se usan las alas y la corona (sus cosméticos); sus armas y armadura no están en la
  web: si el dueño lo quiere, puede ser una crate más.
- Mod 1.3.7: tipos nuevos `held` (cosmético en la mano, `TFItemTypes.Held`) y `balloon` (globo: va en la mano y
  flota por encima con su cuerda; `balloon_display` en build_mod_items.py encoge el modelo para que quepa en los
  límites de Minecraft e inclina la cuerda hacia fuera). `hmc_worn.py` ahora resuelve los cosméticos que HMCCosmetics
  pide por número (material + model-data) con la config de ItemsAdder. Revisado cada conjunto puesto en el probador.
- Probador: ya dibuja lo de la mano izquierda (globos y escudos), como Minecraft (reflejado).

### Entrega anterior (6 de octubre de 2026, noche): 7 rangos con su set, crates cambiadas y TF Client 1.3.6
El dueño (textual, *«no lo repetiré 2 veces»*): rangos de mayor a menor **Fantástico** (kit Eagle Ascendant),
**Celestial** (Luz de Estrella, sacado de las crates), **Cósmico** (Oni; desde 1.3.10 es **Necros** y Oni es crate), **Eterno** (Malika), **Mágico** (Dark World),
**Inmortal** (Beats, sacado de las crates) y **Mortal** (San Patricio, sacado de las crates). Los huecos de las crates
los llenan **Akira** (donde estaba Luz de Estrella), **Cardael** (Beats) y **Evergreen** (San Patricio), y los kits
subidos que no nombró van a crates: **Soul Skull** → hay **41 crates** (no 40) y 7 rangos. **Happy New Year 2026 NO
se sube hasta que el dueño lo diga** (el zip se subió, pero no está en `SETS`).
- Atributos de menor a mayor (pedido: *«el mínimo son atributos de hierro, diamante, netherita y los últimos 2 a más
  2 o 3 puntos más que la netherita»*): Mortal **hierro**, Inmortal **diamante**, Mágico **netherita**, Eterno
  **netherita +1**, Cósmico **+1,5**, Celestial **+2**, Fantástico **+3** (+N = N más de daño, de armadura por pieza y
  de dureza). Eterno y Cósmico no los fijó el dueño: se eligieron para que suba en orden. Las crates siguen en +1.
  Definidos en `RANKS` de `tools/crates.py` → `tier` en `tf_sets.json` → `items/TFTier.java`. Siguen ocultos.
- **Precios de los rangos provisionales** (el dueño no los dio; Stripe en pausa): 4,99 / 7,99 / 11,99 / 15,99 /
  19,99 / 24,99 / 29,99 USD, en `RANKS`. Cada rango da `lp user {player} parent add <grupo>` (grupos `mortal`,
  `inmortal`, `magico`, `eterno`, `cosmico`, `celestial`, `fantastico`; **el dueño tiene que crearlos en LuckPerms**
  con su prefijo) y `tf web sets give {player} <set>`. Al mejorar se quitan los grupos inferiores (ya existía).
- La tarjeta del rango lleva la portada de su set y abre el probador (`/tienda#rangos-<clave>`). Los textos que
  decían que los rangos «no dan ventaja» se quitaron (ahora dan un set con atributos).
- Mod: `build_mod_items.py <packs> <set ...>` rehace **solo** esos sets y deja el resto (los packs originales de los
  otros 40 no están en el contenedor). Se rehicieron eagle, akira, oni, malika, evergreen, darkworld, cardael y
  soulskull; `check_mod_items.py` sin errores; cada set se revisó en el probador (frente, lado y espalda: las alas
  quedan en la espalda) y cada objeto suelto. Dark World traía una animación que pedía un fotograma que no existe:
  el script la corrige. Las alas de Eagle (a la altura de la cintura) y de Oni (algo bajas) se subieron con
  `Y50_BY_TAG` en `build_mod_items.py`. Nuevos tipos: `crown` (cabeza) y `fishingrod` (caña).
- Probador: en la vista «Objeto» solo se ponen de pie las armas y herramientas; cascos, alas y escudos se ven como en
  el inventario (antes el casco de Dark World salía tumbado y las alas de Akira en vertical).

### Entrega anterior (octubre de 2026): rediseño completo + cumplimiento de Mojang + páginas legales + TF Client 1.3.4 → 1.3.5
El dueño rechazó el estilo «fantasía» (dorado, Cinzel, biseles): *«no veo un rediseño completo… todo está crudo…
rediseña todo con otro concepto… es minecraft, la tipografía cámbiala pero no a una de píxeles… animaciones en los
botones, transiciones, loading»*. Y después: *«revisa que nuestros productos no incumplan los términos de Mojang…
aclarar que no estamos afiliados… todo el tema legal, políticas de privacidad… nada debe estar con cabos sueltos»*.

**Diseño nuevo («Minecraft moderno»)** — `public/styles.css` reescrito desde cero (orden: tokens → base → carga →
botones → cabecera → portada → bloques comunes → páginas legales → pie → tienda → ventanas → cuenta → probador →
animaciones → tamaños de pantalla). Edita la sección que toque; no añadas parches al final.
- Paleta: noche `--bg #0a0d14`, superficies `--s1/--s2/--s3`, **esmeralda** (`--g-*`, acción principal),
  **amatista** (`--violet`), oro solo para monedas. Tipografías **Unbounded** (titulares, botones, precios) y
  **Figtree** (texto), de Google Fonts.
- Botones `.btn` con volumen de bloque: labio inferior (`--lip`, `--lift`), se elevan al pasar el ratón con un
  destello que los cruza y se hunden al pulsar. Variantes: `btn-primary` (verde), `btn-ghost`, `btn-discord`,
  `btn-coin` (tienda de monedas), `btn-theme` (color de cada crate, `--t1/--t2/--t3`), tamaños `btn-sm`/`btn-lg`.
  `.is-loading` pone una rueda delante del texto (se usa al reclamar, pagar, entrar y comprar con monedas).
- Tarjetas `.panel`/`.frame` con borde suave, sombra y una franja de color arriba (crates y productos); se elevan
  al pasar el ratón. Los rangos usan el color de su prefijo (`rankAttr` en app.js).
- **Pantalla de carga** solo en la primera página de la visita (script `BOOT` en `pages.py`, `sessionStorage`
  `tf-seen`, mínimo 0,7 s y máximo 3 s); en las siguientes, **transición entre páginas** con `@view-transition`.
  El héroe y las cabeceras entran escalonados cuando la página está lista (clase `ready` en `<html>`).
- **Esqueletos** con brillo mientras carga la tienda, la portada y la cuenta (`skeletons()` en pages.py); las
  imágenes aparecen con un fundido al cargar (clase `fx` + `ok`, en `img()` de app.js); las cifras de la portada
  suben desde 0; las barras, pestañas, preguntas frecuentes (se abren con transición de altura) y el menú del móvil
  están animados. Todo con transform/opacity y apagado con «reducir movimiento». Sin backdrop-filter ni capas fijas.
- Se quitó el grano fijo de fondo. El brillo de color de las crates se mantiene (el dueño lo quiere).

**Normas de Mojang** (EULA + *Minecraft Usage Guidelines*). Revisado producto por producto y corregido con el visto
bueno del dueño, **salvo los atributos de las crates** (ver abajo):
- **Ruleta retirada** de la web (lo que se parezca al juego de azar está prohibido, y daba objetos con ventaja). El
  dueño preguntó si con monedas ganadas jugando estaría permitida: Mojang prohíbe en general «anything meant to
  resemble gambling mechanics», así que se dejó fuera; el código sigue (ver README) por si se rehace de forma permitida.
- **Monedas con dinero retiradas** (las monedas solo se ganan jugando; con dinero comprarían diamantes, netherita o
  élitros en la tienda de monedas = ventaja). La tienda de monedas sigue, pagada con monedas del juego.
- **Rangos sin ventajas**: fuera kits de objetos, el «acceso al mundo de recursos» (no se puede cobrar por partes del
  servidor) y /ec. Quedan prefijo, hogares, /fly en el lobby, /hat y partículas, mascota cosmética, /nick, cola
  prioritaria, título y color, rol de Discord. **El dueño tiene que quitar en LuckPerms/EssentialsX los kits y /ec de
  los rangos y abrir el mundo de recursos a todos** (se le dijo). Después decidió que cada rango da un set completo
  con atributos (ver «Última entrega»): los rangos actuales son los 7 de arriba.
- **Atributos de las crates — DECISIÓN DEL DUEÑO (contra la recomendación):** la 1.3.4 los bajó a hierro para
  cumplir; el dueño pidió (*«ponles atributos 1 punto mayor a la netherita… no vamos a decir qué atributos tiene…
  mojang no se va a meter»*) y la **1.3.5** les pone la netherita +1 (+1 de daño; armadura 4/9/7/4, dureza 4,
  empuje 0,2; no se queman en lava) y oculta los atributos en la descripción del objeto (la barra de armadura del
  juego sí los refleja). Se le explicó que eso es la ventaja pagada que prohíbe Mojang y que puede acabar en bloqueo
  del servidor; es su decisión y su riesgo. **La web no puede decir nada falso**: se quitaron «valores de hierro»,
  «no dan ventaja» y «cumplimos las normas de Mojang»; la web simplemente no habla de atributos. No vuelvas a poner
  esas frases mientras los atributos sean estos. El contenido de las crates sigue siendo fijo y visible (no son cajas
  al azar).
- «Donaciones» → «compras» (Mojang exige llamar a las cosas por su nombre).

**Páginas legales** (textos en `tools/legal.py`, generadas por `pages.py`; fecha en `LEGAL_DATE`):
- `/legal` — quiénes somos, **no afiliación** (frase obligatoria en español e inglés), cómo funciona la tienda
  (sin azar, monedas solo jugando, sin desbaneos), propiedad intelectual. `/terminos` — cuentas, menores, qué se compra, precios en USD finales, entrega, **5 días
  hábiles para cancelar** (art. 56 LFPC) y reembolsos, cambios del servidor, PROFECO. `/privacidad` — aviso de
  privacidad según la **LFPDPPP de 2025** (datos, finalidades necesarias y voluntaria, terceros, derechos ARCO en 20
  días hábiles, SABG como autoridad, cookies y almacenamiento).
- Responsable: **Equipo de Tierras Fantásticas**, México. Contacto público (sin registro, lo exige Mojang):
  **tierrasfantasticasmc@gmail.com**. **Pendiente del dueño**: la ley pide un domicilio en el aviso de privacidad;
  ahora pone «con domicilio en México». Si da uno (puede ser un domicilio para notificaciones), añadirlo en
  `PRIVACIDAD['responsable']` de `tools/legal.py`.
- Si cambia lo que hace la web (datos que guarda, servicios externos como mc-heads.net, mcsrvstat.us, Google Fonts,
  Stripe, Discord, Cloudflare) hay que actualizar `tools/legal.py` y `LEGAL_DATE`.
- Aviso de no afiliación en el pie de todas las páginas, bajo la tienda y en la ventana de compra (con la aceptación
  de los términos). En «Mis compras» y en la página del pedido, lo gratis sale como «Gratis» y lo de monedas como
  monedas (antes «$0.00»).

## 1. Qué es el proyecto

Tierras Fantásticas es un servidor de Minecraft Java **1.20.1 Forge** (corre en Mohist, hosting **Ultra Servers**,
IP `216.163.187.40:19001`) con +200 mods. Este repo tiene:

| Carpeta | Qué es |
| --- | --- |
| `tierras-fantasticas/` | La web/tienda. Cloudflare Workers + D1 + archivos estáticos (`public/`). Dominio `tierrasfantásticas.store` = `https://xn--tierrasfantsticas-hpb.store`. |
| `tf-client/` | Mod Forge «TF Client» (va en el cliente y en el servidor): menú y pantalla de carga propios, puente con la web, objetos de los sets (crates), oficios (`/tf jobs`), tienda de monedas, ruleta (la de la web está retirada). Versión actual **1.3.12**. |
| `wrangler.jsonc` | Configuración del Worker de Cloudflare (en la raíz a propósito). |
| `.github/workflows/` | `tf-client.yml` compila el mod en cada push que toque `tf-client/` (artefacto `tfclient-jar`); `server-ping.yml` comprueba el servidor. |

Los demás archivos de la raíz (`.xlsm`, `.apk`, `.docx`, `videos/`) **no son del proyecto**: no los toques ni los subas.

READMEs detallados: `tierras-fantasticas/README.md` (web, Stripe, Discord, puente, productos) y `tf-client/README.md`
(mod, comandos, economía, oficios).

## 2. Reglas del dueño (respétalas siempre)

- Habla con él **en español**, claro y sin rodeos. Es exigente y se enfada (con razón) si se repiten errores: lee bien
  lo que pide, haz exactamente eso y **no añadas cosas que no pidió**.
- «dale, y no me pidas permiso para la otra»: cuando el trabajo esté terminado y probado, **fusionar y publicar sin
  preguntar**. Flujo: PR en borrador → marcarlo listo → *squash merge* → reiniciar la rama:
  `git fetch origin main && git checkout -B claude/amazing-wozniak-gtw9ll origin/main && git push --force-with-lease -u origin claude/amazing-wozniak-gtw9ll`.
  Tras fusionar, esperar el despliegue de Cloudflare y **comprobar la web en vivo**.
- Los mensajes de commit terminan con las líneas de atribución de la sesión (Co-Authored-By / Claude-Session) y las
  descripciones de PR con «🤖 Generated with Claude Code» y el enlace de la sesión.
- **Cada vez que cambie el mod**: subir la versión (siguiente: **1.3.20**) en `tf-client/gradle.properties`
  (`mod_version`) y en `TFClient.VERSION`, compilar y **mandarle el `.jar`** (como archivo adjunto).
- **Nunca** lanzar el juego ni un servidor de Minecraft. El mod se comprueba compilando y simulando (p. ej. la ventana
  de oficios se simuló con PIL usando la textura del cofre de vanilla y el arte real).
- **Secretos** solo en Cloudflare (Secrets), **nunca** en el chat ni en git (lista en la sección 4).
  Stripe va en **modo real** (no quiere modo de prueba; ya se enfadó por eso).
- Comandos del mod: **solo** `/tf web …` (staff), `/tf jobs`, `/tf shop` (este lo pidió él en 1.3.12) y
  `/tf claims` (las protecciones, 1.3.18). Los VFX
  no tienen comandos para jugadores: se equipan en la web y en el juego se activan solos. No añadas
  más raíces, alias ni comandos para jugadores (se quejó dos veces de «un vergo de comandos» y de tener `jobs` y
  `oficios` a la vez).
- **TF Client es el mod «nodriza»**: todo va dentro de él (puente con todo). No metas otros mods como dependencia
  (p. ej. Fantastic Currency: de ahí solo se sacó la textura de la moneda de oro, la Fantastic Coin).
- Ventana de oficios: el arte del pack encima de un cofre de 5 filas; **abajo, en el sitio del inventario, solo
  botones** (nunca los objetos del jugador). Está como él quiere en la 1.3.3: no la cambies sin que lo pida.
- Web:
  - Nada de «estrellitas», partículas ni figuritas animadas. El **brillo de color de cada crate** sí (solo opacidad).
    Desde octubre de 2026 el dueño **pidió un fondo de degradado neón animado**: es una sola capa fija (aurora) que
    solo mueve transform/opacidad; no añadas más capas animadas encima.
  - Que **no vaya lenta en el móvil**.
  - Portada ligera (no meter todo en la primera página) y con el **emblema**.
  - Crates, no «llaves»: se compra el pack (la crate), nunca se habla de llaves; sin etiquetas de rareza ni
    «edición limitada», «más popular», etc.
  - Diseño actual: «noche de neón» (paleta rosa/violeta/cian, ver sección 0) sobre la base «Minecraft moderno».
    Tipografías Unbounded + Figtree, **nunca** una de píxeles.
  - Rangos: nunca el nombre del kit ni cuántas piezas trae; sin tabla comparativa.
  - **Normas de Mojang (para productos nuevos):** nada que dé ventaja sobre quien no paga (kits de objetos no; los sets
    de las crates son la excepción que decidió el dueño, ver sección 0), nada al azar ni parecido al juego de azar
    (sin ruletas, llaves ni cajas sorpresa), no vender monedas del juego con dinero, no cobrar por zonas del servidor,
    ni desbaneos ni herramientas del staff, contenido para todas las edades, llamar «compras» a las compras.
  - Aviso de no afiliación con Mojang/Microsoft siempre visible y páginas legales al día (`tools/legal.py`).

### Historial de quejas (para no repetirlas)
- 1.3.1: «rediseño horrible», portada sobrecargada, quitaste el logo → se volvió a poner el emblema y se aligeró.
- «no te pedí estrellitas», «el diseño es una mierda», «se laguea en el teléfono» → se quitaron partículas, aurora
  animada y desenfoques (1.3.3).
- «no veo nada animándose en mi PC… pon un degradado neón… rework a todo» (oct. 2026) → paleta de neón y aurora
  animada; «reducir movimiento» ya no apaga los brillos. «Las alas del fantástico quedan separadas» → check_backs.py.
- «arruinaste la textura de los jobs» → se volvió al arte de la 1.3.0 sobre el cofre, con botones abajo (1.3.3).
- Alas/cascos de las crates mal colocados → se arregló leyendo la configuración de HMCCosmetics de cada pack.
- «rediséñala en serio, mira cómo están de bonitas en internet» → estilo fantasía (dorado, Cinzel, biseles): lo
  rechazó: «todo está crudo… rediseña todo con otro concepto… es minecraft» → concepto «Minecraft moderno» (sección 0).
- «revisa que nuestros productos no incumplan los términos de Mojang… todo el tema legal» → sección 0.

## 3. Cómo ver y probar la web en local

```bash
cd tierras-fantasticas
npm install                          # una vez
npm test                             # 39 tests (D1 simulada; Stripe/Discord simulados)
node tools/preview.mjs               # http://127.0.0.1:8788 con datos de prueba
PW=$(npm root -g)/playwright node tools/screenshots.cjs /tmp/capturas / /tienda "/tienda#ruleta" /ayuda
```

- `tools/preview.mjs` arranca el Worker real con una D1 en memoria y mete datos de ejemplo: jugador **Notch** con
  cuenta (contraseña `contraseña-segura`), rango Aventurero y una tienda de monedas con 8 objetos.
  Con `COOKIE_FILE=/ruta/cookie.txt` guarda la cookie de sesión de Notch (para ver «Mi cuenta» con sesión iniciada).
- `tools/screenshots.cjs` hace capturas de página completa en escritorio (`d_*.png`, 1440×900) y móvil
  (`m_*.png`, 390×844) e imprime el tamaño: **si el ancho no es 1440 o 390, algo se sale de la pantalla**.
  Las imágenes con `loading="lazy"` pueden salir vacías en la captura (al desplazar cargan: no es un fallo).
- Para parar el servidor: `kill $(lsof -t -i :8788)`. **No uses `pkill -f`** (puede matar tu propia terminal).
- Si cambias el catálogo, reinicia el servidor de vista previa (si no, sirve el `products.json` viejo).
- Las páginas HTML se **generan**: edita `tools/pages.py` y ejecuta `python3 tools/pages.py`. No edites los `.html`
  a mano. Los iconos SVG están en `tools/icons.py`.
- El catálogo de crates/ruleta se genera con `python3 tools/crates.py` → `config/products.json` (premios de la
  ruleta en `ROULETTE_POOL`, precio en monedas `ROULETTE_COIN_PRICE`).
- En este entorno el navegador sin cabeza no carga webs externas con proxy/CA propios (muchas fallan por
  certificado o Cloudflare): para referencias de diseño usa lo que sepas o búsquedas web.

## 4. Cloudflare (cómo se publica la web)

- **Despliegue automático**: el proyecto de Cloudflare (*Workers & Pages → `tierras-fantasticas`*) está conectado
  a este repo de GitHub. **Cada fusión en `main` se publica sola** con `npx wrangler deploy` (sin build command).
  No hay que hacer nada a mano; tarda ~1 minuto.
- `wrangler.jsonc` (raíz): `main` = `tierras-fantasticas/src/index.js`, archivos estáticos en
  `tierras-fantasticas/public` (binding `ASSETS`), base de datos **D1** `tierras-fantasticas` (binding `DB`, se creó
  sola), y variables públicas: `SERVER_NAME`, `SERVER_IP` (`216.163.187.40:19001`), `DISCORD_URL`
  (`https://discord.gg/tRrunHBZE`), `CURRENCY` (`USD`), `DISCORD_GUILD_ID` (`1439703812368765082`).
  `keep_vars: true` para no borrar lo puesto a mano en el panel.
- **Secrets** (panel de Cloudflare → *Workers & Pages → tierras-fantasticas → Settings → Variables and Secrets*,
  tipo *Secret*). Los pone el dueño; tú **nunca** los pidas por chat ni los escribas en el repo:
  - `STRIPE_SECRET_KEY` (`sk_live_…`) y `STRIPE_WEBHOOK_SECRET` (`whsec_…` del webhook en modo Live).
  - `BRIDGE_SECRET`: la clave `bridge.secret` del archivo `config/tfclient-server.properties` del servidor.
  - `DISCORD_CLIENT_ID`, `DISCORD_CLIENT_SECRET`, `DISCORD_BOT_TOKEN`, `DISCORD_WEBHOOK_URL`.
  - `SESSION_SECRET` es opcional (si falta, la web genera una y la guarda en D1).
- Dominio propio: *Settings → Domains & Routes → Custom domain* `tierrasfantásticas.store` (ya hecho).
- **Comprobar que un despliegue llegó** (desde la terminal):
  ```bash
  curl -s "https://xn--tierrasfantsticas-hpb.store/styles.css?x=$RANDOM" | head -5     # ¿el CSS nuevo?
  curl -s -o /dev/null -w "%{http_code}\n" https://xn--tierrasfantsticas-hpb.store/
  curl -s https://xn--tierrasfantsticas-hpb.store/api/status                       # estado del puente
  ```
  Busca en la respuesta algo que solo tenga la versión nueva (un comentario del CSS, una clase nueva…).

### Stripe (pagos, modo real) — EN PAUSA por decisión del dueño
No hay que hacer nada aquí ahora. Se deja documentado para cuando lo retome:
1. Dashboard de Stripe en **Live** → *Developers → API keys* → *Secret key* `sk_live_…` → Secret `STRIPE_SECRET_KEY`.
2. *Developers → Webhooks → Add endpoint* (en Live): `https://xn--tierrasfantsticas-hpb.store/webhook/stripe` con
   los eventos `checkout.session.completed`, `checkout.session.async_payment_succeeded`,
   `checkout.session.async_payment_failed`, `checkout.session.expired`, `charge.refunded`, `charge.dispute.created`.
   Su *Signing secret* `whsec_…` → Secret `STRIPE_WEBHOOK_SECRET`.
3. Los precios salen siempre del catálogo del servidor (`config/products.json`, en céntimos). Pago = página de Stripe
   (Checkout); el webhook firmado confirma y deja la entrega en la cola del puente.

### Discord
Aplicación en <https://discord.com/developers/applications>: OAuth2 (Client ID/Secret → Secrets; redirect
`https://xn--tierrasfantsticas-hpb.store/auth/discord/callback`), bot (token → `DISCORD_BOT_TOKEN`, permisos
*Gestionar roles* + *Crear invitación*, su rol por encima de los rangos), webhook de anuncios → `DISCORD_WEBHOOK_URL`.
Detalles paso a paso en `tierras-fantasticas/README.md`, sección «3. Discord».

### Puente con el servidor de Minecraft
- El TF Client del **servidor** llama cada 10 s a `POST /bridge/poll` (protocolo 2) con `Authorization: Bearer <BRIDGE_SECRET>`.
  Manda jugadores conectados/vistos, confirmaciones de entregas (con `prizes` para la ruleta), cambios de rango del
  staff y cambios de la tienda de monedas. Recibe entregas pendientes (comandos con `{player}`/`{uuid}` y campos
  `kind`/`color`), rangos, la configuración de la ruleta y la tienda de monedas.
- En el servidor: `config/tfclient-server.properties` (`bridge.url`, `bridge.secret`, `economy.mode` =
  `auto|vault|tf|comandos`, `economy.currency`). En consola debe salir `TF Bridge: conectado con la web`.
- Pedidos con monedas (ruleta y tienda de monedas) se crean en la web con id `COIN…` e importe 0; el mod cobra las
  monedas al ejecutar `tf web ruleta girar {player} N` / `tf web tienda comprar {player} <id>` y, si no tiene
  bastantes, devuelve el error y la web lo enseña.

## 5. El mod (TF Client 1.3.10)

- Compilar: `cd tf-client && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build --no-daemon -q -Porg.gradle.java.installations.paths=$JAVA_HOME`
  → `build/libs/tfclient-1.20.1-1.3.10.jar` (va en `mods/` del juego **y** del servidor, misma versión). Si el contenedor solo tiene Java 21 (pasó en octubre de 2026), basta
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build --no-daemon -q`: Gradle descarga el JDK 17 solo.
- Objetos de los sets (`items/TFTier.java`, `TFItems.create`, `TFItemTypes.Material`/`Armor`): cada set tiene su nivel
  (`tier` en `tf_sets.json`: `iron`, `diamond`, `netherite` o `netherite+N`). Crates +1 (desde la 1.3.5, decisión del
  dueño); rangos de hierro a +3 (1.3.6). Atributos ocultos en la descripción (`HIDE_ATTRIBUTES`).
- Comandos (todos en `items/TFCommands.java`):
  - `/tf jobs` (todos; staff: `recargar`, `nivel`, `xp`, `reiniciar`; `ver <oficio>` lo usan los avisos del chat).
  - `/tf shop` (todos; staff: `recargar`): la tienda del servidor, `shop/TFShop*.java`, config `config/tfclient-shop.json`.
  - `/tf web sets list|give`, `/tf web tienda add|precio|quitar|lista|vaciar` (staff) y los que usa la web:
    `/tf web rango`, `/tf web monedas ver|dar|quitar|poner`, `/tf web ruleta girar`, `/tf web tienda comprar`.
- Código: `server/TFBridge.java` (puente), `shop/TFCoinShop.java`, `shop/TFRoulette.java`, `economy/` (Vault por
  reflexión en Mohist, monedas propias o comandos), `jobs/` (oficios, config `config/tfclient-jobs.json`),
  `menu/TFPanelMenu.java` + `client/TFPanelScreen.java` (ventana de oficios: 10 huecos de la rejilla en los huecos
  29-33/38-42 del cofre y 36 botones abajo; nunca el inventario del jugador).
- Herramientas: `tools/check_backs.py [--fix]` (mide/acerca los cosméticos de espalda sin necesitar los packs),
  `tools/build_mod_items.py <packs> [set ...]` (con sets, solo rehace esos; modelos/texturas de los packs; usa `hmc_worn.py` para lo que va en
  la espalda/cabeza según HMCCosmetics), `tools/check_mod_items.py`, `tools/build_jobs_gui.py <packs>` (arte de oficios
  sin los textos en inglés). Los packs descomprimidos no están en el repo (los subió el dueño en zips).

## 6. Mapa rápido de la web

- Servidor: `tierras-fantasticas/src/app.js` (rutas: productos, Stripe, `/bridge/poll`, ruleta `POST /api/roulette/coins`,
  tienda de monedas `GET /api/coinshop` y `POST /api/coinshop/buy`, cuentas y Discord), `src/store.js` (D1),
  `src/stripe.js`, `src/discord.js`, `src/session.js`.
- Cliente: `public/app.js` (cabecera, estado del servidor, tienda y pestañas, crates, ruleta, tienda de monedas,
  ventana de compra, cuenta), `public/viewer.js` y `public/wardrobe.js` (visor 3D y probador de crates con el
  personaje), `public/success.js` (página del pedido).
- Tests: `tierras-fantasticas/test/app.test.js`.
