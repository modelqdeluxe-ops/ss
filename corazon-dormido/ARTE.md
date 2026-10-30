# Biblia de arte de Corazón Dormido (0.8)

Las reglas con las que se dibuja todo el juego. Se copió la **construcción** de Core Keeper (medidas, proporciones,
paleta, contorno), **nunca sus dibujos** (son de Pugstorm/Fireshine). Del 3D (KayKit, Quaternius) solo se usa el
**movimiento de los huesos**, nunca el modelo: el juego es pixel art y todo lo que se ve lo dibujamos nosotros.

## 1. Lo medido en Core Keeper (para no volver a medirlo)

Medido píxel a píxel con `herramientas/estudio-ck/medir.py` (las imágenes de la wiki están a ×4; no van al repo).

| Qué | Medida |
|---|---|
| Personaje | 13×20 px, cabeza 13×11 (más de media altura), ojos 2×2 con un píxel blanco de brillo |
| Criaturas pequeñas | babas 14×13, cavelings 13×18 (con arma hasta 22×27) |
| Criaturas grandes | brutos 43×40 |
| Jefes | 80–145 px (Hive Mother 124×102, Ghorm 128×147, Azeos 143×111) |
| Iconos | 16×16 como mucho; mesas y cofres llenan 15–16 de ancho; 5–17 colores |
| Colores por dibujo | 6–24; **3–4 tonos por material** |
| Paleta | única para todo el juego: en 377 dibujos, 64 colores cubren el 63 % de los píxeles |
| Contorno | **sin negro**: el tono más oscuro del material (sel-out), valor 0,2–0,38 (madera `#39341f`, `#614822`; cofre verde `#073c31`) |
| Paredes | la tapa oscura (la masa), con un canto del material donde da al suelo; la cara con ladrillos o vetas con luz |
| Suelos | tranquilos: manchas grandes de 3 tonos y poco contraste; los detalles, pocos y bien puestos |

## 2. Reglas (todas las piezas)

1. **Luz arriba a la izquierda** (y un poco de frente). Lo que mira a la luz, un tono más claro; abajo a la derecha,
   la sombra.
2. **3–4 tonos por material** (sombra, medio, luz y un brillo solo en metales, ojos y filos). Rampas con el matiz
   girando: las sombras hacia el azul y más saturadas, las luces hacia el amarillo.
3. **Contorno de color**: el borde de fuera es el tono más oscuro del material que toca (nunca `#000`); abajo y a la
   derecha, un poco más oscuro. Entre dos piezas, lo de detrás se oscurece donde lo tapa lo de delante (línea
   interior). En las babas el borde de arriba es claro.
4. **Nada de píxeles sueltos** ni ruido: las manchas de un tono tienen 3 píxeles o más; las texturas (vetas, grietas,
   hojas) pesan poco (`ESTILO.textura` = 0,65 en el escultor).
5. **Nunca se gira un dibujo** (salen dientes): lo que se mueve se vuelve a dibujar en cada postura (esqueleto 2D).
6. **Lo que brilla solo** (fuego, cristales, ojos, Podre, venas del Corazón) va además a la capa `_luz`, que el juego
   pinta sin sombra y con halo.
7. Cada píxel guarda su **normal** (las antorchas iluminan el lado que toca).
8. **Tamaño Core Keeper**: personaje 13×20, criaturas 14–43 px, jefes 80–190 px, iconos ≤ 16×16.

## 3. Paleta de Ásor (rampas compartidas)

Las rampas viven en el código y se reutilizan; un material nuevo se hace con una rampa que ya existe o con `rampa()`
del escultor. Formato de las de las criaturas y objetos a mano: `[línea, sombra, medio, luz(, brillo)]`.

- Criaturas: `criaturas-fichas.js` (`R`: gruto, cuero, piel, hueso, madera, trapo, piedra, musgo, metal, óxido; `OJO`).
- Objetos a mano: `objetos-px.js` (`R`: carne del Corazón, arteria, cristal, vena, Podre, hojas, corteza; `MET`:
  madera, selva, muerta, cobre, estaño, hierro, carmesí, médula, plata, piedra, alma).
- Modelos horneados: `modelos.js` (`MAT`), `modelos2.js` (`MAT2`), `jefes3d.js` (`MAT_JEFE`).
- Paredes y suelos: `arte.js` (`RAMPA`, `RAMPA_SUELO`), `arte3.js`/`arte4.js` (los de las bandas), `suelos2.js`.

Metales por nivel (iconos, herrajes, armas): cobre → estaño → hierro → carmesí → médula → plata. Cada uno con su
rampa en `MET`; el icono de una mesa o un yunque de un nivel lleva ese metal.

## 4. Cómo se hace cada cosa

| Qué | Dónde | Cómo |
|---|---|---|
| Personaje | `esqueleto.js` (`PJ_ESQ`) | piezas dibujadas por hueso con las animaciones de KayKit; dos manos (`handslot`) |
| Criaturas y jefes | `criaturas-esq.js` (`CRI`) + `criaturas-fichas.js` | primitivas con volumen (`cap`, `tubo`, `ovalo`, `poli`, `placa` biselada, `vara`, `rej`), `terminar` pone el contorno |
| Objetos dibujados a mano | `objetos-px.js` (`OBJ_PX`) | `CRI.lamina(W, H, fn)` (las mismas primitivas, quieto): el Corazón, los árboles, los iconos de estaciones |
| Resto de objetos | `modelos*.js` con `escultor.js` | modelo 3D de figuras que se hornea a la cámara del juego; `ESTILO.ck` lo deja en 4 tonos por material, contorno de color y textura suave |
| Paredes y suelos | `arte.js` (`muroArriba`, `muroCara`, `suelo`), `suelos2.js` | 16×16 a mano por código; el borde de la tapa es el tono más oscuro del material y tiene un canto claro |
| Iconos | `arte*.js`, `objetos-px.js` | rejillas de letras a mano (16×16); los que salen de un dibujo del mundo, con `reducir` (color que más se repite y borde oscuro) |
| Letra | `glifos8.js` (Pixel Operator 8) y la de 3×5 para los números | ver `CREDITOS.md` |

Al sustituir un dibujo viejo por uno nuevo con el mismo nombre: `quitarDibujo(nombre + sufijo)` para no dejar su
`_luz`, y apuntar en `META_SPR` dónde va el pie (`OBJ_PX.sustituir`).

## 5. Ver el arte sin abrir el juego (y medirlo)

- `node herramientas/taller.cjs <salida.png> '<regex>' [zoom]`: los dibujos del atlas ampliados.
- `node herramientas/vista-mano.cjs <salida.png> <zoom> "<expresión>"`: cualquier dibujo hecho al momento, p. ej.
  `"[['corazon', OBJ_PX.corazon(false).color]]"` o `"Object.entries(OBJ_PX.ICONOS_MESA).map(([n,f])=>[n,f()])"`.
- `node herramientas/criaturas.cjs <salida.png> [tipos] [zoom] [anims]`: criaturas y jefes, una animación por fila.
- `node herramientas/galeria.cjs <carpeta> [escena]`: capturas del juego en cada zona.
- Antes de dar algo por bueno: mídelo con PIL (tamaño, colores, color del contorno) y compáralo con lo de antes
  (`herramientas/comparar.py antes.png despues.png salida.png`).
