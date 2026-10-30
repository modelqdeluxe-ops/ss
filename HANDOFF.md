# Traspaso — Tierras Fantásticas (léelo entero antes de tocar nada)

Última actualización: **22 de octubre de 2026** (web: Atardecer con contraste medido —`tools/contrast_audit.py`—, botones con jerarquía y escenarios caramelo; TF Client **1.3.90**: isla ×1,5 con la grieta en el centro, cielo sin muaré y más animado, alas de Caelthar; 1.3.89: grieta del Void normal y madre, santuario nuevo, Void con shaders, llamas de Ignevar; 1.3.88: The Void —dimensión con la isla del dueño, cielo con agujero negro, el caballero de luz Aurelion y su viaje—, grieta 3.0 con shaders, Ignevar vuela otra vez, el Contemplador no sube al jugador; 1.3.87: grieta y cielo 2.0 —más grande, desaparece en la pelea, cielo sin píxeles con sol negro y fugaces nuevas, música siniestra—, sin supersalto de Astwood, `freeze` como MythicMobs, el Mamut ya no aparece y desaparece; 1.3.86: la grieta del vacío, el cielo de nebulosas y estrellas fugaces junto a una grieta, sonidos propios y rayos de la muerte que respetan los bloques; 1.3.85: los jefes ya no andan de lado —el cuerpo es la dirección en la que los mueve Minecraft— y no sueltan al jugador que lanzan fuera de la arena; 1.3.84: prueba de los jefes en el juego de verdad en GitHub Actions y lo que encontró —grietas que se reiniciaban, jefes de espaldas, la correa que soltaba al jugador, jefes volando lejos—; 1.3.83: alcance de los golpes, sin objetos, barra al morir, música y muerte épica; web: tienda rediseñada). Repo `modelqdeluxe-ops/ss`. Rama de trabajo:
`claude/intelligent-cray-63dqai` (se rehace desde `main` tras cada fusión); lo publicado está en `main`. Este documento
es para que otra IA (o persona) siga exactamente donde se quedó: qué es el proyecto, qué reglas puso el dueño, **qué
estábamos haciendo ahora mismo**, cómo funciona Cloudflare/Stripe/Discord/el puente con Minecraft y cómo publicar.

---

## 0. Lo que estábamos haciendo AHORA MISMO (empieza por aquí)

> **Para la siguiente IA: lee primero [`SIGUIENTE-IA.md`](SIGUIENTE-IA.md)** (qué hacemos, reglas, el procedimiento
> exacto con la prueba en el juego en GitHub Actions, cómo publicar y lo que queda). Lo último está en el CHECKPOINT
> de la 1.3.99, más abajo.

### Web (22 oct 2026, 5.ª ronda): COMPRAR COMO REGALO, garantías fuera de la cabecera, pestañas con color (hecho y publicado)
- **Regalo**: botón rosa con un regalo (`.btn-gift`, `data-gift`) junto a cada «Comprar» (tarjetas, crates, visor 3D,
  cosméticos, skills, rangos) y conmutador «Para mí / Como regalo» en la ventana de pago (`setGiftMode` en app.js).
  Hace falta la cuenta de quien paga; el que lo recibe tiene que haber entrado al servidor (`/api/player`); a uno
  mismo, no (`gift_self`). Mensaje opcional (100 caracteres, sin § ni controles: `cleanGiftMessage`).
  Backend: columnas `orders.gift` (JSON `{fromUuid, fromName, message}`) y `gift_from` (UUID de quien paga, para que lo
  vea en «Mis compras»); el pedido (username/uuid) es del que lo recibe. Al entregar se añade un `tellraw {player}`
  con «¡Regalo! X te ha regalado …» y el mensaje. La firma de los Términos es de quien paga (contexto `regalo`), el rol
  de Discord NO va al Discord de quien paga, el anuncio dice «X le ha regalado a Y», el recibo lleva los dos, y la
  sanción por disputa fuera de plazo es `tempbanip <quien pagó> 6d …`. Términos 2026-10-22.2, sección «Comprar como
  regalo». Prueba en `test/app.test.js` (56).
- **Garantías** (Pago seguro, Entrega, Para regalar, Rol en Discord): ya no en la cabecera de la tienda (estorbaban);
  fila de 4 placas debajo de los productos (`.shop-trust`).
- **Pestañas de la tienda**: cada una con su color (fondo claro, borde y canto de su tono, letra de su tono casi
  negro), más separadas (18 px); la elegida, llena y con aro.
- Arreglos de contraste en la ventana de pago (restos del tema oscuro): el campo enfocado se ponía NEGRO
  (`#0d121c` → blanco), «Jugador verificado» verde oscuro, enlaces de los Términos y «Mi cuenta» oscuros, errores en
  rojo oscuro sobre rosa claro.

### Web (22 oct 2026, 4.ª ronda): comprar LIMA, Tienda AMARILLA, recibos, registros por correo y sanción (hecho y publicado)
Pedidos del dueño: comprar más verde claro/lima, «Tienda» más amarilla, quitar la pastilla blanca de las crates al
pasar el ratón, el logo flotando arriba y abajo con brillo de medalla (no siguiendo al ratón), Términos con «sin
reembolso pasados 5 días» y baneo de IP de 6 días por reembolso injustificado, cada firma de los Términos y cada recibo
por correo al dueño, y que al comprar se descargue solo un recibo aparte del de Stripe.
- **Botones**: `.btn-buy`/`.btn-rank` lima (#bef264/#a3e635/#84cc16, letra #172b04, >7:1); `.btn-primary`/`.btn-shop`/
  `.btn-theme` amarillo dorado (#fde68a/#fcd34d/#f5b316, letra #2a1600). Auditoría: solo los falsos positivos conocidos.
- **Crates**: fuera el `<span class="try-on">` de `crateCard` (app.js). En los cosméticos con set sigue, ya legible.
- **Logo**: `<span class="portal-medal">` (pages.py); `logo-float` (6 s, 14 px) + `logo-shine` (degradado recortado con
  `mask: url(/img/logo.webp)`) + `logo-glint` (un poco más de brillo). Fuera el JS de seguir al ratón. 2.º ajuste
  (el dueño no veía la flotación y el logo chocaba con el título): 5 s y 22 px, y flota también con «menos movimiento»
  (Windows con los efectos de animación apagados lo activa); el título de la portada en escritorio pasa a
  `clamp(56px, 5.9vw, 78px)` («Fantásticas» se salía de su columna y se metía bajo la espada), logo 400 px y hueco 56 px.
- **Backend**: `src/pdf.js` (PDF a mano), `src/receipt.js` (recibo y `REFUND_POLICY`), `src/mail.js` (Resend). Correos:
  firma de Términos (`recordTerms`), recibo al entregar (`mailReceipt` en `fulfill`), reembolso/disputa con veredicto
  y PDF (`handleReversal`). Disputa pasados 5 días hábiles de un pedido entregado → `queuePenalty` encola `B<pedido>`
  con `tempbanip {player} 6d …`. `GET /api/order/:id/receipt` da el PDF; `success.js` lo descarga solo una vez
  (localStorage `tf-recibo-<id>`) y deja el botón «Descargar recibo (PDF)». Prueba nueva en `test/app.test.js` (55).
- **Términos 2026-10-22.1** (`tools/legal.py`): «Pasados esos 5 días hábiles no hay reembolsos», «Reembolso
  injustificado: baneo de IP de 6 días», sección nueva «Registro de la aceptación y recibos»; privacidad con Resend y
  las copias. Aviso corto también en la ventana de pago, la tienda y la FAQ.
- **FALTA (lo hace el dueño)**: crear la cuenta de Resend con su correo y poner en Cloudflare los Secrets
  `RESEND_API_KEY` y `RECORDS_EMAIL` (README, «Registros por correo y recibos»). El correo NO va al repo (es público).

### Web (22 oct 2026, 3.ª ronda): comprar en VERDE, rangos sin «nuevo», tarjetas con borde (hecho y publicado)
Quejas: «¿por qué el botón de comprar son los mismos colores?» (comprar era naranja como «Tienda»), «en los rangos
quita lo de nuevo y pon una breve explicación de cada comando, pero no cómo se usa», «las tarjetas de arriba no se
contrastan» (los rangos, blancos sobre crema y el elegido casi igual), y las piezas rosas/doradas sobre caramelo.
- **Comprar = VERDE** (`.btn-buy`, `.btn-rank`; #15803d con letra blanca, 5:1): ningún otro botón es verde. Navegar
  (`Tienda` de la cabecera, «Ir a la tienda») = naranja `.btn-primary`. Gratis = azul. Secundario = blanco.
- **Rangos**: cada rango es una tarjeta con borde de 2 px y canto; el elegido, lleno de su color con letra oscura.
  Los comandos: solo «qué es» (`RANK_PERK_INFO` en `tools/crates.py`, p. ej. «Yunque portátil», «Vuelo libre»), sin
  «nuevo» ni cómo se usa; después `python3 tools/crates.py`.
- **Escenarios y miniaturas sobre CAFÉ** (`--stage`, `--tile` #6f5038), no caramelo: con piezas blancas, rosas y
  doradas contrasta; caramelo se comía las piezas cálidas.
- Las categorías de la tienda y las crates: borde de 2 px y canto (se distinguen del fondo).

### Web (22 oct 2026, 2.ª ronda): contraste medido, jerarquía de botones y escenarios caramelo (hecho y publicado)
Queja del dueño sobre el Atardecer: «en la presentación de los modelos el fondo es blanco y con armaduras blancas no se
ve… los colores de los botones son una mierda, todos se parecen, no contrastan… eres autónomo, estúdiate las buenas
prácticas». **Lección: antes de enseñarle nada, MIDE (contraste, que los botones se distingan, piezas blancas sobre
fondo) en vez de esperar a que él lo encuentre.** Reglas aplicadas (Refactoring UI, WebAIM/WCAG 2.2, guías de
jerarquía de botones), que hay que seguir en adelante:
- **Texto ≥ 4.5:1** (3:1 si es grande: 24 px o 18.66 px en negrita); **bordes de controles ≥ 3:1**.
- **Un solo color de acción** (naranja, relleno, letra marrón oscura #2a1100): principal y comprar. **Secundario =
  blanco con contorno oscuro** (se distingue por la forma, no solo por el color). **Gratis = azul** (naranja y azul
  se distinguen con daltonismo; naranja y verde NO). Discord/WhatsApp con sus colores, en tonos profundos para que el
  blanco se lea. Lo elegido (chips, pestañas legales, Entrar/Crear cuenta) en naranja con letra oscura.
- **Escalas fijas** en `:root`: grises cálidos `--n-50…900`, naranja `--o-100…900`, azul `--b-*`. No inventes tonos.
- **Escenarios 3D, visor, armario, imágenes de las crates y miniaturas de piezas sobre CARAMELO** (`--stage`,
  `--tile`), nunca blanco: hay alas y armaduras blancas.
- Textos con el color del rango/crate/colección: `color-mix(... 40-45%, #1f1408)` (oscurecidos). El nombre del rango
  sin `-webkit-text-stroke` (en Unbounded deja rayas dentro de las letras). El chat del rango, negro como en el juego.
- Navegación: el elegido sin relleno (texto oscuro y raya naranja) para no parecerse a un botón.
- **Herramienta nueva**: `python3 tools/contrast_audit.py <carpeta>` (con la vista previa encendida y
  `PW=$(npm root -g)/playwright`): mide el contraste de cada texto contra los píxeles reales de detrás, en escritorio
  y móvil. Pasó de 268 fallos a 29, todos falsos positivos comprobados a ojo (teclas de skills y contadores dentro de
  píldoras pequeñas, una respuesta cerrada de la ayuda, una etiqueta oculta en el móvil y el aviso legal pegado a la
  sombra de una tarjeta). Pásala antes de publicar cualquier cambio de colores.

### Web (22 oct 2026): diseño «Atardecer» claro + botones cartoon «cel-shading» (hecho y publicado)
Después del rediseño de la aurora (abajo), el dueño dijo «pasamos de mierda a más mierda… los colores, animaciones, el
frontend, es horrendo» (la colocación de la tienda sí le gustaba). Se le enseñaron maquetas (scripts de prueba con CSS
inyectado, sin tocar la web): 9 estilos oscuros → «¿por qué todos tus diseños son negros?» → 6 claros/de color →
**eligió «C · Atardecer»** → 14 variantes de él → se quedó con **la C1 (la primera)**. Botones: rechazó 8 variantes
de color, 8 de forma, 8 colores del «juego móvil», 8 con texturas («horribles») y 8 «pro»; pidió **«diseño cartoon
profesional, pero no la tipografía sino el botón»** y eligió **el 4: cel-shading**. Lecciones: **no le enseñes 9
variantes del mismo diseño oscuro con otro acento; enséñale cosas de verdad distintas, y en grande**; no metas
morado; letra normal (no cartoon).
Lo que hay ahora (styles.css, comentario de arriba):
- **Web clara**: tokens `--s*`, `--text*`, `--bd*` claros; acento naranja `--a-400` #f26b1d. `body::before` = el reino
  de día (`hero-1920.webp`, `hero-768.webp` en el móvil) arriba de la página, **absoluto** (se va al bajar; fijo
  tapaba el texto) y fundido con melocotón → crema `#fff6ec`.
- **Botones** `.btn`: cel-shading con variables `--l --c --d --o --fg` (luz, cara, sombra, contorno/canto, texto).
  Principal/`btn-shop`/`btn-primary` naranja, `btn-buy` oro con letra marrón, `btn-ghost` azul, `btn-claim` verde,
  Discord/WhatsApp con sus colores, `btn-rank` con el color del rango.
- **Islas oscuras** que se quedan: el pie (`.footer` redefine sus tokens), las ventanas de VFX/Skills (`.cx`), la
  placa de las imágenes de las crates y las burbujas de chat/nametag de los rangos.
- Textos con el color del rango o de la crate: mezclados con marrón (`color-mix(... #2a1d14)`) para leerse en blanco.
- **Sin animaciones en bucle**: fuera la aurora (`.sky`), las letras del título que saltaban y el aro del portal.
  `tools/nebula_bg.py` y las `img/nebula-*.webp` ya no se usan (se pueden borrar si nadie quiere volver a la aurora).
- Maquetas que vio el dueño (por si pide otra): estaban en el scratchpad de la sesión; para rehacer una, inyecta CSS
  con Playwright (`addInitScript` que añade un `<style>`) sobre `tools/preview.mjs`.

### Web (21 oct 2026): «cambio de aires» de toda la web, SIN MORADO (publicado y luego sustituido por el Atardecer)
Pedido del dueño: «que la página web sea mucho más profesional, más bonita, más interactiva, mejor colocados los
elementos… un cambio de aires. Nada transparente ni UX fea. Primero busca plugins o skills seguros que te ayuden a
diseñar». A mitad: **«quita ese diseño morado»** → **nada morado ni rosa en la web** (no lo vuelvas a meter).
Guías usadas: la skill `frontend-design` de Anthropic (el dueño instaló el plugin; también `Design`) y las Web Interface
Guidelines de Vercel. Concepto «Portal del reino»:
- **Fondo**: el reino de noche (azul de luna) con una **aurora boreal** esmeralda/turquesa/azul que se mueve (la misma
  capa única de antes, regenerada con `python3 tools/nebula_bg.py`: mismos archivos `nebula-*.webp`).
- **Colores**: pizarra azul marino maciza (`--s0…--s4`, `--surface`, bordes `--bd` macizos), acento verde aurora
  (`--a-*`, el nombre viejo se queda), **botón principal azul** (`--brand` #2b7bf0), comprar ámbar, gratis esmeralda.
  Las pestañas de categoría: Crates pasó de violeta a esmeralda. Los colores de contenido (rangos, categorías de VFX,
  clases de skills, cosméticos rosa) no se tocaron.
- **Tipografía**: Unbounded solo en h1/h2; Figtree en botones, h3 y etiquetas (en minúscula, sin mayúsculas espaciadas).
- **Cabecera** siempre sólida (también en la portada), con el elegido iluminado; en teléfonos < 480 px la cuenta va en
  el menú («Mi cuenta») para que quepan nombre, Tienda y menú (antes el botón del menú se salía por la derecha).
- **Portada**: texto a la izquierda y el emblema sobre un **portal** (aro de la aurora que gira despacio y sigue al
  ratón: `[data-portal]` en app.js); debajo tres tarjetas (Discord, WhatsApp, cómo entrar).
- **Páginas interiores**: miga de pan (`crumbs()` en pages.py) y título a la izquierda. El mundo: título fijo a la
  izquierda y contenido a la derecha (`.feature-board`), pasos con línea que los une (`.stepper`), normas en una lista
  con iconos (`rule()`). Ayuda: cómo entrar + contacto fijos a la izquierda, preguntas con **buscador**
  (`[data-faq-search]`) a la derecha. Pie nuevo con la IP para copiar y botones de Discord/WhatsApp/correo.
- **Tienda**: las garantías (pago seguro, entrega, rol) arriba a la derecha (en el móvil, una fila compacta); barra de
  categorías maciza (la elegida, llena de su color); el texto de la sección y el buscador en una barra; tarjetas de
  crate sin destellos translúcidos.
- `tools/screenshots.cjs` ahora estira la ventana a la altura de la página (la captura `fullPage` de Chromium sin GPU
  pintaba el fondo fijo encima del contenido y salía apagado). Para vistas previas «como las ve un jugador» usa capturas
  de la pantalla (1440×900 / 390×844) en varias alturas.

### Web (13 oct 2026): tienda — barra de categorías, VFX y Skills REHECHOS DE CERO (hecho y publicado)
Historia, para no repetirla: el dueño pidió «un rediseño completo de la gui de skills, vfx y tarjeta de botones de
tienda, quiero un cambio real» («tarjeta de botones» = la barra de categorías). Las dos primeras rondas (PR #94 y #95)
solo recolorearon la MISMA estructura (rejilla de 6 fichas con icono; vista previa arriba + mosaico abajo, igual desde
el 1.3.37) y el dueño respondió: *«siempre tiendes a hacer diseños transparentes… se confunde con el resto»* y luego
*«sigue el mismo puto diseño de hace 10 builds, no hiciste un rework completo»*. **Lección: cuando pida un rework,
cambia la estructura y el lenguaje visual, no los colores.** Reglas que siguen valiendo: **nada translúcido** (las
mezclas `color-mix` van con un color sólido, nunca con `transparent`), contraste medido con PIL, textura `--grain`
(ruido SVG en `:root`), y **lo que ya tienes no sale solo en la vista previa** (se ve marcado en su tarjeta).

Lo que hay ahora (solo web: `tools/pages.py` → `public/tienda.html`, `tools/tab_art.py`, `public/app.js`,
`public/styles.css`):
- **Barra de categorías** (`.shop-tabs`): **cinco** tarjetas (Rangos, Cosméticos, Crates, VFX, Skills; el dueño
  quitó «Recompensas gratis»: sus productos siguen en `products.json` pero ya no tienen pestaña y `#gratis` lleva a
  Crates). Cada una con **el dibujo** de la sección (`img/tabs/<cat>.webp`, los genera `python3 tools/tab_art.py`:
  escudo real, alas, crate Luminite recortada, kill K.O. y un abanico de tres iconos de clases) sobre su **color vivo**
  (`--t`, `--t-hi`, `--t-lo`, `--t-ink` por pestaña en styles.css), **contorno** de su tono claro y el nombre abajo.
  La elegida se enciende (tono claro → color), contorno blanco, anillo de su color. Móvil y tablet 3 + 2; desde
  1000 px las cinco en fila.
- **Colores vivos, nunca marrones**: `toneVars(hex)` (app.js) saca de cada color su tono claro, profundo y casi negro
  (`--c-hi`, `--c-lo`, `--c-ink`) **sin mezclarlo con negro** (eso daba marrón/verde oliva); en los amarillos las
  sombras giran hacia el naranja. Lo usan las tarjetas del catálogo, la arena de VFX, la cabecera y el marco de Skills.
- **VFX y Skills = una ventana propia color grafito** (`.cx`, neutra: ni violeta ni azul de la web) con su filo de
  color arriba, cabecera (etiqueta, título, texto) y dentro `.cx-layout`: **catálogo a la izquierda y vitrina a la
  derecha** (VFX desde 1024 px, Skills desde 1200 px); antes, la vitrina arriba y el catálogo debajo. **Sin filtros**
  (el dueño no los quiere: ni en VFX ni en Skills). La vitrina es `sticky`; `cxStick()` (app.js) le pone el `top` para
  que, si es más alta que la pantalla, quede pegada por abajo y se vea la compra. `cxReveal()` sube a la vitrina al
  elegir algo cuando no es sticky.
  - **Catálogo** = tarjetas tipo «rareza» (`.cx-tile`): el dibujo sobre el **color vivo** de su categoría/clase, franja
    de color y nombre sobre su tono oscuro; la elegida con marco blanco y la franja en su tono claro. Etiquetas macizas
    «✓ Equipado»/«Tuyo» (VFX) y «✓ Activa»/«Tuya»/«En cola» (Skills).
  - **VFX** (`renderVfx`, `vfxShow`, `vfxTile`, `vfxAction`): arena con el color de la categoría macizo y charco de luz,
    etiqueta de categoría, flechas con contador (`data-vfx-step`); ficha con nombre, descripción, cómo sale y la acción
    en una placa negra (`.cx-deal`: Gratis / Es tuyo / Equipado + botón). Al pasar el ratón, la miniatura se anima.
    Paquetes de skills antiguos al final (`vfxOldPacks`, `.cx-old`), solo si tienes alguno. OJO: las animaciones acaban
    con ~1,6 s vacías antes de repetirse (así son los WebP); si una captura sale con la arena vacía, es eso.
  - **Skills** (`renderSkills`, `skDetail`, `skScreen`, `skSlot`, `skPickCard`, `skBuy`, `skTile`): plantel de
    retratos (icono de la clase ampliado sobre su color) y ficha: cabecera con emblema, rol, nombre, tipo y descripción;
    **pantalla CUADRADA** (como las vistas previas, 480×480: así se ve entera, con el jugador; hasta 520 px) enmarcada
    en el color de la clase (por dentro `--sk-bg`, el fondo pintado en las imágenes). OJO: una pantalla apaisada
    recortaba la imagen y no se veía el muñeco (queja del dueño). Desde 900 px, a su lado, la barra de skills como la
    del juego (tecla 5–0 en la esquina; pasivas aparte con «P») con datos y la skill elegida (`#sk-pick`); abajo la
    compra en dos filas (`.sk-buy`: con dinero ámbar / con monedas) o la placa de estado (`.cx-own`).
- Comprobado con `tools/preview.mjs` (con `COOKIE_FILE` para ver a Notch con sesión; tras reiniciarlo, Notch no tiene
  VFX: se pueden reclamar con `POST /api/vfx/claim`) en 1440×900, 1024, 768 y 390 de ancho; `npm test` 53/53.

### CHECKPOINT (30 oct 2026, TF Client 1.3.153) — lee esto primero

**1.3.153** (el dueño: «necesito que des menos monedas en los oficios, misiones, etc, y que los jobs no me suban nivel
cuando mino o hago la acción, solo me debe dar al completar la misión» y «quita recompensas que den netherita»). Antes,
en la web, las skills a 150.000 Fantastic Coins (`tierras-fantasticas/tools/skills.py`, PR #219).
- **Oficios**: las acciones solo pagan monedas (`TFJobs.record` ya no llama a `addXp`; `TFJobsConfig.Action` sin `xp`;
  el pad, `/jobs` y el editor del pad de admin ya no la enseñan). El nivel sube con la XP de las misiones al COBRAR.
  Curva nueva **150 × 1,075^(nivel-1)** (antes 120 × 1,15^(nivel-1): con solo misiones el 30 pedía ~15 días y el 49,
  ~180); ahora de medio día a ~6 días por nivel, el 50 en unos 4 meses haciendo las 3 diarias.
- **Monedas a la mitad**: oficios (acciones, misiones, niveles, hito 50: 450), misiones del pad y cazas
  (`tools/gen_economia.py`: `ECON4`, `econ()`). En los servidores, una vez: `PadEconomy.economy4` (marca
  `"economia": 4`, copia `.antes-economia4.json`; en oficios.json además acciones a `xp: 0` y la curva nueva). Los
  archivos por defecto salen idénticos a los migrados (comprobado).
- **Sin netherita en los premios**: hito 50 de oficios, pase (niveles 40 y 50, solo verdes), gachapón (épico y
  legendario), ejemplo de `/tf crates create` (esmeralda). En las configs que ya hay (pase, gachapón, kits,
  recompensas, oficios, misiones), una vez: `PadEconomy.withoutNetherite` (marca `"sinNetherita": true`; un nivel o día
  que se quede sin `premios` recibe un diamante). Las crates del dueño (`config/fscrates/cajas`) no se tocan.
- Prueba `[prueba-jefes=oficios]`: «XP del oficio al fundir … (no sube: bien)», «al cobrar … (sube: bien)» y
  «economía 4 en configs viejas … (bien)» (`BossTest.economy4Test`).

### CHECKPOINT (28 oct 2026, TF Client 1.3.152)

**1.3.152** (el dueño con capturas, seis cosas de la fiesta). Un solo comando, `/tf cumpleaños` (fuera `cumpleanos`).
El letrero ya no sigue la vista: quieto en el cielo hacia donde se miraba al empezar (`skyYaw`) a 58° (las «2 en
punto»), de cara al jugador (`Quaternionf.rotationYXZ`), sin niebla (`setShaderFogStart`); el nombre en estrellas
(40°) y los corazones de luz (34°) en la misma dirección. Los corazones del nombre son el corazón de la vida
(`textures/gui/icons.png`, 52,0 9x9) dibujado como quad de `RenderType.text`, no «❤» (con su paquete salía raro).
Con shaders el letrero se dibuja en `vacio.AfterShaders.end` (`TFBirthdayClient.renderAfterShaders`, como la grieta) y
cada cohete suelta además sus chispas de `TFPartyParticle` (con `physics`: caen y frenan) y un destello: probado con
`[shaders]` (Complementary), se ve todo. Los títulos son nuestros (`RenderGuiEvent.Post`): como mucho 4x y el 80 % del
ancho. La canción, seis vueltas seguidas (`VERSES`: campana, flauta, carillón, xilófono, campana+pling y, con la traca,
todo; bajo y vals de percusión desde la segunda) y fanfarria nueva (`fanfare`: arpegio, acorde y lluvia de carillón).
Ojo: con shaders la máquina de la prueba va a 1-2 fps; las capturas del final llegan tarde.

### CHECKPOINT (28 oct 2026, TF Client 1.3.151)

**1.3.151** (el dueño: «es muy simple el evento de cumpleaños, y no pongas mirad, es Mira»). La fiesta de
`/tf cumpleaños` dura 90 s (`TFBirthday.LENGTH`) en cuatro actos (`client/TFBirthdayClient`): cuenta atrás (cielo
violeta con `ComputeFogColor`, redoble y 3-2-1 en títulos), «¡ahora!» (anillo de 12 cohetes y un corazón de luz),
fiesta (letrero de 3 líneas que entra con rebote y brillos alrededor, el nombre escrito con estrellas que vuelan a su
sitio con letras de 5x7 `GLYPHS`, farolillos y globos que suben, confeti, cohetes, «Cumpleaños feliz» dos veces con
campana, arpa, bajo y percusión, y al cumpleañero una corona de puntos y notas que ven los demás) y traca final (dos
corazones, fanfarria y «¡Felicidades, NOMBRE!» a todos). Las luces son `client/TFPartyParticle` (dibujos del atlas de
Minecraft, sin registrar nada, luz completa: se ven con shaders). Chat: «¡Mira al cielo!». Ojo: el segundo estallido de
los cohetes dobles va a `LATER` (añadirlo a `ROCKETS` mientras se recorre cerraba el juego); el tick va en try/catch.
Prueba: `[prueba-jefes=cumple]`.

### CHECKPOINT (28 oct 2026, TF Client 1.3.150)

**1.3.150** (el dueño, cuatro cosas). Prueba en el juego: `[prueba-jefes=encantar,protes,cumple]`, todo «bien».
- **Encantar las piezas de los sets** (`items/TFEnchanting`, mixins `EnchantTableMixin` sobre `ItemStack.isEnchantable`
  y `EnchantmentHelperMixin` sobre `getAvailableEnchantmentResults`): la mesa acepta las ya encantadas mientras quede
  algo que puedan llevar y solo ofrece lo que no tienen ni choca; el yunque con libro (`AnvilUpdateEvent`) suma sin bajar
  nunca un nivel (los de rango por encima del máximo se quedan), coste como Minecraft con tope 39; nunca
  Irrompibilidad ni Reparación; una pieza nuestra no se repara, no se junta ni se sacrifica; la piedra de afilar no las
  coge (quitaría lo del rango y daría XP). El resultado es la copia de la pieza: atributos, dueño y NBT intactos. Los
  sets full (Celestial/Fantástico) no tienen nada que añadir.
- **Pad de admin**: textura nueva 32x32 (`textures/item/pad_admin.png`, tableta granate con corona y escudo) y la app
  `a_protecciones` (`PadClaims.ADMIN`): todas las zonas (las de grupo, por la principal), buscar por dueño o nombre,
  IR al momento, y la zona con sus pestañas de siempre (ajustes = permisos, miembros, baneos, partículas, más) con el
  dueño arriba; ELIMINAR del staff = sin piedra y aviso al dueño (como el panel de `/tf web claims`). `PadClaims.act`
  es lo común; `app` decide dónde se redibuja.
- **Protecciones, MÁS** (para todos): «Nombre de la zona» (`ClaimFlags.customName`, guardado como `customName`; sin §,
  32 letras; solo se ve en el pad) e «Ir a la zona» (`PadClaims.surface`: encima de la piedra en la superficie, sin
  hojas; con techo, el primer hueco libre encima; si no se puede estar, lo más cerca; con la cuenta atrás de viajes).
- **`/tf cumpleaños felicitar <jugador>`** (también `cumpleanos`; `parar`): `server/TFBirthday` (1 min, chat en
  arcoíris, canal `tfclient:cumple`, también a quien entra o cambia de mundo) y `client/TFBirthdayClient` en los mundos
  con cielo de Overworld (Multiverse incluido; Nether, End y Void no): letrero en el cielo que sigue la mirada
  (drawInBatch8xOutline, AFTER_PARTICLES), cohetes del cliente (`createFireworks`, sin entidades ni daño) con traca al
  final, confeti de lana, «Cumpleaños feliz» con campanas y, al homenajeado, el título. Sin probar con shaders.

### CHECKPOINT (28 oct 2026, TF Client 1.3.149)

**1.3.149** (el dueño: «añade un comando para quitar estas cosas yo manualmente… y para quitar todos los equipados»
y «añade contraseña al comando tf… cuando la ponga me desbloquea el comando solo a mí o al que tenga la pass»).
- `/tf web vfx quitar <jugador|todos>` (`VfxServer.remove`): quita el efecto de kill equipado, también a los
  desconectados (nombre → conectado, caché de perfiles o UUID offline); a los conectados se lo dice a la web.
- `server/TFStaffLock` + `client/TFStaffLockScreen` (canal `tfclient:clave`): además de ser staff, /tf pide la
  contraseña. Cada rama de /tf lleva `TFStaffLock.guard` (requires + `allowed`: la consola y el puente siempre; un
  jugador solo si la puso; nada más, así que ni bloques de comandos, ni funciones, ni `/execute as` un mob). Un
  `CommandEvent` (HIGHEST) ve «tf …» (o «… run tf …») de un staff sin abrir: lo cancela, le abre la ventana (puntos; la
  contraseña va por paquete, no sale en el chat ni en los logs) y, bien puesta, ejecuta lo escrito. /tf sola también
  la pide (por si en Mohist no llega el CommandEvent). Dura hasta que sale; `/tf clave cerrar` la vuelve a cerrar.
  5 fallos: 15 min. La contraseña NO está en el repo: solo sal + huella (PBKDF2-SHA256 200k, SHA-256 de la clave, como
  `client/TFLock`); `config/tfclient/clave-tf.json` (se crea al arrancar; `"activa": false` la quita) y desde la
  consola `tf clave cambiar <nueva>`. Prueba en el juego: `[prueba-jefes=clave]`. El pad de administrador va aparte.

### CHECKPOINT (28 oct 2026, TF Client 1.3.148)

**1.3.148** (el dueño: «apareció un wey con uno desbloqueado», 8Dark_Dragon8, sin compra, sin Discord y sin
`/tf web vfx` en ningún log). Hueco en `vfx/VfxServer.ownsKill`: sin respuesta del puente (`webFree` empezaba en true y
`!TFBridge.connected()` daba true) el pad daba por tuyos TODOS los efectos de kill. El puente se corta ~10 s cada 10–20
min («Read timed out»), así que pasaba a diario; lo equipado entonces se quedaba para siempre porque «el que ya lleva»
contaba como suyo, y la web (que rechaza lo no comprado) nunca se enteraba. Ahora: sin respuesta vale lo último que
dijo la web (`OWNED`; solo sin web configurada, `TFServerConfig.enabled()` false, son todos libres); `Equip.granted`
(`"dado"` en `vfx.json` del mundo) marca lo que puso la web (`applyWeb`) o el staff (`/tf web vfx kill`); y
`applyOwned(srv, …)`, cuando la web responde y no son gratis, quita el efecto equipado que no es suyo ni dado (log
«TF VFX: se quita a … el efecto de kill …»). Lo que el staff dio antes de esta versión no lleva la marca y se quita.
Incluye también 6b3de671 (sin el aviso de saldo al apagar).

### CHECKPOINT (27 oct 2026, TF Client 1.3.147)

**1.3.147** (el dueño, con su latest.log: «¿nuestro mod causa lag? El server se llena de RAM a veces» → «elimina por
completo el rastreador»). El Rastreador leía todo el mundo del disco cada 30 min (3.586 archivos, ~1,9 millones de
chunks, ~7 min cada vez): mucha RAM y disco. Quitado entero: `rastreo/TFSearch`, `rastreo/TFTracker` (registro de
cofres), la app `a_rastreo` (`pad/server/PadAdminTracker`, su icono y tema), `/tf rastreo` y `/tf buscar`. Se queda
`rastreo/TFPurchases` (las compras de la web, por el puente): con ellas `TFBinding.rightful` ata lo comprado a quien lo
compró. Siguen igual las protecciones: nada ligado a otro sale de cofres, tolvas o tuberías (1.3.141), lo de un pedido
vuelve a su comprador (1.3.145) y lo comprado no se destruye ni lo borra un clear lag en el suelo (TFWebDrops). Prueba
`[prueba-jefes=oficios]`: la pala de mi compra atada a un «ladrón» pasa a ser mía.
Y (el dueño: «cuando oculto las alas en el hueco de Accessories con el puntito, se vuelven a dibujar»): con el hueco
oculto Accessories no llama a nuestro dibujante y el respaldo (`BackLayer`, «el mod no lo dibujó») las pintaba igual.
`TFAccessoryLookup.visibleFromAccessories` (AccessoriesContainer.shouldRender, hueco a hueco, normal y cosmético) en
`TFAccessorySlots.backCosmeticsRaw` y `wearsBack` (el Armario tampoco las pone si está oculto). Curios ya lo respetaba
(`fromCurios(..., true)`, getRenders). Accessories no arranca en la prueba: comprobado con la API de beta.48 (la del
servidor) y `[prueba-jefes=accesorios,armario] [accesorios=curios]` para no romper lo demás.

### CHECKPOINT anterior (26 oct 2026, TF Client 1.3.146, PUBLICADA)

**1.3.146** (el dueño: «en las semanales salen misiones de end game y mid game: no pueden rotar ahora misiones del Nether
ni del End»; y «mi pase tiene recompensas en blanco»).
- Misiones del pad: «requiere»: nether / end en cada misión del Nether (también las pociones: piden polvo de blaze) o del
  End; solo rotan si están en «abiertos» de misiones.json (por defecto ninguno: 194 diarias y 29 cazas). «semanal»: false
  en las que x5 serían de mitad de partida (diamantes, esmeraldas, guardianes, vindicadores…): 173 semanales.
  `PadMissions.WEEKLY`. misiones.json versión 4 (el servidor guarda la de antes y pone la nueva).
- TF Pass: `PadPass.fillEmpty`: los niveles sin premio se rellenan con el de fábrica (una vez, versión 3 de pase.json).
  Al dueño se le mandó su pase.json lleno a mano (los 20 niveles vacíos: 1 verde + algo pequeño).

### CHECKPOINT anterior (26 oct 2026, TF Client 1.3.145, PUBLICADA)

**1.3.145** (el dueño, con el Rastreador: «HitGp: con él 10, en cofres 30, CON OTROS 8, sin encontrar 14; ¿cómo que no
están?»). Lo de HitGp pasó con la 1.3.132 (sin las protecciones de la 1.3.134 y la 1.3.141). Su
`sophisticatedbackpacks.dat` (133 mochilas) no tiene nada de su pedido. En el servidor están WorldListTrashCan (una
papelera que recoge lo del suelo; cualquiera la abre y luego se vacía) e InventoryRollbackPlus. Arreglos:
- **Dueño de verdad** (`TFBinding.rightful`/`fixOwner`): lo de un pedido (TFOrder) se ata a quien lo compró
  (`TFPurchases`), no al primero que lo lleva. Antes, lo que estaba sin dueño en un cofre quedó atado al ladrón y
  **TRAER TODO se lo devolvía a él al momento**. Cada segundo (y siempre que es de otro) se corrige: en el inventario, en
  el cursor y en accesorios (lo de otro puesto en accesorios también vuelve a su dueño). `TFSearch.recover` y lo
  pendiente al entrar atan lo recuperado a quien lo recibe.
- **Papeleras**: `TFWebDrops.CLEANERS` con trash, garbage, basura, papelera, sweep y dustbin (14 llamadas de pila).
- **Rastreador**: lee `<mundo>/data/*.dat` (`scanData`): lo de dentro de las mochilas de Sophisticated Backpacks (cada
  una con su uuid; el objeto de la mochila lleva `contentsUuid`) y sale donde está la mochila («en una mochila: en el
  inventario de X»; `resolveBags`); lo que espera a su dueño (tfclient_returns.dat) y lo de otros mods. TRAER saca de
  las mochilas con su propio inventario (`takeFromBag`, por reflexión) y de los accesorios.
- Prueba: `[prueba-jefes=oficios] [mochilas]` (`-Ptfmochilas`: Sophisticated Backpacks 3.26.12 + Core 1.5.12, las del
  servidor): una pala de la compra atada al ladrón en mi inventario se queda y pasa a ser mía; un pico atado al ladrón en
  una mochila dentro de un cofre: sale «en una mochila» y TRAER TODO me lo da atado a mí.

### CHECKPOINT anterior (26 oct 2026, TF Client 1.3.144, PUBLICADA)

**1.3.144** (el dueño: «no pongas texto redundante, ambiguo ni tan extenso; eso de "en creativo no cuenta" ya se sabe»):
la línea de cómo se hace cada misión (`TFMissionCheck.how`) dice solo lo que no es obvio, en pocas palabras («Solo
maduros.», «Los de spawners no cuentan.»…); pescar, encantar y reparar no llevan ninguna.

### CHECKPOINT anterior (26 oct 2026, TF Client 1.3.143, PUBLICADA)

**1.3.143** (el dueño: «la misión de crear cofres no cuenta los de Woodworks; haz universal la detección»).
`TFJobsConfig.Target.sameKind`: un objetivo `minecraft:chest`, `minecraft:trapped_chest`, `minecraft:barrel` o
`minecraft:bookshelf` cuenta cualquier bloque de esa clase o con la etiqueta de Forge (`forge:chests/wooden`,
`forge:chests/trapped`, `forge:barrels/wooden`, `forge:bookshelves`), de cualquier mod, al fabricar, colocar y romper
(oficios y misiones del pad). Prueba: `[prueba-jefes=oficios] [woodworks]` (Woodworks 3.0.2 + Blueprint 7.1.3,
`-Ptfwoodworks`): fabrica un cofre de 8 tablones de abeto.

Y (el dueño: «verifica todas las misiones, en jobs y todo; no quiero más actualizaciones de este tipo; un colchón de 2
meses de misiones, no idénticas; muchos más niveles de jobs sin pasarse con los pagos; que todas se cumplan y todos
cobren, cazas, todo; y una pequeña descripción de lo que hay que hacer en cada misión»):
- `tools/gen_economia.py` escribe ya los pagos finales (escala 0,6 de `PadEconomy.scale`, `economia: 3`) y comprueba
  ids y nombres repetidos. Misiones del pad: 223 diarias (unos 74 días sin repetir ninguna, 3 al día) y 42 cazas;
  oficios: 285 misiones, hasta el nivel 50, y una «nunca» de leyenda en cada uno. Las que ya había salen idénticas.
  Configs a la versión 3 (`versionEconomia` de oficios.json y `version` de misiones.json): el servidor guarda la de
  antes (`.antes-v3.json`) y pone la nueva.
- `TFMissionCheck`: revisa cada misión con los registros y las recetas (existe, la etiqueta no está vacía, el tipo va
  con el objetivo, algo de verdad del objetivo cuenta, «fabricar» sale de la mesa, «fundir» de un horno, «criar» se
  puede criar). Al arrancar avisa en la consola («TF Misiones: …»). `how(tipo, objetivo)`: la línea de «cómo se hace»
  que sale en cada misión (pad, oficios y el menú /jobs).
- Bayas dulces y luminosas cogidas con clic derecho cuentan como cosechar (`TFJobs.onRightClick`/`checkPicks`).
- Etiqueta `tfclient:hormigon` (los 16 hormigones, para la misión de constructor).
- Prueba `[prueba-jefes=oficios]`, paso 21: revisa todas las del mod, hace y cobra todas las de hoy del pad (diarias,
  semanales, cazas) y de los 10 oficios al nivel 50 (`PadMissions.testAll`, `TFJobs.testAllJobs`) y las bayas.

### CHECKPOINT anterior (26 oct 2026, TF Client 1.3.142, PUBLICADA)

**1.3.142** (el dueño: «una app de rastreador en el pad de admin: cada jugador que compró, todos sus objetos y dónde
están —puestos, cofres, coordenadas—; que funcione desde el minuto 1»). Piezas (paquete `rastreo`):
- `TFPurchases`: las compras pagadas de la web. El puente manda `wantOrders` (al arrancar, cada 5 min o al abrir la app)
  y la web responde `orders` (`ordersForServer` en app.js: las 1.000 últimas, con sus comandos); de los comandos salen
  los objetos esperados («tf web sets give {player} set [pieza]», «give {player} objeto n»). Copia en
  `<mundo>/tfclient/compras.json`.
- `TFSearch`: dónde está cada objeto marcado (TFOrder) o ligado (TFOwner): conectados (puesto, mano, inventario, ender,
  accesorios, cursor), desconectados (playerdata) y todo el mundo en disco (lee los .mca de `region` y `entities` de
  todas las carpetas de mundos, también Multiverse y los de mods; cofres, shulkers dentro, marcos, vagonetas, suelo).
  Hilo aparte; al escanear a mano guarda el mundo antes. Solo: al minuto de arrancar y cada 30 min. `recover` quita lo
  de un pedido de donde esté y se lo da al dueño (a un desconectado, al entrar: `rastreador-pendiente.json`).
- `TFTracker`: registro de quién saca/mete/rompe en contenedores (`<mundo>/tfclient/rastreador/<día>.log`, 60 días);
  `/tf rastreo jugador|aqui|objeto` y `/tf buscar <jugador|pedido>`.
- App `a_rastreo` (`pad/server/PadAdminTracker`, icono `icon_rastreo.png`): COMPRADORES (los que tienen algo con otro,
  primero), ficha de cada uno con cada compra y cada objeto (dónde, IR, TRAER, TRAER TODO), ligados sin pedido,
  MOVIMIENTOS y buscar.
- Prueba: `[prueba-jefes=oficios]` (al final: «rastreador: …», capturas `rastreador_*`).

### CHECKPOINT anterior (25 oct 2026, TF Client 1.3.141, PUBLICADA)

**1.3.141** (el dueño, con el playerdata del servidor: «a HitGp le robaron el kit Dragón Mecha de un cofre estando
desconectado; no es posible que lo puedan tomar de los cofres»). Revisión del playerdata (152 archivos): HitGp
(`e8c7696c…`, pedido TFHSYSEWUGHHL43UQR) conserva la armadura (ligada) y las alas; faltan las 16 armas/herramientas, el
escudo y el sombrero (no los tiene ningún jugador: seguirían en un cofre o los movieron con tolvas). Llamitaa
(`e69d9e54…`) tiene las 8 manzanas de oro de ese pedido y kwns_700 (`aebbb291…`) tuvo sus alas a las 15:13. capitf
(`c825813a…`) tiene armadura, escudo y alas. Agujeros y arreglo (`TFBinding`):
- Nada impedía sacar un objeto ligado de un cofre (solo se devolvía al llevarlo en el inventario; con tolvas o rompiendo
  el cofre ni eso). Ahora `mixin/binding/SlotBindingMixin` (Slot.mayPickup: clic, mayúsculas, teclas, tirar),
  `HopperBindingMixin` (las tolvas no recogen del suelo lo ligado) e `InvWrapperBindingMixin` (tolvas y tuberías no lo
  sacan de contenedores); el cursor también se revisa.
- Las alas, sombreros y mochilas de los sets de rango/crate no se ligaban («cosméticos»): ahora sí (los sets de
  cosméticos de monedas siguen sin ligar). Los que no tienen dueño se ligan a quien los lleve, también puestos en
  Accessories/Curios (`bindWorn`).
- Prueba: `[prueba-jefes=oficios]` (al final: «robo: …»).

### CHECKPOINT anterior (25 oct 2026, TF Client 1.3.140, PUBLICADA)

**1.3.140** (el dueño: «el casco del rango Mágico es cosmético, hazlo casco como el de Eterno; las armaduras de los
rangos son netherita + 2 puntos (pechera 10 en vez de 8), solo Mortal y Eterno + 1 (9); las crates netherita + 1» y
«rankings del pad: más dinero, más muertes y mobs matados»).
- Niveles (`tier` de `tf_sets.json` y `RANKS` de `tierras-fantasticas/tools/crates.py`): Mortal (patrick) +1, Inmortal
  (beats) +2, Mágico (darkworld) +2, Eterno (malika) +1, Cósmico (necros) +2, Celestial (eagle) +2, Fantástico
  (starlight) +2 (antes: hierro, diamante, netherita, +1, +1,5, +2, +3). Crates y Void siguen en +1. +N = N más de
  armadura por pieza, de dureza y de daño (`TFTier`).
- Casco de Mágico (`darkworld_helmet`): `"armor": true` como el de Eterno; `build_mod_items.mark_armor_head` lo pone
  solo en los sets de rango y de recompensa sin casco de armadura (antes lo quitaba a todo lo que no fuera recompensa).
- Ranking (`PadRanking`): DINERO con cualquier economía (Vault/EssentialsX: saldo de los conectados, el apuntado cada
  minuto en pad-estadisticas.json «saldo» y el de `plugins/Essentials/userdata/*.yml`), MUERTES y MOBS (estadísticas de
  Minecraft: conectados al momento y `<mundo>/stats/*.json`). La pestaña «monedas» de antes lleva a DINERO.
- Prueba: `[prueba-jefes=oficios]` se pone cada set de rango y comprueba armadura total, pechera y dureza, captura
  el de Mágico con su casco y los tres rankings nuevos.

### CHECKPOINT anterior (25 oct 2026, TF Client 1.3.139, PUBLICADA)

**1.3.139** (el dueño: «las misiones no reconocen nada; no dejan cobrar los jobs; la Tormenta del Wither deja la lluvia;
rangos en los clanes; la clave del puente se desconecta; configs que se actualicen solas; que el pad no publique los
warps de admin; que los objetos no salgan en el JEI»).
- **Misiones y oficios**: el evento de Forge de fabricar/fundir/preparar llega con el montón vacío (aire) al sacar con
  mayúsculas + clic: no contaba nada. Ahora lo cuentan los mixins `mixin/jobs/ResultSlotJobsMixin` (mesa e inventario),
  `FurnaceResultSlotJobsMixin` (horno, ahumador, alto horno) y `BrewingStandMenuJobsMixin` (pociones con mayúsculas;
  `TFJobs.quickBrew` para no contarlas dos veces) → `TFJobs.made`. Lo que se gana se paga al momento (`record`); COBRAR
  paga primero y, si la economía falla, la misión sigue sin cobrar (`TFJobs.claim`). Las diarias hechas sin cobrar se
  cobran hasta 30 días (`TFJobsConfig.UNCLAIMED_DAYS`; las de antes de la 1.3.139 sacan la plantilla del id) y, si la
  misión ya no está en oficios.json, con lo que valía al hacerla (`MissionState.name/coins/xp`, `leftover`). Las
  semanales duran de lunes a domingo (el texto lo dice; «23 h» un domingo era correcto).
- **Tormenta de un jefe** (`weather` de MythicMobs, `SkillMore.fakeWeather`): se quita al morir (o quitarse) quien la
  puso, mirándolo cada segundo, además de al acabar su `duration`.
- **Clanes** (`PadClans`): rangos líder, colíder, veterano y miembro (`rangos` en clanes.json; los `oficiales` de antes
  pasan a colíder solos). El líder asciende/baja y pasa el clan (LÍDER, dos clics); invitan líder, colíderes y
  veteranos (esto se apaga en AJUSTES: «Los veteranos invitan»); echan el líder (a miembros) y los colíderes (a
  veteranos y miembros).
- **Puente**: una consulta colgada (DNS/red sin tiempo máximo) dejaba el puente parado hasta reiniciar; a los 90 s se da
  por perdida y se usa otro hilo (`TFBridge.STUCK_MS`). La clave nunca se regenera si no se pudo leer servidor.properties
  y tiene copia en `tfclient-puente.clave` (carpeta del servidor) y `config/tfclient/.puente.clave`
  (`TFServerConfig.readBackup`). Un fallo al leer puente-entregas.txt ya no deja el puente sin arrancar.
- **Warps** (`EssentialsWarps.visible`): a cada jugador solo los que puede usar (con per-warp-permission, su permiso
  essentials.warps.<nombre>; sin eso, todos menos los que se llaman admin/staff/mod/op/privado/oculto/test). OP, todos.
- **JEI**: `compat/jei/TFJeiPlugin` (API de JEI solo para compilar, `maven.blamejared.com`) quita los objetos de tfclient
  de la lista a quien no es OP ni está en creativo (`client/TFHiddenItems`, cada segundo) y los devuelve si pasa a
  serlo; la pestaña del creativo, igual (se rehace al cambiar). Prueba: `[prueba-jefes=oficios] [jei]` (fundir,
  fabricar, pociones, cobrar, rangos del clan, tormenta y JEI).

### CHECKPOINT anterior (25 oct 2026, TF Client 1.3.138, PUBLICADA)

**1.3.138** (el dueño: «o sea que no arreglaste esto: las alas de Accessories se quedan»). Lo de la 1.3.137 dependía de
la copia que el cliente tiene de los huecos de accesorios; si se queda vieja, las alas seguían. Ahora lo de la espalda
lo dice el **servidor** (`items/TFBackSync`): cada medio segundo mira el pecho y los huecos de Accessories y Curios
(también los cosméticos) de cada jugador y, si cambia, se lo cuenta a todos (mensaje 1 del canal `tfclient:wardrobe`). El
cliente solo dibuja lo que el servidor confirma (`TFBackSync.allowed`): los dibujantes que registramos en Accessories y
Curios, el respaldo de `BackLayer` y el Armario (`wearsBack`). Prueba: `[prueba-jefes=accesorios] [accesorios=curios]`
pone unas alas solo en la copia de Curios del cliente (como si se quedara vieja): sin el arreglo se dibujan (lo del
dueño), con él no.

### CHECKPOINT anterior (24 oct 2026, TF Client 1.3.137, PUBLICADA)

**1.3.137** (el dueño: «me están haciendo múltiples reportes de que las armaduras o sets de la web se quedan en el
jugador cuando las desequipa: cuando quita la armadura el mod sigue dibujando el item, pasa también con las alas»).
Causa: el **Armario** (web y app Armario del pad, `TFWardrobe`) dibujaba la pieza elegida en su hueco lleve o no algo
puesto: al quitarse la armadura o las alas seguía viéndose la del Armario (y con alas en un hueco que dibuja el mod de
accesorios, salían las del Armario y las puestas a la vez). Ahora el Armario solo cambia cómo se ve lo que lleva:
`TFWardrobe.armorLayer`/`headLayer`/`chestArmor` con el hueco vacío no dibujan nada; `BackLayer` solo pone las alas del
Armario si lleva alas o mochila en el pecho o en un hueco de accesorios (`TFAccessorySlots.wearsBack`), y si las dibuja
el mod de accesorios, nuestro dibujante pone las del Armario en su lugar (`TFAccessorySlots.wardrobe`). El pad lo dice
en la cabecera del hueco. Prueba en el juego: `[prueba-jefes=armario]` (pechera, Armario, quitarla, alas, quitarlas;
`TFItemsClient.BackLayer.debug`). El dueño después: «el casco Dragón Mecha de un jugador seguía tras reiniciar sin
llevar nada» (el «Sombrero Dragón Mecha» en su Armario: se guarda en wardrobe.json y en la web; con esto deja de verse sin
borrar su data) y «yo lo vi con las alas en el hueco de espalda de Accessories». Accessories 1.20.1 no arranca en la
prueba (pide Mixin nuevo y piezas de Fabric: Sinytra Connector); con Curios (`[prueba-jefes=accesorios]
[accesorios=curios]`, `-Ptfaccessories=curios` en build.gradle) quitar las alas, del hueco normal y del cosmético, deja de
dibujarlas. Para Accessories, nuestro dibujante solo pinta unas alas si siguen puestas en sus huecos
(`TFAccessorySlots.stillWorn`; si la lectura de huecos no ha funcionado nunca, no tapa nada). Si vuelve a pasar con
Accessories, pedir la línea «TF Client: accesorios: Accessories quería dibujar … que ya no lleva puesto» del latest.log.

### CHECKPOINT anterior (24 oct 2026, TF Client 1.3.136, PUBLICADA)

**1.3.136** (el dueño, con captura: «la primera vez que abro Minecraft después de instalar todo con el AutoModpack la
pantalla de carga sale así»: la roja de Forge con MOJANG y, abajo a la izquierda, la de TF pequeña y del revés).
Causa: el primer arranque aún no tiene `config/fml.toml` con `earlyWindowControl = false` (lo escribe
`TFConfig.disableForgeEarlyWindow` para el siguiente), así que la pantalla de carga es la de Forge
(`ForgeLoadingOverlay`), que deja la proyección en píxeles de la ventana con la Y hacia arriba; `TFLoadingOverlay`
dibujaba encima con ella. Arreglo: `TFLoadingOverlay.restoreGui` (guarda la proyección de la interfaz antes de la de
Forge y la vuelve a poner con el marco principal, la ventana entera, sin recortar caras y la mezcla normal; antes vuelca
lo que Forge dejó a medias y borra la profundidad: en su fundido final pintaba el menú y su rojo encima de lo de TF). La
prueba saca `00_carga_20_sin_arreglo` y `00_carga_40` … `00_carga_200` (en CI no hay fml.toml: es el primer arranque).
Para que ni el primer arranque muestre la ventana roja de Forge (antes de cargar los mods no hay nada del mod que lo
pueda cambiar): que el AutoModpack reparta `config/fml.toml` (`syncedFiles` de `automodpack/automodpack-server.json`)
con `earlyWindowControl = false`. Rapidez al menú: al llegar al menú el log dice «TF Client: arranque: N s … (la pantalla
de carga, M s; mods: K)»; para saber qué mod tarda, pedir al dueño `logs/latest.log` y `logs/debug.log` (Minecraft
apunta el tiempo de cada parte de la recarga). Recomendado: ModernFix (Forge 1.20.1) en el modpack.

### CHECKPOINT anterior (24 oct 2026, TF Client 1.3.135, PUBLICADA)

**1.3.135** (el dueño: «las protecciones de la tienda son la x10, x25, x40, x64 y x80; las de 100 en adelante van con
los rangos, no las podemos dar en la tienda»): `tools/tienda/precios.py` (`PROTECCIONES`, `VERSION` 3) y
`TFShopConfig.VERSION` 3. A una tienda de la 1.3.134 el servidor solo le quita las de 100x100 en adelante
(`TFShopConfig.dropRankStones`, copia `tienda.antes-v3.json`), sin tocar lo demás; una más vieja se cambia entera
como en la 1.3.134. Lo mira la prueba «tienda».

### CHECKPOINT anterior (24 oct 2026, TF Client 1.3.134, PUBLICADA)

**1.3.134** (tres pedidos del dueño):
1. **La tienda nueva** («estudia lo que cuesta ganar monedas, fija precios justos, no regalados; todos los objetos del
   juego por categorías; la tienda solo compra Tesoros con límite diario»). Estudio y reglas en
   **[`tf-client/TIENDA.md`](tf-client/TIENDA.md)**; la tabla, en `tf-client/TIENDA-PRECIOS.md`. La genera
   `tf-client/tools/tienda/precios.py` con las recetas del jar de 1.20.1, la lista de minecraft-data y los nombres de
   `es_es.json` (cómo se bajan, en su cabecera): 1.039 objetos en 10 categorías (Construcción, Decorativos, Minerales,
   Equipo, Drops de mobs, Granja y Comida, Redstone y Transporte, Utilidades, Protecciones y Tesoros), una hora de
   trabajo ≈ 75 monedas y comprar = 6 veces lo que ganarías en el rato de conseguirlo; fuera el diamante, la netherita y
   lo que los lleva, lo del Nether que hace avanzar y todo lo del End. La tienda solo compra los 16 Tesoros (límite al
   día). El servidor cambia su `tienda.json` solo (`versionTienda` 2, `TFShopConfig.VERSION`; copia
   `tienda.antes-v2.json`). VENDER del pad enseña también los Tesoros que no llevas (`PadShop.sell`).
2. **Clanes**: el cobro de la 1.3.133 está bien (`PadClans`: avisa al escribir el nombre y cobra al crear el clan); lo
   mira la prueba «tienda». **Compartir canciones** (el dueño: «al clicar en una canción, compartir: me crea un disco en
   la mano, se lo doy a otro, lo consume y se le guarda en una pestaña nueva, COMPARTIDOS»): botón morado en cada fila de
   Música (`PadMusicPage`), objeto `tfclient:disco_compartido` (`SharedDiscItem`, textura `tools/musica/disco_compartido.py`),
   servidor `PadMusicShare` (las de la carpeta se suben como las del altavoz y se guardan en `<mundo>/tfclient/compartidas/`,
   2 GB), cliente `MusicShare` (baja la canción y la mete en `MusicLibrary.SHARED`; lo pendiente, en
   `musica/compartidas-pendientes.json`). Mensajes `Share`/`Shared` (ids 26 y 27 del canal del pad).
3. **Compras de la web en el suelo** («que no desaparezcan aunque pase un clear lag»): `items/TFWebDrops` (con la marca
   `TFOrder` o un objeto de set con dueño): vida sin límite, invulnerable (lava, fuego, explosiones), y
   `mixin/EntityRemoveMixin` no deja que los borre `/kill` ni una clase de plugin o mod con «clear», «lag», «clean»,
   «limpi», «remove», «purge», «kill» o «butcher» en el nombre (las 8 llamadas de encima). Cogerlo, tolvas, imanes, juntar
   pilas, el vacío y los reembolsos siguen igual (sin duplicar). Si el servidor usa otro limpiador con otro nombre, el log
   no dirá «TF Web: … se queda»: añadir su palabra a `TFWebDrops.CLEANERS`.

Prueba en el juego nueva: `[prueba-jefes=tienda]` (tienda, clan, disco y suelo; menos de 2 min).

### CHECKPOINT anterior (24 oct 2026, TF Client 1.3.133)

**1.3.133**: fundar un clan cuesta 1.500 monedas (el dueño: «que crear un clan valga 1500 monedas y sea ajustable en el
pad de admin»). Ajuste `clanes.precio` de `pad.json` (`PadConfig.SETTINGS`, grupo `economia`; 0 = gratis), en el pad
de administrador › AJUSTES › ECONOMÍA («Fundar un clan», icono de estandarte). `PadClans`: la vista sin clan dice lo
que cuesta; al escribir el nombre avisa si no le llega; se cobra (`TFEconomy.take`) al final, cuando se crea el clan.
Sin prueba en el juego.

### CHECKPOINT anterior (24 oct 2026, TF Client 1.3.132, PUBLICADA)

**1.3.132**: editor de color de la etiqueta del clan (pestaña COLOR del líder, `PadClans.colorView`): paleta de 12 tonos
en 5 luces, grises, los 16 de Minecraft y campo `#RRGGBB`; el color se guarda en hex (`rgbOf`, acepta los nombres de
antes) y sale exacto en el chat y en el sufijo (`&#RRGGBB`). El sufijo del clan se pone por la API de LuckPerms
(`TFLuckPerms.setSuffix`), sin el mensaje de los comandos /lp en el chat. Sin prueba en el juego.

### CHECKPOINT anterior (24 oct 2026, TF Client 1.3.131, PUBLICADA)

**1.3.131**: el selector de objetos del pad de admin (`PadItemPicker`, «TODOS LOS OBJETOS»: tienda, recompensas, kits…)
enseña todos los objetos del juego en una sola lista con scroll (`PAGE` y `PadView.MAX_CELLS` 4096; la rejilla solo
dibuja lo visible). Sin prueba en el juego.

### CHECKPOINT anterior (24 oct 2026, TF Client 1.3.130, PUBLICADA)

**1.3.130**: el clan ya no pisa el prefijo de LuckPerms en la lista: con el plugin TAB no se toca la lista
(`PadClans.tabPlugin`), y la etiqueta del clan va como sufijo de LuckPerms (`PadClans.syncSuffix`, prioridad 50, al entrar
y al cambiar de clan; con LuckPerms el nombre ya no la añade para no repetirla). El escudo al cubrirse, un poco más arriba
(-0,18). «Traer su skin» también de jugadores conectados y bots (su perfil, `SkinMojang.get`). Sin prueba en el juego.

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.129, PUBLICADA)

**1.3.129**: el puente da todos los `give` (también los de Minecraft) con la marca del pedido (`TFRevocations.stampAny`),
así un reembolso quita también los objetos extra del rango; FALTA cobrar en monedas lo ya consumido (necesita contar lo
dado por pedido). Los rangos del pad con el peto de su set (`PadAccount.icon`, sin atributos). La cara propia en el pad,
con la skin que el juego usa para el jugador (`PadUi.ownHead` en `PadUi.item`, premium o no). Los sets de rango se llaman
como su rango (Mortal, Inmortal, Mágico, Eterno, Cósmico, Celestial, Fantástico) en `tf_sets.json` y `lang/es_es.json`.

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.128, PUBLICADA)

**1.3.128** (el dueño, al 5 % de uso): escudos de los sets que no tapan la vista al cubrirse (`ItemInHandRendererMixin`:
más abajo y a su lado en primera persona); fuera el «Test» de la tienda (queda en `/staff`); la piedra en la mano solo
con las 4 esquinas de suelo a techo del mundo (`ClaimPreviewLines`); piedras de los rangos: Mortal 80, Inmortal/Mágico/
Eterno 100, Cósmico/Celestial/Fantástico 150 (`tools/crates.py RANK_CLAIM` y `products.json`; la 250 es de pago); las
piedras entregadas llevan la marca de su pedido (`TFRevocations.stampAny`): al reembolsar desaparecen de inventarios y
cofres, y si están colocadas se borra la zona y se rompen (`TFRevocations.claimPlaced/breakClaims`, guardado en
`tfclient_revocations`); al romperla su dueño vuelve con la marca; en creativo también vuelve al inventario. Lo gastado
de un reembolso se cobra con la deuda de la 1.3.125 (`TFDebt`). Sin prueba en el juego.

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.127, PUBLICADA)

**1.3.127** (el dueño, con capturas): (1) «el Test no sirve, no llegan items ni rango»: el puente aplica la respuesta de
la web en un try (un fallo en efectos/skills/armario ya no impide las entregas) y cada entrega en su try; el panel
`/staff` enseña el error de cada pedido y «esperando a que el servidor lo entregue» (si sigue sin llegar, mirar ahí y
el latest.log, «TF Bridge»). (2) Su cabeza en la lista de jugadores del pad sin skin: `PadUi.skin` usa la de la cuenta
(`PadSkinModel.accountTextures`). (3) Alas que se quedan en la espalda: probablemente puestas en el Armario (pad › Armario
› Espalda), que se dibuja encima de todo (`BackLayer`). (4) Neones del pad sobre las abrazaderas naranjas: quitados de
`neon.png`/`neon_core.png` donde `frame.png` es naranja. (5) Daga Dark World (Mágico) tumbada en la mano: la colocación de
la espada de su set (`darkworld_dagger.json`).

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.126, PUBLICADA)

**1.3.126** (ocho quejas del dueño desde el servidor real):
1. Alas en el hueco de espalda: se queda el arreglo de la 1.3.125 de la otra sesión (BackLayer las dibuja si el mod de
   accesorios no lo hace, también en el hueco cosmético).
2. Planeo de las alas: 30 s (`TFWings.GLIDE_TICKS` = 600).
3. Al perder la conexión (o desconectar) se vuelve al menú principal, nunca a la lista de servidores (`TFLock.onScreen`).
4. Piedras de protección de los rangos que no llegaban: el puente da él mismo los `give <jugador> <mod:objeto> [n]` de
   objetos de mods (`TFBridge.run`; en Mohist el /give puede ser el de Bukkit, que no los conoce).
5. Casco de Eterno (`malika_helmet`): `"armor": true` (protección de su nivel) y encantable como casco
   (`Cosmetic.canApplyAtEnchantingTable`). La espada de Cósmico: capturas `espada_cosmico_*` en la prueba de `pad`.
6. Web: los rangos con su precio y `testClaim`: botón «Test» solo en el navegador donde se entró a `/staff` (guarda la
   clave en localStorage) y `/api/claim` lo exige con `ADMIN_KEY`.
7. Lag de los botones fuera de partida: Minecraft limitaba los menús a 60 FPS; ahora el límite del jugador
   (`MinecraftMixin.getFramerateLimit`).
8. La cara/muñeco propio del pad sin la skin (servidor sin premium): la de la cuenta de Minecraft
   (`PadSkinModel.account`, también en `PadCommunityClient.face`).

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.125, PUBLICADA; la de otra sesión)

Pedidos del dueño (con la 1.3.124 en el servidor; 3 capturas: inventario con alas en el hueco cosmético, TAB con los
bots y «MIEMBRO», chat con «Bots limpios… 2Ezequiel se ha unido» y `/tpa` que solo sugiere a Pewez777):
1. «Los comandos de los rangos no se aplican» — HECHO (sin probar en el servidor). Causa probable: `TFRankPerms` se
   fiaba de `<mundo>/tfclient/rank_perms.json`; con el mundo traído de otro servidor decía «ya está» y los grupos no
   existían en LuckPerms, así que `lp user X parent set <rango>` fallaba y el jugador seguía en default («MIEMBRO» en el
   TAB). Ahora `TFLuckPerms.group` mira con la API (GroupManager: existe, permisos, prefijo, peso) y `onRankList` vuelve
   a poner todo si falta algo (como mucho cada 5 min); `setRank` crea los grupos antes si no existe el del rango
   (`TFRankPerms.ensureGroups`); al crear un grupo, `setweight 100+tier*10` y `meta setprefix` «&<color>&l<RANGO>&r »
   si no tiene (lo del staff no se toca).
2. «Los encantamientos de las armaduras de los rangos no se aplican» — HECHO. Con el inventario lleno las piezas caían
   al suelo y `sets encantar` (justo después de `give`) no las veía. `TFSetEnchant.enchantNow`: inventario, las tiradas
   a 8 bloques y, 10 minutos, las que recoja (`EntityItemPickupEvent`) o le entren al inventario (cada segundo).
3. Menú principal «contorno raro» y «WhatsApp se ve comida» — HECHO. Sin contorno (con la Cinzel de la 1.3.124 se veía
   mal): sombra suave de un píxel real. El ancho del botón se mide con la misma letra que se dibuja (`fontIndex`) y si
   no cabe se baja un paso de letra entera (n−1), nunca una escala rota (perdía columnas). La prueba del pad saca
   también `menu_principal_escala2`. `tools/menu_sim.py` sigue simulando el diseño de la 1.3.121 (Titan One).
4. Alas en el hueco cosmético que no salen — HECHO A CIEGAS (sin Curios/Accessories en la prueba). `TFAccessorySlots`:
   si el mod de accesorios no llama a nuestro dibujante para ese jugador en 250 ms, BackLayer dibuja lo de los huecos de
   espalda, también los COSMÉTICOS (`TFAccessoryLookup.cosmeticsFromCurios/cosmeticsFromAccessories`). Queda en el log
   «TF Client: accesorios: el mod de accesorios no dibujó …». Pedir al dueño las líneas «TF Client: accesorios» y
   «alas» de su latest.log si sigue sin verse.
5. Reembolso que quite las monedas (saldo negativo) — HECHO. Web (`revoke` en `src/app.js`): por cada
   `tf web monedas|verdes dar {player} N` del producto, `tf web monedas|verdes retirar {uuid} N`. Mod: `economy/TFDebt`
   (`<mundo>/tfclient/deudas.json`): se quita lo que tenga (también desconectado; TF, Vault o comandos) y el resto es
   deuda; `TFEconomy.balance` y `PadGreen.balance` la restan (saldo negativo, no puede gastar); lo que gane paga antes la
   deuda (`TFEconomy.give`, `PadGreen.add`); al entrar y cada minuto se cobra lo que le entre por otro camino. Web: 52
   pasan y los mismos 7 de `main` fallan (rangos gratis de la prueba de la tienda de otra sesión); con los precios de
   verdad de los rangos (de 4d4aa9e9), 59/59.
6. Bots — HECHO (sin probar en el juego: el dueño pidió solo compilar). `server/TFBotPlayer`: cada bot entra como un
   jugador de verdad (`PlayerList.placeNewPlayer` con una conexión falsa: `Connection` con un `EmbeddedChannel` puesto
   por reflexión, que no manda nada y da por enviado lo que le mandan, así `/kick` lo saca), para que Mohist le cree su
   jugador de Bukkit y lo vean TAB, la consola, EssentialsX (`/tpa` lo sugiere) y LuckPerms (`TFLuckPerms.loadUser` lo
   carga antes, con su grupo por defecto). No se manda su entidad a nadie (`broadcastToPlayer` false), cuenta como
   espectador para el juego (monstruos, dormir, empujones) pero el TAB lo enseña en supervivencia, no recibe daño.
   `TFBots`: los de verdad en `REAL`; los que estaban vuelven de uno en uno al arrancar (`RESTORE`); al apagar no
   «salen»; un `/kick` lo saca de la lista (`onLogout`); el ping es `ServerPlayer.latency`. `MinecraftServerBotsMixin`
   y el envío de la lista al entrar solo suman/mandan los de solo-lista (`listOnly`). `PadPlayers` no los repite. Con
   `bots.reales=false` (servidor.properties) o si falla al meter uno, va solo en la lista como antes.
   **Regla del dueño**: al arrancar con otra versión del TF Client (`mod` en `config/tfclient-bots.json`), y con
   `/tf bots limpiar`, se borra el registro y los datos de los bots (`wipeData`: playerdata/stats/advancements de
   Minecraft, `plugins/Essentials/userdata/<uuid>.yml`, `lp user <uuid> clear` de los que vinieron y su ficha del pad,
   `PadStats.forget`).
7. Entrar más rápido al servidor (cachear la verificación de mods de Forge) — POR HACER (no entra en la 1.3.125). Mirar qué tarda al conectar
   (AutoModpack, la sincronización de registros de Forge, el árbol de búsqueda creativo / JEI al entrar, lo del propio TF
   Client al entrar); pedir al dueño el latest.log de una entrada para medir.

Publicada sin prueba en el juego (el dueño: «haz lo de los bots rápido y así compila el mod, no pruebes in game»).

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.124, PUBLICADA)

**1.3.124** (el dueño: «sigue habiendo lag en los botones y su animación, hasta los botones de ajustes»): cada pastilla
(`TFPill.paint`) se pinta una vez en una imagen de su tamaño en píxeles reales (`TFPill.texture`: relleno, cristal, hilo
y el doble hilo del principal, con los bordes suavizados) y se dibuja de un solo trazo; con el ratón se funde encima la
imagen encendida. Antes eran miles de rectángulos por botón y fotograma (las esquinas píxel a píxel). Vale para el menú
principal, Opciones y todos los botones con el tema de TF. Además (el dueño: «los botones siguen algo grandes, el
contorno de las letras un poco pasado, la tipografía no me convence con la forma del botón»): filas 21/17/17/17/15,
menos aire a los lados, contorno de un píxel y letra **Cinzel** en el menú principal (antes Titan One; sus
`titanone_N.json` siguen en el jar por si se quiere volver), `MENU_TEXT_UP` 3.9.

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.123, PUBLICADA)

**1.3.123** (el dueño: «no me reconoce el server, pon mediante IP y puerto en el jar: 216.163.187.40:19015, rápido»):
`TFLock.EMBEDDED` = `216.163.187.40:19015` (el dominio `tierras.ultramc.co` no llevaba al servidor). También en la web
(`SERVER_IP` de `wrangler.jsonc`, el valor por defecto y las páginas) porque el mod pregunta la IP a la web y la de la
web manda; y en `server-ping.yml`. Se sigue pudiendo cambiar desde `/staff`.

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.122, PUBLICADA)

**1.3.122**: la guía de la config se llama `config/tfclient/LEEME.md` (el dueño: «quita el apellido IA»); el
`LEEME-IA.md` de antes se borra al arrancar (`TFConfigDir.writeGuide`). Lo demás, como la 1.3.121 (abajo).

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.121, PUBLICADA)

**1.3.121: migración al servidor de verdad y cuatro pedidos del dueño** (dos imágenes nuevas: fondo y carga):
1. **IP `tierras.ultramc.co`** (`client/TFLock.EMBEDDED`): el mod solo entra ahí (`ConnectScreenMixin`). Solo el dueño
   la cambia: en `tierrasfantásticas.store/staff` (clave `ADMIN_KEY`), «IP del servidor» (`POST /api/staff/server`,
   setting `server_ip` de la web; vacía = `SERVER_IP` de `wrangler.jsonc`). El mod la pregunta al arrancar
   (`TFLock.fetchOfficial`, siempre a `TFConfig.OFFICIAL_WEB`, no a `web.url` de `cliente.properties`); si la web no
   responde, la del mod. `server.address` de `cliente.properties` ya no se lee (se borra del archivo). La web enseña la
   misma IP (`/api/config → serverIp`). `server-ping.yml` mira `tierras.ultramc.co:25565`.
2. **Piedra de protección en la mano** (`claims/client/ClaimPreviewLines`): sin líneas a ras de suelo; postes cada 4
   bloques del suelo a lo alto de la piedra (bloque del jugador + 1), una línea recta a esa altura y las esquinas altas.
3. **Menú principal** (`TFMenuButton`, `TFPill`, `TFMenuPaint`): cada botón del ancho de su palabra (con sitio para
   los hilos de oro de los lados, que se quedan), filas más bajas (`ROW_H` 24/19/19/19/17), sin destello (fuera
   `TFPill.shine`), Salir rojo, WhatsApp verde, Web morado y Discord azul siempre, texto 1,45 px de letra más arriba
   (`MENU_TEXT_UP` 4.5: medido en la captura de la 1.3.120, las mayúsculas quedaban 3,5 px bajo el centro). Lag: el
   texto (contorno y letras) en un solo `drawManaged` (antes ~2000 envíos por fotograma), contorno con 8 copias y las
   formas de las pastillas en caché (`TFMenuPaint.SHAPES`; también las del diálogo de los caballeros). Fondo
   (`menu_background.png`) y carga (`loading_background.png`, con el título) nuevos: las del dueño (1536x865) a
   1920x1080 con Lanczos y un enfoque suave.
4. **AutoModpack primero** (`client/TFAutoModpack`): sin AutoModpack (modId `automodpack` o un `automodpack*.jar` en
   mods) no se entra al servidor: pantalla «Necesitas AutoModpack» (carpeta mods, Discord, volver). Sin versión fija
   (el dueño: «nosotros la compartimos»). Con él, se conecta sin mirar los demás mods: fuera `TFServerCheck`,
   `TFServerCheckScreen` y `TFMissingModsScreen`.

Prueba en el juego: la de `pad` saca también `falta_automodpack`.

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.120)

**1.3.120: nueve pedidos del dueño** (4 capturas y un mensaje a mitad: «rework de los botones del menú principal»):
1. **Círculos detrás de los iconos del pad**: fuera el `PadSkin.glow` de detrás del icono de cada fila
   (`PadViewPage`); y `TFPadScreen.render` llama a `ShaderPacks.fixBlendForGui()` al empezar (con Photon, la caché de
   mezcla de GlStateManager desfasada volvía discos sólidos los brillos suaves).
2. **Etiquetas del pad más arriba** (las letras iban pegadas abajo): pestañas y botones de `PadSkin`, `PadUi`,
   `PadDataPage` y `PadMusicPage` 1-2 píxeles más arriba.
3. **Bots** (`server/TFBots`): `/tf bots add cantidad <n> [segundos]` y `/tf bots add nombre <nombre>` (sugiere los
   libres), `/tf bots remove nombre <nombre>`, `/tf bots limpiar` (fuera todos y su registro). El registro empieza de
   cero: `FILE_VERSION = 2`, un `config/tfclient-bots.json` de antes se ignora; solo tienen datos los que se unan.
4. **Empezar limpios** (`server/TFDataReset`): al arrancar la 1.3.120 por primera vez (marca
   `<mundo>/tfclient/limpieza-1.3.120.hecha`) pasan a `<mundo>/tfclient/respaldo-<fecha>/` monedas, monedas del
   gachapón, estadísticas/ranking, títulos, hogares, regalos, clanes, oficios, skills, efectos, armario, tienda y su
   registro, GTS y su registro, Comunidad (fotos y skins), las protecciones (`claimblocks_data.json`) y los bots; de
   Viajes, Pase, Gachapón, Kits y Recompensas solo «jugadores» (lo del staff se queda). Los UUID que tenían datos van a
   `reinicio-web.json`: `TFBridge` los manda en `/bridge/poll` como `reset` y la web (`store.resetPlayer`) borra sus
   rangos, efectos, skills y armario, cancela las entregas pendientes y marca sus pedidos «reiniciado»; con `resetDone`
   se borra el archivo. Otra vez: `/tf limpiar datos` (staff 4; al reiniciar el servidor).
5. **Piedra de protección en la mano** (`claims/client/ClaimPreviewLines`): el borde de la zona que haría, como una
   valla de luz del color de la piedra (línea gruesa a ras de suelo, otra 2 bloques más arriba, postes cada 4 y las
   esquinas altas; tenue a través del terreno), no partículas (`ClaimOutlineRenderer` ya no pinta la vista previa).
   Colocada, el contorno de partículas de siempre.
6. **Cámara** (`PadCamera`): clic derecho = selfie (tercera persona de frente; Esc vuelve a la cámara de antes), rueda =
   zoom (1-8x). Foto publicada hasta 1920x1080 (4,2 MB; antes 1280x720), 10 fotos en memoria. Las fotos se sacan del
   fotograma ya pintado (con shaders si los lleva): los demás las ven igual.
7. **Regalos** (`PadPlayers.gift`): tocar un objeto ya no te saca (las vistas de regalo no tenían pestaña y el pad
   volvía a la lista); eliges el objeto, escribes la cantidad (hasta lo que tienes; TODOS) y sale de tu inventario; las
   monedas, igual. Mismo arreglo en el menú del jugador y en elegir zona.
8. **Comentarios en las fotos** (`PadCommunity*`): botón Comentarios (con cuántos) que abre su ventana encima de la
   lista: la foto en pequeño (clic = en grande), cada comentario con la cara de la skin (la del Armario, la del
   conectado o la última que se le vio), nombre, cuándo, texto con emojis y sus reacciones (una por jugador, como las
   fotos; 12 emojis distintos), botón de reaccionar (el mismo selector) y la X de borrar (el que lo escribió, el dueño
   de la foto o el staff; dos clics). Campo abajo (140 letras, Intro envía, Ctrl+V). En `comunidad.json`:
   `comentarios: [{id, autor, nombre, texto, fecha, reacciones}]`, 300 por foto, uno cada 4 s. Paquetes 17-19 del
   canal del pad. La tarjeta: like, Reaccionar y Comentarios en una barra abajo (a ras del pie de la foto, se acortan si
   no caben), las reacciones encima y BORRAR/DENUNCIAR arriba a la derecha.
9. **Botones del menú principal, rehechos** (`client/TFPill`, `TFMenuButton`, `TFButtonTheme`): sin texturas (fuera
   `menu_main.png/.json`, `button_slice.png`, `menu_glow.png`, `button_shine.png`, `TFMenuPlates` y
   `tools/gen_menu_buttons.py`). Pastillas de cristal oscuro con hilo de oro como las respuestas de los caballeros, en
   píxeles reales: sombra, halo de su color con el ratón (oro; rojo Salir, verde WhatsApp, azul Discord), cristal,
   destello al entrar el ratón (el principal, cada 6,5 s), hilos de oro a los lados del texto que crecen con el ratón
   (en Tierras Fantásticas siempre, con doble hilo), sube un píxel y se hunde al pulsar. Columna limpia: Tierras
   Fantásticas; Mundo local; Mods; Opciones | Salir; WhatsApp | Web | Discord; el emblema baja con los botones. Los
   demás menús (Opciones, mundos…) y los deslizadores, con la misma pastilla. Texto centrado a la vista medido con
   `tools/menu_sim.py` (±1 píxel real; ahora simula el diseño nuevo).

Prueba en el juego: la de `pad` saca también Comunidad (una foto de un bot con comentarios: `pad_comunidad`,
`pad_comentarios`, `pad_comentarios_emojis`), la piedra en la mano (`piedra_en_mano*`) y el menú principal y Opciones
(`menu_principal`, `menu_principal_raton`, `menu_opciones`).

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.119, PUBLICADA)

**1.3.119** (el dueño: «Cósmico y Celestial no traen heal, pónselos; el pad debe estar ligado a los hogares o permisos;
usamos Multiverse, el spawn está en otro overworld (un mundo vacío): los tp entre dimensiones y Explorar siempre fuera
del overworld del spawn»):
- Web (`tools/crates.py`, `RANK_SERVER_PERKS`): /heal también en Cósmico y Celestial (`essentials.heal`). El TF Client
  lo pone solo en LuckPerms al cambiar la lista de rangos.
- `server/TFBukkit` (reflexión, Mohist): comandos como el jugador (`Player.performCommand`), si un plugin tiene un
  comando, el nombre de Bukkit del mundo del jugador y sus permisos.
- Hogares del pad (`PadHomes`): el límite **como lo cuenta EssentialsX** (1; con `essentials.sethome.multiple`, el
  «default» de `sethome-multiple`; el mayor de cada grupo cuyo permiso `essentials.sethome.multiple.<grupo>` tenga;
  `…unlimited` = sin límite), con los permisos de Bukkit (LuckPerms). /home, /sethome y /delhome como el jugador por
  Bukkit (EssentialsX lleva entre los mundos de Multiverse). Los mundos de Multiverse se ven por su nombre («Spawn»).
- Viajes (`PadTravel`): Spawn = el mundo de `pad.json → mundos.spawn` si se puso; si no, el `/spawn` de EssentialsX
  como el jugador; si no, el del mundo normal. Los warps, como el jugador por Bukkit.
- Explorar: el mundo de `mundos.explorar` si se puso; si no, el mundo normal y, si es un mundo vacío (todos los intentos
  caen al vacío), los demás overworlds con terreno (primero los «surv…»); nunca el del spawn ni uno ya visto vacío.
  `/tf web viajes explorar|spawn aqui|auto` y `/tf web viajes lista`.
- **No probado en el juego** (sin Multiverse ni EssentialsX en GitHub Actions): compilado. En el servidor: `/tf web
  viajes lista` dice a qué mundo van Explorar y Spawn.

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.118, PUBLICADA)

**1.3.118** (el dueño: «¿y lo de los permisos cómo quedó?»): dos fallos de los permisos de los rangos (`TFRankPerms`):
- Con `parent set` el jugador sale del grupo `default` y los grupos de rango no lo heredaban: perdía lo básico que el
  staff tenga en default. Ahora cada grupo de rango hereda de default (`lp group <rango> parent add default`).
- La firma de lo aplicado (`<mundo>/tfclient/rank_perms.json`) pasa a «v2|…»: si con la versión anterior los comandos
  se dieron por buenos sin llegar a LuckPerms (iban por el despachador de Forge), al arrancar la 1.3.118 se vuelve a
  poner todo una vez, ya por la consola de Bukkit. A mano: `/tf web rango permisos`.
- Permisos de cada rango (de `tierras-fantasticas/tools/crates.py`, `RANK_SERVER_PERKS`): ver la respuesta al dueño;
  Cósmico no trae /heal (Eterno sí) y Celestial no trae /heal ni /feed: se le preguntó si es así.

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.117, PUBLICADA)

**1.3.117: ocho quejas del dueño** (5 capturas y un zip de NPC de Wardenful Studio) **y el rango en LuckPerms** (9):
1. **Ranking del pad**: salen los bots que han entrado alguna vez (`TFBots.known`, `rankValue`): misiones, cazas y
   exploraciones de su ficha y monedas de quien empieza (de unas decenas a poco más de mil, nunca múltiplo de 5), sin
   likes. El «2º ?» era alguien sin nombre en el pad: el nombre sale del pad, del conectado, del bot o de la caché de
   perfiles del servidor; si no se sabe, no sale (`PadRanking.withBots`, `name`).
2. **Jugadores del pad sin ficha**: al tocar a alguien (jugador o bot), Invitar al clan (si eres líder u oficial y él
   no tiene clan: `PadClans.canInvite/invite`), Invitar a la protección (eliges la zona; un jugador pasa a ser miembro,
   un bot no entra en los datos: `PadClaims.zonesFor/addMember`) y Enviar regalo (el de antes: objeto del inventario o
   monedas; ahora también a los bots). Pestañas `f:` (menú), `p:` (zona), `r:` (regalo).
3. **Caballeros que suben y bajan**: su modelo es el de Caelthar y trae `idle` (flotar); `VfxLayers` lo ponía solo por
   debajo de todo. `KnightRenderer`: a los sacros, sin reposo ni andar por defecto (`setDefault(idle/walk, "-")`). La
   prueba `corta` apunta «reposo por debajo: [-]».
4. **Objetos negros con shaders en el Void**: la prueba `corta` pone objetos en la mano y en el inventario en el
   santuario y en el mundo normal (`objetos_void*`, `objetos_mundo*`, `BossTestClient.itemsLight`). Medido (`a64be32`,
   Complementary): el objeto en la mano, luminancia 45 en el Void frente a 108 fuera (el inventario, igual en los dos);
   en el Void la luz del jugador es bloque 0 y cielo 0, y el paquete (con sus programas del End) solo le da su ambiente.
   `vacio/VoidItemLight`: con paquete y en el Void, la mano (`ItemInHandRendererMixin`) y los objetos tirados
   (`ItemEntityRendererMixin`) con luz de cielo 15 y de bloque 12. **Comprobado** (`eaf789b`): 68 en el Void (la
   manzana dorada se ve dorada). Si el dueño los sigue viendo oscuros con Photon, subir `VoidItemLight.BLOCK` (máx. 15).
   También comprobado en `a64be32`: caballeros «reposo por debajo: [-]», el Archivista sin efectos pegados, el ranking
   con bots (332, 311, 241… monedas; 5, 5, 4… misiones) y el menú de un bot sin ficha.
5. **Llamas del Archivista pegadas**: `SkillRuntime.exec`/`cancelCast`: lo que hace un efecto (actor) no lleva el uso
   del jefe; el combo del cerebro ya no cancela su `remove{delay=20}` (JEFES.md §3, tabla de MythicMobs).
6. **Web**: cosméticos y clases de skills **solo con Fantastic Coins** (`coinsOnly`; `/api/checkout` los rechaza con
   `code: coins`); skills a **50.000** (`tools/skills.py`); tarjetas de crates y protecciones con el precio centrado y
   el regalo y comprar debajo, del mismo alto; en el móvil la crate dice solo «Comprar». `npm test` 57/57.
7. **TF Client solo para Tierras Fantásticas** (`client/TFLock`, `mixin/ConnectScreenMixin`): toda conexión a un
   servidor que no es el oficial (`TFLock.OFFICIAL`, ahora `216.163.187.40:19229`) va al oficial; cliente.properties ya
   no puede poner otro. **Cuando el dueño pase la IP final: añadirla a `TFLock.OFFICIAL` y a `TFConfig.DEFAULT_ADDRESS`.**
   Mundos locales solo con contraseña (`TFLocalLockScreen`; en el repo solo su huella PBKDF2 con sal: **la contraseña
   la tiene el dueño, no la escribas en el repo**); la prueba en el juego entra sin ella (`BossTest.ON`).
   Copyright en los 420 archivos propios (no en `crates/libs`, `javazoom`, `net/sourceforge`: son de terceros),
   `LICENSE` (y `LICENSE-TFCLIENT.txt` en el jar), mods.toml y el manifiesto. **No hay forma de impedir descompilar un
   jar** ni contraseña para eso (Minecraft tiene que poder leerlo); ofuscar con ProGuard rompería mixins y reflexión.
   Lo que sí protege de verdad: **el repo es público** (cualquiera lee el código sin descompilar nada). Se le propuso
   hacerlo privado y servir el jar desde otro sitio (las Releases de un repo privado no se descargan sin cuenta).
9. **El rango comprado, en LuckPerms** (el dueño: «/lp user (usuario) parent set (rango)»; rangos mortal, inmortal,
   magico, eterno, cosmico, celestial, fantastico; «con Essentials también»): la web manda `lp user {player} parent set
   <rango>` (antes `parent add`, y al mejorar quitaba los de abajo). `server/TFLuckPerms`: todo comando de LuckPerms
   (también los de `TFRankPerms`: crear los grupos y sus permisos de EssentialsX) va **por la consola de Bukkit**
   (`Bukkit.dispatchCommand(getConsoleSender())`, por reflexión), como si lo escribiera el staff; antes iba por el
   despachador de Forge con una fuente propia, que en Mohist puede no llegar al plugin, y se daba por bueno sin mirar.
   Sin Bukkit (LuckPerms como mod), por la consola de Minecraft. A los 2 s mira con la **API de LuckPerms** (por
   reflexión: ServicesManager de Bukkit o `LuckPermsProvider`) que tenga el grupo; si no, lo repite; si sigue sin
   tenerlo, error en la consola y aviso al staff conectado. **Staff**: si tiene un grupo que no es de la tienda (admin,
   mod…), no se le quita: `parent add` del rango y fuera solo los otros rangos. Al entrar y en cada consulta, si la web
   dice que un conectado tiene un rango y LuckPerms no le tiene el grupo (ni uno igual o más alto), se le pone
   (`TFLuckPerms.ensure`). Los reembolsos siguen con `parent remove` (y `parent add` del anterior). `/tf web rango
   <jugador>` enseña además sus grupos en LuckPerms y lo último que hizo el TF Client. EssentialsX: sus permisos van en
   el grupo de cada rango (`TFRankPerms`) y `essentials reload` también por la consola de Bukkit. **No se puede probar en
   GitHub Actions** (LuckPerms para Forge está compilado para el servidor de verdad): la prueba solo comprueba que sin
   LuckPerms lo dice y no se rompe. Hay que mirarlo en el servidor del dueño con una compra o con `/tf web rango`.
8. El zip «NPCs For Mini Games vol.1» (bedwars, pvp, rtp, skywars, spleef: un `idle` cada uno; el «pvp» es un gato con
   orejas y cola, no un caballero) no se ha metido: se le preguntó para qué lo quiere.

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.116, PUBLICADA)

**1.3.116** (el dueño, tras la 1.3.115: «¿qué tienen que ver esas armaduras en los rangos si ya tenemos los sets
custom? Esos encantamientos no los muestres; no muestres ningún item extra porque va contra las reglas: en el juego se
les dan, en la página no se mencionan, o pon "y más cosas en el juego"»):
- Fuera las armaduras y herramientas vanilla de los rangos. Lo que se encanta es **el set propio del rango**: la web
  manda `tf web sets give {player} <set>` y luego `tf web sets encantar {player} <set> <nivel>` (aparte: con un mod de
  antes el set llega igual, sin encantar, y la entrega sale con error). `items/TFSetEnchant`: cada pieza recibe los
  encantamientos vanilla de su nivel que el juego le permite (`Enchantment.canEnchant`, compatibles): 1 Mortal …
  5 Cósmico; 6 Celestial y Fantástico, todos al máximo (sin maldiciones). La prueba del pad entrega el de Fantástico y
  apunta lo que lleva cada pieza. **Comprobado** (`8ff9480`): armas con filo 5, saqueo 3, aspecto ígneo 2…; armadura con
  protección 4, reparación, espinas 3…; arco con poder 5; escudo con irrompibilidad y reparación; las alas, nada.
- En la web no sale nada extra (ni monedas, ni recursos, ni encantamientos, ni iconos): en los rangos, «Y más cosas en
  el juego»; en las crates, la misma frase al final de la descripción. Se siguen dando igual en el juego.
- **Hay que poner la 1.3.116 en el servidor** (si no, `encantar` falla).

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.115, PUBLICADA)

**1.3.115: once quejas del dueño** (con 2 capturas y un mp3; la 1.3.114 está PUBLICADA):
1. **Caballeros del santuario «con la animación de volar»** (su captura: Osric de cerca, con la postura de «leer»:
   brazos adelante, cabeza baja): fuera las posturas propias (leer, agacharse, alzar el farol, hacerse sombra).
   `KnightRenderer.sacredPose` ahora es la de un jugador: derechos, vaivén de brazos de `bobArms`, el brazo de lo que
   llevan a 18° y al andar piernas y brazos como `HumanoidModel`. La prueba `corta` saca a cada uno de frente y de lado
   (`caballero_N_lado`) con la cámara donde no la tape nada (`BossTest.knightCam`). **Comprobado** (prueba `16ecf50`):
   derechos, de lado y andando (piernas hasta 0,60).
2. **Altar de la armadura del Void** (`VoidArmor.pedestal`): tarima de piedra negra pulida 5×5, escalón con 4 racimos de
   amatista y obsidiana llorosa en el centro; el soporte de pie encima. Se construye una vez, también en mundos de antes,
   sin romper nada. La burbuja (`SanctuaryFx.altar`) va centrada en el soporte de verdad (era asimétrica: centrada en la
   altura guardada del altar) y sin el círculo del suelo. **Comprobado** (`16ecf50`): de pie sobre la obsidiana llorosa
   (a 0,00) y la burbuja redonda alrededor.
3. **Sonidos de las crates por rareza** (`tools/crates/sfx_crates.py`, `premio()`): los cinco eran de la misma familia
   (Re mayor, celesta y destellos). Ahora cada uno con su carácter y graves de verdad: común (golpe seco y metales), rara
   (soplo que estalla en cristal y arpa, Mi mayor), épica (al revés, caída al subgrave, coro «ooh» en Re menor),
   legendaria (redoble y fanfarria en tresillo con platillo) y mítica (subida de 1,25 s, doble golpe, coro que asciende,
   campana). La escena los pide antes (`CrateCinematicScreen.PRIZE_LEAD`) para que el golpe caiga al salir el premio; la
   crate del mundo y la clásica usan la versión sin subida. **Música de las crates más alta**: a 1,0 y el archivo de
   −15 a −12 LUFS (`cifrar_musica.py --descifrar` y vuelta a cifrar).
4. **Bots** (`TFBots.card`): 0 fotos y 0 likes; un oficio de nivel bajo, de 0 a 5 misiones, alguna caza y pocos viajes.
5. **Tienda web** (`tools/crates.py`): todas las crates a 4,99 $ con 100 Fantastic Coins, 1 Gacha Coin, 8 manzanas
   doradas, 1 de Notch, 8 diamantes, 32 botellas de XP, 32 zanahorias doradas y 16 perlas. Rangos con su piedra,
   Fantastic Coins (100 a 500) y Gacha Coins (2 a 10), manzanas doradas y de Notch, tótems (desde Inmortal) y armadura y
   herramientas encantadas según el rango (vanilla); Celestial y Fantástico, de diamante con todos los encantamientos
   al máximo. (Después de publicar, el dueño: «te pasaste con las monedas, nada de 15k ni más de 700, y nada de
   netherita»: eran 750 en las crates y de 1.000 a 15.000 en los rangos, y Fantástico iba de netherita. **No dar más
   de ~500 monedas por compra ni nada de netherita.**) Comandos nuevos del mod: `/tf web verdes dar|quitar|ver`. **Cada premio con su
   icono** en la web (`rewards` en products.json, `rewardsGrid` en public/app.js: icono, cantidad, encantamientos).
6. **Pozos de luz** (`tools/void/sonidos_viaje.py`): el «acordeón» era el coro sintetizado; ahora soplo cálido, colchón de
   senos puros y brillo alto (sin coro, campanas ni arpa). **Música del santuario**: la del dueño («La petite fille de la
   mer», 5:55, −12,5 LUFS), cifrada en el jar (`sounds/santuario/musica.ogg`; `SealedAudio` también abre esa carpeta).
7. **Economía exigente**: oficios (acciones, misiones y niveles), misiones del pad y cazas al 60 % (`PadEconomy.scale`,
   una vez, marca `economia: 3`, copia `.antes-economia3.json`; los archivos por defecto, ya rebajados); en la web,
   2.500 Fantastic Coins por dólar (antes 1.250) y la clase de skills a 15.000; el regalo de diamantes, 3 (antes 5).
8. **Piedras de protección**: el escudo se queda; fuera el grano, los desconchones, las grietas, los remaches, las rayas
   de reflejo y los canales de arriba (las gemas del marco, solo en la de 500). Icono de la pestaña de la tienda rehecho.
9. **La config del servidor manda**: el cliente usa la web que le dice el servidor (`TFPadNet.State.web`,
   `TFConfig.webUrl`); `LEEME.md` (antes `LEEME-IA.md`) explica que en el cliente solo cuentan cliente.properties y las preferencias.
10. **Roles de Discord al comprar un rango**: por su nombre (`DISCORD_ROLE` en crates.py; `Discord.resolveRoles` los busca
   con el bot, exactos o sin adornos). Hace falta el bot con permiso de gestionar roles y su rol por encima de los rangos.

### CHECKPOINT anterior (23 oct 2026, TF Client 1.3.114, PUBLICADA)

**1.3.114: nueve quejas del dueño** (con 5 capturas; «toma en serio esto por favor» lo de la economía):
1. **Las monedas se llaman Fantastic Coin y Fantastic Gacha Coin** (no «monedas» ni «moneda verde»): en el pad, los
   premios, los kits, el objeto (lang es/en) y `TFServerConfig` (`economy.currency` por defecto «Fantastic Coins»; el
   «monedas» de antes se cambia solo).
2. **Void**: el soporte de la armadura del altar flotaba 1 bloque (la prueba lo midió: 1,00 sobre el cuarzo liso):
   `VoidArmor.groundY` busca el suelo de verdad; **comprobado** (prueba `69ce034`: a 0,00 del cuarzo). Los caballeros
   del santuario «hacen la animación de volar»: la prueba `corta` mide en el cliente su velocidad y sus piernas
   (`BossTestClient.KNIGHT_MAX`: hasta 0,23 bloques/tick y piernas hasta 0,73) y en las capturas de lado sin HUD
   (`caballero_N_andando`) andan por el suelo con las piernas en paso: **no se reproduce**. Si el dueño lo sigue viendo,
   pedirle un vídeo (puede ser su servidor: si los mueve a saltos, el cuerpo se desliza sin mover las piernas).
3. **Encantados morados con shaders** (su captura: el inventario, los encantados enteros de 63,30,102 con la forma de
   su modelo): el brillo se pinta tapando el objeto en vez de sumarse. Causa (leído en Oculus 1.7/1.8 y en el
   `shaders.properties` de Photon, que pone `blend.gbuffers_X = off` y mezclas por búfer `blend.X.colortexN`): Oculus
   pone las mezclas por búfer con `glBlendFunci`/`glEnablei` sin pasar por la caché de `GlStateManager` y al acabar
   «restaura» por esa caché (no hace nada), así que el búfer 0 se queda con la mezcla del paquete y el brillo (alfa 1)
   con ONE, ONE_MINUS_SRC_ALPHA tapa el objeto. `ShaderPacks.fixBlendForGui` (`GuiGraphicsMixin`, antes de cada objeto
   en la interfaz con un paquete puesto): suelta el bloqueo de `BlendModeStorage` y pone en OpenGL la mezcla de la caché
   (los campos de `GlStateManager.BLEND` se buscan por tipo y su orden se comprueba en marcha). La prueba `75e21a6`
   (Complementary; Photon no arranca en la máquina de la prueba) vio **93 desajustes** entre OpenGL y la caché al pintar
   objetos; el inventario con encantados sale bien con el arreglo (sin romper textos ni transparencias). La primera
   vez que hay desajuste se apunta en el registro del dueño («la mezcla de OpenGL no era la que cree Minecraft»).
4. **TF Pad admin**: apps nuevas **TF PASS** (`PadAdminPass`, a_pase: temporada, niveles, XP y los premios de cada
   nivel) y **GACHAPÓN** (`PadAdminGacha`, a_gacha: precios, garantía, rarezas y premios con su peso); en **OFICIOS**
   cada oficio se abre (`PadAdminJobMissions`): lo que paga cada acción y sus misiones (todas sus claves) y misiones
   diarias por oficio.
5. **Todo configurable a mano o con una IA**: el mod escribe `config/tfclient/LEEME.md` (hasta la 1.3.121, `LEEME-IA.md`) al arrancar (qué archivo
   cambia cada cosa, cómo se escriben los premios, `/tf reload`); `pase.json` y `gachapon.json` llevan `_ayuda`.
6. **Piedras de protección dibujadas a mano** (`tools/gen_claim_blocks.py`): marco tallado con bisel y cantoneras de
   metal (cobre hasta 40, hierro hasta 150, oro en 250/300/500), escudo de cristal del color del nivel (TF en relieve
   solo delante, con contorno para que se lea también en la de oro), gema tallada arriba con canales de luz; las de
   300 y 500 con gemas en el marco. De noche brilla el cristal. Imágenes de la web rehechas, con la cara del TF.
7. **Economía** (`PadEconomy`): el TF Pass y el Gachapón regalaban más que todo lo trabajado; las configs de antes se
   rebajan solas al cargar (monedas del pase al 15 %, del gacha al 20 %, objetos raros menos; copia `.antes-v2.json`).
   **Web**: piedras 10/15/20 $; efectos de muerte de pago a 2,99 $ (`vfx_owned`, el pad solo deja equipar los
   comprados); crates a 4,99 $ y las imponentes a 5,99/6,99 $, todas con recursos fáciles (filetes, manzanas doradas,
   diamantes, botellas de XP…); rangos con su piedra (100x100 los 3 primeros, 150x150 eterno y cósmico, 250x250
   celestial y fantástico) y recursos según el rango; cosméticos y skills con precio en Fantastic Coins (1.250 por
   dólar; clase de skills 7.500).
8. **El altavoz del pad** ya no dice que los demás no te oyen: las canciones de la carpeta se reparten a los cercanos.

### CHECKPOINT anterior (22 oct 2026, TF Client 1.3.113, PUBLICADA)

**1.3.113: seis quejas del dueño** (con 3 capturas; la 1.3.112 está PUBLICADA):
1. **Rayas blancas de la escena de las crates** (las veía antes de abrir, al moverse la cámara; «con capturas no las
   aprecias»): medido en la captura `crate1_preabrir` de la prueba de la 1.3.112, píxeles sueltos de (237,237,211) junto
   a los lados de la cascada del centro = el horizonte del cielo de `crate_sky`: eran **rendijas** entre caras (la
   rasterización deja líneas de 1-2 muestras sin pintar y por ellas se veía el cielo claro de fondo). Pasada nueva
   `crate_fill.fsh` (`ScenePost.finish`, antes del resplandor y de bajar la imagen): la muestra que está mucho más lejos
   que lo que tiene a ambos lados (a 1-3 muestras, en horizontal o vertical, por la profundidad) toma el color de esos
   lados (solo donde se ve el cielo: sin coste en lo demás). No bastó: la prueba `97207d7` (vista de antes de abrir sin
   supermuestreo, sin nitidez —`TEST_SHARP`— y sin tapar rendijas) las sacó en todas y solo no salían sin el shader de
   la escena («sin_post»). La causa: `crate_sky` pintaba el horizonte crema también por debajo del horizonte y las
   rendijas de las paredes miran ahí; el cielo de respaldo sin shader, debajo del horizonte, es la bruma oscura.
   **Arreglo**: en `crate_sky`, por debajo de ~6° de altura, la bruma (FOG) como sin shader (el cielo de verdad no se
   ve en ningún encuadre: `cielo_visible.py` ~0 %). **Comprobado** en la prueba `7d9dac6`: sin las líneas de las
   paredes ni las de las vigas ni a los lados de la cascada; los píxeles claros finos de la vista de antes de abrir,
   de 4079 a 2100 (sin el shader, 1960: lo que queda son flores, pétalos y destellos del agua).
2. **Piedras de protección rehechas** (`tools/gen_claim_blocks.py`; «no me gusta el diseño, sus contornos; no pongas
   el TF en todas las caras»): sin el marco biselado ni el panel ni los remaches; piedra oscura con grano y grietas,
   vetas de cristal del color del nivel que serpentean y brillan de noche, el **TF solo delante**, arriba un cristal en
   bruto del que salen las vetas, abajo solo piedra; las de pago, con pepitas de oro. Las imágenes de la tienda web
   (`tierras-fantasticas/tools/protecciones.py`), rehechas.
3. **Los bots en el pad**: Jugadores los enseña conectados (con su cara) y su ficha tiene oficio con nivel, misiones,
   cazas, fotos, likes, exploraciones y viajes (`TFBots.card`: siempre lo mismo para el mismo bot, sale de su nombre,
   un poco más cada vez que vuelve), sin clan; sin la pestaña REGALAR (un regalo a un bot se perdería).
4. **Oni, la «flecha»**: la prueba (`oni_lanzas_mano`) la sacó igual que en la captura del dueño: era un plano de 16x16
   con un rayo amarillo y verde animado (`animation_02`) al lado de la hoja; en la mano cada fotograma sale suelto, como
   una flecha flotando (al agrupar cubos no salía: el plano toca la hoja). Fuera de las 15 armas y del escudo de Oni,
   en el mod, en el visor de la web y en sus miniaturas (`tools/limpiar_piezas.py`, `QUITAR_TEXTURA`). Las piezas rojas
   junto a la empuñadura son del diseño (la guarda).
5. **TF Pass**: el premio del medallón a 1,5 (a 2 se salía del círculo y la etiqueta de la cantidad pisaba el aro y el
   camino), la cantidad centrada dentro, el candado en su chapa redonda y la placa del número 1 más alta y separada.
   **Protecciones**: ajuste nuevo en la zona (pad › Protecciones › la zona): «¿Cómo se ven los mensajes?» TÍTULO (el
   texto en medio de la pantalla, del tamaño del subtítulo de Minecraft) o BARRA (como siempre); sin «[Protección]»
   delante (`PlayerTracker.show`, `ClaimFlags.messageAsTitle`).
6. **Sonidos de las crates, propios** (`tools/crates/sfx_crates.py`, «horribles, no te estás tomando el tiempo»): la
   llave (aparece, vuela girando, golpea y entra, gira con trinquete y campana), las runas, el temblor que sube hasta
   el golpe de la apertura, la apertura (golpe grave, la tapa, metales y colchón que florecen, lluvia de destellos), el
   tic de la ruleta, la parada y **un premio por rareza** (común … mítica, cada uno más grande). Lo mecánico con
   grabaciones CC0 de Kenney (`tools/crates/muestras/`), lo mágico sintetizado, con sala y volumen medido. La crate del
   mundo usa sus versiones mono (`mundo_*`, `CrateSfx`); fuera el Warden y el valle de las almas de la clásica.
   `sonidos_crates.py` solo hace ya el ambiente y la música.

### CHECKPOINT anterior (22 oct 2026, TF Client 1.3.112, PUBLICADA)

**1.3.112: cinco cosas del dueño** (con 5 capturas):
1. **Sets limpios**: las espadas y lanzas left/right de Oni llevaban el cofre de la crate de Oni pegado a la empuñadura y
   el casco de Bahamut un bloque suelto. `tools/limpiar_piezas.py` (lista `LIMPIAR`): agrupa los cubos que se tocan y
   deja el grupo del cubo más largo, en el mod, en el visor de la web y en su miniatura. Revisados los 55 sets (hoja de
   miniaturas y grupos sueltos): el resto de piezas separadas son del diseño. Si se reimportan los packs, repasarlo.
2. **Crates**: las partículas propias (luz/destello/fogonazo) salían como cuadrados claros con un punto (también en la
   prueba de la 1.3.109): ahora van como las translúcidas del juego (`PARTICLE_SHEET_TRANSLUCENT`, texturas de alfa
   normal; fuera `crate_particle` y la mezcla premultiplicada). **Las líneas a los lados de la cascada de en medio**: el
   agua que cae se estrechaba 0,16 también junto a la piedra (sin cara de lado): una rendija abierta de arriba abajo por
   la que se veía la luz de detrás (línea crema (215,217,190) en la prueba). Ahora solo se estrecha donde hay aire
   (`SanctuaryScene.airAt`, igual en `santuario_render.py`). **Sonido**: el ambiente sin campanitas («rin»), 60 s de
   cascada, viento, gotas y pájaros en bucle sin costura (`sonidos_crates.py ambiente`); música al 0,70 y ambiente al
   0,45. **El volumen de la esquina** ahora mueve también la música y el ambiente del santuario
   (`CratePreOpenScreen.audioGain`, rueda ±10 %). **ABRIR** abre sin «¿Consumir 1 llave?». **Botones** de cristal
   teñidos del color de la rareza (`GlassButton`).
3. **Pad**: la estela no salía en la vista previa de Combate (con prueba de profundidad, a la altura del fondo): un poco
   por delante (`TrailPreview`, z +100). Decoración: rombos de oro en el borde de las ventanas, halo al pasar el ratón
   por las tarjetas, brillo en las pestañas y rombos en la elegida (`PadUi`).
4. **Bots**: `/tpa`, `/tpahere`, `/msg` (y alias), `/r` y `/tpacancel` a un bot los contesta `server/BotCommands` con los
   mensajes de EssentialsX (idioma y colores de su `config.yml`); la petición se deniega a los 5 s y los mensajes no se
   responden. Entra por `mixin/BotCommandsMixin` (`performChatCommand`, tras validar la firma del chat) y por el
   `CommandEvent` de Forge. Los bots salen en el TAB como un usuario sin rango (sin equipo ni prefijo), igual que los de
   verdad con el rango por defecto.
5. **Web**: sección **Protecciones** en la tienda (`tools/crates.py` `PROTECTIONS`: 250×250 4,99, 300×300 7,99 y 500×500
   12,99 USD, precios puestos por la IA; se entregan con `give {player} tfclient:proteccion_<tamaño> 1`), imágenes de
   `tools/protecciones.py`, pestaña azul (`tab_art.py`, seis en una fila desde 1000 px).

### CHECKPOINT anterior (22 oct 2026, TF Client 1.3.111)

**1.3.111: los bots entran al azar y con habituales** (el dueño: «al poner uno o varios nunca debe ser el mismo que
puse, no debe ser lineal unirlos, debe ser al azar, y a veces que toque uno de los que ya salieron anteriormente, para
que se note que son jugadores ocasionales o activos»). `TFBots`:
- `pick`: uno de cada tres más o menos (`RETURNING` = 0,35) es uno que ya estuvo y salió hace más de 5 min
  (`BACK_AFTER_MS`), con más probabilidad cuantas más veces ha venido; si no, uno que no ha venido nunca.
- `jitter`: entre uno y otro (al entrar y al salir), una espera al azar de 0,35 a 1,65 veces los segundos pedidos.
- `remove all` los saca en orden al azar.
- Los vistos (`veces`, `salio`) se guardan en `config/tfclient-bots.json` junto a los de dentro.
- La prueba del pad mete una segunda tanda y apunta cuántos repite de la primera (`BossTest`, ticks 664 y 670).

### CHECKPOINT anterior (22 oct 2026, TF Client 1.3.110, PUBLICADA)

Prueba en el juego (2e1ccc51, `[prueba-jefes=pad]`): `/tf bots add 4` → en el TAB «_Brisa_», «chinoXD29», «Fakozun»
y «FedeeUwu» con su cara (y el jugador «Dev»); `remove all` los quita. Para volver a generar los nombres:
`MOJANG_CACHE=<archivo.json> python3 -I tools/bots/nombres_bots.py` (la caché evita volver a preguntar a Mojang).

**1.3.110: los nombres de los bots** (el dueño, con una captura del TAB: «los nombres son iguales, genera nombres únicos
sin patrones idénticos» y «¿por qué siempre usas un apellido tipo Balón Rojo, Oso Marino? Investiga cómo usan los
nombres de Minecraft las personas reales»). Los de la 1.3.109 eran casi todos «Palabra_Palabra» en español.
- **Investigación**: 190 222 nombres reales de cuentas Java (muestra repartida por todo el dataset
  huggingface.co/datasets/mudkipdev/minecraft-usernames, 51,5 millones): largo medio 9,5; 37 % acaban en cifras (2 o 4
  sobre todo); solo 14 % llevan «_»; 32 % minúsculas, 29 % Mayúscula inicial, 28 % CamelCase, 5 % MAYÚSCULAS; «xX…Xx»
  0,1 % y «xNombre» 0,7 %. Los hispanos (6 131), igual: apodo + cifras («Mati39», «juancu17»), apodo + iniciales
  («sebastianrv», «NicoPL»), letras repetidas («Tomiinoo»), «xd», Its/The/Soy + apodo, nombre + apellido
  («ivangallego»), apodos inventados («Iynox»).
- **`tools/bots/nombres_bots.py`** los genera con esas plantillas y pesos (semilla fija) y con reglas: nada de
  Adjetivo + Sustantivo; ningún molde más de 4 veces (los de una palabra, 7); como mucho 45 % con cifras; cada plantilla
  hasta su parte medida (las raras, una vez); ningún apodo ni raíz de 5 letras repetidos; cada coletilla (xD, Rex,
  god…) una vez y The/Its/Soy dos; chica o chico según la skin (CHICAS, mirado en la hoja de skins); y libres en la API
  de Mojang.
- **`TFBots`**: los bots guardados con un nombre viejo (en `config/tfclient-bots.json`) vuelven con otro nombre de la
  lista, los mismos que había.

### CHECKPOINT anterior (22 oct 2026, TF Client 1.3.109, PUBLICADA)

**Prueba en el juego** (43bcf13c, `[prueba-jefes=pad,crates,npcs] [shaders=photon]`, informe en `pruebas-jefes`): el NPC
dentro de una zona con «arden los monstruos» sigue vivo y sin arder a los 5 s; `/tf bots add 4 1` → 4 en el servidor y
el TAB con 5 (4 con su cara y el jugador), mensajes amarillos de entrar y salir, y `remove all` los quita; las dos
aperturas y las dos cinemáticas, bien (efectos propios dibujándose; ~250 ms por fotograma en la máquina de la prueba);
«La zona» se lee entera (la prueba sin el arreglo también la ve bien: el fallo depende del juego del dueño, así que
falta que él lo confirme); la Valentine apunta bien en Combate; las piedras de protección, oscuras con las TF de color;
y junto a la cascada alta ya no se ven las rayitas. La sesión que la hizo se cayó tras la prueba; la publicó otra (la
de la web) sin tocar el código.

**1.3.109: siete cosas del dueño** (con 4 capturas suyas):
1. **Las rayas a los lados de la cascada del medio**: las enredaderas del costado del pilar que da a la cascada alta se
   ven casi de canto y su textura aplastada salía en rayitas (la prueba las veía igual sin agua, sin efectos y sin
   plantas al viento). `SanctuaryScene.draw`: lo recortado con mipmaps (como las hojas) y filtrado anisotrópico (hasta
   16x; el registro dice cuánto) en la piedra y las enredaderas de la escena. CRATES.md §2.
2. **El sonido y la música de las crates**: el «sonido horrendo» era el de la crate del mundo (y de la apertura
   clásica): el estampido y la carga del Warden, truenos, el Wither y los lamentos del valle de las almas. `CrateSfx`,
   nueva paleta (encantamiento, brújula imantada, faro, conducto, cofre, subir de nivel, desafío completado, fuegos), y
   la crate del mundo la usa también. Los efectos de la escena, sonidos de Minecraft (`sounds.json`, eventos) en vez
   de los sintetizados. **La música del dueño (Dalarna) va dentro del mod, cifrada** (`tools/crates/cifrar_musica.py`,
   `crates/client/media/SealedAudio`, `mixin/SoundBufferLibraryMixin`: AES-128/CTR, la clave va en el mod: frena al que
   abra el jar, no al que se empeñe). **Los efectos de la crate** (no la escena): partículas propias
   (`CrateParticles` → `client/fx/CrateFxParticle`: luz suave, destello de 4 puntas y fogonazo, sumando luz, del color
   de la rareza; texturas de `tools/crates/particulas_crates.py`) en vez de polvo, encantamiento, varas del End, fuegos y
   explosiones; capas de serie nuevas (`ParticleLayer.defaults`; las de antes sin tocar pasan solas: `legacyDefaults`).
3. **Texto borrado en el pad** (Protecciones: el título «La zona» sin texto salvo la última letra): el brillo redondo
   del icono de la fila siguiente sobresalía de su tarjeta y, como algo del juego del dueño pinta el texto después,
   lo tapaba con su profundidad aunque fuera transparente. Ahora lo translúcido no escribe profundidad
   (`PadRenderTypes.SOFT` para recuadros y degradados con alfa, `depthMask(false)` en texturas con alfa) y el brillo no
   sale de la tarjeta. La prueba lo imita con `TFPadScreen.testTextLast` (todo el texto al final).
4. **Los NPC ardían en las protecciones**: «¿Arden los monstruos?» (de serie, sí) empujaba, quemaba y hería a todo lo
   Enemy, y los NPC (BossEntity) lo son por dentro. `claims/util/ClaimMobs.hostile` (ni NPC ni fauna) en todas las
   reglas de monstruos de las protecciones; y los NPC ni arden al sol ni con fuego (`BossEntity.fireImmune`).
5. **Las piedras de protección, oscuras**: `tools/gen_claim_blocks.py`: piedra negra pulida con remaches (las de pago,
   marco de oro viejo), panel casi negro con un filo de luz y las letras TF grabadas del color de su tamaño.
6. **La espada Valentine al revés en la vista previa de Combate** (y sin estela): muchas armas 3D, en su posición
   «gui», apuntan arriba a la izquierda (−118°…−137°) y no a −45° como las de Minecraft. `TrailPreview` las gira según
   hacia dónde apunta su hoja y las pone del largo de una espada normal.
7. **Bots** (`/tf bots add <cantidad> [segundos]` —desde la 1.3.120 `/tf bots add cantidad <n> [segundos]` o
   `add nombre <nombre>`—, `/tf bots remove <cantidad|all> [segundos]`, `/tf bots list`,
   staff): `server/TFBots`: jugadores de mentira que solo están en la lista de jugadores (el TAB), con su cara (68 skins
   firmadas de MineSkin: `tools/bots/skins_bots.py`), 68 nombres inventados que no usa ninguna cuenta
   (`tools/bots/nombres_bots.py`, comprobados en la API de Mojang), su ping y el mensaje amarillo de entrar y salir; sin
   entidad ni conexión. Entran de uno en uno cada N segundos (el primero, ya; sin segundos, cada 3). Se guardan en
   `config/tfclient-bots.json` y vuelven al reiniciar. La lista de servidores los cuenta (`MinecraftServerBotsMixin`).

### CHECKPOINT anterior (22 oct 2026, TF Client 1.3.108, PUBLICADA)

**1.3.108: el pad** (el dueño, al ver la 1.3.107: «déjalo sin fondo, sin el logo ese que pusiste, que quede limpio; y los
iconos, ¿no ves que están muy abajo?»):
1. **Cristal limpio**: fuera el emblema «TF» en un aro (`TFPadScreen.emblem`, `fondo_tf.png`, `tools/pad/fondo_tf.py`):
   solo el degradado del tema, la trama, las luces y la sombra de abajo. Ni monogramas, ni logo, ni aldeas.
2. **Las fichas de la portada, centradas**: medido en la captura de la 1.3.107 (800×450), 23 px de aire entre la barra y
   la primera fila y 3 px entre los nombres de la última y el marco. Era `PadHomePage.layout`: para que la primera fila
   no pisara el logo del castillo (que baja hasta la esquina del cristal) bajaba la rejilla entera, y además con
   `logoRight`, que llega a la fila 86 del pad cuando el dibujo acaba en la 82 (medido en frame.png). Ahora la portada usa
   `TFPadScreen.logoInk` (el borde exacto, fila a fila; `logoRight` sigue para las ventanas de las apps), la primera
   fila empieza justo debajo del logo y, si hace falta, las filas se juntan (hasta `MIN_GAP`) para que debajo quede el
   mismo aire que encima. Medido en la portada de la prueba (800×450): 1.3.107, 23 px arriba y 4 abajo; 8a0dbff0 (aún
   con `logoRight`), 24 y 10; f5fdf206, **16 y 13**, y la ficha de Música justo debajo de la hierba del logo, sin pisarla.
   `[prueba-jefes=pad] [shaders=photon]`.

### CHECKPOINT anterior (22 oct 2026, TF Client 1.3.107, PUBLICADA)

**1.3.107: cuatro quejas del dueño** (juega con Oculus 1.8 + Embeddium + ImmediatelyFast + EMF/ETF + EntityCulling):
1. **«Tus rayas aún se ven»**: eran la columna del haz de sol con el borde marcado y sus 9 vetas finas (líneas claras
   junto a las columnas de piedra), los rayos del sol en pantalla de `crate_post` (cada luciérnaga u hada dejaba una
   ristra de rayitas hacia el sol) y los costados de las cascadas vistos de canto. Ahora: borde suave y sin vetas;
   rayos solo desde el hueco del sol y con el punto de partida distinto en cada píxel; costados que se apagan de canto.
   Y lo que quedaba (la prueba con dos fotogramas seguidos, `crate1_preabrir` y `_otra`: rayitas de 1 px que cambian de
   uno a otro en la pared de enredaderas junto a la cascada alta, justo donde las recortó el dueño): lo más fino que un
   píxel caía cada fotograma en un píxel distinto. **Supermuestreo**: la escena se dibuja al doble de ancho y de alto y
   se baja al final (`ScenePost.ssaa`, hasta 2560×1440; si de 60 fotogramas más de la mitad pasan de 40 ms, vuelve a 1).
   Se mide con `parpadeo_fino.py` (lo que parpadea entre las dos) y `verticales.py`. CRATES.md §2.
2. **La música**: la que mandó (Dalarna, 3 min, en bucle) con el ambiente de la fuente debajo, desde la pantalla de
   antes de abrir hasta el final sin cortarse (`CrateCinematicScreen.loops`). Va en un paquete de recursos aparte
   (`TF-Santuario-musica.zip`, `tfclient:sounds/crates/musica.ogg`): el repo y las Releases son públicos. CRATES.md §2.
3. **«Los NPC no tienen sus modelos (silla…)» en su servidor**: los muebles se dibujaban solo por el camino de la
   entidad y con la clave que calcula el cliente; ahora con el modelo (`Actor.renderModel`, por cualquiera de sus
   caminos) y con la que manda el servidor (`BossEntity.sceneKey`); el registro dice «TF NPC: <clave> con N mueble(s)».
   El camarero, detrás de su barra (y la barra en el panel). NPCS.md §3.
4. **El pad**: sin los monogramas «TF» y sin el paisaje de aldeas de abajo (`TFPadScreen.landscape`, fuera; las
   texturas `land_*` siguen para el mapa del TF Pass); detrás de las apps, un emblema dibujado a píxeles: las letras «TF»
   dentro de un aro, en azules oscuros (el dueño: «no copia y pega el logo»; `tools/pad/fondo_tf.py`, `fondo_tf.png`).

La prueba: `[prueba-jefes=pad,crates,npcs] [shaders=photon]` en 299cd5d7 (muebles, panel, pad) y
`[prueba-jefes=pad,crates] [shaders=photon]` en 29f241d4 (la vista sin cada cosa) y 352a4319 (supermuestreo: «escena a
2x»; en la zona del recorte del dueño, las rayitas que parpadean entre dos fotogramas, de 54 a 33, y en las paredes de
87 a 33 y de 18 a 5; lo que queda son las hadas, las luciérnagas y el brillo de los faroles, que se mueven; el pad sin
aldeas, con el aro del tono del logo de antes, ≈ (104, 154, 179)). Los fotogramas de la prueba, 216 ms (eran 150: la
tarjeta de la máquina es de mentira).

### CHECKPOINT anterior (21 oct 2026, TF Client 1.3.106, PUBLICADA)

**1.3.106: cinco quejas del dueño** (con cinco capturas suyas; juega con **Oculus + Photon**):
1. **Crates sin ningún efecto** (ni los de serie): con Oculus/Embeddium instalados (aun sin paquete) no salía nada de lo
   que la escena dibujaba por el lote del juego (halos, hadas, luciérnagas, pétalos, anillos, haz, runas, premios). Lo
   reproduce la prueba con `[shaders=photon]` (en su máquina Photon no arranca, pero Oculus y Embeddium bastan). Ahora
   `SceneFx` los dibuja como el agua (VertexBuffer y shaders propios `crate_fx_*`); con un paquete puesto el mundo se
   sigue dibujando detrás de la cinemática (Oculus se quedaba a medias); las partículas de la caja, con esos mods, no
   van en la escena (salían cuadrados blancos). CRATES.md §2.
2. **Rayas blancas al mover la cámara**: bloques 0,2 % más grandes (las hojas, recortadas como antes: macizas o con una
   copia oscura dentro salían con manchas blancas con Oculus/Embeddium), el brillo del estanque y los hilos de las cascadas que se apagan donde son más
   finos que un píxel y FXAA en `crate_post`. **La música**: «Aerie» de Minecraft (la sintetizada fuera). CRATES.md §2.
3. **El pad sin lo que escribe y sin algunas letras**: algo de su juego pinta los recuadros después del texto (la
   prueba lo imita: `TFPadScreen.testBoxesLast`; sin el arreglo, la casilla de Música sin texto y solo el cursor, como
   su captura). Ahora cada cosa que pinta el pad va 0,005 más adelante que la anterior (`TFPadScreen.nextOrder`, en
   `GuiGraphicsMixin`, `PadSkin.mat` y `PadFont.front`): la profundidad respeta el orden aunque se pinten en otro.
   (Subir solo el texto 1, como se probó primero, sacaba la marca de agua «TF» del fondo por encima de los paneles.)
4. **NPC sentados en algo que no está**: `tools/npcs/muebles.py`: los muebles de Nexo de la taberna (Chair, Table, Mug)
   y cinco hechos con su madera en el panel (Scenes Tavern); y a 13 NPC de Townsfolks Vol 3 su silla, sofá, taburete
   alto, banco, escritorio o la mesa de la taberna, a su medida (`npcs/props.json`, `SkillClient.renderProps`). NPCS.md §3.
5. **Panel de NPC**: «El más cercano» ya no tapa «Cerca de ti:».

La prueba: `[prueba-jefes=pad,crates,npcs] [shaders=photon]` (dos rondas: 4ee9e243 y 7bd7ba88).

### CHECKPOINT anterior (21 oct 2026, TF Client 1.3.105, PUBLICADA)

**1.3.105** (el dueño: «un poco menos de partículas, y el resplandor del sol tapa un poco los items»): las partículas de
la crate en la escena, el 20 % (era el 35 %); los rayos y el foco del sol, solo detrás de la crate, y el florecer, algo
menos a su distancia (CRATES.md §2).

### CHECKPOINT anterior (21 oct 2026, TF Client 1.3.104, PUBLICADA)

**1.3.104: las partículas de la crate, en la escena** (el dueño: «lo de efectos son las crates, por algún motivo la
escena no me los mostraba»; no tiene que ver con los shaders). Las capas de partículas del editor de cada crate salen
en el mundo, junto al bloque, y el mundo no se dibuja durante la escena: no se veían. Ahora se dibujan dentro de la
escena (CRATES.md §2), más suaves que en el mundo (lo eligió el dueño: el 35 % y sin tapar la crate ni el premio). La
cinemática dice además en el registro si hay paquete de shaders y si su shader cargó.

### CHECKPOINT anterior (21 oct 2026, TF Client 1.3.103, PUBLICADA)

**1.3.103: cinco quejas del dueño** (con tres capturas suyas: el Pad en Música, las pestañas de Efectos y un farol de
la crate):
1. **«El pescador al recoger el anzuelo lo hace por detrás suyo»**: la caña del pack espera a 112° y recoge a 220-233°
   (por detrás de la cabeza; la errata de signo de HUECOS) y en «interact» va al hombro con el pez a la espalda.
   `tools/npcs/sedal.py` la pone delante (`rod`) y, si la punta aún queda por detrás, tensa el sedal hacia el agua de
   delante. Medido: el anzuelo, siempre por delante (de −27/−28/−44 px a +11/+15/+1). NPCS.md §3.
2. **«La cara la tienen atrás»**: las pieles del panel para el 2.º personaje de los dúos le ponían las piezas del
   caminante, que miran al otro lado: cara y pecho en la espalda en los 210 dúos con piel (medido). Ahora,
   `<piel>_2` (la piel reflejada, como las plantillas del pack). NPCS.md §3.
3. **«El pad perdió sus letras»**: no se borró nada (el código del pad no cambió desde la 1.3.73) y en la prueba se
   ven. A él le faltan solo las de PadFont encima de un recuadro (pestañas, botones): como si algo (un mod de
   optimización) dibujara los recuadros después de las letras. `PadFont` las dibuja ahora 1 por delante (escriben la
   profundidad: el recuadro ya no las tapa). Lo de «la GUI de Efectos solapada» era eso (pestañas sin nombre).
4. **Crates**: las «líneas blancas» de su recorte eran la colita de las hadas (18 chispas sueltas casi blancas): ahora
   una estela continua de su color; los pétalos de la apertura, rosas y con forma de pétalo; el sonido, medido, mucho
   más bajo y por «Bloques». CRATES.md §2.
5. La prueba en el juego guarda enteras (PNG) las capturas de antes de abrir y de la apertura de la crate 1, y saca
   un dúo con piel desde los dos lados (`npc_duo_a`, `npc_duo_b`).

### CHECKPOINT anterior (21 oct 2026, TF Client 1.3.102)

**1.3.102: el sedal de los pescadores cuelga por su peso** (orden del dueño: «arregla ese porque quiero poner
pescadores»). En el pack el sedal sube al cielo (hasta 10-13 bloques en «fishing» y «sitting»; el pez en el aire en
«boat» e «interact»; es del pack, ModelEngine lo enseña igual). `tools/npcs/sedal.py` (lo pasa `build_npcs` antes de
pegar) lo gira en cada fotograma para que cuelgue recto desde la punta de la caña, con el anzuelo y el pez debajo, en
el agua. La caña y el largo del sedal, los del pack. Todo en `tf-client/NPCS.md` §3. Lo que queda: `NPCS-CONTINUAR.md`.

### CHECKPOINT anterior (21 oct 2026, TF Client 1.3.101, PUBLICADA)

> **Publicada** como Release `tfclient-v1.3.101`. Lo que queda (restos de la plantilla del pack, el sedal del pescador)
> está en **[`tf-client/NPCS-CONTINUAR.md`](tf-client/NPCS-CONTINUAR.md)**. Los packs de NPC no están en el repo.

**Lo último (lo que pidió el dueño al ver las pruebas)**, todo en `tf-client/NPCS.md` §3:
- **«Ninguna extremidad despegada, por más pequeñito que sea el hueco»**: las animaciones del propio pack despegan la
  cabeza, los brazos o las piernas (53 de los 130 NPC en su escena, hasta 3,2 px; lo mismo al milésimo con las reglas de
  Blockbench sobre el `.bbmodel` original). `tools/npcs/pegar.py` (lo pasa `build_npcs`) las pega en cada animación
  (solo la posición; los giros son los del pack) y `tools/npcs/pegado.py` lo mide: con la piel del panel, **0 huecos en
  1 456 420 poses** (rejilla de 60/s y 3 semillas al azar). Con la «Plantilla del pack» quedan restos de 0,003-0,35 px
  en ventanas de 4-5 ms (NPCS-CONTINUAR.md).
- **Motor como ModelEngine 4**: una animación suena una sola vez (la de reposo y un state{} del mismo nombre no se suman:
  57 NPC salían con cada giro al doble, el «angry 1» con el torso partido); la de reposo o la de andar puesta por state{}
  se para al dejar de tocar (el vendedor andaba con la de reposo sumada); changepart{m} solo en el mob que lleva ese
  modelo (el dúo de la sierra volvía a la plantilla amarilla); los NPC que vuelven del disco con su chunk lanzan su
  ~onLoad (`SkillMobs.reborn`/`sweep`).
- Caminantes por su ruta (@TargetedTarget), panel con rueda y textos que no asoman cortados, prueba en el juego con
  filas a 20 bloques, capturas sin pisarse y el vendedor y el caminante andando de cerca.

**1.3.101: los NPC de escenas** (todo en **`tf-client/NPCS.md`**). El dueño mandó 13 packs de Scenes (MythicMobs +
ModelEngine) y pidió convertirlos todos «correctamente, con sus animaciones», un panel de control para colocarlos
(posición en tiempo real, rotación…) y `/tf npc` que lo dé solo a quien lo ejecuta; luego: «ya puedes meterlos».
- Conversor `tools/npcs/build_npcs.py` (con el de los jefes): 13 packs, 130 NPC + 4 marcadores; nombres y cosméticos de
  los menús del pack; **auditor 0 problemas** en los 13 (y `verificar.py` «todo bien», que ahora también los audita).
- Motor fiel a MythicMobs/ModelEngine: variables anidadas y con «|por defecto», globales con save=true guardadas con el
  mundo (`skills/SkillSaved`), `@pin`/`distanceFromPin`, `followPath`, `teleportTo` a los objetivos, `light` (brillo
  del hueso), `meleeHit`, ~onLoad de los NPC que vuelven del disco, changepart/remapmodel sin modelo no hacen nada.
- Entidad `tfclient:npc` (BossEntity en modo NPC: se guarda con el mundo); panel `npcs/` (`TFNpcs`, `NpcNet`,
  `NpcCatalog`, `NpcPanelItem`, `client/NpcPanelScreen`).
- Prueba en el juego: `[prueba-jefes=npcs]` (`BossTest.npcsStep`).
- Los packs no están en el repo (los zips los tiene el dueño); para reconvertir, NPCS.md §3.

### CHECKPOINT anterior (21 oct 2026, TF Client 1.3.100)

**1.3.100: TF Crates, lo que pidió el dueño al ver la 1.3.99** (todo en `tf-client/CRATES.md` §2):
- el santuario **siempre en vivo**: también detrás de la pantalla de antes de abrir y de las recompensas (antes, negro);
- botones **pequeños de cristal, abajo y sin sonido** («ABRIR», «RECOMPENSAS»; nada de amatista en ningún sitio);
- **recompensas rehechas**: cuadrícula de premios por rareza en un panel de cristal sobre el santuario;
- **la llave se lanza y golpea el cofre** (destello, chispas, el cofre brinca, tiembla y se abre);
- sin rayas blancas (pétalos y partículas siempre de cara a la cámara, claridad sin bordes), sin la línea negra de las
  cascadas, escena más oscura y con más contraste, faroles con resplandor, turquesa del agua en lo cercano, haz de sol
  que se nota, hadas como puntitos con colita.

### CHECKPOINT anterior (21 oct 2026, TF Client 1.3.99, PUBLICADA)

> **Publicada** como Release `tfclient-v1.3.99` (el dueño pidió el jar). La cinemática de las crates, medida contra su
> imagen (`tf-client/docs/crates/imagen-del-dueno.webp`) con `tools/crates/comparar.py`, en el juego (prueba `7e24e2c`):
> saturación 0,652 (foto 0,621), luz media 0,347 (0,318), luz alta 0,903 (0,832), contraste 0,260 (0,231), contraste
> local 0,096 (0,080), nitidez 0,093 (0,093). Agua limpia y continua (el dueño **odia** las texturas quebradas,
> resquebrajadas o cuadriculadas: nada de celdas, escamas, rejillas ni líneas en el agua), espuma y bruma en el pie de
> cada cascada (sin corte), premio limpio, mensaje sobrio. Todo en `tf-client/CRATES.md` §2 y
> `tf-client/CRATES-CONTINUAR.md`.

**1.3.99: TF Crates** (guía completa en **`tf-client/CRATES.md`**). Fantastic Crates dentro de TF Client (`/tf crates`;
hay que **quitar `Fantastic Crates 8.jar`** del servidor y de los clientes) y la cinemática de apertura: **el Santuario
de las Hadas**, rehecho **como la imagen que mandó el dueño** («quiero exactamente esto, colores, iluminación,
shaders»): templo en ruinas de piedra con musgo cubierto de hiedra, vigas de roble oscuro con flores de esporas y
faroles con cadena, sol blanco cálido con rayos desde el hueco, cascada alta y escalera de agua al fondo, dos cascadas
de los lados (por debajo de un dintel), estanque turquesa claro con el fondo de baldosas y la red de líneas de luz, el
altar con dos amatistas y la crate. El dueño se quejó varias veces («no veo idéntica a mi foto», «el color para nada
coincide», «quiero precisión en los colores», «no veo mejoría en saturación, contraste»): **se compara midiendo**, no a
ojo: por zonas (agua, hojas, piedra, faroles, rosa, sombras) y saturación, contraste local y nitidez, entre su imagen y
`tools/crates/santuario_render.py` en el tick 0 (CRATES.md §2 tiene los números al dejarlo). Arreglos de fondo: en el
juego todo salía **borroso** (se borraba la profundidad de la escena antes del shader); la **cuadrícula** era de la
vista previa (moderngl no tiene `ctx.depth_mask`); la viñeta, la curva de tono y los rayos aplastaban los brillos.
- **La canción que mandó el dueño** («La petite fille de la mer») va en un resource pack aparte
  (`TF-Santuario-musica.zip`, se le dio hecho; sustituye `tfclient:crates.musica`); no se sube al repo ni a las
  Releases, que son públicos.
- Prueba en el juego: `[prueba-jefes=crates]` (dos aperturas, 11 capturas cada una; ~4 min). El dueño pide **una sola
  prueba** por cambio; antes, la vista previa.
- Pendiente del dueño: que lo vea en su juego; los cascos de los otros 7 sets de crates; modelos o skins de caballeros;
  el PR en borrador #137.

### CHECKPOINT anterior (20 oct 2026, TF Client 1.3.98)

**1.3.98** (`tf-client/JEFES.md` §3.23, con la tabla queja → causa → arreglo). Lo que pidió el dueño tras la 1.3.97:
- **Caballeros sacros** en el suelo, cada uno con su postura (guardia, otear, leer, cuidar flores, alzar el farol), su
  color (plata, azur, ónice, jade, carmesí) y su objeto (escudo, lanza, libro, brote, farol); solo Caelthar flota.
- **El soporte de la armadura** ya no salta (no tenía «NoGravity» de verdad).
- **La grieta del Void**: tras cada jefe se apaga hasta que el staff la enciende (`/tf void grieta encender` o el
  siguiente en `/tf boss → VOID`), y entonces se reabre la grieta del cielo.
- **La isla de la grieta**: sin la «iglesia», con colinas y decorada (`tools/void/isla_grieta.py`), un Ancla a 56 bloques
  de la grieta; nada al aire (`isla_alba.limpiar`), la Llegada y el portal rellenos por debajo, escalones al final del
  Puente de Luz. `IslandUpdate.VERSION` 6.
- **La fauna** sin el cerebro de los jefes: va derecha a por ti (antes se plantaba a 4-7 bloques y giraba).
- **El agujero de gusano** sin imágenes de baja calidad (paredes hechas para cada dimensión, el Void en nebulosa, al
  final la luz del Alba) y con sonido nuevo; menos ondulación y sin temblor al salir y al llegar.
- **Música**: «Nana del Alba» en el santuario (más baja: 0,3), el Void a 0,45 y la batalla a 0,5; salir del Velo, con
  celesta. Diálogos a 120 letras por segundo.
- Las pruebas en el juego, cortas (el dueño: «no más de 10 minutos»): `[prueba-jefes=corta]`.
- Pendiente del dueño: los cascos de los otros 7 sets de crates; modelos o skins de caballeros; el PR en borrador #137.

### CHECKPOINT anterior (20 oct 2026, TF Client 1.3.97)

**1.3.97** (`tf-client/JEFES.md` §3.22, con la tabla queja → causa → arreglo). El dueño, tras la 1.3.96: *«el agujero de
gusano no parece que es uno... el sonido de la grieta no es épico, no suena el quebranto del cielo; sin sonidos
secuenciados, explosiones ni cristales; la distorsión es demasiada, hasta me bugea la cámara; las ondas no me gustan...
explica qué es el santuario, no es congruente esa zona bonita en una dimensión de caos; nada de bloques del End, lana ni
tierra; biomas muchísimo más grandes; solo el mob grande que grita y el mago, pocos, sin bossbar ni botín; el portal
donde el puente del bioma toca la isla principal, donde era el santuario antes; la armadura a ras de suelo con un
efecto, netherita +1 y una sola vez; pozos sin sapoluz, una burbuja, pocos, mejor sonido; la textura de la corrosión
suave y tres cuartos de corazón; otra melodía en el santuario; los fragmentos son lo que queda tras tragárselo todo el
agujero, los sostienen los caballeros sacros; caballeros sacros con vida y diálogos»*:
- **Todo el Void en roca del Vacío** (`tools/void/paleta.py`: la isla del dueño pasa de lana, terracota, piedra del End
  y tierra a pizarra, toba, basalto, piedra negra, calcita, obsidiana y amatista; `isla_alba.py` falla si queda algo).
- **Los Fragmentos del Alba Rota** (`tools/void/bioma.py`, ~600 × 460, cuatro veces el Páramo): Llanos de Ceniza,
  Bosque de Cristal, Fauces de Obsidiana, Ruinas de la Orden y la Herida, con el Sendero del Alba.
- **La Llegada**: el Umbral (santuario viejo) fuera; donde el Puente de Piedra toca la isla de la grieta, una plaza con
  el **Portal del Alba** al lado.
- **Fauna**: solo el **Desgarrador** (Endermauler, el que grita y lanza) y el **Vigía** (Ender Watchman, el «mago» de
  los Espectros); como mucho 2 por jugador, cada 10 s; sin barra y sin botín.
- **La armadura del Vacío**: de pie en el suelo del altar con aura; el yelmo con armadura (netherita +1, como los sets de
  las crates); una sola vez por jugador.
- **6 Anclas del Alba** (pozos) en burbuja, sin bloque; sonidos nuevos (`tools/void/instrumentos.py`); la corrosión con
  textura suave (2048, `blur`) y **tres cuartos de corazón** (`HURT` 1,5); **música propia del santuario**
  (`tools/void/musica_santuario.py`).
- **El lore** (los caballeros y Caelthar): el ojo del Vacío se tragó la dimensión; los Fragmentos son lo que queda,
  sostenidos por las Anclas (caballeros hechos luz); el santuario es el último jardín vivo.
- **Cinco caballeros sacros** (`SacredKnight(s)`, `tools/void/caballeros.py`: el modelo de Caelthar en plata, acero,
  bronce, rosa y marfil): pasean, saludan y hablan.
- **La grieta del cielo**: sonido nuevo (el cielo cruje, Shepard, braam, coro; `tools/void/sonidos_cielo.py`), sin
  ondas, distorsión suave y temblor lento (≤ 0,4°).
- **El agujero de gusano rehecho**: túnel en perspectiva que serpentea, paredes de cada dimensión y bocas esféricas
  con la dimensión siguiente; el agujero negro del Void al final de la ida.
- `IslandUpdate.VERSION` 5 (en el servidor del dueño la isla se pone al día sola: ~2300 trozos, unos 4 minutos).
- Pendiente del dueño: si los otros 7 sets de crates sin casco de armadura deben tenerlo (JEFES §11); si manda modelos o
  skins de caballeros; y el PR en borrador #137.

### CHECKPOINT anterior (19 oct 2026, TF Client 1.3.96)

**1.3.96** (`tf-client/JEFES.md` §3.21, con la tabla queja → causa → arreglo y el lore entero). El dueño, con una
captura con Photon: *«se ve horrible con shaders... no pongas sonidos tipo explosiones secuenciales, quiero un sonido
fino corrido... hazla un poquito más grandecita y más épica... la distorsión casi no se nota... que pueda configurar los
jefes del Void y su secuencia... el agujero de gusano, que muestre la dimensión de verdad... quiero lore... que el Void
robe tu luz, fragmentos de luz, una isla santuario con el caballero en una burbuja brillante... inventa algo chingón»*:
- **Con Photon, sin barras blancas** (`AfterShaders.syncBlend`: Photon deja mezclas por búfer que la caché de Minecraft
  no ve). La prueba en el juego puede usar Photon: `[shaders=photon]`.
- **Grieta del cielo más grande y épica** (desgarro ~80°, tormenta que gira y se acelera, onda de choque al romperse,
  anillo que se cierra al sellarse, distorsión más fuerte) y **sonido corrido**: una pista sintetizada por momento,
  sin golpes (`tools/void/sonidos_cielo.py`).
- **Grieta del Void**: secuencia de jefes (en orden o al azar, ▲▼, siguiente, al acabar: repetir / parar / el final) y
  atributos de uno o de todos (✎ en `/tf boss`).
- **Agujero de gusano con las dimensiones de verdad** (panoramas capturados en el juego; el Overworld, el del menú) y
  el final cayendo al agujero negro del Void; 11,5 s.
- **Segunda ronda** (el dueño vio la primera, con un medidor de luz y fragmentos que se recogían: *«no quiero un
  contador en pantalla, más bien un efecto de corrosión... que parándome en un sitio de luz me cure, como el bloque de
  magma cuando te ahogas... un set que obtendrán todos al derrotar 5 bosses para ser inmunes al Void, en el santuario...
  haz más grande esa isla, bonita, bien decorada... los mobs del End (sin el que se monta), anímalos bien... un bioma
  completo entre el santuario y la isla principal con estas criaturas, pozos de luz... y un portalito para volver»*):
  - **La corrosión del Vacío** (`VoidLight`): sin números; grietas violetas que brillan y entran desde los bordes
    (el centro siempre limpio), de nada a todo en 4 min fuera de la luz; al máximo, **medio corazón cada 3 s**.
  - **Los Pozos de Luz** (`LightWells`, 26: 16 en el Páramo y 10 en la isla de la grieta) y el Velo del Alba curan en
    3 s (resplandor dorado). No se recogen. La primera vez en cada pozo, un Eco del Alba en el chat.
  - **El Páramo del Alba Rota** (`tools/void/bioma.py`): ~240 × 230 entre la isla de la grieta y el santuario: Bosque
    de Coros, Campos de Amatista, Páramo de Obsidiana, Ruinas de la Orden, la Herida (atraviesa la isla), grietas,
    Sendero de las Esquirlas con farolas de alma, islotes; con **las criaturas del End** (EndMobsVol1 sin el End
    Strider, `criaturas_vacio`: 12 mobs como «fauna» del motor de jefes, con sus reglas de aparición de MythicMobs;
    `VoidFauna`).
  - **El santuario** (`tools/void/isla_alba.py`) al norte del Páramo, el doble de grande y decorado (templo de doce
    columnas, Altar del Vacío, jardines con cerezos, estanque, los Once faros, islotes).
  - **El set Void** («Coloso del Vacío», 18 objetos animados como los demás sets; set de recompensa: ni tienda ni
    crates) y **la armadura del Vacío** (`VoidArmor`): con 5 jefes de las grietas derrotados, en el altar o pidiéndosela
    a Caelthar; las cuatro piezas = inmune a la corrosión (cada una, un 25 %). Es lo único que protege.
  - **El Portal del Alba** (`VoidPortal`) en el borde oeste de la plaza de la grieta: quedándose 1 s en su luz, vuelve al
    santuario (cruzarlo corriendo no lleva).
  - Las criaturas sueltan su botín del pack (perlas de ender y 12 materiales de Oraxen convertidos en objetos del mod).
  - Probado en el juego (GitHub Actions) sin shaders y con Photon: JEFES §3.21, «Tercera ronda» y anteriores.
- Rama: `claude/peaceful-gauss-4nzddg`. Pendiente del dueño: decir si se cierra el PR en borrador #137 (ya no hace falta).

### CHECKPOINT anterior (19 oct 2026, TF Client 1.3.95)

**1.3.95: la grieta del cielo 2.0** (`tf-client/JEFES.md` §3.20; todo el detalle con imágenes de cada momento, en la
vista previa y en el juego: **`tf-client/docs/grieta-cielo-2/README.md`**). El dueño, al ver la 1.3.94: *«la animación,
el sonido de cristal, el tamaño y el diseño de la grieta son una mierda... esmérate por hacer algo chingón»*. Rehecha
entera:
- **Diseño** (`shaders/core/sky_rift.fsh`): el cielo se rompe como un cristal golpeado (telaraña de fracturas), un
  desgarro quebrado como un rayo (~66° de largo) con dientes angulosos y el borde ardiendo, grietas que siguen más allá
  de las puntas, dentro el Void (nebulosas, estrellas y el ojo: agujero negro con anillo de oro), alrededor un vórtice
  de tormenta negra y violeta (~115° de cielo) con relámpagos, trozos de cielo que salen disparados al estallar y luego
  flotan hacia el desgarro, fogonazo en estrella; sellada, una cicatriz quebrada.
- **Animación** (`SkyRiftClient.look()`, la misma que la vista previa): preludio de 12 s con la tormenta que crece; se
  rompe a saltos, estalla a los 1,8 s y se abre hasta los 5,5 s; se sella; se vuelve a abrir; el final.
- **Sonidos sin cristal**: carga y «sonic boom» del warden, chasquidos de rayo, trueno del tridente, explosión, portal
  del End, rugido grave del dragón; abierta, un trueno lejano cada 30–50 s.
- **Lo que encontró la prueba en el juego y se arregló**: el borde recto del cuadro al mirarla de lado (cuadro mayor:
  1,21 rad) y las **nubes de Minecraft delante del Void** (de lejos tapaban un 26 %): ahora una máscara de profundidad
  las pone detrás del desgarro (0,4 %), y los bloques, el cristal y la lluvia siguen delante.
- Rama de trabajo de esta entrega: `claude/peaceful-gauss-4nzddg` (siguió el trabajo a medias de
  `claude/trusting-euler-rrxyae`, PR en borrador #137, que ya no hace falta).

### CHECKPOINT anterior (19 oct 2026, TF Client 1.3.94)

**1.3.94** (`tf-client/JEFES.md` §3.19), lo que pidió el dueño tras ver la 1.3.93:
- La grieta del cielo va en una **dirección fija del cielo** (norte, 50° de altura), como el sol: antes era un punto
  sobre el spawn y, al reaparecer lejos, salía junto al horizonte. Más grande (~46° × 64°).
- **Aparición de 12 s más siniestra**: el cielo y la niebla se apagan, sombra violeta que late, latidos, sculk, Wither,
  rugido del warden, relámpagos lejanos con grietas que parpadean y temblor que crece; se rompe a saltos con fogonazo y
  onda de choque y se abre despacio. Mientras está abierta, el cielo queda algo apagado.
- La **grieta del Void siempre es la madre** y va **sin cartel** encima.
- La **espada de Caelthar como la lleva un jugador** (ItemInHandLayer, brazo de ArmPose.ITEM).

### CHECKPOINT anterior (19 oct 2026, TF Client 1.3.93)

**1.3.93**: la **grieta del cielo** (`tf-client/JEFES.md` §3.18), lo que pidió el dueño para que el Void sea «asombroso»:
- Al poner a Caelthar la primera vez (`/tf void npc`, en un mundo Overworld) empieza el **evento** para todo el
  servidor: chat de Caelthar «Algo siniestro se avecina al Overworld...», retumbar y temblor leve (6 s), el cielo se
  rompe (grietas, fogonazo, trueno) y se abre al Void; luego Caelthar dice en el chat que lo busquen en el spawn.
- Sale en **todos los mundos de tipo Overworld** (el spawn es un mundo aparte, de un plugin: también): sobre Caelthar
  en el suyo y sobre el punto de aparición en los demás. Al mirarla, una distorsión leve (`RiftWarp`).
- **Se sella** al caer un jefe del Void y **se vuelve a abrir** (sonido del Wither) cuando la grieta del Void tiene
  otro jefe listo (30 s como mínimo). **El final** lo decide el staff: `/tf void cielo final` (también `evento`,
  `abrir`, `cerrar`, `quitar`, `estado`).
- Diálogo de Caelthar: qué es la grieta del cielo y cómo se cierra (romper el cristal de la del Void; las criaturas
  que su orden encerró allí; derrotarlas a todas).
- Código: `vacio/SkyRift` (servidor, guarda el estado en `data/tfclient_cielo.dat`), `vacio/SkyRiftClient` + shader
  `sky_rift` (cliente), `VoidNet.Sky`. Vista previa: `tools/void/grieta_cielo_preview.py`.
- Arreglos de la 1.3.92 que vio el dueño: **un agujero negro falso** (un círculo negro sin disco) en el lado opuesto del
  cielo del Void (el borde suave de la sombra contaba también los rayos que van al revés); **la espada de Caelthar**
  flotaba al lado del brazo → clavada de punta delante de él con la mano en el puño (`tools/void/caballero_espada.py`
  la dibuja y la mide); el **rescate al caer al vacío** no funcionaba con un cliente lento (teletransporte en cada tick).
- El evento lo lanza el servidor: el dueño necesita el jar nuevo **en el servidor y en los clientes**.

### CHECKPOINT anterior (19 oct 2026, TF Client 1.3.92)

**1.3.92**: lo que pidió el dueño tras la 1.3.91 (`tf-client/JEFES.md` §3.17):
- **El jefe del Void persigue por toda la isla** (ve a todo jugador de la dimensión) pero nunca a menos de 6 bloques
  del borde: `VoidIsland.edgeField` (máscara de la isla), `BossEntity.island()/keepOnIsland()`, `Rifts.reach`. Herramienta
  `tools/void/mascara_jefes.py`; la prueba en el juego lo mide (persecución al borde del este).
- **Agujero de gusano limpio** (sin bloques, cubos ni texturas de píxeles): texturas continuas de ruido filtrado, tres
  puertas y la **antesala del Void** con el agujero negro, que se cruza al llegar. Fuera `WormholeTextures`.
- **Agujero negro**: más lento (una vuelta cada ~84 s), borde de la sombra suave (degradado por el parámetro de impacto),
  más detalle (hebras y carriles de polvo), y la furia de la grieta ya no lo hace latir ni parpadear.
- **Caelthar**: la espada grande del set Hada en la mano; **diálogo rehecho** (nombre nítido, texto rápido, sin amatista).

### CHECKPOINT anterior (18 oct 2026, TF Client 1.3.91)

**1.3.91**: el dueño pidió algo «súper épico» para la grieta y el viaje. Hecho (`tf-client/JEFES.md` §3.16):
- **Grieta sin estrado ni garras**: cerrada (una rendija de luz) con su cristal y sus esquirlas. Al despertarla el
  cristal se agrieta y tiembla; al empezar la pelea **estalla** en trozos y la grieta se abre de golpe; al morir el
  jefe se cierra (implosión, fogonazo) y el cristal se rehace.
- **Distorsión del espacio-tiempo** (`bosses/RiftWarp`, shader `rift_warp`): lente gravitatoria, remolino, ondas,
  onda de choque y temblor de cámara desde que se rompe el cristal hasta que muere el jefe (con o sin shaders). En el
  Void, el **agujero negro se enfurece** (`Rage` en `void_sky`).
- **Viaje por un agujero de gusano** (`vacio/TravelFx`, shader `wormhole`, 7,5 s): la realidad se rasga, túnel
  Overworld → Nether → End (a la vuelta End → Nether → Overworld) con texturas del propio Minecraft, puertas de portal,
  bloques 3D girando (nada de «fotos»: lo pidió el dueño), sonidos; al llegar, la luz se va y el mundo ondula.

### CHECKPOINT anterior (18 oct 2026, TF Client 1.3.90)

**1.3.90**: el dueño, con tres capturas, tras probar la 1.3.89. Hecho y probado (`tf-client/JEFES.md` §3.15):
- **Isla ×1,5 con la grieta en el centro de verdad** (`tools/void/isla.py` → `agrandar`: terreno remuestreado suave,
  lo construido a su tamaño, plaza de radio 28). La grieta sigue en 0 72 0. Los mundos con el Void ya generado se
  rehacen solos al arrancar (`vacio/IslandUpdate` v3, empezando por el santuario; probado en el juego).
- **Disco del agujero negro sin muaré**: no era de los shaders, sino del giro Kepler que se enrollaba con el tiempo de
  juego. Ahora: giro de conjunto + dos capas que se renuevan. **Más vida**: disco más rápido, grumos que orbitan,
  chorros con nudos, anillo que vibra; las nebulosas fluyen, laten y caen en remolino al agujero.
- **Alas de Caelthar**: medido, las plumas pasaban 5 px por delante del pecho. Animación «reposo» nueva con alas
  abiertas detrás (`tools/void/caballero_alas.py`, que mide y se niega a guardar si atraviesan).

### CHECKPOINT anterior (18 oct 2026, TF Client 1.3.89)


**1.3.89**: el dueño, con tres capturas, tras probar la 1.3.88 («excelente con el Void, una mierda con Ignevar»). Hecho y
probado en el juego (`tf-client/JEFES.md` §3.14):
- **La grieta del Void** en el centro de la isla, fija y configurable en `/tf boss` → GRIETAS (tarjeta de arriba, ✎):
  dos listas de jefes con su dificultad (la **grieta normal** y la **grieta madre**, más grande, 11 garras, carmesí) y la
  que está en el centro (por defecto la normal); sale uno al azar de la lista activa cada vez que se recarga.
- **Isla**: plaza oscura de la grieta en el centro (nada construido a 45 bloques); **santuario nuevo** en la punta
  norte (donde estaba el templete con cúpula), con cimientos hasta el suelo; se llega mirando a la grieta. Los mundos
  con el Void ya generado se ponen al día solos (`vacio/IslandUpdate`, por zonas, sin parar el servidor).
- **Cielo**: agujero negro más grande, sin grano (ruido filtrado por píxel), al sur. **Con shaders** (Oculus) el Void
  usa el End del paquete (`vacio/ShaderPacks`) y **la grieta y el agujero negro se dibujan después de la pasada final
  del paquete** (`vacio/AfterShaders`, `bosses/RiftLate`, `mixin/GameRendererLateMixin`): Oculus apaga el color y la
  profundidad de los shaders de los mods mientras dibuja el mundo, y con paquete **ninguna grieta se veía** (tampoco
  en el mundo normal). Probado con Oculus 1.8 + Complementary en el CI (`[shaders]` en el commit).
- **Caelthar, el Caballero Sacro** (antes Aurelion): animación de reposo sin el aleteo que le atravesaba el pecho y
  diálogo sin lag (el halo era ~430 dibujos por fotograma). **Viaje**: solo un fundido (sin rayos, franjas ni anillos).
- **Grieta**: sin la raya de «vidrio roto» (corte del `atan` del remolino). **Ignevar**: sus llamas ya se ven (el
  conversor no encontraba sus objetos `item_display` y, sobre todo, **los modelos de efecto de ningún jefe se cargaban**:
  25 de 9 jefes, desde la bola azul de Astwood hasta los pinchos del Druida), anda a la velocidad de la dificultad y, con inteligencia, se gira despacio hacia ti mientras escupe fuego
  (`gira_al_atacar` en `es.json`; **referencia de `sim_ai` de Ignevar actualizada**: menos giros andando, mismos ataques).

### CHECKPOINT anterior (18 oct 2026, TF Client 1.3.88)

**1.3.88**: el dueño mandó dos capturas, el pack de NPCs de Wardenful Studio y el mapa `EndCarageKitMap.schematic`
(los dos, cifrados en `packs/void.tar.enc`, misma contraseña que los de los jefes). Hecho y probado en el juego
(`tf-client/JEFES.md` §3.13):
- **The Void, dimensión nueva** (`tfclient:void`, paquete `vacio`): la isla del dueño sin las casetas del pozo central
  (relleno con su terreno) y con el santuario del caballero; luz del End, sin sol, sin criaturas; protegida (solo staff);
  quien cae vuelve al santuario. **Cielo con agujero negro** (shader `void_sky`, trazado de rayos), sin el sol negro.
  El cielo y la música del vacío ya no salen cerca de las grietas, sino en la dimensión.
- **Aurelion, el caballero de luz** (`/tf void npc`, `/tf void quitar`, `/tf void ir|volver`): diálogo con lore,
  saluda por el nombre la segunda vez, viaje con animación de luz; el del santuario te devuelve a donde estabas.
- **Grieta 3.0** sin texturas (shaders `rift_obsidian` y `rift_fx`): garras facetadas de hasta 18 bloques, desgarro de
  20 bloques de borde suave.
- **Ignevar** volvía a no volar nunca: los marcadores de una línea con `repeat` se leían una vez (ahora en cada
  repetición, como MythicMobs). **Contemplador**: `throw @self` ya no mueve (MythicMobs/Bukkit) y, por orden del dueño,
  sus golpes no suben al jugador («suelo»). El caballero no se dibujaba (carpeta del modelo).

### CHECKPOINT anterior (17 oct 2026, TF Client 1.3.87)

**1.3.87 (17 oct)**: el dueño (con capturas): *«texturas del cielo y la grieta pixeladas, estrellas fugaces horribles,
una grieta más grande e imponente; que desaparezca al salir el jefe y vuelva al matarlo; las llamas no se animan; Astwood
te manda a volar fuera del mapa y su bola azul no impacta; el área del Archivista cae en el aire; el Mamut lanza al aire,
no sigue, aparece y desaparece en bucle y tras pegarte no le puedes hacer daño; más radio del cielo y música siniestra que
se quite al empezar la pelea; quita los tambores al morir; los rayos son exagerados»*. Hecho y probado en el juego
(`tf-client/JEFES.md` §3.12): **supersalto** (el `JUMP 155` de la jaula de Astwood se cortaba a 127 = Salto 128: 128
bloques de alto en la prueba; ahora hasta 255 como Bukkit: no poder saltar), **`freeze`** = frío de la nieve en polvo
(era un aturdimiento de 24 s con Debilidad V: no le hacías daño al Mamut), **`spawn` de ModelEngine una vez** (el del
Mamut está en bucle y salía de la tierra toda la pelea), lo que invoca un jefe se va con él (el trono del Esqueleto se
apilaba), primer fotograma del lanzallamas (Esqueleto, Hidra), `tyo` y alcance de los proyectiles como MythicMobs (área
del Archivista). **Grieta 2.0** ×1,6, se encoge al salir el jefe y vuelve al recargar; **cielo 2.0** con texturas
suaves (`.mcmeta` blur), panorama 2048 sin estrellas pintadas, paralaje, sol negro, fugaces con estela de textura y
bólidos, radio 48→112 (128 en pelea); **música de la grieta** (`grieta.musica`, Discos) que se va al despertar; muerte
sin tambores y rayos más pequeños. La prueba ahora da daño por skill, proyectiles, lanzamientos con su causa y la pelea
con el relevo. La bola de Astwood sí impacta en la prueba (2/2); con `inertia=100` se esquiva andando, como en el pack.

### CHECKPOINT anterior (17 oct 2026, TF Client 1.3.86)

**1.3.86 (17 oct)**: el dueño: *«la grieta es fea, el sonido feo y simple, las estelas blancas de la muerte (las del
dragón) hacen que vea a través de los bloques, el sonido de TNT es horrible; hazla imponente; que la grieta tiña el
cielo como el del End, con nebulosas y estrellas fugaces; un solo color para todas: oscuridad, morado intenso oscuro»* y
que se suban a git los packs de los 17 jefes. Hecho (`tf-client/JEFES.md` §3.11): **grieta nueva** de un solo morado
del vacío (obsidiana con vetas que brillan, agujas, runas, un desgarro animado con el vacío dentro, cristal de
obsidiana), **cielo de la grieta** en el cliente (nebulosas que giran, estrellas que titilan, estrellas fugaces; niebla,
cielo y nubes teñidos; sin nubes con el cielo entrado), **sonidos sintetizados** (golpe, despertar, estallido, lista,
zumbido en bucle; chasquidos y estallido de la muerte en vez de la TNT) y **rayos de la muerte** con una capa que
respeta los bloques. Probado en el juego (capturas `grieta_lista` y `grieta_despierta`). **Packs**: `ss` es público y las
licencias de LittleRoom prohíben redistribuirlos; el dueño eligió un **repo privado** `tf-packs`, que desde aquí no se
puede crear (GitHub responde 403): cuando lo cree y dé acceso a la app de Claude, subir los 17 zips con su README (jefe →
zip). Después el dueño dijo «súbelos público, no importa»: se subieron a `ss` **cifrados** (`packs/jefes-17.tar.enc`,
AES-256; cómo abrirlo en `packs/README.md`; la contraseña la tiene el dueño, no está en el repo). En claro no: sería
redistribuir packs de pago. El dueño no podía descargar («site not reached»: su red no llega a
`release-assets.githubusercontent.com`, adonde GitHub manda las descargas): la web tiene ahora **`/descargar`** (y
`/tfclient.jar`) que sirve el jar de la última Release pasándolo por Cloudflare, y **`/packs`** con el archivo cifrado
(`tierras-fantasticas/src/app.js`, `passDownload`; prueba en `app.test.js`). Enlace para jugadores:
**https://xn--tierrasfantsticas-hpb.store/descargar**.

### CHECKPOINT anterior (17 oct 2026, TF Client 1.3.85)

**1.3.85 (17 oct)**: el dueño: *«la abominación, ¿por qué no la arreglaste?»*. La prueba en el juego ahora separa lo que
anda de lado o atrás **con el modelo bloqueado por el pack** (`lockmodel`: en ModelEngine el modelo no gira y el jefe
sigue andando; la Abominación lo hace en sus ataques de lengua con `setspeed 0.5`) y dice, en cada episodio, hacia dónde
va, hacia dónde mira el cuerpo, dónde está el jugador y el nodo del camino. Encontrado: Minecraft mueve al mob hacia su
**yRot** (el MoveControl lo gira hasta 90°/tick hacia el punto del camino) y el cuerpo del jefe era otro giro aparte:
al recalcular el camino iba de lado (Astwood, 19 % del tiempo). Ahora yRot sale del cuerpo y, tras moverse, el cuerpo
es yRot (`BossEntity.turnBody/aiStep`, `sim_ai.walk`). En el juego, sin `lockmodel`: Astwood, Kazk y Druida 0 ticks de
lado o atrás; Abominación 2 y 2 de 425; Gárgola 1 y 1; Mecha Piglin 0 y 2. Y el jefe ya no suelta al jugador que sus
golpes lanzan fuera de la arena (lo sigue hasta 64 bloques de la grieta: el Druida lo perdía justo al ir a volar hacia
él). Referencias del simulador rehechas (9 jefes, cambios pequeños). (`lock{f=true}` del pack, que gira al jefe hacia el
jugador cada tick mientras anda despacio, también se ve de lado: es del pack; y el Archivista y Eldric se apartan,
retroceden y esquivan con `velocity{r=true}` de su pack.) Arreglado también un **error en el tick de las skills**
(`ConcurrentModificationException` en `SkillRuntime.tick` cuando un efecto sacaba otro, con Eldric: se cortaba el resto
del tick); el registro de la prueba en el juego guarda ahora las trazas del mod.

### CHECKPOINT anterior (17 oct 2026, TF Client 1.3.84)

**1.3.84 (17 oct)**: el dueño, tras la 1.3.83: *«se quedan bugueados, aparecen se van reaparecen, no me hacen caso, se
van volando lejos algunos, corren en retroceso, texturas bugueadas, sin su animación…»* y **«haz tú las pruebas»**.
Hecho: **prueba en el juego de verdad** en GitHub Actions (`.github/workflows/jefes-prueba.yml`, `bosses/BossTest.java`
y `BossTestClient.java`): Minecraft en pantalla virtual, un mundo plano, una grieta por jefe, la pelea, la muerte y
capturas; sale con `[prueba-jefes]` (o `[prueba-jefes=a,b]`) en el commit y deja informe y capturas en la rama
`pruebas-jefes` (`tf-client/JEFES.md` §3.10). Lo que encontró y se arregló: las grietas se reiniciaban sin chunks que
tickearan (ahora la arena se **fuerza** mientras hay pelea); corrían **de espaldas** (retroceso andando de la 1.3.83,
el cuerpo girando más lento que el MoveControl, y el MoveControl que seguía andando tras pararse); la **correa** soltaba
al jugador si el jefe salía de 40 bloques (ahora le sigue si el jugador está en la arena y vuelve teletransportado si
pasa de 64); **Ignevar** volaba de espaldas (`velocity{relative}` con el yRot de Minecraft) y el **Contemplador**
subía a 61 bloques (vuelo de ghast sin límite y `altitude` que miraba solo 8 bloques); antes de un golpe por delante
el jefe se **planta a girarse**. Las «texturas bugueadas» de las capturas eran la partícula `flash` del Contemplador y
los cubos de lava de Ignevar (del pack). En la prueba completa: los 17 salen una vez, objetivo el 100 % (menos el Mega
Guardián, 87 %, que es un Warden, y la Tormenta del Wither, NoAI), 0 barras tras morir, 0 objetos, 0 errores.
Las **tres cabezas de la Tormenta del Wither no salían** (el pack pide `ob_head_1_hitbox` y el hueso convertido es
`head_1_hitbox`: `VfxModel.bone` busca ahora también sin la etiqueta), así que nunca mordía. Quitado el **salto atrás** del cerebro (`backstep`, no es de MythicMobs): resbalaba ~13 ticks hacia atrás con la
animación de andar (el Druida, cada vez que el jugador estaba más cerca de lo que piden sus hechizos); ahora, demasiado
cerca, se queda quieto. Referencias del simulador rehechas para **todos** los jefes por eso (cambia la secuencia al
azar; sin la línea «pasos atrás»): golpes al aire 20 → 10 en total (Astwood 9 → 5, Esqueleto 11 → 5), sin mirarle
1 → 2 (Abominación), ataques casi iguales (Mega Guardián 644 → 764: ya no pierde tiempo saltando). Última prueba en el juego (234e7e8):
el Druida, 0 ticks de espaldas (antes 66-83); la Tormenta del Wither saca sus 3 cabezas, arrastra y muerde. Lo pendiente sigue igual (Pohaku, 51 packs: `JEFES.md` §11.0).

### CHECKPOINT anterior (17 oct 2026, TF Client 1.3.83)

**1.3.83 (17 oct)**: el dueño probó los jefes nuevos: *«algunos se quedan estúpidos y a veces me ignoran, se giran
mal, caminan de lado, tiran ataques al aire, sus boss bar no desaparecen al morir, sus ítems no deben dropearse»*;
mandó una canción (mp3) que **debe sonar siempre al empezar un jefe y quitarse al morir** y pidió que **cada muerte sea
épica, como la del dragón del End que se desintegra**. Hecho (`tf-client/JEFES.md` §3.9): el cerebro sabe **hasta dónde
llega cada golpe** (áreas, conos, tótems en huesos en el instante del golpe) y no lo lanza al aire; **se gira antes**
de un golpe por delante (y espera a que el pack suelte `lockmodel`); andando, **el cuerpo va hacia donde anda**; sin
camino de Minecraft (jefes grandes) va derecho; el jefe ve toda su arena (`FollowRange` ≥ 48); la **barra se quita al
morir**; **ningún objeto cae** (`dropitem` sin efecto, y los mobs de Minecraft que sacan no sueltan nada); **música**
en bucle (`sounds/jefes/musica.ogg`, volumen de «Discos») mientras se ve la barra del jefe, con fundido al morir;
**muerte épica**: rayos de luz como el dragón del color del jefe, grito del dragón, explosiones, el modelo se encoge y
estalla (≥ 8 s). Referencias del simulador rehechas (9 jefes: alcance, giro, combos que deshacen `lockmodel` como el
Java, el Guardián Hueco ya no cuenta sus giros `bank_*` como ataques). **Sin probar en el juego**: pedir al dueño una
pelea entera. **Avisado al dueño**: la canción es una versión con derechos (Final Destination de Smash Bros., cover de
AdamCrossing) y va dentro del jar público. Lo pendiente de antes sigue igual (Pohaku, 51 packs: `JEFES.md` §11.0).

### CHECKPOINT anterior (15 oct 2026, TF Client 1.3.82)

**1.3.82 (15 oct)**: el dueño mandó **64 packs de jefes** nuevos (13 mensajes) tras pedir «que esto no rompa a los
bosses actuales y venideros» (se hizo la red de seguridad: `tools/bosses/verificar.py` + `referencias/`, que corre en GitHub Actions).
Hecho: el motor con la **IA de MythicMobs** (AIGoalSelectors/AITargetSelectors sobre la de su
tipo de Minecraft, el golpe de Minecraft con `~onAttack`, NoAI de verdad, el ghast, `bindhitbox`, relevos…:
`tf-client/JEFES.md` §3.8) y **12 jefes de LittleRoom**: Rey Calabaza, El Archivista, Eldric, Druida Boreal (+ Mamut de
relevo), Contemplador, Arconte Astral, Kazk, Guardián Hueco, Ignevar, Mecha Piglin, Mega Guardián y Tormenta del
Wither; cada uno con su referencia en `verificar.py`. Los 5 de antes, iguales (la Hidra gana las partículas de sangre
de su raytrace, que no salían). **Pendiente** (`JEFES.md` §11.0): **Pohaku** (necesita su escenario: preguntar al
dueño) y los otros **51 packs** (no están en el repo: estaban en el contenedor de la sesión; si se pierden, pedírselos
al dueño), por tandas, con lo que marque el auditor (IA de tipo: arcos de esqueleto, vex, slime…; facciones; lo que
toca el inventario del jugador: preguntar). Avisar al dueño: el jar crece (~29 MB de jefes ahora) y las licencias de
LittleRoom dicen que no se puede poner a descargar en sitios de terceros.

### CHECKPOINT anterior (14 oct 2026, TF Client 1.3.81)

**Estado**: última versión publicada **1.3.81** (los 5 jefes reconvertidos con bezier; cabezas `h_`/`hi_` como
ModelEngine 4; el resto de ModelEngine 4 y lo que faltaba de MythicMobs sin tocar el mundo: `tf-client/JEFES.md` §3.2,
§3.6, §3.7; lo que queda y lo que hay que **preguntar al dueño**, §11). La 1.3.80 metió ModelEngine 4 en el motor
(§3.6; el servidor Mohist y por qué el motor es nuestro: §10). La 1.3.79 dejó el motor listo para packs nuevos (lo que faltaba de
MythicMobs y un auditor que ve también lo que el motor aceptaba sin hacer nada; los 5 jefes, sin cambios.
Las peleas más vivas —combos, tempo, giro, paso atrás— y los modelos completos son de la 1.3.78, el cerebro táctico
de la 1.3.77, la IA fiel al pack de la 1.3.76 y los jefes, grietas y `/tf boss` de la 1.3.75), Release ya creada:
https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient/tfclient.jar (enlace fijo, siempre la última). La web
(Cloudflare) se despliega sola desde `main`. No hay worktrees de agentes pendientes.

**«Colores vivos» quitado y publicado en la 1.3.68** (orden del dueño, 14 oct): borrados `trail/VividColors.java`,
`mixin/GameRendererMixin.java`, `mixin/PostChainAccessor.java` y `assets/tfclient/shaders/post/vivid.json`; fuera de
`tfclient.mixins.json`, de `TrailSettings` y de Efectos › COMBATE.

**Punchy FUERA de TF Client (1.3.73, orden del dueño, 14 oct).** Se metió en la 1.3.69 (jar-in-jar, con permiso firmado
de Punchy Guys Studios) y en la 1.3.72 crasheaba (StackOverflow: `AnimSettings.ownModel` → `ItemRenderer.getModel` →
`ItemPropertiesMixin` de Punchy → `PunchyConfig.isItemBlacklisted` → nuestro mixin → `ownModel`…) y las armas seguían
bajándose al suelo al golpear. El dueño: «quita el puto Punchy de mi mod, revisa una build anterior para que lo dejes como
estaba, solo deja las estelas, y los sonidos son mierda también». **tf-client está exactamente como en la 1.3.68** (commit
`ad7a158`) salvo el arreglo del pad de la 1.3.70 (`GuiGraphicsMixin` + `TFPadScreen.drawing()`: era un fallo de la
1.3.67, no de Punchy) y la versión. **No vuelvas a meter Punchy ni otro mod de animaciones de primera persona, ni el
sonido del tajo, salvo que el dueño lo pida otra vez.** Como ya no se distribuye, los compromisos del acuerdo de Punchy no
aplican (el PDF firmado lo guarda el dueño). Si un jugador tiene `config/punchy/` o `config/tfclient/animaciones.properties`
de antes, es inofensivo (nadie lo lee).
**Compilar SIEMPRE con GitHub Actions** (el dueño, otra vez el 14 oct: «usa git actions para compilar siempre»): nada
de `./gradlew` en el contenedor; el push a la rama ya lanza `tf-client.yml` (compila y revisa mixins; la Release solo
sale desde `main`).

**Motor de jefes «perfecto para todos los que metamos»** (orden del dueño, 14 oct) y **«no queremos que esto rompa a
los bosses actuales y venideros»** (15 oct): `python3 tf-client/tools/bosses/verificar.py` tiene que dar «todo bien»
(auditor a 0, su autoprueba, `test_convert.py` y cada jefe igual que sus referencias de `tools/bosses/referencias/`:
simulador y ficha con cajas, modelos y colas). **GitHub Actions lo exige en cada compilación.** Un cambio a propósito
en un jefe se acepta con `verificar.py --actualizar <jefe>` y se explica; un jefe nuevo trae sus referencias. Lo que un pack use
y el motor no tenga se implementa con la semántica de MythicMobs, nunca se quita del pack. Ver `tf-client/JEFES.md` §7.

**JEFES: toda la documentación técnica está en `tf-client/JEFES.md`** (arquitectura, datos, motor, reglas de
MythicMobs/ModelEngine que no hay que romper, cerebro táctico, herramientas, cómo añadir un jefe y qué mirar según lo
que diga el dueño). Léelo antes de tocar nada de jefes.

**Releases por versión (14 oct)**: el workflow de Rumentis (`compilar.yml` en `claude/happy-ritchie-u6007w`) borra todas
las Releases con tag `^v[0-9]`; por eso desaparecían las `v1.3.x` de TF Client (solo quedaba la fija `tfclient`). Desde la
1.3.68 el tag es **`tfclient-v<versión>`**: https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient-v1.3.68/tfclient-1.20.1-1.3.68.jar

**Cómo trabaja el dueño contigo (reglas que ha repetido; incúmplelas y se enfada con razón)**:
- Escribe en español; responde en español, claro y sin rodeos. Quiere **cambios reales**, no retoques, y calidad de
  producto público. Prueba en el juego y manda capturas: **míralas píxel a píxel** (recórtalas, amplíalas, mide colores con
  PIL) antes de decir que algo está arreglado. Dos veces se dio por arreglada la «doble pantalla» sin auditarla bien.
- **NO uses subagentes** (Agent/Workflow): el dueño lo prohibió expresamente (se gastaron sus tokens). Hazlo tú, directo.
- Publica siempre en `main` sin pedir permiso (rama → PR → squash-merge). Nunca abras el juego ni un servidor. Secretos solo
  en Cloudflare. Nunca subas los packs comprados (zips de MythicMobs/ItemsAdder…): solo lo generado a partir de ellos.
- **Jars: solo como Release** (ver `CLAUDE.md`). No subas jars a `descargas/`. Las Releases de TF Client NO van marcadas
  «latest» (el repo tiene otras: StickerYa, Rumentis, mercado).

**Cómo publicar una versión** (5 min): sube `mod_version` en `tf-client/gradle.properties` y `VERSION` en
`tf-client/src/main/java/net/tierrasfantasticas/tfclient/TFClient.java`; compila con GitHub Actions (push a la rama);
apunta la entrega aquí arriba (bloque «TF Client 1.3.x»); commit, push a la rama, PR y squash-merge (herramientas
`mcp__github__create_pull_request` / `merge_pull_request`); espera el CI de `main`
(`curl -s https://api.github.com/repos/modelqdeluxe-ops/ss/actions/runs?head_sha=<sha>`) y comprueba que el enlace fijo baja
la versión nueva (`unzip -p tfclient.jar META-INF/mods.toml | grep version`). Luego
`git fetch origin main && git checkout -B <tu rama> origin/main && git push --force-with-lease`.
Sin caché de Gradle (contenedor nuevo), `--offline` falla: la primera vez `./gradlew compileJava` sin `--offline` (si Maven
Central da 429, reintentar a los pocos segundos).

**Sin verificar en el juego (solo simulador o compilando)**: **la 1.3.81** (lo primero que mirar si el dueño manda
capturas: las cabezas al mirar —la Gárgola gira torso y cabeza una vez, no el doble—; que los golpes salgan de donde
se ven al girar la cabeza o la cola; las 6 animaciones reconvertidas con bezier —`soul_sword_attack_3` del Esqueleto,
`spawn` de la rotura del escudo y del agujero, `idle`/`walk`/`land` de la cabeza cortada de la Hidra—; el resto de
sus peleas no cambia: `sim_ai.py` idéntico), **la 1.3.80** (lo primero que mirar si el dueño manda
capturas: la cola y las estelas de la Gárgola y la capa del Esqueleto al girar —deben doblarse y arrastrarse, sin
huecos—; el parpadeo rojo al pegarles —el Esqueleto lo tiene apagado en su pack, `d=false`—; que suban un bloque
andando —`step`—; nada más cambia en sus peleas), **la 1.3.79** (nada cambia en los 5 jefes —simulador
idéntico con varias semillas—; lo nuevo lo usarán los packs que vengan; lo único que puede notarse: `~onDamaged` va
antes del golpe y sus umbrales de vida miran la vida de antes, como MythicMobs), **la 1.3.78** (combos, tempo, giro, paso atrás y
los modelos completos: simulado con `sim_ai.py --dif demencial`, auditado con `audit.py` y los modelos dibujados en
Python; lo primero que hay que mirar si el dueño manda capturas: el escudo del Esqueleto —ahora se ve—, que no le
sobre ninguna pieza, el trono con su escalinata, las pústulas de la Abominación y la esfera de Astwood llegando al
jugador), **la 1.3.77** (el cerebro de los jefes: simulado con
`tools/bosses/sim_ai.py --smart 1`), **la 1.3.76** (la IA de los jefes: simulada con
`tools/bosses/sim_ai.py`; pruébalos en SUPERVIVENCIA: como en MythicMobs, no atacan a quien está en creativo),
**la 1.3.75 entera** (jefes, grietas y `/tf boss`: compila
en Actions y los modelos/animaciones se revisaron en hojas renderizadas en Python, pero nadie ha luchado contra ellos; lo
primero que hay que mirar si el dueño manda capturas: la vista previa 3D de la ventana —que mire de frente y quepa—, el
cristal y su cartel, la barra del jefe, las cabezas de la Hidra al golpearlas y el agarre del Esqueleto/Abominación),
**la 1.3.74** (estelas apagadas al entrar; con el espadón y
las demás armas 3D, la estela de punta a guarda de su hoja real; el escenario de noche del TF Pass, visto en el simulador),
**la 1.3.73** (sin Punchy: armas y manos como en la 1.3.68,
Efectos solo con COMBATE, estelas sin sonido; la portada del pad sin las nubes blancas), lo de la 1.3.67 (estelas de arma en primera y tercera persona,
Efectos › COMBATE, el pad en lote —fluidez—, kits en una ficha, Gachapón y TF Pass nuevos, piedras con TF)
y lo de la 1.3.66 (pestañas iguales,
pad de administrador rehecho, interruptores) y lo de la 1.3.65 (pantalla rectangular sin escalones, tubos
rectangulares, paisaje fijo con un castillo y humo) y la 1.3.64 (pantalla que cubre todo el cristal, neon_halo, MP4/M4A/AAC con JAAD) y de la 1.3.63 (marco con su bisel y el fondo debajo,
neones de los temas, paisaje, barra de estado, SKINS con `PadSkinModel`, app Guía, MP3 con JLayer) y de antes las skins
entre jugadores (canal `tfclient:skins`, mixins de skin), el buzón de REGALOS, el asistente de kits del admin y el
escaneo de la carpeta de música. Si el dueño manda capturas de algo de esto, empieza por ahí.
**NO vuelvas a tocar el bisel del marco** (1.3.62 lo «borró» a brochazos con `sin_bisel` y el dueño se enfadó: «mató el
diseño de la pantalla»). frame.png es marco.png TAL CUAL con el cristal hueco; la «doble pantalla» se arregla porque el
fondo del cristal va DEBAJO del marco y llega hasta el bisel.

**Pendiente / ideas que el dueño pidió y siguen abiertas**:
- **Jefes: TODO lo que falta, con dónde tocarlo, cómo comprobarlo y un prompt listo para la siguiente IA, en
  `tf-client/JEFES.md` §11.** Lo primero: que el dueño los pruebe en el juego y que decida lo que se le preguntó en la
  1.3.81 (mecánicas que cambian el mundo o el inventario, dueño/montura, altura de los ojos de ModelEngine).
- **Más jefes**: el dueño los manda «de 5 en 5» (zips de LittleRoom y otros, MythicMobs + ModelEngine). Cómo meter un
  lote: ver «Jefes: cómo añadir uno» en el bloque de la 1.3.75. Esperan su prueba en el juego y su opinión.
- **Actualización de CLANES** (la siguiente que quiere el dueño). Hoy `PadClans`: fundar (nombre y etiqueta), invitar,
  roles líder/oficial/miembro, echar, ascender, salir, disolver, color de la etiqueta, fuego amigo, etiqueta junto al
  nombre, 20 miembros. Lluvia de ideas que se le dio (1.3.66), por fases: **1** banco del clan (depositar/retirar por rol,
  historial), chat de clan, casa del clan en Viajes, estandarte (editor de banner), descripción/lema y clanes públicos con
  SOLICITAR, registro de actividad · **2** nivel y XP del clan (misiones, cazas y oficios de sus miembros), misiones
  semanales de clan, ranking de clanes, mejoras pasivas compradas con el banco · **3** zonas de protección del clan,
  alianzas y rivales, guerra de clanes programada (opt-in), cofre del clan, nombres y permisos de rol · **4** app CLANES en el
  pad de administrador, coste de fundar, página de clanes en la web, logros. Espera a que el dueño elija.
- «Más diseñitos bonitos en las GUIs, mejora lo que tenemos»: es continuo. Lo último: filas con icono 32 en pastilla de
  color (`PadSkin.tile`), campos con lápiz (`PadSkin.field`), temas, el cristal limpio (1.3.108; antes, monogramas TF,
  el paisaje de aldeas y, en la 1.3.107, un emblema «TF»). Ideas no hechas: teñir también las
  fichas azules de la portada con el tema, más ilustración en los vacíos, Ajustes del simulador al día.
- Música: FLAC no se lee (MP3, MP4/M4A, AAC, OGG y WAV sí). El MP3 empieza con ~50 ms de silencio (retardo del codificador y el
  frame Xing/Info): se podría recortar con la etiqueta LAME.
- Adelgazar el jar (55 MB: texturas de GUI 13,6 MB y sonidos de skills 12,7 MB se pueden comprimir) — ofrecido, sin respuesta.

**Mapa rápido del TF Pad (lo que más se toca)** — `tf-client/src/main/java/net/tierrasfantasticas/tfclient/pad/`:
- `TFPadScreen` (marco, cristal, fondo animado, temas, avisos, barras que se arrastran), `PadSkin` (todo el estilo: pestañas
  `tab`/`tabRow` — todas del mismo ancho y separación —, tarjetas, filas `tile`, campo `field`, `photo`, botones,
  interruptor `toggle`), `PadViewPage` (pinta las apps
  del servidor: `PadView` filas/tarjetas/galería/hero; `SELF_MODEL` = el jugador en 3D), `PadHomePage` (portada),
  páginas propias: `PadMusicPage`+`music/`, `PadCommunityPage`+`PadEmoji*`, `PadGachaPage`, `PadPassPage`, `PadInfoPages`
  (Ajustes con temas), `PadSettings` (ajustes y `THEMES`).
- Servidor (`pad/server/`): una clase por app (`PadJobs`, `PadWardrobe` = Armario+Efectos, `PadRewards` = DIARIA+REGALOS,
  `PadPlayers` = regalos/buzón, `PadKits`, `PadAdminKits` = el kit en UNA ficha (lista + `sheet`), `PadClaims`…).
  Efectos › COMBATE es del cliente: `PadCombat` (lo monta `PadViewPage` y sus acciones no van al servidor;
  `PadCombat.withTab` pone la pestaña); estelas en
  `trail/` (`TrailClient`, `TrailProfile`, `TrailSettings` = config/tfclient/estelas.properties, `TrailPreview`).
  Skins: `skins/`.
  Pad de administrador: `PadAdmin` (APPS, AJUSTES, TIENDA, VIAJES, GTS, COMUNIDAD, OFICIOS), `PadAdminKits`,
  `PadAdminRewards`, `PadAdminHunts`; todas con las piezas de `PadAdminUi` (toggle, value + CAMBIAR, stepper − +, cycle « »,
  choice, button, section, note, editor de objetos y monedas; `PadView.compact()` = filas compactas) y `PadItemPicker`. **Cada vista debe llamar `.selected(<su pestaña>)`**: el
  cliente manda esa pestaña con cada acción (si no, la acción vuelve a la portada de la app).
- Texturas: `tools/pad/build_pad.py --marco` (frame.png = marco.png con el cristal hueco, neon.png/neon_core.png),
  `build_skin.py` (rayas, luces, rayos y el paisaje land_far/mid/near),
  `fx_images.py`, `job_images.py <carpeta textures del pack Medieval Jobs>`. Simulador: `python3 tools/pad/sim/pages.py
  <carpeta>` (`PAGES=p_x,p_y`, `THEME=n`; también `p_skins` y `p_guia*`). Pinta como el juego: fondo debajo, marco encima.


> **REGLA NUEVA (13 oct, pedido del dueño): el jar se publica como Release de GitHub, no en `descargas/`.** Subir
> `mod_version` (y `TFClient.VERSION`) y fusionar en `main` basta: `.github/workflows/tf-client.yml` crea la Release
> `tfclient-v<versión>` (antes `v<versión>`) con el jar y `tfclient.jar`. Enlace fijo: https://github.com/modelqdeluxe-ops/ss/releases/download/tfclient/tfclient.jar
> (más rápido que el `raw` del repo, que GitHub limita para archivos grandes). Ver `CLAUDE.md`.

> **TF Client 1.3.81 (14 oct) — JEFES RECONVERTIDOS, EL RESTO DE MODELENGINE 4 Y DE MYTHICMOBS.** El dueño mandó los
> 5 zips otra vez y pidió: reconvertirlos (§11.1), el ModelEngine que faltaba (§11.2) y el MythicMobs que faltaba
> (§11.3) salvo lo que cambie el mundo, con la semántica de las wikis. Todo en `tf-client/JEFES.md` §3.2, §3.6, §3.7 y §11.
> - **Reconversión**: cambian 6 animaciones, todas por **bezier** (ningún pack trae Molang): `soul_sword_attack_3` del
>   Esqueleto, `spawn` de `tower_skeleton_shield_break` y `tower_skeleton_summon_hole`, e `idle`/`walk`/`land` de la
>   cabeza cortada de la Hidra. `build_bosses.py` ya escribe siempre igual (orden fijo).
> - **Cabezas como ModelEngine 4** (wiki + BetterModel): giro global sin acumularse, hijos sin etiqueta que no giran,
>   `hi_` = todo el subárbol, límites por cabeza. Antes la Gárgola giraba el torso y la cabeza el doble.
> - **ModelEngine**: `leash`/`leashself` (correa como la de Minecraft), cabezas/colas/física/IK **también en el
>   servidor** (`vfx/VfxRig`: golpes y cajas donde se ven), `animatemodel`, `rootmotion`, `changeparent`, `submodel`,
>   `swapentity`, `petrify`, `pairmodel`, `modeldisguise`, `move`, `syncyaw`, `jig[repeat=N]`, caja de la entidad con
>   el modelo (`hitboxscale`, `scale{hitbox}`, `showhitbox`), `viewrange`, `renderfire`, sombra, skins slim/base64/de
>   jugadores desconectados, `setmodeltag` con alineación.
> - **MythicMobs**: las líneas sin disparador son `~onCombat` (como MythicMobs; antes `~onSpawn`), `~onSkillDamage`,
>   `~onShoot`/`~onBowHit`, `~onProjectileHit`/`Land`, `~onDespawn`, `~onDismounted`, `~onHear` (como el Warden),
>   `~onReady`/`~onSpawnOrLoad`; `@Cone`, `@Sphere`, `@Rectangle`, `@BlocksInRadius`, `@BlocksNearOrigin`; `score`,
>   `globalscore`, `itemrecharging`, `mobsinchunk`, `playerkills` y `wearing`/`haspermission` de verdad (antes «sí»
>   siempre); `blackscreen`, `bloodyscreen`, `bossborder`, `skybox`, `sendtoast`, `enderbeam` (solo dibujado),
>   `hologram`, `consume`, `oxygen`, `shieldbreak`, `setgliding`, `setpose`, `setmobcolor`, `setpathfindingmalus`,
>   `swap`, `teleportin`, `resetai`, `randommessage`, `variablemath`, `switch`, `chainmissile`, `blockunmask`,
>   `summonareaeffectcloud`, `barremove`. Los jefes ya no pasan por portales.
> - **Sin hacer, para preguntar al dueño** (§11.3): lo que cambia el mundo (`breakblock`, `fill`, `time`, `weather`…),
>   el inventario o el estado del jugador (`giveitem`, `takeitem`, `decapitate`, `setgamemode`, marcador…), dueño y
>   montura (`setowner`, `mountme`, `remount`…), y la altura de los ojos del hueso `hitbox` (§11.2).
> - Comprobado: auditor 0 problemas en los 5, `--prueba` 20/20, `test_convert.py` «todo bien» (10 pruebas nuevas),
>   `sim_physics.py` (Gárgola y Esqueleto: se doblan sin pasar de 50°), `sim_ai.py` **idéntico** (semillas 3 y 11,
>   todas las dificultades, `--min 8`). Cobertura: condiciones 88/88, objetivos 47/47, mecánicas 198/235.

> **TF Client 1.3.80 (14 oct) — MODELENGINE 4 COMPLETO EN EL MOTOR.** El dueño: «¿por qué no arreglaste el segment?
> Necesito que la importación de todos los demás bosses sea correcta, sin errores, sin texturas malas, sin fallas de
> animación, IA perfecta… el server es Mohist… dame el máximo potencial». Todo en `tf-client/JEFES.md` §3.6 y §10.
> - **Colas y segmentos** (`seg_`/`tl_`, mecánica `segment`): `vfx/VfxPhysics`, verificado en Python
>   (`tools/bosses/sim_physics.py`): la Gárgola girando dobla su cola en arco continuo, la capa del Esqueleto ondea.
>   **Física** `jig_`. **IK** de Blockbench + `ikchain`/`iktarget`/`ik` (`vfx/VfxIk`, CCD; un fallo de convergencia
>   encontrado al probarlo y arreglado antes de publicar).
> - **Las 26 etiquetas de hueso** de ModelEngine en el conversor (`b_`, `h[...]_`, `tag_`, `ih_/ir_/il_`, miembros de
>   jugador, `jig[...]_`, `ik[...]_`...) y casi todas sus 64 mecánicas: cabezas (`headconfig`, `lockmodelhead`,
>   `headtarget`), `modifystate`, `statetoggle`, `setitemmodel`, `setmodeltag`, `enchant`, `billboard`, `glowbone`,
>   `cycleparts`, `hitboxconfig`, `modelplayerskin`, la familia `vfx`... De `model{}`: `damagetint` (parpadeo rojo),
>   `nametag`, `lockpitch/yaw` y `step` (antes `s` se leía como escala).
> - **Animaciones fieles**: bezier, Molang (`tools/molang.py`) y fotogramas antes/después se hornean como Blockbench;
>   Box UV y texturas emisivas. **Los 5 jefes se convirtieron con el conversor viejo**: para aplicarlo hay que volver a
>   convertirlos (el dueño tiene que mandar otra vez sus zips).
> - Efectos de MythicMobs (`particletornado`, `particlelinehelix`, `atom`, `geyser`...), `summonfallingblock` (sin
>   tocar el mundo), `shootpotion`, `shootshulkerbullet`; `meg:`/`mythicmobs:` en los nombres.
> - **Auditor de modelos y texturas** (texturas que faltan, UV fuera, tiras mal cortadas, pistas rotas, IK) y
>   `test_convert.py` (19 pruebas del conversor). Autoprueba del auditor 16/16; los 5 jefes, 0 problemas; `sim_ai.py`,
>   idéntico. **Mohist**: el motor propio es la vía (ModelEngine no soporta híbridos); qué mirar en `spigot.yml`, §10.

> **TF Client 1.3.79 (14 oct) — MOTOR DE JEFES LISTO PARA PACKS NUEVOS.** El dueño: «recuerda que quiero que este
> motor sea perfecto y bien corregido para todos los bosses que metamos». (Una sesión anterior empezó esto y se quedó
> sin tokens sin subir nada: se rehízo entero desde la 1.3.78.) Todo en `tf-client/JEFES.md` (§3.2, §3.3, §6–§9).
> - **Lo «de mentira» destapado**: el motor aceptaba `cancelevent`, `threat`, `clearthreat`, `setinvulnerable`,
>   `goto`, `glow`, `setgravity` sin hacer nada, y daba por cumplidas `lineofsight`, `world`, `biome`, `moonphase`,
>   `hasowner`, `hasai` (y `targetinlineofsight` solo miraba la distancia). El auditor las contaba como hechas: un pack
>   con una fase invulnerable habría dado «0 problemas» y fallado en el juego. Ninguno de los 5 jefes dependía de eso.
> - **`~onDamaged` antes del golpe** (como el EntityDamageEvent de MythicMobs): `cancelevent` lo anula y
>   `modifydamage{amount;action}` lo cambia (`SkillMobs.Hit`); los umbrales miran la vida de antes.
> - **Mecánicas nuevas o de verdad**: `threat{amount;mode}` (ADD…FORCE), `clearthreat`, `rally`, `setinvulnerable`,
>   `goto`, `glow`, `setgravity`, `suicide`, `setmaxhealth`, `shield`/`shieldpercent`, `explosion` (nunca rompe
>   bloques), `fakeexplosion`, `forcepull`, `disengage`, `rotatetowards`. **Disparadores**: `~onDropCombat`,
>   `~onChangeTarget`, `~onKill`, `~onPlayerKill`, `~onInteract`. **Objetivos**: `@ThreatTable`/`@TT` (en jefes),
>   `@RandomThreatTarget`, `@RandomThreatTargetLocation`, `@TriggerLocation`, `@PlayersOnServer`, `@SpawnLocation` (la
>   grieta, no donde está). **Condiciones**: `playernotwithin`, `lineofsight`, `targetinlineofsight`, `world`, `biome`,
>   `moonphase`, `hasowner`, `hasai`, `day/night/dusk/dawn`, `raining/thundering/sunny`, `outside`, `lightlevel`,
>   `distancefromspawn`, `heightabove/below`, `lastdamagecause`, `mounted`, `haspassenger`, `isfrozen`, `gliding`,
>   `isclimbing`, `motiony`, `fallspeed`, `level`, `time`, `targets`, `playersonline`, `mobsinworld`; `health` en
>   la escala del pack.
> - **Auditor**: lee del Java lo que no hace nada (`-> { /* */ }`, `-> true;`) y los disparadores que saltan; lo «de
>   mentira» sin su porqué en `STUB_OK` es problema; avisos aparte (hoy solo `segment`: capas/colas rígidas).
>   `--cobertura` (catálogo `tools/bosses/mythic_ref.py`) y `--prueba` (11 de 11 errores metidos a propósito). Los 5
>   jefes: 0 problemas.
> - `sim_ai.py` refleja todo (golpe antes de entrar, cancelar/modificar, invulnerable, suicide, playernotwithin,
>   amenaza): los 5 jefes dan exactamente las mismas cifras que con la 1.3.78 (semillas 3 y 11, Normal, Difícil y
>   Demencial).

> **TF Client 1.3.78 (14 oct) — PELEAS DE JEFES MÁS VIVAS Y MODELOS COMPLETOS.** El dueño, tras la 1.3.77: «actúan
> mejor, pero los sigo sintiendo estúpidos, lentos al girar… solo me tiran 2 ataques básicos, ¿no debería haber más
> combos?… el de plaga se queda estúpido gritándome cuando estoy frente a él… el esqueleto tiene una espada en la
> pata… Astwood tira una bola azul, ¿qué efecto tiene? cae como un huevo al suelo… perfecciona esto para que al meter
> los demás no tengamos errores». Todo en `tf-client/JEFES.md` (§3.3, §4.2, §4.3, §6–§8).
> - **Combos** (`SkillBrain.chain`, inteligencia ≥ 0,6): pasada la parte activa de un ataque (`activeEnd`), corta su
>   recuperación y encadena otro (cancela lo que le queda con `SkillRuntime.cancelCast`: cada uso lleva un id).
> - **Tempo** (`BossEntity.tempo` = velocidad × (1 + 0,12 × inteligencia)): delays, gcd, variables del jefe y
>   animaciones a la vez (Demencial ×1,4). **Giro** (`turnBody`): `rde`/`rdu` del pack acortados con la inteligencia.
> - **Plan** (`SkillBrain.plan`): solo piensa en ataques de su fase/postura (`possible`) y rota el repertorio (el que
>   hace más que no usa). Antes podía «pensar» en la plaga (< 50 %) de la Abominación y quedarse quieto. **Paso
>   atrás** (`backstep`) y apartarse cuando se le pegan más que el alcance mínimo (la Abominación pide ≥ 3 bloques en
>   todo: pegado a ella no hacía nada). `~onEnterCombat` una vez por pelea (no vuelve a gritar).
> - **Modelos**: el conversor tiraba los cubos con el «ojo» apagado en Blockbench, pero ModelEngine los dibuja (el pack
>   los enseña/esconde con `partvis`): el Esqueleto **no tenía escudo**, ni espada a la espalda, ni escudo junto al
>   trono; el trono no tenía escalinata ni pozo de almas; la Abominación, ni pústulas ni zarpazo. Y `partvis` en los
>   jefes con los valores de ModelEngine (sin `v` esconde, `child` false, `em` true): la espada de la muerte ya no
>   sale en la pierna.
> - **Esfera de Astwood**: `@EIR` de un jefe ya no apunta a sus esbirros/efectos, el misil sale hacia el centro del
>   jugador y su inercia es como mucho 12 (la del pack, 100, no giraba y apuntaba a los pies). Hace 6 de daño que
>   ignora armadura.
> - **Auditor** `tools/bosses/audit.py` (también al final de `build_bosses.py`): mecánicas/condiciones/objetivos sin
>   implementar (lee los `case` del Java), skills que faltan, animaciones y huesos que no existen, sonidos y modelos.
>   Los 5 jefes: 0. El conversor corrige erratas de una letra en nombres de animación (Astwood «absord_life_start»).
> - `sim_ai.py --dif <preset>` refleja todo; Demencial: Gárgola 27,5 ataques/min, Hidra 36,9, Abominación 18,8,
>   Esqueleto 19,5 (42 combos en 8 min), Astwood 29,1.

> **TF Client 1.3.77 (14 oct) — CEREBRO TÁCTICO DE LOS JEFES (tipo Souls).** El dueño: «quiero que la IA de estos
> bosses sea muy inteligente, tipo Dark Souls o Elden Ring… y documenta todo para que otra IA pueda retomarlo».
> - `skills/SkillBrain.java`: los ataques siguen siendo los del pack; el cerebro decide cuál sale y cuándo (prueba
>   todos sin lanzarlos con `SkillRuntime.usable` y puntúa: alcance, castigos a curas/escudo/arco/distancia, ataques
>   que dan detrás, área si hay grupo, no repetir, aprender lo que esquiva el jugador, algo de azar), piensa de cada
>   20 ticks a cada 4 según la inteligencia, a veces se lo piensa, encadena ataques sin gcd y da un plan de movimiento
>   (distancia de su próximo ataque, rodear mientras espera). `BossEntity`: sigue al jugador al cargar el golpe, elige
>   objetivo (curas, arqueros, rematar) y se enfurece por debajo del 50 % y del 25 %.
> - **Inteligencia** nueva en la dificultad (`Difficulty.smart`, JSON «inteligencia»; fila 5 de `/tf boss`): Fácil
>   30 %, Normal 55 %, Difícil 75 %, Pesadilla 90 %, Demencial 100 %; 0 % = el pack tal cual. La ventana, con 9 filas,
>   sigue sin solapes a los 8 tamaños (`sim_gui.py`).
> - El gcd ya no se divide por el ritmo (cortaba las animaciones a medias); el ritmo acorta cooldowns y lo que piensa.
> - `tools/bosses/sim_ai.py --smart X`: el cerebro copiado en Python, y el jugador simulado se cura, se cubre y
>   dispara; mide ataques por minuto, variedad, repeticiones y curas castigadas.
> - **Documentación completa: `tf-client/JEFES.md`.**

> **TF Client 1.3.76 (14 oct) — IA DE LOS JEFES ARREGLADA.** El dueño, tras probar la 1.3.75: «cargan bien en cuanto a
> textura, pero no tienen todas sus habilidades, solo hacen 1 ataque genérico, son estúpidos; unos se quedan bugueados
> en el cielo como el rey esqueleto… incluso en la dificultad máxima». No era el pack: era nuestro motor, que no hacía
> lo que hace MythicMobs/ModelEngine en varias cosas que los jefes usan a cada paso:
> - **Cooldown antes de las condiciones** (`SkillRuntime.runMeta`): se ponía el enfriamiento aunque la skill no saliera
>   (distancia, ángulo, auras…). Los ataques que se miran cada segundo se quedaban en recarga sin salir: casi siempre
>   quedaba uno (Astwood, con su randomskill, solo daba puñetazos). Ahora solo cuenta si sale.
> - **Variables con duración** (`setvar{...;duration=40}`) no caducaban (`putVar`): la Hidra («saltando», «mordiendo»,
>   «lanzando fuego») y la Abominación («lengua», «garras», «tentáculo») se quedaban marcadas para siempre tras el
>   primer ataque.
> - **Animaciones por prioridad** (`VfxLayers.play`): en ModelEngine 4 cada prioridad es una máquina de estados con UNA
>   animación (la nueva sustituye a la anterior); aquí se apilaban. El «sleep» del Esqueleto de la Torre (lo sube 5,6
>   bloques y lo echa 7 atrás, sobre el trono) seguía debajo al despertar: **flotaba en el aire**.
> - **`~onDamaged` con el daño del golpe** (no el recibido, `BossEntity.hurt`): con el escudo arriba el Esqueleto tiene
>   Resistencia V y le entraba 0, así que el escudo no se rompía nunca y no pasaba de la fase 1. Y **potion{level}** en
>   los jefes es el amplificador (como MythicMobs: level=4 es Resistencia V); en las clases sigue como estaba.
> - **`gcd` siempre del jefe** (antes, dentro de un ataque lanzado con @target, se lo ponía al jugador y encadenaba
>   ataques encima unos de otros) y **las mecánicas de ModelEngine sin objetivo** (`bodyrotation`, `partvis`…) van a su
>   modelo (`SkillRuntime.OWN_MODEL`).
> - **`propel` en los jefes**: «v» bloques por tick hacia el objetivo (antes v/10 sumado: la Gárgola no saltaba, solo
>   daba manotazos en el sitio). **`setspeed{s}`** es la velocidad del tipo por «s» (la Abominación sale con
>   MovementSpeed 0 y no andaba nunca).
> - **Hacia dónde mira** (`Host.lookYaw`): fieldOfView, @forward y los proyectiles usan la dirección de la entidad
>   (mira a su objetivo, 30°/tick), no la del modelo; el modelo, andando, sigue esa dirección y, parado, la cabeza
>   arrastra al cuerpo pasado «mh» (como ModelEngine). Se acercan hasta casi tocar (cuerpo + 1 bloque; antes +2,2 y
>   Astwood nunca llegaba a los 4 bloques de sus puñetazos).
> - `playerWithin` cuenta también a quien está en creativo (la Gárgola y el Esqueleto despiertan), y la incursión no se
>   cierra si solo hay staff en creativo cerca. **Pero, como en MythicMobs, no atacan a quien está en creativo.**
> - **Simulador de la IA**: `python3 tools/bosses/sim_ai.py [jefe] [--min 8] [--seed N] [-v] [--antes]` ejecuta el
>   programa del jefe con las mismas reglas que Java contra un jugador que se mueve, le pega y es lanzado, y cuenta los
>   ataques. Con `--antes` reproduce el motor de la 1.3.75 (la Abominación: 2 ataques en 4 minutos; la Hidra se quedaba
>   en 9 de 15). Ahora: Gárgola 5/5, Hidra 14/15 (la rotura de cabezas sale por debajo del 50 %), Abominación 6/7 (el
>   agarre depende de que el tentáculo te pille), Esqueleto con sus 3 fases (escudo → sin escudo → espada de almas por
>   debajo del 35 %), Astwood 12/12; 6–30 ataques por minuto según el jefe.

> **TF Client 1.3.75 (14 oct) — JEFES DE INCURSIÓN.** El dueño mandó 5 packs de jefes (zips: Gárgola, Hidra y Abominación
> Parasitaria de LittleRoom, Tower Skeleton y Astwood) y pidió: «portear todo correctamente, animaciones, vfx, fases,
> todo… funcionalidad completa con nuestro motor»; que sean **incursiones de grupo**: «una grieta o un cristal muy grande
> tipo gema que al golpearla activa y sale el boss»; y **`/tf boss`** para elegir el jefe con **vista previa** y su
> dificultad (velocidad, ataque, daño…: **presets de Fácil a Demencial**), «gui sin solapamiento y bien hecha». También:
> «para compilar usa Git actions siempre». **Los packs NO están en el repo** (ni hay que subirlos): solo lo convertido.
> - **Convertidor** `tf-client/tools/bosses/build_bosses.py <carpeta con los packs descomprimidos>` (Astwood venía en
>   RAR: `bsdtar -xf`). `JEFES` = carpeta del pack → id (`gargola`, `hidra`, `abominacion`, `esqueleto_torre`,
>   `astwood`), su mob de MythicMobs y la vida/daño base (Normal, 1 jugador; los de los packs eran de ejemplo).
>   `es.json` = nombre, título, color, descripción, ataques, `preview_hide` (huesos que no salen en la vista previa) y
>   nombres de los objetos que sueltan. `FIXES` = skills que el pack llama sin traerlas (`LR_HYDRA_stomp_damage`). Sale
>   `assets/tfclient/bosses/<id>.json` (árbol de skills alcanzable desde el mob, mobs que invoca, modelos, objetos),
>   `bosses/models/<id>.<modelo>.json` (los .bbmodel con `tools/vfx_bb.py` en **modo jefe**: prioridad y override de
>   las animaciones, scripts `mm:` de la línea de tiempo, cajas `ob_`, asientos `p_`, cabezas `h_`/`hi_`, tamaño del
>   hueso `hitbox`), texturas `textures/bosses/`, modelos de ítem `models/bosses/` y `models/item/jefe_*.json`,
>   sonidos `sounds/bosses/` (eventos `tfclient:bosses.<id>.<x>`), glifos `font/bosses_<id>.json`. En la pasada completa
>   rehace también el cristal (`rift_model.py`, modelo procedural: pedestal con runas, anillos, gema, núcleo, grietas y
>   esquirlas; animaciones idle/activate/dormant/regrow; texturas `*_tint_e` = emisivas teñidas del color del jefe).
> - **Motor** (`skills/` + `vfx/`, sin cambiar las clases de jugador): `VfxLayers` = animador por capas como ModelEngine R4
>   (prioridad, override/aditivo por hueso y canal, entrada/salida suave, reposo/andar por defecto, muerte),
>   `VfxHitboxes` (cajas `ob_` → AABB), `SkillMobs` (los jefes y esbirros como «host» de MythicMobs: disparadores
>   ~onSpawn/~onTimer/~onDamaged con umbral de vida `<50%`, ~onDeath, señales, tabla de amenaza, scripts de las
>   animaciones), `CameraShake`; condiciones/objetivos/movimientos nuevos que usan los packs.
> - **Paquete `bosses/`**: `BossEntity` (`tfclient:jefe` y `tfclient:esbirro`: vida sin el tope de 1024, cajas por
>   piezas `BossPart` —las 5 cabezas de la Hidra—, respiro de 8 ticks por jugador, giro del cuerpo como ModelEngine,
>   barra «Nombre · Dificultad», muerte con su animación), `RiftEntity` + `Rifts` (la grieta: LISTA → al golpearla
>   DESPIERTA 3,5 s → INCURSIÓN → RECARGA; correa de 40 bloques; si se queda sola 30 s, el jefe se va; monedas con
>   `TFEconomy.give` a cada jugador que le hizo daño; guardadas en `<mundo>/tfclient/grietas.json`), `Difficulty`
>   (presets facil/normal/dificil/pesadilla/demencial: vida, daño, velocidad, ritmo de ataque, armadura, vida por
>   jugador extra, recarga y monedas), `BossConfig` (`config/tfclient/jefes.json`: la elegida de cada jefe),
>   `SeatEntity` (agarres), `BossWorld`, `BossItems` (Guja/Égida/Cuernos del Esqueleto con sus skills del pack),
>   `BossNet` (canal `tfclient:bosses`), `BossScreen` (la ventana), `BossRenderer`/`RiftRenderer`/`ModelDraw`.
> - **`/tf boss`** (staff, permiso 2; el dueño lo pidió aunque la regla general es solo `/tf web` y `/tf reload`):
>   izquierda los jefes (tarjeta de su color con punto de la dificultad guardada), centro el jefe en 3D (reposo,
>   arrastrar = girar) y PONER GRIETA AQUÍ / PROBAR DELANTE / GUARDAR CAMBIOS, derecha DIFICULTAD (5 presets y 8 valores
>   con − y +; Mayús = ×5; rueda del ratón; ayuda al pasar por encima; «Con 4 jugadores: X de vida» y la descripción si
>   cabe) y GRIETAS (IR, ✎ editar su jefe/dificultad —GUARDAR/REINICIAR/DEJAR DE EDITAR—, ✕ dos veces para quitar,
>   QUITAR JEFES DE PRUEBA). Colores macizos. **Disposición comprobada** con `python3 tools/bosses/sim_gui.py [--png
>   carpeta]` (mismas cuentas que `BossScreen.layout`) de 427×240 a 1280×720: nada se solapa ni se sale.
> - **Jefes: cómo añadir uno**: descomprime su pack en la carpeta de packs, añádelo a `JEFES` (id, mob, vida, daño) y a
>   `es.json`, `python3 tools/bosses/build_bosses.py <carpeta>` (debe decir «problemas: 0»; si falta una skill del pack,
>   a `FIXES`), mira las hojas de sus modelos y sube la versión. Las mecánicas de MythicMobs que un pack use y el motor no
>   tenga salen en «missing» del JSON del jefe.

> **TF Client 1.3.74 (14 oct)** — el dueño: «quedó mejor; haz que la estela esté desactivada por defecto, y que adopte
> el tamaño real del item: con el espadón la estela se pone en medio o a tamaño de una espada vanilla. Y el fondo del TF
> PASS es una mierda, dale rework como lo hiciste con el gachapón».
> - **Estelas apagadas de fábrica** (`TrailSettings.enabled = false`, también RESTABLECER). La clave del archivo pasa de
>   `activadas` a `encendidas`: la vieja salía `true` en cualquiera que hubiera tocado un ajuste, así que con esta versión
>   arrancan apagadas para todos; quien las quiera, las enciende en Efectos › COMBATE.
> - **Estela del tamaño real del arma** (`TrailClient.readBlade`): antes la punta y la guarda salían de la textura,
>   como si todo fuera una espada plana de 16 px; las armas 3D del servidor (el espadón mide 32 px de alto) quedaban con
>   la estela a media hoja. Ahora, si el modelo no es plano, se mira su forma (`readShape`): los vértices del modelo
>   cocido, puestos como los lleva un jugador en tercera persona (la mano en el origen, el arma hacia +y); la punta es
>   el vértice más lejos de la mano por encima de ella (una guadaña cogida por el medio no toma el pie del mango), la
>   empuñadura el otro extremo en esa línea y la hoja empieza a un tercio. Simulado en Python con los 552 modelos de
>   armas 3D de TF Client: todos con la punta en el extremo largo. Las planas (Minecraft, otros mods), como antes. La hoja
>   se guarda por modelo (con sus «overrides»). La vista previa de COMBATE usa la posición «gui» del modelo
>   (`TrailClient.guiSpace`), así la estela sale sobre la hoja también con un arma 3D en la mano.
> - **TF Pass, escenario nuevo** (`PadPassPage.drawNight`, igual en el simulador: `pass_night`): el cielo de día casi
>   blanco con colinas verde pálido no tenía contraste. Ahora, como el de la ruleta pero suyo: noche esmeralda, aurora de
>   dos cintas que ondula, estrellas que titilan, la luna (`moon.png` al doble), un charco de luz de oro y focos que
>   giran detrás de tu nivel (te siguen al desplazar), resplandor del horizonte, colinas en silueta con el borde de luna
>   (paralaje), luciérnagas que suben, rayas que se mueven y los lados en sombra. El camino que falta, de piedra azul
>   (antes arena clara). HUD, medallones, bocadillo y tira de XP, igual.

> **TF Client 1.3.73 (14 oct)** — el dueño, tras el crash de la 1.3.72 al activar las animaciones y con la espada (también
> la de netherita) bajándose al suelo en cada golpe: «quita el puto Punchy de mi mod, revisa una build anterior antes de
> meter Punchy para que lo dejes como estaba, solo deja las estelas, y los sonidos son mierda también».
> - **tf-client vuelto a la 1.3.68** (`git checkout ad7a158 -- tf-client/` y borrados los archivos nuevos): fuera Punchy
>   entero (tarea `punchyJar` de build.gradle, `META-INF/jarjar/`, `punchy-PERMISO.txt`, `credits` de mods.toml, `anim/`,
>   `PadAnims`, los mixins `Punchy*`, `FishingHookRendererMixin` y `GameRendererHandsMixin`), Efectos › ANIMACIONES, la
>   guía de ANIMACIONES y el sonido del tajo (`sounds/combate/`, sus entradas en sounds.json, `TrailSettings.sound`,
>   `TrailClient.swingSound`, `tools/trail_sounds.py`). Las manos y las armas vuelven a ser las de Minecraft (y las del
>   modelo de cada arma TF), como antes de Punchy.
> - **Se queda**: las estelas tal cual estaban en la 1.3.68 (Efectos › COMBATE) y el arreglo del pad de la 1.3.70
>   (`GuiGraphicsMixin`: el lote se vacía en blanco y se deja la mezcla encendida; si no, la portada sale con manchas
>   blancas). `git diff ad7a158 -- tf-client` = solo eso y la versión.
> - Las entregas 1.3.69–1.3.72 de abajo quedan como historia: nada de eso está ya en el mod.

> **TF Client 1.3.72 (14 oct)** — el dueño: «¿no arreglaste lo de la espada? No estaba así, se jodió con Punchy».
> - **Causa** (javap de Punchy): en primera persona Punchy cuelga el arma de su brazo 3D y, para espadas y herramientas,
>   `ItemInHandRendererMixin.punchy$forceVanillaDisplay` cambia la vista a `FIRST_PERSON_*`: usa la posición de
>   primera persona del modelo, la de la espada de Minecraft (0/−90/25°, 1,13/3,2/1,13 px, 0,68). Las armas TF son
>   modelos 3D con la suya (`oni_right_sword`: 60,25°, 5,5/4/−10,25 px): sobre el brazo de Punchy ese desplazamiento hace
>   de palanca y cada espadazo barre el arma hasta el suelo.
> - **Arreglo**: tipo nuevo `AnimSettings.Kind.MODEL` «Armas y objetos del servidor», **NO de fábrica** (`def`): objetos
>   `tfclient:` cuya transformación `FIRST_PERSON_RIGHT_HAND` no es la estándar (`ownModel`) van por la lista negra de
>   Punchy = como antes de Punchy. Los de Minecraft siguen con Punchy. Para animarlas con Punchy habría que ajustar cada
>   modelo (su sistema de «tuning» por objeto) con capturas o vídeo del dueño.

> **TF Client 1.3.71 (14 oct)** — el dueño: «cuando desactivo las animaciones no vuelve a la normalidad y cuando suelto
> espadazos el modelo se baja hasta el suelo» (captura: no se ve la mano), «cuando las desactivo deben volver a las vanilla
> originales sin errores».
> - **Por qué**: el «Enable Punchy» de Punchy (lo que cambiaba ACTIVADAS/APAGADAS) no lo apaga del todo. Su tick se sale al
>   principio sin limpiar (`PunchyClient` lambda$init$0: `if (!isModEnabled()) return`): lo que estaba a medias se queda
>   congelado (mano bajada, objeto en uso, cámara, retraso del inventario). Y varios de sus enganches en Minecraft no
>   miran si está activado (revisados todos con javap): `ItemRendererMixin` (pone una transformación «de reposo» al
>   objeto que usas, a cualquier objeto), `ItemPropertiesMixin` (arco/ballesta: pasan por su lista negra),
>   `FishingHookRendererMixin` (el sedal sale de la mano de su modelo), `CameraMixin`, `MinecraftScreenDelayMixin`,
>   el cuerno. Los demás sin comprobación son inofensivos (tercera persona = vacío, bobView solo apunta, fuego vacío,
>   barcas/vagonetas solo con la barca en la mano, rastreadores de golpes/minado/uso).
> - **APAGADAS = vanilla** (`PunchyBridge.turnOff`/`vanilla()`): (1) ~10 ticks todo cuenta como su lista negra (incluida
>   la mano vacía) con Punchy aún encendido: su `enforceBlacklist` para y funde lo que tuviera; (2) se apaga
>   (`enableMod=false`); (3) mientras esté apagado, `vanilla()` (lee `PunchyConfig.isModEnabled` con un MethodHandle, así
>   vale también si se apaga desde su propia pantalla) cierra cada puerta: `PunchyConfigMixin` (todo en lista negra),
>   `PunchyArmRendererMixin` (no pinta brazos; sin transformación de reposo; sin brazo guardado), `PunchyUseItemMixin`
>   (cuerno), `PunchyCameraMixin` (`CameraAnimationState.isActive` = false), `PunchyScreenDelayMixin` (inventario al
>   momento) y `FishingHookRendererMixin` (nuestro, en Minecraft: el único Vec3 de `FishingHookRenderer.render` = el punto
>   del sedal; se cambia tras el de Punchy por el cálculo original de Forge 1.20.1, mirado en el cliente oficial de Mojang).
>   La cuenta atrás solo corre sin pausa (como el tick de Punchy). Los tipos en NO («con qué objetos») también cierran
>   la transformación de reposo, el cuerno y el sedal de la caña.
> - (Lo de «se baja hasta el suelo» se explicó mal aquí como la bajada normal de las armas lentas; el dueño lo desmintió:
>   antes de Punchy no pasaba. La causa real y el arreglo, en la 1.3.72.)

> **TF Client 1.3.70 (14 oct)** — el dueño probó la 1.3.69 (captura de la portada): «¿qué mierda hiciste con el pad?»,
> «las estelas no sirven, no se muestran, ni tampoco hay sonidos».
> - **Pad (portada con manchas blancas)**: medido en su captura: las nubes y los rayos del cielo salían opacos, lo
>   transparente de su textura en blanco (246,252,255) con la sombra gris alrededor. Venía del dibujo en lote de la
>   1.3.67 (nunca probado en el juego): `GuiGraphicsMixin` vacía el lote antes de cada textura y al vaciarlo Minecraft
>   apaga la mezcla (blend); el `blit` siguiente salía opaco. Además lo del lote se pintaba con el color/alfa ya puesto
>   para esa textura. Ahora, mientras pinta el pad (`TFPadScreen.drawing()`), el lote se vacía en blanco y luego se deja
>   el color de la textura, la mezcla encendida y la normal (el pad siempre pinta así). Fuera del pad, igual que antes.
> - **Estelas con Punchy**: Punchy pinta el arma de la primera persona con `THIRD_PERSON_*_HAND` (la cuelga de su brazo
>   3D), y `TrailClient` solo apuntaba la hoja con contexto de primera persona: con Punchy no salía ninguna estela.
>   `GameRendererHandsMixin` marca el pase de las manos (`GameRenderer.renderItemInHand`) y en él lo del propio jugador
>   cuenta como primera persona.
> - **Sonido del tajo** (no había ninguno): `tools/trail_sounds.py` (sintetizado: ruido con un paso banda que baja de
>   agudo a grave, cuerpo grave; 1,3–1,9 kHz de centro, nada por encima de 5 kHz; sin chasquidos) → sounds/combate:
>   `combate.tajo` (3 variantes al azar, ~0,2 s) y `combate.tajo_fuerte` (crítico, 0,43 s). Suena al golpear (no al
>   picar) con un arma que deja estela; volumen según la carga, tono según el arma (hachas y picos más graves).
>   Efectos › COMBATE › SONIDO: «Sonido del tajo» SÍ/NO y volumen (estelas.properties: `sonido`, `sonido.volumen`).

> **TF Client 1.3.69 (14 oct)** — el dueño: «mete estas animaciones (Punchy) a TF Client, que se puedan desactivar desde
> el apartado de Efectos del pad», «adapta esas animaciones completas sin errores» y trajo el permiso firmado.
> - **Punchy 2.8e entero, sin tocar, dentro del jar** (jar-in-jar de Forge): `build.gradle` (tarea `punchyJar`) baja el
>   jar oficial de Modrinth, **comprueba su SHA-512** (si no coincide, la compilación falla) y lo mete en
>   `META-INF/jarjar/`; `META-INF/jarjar/metadata.json` lo declara (`maven.modrinth:punchy-fpa`, `[2.8e,)`). Se eligió así
>   y no reescribirlo: son ~400 clases y 213 clips con su motor propio (modelos Bedrock, ~30 mixins, compatibilidad con
>   Iris, Better Combat...); rehacerlo a mano era meter errores. Comprobado en el código de Forge 47.3 (JarJarSelector
>   0.3.19): **si el jugador tiene Punchy suelto, Forge usa ese** y no hay mod duplicado. En el **servidor** no hace nada
>   (su arranque mira `FMLEnvironment.dist == CLIENT` y todos sus mixins son de cliente) y no tiene canales de red.
>   Comprobado también que no choca con lo nuestro: las estelas siguen (Punchy pinta el arma con `renderItem`, donde se
>   apunta la hoja, y no anula `swinging`); solo retrasa al abrir inventarios/cofres (para su animación), no el pad;
>   `ModelPart` solo lo toca en sus piezas; su tecla Inspeccionar es la I (las de su modo «tuning», solo si se activa).
> - **Efectos › ANIMACIONES** (`PadAnims`, del cliente como COMBATE): arriba el crédito con el logo de Punchy
>   (`punchy:icon.png`), ACTIVADAS/APAGADAS (todo Punchy) y MODRINTH (enlace con el aviso de Minecraft: `§link:` en
>   `PadViewPage.run`). GENERAL: velocidad (0,5–7), Inspeccionar (quita o pone la tecla). **CON QUÉ OBJETOS** (TF Client,
>   `AnimSettings`, config/tfclient/animaciones.properties): espadas, hachas, picos, palas, azadas, arco, ballesta,
>   escudo, tridente, caña, comida y bebidas, bloques, mano vacía y otros; un NO lo hace con un único mixin
>   (`PunchyConfigMixin`: `PunchyConfig.isItemBlacklisted` → true), que es lo que Punchy consulta en TODAS sus animaciones
>   y al pintar los brazos para dejar esa mano como Minecraft normal (su lista negra). MOVIMIENTO (andar, correr con fuerza
>   y ritmo, caer, nadar, trepar, gatear, planear, barca, movimientos nuevos), EFECTOS EN LOS BRAZOS (fuego, escarcha,
>   tiritar, barro, sudor, quemaduras, partículas, brillos), FÍSICA, OBJETOS EN 3D, MÁS (abre la pantalla de ajustes de
>   Punchy: su lista negra, packs...). Esos son los ajustes del propio Punchy (`PunchyBridge`, por reflexión con su API:
>   `snapshot` → cambiar → `applyAndSave` → `setAnimationSpeed`, lo mismo que su pantalla; config/punchy/punchy_config.json).
>   RESTABLECER deja de fábrica lo de la pestaña. La fila de pestañas cabe con ANIMACIONES (medido: 1024x768 a 4K).
> - Guía (Efectos) con ANIMACIONES y el crédito; `credits` en mods.toml; permiso en `META-INF/licenses/punchy-PERMISO.txt`.

> **TF Client 1.3.68 (14 oct)** — el dueño: «quita Colores vivos». **Colores vivos quitado** (el filtro de saturación
> de la 1.3.67): fuera `VividColors`, `GameRendererMixin`, `PostChainAccessor`, el shader `post/vivid.json`, su ajuste en
> `TrailSettings` y su fila en Efectos › COMBATE. Nada más.

> **TF Client 1.3.67 (14 oct)** — el dueño: «la creación de kits es un dolor de cabeza: dar objetos, asignar monedas…
> que lo pueda meter todo de un solo», «la ruleta es una mierda: animación lenta, sin sonidos», «la textura de las claims
> exagerada otra vez: simplistas, sin números ni figuras» y luego «ponle TF siempre con un contorno bonito», «no veo
> innovación: el pase sigue igual, la ruleta también», «mete estas animaciones (Afterswing) y efectos de combate,
> full configurables en la app de efectos», «a veces el pad va lento: más fluidez, sin quitar animaciones».
> - **Piedras de protección** (`tools/gen_claim_blocks.py`, 32x32): marco de piedra (oro en las de pago) y un panel del
>   color del nivel con **TF** en blanco con contorno oscuro. Sin números ni gemas.
> - **Kits en una sola ficha** (`PadAdminKits.sheet`): CREAR hace el kit con tu inventario (monedas aparte); en la ficha,
>   todo a la vista: objetos (MI INVENTARIO / ELEGIR), monedas y verdes con − + (saltos redondos; pulsar la fila = escribir
>   la cifra), cada cuánto « », quién puede « », precio − +, nombre, descripción, icono; PUBLICAR/OCULTAR arriba; ATRÁS,
>   PROBAR, COPIAR, BORRAR. `PadAdminUi.stepper/cycle/step`, `PadView.COMPACT` (filas de 22 de alto con icono de 16).
> - **Gachapón** (`PadGachaPage`, rehecho): tiradas de 2,1 s con aceleración, rebote y freno; tarjetas en perspectiva,
>   lengüeta que salta en cada tic (el tic sube de tono con la velocidad), estela, destello, chispas y temblor; resultado
>   con el título que golpea y el x5 que da la vuelta carta a carta; sonidos propios (`build_sounds.py --only`:
>   gacha_start/tick/stop/flip/win1-4, uno por rareza); barra de garantía por tramos; PREMIOS por rareza.
> - **TF Pass** (`PadPassPage`, rehecho): mapa de la temporada: camino que ondula sobre colinas en paralaje, medallones por
>   nivel (hitos con banderín), TÚ, COBRAR flotando, bocadillo con los premios al pasar el ratón, fanfarria y chispas.
> - **Estelas de arma** (idea y texturas de Afterswing, MIT: `META-INF/licenses/afterswing-MIT.txt`; el código es nuevo,
>   Afterswing es de Fabric 26.x): `ItemInHandRendererMixin` apunta en `renderItem` la punta y la base de la hoja en cada
>   fotograma (primera persona: en el espacio de la mano, se pinta al acabar las manos; tercera: al mundo con la rotación
>   inversa de la cámara, se pinta tras las partículas; solo entre AFTER_SKY y AFTER_ENTITIES y junto a su dueño, así el
>   muñeco del inventario no cuenta). La forma de la hoja y el color automático salen de la textura del arma (sirve con
>   armas de otros mods). 7 estilos (`TrailProfile`), mezcla brillo/translúcida/sólida, duración, ancho, opacidad, cola,
>   filo brillante, solo ataques (no al picar bloques), según la carga, al sacar el arma, otros jugadores y mobs con
>   distancia, qué armas, poco detalle, menos destellos.
> - **Colores vivos**: un filtro de saturación que salió en la 1.3.67. **El dueño ordenó quitarlo** (14 oct): quitado en
>   la 1.3.68.
> - **Efectos › COMBATE** (`PadCombat`): pestaña del cliente (si el servidor no la manda, `PadViewPage.set` la añade);
>   arriba la vista previa (`TrailPreview`: una espada dando tajos con la estela de los ajustes, con el mismo código del
>   juego; `PadView.TRAIL_PREVIEW`), abajo todo en filas compactas, RESTABLECER. Simulador: `tools/pad/sim/trail.py`,
>   `PAGES=p_combate,p_combate_neon,p_combate_brasas,p_combate_humo`.
> - **Fluidez del pad**: la fuente pixel pintaba **cada letra 12 veces (contorno) y cada vez era una llamada a la tarjeta
>   gráfica**; ahora cada rótulo es un lote (`PadFont` + `PadRenderTypes.font`). Todo el pad se pinta en lote
>   (`TFPadScreen.render` → `drawManaged`; `PadSkin.batching`/`done`): rectángulos, degradados, luces y textos seguidos van
>   juntos; para que nada se desordene, `GuiGraphicsMixin` vacía el lote antes de cada textura (blit) y los recortes ya lo
>   hacían. Además: círculo de `glow`/`disc` precalculado y la cabecera partida en líneas solo si cambia. Mismas animaciones.

> **TF Client 1.3.66 (13 oct)** — el dueño: «las texturas de las claim son exageradas, la primera y la segunda se parecen,
> tonos grises», «énfasis en el pad de admin: bien configurado, muy fácil, muy intuitivo», «los botones de las pestañas no
> se parecen: unos más distanciados, otros más pegados», «mejoras reales en el diseño de las apps», «lluvia de ideas de
> clanes» (está arriba, en Pendiente).
> - **Piedras de protección** (`tools/gen_claim_blocks.py`): piedra pulida con placa y remaches, marco de hierro (de oro en
>   las de pago: 250, 300 y 500), cada nivel con su gema de color (rombo en los lados, redonda arriba) y su tamaño escrito
>   («50», «100»…); 10 colores distintos (`ClaimTier` y `ClaimBlocks.colorForTier` iguales).
> - **Pestañas**: `PadSkin.tabRow`/`tabWidth` — todas las de una fila con el mismo ancho (el de la más larga) y 3 de
>   separación, en todas las apps (Comunidad, Música, las del servidor, las fichas oscuras); el simulador igual (`sk_tabs`).
> - **Pad de administrador**: portada con icono propio para APPS (`icon_apps`, `iconos4.apps_admin`) y una franja abajo
>   que explica qué configura cada app al pasar el ratón (`PadHomePage.adminHint`). APPS en tarjetas con el icono de cada
>   app (VISIBLE/OCULTA, MOSTRAR TODAS). AJUSTES por pestañas (VIAJES, EXPLORAR, JUGADORES, ECONOMÍA, SISTEMA) con unidades
>   (`PadConfig.Setting` tiene `group` y `unit`). TIENDA: categorías primero; cada categoría con ficha y sus objetos en
>   tarjetas (oro = lo que paga el jugador, verde = lo que le paga la tienda); AÑADIR OBJETO con el selector y luego pide
>   el precio de compra y el de venta; ficha del objeto con todos sus valores y QUITAR. **Arreglado**: las vistas no decían
>   su pestaña y EDITAR te devolvía a la portada de la tienda. VIAJES con PUNTOS (ficha: nombre, icono, MOVER AQUÍ, IR,
>   BORRAR) y WARPS. RECOMPENSAS: días y recompensas sueltas con objetos + monedas + monedas verdes (`PadAdminUi.items/coins`),
>   cada cuánto en su página. CAZAS y OFICIOS: valores con explicación y CANCELAR. Ningún «ponte el objeto en la mano»:
>   siempre el selector (`PadItemPicker`).
> - **Interruptores**: en las apps del servidor, una fila con un solo botón SÍ/NO o con el par SÍ + NO (Protección) se
>   dibuja como el interruptor animado de Ajustes (`PadViewPage.switchOf`/`drawSwitch`, `PadSkin.toggle`).

> **TF Client 1.3.65 (13 oct)** — capturas del dueño: «quiero pantalla completa, quita ese pixel» (el escalón del bisel en
> la esquina), «mira ese dibujo» (el tubo de neón con una muesca oscura arriba), «el diseño de las casitas, tienes 2
> castillos repetidos».
> - **Pantalla rectangular**: el cristal de marco.png tiene tres esquinas escalonadas (8x12 px de bisel hacia dentro) y un
>   saliente de 1 px arriba a la derecha. `build_pad.full_screen` deja el hueco como rectángulo completo (`SCREEN_BOX` =
>   205, 250, 1361, 795; el logo arriba a la izquierda se queda) y prolonga el borde del bisel recto, con la esquina a
>   inglete. `TFPadScreen.GLASS_T` = 250.
> - **Tubos de neón**: cada uno es ahora un rectángulo perfecto y uniforme (la caja de su núcleo) con una franja de luz en
>   el centro (neon_core); el dibujo original tenía sombras y muescas que, teñidas, parecían manchas.
> - **Paisaje**: una panorámica de 480x56 (más ancha que el cristal más ancho), **quieta y centrada**: un solo castillo
>   (torres con tejado en punta, homenaje con bandera, rosetón y puerta) y dos casitas nuevas (tejado de tejas con alero,
>   chimenea que asoma, puerta con marco y pomo, ventanas encendidas con cruz). El humo de las chimeneas lo anima el pad
>   (`TFPadScreen.CHIMNEYS` = las de `build_skin.CHIMNEYS`: si cambias las casitas, cópialas).

> **TF Client 1.3.64 (13 oct)** — el dueño probó la 1.3.63 (capturas): «parece que hay 2 pantallas una sobre otra»,
> «la línea, el efecto se sale: quiero una línea perfectamente dibujada», «que el reproductor reproduzca MP4, mayor calidad».
> - **Doble pantalla**: abajo y a los lados del cristal quedaba una franja lisa del color del cristal (95,212,252 en su
>   captura): lo de encima del color base (trama, sombra, paisaje) acababa en el rectángulo viejo de la pantalla, que era
>   1–3 px de pad más pequeño que el cristal de marco.png. Ahora `TFPadScreen.GX/GY/GW/GH` = **51, 62, 290, 137**: cubre
>   TODO el cristal (51,25–340,25 × 62,25–198,75) y lo que sobra lo tapa el marco, que va encima. Simulador igual.
> - **Neones de los temas**: el halo azul de alrededor de cada tubo, teñido, parecía una mancha que se salía. Ahora
>   `build_pad.frame_textures` separa los 8 tubos (`_neon_tubes`: manchas de núcleo largas y finas) de su halo:
>   neon_halo.png (el halo en el azul marino del canal, sin teñir), neon.png (el tubo opaco, nítido, y las gemas) y
>   neon_core.png. `TFPadScreen.drawFrame` pinta halo → tubo teñido → núcleo.
> - **MP4 / M4A / AAC**: JAAD (`net.sourceforge.jaad.aac` y `.adts`, dominio público, copiado de DV8FromTheWorld/JAADec;
>   arreglo en `ICStream.iq`: valores de 8191 se salían de la tabla y reventaba con MP4 de ffmpeg) decodifica el AAC
>   (LC y HE). El contenedor lo lee **`Mp4Audio`** (propio: el de JAAD leía bytes dos veces con RandomAccessFile y no sabe
>   de MP4 fragmentado): pista de audio aunque haya vídeo, moov al final, fragmentado (moof/tfhd/tfdt/trun), lista de
>   edición (recorta el retardo del codificador como ffmpeg), etiquetas de iTunes (©nam, ©ART, covr). `AacDecoder` (MP4,
>   salta a mitad decodificando y tirando el frame anterior; 5.1 → estéreo) y `AdtsDecoder` (.aac suelto). Medido
>   contra ffmpeg: **90,5 dB** en M4A 256/128 kbps, 48 kHz, MP4 con vídeo, fragmentado y ADTS, 0 muestras de desfase y
>   mismo largo; salto a 3000 ms exacto; 6 min a 256 kbps en 3,8 s. `AudioDecoder.Kind` MP4/AAC (sniff: ftyp/moov/mdat/
>   free/wide; ADTS = 0xFFF con capa 0, antes que MP3) y `Kind.ext()`. Límite de descarga: 250 MB. Guía al día.

> **TF Client 1.3.63 (13 oct)** — el dueño probó la 1.3.62: «la otra IA jodió la pantalla, mató el diseño, los colores
> mal pintados», «la pantalla de skins una reverenda mierda: una cabeza sin skin, vista previa rota que se sale; solo
> quiero ver mi skin, no lo que traigo puesto», «retoques reales a las GUIs y al fondo, todo menos los iconos», «que el
> reproductor lea bien canciones de más de 300 kb(ps), calidad de sonido», «no está claro cómo meter links: haz una app
> Guía con todas las instrucciones; con ella la interfaz queda simétrica».
> - **Marco**: frame.png vuelve a ser marco.png **tal cual** (bisel, brillos, esquinas escalonadas) con el cristal
>   transparente (`build_pad.frame_textures`, `--marco`). `TFPadScreen.render` pinta primero el fondo del cristal en todo
>   su hueco (`GLASS_L/T/R/B` = 205, 249, 1361, 795: hasta el bisel; color del tema en degradado en `glassBase`) y luego
>   el marco encima, que lo recorta con su forma exacta: sin franja de otro azul (la «doble pantalla») y sin estropear el
>   dibujo. Fuera `sin_bisel`, glass_shape/glass_fade y logo_over (el logo ya va en el marco, encima).
> - **Neones de los temas**: antes un color plano (neon.png = máximo del canal × color). Ahora neon.png lleva el brillo
>   ((g+b)/2: núcleo, halo y caras de las gemas) con alfa que se apaga en el halo, teñido; encima neon_core.png en blanco
>   (el núcleo encendido). El logo (x<400, y<340 de la imagen) no se tiñe.
> - **SKINS** (Armario): `PadSkinModel` dibuja SOLO la skin en 3D (`PlayerModel` horneado de `ModelLayers.PLAYER(_SLIM)`,
>   sin capas: nada de armadura, objetos, alas, capa ni cosméticos), mirando al ratón y respirando, recortado a su caja.
>   Imágenes: `PadView.SELF_MODEL` (lo que llevas), `tfclient:skinmodel/own` (la de tu cuenta: `SkinClient.bypass` deja
>   leer la de `PlayerInfo` sin la del Armario) y `tfclient:skinmodel/<huella>_f|_n` (`SkinServer.model`). Escaparate de
>   58 de alto + tarjetas normales (caben sin desplazar a 1080p); «Mi skin» → «De siempre» con la skin real.
> - **Música**: medido contra ffmpeg (`Dump` + SNR): JavaMP3 distorsionaba los MP3 de 128 kbps (14,5 dB, picos +50 %) y
>   se apartaba en los de 320 (41,8 dB). Ahora **todos** los MP3 van con JLayer (52,9 dB en 320/128/VBR/48 kHz/MPEG-2: la
>   misma señal, 0,02 dB más bajo), ~110× tiempo real. Al saltar a mitad se decodifican y tiran los 2 frames de antes
>   (reserva de bits: sin chasquido). Fuera `pad/music/mp3/` (JavaMP3), `Mp3Decoder` y su licencia. Límite: 80 MB.
> - **App Guía** (`PadGuidePage`, icono `iconos4.guia`: libro abierto con «?» y marcapáginas): Primeros pasos, las 23
>   apps y Ajustes; cada tema con secciones, pasos numerados, puntos y CONSEJO, y ABRIR para ir a la app. El texto está
>   en el Java (si cambia una app, cambia su tema). 24 apps: 8 + 8 + 8. El staff la puede apagar (APPS del admin).
> - **Fondo** (fuera en la 1.3.107): paisaje de pixel art abajo del cristal (`TFPadScreen.landscape`, `build_skin.land_*`: montañas con nieve,
>   colinas con robles y abetos, el castillo y casitas), tres capas del color del tema que se deslizan (paralaje),
>   delante de las nubes. **Barra de estado** de cristal escarchado detrás del título, la hora y las monedas.

> **TF Client 1.3.62 (13 oct)**:
> - **«Doble pantalla» (la de verdad)**: era el diseño de marco.png: un anillo de bisel azul oscuro de 15–20 px alrededor
>   del cristal (con su brillo y la esquina escalonada) que parecía una segunda pantalla detrás. `build_pad.sin_bisel`
>   lo pinta del color del cristal (flood fill desde el cristal por los píxeles azulados hasta la línea negra; no toca el
>   logo ni las gemas) al generar frame.png. El fondo animado se recorta en el borde real (`glassBase` deja el recorte).
> - **Temas del pad** (Ajustes › Tema del pad, `PadSettings.THEMES`): Cielo, Rosa, Lavanda, Menta, Atardecer, Oro, Fresa,
>   Noche. El cristal con su forma exacta (`glass_shape.png` del color de abajo + `glass_fade.png` del de arriba) y los
>   neones/gemas del marco teñidos (`neon.png`), de `build_pad.glass_masks`. (1.3.108: el cristal limpio, sin el papel de
>   monogramas «TF», sin el paisaje y sin el emblema de la 1.3.107.) Se guarda en pad-cliente.properties (`tema`).
> - Pestañas de Comunidad/Música y etiquetas oscuras (SONANDO, RULETA) del mismo alto (17) y letra a la misma altura.
> - Armario › SKINS: escaparate con tu modelo 3D grande (cabecera de 96 con `PadView.SELF_MODEL`) y tarjetas de galería.
> - Música: **cualquier MP3** (MPEG-1 con JavaMP3; MPEG-2/2.5 con JLayer 1.0.1, `javazoom.jl.decoder`, LGPL 2.1, copiado sin
>   cambios + `resources/javazoom/jl/decoder/*.ser` y la licencia; `JLayerDecoder`, con la duración de frame corregida para
>   MPEG-2.5) y las canciones metidas a mano en `.minecraft/tfclient/musica` (y subcarpetas) se añaden solas al abrir
>   Música (`MusicLibrary.scanFolder`: título/artista/portada de sus etiquetas o el nombre del archivo).

> **TF Client 1.3.61 (13 oct)** — 10 quejas del dueño tras probar la 1.3.60 (primera versión publicada como Release):
> 1. «Doble pantalla»: la sombra suave de abajo del cristal se cortaba en GY+GH y la franja de cristal del marco de
>    debajo quedaba más clara; ahora la sombra llega al borde real (`TFPadScreen.glassBase`).
> 2/5. Pestañas: la elegida de Música (playlist con ×) se dibujaba con la geometría vieja; letra de las pestañas grandes
>    centrada (y + 4).
> 3. Oficios en tarjetas de galería (`PadJobs`: `.gallery()`) y retratos anchos como foto (`PadSkin.photo`/`wide`); en la
>    cabecera del oficio también en grande.
> 4. NO elegido en rojo (`PadClaims.answer`, botones SÍ/NO del admin) e interruptores apagados en rojo (`PadSkin.toggle`).
> 6. Skin puesta: el modelo 3D real del jugador (`PadView.SELF_MODEL`, `PadViewPage.selfModel` con
>    `InventoryScreen.renderEntityInInventoryFollowsMouse`); vale premium y no premium (dibuja al jugador como lo ve el juego).
> 7. Pistas de los campos: se encogen hasta caber en vez de «…» (`PadSkin.field`); pistas largas acortadas.
> 8. Aviso flotante: escala normal (no la grande), 2 líneas, 1,5–2,6 s, ya no se para con el ratón encima.
> 9. Recompensas: fuera OTRAS (el kit diario adoptado solo); pestaña REGALOS = buzón (`PadPlayers.gifts/claimGifts`,
>    `regalos.json` jugador.buzon): los regalos de Jugadores ya no van directos al inventario, se recogen ahí (RECOGER /
>    RECOGER TODO); lo «pendiente» de antes pasa al buzón al entrar.
> 10. Filas: icono al doble (32) en pastilla del color de la fila (`PadSkin.tile`, ICON_BOX 36). Simulador al día.

> **TF Client 1.3.60 (13 oct)** — repaso de diseño de las apps (punto 5 del dueño) y retratos de oficios:
> - Oficios: retratos de 64x39 (`job_images.py`, BOX 96–160): se ven las manos y la herramienta. `PadSkin.image`
>   ya no deforma: cabe en maxW x maxH sin perder proporción (las tarjetas dejan que la imagen use su ancho).
> - Filas (`PadViewPage.drawRow`): lavado del color de la fila que se apaga a la derecha, luz detrás del medallón, brillo
>   al pasar el ratón y flechita «›» en las que se puede entrar (sin botones). Campo de texto nuevo (`PadSkin.field`:
>   marco del color de la app, lápiz, cursor de su color). Botones desactivados en cristal claro (antes azul noche:
>   parecían rotos). Bajo una lista corta, el icono de la app grande y suave con destellos (`PadSkin.listFooterArt`).
>   Simulador al día.

> **TF Client 1.3.59 (13 oct)**: «pantalla doble» del pad: el cristal de frame.png es algo más grande que GX/GY/GW/GH
>   (en la imagen: x 205–1360, y 250–794; la pantalla cubría 208–1359 × 256–783) y su azul asomaba debajo de la
>   pantalla, sobre todo abajo (11 px de imagen). `TFPadScreen.glassBase` pinta la trama del cristal en todo ese hueco
>   (GLASS_L/T/R/B, contando lo que se alarga el marco), recortado a píxeles reales. El simulador no lo refleja (su capa
>   del cristal mide justo GW×GH).

> **TF Client 1.3.58 (13 oct)**:
> - **Armario con skins** (pestaña SKINS, la primera): colección por jugador (máx. 16) en
>   `<mundo>/tfclient/skins/<uuid>/` (skins.json + `<huella>.png`, 64x64, ≤16 KB, normalizada: `SkinImages`). Importar
>   de `.minecraft/tfclient/skins/` (ABRIR CARPETA / ACTUALIZAR / IMPORTAR) o de un jugador de Minecraft por su nombre
>   (`SkinMojang`, en el servidor); exportar a `.minecraft/tfclient/skins/exportadas/`; brazos finos/normales,
>   renombrar, borrar (con confirmación). La puesta la ven todos los que tienen el mod: canal `tfclient:skins`
>   (`SkinNet`: Active/Want/Png/Scan/LocalList/UploadAsk/Upload/Export), mixins `AbstractClientPlayerSkinMixin` y
>   `PlayerInfoSkinMixin`. Pantallas en `PadWardrobe` (`skinsView`, pestañas «skins», «skins:ver:<id>», «skins:nueva»).
>   Ya no sale «Armario no disponible» sin la web conectada ni «solo estético».
> - **Pad de administrador, KITS**: asistente paso a paso (`PadAdminKits`: nombre → icono → objetos → monedas y monedas
>   verdes como números → cada cuánto → quién/precio → revisar y publicar; borrador si se deja a medias; ficha con
>   CAMBIAR, COPIAR, BORRAR, PROBAR). Las monedas como objeto (oro y verdes) nunca se dan: `TFCoins.credit` las suma al
>   monedero en kits, recompensas, premios y tienda. Textos de ajustes (`PadConfig`) en lenguaje claro.
> - Pendiente: más diseño en las apps.

> **TF Client 1.3.57 (13 oct)** — pedidos del dueño tras probar la 1.3.56:
> - **Monedas en kits/recompensas/premios**: `PadKits.give` ya no da Fantastic Coin ni monedas verdes como objeto:
>   las suma al saldo (`TFEconomy.give` / `PadGreen.add`); en la vista del kit salen como «+N».
> - **Barras de desplazamiento**: el asa se agarra y sigue al ratón, clic en el carril salta ahí (`PadUi.grabbable` +
>   `PadUi.BARS`, lo maneja `TFPadScreen`); arrastrar sobre la lista ahora va en el mismo sentido que la barra.
> - **Entrada de las apps** al abrir el pad ~40 % más rápida (`PadHomePage.entrance`).
> - **Oficios**: el muñeco de cada oficio del pack Medieval Jobs (`tools/pad/job_images.py <carpeta textures del
>   pack>` → `textures/gui/pad/jobs/<fondo>.png`, 39x39) en las tarjetas y la cabecera (`PadJobs.portrait`).
> - **Pestañas nuevas también en Comunidad y Música** (`PadSkin.tabCompact`, en el sitio de las de carpeta).
> - Pendiente (pedido del dueño): skins en el Armario (importar/exportar/guardar), auditoría y rehacer el pad de
>   administrador (creador de kits), «pantallas duplicadas» (falta saber cuáles), más diseño en las apps.

> **TF Client 1.3.56 (13 oct)** — el dueño probó la 1.3.55 («me encanta el nuevo diseño de las apps y fondos») y pidió:
> - **Avisos del pad** (`TFPadScreen.showNotice`): pastilla pequeña abajo en el centro (no tapa cabecera ni pestañas),
>   color por tipo (verde ✓ éxito, rojo ! error, azul i el resto, por cómo empieza el texto), entra en 0,22 s, dura
>   2,2 s (hasta 3,6 s si es muy largo), clic = cerrar, con el ratón encima se queda; uno solo a la vez.
> - **Pestañas** (`PadSkin.tab`/`tabRow`, todas las apps del servidor y Gachapón): pastillas de 17 de alto con margen,
>   texto centrado, hueco de 3 sobre la ventana; la elegida en el color de la app con flechita hacia la cabecera.
>   Comunidad y Música siguen con `PadUi.tab`.
> - **Icono de Oficios**: el pico de diamante de verdad (`Items.DIAMOND_PICKAXE` a 2×, `PadSkin.icon32`).
> - **Inicio del pad animado** (`PadHomePage.ambient`): nubes en dos capas, rayos de sol, brillo que late detrás de la
>   rejilla y motas que suben; los iconos entran uno tras otro con rebote (`PadPage.shown()`) y un brillo los cruza
>   cada 9 s. Todo quieto con animaciones apagadas. Repaso: botones con sombra en vez de contorno, texto marrón en
>   los dorados, insignia «TÚ» del Pass más alta, notas de Ajustes centradas.
> - **Comunidad**: una reacción por jugador y foto (otro emoji la cambia, el mismo la quita; lo valida el servidor);
>   el selector y las reacciones ya no enseñan nombres ni atajos.
> - **Web**: 228 vistas previas de skills rehechas (39 clases) con `tools/skills.py --vistas`.

> **TF Client 1.3.55 (12 oct)** — pedidos del dueño:
> - **Chispitas de protección**: «muy grandes y muy rápidas» → 0,022 (grandes 0,032), salen a 0,042–0,06/tick y frenan
>   (`friction` 0,96 en `MagicDustParticle.spark`), viven 28–38 ticks con entrada 4 y salida 10: flotan ~1 bloque.
> - **`/tf claim give <jugadores> <nivel> [cantidad]`** de vuelta (se perdió en la 1.3.33 al dejar solo /tf web y
>   /tf reload; `ClaimCommands.command()` ya no se registraba). Operadores (nivel 2). También en `/tf web claims give`.
>   Código: `ClaimCommands.giveCommand()`.
> - **Letras del menú**: fuera la textura por franjas con tijera (`gradientLetters`/`texture`): en el juego se veía
>   «toda la letra oscura y solo el pie claro» y pixelada. Ahora color liso (`TFMenuButton.fill`: oro 0xF0CF7E que
>   respira hacia 0xFBE7B4 en Tierras Fantásticas, crema 0xEAE4D8 en las demás) con el mismo contorno fino. Sim igual.
> - **Pad rediseñado** (todas las apps menos Música; Comunidad va con los emojis, abajo): `PadSkin.java` (cabecera por
>   app con color, eslogan, rayas que se mueven y brillos; fichas con luz, sombra y elevación; seleccionada con borde
>   de oro que late; cabeceras grandes con rayos; filas con franja de color; botones que suben/bajan; barras con
>   reflejo; vacíos con ilustración). Fondo del pad animado sin quitar el azul: luces desenfocadas que suben, un haz
>   cada ~13 s, destellos y trama de cristal (el logo TF se repinta encima con `logo_over.png`). Todo respeta
>   `PadSettings.animations`. Efectos: galería de kills con la **imagen real de cada efecto** (`textures/gui/pad/fx/
>   kill_<id>.png`, 50) y clases de skill con su imagen (`class_<id>.png`, 39), de `tools/pad/fx_images.py` (lee
>   `tierras-fantasticas/config/vfx.json` y `skills.json`); `PadView` filas/celdas llevan un id de imagen opcional
>   (**cambió el formato de red de PadView**). Ranking con podio, Monedero/Mi Rango con cabecera grande, Ajustes con
>   interruptores y deslizador. Texturas del skin: `tools/pad/build_skin.py` (build_pad.py lo llama). Simulador al día
>   (`tools/pad/sim/skin.py`). Solo visto en el simulador: **probar en el juego**.

> **Emojis de Comunidad (pad) — el dueño: «auméntalos, emojis de Discord vanilla, muchos, hasta animados»**:
> - **1.897 emojis de Discord** (Twemoji 15.1, los mismos de Discord; gráficos **CC BY 4.0**) + **95 animados**
>   (Noto Animated Emoji de Google, **CC BY 4.0**), en atlas: `assets/tfclient/textures/gui/emoji/` (`static_*.png`,
>   `anim_*.png`, PNG de paleta, ~2,2 MB en total) + `index.json` (id = atajo de Discord, atajos en inglés y español,
>   categoría, nombre en español, celda, fotogramas). Atribución: `LICENSE-twemoji.txt`, `LICENSE-noto-animated.txt`,
>   `CREDITS.txt` en esa carpeta (hay que mantenerlos). Se rehacen con `python3 tools/pad/make_emojis.py` (baja a
>   `tf-client/build/emoji-src`, fuera de git; `--offline` reusa lo bajado). Atajos en español a mano:
>   `tools/pad/emoji/alias_es.json`; lista de animados: `tools/pad/emoji/animated.json`.
> - Código: `PadEmojis` (índice, búsqueda, validación; también en el servidor), `PadEmojiDraw` (dibujo nítido por hoja
>   reducida en segundo plano, animados por la hora, texto con `:atajo:`/emoji tal cual), `PadEmojiPicker` (selector
>   tipo Discord: buscador, pestañas por categoría, Frecuentes en `tfclient/emojis_frecuentes.json`, pie con el
>   emoji, Mayús+clic = sigue abierto). Reacciones con **cualquier** emoji, varias por jugador, 20 distintas por foto;
>   el servidor valida los ids. Las reacciones viejas (risa/wow/triste/fuego/top) se pasan solas a joy/open_mouth/cry/
>   fire/thumbsup al cargar `comunidad.json`. **Cambió el formato de red de Feed/Act**: cliente y servidor juntos.
>   Fuera `emo_*.png` y `tools/pad/emoji/*.png` viejos. Simulador: `tools/pad/sim/comunidad.py` (lo llama pages.py).

> **Web (12 oct)**: el despliegue de Cloudflare del PR #77 falló (el código compila: `npx wrangler deploy --dry-run`
> en una copia limpia de main va bien, 5.726 archivos); se reintenta con un push nuevo. Si vuelve a fallar, mirar el
> registro en el panel de Cloudflare (Workers › tierras-fantasticas › Builds). Ojo con el tamaño del repo (~1,8 GB por
> los jar de `descargas/` en el historial).

> **TF Client 1.3.54 (12 oct)** — pedidos del dueño:
> - **Menú**: letras con textura (`TFMenuButton.texture`: bisel de brillo arriba, sombra abajo, grano fino), un poco
>   más arriba (`MENU_TEXT_UP` 4,6) y más juntas (`Tracked`: letra a letra, `TRACKING` 0,45 px de glifo, ancho exacto
>   con `getSplitter().stringWidth`). Simulador al día.
> - **Chispitas de protección**: más pequeñas (0,05/0,075), 7 por tick, ±1,6 bloques, suben ~2 bloques a 0,11–0,16/tick.
> - **Efectos de kill** (auditoría de los 50, sin los packs originales a mano: solo lo generado): el modelo se quitaba
>   un tick tarde y enseñaba la pose inicial (49/50) → `age >= life` como MythicMobs; armadura/objetos de mano
>   flotando → parte oculta a escala 1e-4; mobs no humanoides siguen el torso del muñeco; fuera el humo de muerte de
>   Minecraft a mitad del efecto (`LivingEntityPoofMixin`, evento 60; solo sale al final en espectral y jade); jugador
>   que reaparece pronto ya no queda invisible. `build_vfx.py`: todos los alias de partículas de MM y repeat sin
>   repeatInterval como el motor. Pendiente: rehacer con `build_vfx.py` cuando el dueño vuelva a pasar los zips.
> - **Skills** (auditoría del motor contra MythicMobs): `?!condición` iba al revés en el juego (111 líneas, 11 clases);
>   modelos de proyectiles/órbitas 1,35 bloques más altos (bulletyoffset); totems con `c=1` solo daban al más cercano;
>   onHit una vez por paso con todos los objetivos; misiles daban a mobs (`hitnonplayers`); condiciones de parada sobre
>   el objetivo; puntería, METEOR, `eso/so`, @targetblock, leap/lunge con la parábola de MM; inmunidad de 4 ticks por
>   uso (los rayos ya no hacen miles de daño); una skill que no hace nada no gasta el enfriamiento; `<caster.damage>`
>   ≥ 7; `<damage.modifier>`. sim.py igual que Java. Resultado: 190/215 activas hacen daño (7 que no: utilidad), audit
>   0 fallos, check_assets 0, check_mod_items 0, sim --todas 0 problemas, leak2 estable. Texturas: 0 fallos (1.870 PNG).
>   Pendiente: rehacer las vistas previas de la web (proyectiles a la altura nueva).
> - **Web**: el personaje 3D ya no parpadea en el hombro/cintura/piernas al girar (z-fighting: cada parte crece un
>   pelín según su rango, `fitDepth` ajusta near/far, `centroid` en la UV; `wardrobe.js`, `viewer.js`).

> **TF Client 1.3.53 (12 oct)** — el dueño: «Mundo local pegado a Tierras, mira el espaciado vertical»; «las flechas
> son una mierda, solo que salten partículas al acercarme al borde». Menú: el hueco vertical se mide entre lo que se
> VE de cada fila (gemas y tréboles incluidos: `vis` en menu_main.json) y es el mismo en todas (`VIS_GAP` 30 de la
> lámina); las filas se apilan con la escala real de cada placa y lo que no cae en píxel de interfaz se corrige al
> dibujar (`SHIFT_Y`). Simulador: huecos iguales ±1 px real. Protecciones: fuera las flechas; a ≤4 bloques de un lado
> saltan **chispitas** sueltas del color de la zona alrededor del jugador (±2,5 bloques, de los pies a +2,2), que suben
> un poco y se apagan en 10–15 ticks; 4 cada 2 ticks como mucho (menos lejos y con partículas reducidas/mínimas).

> **TF Client 1.3.52 (11 oct)** — el dueño: «¿eso es simétrico? mira las gemas», «botones pegados, sigue un milímetro
> asimétrico de punta a punta». Simetría exacta:
> - `gen_menu_buttons.py`: cada placa es su mitad izquierda + su reflejo (eje en la gema del centro); la lámina original
>   no era simétrica por unos píxeles. **Opciones = Salir y WhatsApp = Discord en dibujo** (`recolor`: la placa de
>   Salir/Discord con el hueco pasado al gris/verde de la lámina; marco, tréboles y filo intactos).
> - `TFMenuPlates.Placed`: las zonas lisas de cada lado de la gema del centro se estiran por separado para que la gema
>   caiga en el centro exacto del botón (`Plate.anchor`).
> - `mainLayout`: anchos pares, centro de verdad de la ventana (`getWidth() / 2s`; el ancho de interfaz se redondea) y
>   `mainShiftX` (medio píxel) al dibujar: todas las gemas en el mismo píxel y bordes izq/der iguales (simulador:
>   silueta = su espejo en el 99–99,6 % de los píxeles; lo demás, las letras).
> - Más aire: `GROUP_GAP` 14 entre botones de una fila; `GAPS` 96/96/72/80 entre filas (los tréboles ya no se tocan).

> **TF Client 1.3.51 (11 oct)** — el dueño (con captura): botones solapados, raya/sombra oscura en las letras de
> Tierras, botones gigantes con texto pequeño, sin simetría, botones que pierden la animación (Discord); partículas de
> las protecciones desordenadas; en la tienda «128 unidades» con un 64 en el icono.
> - **Menú** (`TFMenuButton.mainLayout`): filas separadas lo justo para que las gemas no se toquen (`GAPS` 84/86/62/46
>   en la lámina); cada botón **a la medida de su texto** (+ `PAD` 0,4 del alto a cada lado; `TEXT_SIDE` del json: 0,9 en
>   Tierras, 1,25 en los azules por las enredaderas) y **simetría en espejo** (`GROUPS`: Opciones = Salir, WhatsApp =
>   Discord, Web en medio; azules iguales y ≥ 62 % de Tierras); cada fila centrada. Contorno de las letras fino (5 %) y
>   suave (`OUTLINE_SHADE` 0,55, oro 0,72 del color de la placa), oro más claro abajo. El foco solo enciende el botón con
>   teclado (`getLastInputType().isKeyboard()`): tras un clic ya no se queda encendido.
> - **Protecciones** (`ClaimOutlineRenderer`, `MagicDustParticle`): líneas de puntos fijos cada 0,5 bloques alineados
>   al mundo (rombo de la vara del End, color de la zona avivado `vivid()`), nodo grande cada 2 bloques y una onda de
>   brillo que recorre la línea; menos densidad lejos (≤12 cada 0,5, ≤22 cada 1, ≤32 solo nodos). A ≤5 bloques de un
>   lado: **flechas «┘» de 7 bloquecitos que bajan en diagonal** (0,05 bloque/tick hacia delante y hacia abajo) en una
>   rejilla fija del mundo, parpadeo común de 14 ticks, más vivas cuanto más cerca. Tandas cada 8 ticks; tope ~1.750
>   motas vivas en el peor caso. Solo sprites glitter; nunca una mota sin sprite.
> - **Tienda** (auditoría completa): el número del icono es el real (128, 1.024; el pad manda la cantidad aparte del
>   ItemStack: `PadView.writeIcon/readIcon`); «Faltan N» exacto hasta 9.999 y redondeado hacia arriba después; precios
>   exactos hasta 9.999.999; límite diario en unidades reales y por delante del dinero; venta con «Faltan N» y
>   «LÍMITE DE HOY»; «Puedes ganar» igual que VENDER TODO; campo OTRA CANTIDAD acepta «1.024» y explica el error;
>   mensajes nuevos (`limiteCompraQueda`, «te faltan …», `{unidades}`), singular/plural, puntos de miles; tienda de cofre
>   con el permiso de categoría y sus textos (parece sin uso, arreglada igual). Sim del pad al día.

> **TF Client 1.3.50 (11 oct)** — **crash arreglado**: `MagicDustParticle.refreshSprites` usaba
> `Minecraft.getTextureAtlas(LOCATION_PARTICLES)` y el atlas de partículas no está en el ModelManager (NPE en
> `AtlasSet` al entrar al mundo con contorno de protección). Ahora lo pide al TextureManager (`TextureAtlas.getSprite`),
> con try/catch; si no hay sprites no se crean motas, y una mota sin sprite se quita sola. Menú (el dueño: «una línea
> negra delante del botón de Tierras, la letra demasiado grande, la animación de la línea blanca horrible, menos
> contraste»): fuera la sombra de las letras (era la raya oscura debajo de Tierras Fantásticas), fuera las líneas de
> luz (`TFMenuPaint.sweep` ya no se usa en el menú) y el brillo de las letras; el oro «respira» despacio
> (`GOLD_BREATH_MS` 3,8 s) con la luz cálida detrás; con el ratón la placa se ilumina menos (16 %) y sube menos.
> Letra más pequeña (`CAP_FILL` 0,5: ×1 a 1080p), blancas más apagadas (E6E0D3→BDB5A6), oro más suave, contorno del
> color de la placa también en el oro (`GOLD_OUTLINE_SHADE` 0,5) y más fino. «Tierras Fantásticas» con su propio tamaño,
> más grande (`TF_CAP_FILL` 0,52 de su placa, más alta: ×1,5 a 1080p, ×1,33 a 720p; `tfTextSize`).

> **TF Client 1.3.49 (11 oct)** — el dueño: «las letras blancas tienen un contraste muy alto, que no se vean más
> contrastadas que su fondo». Las blancas del menú pasan a blanco hueso (`WHITE` F3EEE3→CFC8BB; con el ratón, casi
> blanco), su contorno es más fino (7 % de la letra) y del **color de su placa oscurecido** (`tint` en
> menu_main.json, mediana del hueco del texto, × `OUTLINE_SHADE` 0,3) en vez de negro, y la sombra más tenue (30 %).
> Las doradas de Tierras Fantásticas igual.

> **TF Client 1.3.48 (11 oct)** — el dueño: «las letras siguen siendo delgadas y pequeñas, el icono de Discord no se
> parece, ¿por qué el logo más pequeño? debe ser más grande que los botones». Menú de inicio:
> - Letra **Titan One** (OFL, `font/titanone.ttf` + `titanone_1..12.json`; quitada New Rocker): gruesa y redonda, de
>   botón de juego. Contorno grueso (8,5 % de la letra: 16 direcciones + 8 a media distancia, `TFMenuButton.ring`),
>   sombra debajo y degradado en todas (oro en Tierras Fantásticas, blanco a gris azulado en las demás;
>   `gradientLetters`). Llena hasta el 70 % del hueco de la placa (`CAP_FILL`).
> - **Iconos de verdad** (`textures/gui/menu_icons.png`, `tools/gen_menu_icons.py`): logos de WhatsApp y Discord
>   (Simple Icons, CC0) y globo de Lucide (ISC) en Web, con contorno y sombra como las letras.
> - **Emblema grande**: se queda al menos con el 44 % del alto (`LOGO_FRAC`; 113 de alto a 1080p, antes 77); filas más
>   juntas (`ROW_GAP` 50) y columna más ancha (`SPAN` 1,7) para que los botones no pierdan tamaño.
> - Música: lo de «Favoritas» era solo un nombre de ejemplo del simulador (`tools/pad/sim/pages.py`; cambiado); en el
>   juego no hay pestaña de favoritos. Pegar un link con una playlist abierta la guarda en esa playlist (1.3.47).

> **TF Client 1.3.47 (11 oct)** — 7 pedidos del dueño:
> 1. **Pad Música, botones de fila** (`PadMusicPage.rowButton`): 14x14 redondeados con borde de su color, cara clara y
>    dibujo de 8x8 con trazo de 2 justo en el centro (antes 5x5 descentrado); al pasar el ratón, llenos de su color.
>    El «+» de las pestañas es una cruz dibujada y centrada.
> 2. **Contorno de las protecciones** (`claims/client/ClaimOutlineRenderer` + nuevo `MagicDustParticle`, solo
>    cliente): ya no es una caja de líneas con tapa; es polvo mágico del color de la protección (destellos y polvo
>    vanilla teñidos, brillo propio): líneas verticales en las esquinas (16 abajo a 24 arriba del jugador) y por el
>    suelo en cada lado, hasta 32 bloques. A ≤5 bloques de un lado: «cortina» de 12 de ancho en franjas diagonales
>    que se deslizan, todas parpadeando a la vez (pulso de 14 ticks), más densa cuanto más cerca. Tope: 14 motas/tick
>    las líneas + 10 por lado cercano (máx. 2), ×0,6/×0,3 con partículas reducidas/mínimas. Se borran al salir del
>    servidor. `ParticleBorder` (relleno opcional del dueño, del servidor) sin tocar.
> 3. **Avisos del pad** (`TFPadScreen.drawNotice`): pastilla arriba, bajo la barra (no tapa los controles), del ancho
>    del texto, entra y se apaga sola en 2–3,5 s. Textos del altavoz cortos.
> 4. **Playlists**: TODAS es solo ver/oír todo (sin el + de cada fila ni su menú). En una playlist: cabecera
>    «N canciones» + ELEGIR CANCIONES (modo elegir: toda tu música con casillas ✓/+; LISTO), el − la saca, y el link
>    pegado abajo se baja **y entra en esa lista** (`MusicDownloader.download(link, listId)`, `MusicLibrary.addToList`).
>    Al crear una lista se abre el modo elegir.
> 5. **Pestañas de playlists**: TODAS + las que quepan (nombre ≤58 px; «LISTA» si no tiene letras dibujables) y, a la
>    derecha y fijas, flechas ‹ › por páginas y el +; la rueda sobre las pestañas también pasa.
> 6. **Tienda en unidades** (`PadShop`, `TFShopMenu`, `PadAdmin`): «16 unidades» / «1 unidad», nunca «lote»; el campo
>    «otra cantidad» pide unidades (múltiplo del paquete; si no, aviso y no compra).
> 7. **Menú de inicio**: letra **New Rocker** (OFL, `font/newrocker.ttf` + `newrocker_1..12.json`,
>    `OFL-NewRocker.txt`) en las placas; todas las filas del mismo ancho (`ROWS`, `SPAN` 1,42 × Tierras Fantásticas):
>    una columna limpia. Cinzel sigue en las demás pantallas. `tools/menu_sim.py` y `tools/pad/sim/pages.py` al día.

> **TF Client 1.3.46 (11 oct)** — el dueño: «el de Tierras Fantásticas debe ser dorado, que las letras resalten más
> y tengan efecto». Letras de Tierras Fantásticas en **oro** (`TFMenuButton.goldLetters`: degradado de oro pálido a
> ámbar en franjas de 2 px reales con `RenderSystem.enableScissor`, contorno marrón oscuro), **luz cálida que late**
> detrás (`goldGlow`, menu_glow aditivo) y un **brillo que cruza las letras** cada 4,2 s y al entrar el ratón. Todas las
> letras resaltan más: contorno de ½ píxel de interfaz (borde entero de un cuadrado de `ro` px reales) y sombra más
> marcada. Los demás textos siguen en blanco cálido. `tools/menu_sim.py` al día (escena `_destello` muestra el brillo).

> **TF Client 1.3.45 (11 oct)** — el dueño: «la tipografía no es congruente, es demasiado pequeña; el color de Mundo
> local no es amarillo». Menú principal: **un solo tamaño de letra para todos los botones** (`TFMenuButton.textSizeFor`:
> el mayor con el que cada texto + icono cabe en su placa y las mayúsculas, 5,3 de alto a escala 1, no pasan del 56 %
> del hueco interior de la placa más baja; ~1,5 a 1080p, 1,33 a 720p) y **un solo color** (blanco cálido `FFF3DC`;
> con el ratón blanco puro, ya no dorado/amarillo; también en `TFButtonTheme`). Nuevas `font/cinzel_7..12.json` para
> que cualquier tamaño se dibuje 1:1 (nítido); el icono crece con la letra. Fila de abajo más ancha (WIDEN 1,5),
> K_MAX 0,15, LOGO_MIN 80, hueco del texto a 0,2 del alto en las placas pequeñas.

> **TF Client 1.3.44 (11 oct)** — el dueño mandó dos láminas: la del menú principal (con el layout) y la de los
> botones de ajustes/navegación: «corta bien, escribe bien, diseña bien, anima bien», sin píxeles blancos en el
> contorno y con icono en Web. `tools/gen_menu_buttons.py <lámina menú> <lámina ajustes>` (las láminas NO van en el
> repo; traen el fondo de cuadritos pintado: se quita rellenando desde el borde, se suaviza el contorno, se borra lo
> claro a ≤2 px del borde y los transparentes toman el color del vecino) genera `menu_main.png` (8 placas, mipmaps) +
> `menu_main.json` (uv, cuerpo, hueco del texto, zonas lisas estirables, gemas, faroles y sitio en la lámina),
> `menu_glow.png` y `button_slice.png` (piedra con gemas ámbar: normal/ratón/desactivada). **Menú principal**
> (`TFMenuButton.mainLayout` + `TFMenuPlates`): la composición de la lámina escalada para caber bajo el emblema
> (Tierras Fantásticas madera con gemas y faroles; Mundo local y Mods azules; Opciones gris | Salir rojo; WhatsApp
> verde | Web morado | Discord azul, iconos en los tres); solo se estiran las zonas lisas (tréboles, gemas y faroles
> conservan su forma). Animación: con el ratón sube y crece un 2 %, se ilumina (placa sumada), la cruza una línea de
> luz y las gemas brillan; al pulsar se hunde y oscurece; faroles que titilan, chispas en las gemas cada ~5 s y
> destello en Tierras Fantásticas cada 6,5 s. Texto Cinzel nítido con contorno oscuro (Tierras Fantásticas a 1,25).
> **Demás menús** (`TFButtonTheme`): placa de piedra, puntas a la altura del botón y centro cortado (sin estirar),
> fundido del ratón por botón. `tools/menu_sim.py` al día. Quitados `gen_buttons.py` y `menu_buttons_N.png`.

> **TF Client 1.3.43 (11 oct)** — el dueño rechazó los botones en pixel art hechos a mano («la peor basura de
> diseño… busca texturas bonitas en internet»). Ahora los botones del menú de inicio son las **tablas de madera con
> marco de metal** del "Medieval Game Button Pack" de pzUH (**CC0**, https://opengameart.org/content/medieval-game-button-pack;
> el pack no va en el repo, solo lo convertido). `tools/gen_menu_buttons.py <Button.png del pack>` las pasa a
> `textures/gui/menu_buttons_N.png` (N = escala de interfaz 1..6, placas de 200N x 20N, centro repetido en espejo):
> marco plateado en reposo, dorado con el ratón (fundido), el principal dorado, Salir rojo, pulsado más oscuro,
> desactivado gris. `TFMenuButton.plate` dibuja la hoja de la escala actual 1:1 en píxeles reales (7 px de interfaz de
> cada lado, centro cortado), nítida. Texto Cinzel claro (dorado en el principal) con sombra marrón, centrado en la
> madera (`WOOD_CENTER` 0.46: el marco de abajo es más grueso). Mismo orden: Tierras Fantásticas · Mundo local · Mods ·
> Opciones | Salir · WhatsApp | Web | Discord; iconos solo en WhatsApp y Discord. `tools/menu_sim.py` al día. (La 1.3.42,
> pixel art, nunca llegó a main.)

> **TF Client 1.3.41 (11 oct)** — el dueño: «los botones del menú de inicio están horribles, no pongas figuras, solo
> para WhatsApp y Discord». Botones planos y sobrios (`TFMenuButton`): principal dorado liso con borde fino; los demás,
> panel oscuro (74 %) con borde fino blanco que al pasar el ratón se vuelve dorado (rojo en Salir); sin degradados de
> cristal, halos ni destellos. Iconos solo en WhatsApp (verde #25D366) y Discord (#5865F2), en su color.
> Orden: Tierras Fantásticas · Mundo local · Mods | Web · Opciones | Salir · WhatsApp | Discord. WhatsApp abre
> `<web>/whatsapp` (la web redirige al grupo, `WHATSAPP_URL`). Simulador al día: `tools/menu_sim.py`.
> Música del pad: los enlaces que se le dieron (CC0 de OpenGameArt) pasan las comprobaciones de `MusicDownloader`
> (MP3 MPEG-1 capa III ≥128 kbps, 200 audio/mpeg, <80 MB).

> **TF Client 1.3.40 (11 oct)** — segunda ronda de quejas del dueño (publicado en main; jar en `descargas/`):
> - **Skills siempre activas**: la skill elegida con 5-0 se queda puesta hasta elegir otra (cada clic la usa cuando
>   está lista; mientras se recarga, o si no se dan sus condiciones, el clic es un golpe normal que lanza las auras de
>   golpe). Fuera lo de «ataque básico» y «un solo uso»: `SkillNet.Select(slot)`, `State(...selected)`,
>   `SkillServer.useSelected` devuelve si salió. HUD más pequeño (iconos de 12 px).
> - **«Dejan de hacer daño con el tiempo»**: `SkillRuntime.later` descartaba en silencio las tareas nuevas al pasar de
>   20.000, y los efectos con `repeat=999` (Mago del Rayo...) programaban todas sus repeticiones de golpe (≈99.000 en 8
>   min de simulación). Ahora cada repetición programa la siguiente y se corta si el efecto ya no existe (igual en
>   `sim.py`); si aun así se llena, avisa en el log. Prueba larga (scratch `bin/leak2.py`, 8 min por clase): las 39
>   clases siguen haciendo el mismo daño cada minuto.
> - **Rayas en texturas**: las esquinas de cada cara se meten un poco hacia el centro en la textura
>   (`VfxModel.insetUv/uvCenter`, en `SkillClient` y `VfxClient`): no se cuela el píxel vecino ni el fotograma siguiente
>   de las texturas animadas.
> - **Alas**: el planeo no se recargaba nunca (la marca de gastado solo se borraba en el suelo, donde Minecraft no
>   pregunta). Ahora 10 s de planeo → caes → 3 s → otra vez (`TFWings`); quitada la barra del centro (`TFGlideHud`).
> - **Comunidad (pad)**: REACCIONAR se cerraba con el temblor del ratón (`drag` cerraba el selector): ahora el arrastre
>   cuenta desde 4 unidades. Corazón = botón de like que funciona (también en tus fotos, sin sumar estadísticas).
>   Emojis más pequeños y nítidos (`TFPadScreen.blitSharp`).
> - **Mensajes**: nada en pantalla (títulos/encima de la barra) por oficios, misiones, cazas, TF Pass, tienda web,
>   ruleta, regalos: una línea de chat `[Prefijo] texto` (`util/TFChat`). Compra web: «[Tienda] Recibiste X. ¡Gracias
>   por tu compra!» y a todos «[Tienda] Steve compró X.» (antes 8 líneas y un título).
> - **Menú principal**: botones nuevos (`client/TFMenuButton`, `TFMenuPaint`, `TFMenuIcons`): principal en placa de oro,
>   secundarios de cristal azul noche con icono, hover dorado con halo y destello, Salir en rojo; Cinzel por escala
>   (`font/cinzel_N.json`). Simulador: `tools/menu_sim.py`.

> **TF Client 1.3.39 (11 oct) — Skills rehechas tras probarlas el dueño** (publicado en main; jar en `descargas/`).
> Quejas del dueño (textual, resumido): «texturas moradas incompletas; skills que no funcionan; partículas flotantes de
> polvillo que dan lag; no tengo control, a veces ellas me controlan; no quiero que al presionar una letra se pongan;
> GUI de skills más pequeña; usar las skills con 5 6 7 8 9 0 (los de los huecos): al presionar se ELIGE la skill y se
> usa con el clic de ataque (arqueros: al soltar la flecha con el arco); no quiero que me den armas, solo la skill; las
> de la hechicera no hacen nada; el pad aún enseña el paquete de skills de VFX y deben salir las clases para activar y
> desactivar; dime qué dependencias necesitan y si corren en Forge 1.20.1 Mohist».
> - **Texturas moradas**: en 1.20.1 los modelos de ítem solo ven texturas del atlas de bloques (`block/`, `item/`...);
>   las 738 texturas de `textures/skills/` no estaban → moradas y negras. Ahora `assets/minecraft/atlases/blocks.json`
>   (lo escribe `build_skills.write_atlas`, solo con las carpetas que usan los modelos de ítem; las de ModelEngine
>   `me/` se cargan aparte y NO van al atlas: son ~49 M píxeles). `audit.py` comprueba el atlas (`check_atlas`).
> - **Controles** (`SkillHud`, `SkillNet.Select/Use`, `SkillServer.select/useSelected`): teclas «TF Skills»
>   `key.tfclient.skillslot.1-6` = 5,6,7,8,9,0 (nombres nuevos para que a todos les salgan esas). Con clase, esas teclas
>   no cambian de hueco (`preTick` les quita la pulsación a `keyHotbarSlots` con la misma tecla: Forge reparte una
>   pulsación a todos los atajos de esa tecla) y **eligen** la skill; sale con el siguiente clic izquierdo (`attack`,
>   solo pulsación nueva: mantener picando no lanza) o, en las clases de arco (`SkillDefs.BOW_CLASSES`: arquero,
>   arquero_despertado, null), al soltar una flecha (`ArrowLooseEvent`, con munición y algo de tensión). El **ataque
>   básico** de cada clase (`basic` en el JSON de la clase: la habilidad de clic izquierdo del arma del pack, o
>   `BASIC_OVERRIDE` en build_skills) se queda puesto y cada clic lo repite; las demás se usan una vez y se vuelve al
>   básico. Misma tecla = quitar; cambiar de hueco (1-4, rueda) = quitar todo. HUD: fila pequeña (16 px) a la derecha de
>   la barra (dos filas si no cabe), tecla pequeña, dorado = elegida, azul = básico puesto. Sin esto la Bruja no podía
>   cargar energía (su básico la da) y sus hechizos «no hacían nada».
> - **Sin armas ni armaduras**: `SkillItems` ya no da nada y quita del inventario los objetos de clase que se dieron
>   (al entrar o cambiar de clase). Fuera `/tf web clases equipo`. El objeto de mano que miran algunas skills (guantelete
>   del Dragón Rojo: itemissimilar / equip HAND) se lleva «de mentira» por jugador (`SkillItems.HAND`, empieza en el
>   básico). Los items `clase_*` siguen registrados (por si alguien los tiene) pero nadie los recibe.
> - **Lag**: presupuesto de partículas por jugador en el servidor (`SkillFx.allow`: 30/tick con reserva de 450 para
>   las skills que lanzas; 1,5/tick para lo que sale de pasivas TIMER — `Ctx.ambient`) y en el cliente
>   (`SkillClient.add`: 200/80/25 por tick según Opciones › Partículas, máx. 48 por efecto, **sin forzar** (antes
>   `addParticle(force=true)` saltaba el ajuste del jugador), polvo como mucho tamaño 2: dura menos). Medido con
>   `pcount.py` (scratch): quietos, Vampiro ~950 partículas/s, Chamán 500, Guja 458; Catedral Divina ~94.000 en 5 s.
> - **Control**: un jugador nunca se aturde a sí mismo; lentitud fuerte (nivel ≥3), salto bloqueado, ceguera, náusea...
>   que se pone él mismo, fuera; levitación propia máx. 1 s, lentitud suave máx. 1,5 s (`SkillRuntime.selfEffect`);
>   lo que sale de una pasiva por tiempo nunca mueve ni teletransporta al jugador (`SkillMotion.push`); `recoil` ya no
>   mueve la cámara.
> - **Filo del Vacío**: le faltaba su ataque básico (VOID_EDGE_ATTACK, del arma, no está en la clase de MMOCore);
>   `compile.py` mete el de clic izquierdo del arma si falta. Ahora 5 skills (Tajo del Vacío).
> - **Tajos cuerpo a cuerpo**: los tótems de un momento (≤10 ticks, radio ≤2) de un jugador llegan 0,5 más
>   (`SkillMovers.MELEE_REACH`, igual en sim.py): en el pack llegaban ~2 bloques y se pega a los mobs hasta a 3.
> - **Textos**: el mod enseña nombres en español (`name_es`/`role_es`); avisos del pack traducidos (`es.json`
>   «_mensajes», `build_skills.translate_messages`); descripciones que explican la energía de la Bruja (el básico la
>   carga, cada hechizo cuesta 3-16) y el guantelete del Dragón (se carga **manteniendo Shift**).
> - **Pad**: Efectos › SKILLS ya no enseña los paquetes de VFX (y `VfxServer` ya no equipa paquetes): enseña las clases
>   compradas (la web las manda en `skills[].owned` del poll) con ACTIVAR / DESACTIVAR y, de la activa, sus skills con
>   tecla. Lo elegido va a la web en `pad.skills` del poll (`SkillServer.chooseFromPad`, `TFBridge.padSkill`).
> - **Web**: teclas 5-0 en Skills, «Ataque básico · se queda puesto», sin «arma y armadura», FAQ de cómo se juega, la
>   skill nueva del Filo del Vacío con su vista previa. **Las clases siguen a 1 moneda** (prueba del dueño: `COIN_PRICE`
>   en tools/skills.py; volver a 7500 cuando lo diga).
> - Prueba nueva (scratch `bin/combat2.py`): combate con los controles nuevos (15 s de básico que carga recursos, luego
>   cada skill + clics, ventana de 10 s, zombis a 1,8/6/7,5/8). Resultado: 188 de 246 pegan; ~18 son de apoyo/modo
>   (escudos, saltos, teletransportes, curas, formas del Vampiro) y las que no salen piden algo del pack (Golpe
>   Huracanado: enemigos en el aire por el tornado; Paso del Viento: en el aire; Marca de la Muerte: en dos pasos).
> - Dependencias (pregunta del dueño): los packs originales piden MythicMobs (+ ModelEngine R4, MythicLib/MMOCore,
>   MMOItems, ItemsAdder/Oraxen/Nexo según el pack). Son plugins de Paper; en Mohist (Forge+Bukkit) MythicMobs a veces
>   va, pero ModelEngine e ItemsAdder no soportan servidores híbridos. Por eso el TF Client trae su propio motor: no
>   hace falta ningún plugin. El jar de MythicMobs 5.11 que mandó el dueño solo se usó como referencia (descompilado en
>   el scratch, nunca en el repo).

> **TF Client 1.3.38 (10 oct, noche) — TF Pad: auditoría, pad más grande, iconos nuevos, Gachapón y TF Pass** (sin
> commit: lo hace el dueño). Pedido: «los botones de x … en la música están solapados… audita el pad… módulo
> Gachapón con monedas verdes… las monedas se ganan en el pase de batalla, TF Pass… pad un poco más grande vertical y
> horizontal… apps simétricas… redibuja el pico de jobs».
> - **Pad más grande** (`TFPadScreen.init/drawFrame`): el tamaño y el centrado se calculan con lo que **se ve** del marco
>   (`VIS_L/R/T/B` = x 10–382, y 2–235 en píxeles de pad; frame.png tiene 16 px transparentes abajo) y además de las
>   filas lisas (`STRETCH_U/D`, `MAX_EXTRA` 48) se repiten **dos columnas lisas** simétricas a los lados de la gema
>   (`STRETCH_L/R` = 540/1028 de la imagen, `MAX_EXTRA_X` 24 por lado). 1920×1080: cristal 672×328 unidades (antes
>   576×292); 1366×768: ahora ps 3 (antes 2), 504×225 (antes 288×212); 1280×720 y 2560×1440 también caben.
> - **Portada simétrica** (`PadHomePage.layout`): fichas iguales, mismo hueco a lo ancho y a lo alto, filas repartidas
>   (23 apps → 8+8+7), centrada, la última fila incompleta centrada; si no caben las filas, baja a una escala entera
>   menor (siempre nítida). Orden: MÚSICA, OFICIOS, MISIONES, **TF PASS, GACHAPÓN**, CAZAS, RECOMPENSAS, TIENDA, GTS,
>   MONEDERO, VIAJES, HOGARES, KITS, PROTECCIÓN, CLANES, JUGADORES, COMUNIDAD, CÁMARA, RANKING, ARMARIO, EFECTOS, MI RANGO,
>   WEB. También en el pad de admin (APPS) para apagarlas.
> - **Auditoría (arreglado)**: Música — el «+» de cada canción caía encima de la duración y debajo de «¿BORRAR?»
>   (ahora los botones van de derecha a izquierda con su hueco: [X/¿BORRAR?] [+] duración; en playlist [−]); la X de la
>   playlist elegida tocaba el nombre (nombre a la izquierda y X detrás, con hueco y hover); el % del volumen caía
>   encima de PORTADA (ahora en una etiqueta a la izquierda del botón); la tarjeta del reproductor empezaba más arriba
>   que la lista (ahora las dos alineadas, con su pestaña oscura SONANDO/REPRODUCTOR). Apps del servidor
>   (`PadViewPage`/`PadUi`): etiquetas con moneda que se salían de la tarjeta al recortarse (`PadUi.fitChip`), la
>   ruedita de «cargando» encima del texto de la barra de información (la barra deja 12 libres), anchos de texto de
>   filas/cabecera y del campo de abajo que podían quedar negativos (mínimos). Barra de arriba: el título se aparta si
>   lo de la derecha llegara a él.
> - **Iconos** (`tools/pad/iconos4.py` + `build_pad.normalize`): pico de Oficios con la estructura del de Minecraft
>   (arco con centro en la empuñadura, puntas curvadas, mango que asoma), TF Pass (billete dorado con estrella y moneda
>   verde), Gachapón (máquina de cápsulas), Misiones (pergamino con lista), Viajes (mapa plegado), Efectos (destello).
>   Todos caben en 30×30 y van centrados por su caja (antes Cazas, GTS, Música, Protección y Armario tocaban el borde).
> - **Moneda verde** `tfclient:fantastic_coin_green` («Fantastic Coin verde» / «Green Fantastic Coin»), textura sacada
>   de la de oro con una rampa esmeralda por luz (`build_pad.verde`; también `coin_green.png` y `coin_green_s.png`). Saldo
>   propio en el servidor (`pad/server/PadGreen`, `<mundo>/tfclient/monedas-verdes.json`), en la barra de arriba (junto
>   a las de oro), en el Gachapón y en el Pase. **Solo se gana con el TF Pass**: no hay comando, la web no la vende ni la
>   conoce y no se manda a otros (normas de Mojang sobre premios al azar).
> - **Gachapón** (`pad/server/PadGacha` + `pad/PadGachaPage`): config `config/tfclient/gachapon.json` (precio 1, tirada
>   x5 por 5, historial 30, garantía: épico o mejor cada 10 tiradas; rarezas Común 62 / Raro 27 / Épico 9 / Legendario
>   2 con su color; 22 premios con rareza y peso: monedas, objetos, comandos con {player}/{uuid}, verdes). El servidor
>   cobra, tira y da el premio; el pad anima una ruleta (tira de premios que frena en ~4,6 s con tic) y enseña el
>   resultado, las demás de la x5, PREMIOS con su probabilidad e HISTORIAL. Datos en `<mundo>/tfclient/gachapon.json`.
> - **TF Pass** (`pad/server/PadPass` + `pad/PadPassPage`): config `config/tfclient/pase.json` (temporada 1 «El
>   Despertar» desde el día que se crea, 60 días, se renueva sola; 50 niveles de 400 XP, `xpExtraPorNivel`; XP diaria
>   100, semanal 500, caza 40; premios por nivel: 65 monedas verdes por temporada + monedas y objetos). `PadMissions`
>   llama a `PadPass.missionDone` al completar una misión (y la fila dice «+100 XP de pase»). Pad: cabecera con el
>   nivel, barra de XP, cuenta atrás, verdes y RECLAMAR TODO; pista de niveles con desplazamiento (rueda, flechas,
>   ←/→), raíl de oro, hitos cada 5, tu nivel con «TÚ», COBRAR/✓/candado; abajo el nivel señalado y cómo se gana XP.
>   Datos en `<mundo>/tfclient/pase.json` (por temporada: lo no reclamado se pierde al cambiar de temporada).
> - Red: paquete nuevo `TFPadNet.Data` (JSON, id 8) y `PadServer.DataApp` para apps con página propia; `State` lleva el
>   saldo verde. `/tf reload` recarga gachapon.json y pase.json.
> - Simulador: `tools/pad/sim/sim.py` con la geometría nueva (`Sim(fbw, fbh)`, `save(screen=True)`), portada nueva,
>   Música con los estados de ratón (`p_musica_fila/borrar/lista`), `p_gachapon*` y `p_pase*`. Compilado, jar 1.3.38,
>   `check_mod_items` 0 errores. **Sin probar en juego** (regla).

> **🚧 TRABAJO EN CURSO — Skills (TF Client 1.3.35+; checkpoint, si retomas sigue esto):**
> Pedido del dueño (9 oct, noche): portar **~39 packs de skills** (clases de MMOCore / MythicLib / MythicMobs /
> ModelEngine / ItemsAdder / MMOItems) «correctamente y sin errores»: modelos, animaciones, mecánicas, controles,
> ilustraciones, sonidos, todo. En la web, **quitar «Tienda de monedas»** y poner una sección **«Skills»** bien hecha;
> actualizar el pad y lo necesario. Los manda de 5 en 5 (comprados: **no subir los packs al repo**, solo lo convertido).
> **Decisiones del dueño:** se consiguen **con dinero (Stripe) y con monedas** (las dos); cada jugador tiene **una
> sola clase** a la vez. **Cambiado el 11 oct (1.3.39, ver arriba):** las teclas 5-0 ELIGEN la skill y se usa con el
> clic izquierdo (arqueros: al disparar); **sin armas ni armaduras**, solo las skills.
> **Recibidos: 40 packs** (40 clases, ~280 skills; ver `tools/skills/inventario.py <carpeta>`), descomprimidos en el
> scratch de la sesión `skills/` (el scratch es **de cada sesión**: en una nueva hay que pedirle los zips al dueño otra
> vez; llegan a `/root/.claude/uploads/<sesión>/<prefijo>-<Pack>.zip`. Se descomprimen con `bsdtar -xf` (paquete
> `libarchive-tools`, que también abre el `.rar`) en `<scratch>/skills/<Pack sin prefijo>/`, y los zips de dentro en una
> carpeta con su nombre; con eso `build_skills.py` saca la tanda 1 **idéntica** a la del repo. El zip de
> `samus2002_AWAKENED_ARCHER` trae dentro una copia exacta de `fire_katana`: bórrala, o el arquero cuenta 4 skills de más). Los primeros 25: Red-DragonPack, MAGEPACK-1.1 (.rar),
> Glacia-v1.2, ZEPHYR-v1.3, samus2002_NECROMANCER, THORPACK-1.3, NULLPACK-1.1, samus2002_ARCTIC_KNIGHT,
> RPG_Class_Series_Dragon_Warrior, samus2002_BLOODMOON_VAMPIRE, samus2002_SUMMONER_PACK_v1.7, samus2002_DEATH_KNIGHT,
> RRPG_Void_Edge_Class_Full, PackClass_Magic_Lightning (zips dentro), LostAssets_ClassPack_04_Revenant,
> LostAssets_OriginalClassPack_01_FrostSamurai, samus2002_AWAKENED_ASSASSIN, samus2002_HEROES_THUNDER_RONIN,
> ModelFoundrys_Witch_Class, HEROES_WATER_SAMURAI, AWAKENED_WARRIOR, LostAssets_ClassPack_03_Paladin,
> HEROES_FLAME_WARRIOR, samus2002_AWAKENED_ARCHER, fire_katana; y después: Lost_Loong-Cetus, samus2002_AWAKENED_MAGE,
> Warrior_Class, samus2002_GALE_GLAIVE, Khans_RPG_Klasses (Archer, Mender, Rogue), samus2002_AWAKENED_MARTIAL_ARTIST,
> _SHAMAN, samus2002_HEROES_BEAST_SLAYER, samus2002_AWAKENED_GUNSLINGER, _PYROMANCER, _CLERIC, _NECROMANCER, _SPIRITCALLER.
> Formatos: clases MMOCore (`classes/*.yml` + `skills/*.yml` con nombre/lore/cooldown/maná) → MythicLib
> (`mythicmobs-skill-id`) → árbol de MythicMobs; armas de MMOItems con `ability: {type, mode: LEFT_CLICK/RIGHT_CLICK/
> SHIFT_*/SNEAK}`; VFX como mobs de ModelEngine (`.bbmodel`) o modelos de ítem (ItemsAdder/Nexo/«resourcepack»).
> Mecánicas usadas (≈60): summon, state, changepart, model, aura, delay, projectile, missile, damage, potion, throw,
> lunge, leap, orbital, totem, effect:sound/particles/particlering/particlesphere, teleport, heal, ignite, stun, pull,
> velocity, equip, brightness, tint, partvis, setvariable, randomskill, sudoskill… (conteo en el commit del inventario).
> **Plan** (marca [x] y haz commit+push como checkpoint):
> - [x] Inventario automático de cada pack → `tools/skills/inventario.py` (valores con nivel al máximo: la clase se
>       compra entera; pasivas TIMER/DAMAGED de MMOCore o MythicLib).
> - [x] Compilador → `tools/skills/compile.py` (programa JSON por clase: skills, árbol de MythicMobs alcanzado,
>       mobs/efectos, ítems; sigue `oH/oT/oE`, `castinstead/orElseCast`; valores sueltos como `delay 28` en `a._`).
> - [x] Generador → `tools/skills/build_skills.py <scratch>/skills` (sin args = solo las clases de `TANDAS`; con
>       `--todas` revisa los 40): `assets/tfclient/skills/classes/<id>.json` + `catalog.json`, modelos de ítem
>       (`models/skills/`, `textures/skills/`, también los que genera ItemsAdder/Oraxen desde su config), modelos de
>       ModelEngine (`skills/models/<clase>.<modelo>.json`, formato de `vfx_bb.py`), sonidos (`sounds/skills/`, eventos
>       `tfclient:skills.<clase>.<x>` en sounds.json; si el pack no trae uno, el de otro pack o el vanilla más
>       parecido) e iconos 32×32 (`textures/gui/skills/<clase>/<skill>.png`; si el pack no trae, se dibuja el efecto,
>       `skills/jsonmodel.py`). `samus2002_NECROMANCER` no es clase (es un jefe): fuera. Magic Lightning: la versión
>       «Skills_Untimate_V3 / config to Oraxen».
> - [x] Motor en el mod (`skills/`, compila; **sin probar en juego**): `SkillDefs` (lee el jar), `SkillRuntime`
>       (intérprete: delay, repeat, objetivos, condiciones con acciones, variables `SkillVars`), `SkillTargets`,
>       `SkillConds`, `SkillMovers` (projectile/missile/totem/orbital), `SkillAuras`, `SkillFx` (partículas/sonidos por
>       paquete), `SkillModels` (model/state/changepart/partvis/tint, disfraz en jugador, @modelpart), `SkillMotion`,
>       `SkillActor` (los «mobs» de efecto NO son entidades: los lleva el servidor y los dibuja el cliente),
>       `SkillServer` (una clase por jugador, `<mundo>/tfclient/skills.json`, config `config/tfclient/skills.json`,
>       pasivas, `/tf web clases dar|lista|ver`, puente: campo `skills` [{uuid, clase, at}] en la respuesta del poll),
>       `SkillClient` (dibuja actores: ítem en la cabeza como un soporte + modelos ME animados), `SkillHud` (columna a
>       la derecha con iconos/teclas/cooldown; teclas R G Z X V B N M en Controles › «TF Skills»).
> - [x] Simulador offline `tools/skills/sim.py <clase> [skill] [--webp carpeta]` / `--todas`: copia las reglas del
>       motor Java (si cambias uno, cambia el otro), avisa de mecánicas/objetivos/condiciones/variables que faltan y
>       saca WebP animados (Steve + 3 zombis, skins del client.jar vía `build_vfx.py`). Con las pasivas de la clase
>       calienta 45 ticks antes; prueba cada variante del objeto de mano (Dragón Rojo) y se queda con la que más hace.
>       Pasada a las 39 clases: 271 avisos, todos de tandas futuras (lista: `mmocantarget`, `settextdisplay`,
>       `<modifier.damage>` sin valor, `setnodamageticks`, `modifyprojectile`, `slash`, `@variablelocation`,
>       `@children`, `signal`, `setvarloc`, `@targetblock`, `@entitiesinworld`, `hastag`, `posearmorstand`…).
>       La tanda 1 sale con 0 avisos.
> - [x] Armas y armaduras de clase: `build_skills.py` genera un set `clase_<id>` (`skills/class_sets.json`, lo lee
>       `TFSets` además de `tf_sets.json`; `SetDef.classId`): arma de MMOItems (sin la «shiny»), armadura de cuero
>       con CustomModelData + capas de OptiFine CIT, cabeza, y los objetos de mano que mira la clase (Sacred Gear del
>       Dragón Rojo y sus niveles s2/s3: `itemissimilar` / `equip …:HAND` → `SkillItems`). Se entregan vinculados al
>       tener la clase (`equipo` en skills.json, una vez por clase) y se quitan al cambiar de clase.
>       `/tf web clases equipo <jugador>` las vuelve a dar.
> - [x] Motor ampliado: `ondamaged/onattack` (auras que multiplican el daño), `command{c="meg disguise X"}` → modelo
>       en el jugador, objeto en la mano de los efectos (`hand`), etiquetas que no son variables (`<#FFAC00>`) se
>       quedan, `oH/oT/oE`, `castinstead/orElseCast`. Bug arreglado: rutas del pack relativas (la carpeta `skills/`
>       del scratch hacía que mobs/ítems contasen como skills).
> - [x] Textos en español de la tanda 1: `tools/skills/es.json` (nombre, rol, descripción, color; nombre y
>       descripción de cada skill) → `name_es/role_es/desc_es`, `name_es/desc_es` en el JSON de cada clase.
> - [x] Bug (10 oct): las TargetConditions con `castinstead`/`orElseCast` se trataban como filtro (tiraban el
>       objetivo). Ahora, por objetivo: si toca, ese objetivo va a la otra skill y los demás siguen
>       (`SkillRuntime.runMeta` y `sim.py`). Antes `frost_beam` (Glacia) hacía 0 de daño y el martillo de Thor al
>       volver se trataba a sí mismo como objetivo en vez de desaparecer.
> - [x] **Las 39 clases** (10 oct): `build_skills.py` ya convierte todas (`TANDAS` con las 39; ids limpios en
>       `CLASS_IDS`: `arquero`, `nigromante`, `vampiro`...). El pack `samus2002_NECROMANCER` es un **jefe** (mob con
>       barra de jefe y fases), no una clase: fuera. Zips con copias dentro: `samus2002_AWAKENED_ARCHER` trae
>       `fire katana` y `samus2002_AWAKENED_CLERIC` trae el Piromante entero: bórralas (son idénticas a sus packs).
>       Motor ampliado (Java y `sim.py` igual): condiciones en línea de objetivos y proyectiles (`tc`/`hc`/`sc`),
>       `sort`/`limit`, ~30 mecánicas nuevas (signal/onSignal, settextdisplay con la fuente de la clase, slash,
>       polygon, chain, spin, hide, sendtitle, modifyprojectile, setvarloc, tags, stance, faction, gcd...), ~15
>       objetivos (@children, @EntitiesInCone, @EntitiesInLine, @VariableLocation, @TargetBlock...), ~15 condiciones
>       (mmocantarget, ischild, hastag, pitch, ismoving...), `origin=@Objetivo` en cualquier línea, `@forward{uel}`,
>       proyectiles pegados al suelo (hugSurface), targetIsOrigin, onHitBlock, immuneDelay, orbitales con aura,
>       auras de evento (onDamaged con cancelEvent/damageMods, onAttack, onSwing con clic al aire, onDeath),
>       **esbirros** (`SkillMinions`: los mobs con IA del pack siguen al dueño, persiguen y atacan con sus skills),
>       montar en un modelo (mountmodel), `onSummon`, `onSurface`, «Essentials» de la serie Awakened que le faltan
>       a un pack (se cogen de otro). Errores que había: TIMER de las pasivas va en **ticks** (no segundos); solo
>       las N primeras `passive-type` de una clase con N ranuras `<PASSIVE>` son pasivas (las demás, con tecla);
>       acción `cast X` de las condiciones; variable sin poner = `UNDEFINED`; `isChild` = «tiene dueño».
>       `sim.py --todas`: 0 problemas en las 39; `--trace` enseña por qué una skill no sale.
> - [x] **Checkpoint 10 oct (tarde):**
>       textos en español de las 39 (`tools/skills/es.json`); `tools/skills/check_assets.py` (sonidos, modelos de ítem
>       y de ModelEngine con sus texturas, iconos, fuentes y partículas de 1.20.1: **0 fallos**); modelos rotos de los
>       Khans (override malo → el del config); iconos de LostAssets sacados del modelo del mob que invocan; los
>       esbirros pegan a otros actores cuando el pack lo pide (hitConditions con mythicmobtype); `START_VARS`
>       (nigromante/invocador empiezan en postura «free»); **esbirros que no atacaban**: `setname{name=<target.name>}
>       @owner` desde un esbirro le pone al esbirro el nombre del dueño (así encuentra el aura «<caster.name>TARGET»),
>       y `<target.name>` de setname se resuelve por cada objetivo de la línea (Java `SkillVars.resolve` + `sim.py`).
>       `sim.py` tiene ahora un muñeco cerca (2.3 bloques) para probar golpes cuerpo a cuerpo.
>       Arreglos de motor de la otra sesión (commits `f2eb275`, `374b1e5`): `slash` con onHit (Katana de Fuego),
>       condiciones `cast X` seguidas al compilar (Misil Arcano), la bala de un proyectil fuera de los selectores de
>       área salvo por tipo, `vo` de proyectiles y gravedad (granizo de Glacia, lluvia de Null), Firestorm Volley con
>       una pulsación, elixires de la Bruja, Cyclone Shot del arquero, offsets de @selflocation/@origin.
>       Skills con daño 0 en el sim revisadas: son pasivas/buffs/posturas o necesitan otra skill antes (detalle en el
>       PR borrador #58). Duda abierta: el Shift+clic derecho del Samurái de Escarcha (`leap` de MythicMobs) puede
>       soltar el golpe en el aire; no se tocó (afectaría a 10 clases).
> - [x] **TF Client 1.3.35 (10 oct)** — las 39 clases de Skills dentro. Revisión visual de las 39 hecha (hojas de 4
>       fotogramas con `sim.py`): sin texturas rotas ni modelos que falten. Lo que se ve raro en las hojas y está
>       bien: el cubo gris es la partícula `explosion` dibujada por el sim; la guadaña/lanza/dagas flotando sobre el
>       jugador (Nigromante, Invocador de Espíritus, Vampiro) son el arma del combo del pack (orbital de radio 0).
>       `sim --todas` 0 problemas (solo `vampiro bloodborne_blades` «no se lanza»: ya empiezas con hojas),
>       `check_assets` 0 fallos, `check_mod_items` 0 errores. **No publicado todavía** (falta la web de Skills).
>       **El jar ya pesa ~48 MB y el envío de archivos admite 30 MB.** El dueño prefiere **enlace directo**: el jar se
>       sube a `descargas/` en la rama y se le da `https://github.com/modelqdeluxe-ops/ss/raw/<rama>/descargas/<jar>`
>       (el repo es público; comprobar con `curl -sSL -o /dev/null -w '%{http_code} %{size_download}'`).
> - [x] **Rechequeo a fondo + TF Client 1.3.36 (10 oct, noche)** — el dueño: «no quiero errores, son cosas que vamos
>       a vender». Herramientas nuevas: `tools/skills/audit.py` (modelos de ítem con las reglas de 1.20.1, animaciones/
>       huesos/piezas de ModelEngine que piden las skills y los mobs, texturas, cabeceras Ogg, textos; los 17 avisos
>       son cosas que el pack trae así, listadas en `PACK_AS_IS`). En el scratch: `stress.py` (cada clase lanza todas
>       sus skills 3 rondas con golpes), `flicker.py` (qué efecto se vuelve a crear en bucle con el jugador quieto),
>       `residue.py` (lo que el compilador deja sin leer de cada línea de los .yml). Fallos encontrados y arreglados
>       (Java y `sim.py` igual): condiciones compuestas `(A false || B) true` y `&&` se perdían (armas del Vampiro
>       parpadeando, golpes de la Bruja); `mergeAll`/`mergeSameCaster` en órbitas (lanza del Invocador de Espíritus);
>       renovar el aura de una órbita alarga la órbita (guardián del Invocador); el tope de 60 s ya no borra la bala de
>       una órbita (guadañas fijas); `entitytype` sobre efectos mira el tipo del mob del pack; caras `#missing` fuera
>       (Disparo de Dragón); `delay "<modifier.duration> * 20"` (Escudo Helado, Shadowquake); **parámetros de skill**
>       (`skill{s=X;damage=<modifier.damage> * 0.4}` → `<skill.damage>` en X: los combos pegaban de más);
>       `cooldown`/`cd` de cada línea; `pk`; **`@TargetedTarget` = el objetivo heredado** (en 13 packs el daño en área
>       solo le llegaba al de la mira); los displays no salen en `@EIR/@ENO` salvo que se busquen por tipo;
>       `@forward{ofowner}`, `targetSelf` (curas del Clérigo), `livingOnly`, `targetArmorStands`.
> - [x] **TF Client 1.3.37 (10 oct)** — 1.3.35 y 1.3.36 NO arrancaban: «Duplicate registration
>       clase_invocador_summoner_tome» (el tomo del Invocador salía dos veces en su set: en la mano y en MMOItems).
>       `build_skills.class_set` ya no repite un objeto y `check_mod_items.py` revisa también `skills/class_sets.json`.
> - [x] **Web: sección «Skills»** en lugar de «Tienda de monedas» (`tools/skills.py` → `config/skills.json` +
>       `public/img/skills/`; precio **5,99 USD o 7.500 monedas** para todas, en `PRICE_CENTS`/`COIN_PRICE` al
>       principio de `tools/skills.py`). Las monedas salen de la economía del pad (estimado: un jugador normal, 2 h/día,
>       gana ~450/día con oficio ~160, misiones de oficio ~75, diarias del pad ~50, semanales ~25, cazas ~100, ventas
>       ~50; uno activo 4 h ~1.000/día) → una clase en ~2 semanas jugando normal o ~1 semana activo; pagar es el atajo.
>       Ojo: la venta de diamantes (40, tope 64/día) y chatarra de netherita (180, tope 16/día) deja sacar hasta ~5.400
>       al día a quien farmee eso. Compra con Stripe/PayPal y con
>       monedas (`tf web monedas quitar {player} N` por el puente); las clases compradas son para siempre y se elige
>       la activa en la web (tabla `skill_active`); `/bridge/poll` manda `skills: [{uuid, clase, at}]`. Términos a
>       versión 2026-10-10.1 (producto nuevo: todos vuelven a aceptarlos). El maná no se enseña (el mod no lo gasta).
>       Vistas previas: `sim.py <clase> --webp <carpeta>` (de 2 en 2) y `python3 tools/skills.py --vistas <carpeta>
>       --rehacer`. Las rutas `/api/coinshop` siguen. **Aviso**: vender ventaja de juego con dinero va contra las
>       normas de Mojang; lo decidió el dueño.
> - [ ] Pad: app/apartado de la clase y sus skills.
> - [ ] Publicar por tandas, con vistas previas. Tanda 1: Glacia, Zephyr, Mago, Dragón Rojo, Thor.
>
> **Estado de `main` (9 oct 2026, noche):** último PR fusionado #56/#57. Última versión publicada del mod: **TF Client
> 1.3.34** (la siguiente, con las Skills, será **1.3.35**). Lo de Skills de arriba está **a medias** en su rama.
>
> **Resumen rápido para la IA que siga (lo hecho hoy, de lo más nuevo a lo más viejo; detalle en las entregas abajo):**
> - 1.3.34: Protección → AJUSTES como **preguntas con SÍ / NO** (el que vale, verde; el otro, gris). El dueño se quejó
>   dos veces de textos confusos («No construir: SÍ», «BLOQUEADO»): **no vuelvas a poner estados ambiguos**; pregunta
>   clara + respuesta.
> - 1.3.33: **solo `/tf web …` y `/tf reload`** (fuera `/tf jobs` y `/tf shop`); `/tf reload` = `pad/server/TFReload`
>   (no recarga un JSON roto: dice cuál y el error; refresca la app abierta de cada jugador). Botones `PadView.GRAY`
>   ahora son grises de verdad (antes salían verdes).
> - 1.3.32: reaccionar también a tus fotos (el like a las tuyas sigue sin poderse).
> - 1.3.31: Comunidad: tarjeta limpia + botón **REACCIONAR** con selector de emojis. 1.3.30: emojis de Noto (44x44).
> - 1.3.29: economía estable, misiones/cazas por temporadas de 90 días, emojis, playlists, altavoz por proximidad.
>
> **Temas abiertos (se le ofrecieron; NO los hagas sin que los pida):**
> - Con `/tf jobs` se fueron los comandos de staff de oficios (`nivel`, `xp`, `reiniciar`): si los quiere, irían en el
>   pad de administrador (no como comandos).
> - Dar like a tus propias fotos (para probar), como ya se hizo con las reacciones.
>
> **Cómo se trabaja con él (resumen de la sección 2):** háblale en español; haz exactamente lo que pide y nada más;
> cuando esté terminado y probado: PR → squash merge → reiniciar la rama desde `origin/main` (force-with-lease) sin
> pedir permiso; cada cambio del mod sube `mod_version` (gradle.properties) y `TFClient.VERSION`, se compila y se le
> manda el `.jar` adjunto (y vistas previas del simulador si cambia algo visual). **Nunca** abrir el juego ni un
> servidor. Secretos solo en Cloudflare. Rama de trabajo: la que te asigne tu sesión (Skills: ver arriba).
>
> **Checkpoints:** el dueño pidió (9 oct) que en trabajos largos se vaya apuntando el plan y el avance aquí arriba y se
> haga commit + push a la rama de vez en cuando, para que otra IA pueda seguir sin perder nada.
>
> **Para seguir:** lee las secciones 0 (entregas, de la más nueva a la vieja) y 2 (reglas). Antes de tocar el menú de
> inicio, lee «menú de inicio como antes»: el dueño **no** quiere la nebulosa ni el cielo animado ni botones nuevos ahí.
> Cómo comprobar sin abrir el juego (prohibido): compilar (`./gradlew build --offline -q`), `python3
> tools/check_mod_items.py`, simular con PIL lo que se ve (texturas reales) y mandárselo como imagen; para el pad,
> `tools/pad/build_pad.py --preview` y `tools/pad/sim/pages.py <carpeta>` (ver 1.3.22). La web: `npm test`
> en `tierras-fantasticas/` y una vista previa local con Playwright (Chromium en `/opt/pw-browsers/chromium`).
> Para portar un mod suyo en .jar: CFR + el tsrg de ForgeGradle (`build/createMcpToSrg/output.tsrg`) para pasar los
> nombres SRG a los oficiales; luego volver a decompilar lo compilado y comparar con el original (ver 1.3.18).

**Prioridades del dueño:** 1) la web (diseño y ahora también **lo legal**), 2) el mod TF Client (cambios que vaya
pidiendo: cada uno sube versión y se le manda el `.jar`), 3) **Stripe conectado** por el dueño (ya no está en pausa); PayPal listo para cuando ponga sus claves.

### Última entrega (9 de octubre de 2026): ajustes de Protección como preguntas — TF Client 1.3.34
Con «BLOQUEADO»/«PERMITIDO» (1.3.33) seguía sin entenderse («¿el que está bloqueado? ¿permitido qué?»). Ahora
(`PadClaims`) cada opción es una **pregunta** con **dos botones, SÍ y NO**: el que vale ahora sale **verde** y el
otro **gris**; se pulsa el otro para cambiarla (la acción `flag:<zona>:<opción>:1|0` pone el valor, no lo alterna).
Las de los de fuera van bajo «Los de fuera de tu zona (tú y tus miembros pueden todo)»: «¿Pueden construir?»,
«¿Pueden abrir cofres?»…; las demás: «¿Las explosiones rompen?», «¿Salen monstruos?», «¿Prohibir el PvP?»,
«¿Avisarte si entra alguien?», «¿Volar?»… con una nota corta debajo cuando hace falta. En MÁS: «¿Se ve el contorno de
la zona?», «¿Mensaje al entrar/salir?» (SÍ/NO) y una fila aparte con EDITAR para escribir cada mensaje. Las preguntas
son cortas para que no se corten (con la fuente de Minecraft, que tiene «¿»; el simulador ya también lo dibuja).

### Entrega anterior (9 de octubre de 2026): solo /tf web y /tf reload, ajustes de Protección claros — TF Client 1.3.33
Pidió quitar los comandos `/tf` porque todo va por la tableta (dejar `/tf web`, «tf claims creo» y un `/tf reload` que
«se actualice de verdad»), y que los ajustes de la zona no confundan («MOBS HOSTILES … SÍ», ¿sí qué?).
1. **Comandos**: fuera `/tf jobs` (borrado `jobs/TFJobsCommands.java`) y `/tf shop`. Quedan `/tf web …` (staff; las
   protecciones del staff siguen en `/tf web claims`) y **`/tf reload`** (nivel 3). El aviso de misión de oficio hecha
   ya no lleva el botón [Cobrar] (era `/tf jobs ver`): dice «cóbrala en el pad, app Oficios».
2. **`/tf reload`** (`pad/server/TFReload.java`; RECARGAR CONFIGS del pad de admin usa lo mismo): pad, tienda,
   oficios, kits, recompensas, misiones y cazas, ayuda, protecciones, límites, efectos (vfx), tienda de monedas,
   ruleta y servidor.properties. Antes de cada JSON comprueba que se lee: si tiene un error, **esa** config se queda
   como estaba y dice el archivo y el error (antes, un JSON roto dejaba los valores de fábrica sin avisar). Después
   manda a todos su estado y **vuelve a pintar la app que tengan abierta** (`PadServer.refreshOpen`, que apunta la
   última vista de cada uno en la sesión «§vista»).
3. **Protección → AJUSTES** (`PadClaims`): cada fila dice QUÉ controla («Construir», «Explosiones», «Daño de los
   mobs»…) y el botón CÓMO está ahora («BLOQUEADO»/«PERMITIDO», «ACTIVADO»/«DESACTIVADO»; el PvP: «BLOQUEADO»/
   «NORMAL»); debajo, una frase con lo que pasa ahora mismo. En MÁS, contorno y mensajes con «ACTIVADO»/«DESACTIVADO».
4. **Botones grises**: `PadView.GRAY` se pintaba **verde** (PadUi.button recortaba el estilo a 3), así que el «NO»
   salía igual que el «SÍ». Ahora es gris de verdad (`BTN[5]`; el de desactivado sigue siendo `BTN[4]`). Afecta a
   todos los botones grises del pad (también en el simulador).

### Entrega anterior (9 de octubre de 2026): reaccionar a tus fotos — TF Client 1.3.32
Pidió poder reaccionar a sus propias fotos (para probarlo): REACCIONAR sale también en las tuyas y el servidor ya no
lo bloquea (`PadCommunityServer`, case "react"). El like a tus fotos sigue sin poderse (no lo pidió).

### Entrega anterior (9 de octubre de 2026): botón REACCIONAR en Comunidad — TF Client 1.3.31
Pidió que la tarjeta de cada foto quede **limpia** (sin la fila de los 5 emojis puesta por defecto) y que haya un
botón **REACCIONAR** para elegir el emoji que quiera. `PadCommunityPage`:
- En la tarjeta: REACCIONAR (azul; desde la 1.3.32 también en tus fotos) y a su lado **solo las reacciones que ya tiene** la foto, con
  su número (la tuya, en oro). Una foto sin reacciones no enseña ningún emoji.
- REACCIONAR abre un **selector** (`drawPicker`): burbuja blanca con filo de oro encima del botón (debajo si no cabe)
  con los 5 emojis a 14 unidades (crecen al pasar el ratón; el tuyo, en oro). Clic en uno = reaccionar (el mismo otra
  vez lo quita, como antes en el servidor). Clic fuera, Esc, la rueda o arrastrar lo cierran.
- Emojis a 44x44 y pintados suavizados (`TFPadScreen.blitFit` pone filtro lineal): se ven bien a cualquier escala.

### Entrega anterior (9 de octubre de 2026): emojis de Comunidad nuevos — TF Client 1.3.30
Viendo los emojis de la 1.3.29 dijo que estaban «horribles», las caritas mal dibujadas y que el pulgar parecía otro
dedo. Ahora son los de **Noto Color Emoji** (de Google; licencia OFL 1.1 / Apache 2.0, se pueden usar en el mod)
reducidos a 22x22: `tools/pad/make_emojis.py` los deja en `tools/pad/emoji/` y `build_pad.py` los copia como
`emo_<nombre>.png`. El pad los pinta en 11x11 unidades (`TFPadScreen.blitFit`): a 1080p, 1 píxel de textura = 1 real.

### Entrega anterior (9 de octubre de 2026): economía, misiones por temporadas, emojis, playlists y altavoz — TF Client 1.3.29
Lo que pidió viendo la 1.3.28 (con capturas):
1. **Leyendas de objetos** solo en **Tienda y Kits** y a 2/3 de tamaño (`PadViewPage.renderOver`); en el resto son
   iconos y no salen (ni las cabezas).
2. **Oficios sin mensajes en pantalla**: fuera el actionbar «Leñador 1 +2 xp +1,5 monedas (6/100)» (y la clave
   `avisoActionBar`) y el título grande al subir de nivel; queda el sonido y la línea en el chat.
3. **Tienda**: COMPRAR solo enseña lo que se compra (categorías sin nada a la venta no salen); lo que solo se vende va
   en VENDER (desde tu inventario). La ficha de un objeto ya no mezcla pestañas COMPRAR/VENDER.
4. **Economía estable** (`tools/gen_economia.py` genera los JSON por defecto): lo que paga cada acción de oficio ÷5–10
   (una hora de trabajo ≈ 40–150 monedas), subir de nivel 10 + 3/nivel, hito 50 = netherita + 1.500, misiones de oficio
   ≈ 10 % de antes (y piden un 50 % más), Misiones del pad 10–35, Cazas 30–90, recompensas y kits por defecto más bajos.
   La tienda por defecto ya era «tipo dólar» (diamante 150). Las configs viejas se guardan como `*.antes-v2.json` y se
   cambian solas (`TFConfigDir.upgrade`). Kits, recompensas y tienda del servidor (los configura el dueño) no se tocan.
5. **Colchón de 3 meses** (`util/TFRotation`): temporadas de 90 días; de cada lista sale cada misión una vez por vuelta
   y en cada vuelta con más cantidad y premio (x1 → x2,5), así en una temporada no se repite ninguna igual.
   - Oficios: las misiones «diaria» son **plantillas** (154 en total); cada día a cada jugador le tocan 3 de las de su
     nivel (`TFJobsConfig.missionsFor`; se guardan en oficios.json «hoy»/«hoyMisiones»; id «h<día>_<plantilla>»).
   - Misiones del pad: 60 diarias (3 al día por jugador) y semanales (3, x5 cantidad, x4 pago).
   - Cazas: 27 presas, 4 cada 12 h; **configurables desde el pad de admin, app CAZAS** (`PadAdminHunts`): mob (vale el
     id de un mob de mod, #etiqueta, hostil/animal), cantidad, monedas, descripción ({n}) e icono.
6. **Comunidad: reacciones** risa, wow, triste, fuego y top (iconos `emo_*.png`), una por jugador y foto (se cambia o
   se quita), además del corazón; contadores encima del like.
7. **Música**:
   - **Playlists**: pestañas TODAS / cada lista / «+» (crear); el + de cada canción abre un menú para meterla o sacarla
     de tus listas; dentro de una lista, el − la saca; la X de la pestaña borra la lista. Siguiente/anterior recorren la
     lista que suena (`MusicLibrary.queue()`).
   - **Altavoz** (botón al final de los controles): los jugadores a menos de 32 bloques oyen tu música situada en ti,
     más fuerte cuanto más cerca. Cómo va: el cliente avisa al servidor (link, por dónde va; cada 10 s se resincroniza),
     el servidor (`PadSpeakers`) se lo cuenta a los que están cerca y a los que llegan, y cada cliente (`MusicSpeaker`)
     baja la canción **del mismo link** (caché musica/altavoz/, 30 como mucho) y la reproduce en mono (`MonoDecoder`)
     en la categoría Discos. Solo funciona con canciones que vinieron de un link. En Ajustes: «Oír los altavoces de
     otros» (SÍ/NO).
   - Para que no se rompa: parar vale siempre; el servidor acepta 4 avisos/s y una canción nueva cada 2 s; el
     recordatorio de cada 10 s no corta lo que ya suena (solo si va desfasado más de 1,5 s); los links de otros solo se
     bajan de internet (nunca 127.x, 10.x, 192.168.x, etc., ni puertos raros: `MusicDownloader.checkPublic`), como
     mucho 2 descargas a la vez, y un link que falla no se reintenta en 2 min.
   - Pestañas de playlists: si no caben, flechas < > (la elegida siempre se ve, el + siempre al final).
8. **Misiones de oficio a medianoche**: las de ayer hechas sin cobrar siguen saliendo (y se cobran) al día siguiente
   (`JobProgress.prevDay/prevIds`, en oficios.json «ayer»/«ayerMisiones»).

### Entrega anterior (9 de octubre de 2026): interior de las apps con diseño nuevo, ventana centrada, fotos nítidas — TF Client 1.3.28
Viendo la 1.3.27 dijo que el interior de las apps era «mediocre y simplista»: el blanco está bien, pero quería
**textura** y **mejor orden visual**; además, **nada de números oscuros sombreados**, las **fotos se veían borrosas**
(al publicarlas y en la vista previa) y, a mitad del trabajo, que **la GUI estaba descentrada** (más hacia la derecha).
1. **Una sola ventana por app** (`PadViewPage`): pestañas **de carpeta** encima (la elegida baja y se une a la
   ventana, blanca con filo de oro; las otras, más bajas y azuladas); dentro, de arriba abajo: **barra de información**
   (lo que antes era el texto suelto sobre el cristal), la **cabecera grande**, la lista y abajo una **barra de
   acciones** con los botones y el campo. Comunidad usa las mismas pestañas.
2. **Textura**: `pattern.png` (rombos de cristal tallado muy suaves, 16x16, se repite sola con GL_REPEAT) en la
   ventana, las vitrinas y las cabeceras; **escuadras de oro** en las esquinas de la ventana.
3. **Tarjetas con vitrina**: arriba, un fondo tintado del color de la tarjeta con la textura, una luz (`glow.png`)
   detrás del objeto al doble y su sombra (`floor.png`); abajo el nombre **en negrita** y una **etiqueta**.
4. **Etiquetas con sentido** (`PadUi.chip`/`tone`): **oro** = precio, con el icono de moneda (`coin_s.png`) —
   el servidor manda `PadView.money(v)` → «¤1.250» y el pad cambia «¤» por la moneda; **verde** = lo que ganas o lo
   que está listo («+¤48», «Listo»); **azul** = estado o nivel; **gris** = no disponible. `Cell.tone` fuerza uno.
   ¡Ojo! «¤» solo se entiende al principio de una etiqueta (sub de tarjeta, badge de fila o de cabecera): en líneas,
   cabeceras o avisos saldría tal cual.
5. **Filas**: medallón del color de la fila, título en negrita, etiqueta a la derecha. **Cabecera grande** como banner
   tintado con el objeto en su medallón con luz.
6. **Cantidades**: el número va en blanco sobre una etiqueta azul noche, sin sombra (`PadUi.item`).
7. **Fotos nítidas**: se publican a **1280x720** (si pesan más de 1,8 MB, 960x540 o 640x360) y la vista previa de la
   cámara va a **960x540**; todas se dibujan con **filtro lineal** (`PadCameraPage.smooth`). Límites: `MAX_PHOTO`
   1,8 MB, servidor hasta 1280x720, 12 fotos en memoria de vídeo. Las fotos ya publicadas antes siguen a 480x270.
8. **Ventana centrada**: `PadPage.W = unitsW - 2·X` (mismo margen que el del logo a los dos lados); la portada sigue
   usando todo el ancho (`PadHomePage.gridW()`). Tarjetas desde 76 de ancho para que quepan 4 por fila.
9. Arreglos de la revisión: la foto se sube **un trozo por tick** (`PadCommunityClient.tick()`, llamado desde
   `TFPadClient`; ~560 KB/s, para no atascar la conexión) y las fotos de Comunidad se **leen en segundo plano**
   (sin tirones al desplazar); el servidor manda como mucho **12 MB de fotos cada 10 s** por jugador; las fotos no se
   repiten en los bordes (clamp); en las cabeceras, el monedero, el ranking y el admin el dinero va **exacto**
   (`PadView.moneyExact`), y abreviado (`money`, «¤250K») solo en tarjetas y filas.
10. Simulador (`tools/pad/sim`) al día con todo esto (`texture`, `chip`, `tone`, `infobar`, `dock`, `title`…);
   texturas nuevas en `build_pad.py` (`pattern`, `glow`, `floor`, `coin_s`).

### Entrega anterior (9 de octubre de 2026): portada centrada, apps por dentro otra vez — TF Client 1.3.27
Viendo la 1.3.26 en el juego pidió (y le gustó lo demás, «los iconos ya quedaron bien»):
1. **Los iconos no estaban centrados en la pantalla** → `PadHomePage.at()`: la rejilla se centra en **todo el
   cristal** (`SW`), no solo en la zona a la derecha del logo; si la primera fila fuera a pisar el logo se aparta
   (`pad.logoRight(top())`). El recorte y el ratón de la portada van ahora de 2 a SW-2.
2. **El GTS le dejó vender sus alas de crates ligadas a él** → `TFMarket.whyNot`: se bloquea **todo objeto de un set**
   (`TFItemTypes.setOf(item) != null`), también los cosméticos (alas, etc.), y lo que lleve dueño. Vale para el GTS y
   para los regalos de Jugadores (usan el mismo `whyNot`). Lo que ya estuviera publicado antes sigue en el GTS hasta
   que se retire o caduque.
3. **El contador del planeo se solapaba** → `TFGlideHud`: ya no hay hueco ni segundos; solo una **barrita** de 30x2
   bajo la mira (verde → ámbar → rojo; en rojo apagado si gastaste el planeo y sigues en el aire).
4. **Tienda → comprar: el color del precio confundía, mucho espacio vacío, nada centrado** → la ficha de un objeto
   lleva una **cabecera grande** (el objeto al doble, lo que dice, «Lote de 16 · llevas 5», «Tienes X monedas» y el
   precio del lote en una etiqueta de oro) y debajo **una tarjeta por cantidad** (1, 8, 32, 64 lotes: «8 uds.» /
   «8 lotes · 128» y el precio en palabras, «7.600 monedas»); las que no puedes pagar dicen por qué en gris («Te faltan
   18K», «Límite de hoy»). Vender igual, con «+…» en verde. Las categorías también ponen el precio entero.
5. **GTS en lista, no en tarjetas** → MERCADO es una lista ordenada: objeto, «Lo vende Steve · quedan 3 días», precio
   y COMPRAR (o SIN SALDO; RETIRAR si es tuyo, marcado en oro); cada fila abre la ficha (cabecera grande con el precio y
   debajo lo que dice el objeto).
6. **Esa vista de tarjetas grandes, para los oficios, con su progreso bien puesto** → Oficios: una tarjeta por oficio
   (icono al doble, nombre, «Nivel 12 · tuyo», barra con el %; la tuya en oro; burbuja verde «+2» si tienes misiones
   para cobrar). Dentro de un oficio, cabecera grande con la descripción, «Nivel 12 de 50 · 340/1200 xp» y su barra.
7. **Auditar todas las apps** → en `PadViewPage`: las tarjetas **llenan el ancho** (y si son pocas se agrandan hasta
   116 y van centradas), y si solo hay tarjetas y caben, **centradas en alto**; las casillas pequeñas, centradas si son
   pocas; una ficha que solo tiene cabecera grande sale centrada en el hueco (sin ventana vacía). Ficha de un kit, de un
   día del calendario y de una recompensa: cabecera grande + lo que trae en tarjetas (`PadKits.contents`; más de 8
   cosas, en casillas pequeñas para que se vean todas).

Piezas nuevas (para seguir): `PadView.hero` (una `Row`; se manda antes de las casillas), `Cell.progress` y
`Cell.badge` (solo tarjetas; `Builder.card(icon, name, color, sub, progress, badge, action, selected)`), `PadUi.pill`
(etiqueta oro/verde/gris). En las tarjetas, la segunda línea va en oro; en verde si empieza por «+»; en gris si la
tarjeta no se puede pulsar. El simulador (`tools/pad/sim`) ya dibuja todo esto (`hero=`, tarjetas con `{prog, badge,
off}`) y tiene páginas nuevas: p_tienda_comprar, p_gts_item(_solo), p_oficio.

### Entrega anterior (9 de octubre de 2026): interior de las apps rediseñado, Recompensas y Web — TF Client 1.3.26
Viendo la 1.3.25 en el juego pidió: 1) los **números de cantidad** salían enormes; 2) la **cabeza** de Jugadores no
salía con su skin («conecta con las skins de todos»); 3) apps **pegadas y abultadas** → pad más grande; **quitar las
partículas**, animaciones correctas (no solo girar) y **el engranaje sin animación**; 4) **rediseño bonito del interior
de las apps** con botones de navegación bonitos (no los iconos); 5) **rework del icono de Oficios** («ese pico más
mierda»); 6) **quitar Ayuda**, app **WEB** (directo a la página) y app **RECOMPENSAS** (diarias o como quieran los
admins, configurables en el pad de admin); **quitar el kit diario** de Kits; kits y recompensas se montan con el
inventario o con **todos los objetos del juego, mods incluidos**.
Hecho:
- **Cantidades** (`PadUi.item`): el objeto a escala k (tarjetas al doble) y el número siempre a tamaño normal.
- **Cabezas con skin**: `PadClans.head` pone en `SkullOwner` el perfil completo con la propiedad `textures` (del
  jugador conectado o la última skin vista, que `PadStats.name` guarda como `skin`/`skinFirma`; vale con
  SkinsRestorer). Respaldo en el cliente: `PadUi.skin` usa el perfil de la lista de jugadores. Lo usan Jugadores,
  Clanes y miembros de Protección.
- **Pad más alto sin deformar** (primero se ensanchó y el dueño lo corrigió: «debiste expandir verticalmente, no
  horizontal», para separar las filas de apps): `TFPadScreen.drawFrame` repite una fila lisa de arriba y otra de abajo
  de `frame.png` (`STRETCH_U = 430`, `STRETCH_D = 596` en píxeles de la imagen, por encima y por debajo de los adornos
  de oro de los lados; `extra` píxeles de pad arriba y abajo, hasta `MAX_EXTRA`, según el alto de la pantalla: a 1080p
  +7 por lado, el cristal pasa de 264 a 292 unidades de alto).
- **Portada** (`PadHomePage`): 7 columnas (las 21 apps en 7x3 a 1080p), nombres a escala normal, aire repartido; sin
  halo, partículas ni destellos. Al pasar el ratón: la ficha sube con sombra y el icono da **un salto con aplastado y
  estirado**; luego respira, o hace su gesto (el pico golpea, la ballesta retrocede, estandarte y percha se mecen, la
  cámara hace clic, Música late). El engranaje de arriba ya no se mueve (solo un subrayado de oro).
- **Kit de interfaz nuevo, en BLANCO** (`PadUi`; primero se hizo en azul noche y el dueño lo corrigió: «yo la quería
  blanca pero rediseñar los elementos en ella»): ventanas blancas con borde de oro y tachuelas (`panel`), tarjetas en
  relieve (`card`: normal, ratón en azul claro, marcada en crema con oro), ranuras hundidas para objetos (`slot`),
  botones con degradado, luz y sombra (16 de alto; los ATRÁS llevan flecha), pestañas doradas (`tab`, 14), títulos de
  sección con línea y rombo (`divider`), barra de desplazamiento de oro, campos y barras hundidos. Textos en azul
  marino (`TEXT`), gris azulado (`MUTED`) y oro (`GOLD_TEXT`). Aplicado en `PadViewPage` (todas las apps del servidor y
  del admin), Comunidad, Cámara, Música (el reproductor sigue en su tarjeta oscura), Ajustes y la barra de arriba.
- **Iconos** (`tools/pad/iconos3.py`): Oficios = pico de diamante grueso (cabeza en media luna sombreada por tonos,
  abrazadera de oro, mango de 3 px); RECOMPENSAS = regalo azul con lazo; WEB = globo terráqueo con el puntero del
  ratón. Ayuda ya no está en el pad (su config sigue, sin usarse).
- **WEB**: app del cliente; abre `TFConfig.webUrl()` en el navegador al momento.
- **RECOMPENSAS** (`pad/server/PadRewards.java`, config `config/tfclient/recompensas.json`, datos del jugador en
  `<mundo>/tfclient/recompensas.json`): pestaña DIARIA = calendario (7 días de partida) con racha: cada día natural se
  reclama el siguiente; si se salta un día vuelve al día 1 (o sigue, si el staff lo cambia); tras el último, vuelta a
  empezar. Pestaña OTRAS = recompensas sueltas (diaria, semanal, cada X horas, una vez), con su ficha y RECLAMAR.
  Primero se pagan las monedas y solo si salen bien se marca cobrado.
- **Kits sin «diario»** (`PadKits.TYPES` = unico, semanal, horas): los kits diarios que hubiera se pasan solos a
  Recompensas como recompensas diarias la primera vez que carga (`PadRewards.adopt`).
- **Pad de admin**: app **RECOMPENSAS** (`PadAdminRewards`): CALENDARIO (encender, qué pasa si se salta un día, cada
  día con monedas y objetos, añadir día / quitar el último) y OTRAS (crear, nombre, activada, tipo, horas, monedas,
  icono de la mano, objetos, borrar). **Selector de objetos** (`PadItemPicker`, también en KITS): TU INVENTARIO (tal
  cual, con cantidad y encantamientos) o TODOS LOS OBJETOS del juego y de los mods (registro de objetos), con
  buscador por nombre o id (en el servidor los nombres son en inglés; el id siempre vale), páginas de 96, «» para pasar de página
  y ×N para la cantidad (1/4/8/16/32/64); pulsar un objeto de la ficha lo quita. Los kits diarios migrados conservan lo
  ya reclamado; si recompensas.json está roto no se migra nada.
- Simulador (`tools/pad/sim/`) al día: marco ancho, portada nueva, kit de interfaz nuevo, Recompensas, admin y selector.

### Entrega anterior (9 de octubre de 2026, noche): iconos grandes, fichas RPG animadas, apps rediseñadas y Música — TF Client 1.3.25
Pidió: los iconos de la portada **más grandes** (no estaban ajustados al pad nuevo), **mejor textura de las fichas** con
**animaciones tipo RPG según el icono**, **rediseñar el interior de cada app** («más bonitas, ajusta todo al tamaño
nuevo») y una app de **Música** como el mod Etched: pegar un **link directo** (Google Drive convertido…), que se
**descargue y se guarde**, reproductor completo con la **imagen de la canción**, **volumen**, que **siga sonando con el
pad cerrado** hasta apagarla en la app, y que **solo la oiga él**. Además, un MP3 gratis con link directo para probar.
Hecho:
- **Escala grande** (`TFPadScreen.big/bs`): px reales por píxel de textura = max(cs, round(ps·0,75)); bs = big/cs (1,5 a
  1080p, 4/3 a 1440p…). Siempre píxeles enteros: nítido. La barra de arriba (◀, título, hora, monedas, engranaje y un
  ecualizador cuando suena música, que abre Música) va a esa escala; `bar = round(12·bs)+6`.
- **Portada** (`PadHomePage`): fichas de 40 px a escala grande (60 unidades), rótulos grandes, 7 columnas: las 20 apps
  caben sin desplazar a 1080p. **Fichas nuevas** (`build_pad.py tile()`): marco de oro biselado, engaste oscuro, cara de
  cristal azul con brillo y tachuelas con gema azul en las esquinas; hover más claro. **Animaciones por app** al pasar el
  ratón: el pico pica y saltan chispas, la moneda gira, el cofre y el saco saltan, sale humo de la chimenea, el escudo
  late, el estandarte ondea, la percha se balancea, la ballesta retrocede y dispara, la cámara hace flash, el engranaje
  gira, Música late al ritmo de la canción que suena…; halo dorado, partículas y un destello que cruza fichas al azar.
- **Apps a escala** (mecanismo general en `TFPadScreen.enter/exit`): toda página con `scaled()` (todas menos la portada)
  se dibuja con la pose ×bs y, mientras se dibuja o recibe el ratón, `PadPage.X/Y/W/H/SW/SH/OX/OY` están en «unidades de
  vista» (zona de la app desde 0,0; a 1080p 353x154). `pad.scissor` convierte solo. **Rediseño** (`PadViewPage`,
  `PadUi`): filas como tarjetas separadas (borde, degradado, barra de color al seleccionar/pasar), títulos de sección con
  línea y rombo de oro, cabecera con rombo, casillas tipo inventario 30x32, tarjetas 84x66 que se elevan y cuyo objeto
  flota, paneles con degradado, botones con destello al pasar el ratón, barras de progreso con degradado, pantallas
  vacías con el icono de la app.
- **Música** (`pad/PadMusicPage` + `pad/music/`), solo cliente:
  - Descarga (`MusicDownloader`): convierte links de compartir de Google Drive (→ `drive.usercontent.google.com/download
    ?id=…&export=download&confirm=t`, y sigue el botón de «descargar de todos modos»), Dropbox (`dl=1`) y GitHub (raw);
    sigue redirecciones; máximo 250 MB; comprueba que sea MP3/MP4/AAC/OGG/WAV de verdad y que se pueda reproducir.
  - Etiquetas y portada (`MusicTags`): ID3 v2.2–2.4 + v1 (título, artista, APIC) y comentarios Vorbis (con
    METADATA_BLOCK_PICTURE); la portada se guarda a 256x256 PNG (sin Graphics2D). Botón PORTADA: imagen desde un link.
  - Biblioteca (`MusicLibrary`): `.minecraft/tfclient/musica/` (`<id>.mp3|ogg|wav`, `<id>.png`, `biblioteca.json` con
    la lista y volumen/aleatorio/repetir). Borrar con confirmación.
  - Decodificadores (`AudioDecoder`): MP3 con **JLayer** (desde la 1.3.63; JavaMP3 distorsionaba), duración con
    `Mp3Frames` (recorre cabeceras); MP4/M4A con `Mp4Audio` + JAAD (`AacDecoder`), .aac con `AdtsDecoder` (1.3.64);
    OGG con stb_vorbis (el de Minecraft); WAV 8/16/24/32 bits y float.
  - Sonido (`MusicStream`/`MusicSound`/`MusicPlayer`): es un sonido del juego (Forge `SoundInstance.getStream`),
    categoría principal, sin posición; play/pausa (para al momento), anterior/siguiente, salto arrastrando la barra,
    aleatorio, repetir (todas/una/no), volumen (barra vertical, rueda, clic en el altavoz = silencio). Sigue sonando con
    el pad cerrado; calla la música de fondo de Minecraft mientras suena; si el motor de sonido se reinicia, sigue donde
    iba; al salir del servidor se pausa. «Now playing» de Minecraft al empezar cada canción. Ecualizador que mide el
    volumen real de la canción cada 50 ms. Teclas: espacio, ←/→ (5 s), Ctrl+V pega el link.
  - Probado fuera del juego: decodificación entera (≈55× tiempo real), salto exacto bit a bit, descarga real con
    redirecciones, etiquetas y portada.
- Canción de prueba en la web: `tierras-fantasticas/public/musica/epic-rpg.mp3` («Epic Computer Game Music» de Kris
  Klavenes, dominio público, con título/artista/portada puestos por nosotros; ver `LEEME.txt` al lado).
- Simulador (`tools/pad/sim/`): ahora dibuja en píxeles reales con la escala grande (`Sim.big_at`, `Local` en pages.py)
  y tiene la portada nueva, las apps a escala y Música.

### Entrega (9 de octubre de 2026): pad grande, pad de administrador y config en carpeta — TF Client 1.3.24
Pidió: tableta más grande, quitar las líneas azules del cristal que se solapaban con los recuadros, **nada de textos
genéricos** («la web no ha mandado…»), centrar «TF PAD», **quitar los títulos** (van en el sitio del prefijo de rango;
el del clan sí se queda), menús más visuales y con scroll **vertical**, tienda que solo compra lo que pongan ellos (nada
de granjas), Viajes con **todos los warps** del servidor, kits inicial (1 vez) / diario / otros, un **pad de
administrador físico** que configure todo el pad, la config del mod **en una carpeta**, sin `/tf claims` (se dan piedras
por kit), **regalos** dentro de Jugadores. Además: la foto no salía en la vista previa, Comunidad «se juntaba tipo
mosaico», el corazón estaba mal dibujado y la foto debía tomarse con **clic izquierdo**, no con C.
Hecho:
- **Pantalla** (`TFPadScreen`): el marco a escala entera (`ps`, lo máximo que cabe al 97 %) y el contenido en «unidades»
  a `cs` = la mitad (a 1080p: marco ×4, contenido ×2 → 576×264 unidades, el doble de sitio que antes y nítido). Recorte
  con `pad.scissor/noScissor` en píxeles reales. `PadPage.X/Y/W/H/SW/SH` ya no son constantes: se calculan en `init()`
  (X = a la derecha del logo a la altura Y, `logoRight(y)`). Barra: ◀ junto al logo, título **centrado** en el cristal,
  hora, monedas y ajustes. Avisos abajo centrados. Arrastrar con el ratón baja la lista (`PadPage.drag`).
- **Cristal** (`tools/pad/marco.png` → `frame.png`): repintado con un degradado limpio; sin líneas ni cuadritos.
- **Portada** (`PadHomePage`): rejilla que baja (rueda, arrastre, flechas), hasta 8 columnas, barra lateral. Sin Títulos
  (`PadTitles.ENABLED = false`; la etiqueta del clan se mantiene). Las apps apagadas por el staff no salen
  (`TFPadNet.State.disabled`).
- **Interfaz** (`PadView`/`PadViewPage`): **tarjetas** 96×74 (`Builder.cards()` + `card(icono, nombre, color, sub,
  acción, sel)`: franja de color, objeto al doble, nombre y línea dorada), casillas 44×46 con el objeto al doble, filas con
  icono al doble en recuadro de 36 y texto centrado. Usan tarjetas: Viajes, Kits, Jugadores, Tienda (categoría), GTS
  (mercado) y las del admin. Textos repasados en todas las apps: cortos y concretos.
- **Comunidad** (`PadCommunityPage`): lista vertical, cada publicación su tarjeta (foto 16:9 grande, autor, cuándo, texto,
  ♥ likes, BORRAR/DENUNCIAR); clic en la foto = en grande. Corazón nuevo 9×8 con contorno (`build_pad.py heart()`).
- **Cámara** (`PadCamera`, `PadCameraPage`): **clic izquierdo** dispara (cancela el golpe), C ya no; la vista previa sale
  al momento desde memoria (`PadCamera.preview`) y reintenta leer el archivo si aún se está escribiendo.
- **Config en carpeta** (`util/TFConfigDir`): todo en `config/tfclient/`: `cliente.properties`, `pad-cliente.properties`,
  `servidor.properties`, `puente-entregas.txt`, `pad.json`, `tienda.json`, `tienda-monedas.json`, `ruleta.json`,
  `oficios.json`, `misiones.json`, `kits.json`, `ayuda.json`, `vfx.json`, `limites.json`, `protecciones.json`. Si encuentra
  el archivo viejo (`config/tfclient-*.…`, o `claimblocks_config.json` del mundo) lo **mueve** solo la primera vez.
- **`pad.json`** (`pad/PadConfig`): ajustes (`viajes.espera`, `viajes.cuentaAtras`, `viajes.warps`, `explorar.*`,
  `hogares.sinRango`, `monedero.enviar`, `monedero.maximo`, `regalos.activado`, `regalos.porDia`,
  `gts.maxPublicaciones`, `gts.dias`), `appsApagadas` y `warpsOcultos`. El servidor bloquea las apps apagadas
  (`PadServer.usable`).
- **Pad de administrador** (`pad/AdminPadItem`, objeto `tfclient:pad_admin`, se da con `/tf web pad admin [jugador]`,
  nivel 3; si lo lleva alguien sin permisos, desaparece). Abre `TFPadScreen(true)` con las apps `a_*`
  (`pad/server/PadAdmin.java`, solo nivel 3): **APPS** (encender/apagar cada app), **AJUSTES** (los de pad.json +
  RECARGAR CONFIGS), **TIENDA** (categorías, objetos desde la mano, precio de compra/venta, cantidad por lote, límites
  diarios, nombre, icono; lo que tiene precio de venta es lo **único** que la tienda compra), **KITS** (crear con tu
  inventario, tipo unico/diario/semanal/horas, horas, monedas, icono, objetos), **VIAJES** (puntos: poner aquí, mover,
  icono, renombrar, borrar; warps visibles/ocultos), **GTS** (retirar publicaciones), **COMUNIDAD** (denunciadas y
  recientes: borrar o «está bien»), **OFICIOS** (ajustes generales de oficios.json).
- **Viajes** (`PadTravel`, `EssentialsWarps`): Spawn, tu cama, puntos, **todos los warps de EssentialsX**
  (`plugins/Essentials/warps/*.yml`, ejecuta `/warp <id>` como el jugador), Explorar y Tus hogares. Esperas y distancias
  desde pad.json.
- **Kits** (`PadKits`): tipos `unico` (una vez, el inicial), `diario` (cada día natural), `semanal`, `horas`; ficha con lo
  que trae y RECLAMAR.
- **Regalos** dentro de **Jugadores** (`PadPlayers`): tarjeta del jugador → FICHA / REGALAR: un objeto del inventario
  (no los vinculados) o monedas, con límite diario; si está desconectado le llega al entrar (`regalos.json` del mundo).
- **Protecciones**: sin `/tf claims` (las piedras se dan por kit); invitaciones de grupo y salir del grupo en la app.
- **Tienda por defecto** (`tfclient-shop-default.json`): solo compra diamante, chatarra de netherita y lapislázuli.
- Simulador (`tools/pad/sim/sim.py`, `pages.py`) al día con la geometría nueva (unidades, tarjetas, portada, admin).

### Entrega (8 de octubre de 2026): todo dentro del TF Pad, sonidos suaves y auditoría de píxeles — TF Client 1.3.23
Pidió: auditar el pad (textos, marcos y recuadros solapados), que las apps **no manden a la web ni a otras ventanas**
(GTS, oficios… «todo debe hacerse desde la app»), meter **hogares**, sonidos de cambio de pantalla menos fuertes y
**sin campana**, arreglar Cámara y Publicar, quitar los **píxeles blancos del contorno** del marco, y el icono de
hogares **una casa** («audita bien que no queden píxeles mal, contornos cortados ni solapes con la pantalla»).
Hecho:
- **Marco de apps del servidor** (`pad/PadView`, `PadViewPage`): además de filas, una **rejilla de casillas** (objeto +
  texto pixel debajo, como un inventario), filas que se pulsan enteras y salen resaltadas, pestañas que **siempre
  caben** (se estrechan y recortan), cabecera de 1 línea con pestañas y 2 sin ellas, todo texto largo acaba en «…»
  (`PadUi.wrapEllipsis`/`fitEnd`), campo de texto propio, «CARGANDO» con ruedita. Acciones `§open:<app>` abren otra app;
  Esc/◀ dentro de una ficha vuelve a la pantalla anterior de la app (botón ATRÁS) antes que a la portada. El contenido
  empieza en Y=84 (no toca el logo TF). Sesiones por jugador y app (`PadServer.get/put`) y doble clic «¿SEGURO?»
  (`PadServer.confirm`). El servidor puede abrir el pad en una app: `TFPadNet.openApp(player, app, tab)`.
- **Apps nativas nuevas** (`pad/server/`): `PadJobs` (oficios: lista, MISIONES con COBRAR, CÓMO SE GANA, PREMIOS,
  trabajar/cambiar/dejar), `PadShop` (tienda: categorías → rejilla con precios → ficha con cantidades, input de lotes,
  pestaña VENDER con lo que llevas y VENDER TODO), `PadMarket` (GTS: mercado en rejilla con buscador, ficha con
  COMPRAR/RETIRAR, VENDER eligiendo del inventario y escribiendo el precio **sin chat** (`TFMarket.publish`), MIS VENTAS,
  RECOGER), `PadClaims` (Protección: tus zonas → AJUSTES con interruptores y ventajas bloqueadas por tamaño, MIEMBROS,
  BANEOS, PARTÍCULAS en rejilla con densidad, MÁS: mensajes de entrar/salir, contorno, grupo e invitar, eliminar),
  `PadHomes` (hogares de EssentialsX: lee `plugins/Essentials/userdata/<uuid>.yml` y ejecuta /home, /sethome, /delhome
  como el jugador; sin Essentials guarda los suyos en `hogares.json` con el límite del rango), `PadWardrobe` (Armario por
  hueco y Efectos de kill/skills, se ponen al momento y se guardan en la web), `PadAccount` (Monedero: saldo, cobro del
  oficio, ventas del GTS, mandar monedas a otro jugador con confirmación; Mi rango: lista de rangos con sus ventajas).
  Explorar pasó a ser una fila de **Viajes** (con «Tus hogares»); su sitio en la portada es **HOGARES**.
- `/tf jobs`, `/tf shop`, `/tf claims`, `/tf claims menu` y el clic en la piedra abren el pad en su app (sin TF
  Client, las ventanas de siempre). Los mensajes de la tienda y del GTS salen como aviso del pad (`TFShop.toPad`,
  `TFMarket.toPad`). Se quitaron las páginas cliente Monedero/Rango/Web (`PadInfoPages` solo tiene Ajustes).
- **Web** (`src/app.js`, puente): la consulta acepta `pad: {wardrobe:[{uuid,slot,set,id|null}], vfx:[{uuid,kind,id|null}]}`
  (solo conectados; se valida que la pieza sea suya) y `padWant: [uuid]` → responde `padData: [{uuid, owned:[…]}]` con
  lo que tiene cada uno en el armario. `rankList` trae `perks` (ventajas en frases) para «Mi rango». Test nuevo.
- **Sonidos** (`tools/pad/build_sounds.py`, rehechos): sin campanas ni brillos metálicos; burbujas, pulsos cálidos
  filtrados, soplos de aire muy bajos y un tic de madera para las pestañas (`pad.tab`, nuevo). Más bajos de volumen.
- **Cámara**: la foto mide 144x81 y MODO FOTO va debajo (antes la pisaba); Esc en modo foto vuelve a la Cámara del pad
  (antes abría el menú de pausa); la subida nunca se queda en «Subiendo…» (a los 30 s sin respuesta avisa).
  **Comunidad**: la foto ya no se sale del panel.
- **Marco**: `tools/pad/limpiar_bordes.py` quita los píxeles grises/blancos del contorno exterior (restos del fondo
  blanco) y deja una línea oscura limpia (marco.png → frame.png).
- **Iconos**: HOGARES es una **casa** dibujada a mano (tejado de tejas, chimenea, ventanas encendidas, puerta, césped).
  `kit.Icon.outline` ya no deja que nada toque el borde del lienzo (Cazas, Títulos y Viajes tenían el contorno cortado).
  Auditoría: 0 iconos con contorno roto, 0 píxeles semitransparentes.
- Simulador (`tools/pad/sim/`) al día con el layout nuevo y las 20 vistas (oficios, tienda, GTS, hogares, armario,
  efectos, protección, monedero, rango, viajes, misiones, comunidad, cámara).

### Entrega anterior (8 de octubre de 2026): TF Pad completo, 20 apps, Cámara y Comunidad — TF Client 1.3.22
Pidió: quitar los «puntos amarillos» (los iconos de la barra de arriba sin números), iconos **simétricos**, **todas**
las apps (clanes, kits, misiones, viajes…) y que **Comunidad sea una red social**: una cámara que guarda las fotos en
su cliente, publicarlas y que los demás les den like. «Revisa cómo funciona el pad de Diosesmon».
- **Inicio:** 20 apps en **2 páginas de 5x2** (rejilla simétrica: centros x 84/140/196/252/308, filas 82 y 136,
  casillas de 38 px). Se pasa de página con la rueda, las flechas, arrastrando o el indicador de abajo (y se desliza).
  El indicador: la página actual es una **barrita dorada** y la otra un punto azul (nada de puntitos amarillos sueltos:
  el dueño los confundió con fallos).
  Página 1: Oficios, Misiones, Cazas, Tienda, GTS, Monedero, Clanes, Viajes, Explorar, Kits. Página 2: Comunidad,
  Cámara, Jugadores, Ranking, Títulos, Armario, Efectos, Mi rango, Protección, Ayuda.
- **Barra de arriba:** título y botón atrás; a la derecha hora (sol/luna), **monedas con número** y el engranaje de
  **Ajustes** (sonidos, tic al pasar, animaciones y volumen; `config/tfclient-pad.properties`). Fuente pixel propia con
  tildes (`tools/pad/fuente4.py`). Esc o Retroceso vuelven al inicio.
- **Vistas del servidor** (`PadView` + `PadViewPage`): el servidor manda pestañas, cabecera, filas (icono, título,
  líneas, barra, premio, 2 botones), botones de abajo y un campo de texto; los clics vuelven como `Action`. Así se
  hicieron casi todas las apps nuevas (`pad/server/*`, registradas en `PadServer`; máx. 12 acciones/s por jugador).
- **Sistemas** (datos en `<mundo>/tfclient/`, se guardan cada 10 s si cambian; un JSON ilegible se copia aparte):
  - **Misiones** (`config/tfclient-misiones.json`): 3 diarias y 3 semanales por jugador, y **Cazas**: 4 cada 12 h.
    Cuentan con lo que ya mide Oficios (`TFJobs.record` → `PadMissions.record`), solo en supervivencia/aventura.
  - **Kits** (`config/tfclient-kits.json`, **siempre gratis**): inicial, diario y semanal, con vista previa.
    Staff: `/tf web kits guardar|quitar|recargar`.
  - **Viajes**: spawn, tu cama y puntos del staff (`/tf web viajes poner|quitar|lista`); 3 s quieto (se cancela al
    moverte o recibir daño), 30 s de espera. **Explorar**: sitio al azar a 800–6000 bloques, fuera de zonas y agua; 5 min.
  - **Clanes**: fundar (nombre y etiqueta de 2–4), invitar, rangos, expulsar, pasar el liderazgo, color, fuego amigo,
    máx. 20. **Títulos**: 12 que se ganan jugando; salen así: «Título» Nombre [TAG] (chat y lista de jugadores).
  - **Jugadores** (conectados y su ficha), **Ranking** (misiones, cazas, likes, exploraciones, monedas), **Ayuda**
    (`config/tfclient-ayuda.json`, se edita sin tocar el mod).
- **Cámara:** «MODO FOTO» esconde la interfaz; la **C** dispara, la foto se guarda en `.minecraft/tfclient/fotos` y el
  pad vuelve con un flash. Desde la Cámara: publicar, borrar o abrir la carpeta.
- **Comunidad** (`PadCommunity*`, canal del pad, paquetes 10–16): las fotos se recortan a 16:9 y se suben a 480x270
  (PNG, máx. 600 KB) en trozos; el servidor las guarda en `<mundo>/tfclient/comunidad/` (+ `comunidad.json`). Pestañas
  Recientes, Populares y Mías; like (avisa al autor), borrar las tuyas y denunciar (con 5 denuncias se oculta). 90 s
  entre fotos y 40 por jugador. Staff: `/tf web comunidad borrar|denuncias`. El cliente guarda en caché las que ya vio.
- **Sonidos nuevos:** `foto` (obturador) y `like`.
- **Iconos nuevos** calcados de Faithful 32x con nuestra paleta (`tools/pad/calco.py`, `iconos2.py`, referencias en
  `tools/pad/ref/`). Lección: **no inventar dibujos**; partir de un modelo bueno real y adaptarlo.
- **Vistas previas sin abrir el juego** (mándaselas al dueño como imagen):
  - Inicio: `python3 tools/pad/build_pad.py --preview` → `tools/pad/preview_home1.png` y `preview_home2.png` (no se
    suben a git: muévelas fuera antes de hacer commit).
  - Apps: `python3 tools/pad/sim/pages.py <carpeta>` → `p_misiones.png`, `p_kits.png`… y `hoja_apps.png`. Saca la
    fuente y los iconos de Minecraft del `client-extra.jar` de ForgeGradle (hay que haber compilado una vez). Los «?»
    son tildes que la fuente ascii no tiene; en el juego salen bien. Si mueves algo en `PadViewPage`,
    `PadCommunityPage` o `PadCameraPage`, cámbialo igual en `tools/pad/sim/sim.py`.
- **Revisión a fondo** (dos agentes con las fuentes de Forge 47.3 decompiladas) y arreglado antes de publicar:
  - Red: pestañas de hasta 64 caracteres (la ficha de Jugadores, `ficha:<uuid>`, no cabía en 32 y no se abría).
  - Viajes a la cama con `findRespawnPositionAndUseSpawnBlock(…, true)`: el último `true` es para **no** gastar la
    carga del ancla. Cualquier daño (`LivingHurtEvent`) cancela la cuenta atrás.
  - Misiones y kits: primero se paga y solo si `TFEconomy.give` sale bien se marca cobrado.
  - Clanes: la confirmación (doble clic) va ligada a la acción exacta; los oficiales tienen SALIR; un campo de texto y
    botones de abajo caben juntos (`PadViewPage`).
  - Explorar: descarta mares y ríos por el bioma sin generar el chunk, máx. 6 chunks generados por intento y 1 min de
    espera si falla.
  - Comunidad: máx. 6 peticiones/s por jugador, subidas a medias se tiran a los 2 min o al salir, `comunidad.json`
    ilegible se copia a `comunidad-roto-*.json`. En el cliente: like/borrar se cambian en local (sin volver a pedir la
    lista, para no saltar de foto), fotos ilegibles no se reintentan, el id que manda el servidor se valida, la
    confirmación de borrar va ligada a esa foto, y la foto no se hace si se abrió otra pantalla entre la C y el disparo.
  - `TFRanks.apply` rehace el nombre de la lista de jugadores al cambiar de rango (con título o clan lleva el prefijo).
- **Sin hacer (no pedido; solo si el dueño lo pide):** el progreso de «fabricar *» y «colocar» se puede farmear,
  invitaciones de clan sin tiempo de espera, Jugadores muestra como mucho 60, `misiones.json` se guarda entero cada
  10 s (con muchos jugadores puede pesar), Esc en modo foto sale al menú de pausa, y `ImgReq` lee del disco en el
  hilo principal (40 cada 10 s por jugador).

### Entrega anterior (8 de octubre de 2026): TF Pad con tecla C, sonidos propios y pico nuevo — TF Client 1.3.21
- **Pico (Oficios):** el de la 1.3.20 no le gustó («teniendo los modelos ahí»). Ahora está **adaptado de la estructura
  del pico de Minecraft / Faithful 32x** (`tools/pad/pico.py`): media luna fina (núcleo de 3 px que acaba en punta),
  simétrica respecto al mango, y el mango pasa por debajo y **asoma por encima del centro del arco**. Lección: cuando
  pida algo «como los modelos», **calca su estructura píxel a píxel** (medidas, grosores, curvas) y solo cambia la paleta.
- **Tecla C** (antes O). Coincide con «Guardar barra rápida» de vanilla (C + número, solo en creativo): no estorba.
- **Sonidos propios** (`tools/pad/build_sounds.py`, síntesis con numpy → .ogg con ffmpeg; `sounds.json` → `pad.*`):
  open (arpegio que sube, mi-sol#-si-mi), close, select, page, back y hover (tic suave). Revisados por espectrograma: sin
  chasquidos ni siseo.
- **Avisos dentro del pad** (paquete `Notice`): «no tienes zonas», «tienda cerrada»… salen en una tarjeta en el pad (el
  chat queda tapado por la pantalla). En el GTS los fallos se ven en el propio botón y suenan.
- Discord desde el pad va por `<web>/discord` (el enlace permanente de la web), con confirmación como WhatsApp.
- Revisión a fondo sin abrir el juego (agente con las fuentes de Forge 47.3 decompiladas): precio del GTS solo con
  números («1500», «1.500», «5k», «2m»; «1.5k» o «12,50» se rechazan), la pregunta del precio se saca en el hilo de red
  (nunca sale al chat público), `gts.json` con algo ilegible se copia a `gts-roto-*.json` y lo que no se lee se guarda
  tal cual, en el GTS solo cuentan clic y Mayús+clic, zona hija de un grupo → abre la nodriza, en creativo la C abre el
  pad al soltarla si no se usó para guardar la barra (C + número).

### Entrega anterior (8 de octubre de 2026): TF Pad y GTS — TF Client 1.3.20
Pidió un pad como el PokePad de Diosesmon con **su marco** (imagen suya) y **todo conectado**. Iconos: los quiere
**pixel perfect, claros (nada oscuros), bonitos y dibujados a mano**; rechazó prompts de IA, un pack CC0 («horribles»)
y un pico flojo: el pico se rehízo **estudiando picos de Minecraft, Terraria y Stardew** (brazos gruesos en media luna,
casquillo con gema, mango de 4 px). **Antes de dibujar algo, busca referencias buenas y compáralo con ellas.**
- **Abrir:** tecla **C** desde la 1.3.21 (`key.tfclient.pad`, se cambia en Controles). Solo funciona en un servidor con el canal del pad.
- **Apps (9):** Oficios, Protecciones, Tienda y GTS las abre el servidor (mismas ventanas que `/tf jobs`, `/tf claims
  menu` —la zona donde estás si es tuya, si no tu primera zona— y `/tf shop`). Monedero (saldo + atajos), Mi rango
  (rango y hogares), Comunidad (Discord, WhatsApp, web), Armario y Efectos (se eligen **en la web**, que es la que sabe qué
  tiene comprado cada uno: `/cuenta` y `/tienda#vfx`) son páginas del pad. Arriba: hora del mundo y monedas.
- **GTS** (`market/TFMarket*.java`, `<mundo>/tfclient/gts.json` + `gts-registro.log`): publicar lo de la mano y
  escribir el precio en el chat (va por `ChatPromptRouter`, como las preguntas de TF Claims; vale en Mohist), comprar,
  retirar, 7 días y vuelve a «Recoger», máx. 10 por jugador. Cobra al momento (si la economía no puede pagarle
  desconectado, cobra al entrar). **No** se publican piezas de sets ligadas, lo comprado en la web (`TFOrder`, para que
  los reembolsos sigan funcionando) ni contenedores llenos. Sin comandos nuevos: solo se abre desde el pad.
- **La tienda normal ya compraba a los jugadores** (clic derecho, «Vender todo», pulsar tus objetos): 58 de los 73
  objetos por defecto se pueden vender. Lo demás, en el GTS.
- **Texturas:** `python3 tools/pad/build_pad.py [--preview]` → `textures/gui/pad/` (dibujos en `tools/pad/`:
  `iconos.py`, `pico.py`, fuente pixel `fuente4.py`; `marco.png` es su imagen limpia). El marco son 392x251 «píxeles de
  pad» (1 = 4 px de `marco.png`); `TFPadScreen` lo escala a un entero de píxeles de pantalla. Si mueves algo en
  `build_pad.py`, cambia las mismas posiciones en `TFPadScreen.java`.

### Entrega anterior (8 de octubre de 2026): menú de inicio como antes, con el paisaje de noche — TF Client 1.3.19
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
- «dale, y no me pidas permiso para la otra» y (10 oct) «nunca me pidas permiso, sube siempre a main todo para que se
  actualice»: cuando el trabajo esté terminado y probado, **fusionar a `main` y publicar SIN PREGUNTAR NUNCA**. Flujo: PR en borrador → marcarlo listo → *squash merge* → reiniciar la rama:
  `git fetch origin main && git checkout -B claude/amazing-wozniak-gtw9ll origin/main && git push --force-with-lease -u origin claude/amazing-wozniak-gtw9ll`.
  Tras fusionar, esperar el despliegue de Cloudflare y **comprobar la web en vivo**.
- Los mensajes de commit terminan con las líneas de atribución de la sesión (Co-Authored-By / Claude-Session) y las
  descripciones de PR con «🤖 Generated with Claude Code» y el enlace de la sesión.
- **Cada vez que cambie el mod**: subir la versión (mira la de «Estado al cierre», arriba) en `tf-client/gradle.properties`
  (`mod_version`) y en `TFClient.VERSION`, compilar y **darle el enlace directo de descarga** del `.jar` subido a
  `descargas/` (pesa ~48 MB: más que el límite de 30 MB de los adjuntos; deja solo el último jar en `descargas/`).
- **Nunca** lanzar el juego ni un servidor de Minecraft. El mod se comprueba compilando y simulando (p. ej. la ventana
  de oficios se simuló con PIL usando la textura del cofre de vanilla y el arte real).
- **Secretos** solo en Cloudflare (Secrets), **nunca** en el chat ni en git (lista en la sección 4).
  Stripe va en **modo real** (no quiere modo de prueba; ya se enfadó por eso).
- Comandos del mod (1.3.33, lo pidió él: «usaremos la tableta»): **solo** `/tf web …` (staff; dentro, `/tf web claims`
  para las protecciones) y `/tf reload` (staff, nivel 3: recarga TODAS las configs de verdad). Fuera `/tf jobs` y
  `/tf shop`: todo lo de los jugadores va por el pad. Los VFX
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

## 5. El mod (TF Client 1.3.66)

- Compilar: `cd tf-client && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build --no-daemon -q -Porg.gradle.java.installations.paths=$JAVA_HOME`
  → `build/libs/tfclient-1.20.1-1.3.10.jar` (va en `mods/` del juego **y** del servidor, misma versión). Si el contenedor solo tiene Java 21 (pasó en octubre de 2026), basta
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build --no-daemon -q`: Gradle descarga el JDK 17 solo.
- Objetos de los sets (`items/TFTier.java`, `TFItems.create`, `TFItemTypes.Material`/`Armor`): cada set tiene su nivel
  (`tier` en `tf_sets.json`: `iron`, `diamond`, `netherite` o `netherite+N`). Crates +1 (desde la 1.3.5, decisión del
  dueño); rangos de hierro a +3 (1.3.6). Atributos ocultos en la descripción (`HIDE_ATTRIBUTES`).
- Comandos (todos en `items/TFCommands.java`):
  - `/tf reload` (nivel 3, 1.3.33): `pad/server/TFReload.java`, lo mismo que RECARGAR CONFIGS del pad de admin.
  - (`/tf jobs` y `/tf shop` se quitaron en la 1.3.33; con ellos, `recargar`, `nivel`, `xp` y `reiniciar` de oficios.)
  - `/tf web sets list|give`, `/tf web tienda add|precio|quitar|lista|vaciar` (staff) y los que usa la web:
    `/tf web rango`, `/tf web monedas ver|dar|quitar|poner`, `/tf web ruleta girar`, `/tf web tienda comprar`.
  - Del pad (staff, 1.3.22): `/tf web kits guardar|quitar|recargar`, `/tf web viajes poner|quitar|lista`,
    `/tf web comunidad borrar|denuncias`.
  - `/tf claims` (de jugadores) ya no existe (1.3.24); el del staff es `/tf web claims`.
  - `/tf web pad admin [jugador]` (nivel 3): da el pad de administrador (1.3.24).
  - Toda la config del mod está en `config/tfclient/` (1.3.24; antes `config/tfclient-*.…`).
- Código: `server/TFBridge.java` (puente), `shop/TFCoinShop.java`, `shop/TFRoulette.java`, `economy/` (Vault por
  reflexión en Mohist, monedas propias o comandos), `jobs/` (oficios, config `config/tfclient-jobs.json`),
  `menu/TFPanelMenu.java` + `client/TFPanelScreen.java` (ventana de oficios: 10 huecos de la rejilla en los huecos
  29-33/38-42 del cofre y 36 botones abajo; nunca el inventario del jugador).
- Módulos nuevos: `claims/` (TF Claims, protecciones; `/tf claims`, `/tf web claims`; ver entrega 1.3.18),
  `items/TFWardrobe.java` + `mixin/HumanoidArmorLayerMixin`/`CustomHeadLayerMixin` (armario de la web, 1.3.17),
  `vfx/` (efectos de kill y skills), `pad/` (TF Pad: `TFPadClient` tecla C y sonidos, `TFPadScreen`, `TFPadNet` canal
  `tfclient:pad`) y `market/` (GTS, 1.3.20). Menú: `mixin/TitleScreenMixin.java`, `client/TFMenuButton.java`,
  `client/TFButtonTheme.java`; fondo `textures/gui/menu_background.png` (paisaje de noche de la web).
- Pad 1.3.38: `config/tfclient/gachapon.json` (Gachapón) y `config/tfclient/pase.json` (TF Pass), datos en
  `<mundo>/tfclient/{monedas-verdes,gachapon,pase}.json`; apps con página propia = `PadServer.DataApp` + `PadDataPage`.
- Generadores: `tools/pad/build_pad.py [--preview]` (texturas del TF Pad; iconos de `iconos*.py`, los de 1.3.38 en
  `iconos4.py`; moneda verde con `verde()`, también el objeto), `tools/pad/sim/pages.py <carpeta>` (vista previa de las apps del pad), `tools/pad/build_sounds.py [--wav DIR]` (sonidos del pad), `tools/pad/limpiar_bordes.py` (contorno del marco), `tools/gen_claim_blocks.py [--preview DIR]` (piedras de TF Claims), `tools/gen_menu_buttons.py` (botones de
  los menús, de las láminas del dueño), `tools/build_vfx.py` (VFX).
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
