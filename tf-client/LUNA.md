# La Luna de Sangre (TF Client 1.3.175)

Lo que pidió el dueño (5 nov 2026), literal:

- «la gente me pide más dificultad en el juego… lo sienten aburrido» → «olvídate de los bosses, esos son endgame.
  Hagamos algo con los mobs… me gusta la noche de sangre con modificadores en los mobs».
- «¿es una vez por cada 10 skips de noche? dentro del juego».
- «el mínimo para que se active la luna de sangre son 3 personas en simultáneo; si no, se skipea la noche y cuenta
  regresiva otra vez; si hay 3 simultáneas se activa cuando toque, pero justo antes de que se active si solo hay 2 no
  se activa el evento».
- «quiero mobs con ojos rojos brillantes, mobs hostiles; que aparezcan mobs fuertes con daño mágico, es decir que ignoren
  la armadura que traes y el daño te penetre».
- «una musiquita baja tenebrosa durante el evento… una melodía tenebrosa bonita tenue».
- «cuando se active quiero que salte una escena para todos con el nacimiento de la luna, una musiquita y un título o
  explicación diciendo la luna de sangre se muestra, los mobs hostiles son más agresivos y peligrosos (escena de mobs
  tipo zombie o esqueletos activándose los ojos rojos brillantes), fin de la escena. Y comienza».

## 1. Cuándo sale

Todo en `luna/BloodMoon.java` (servidor), con el Overworld principal (`server.overworld()`):

| Regla | Cómo |
|---|---|
| Cada 10 noches | Al anochecer (hora **13000**: la luna ya asoma por el este) cuenta una noche. Solo cuentan las noches con alguien conectado (`contarNochesVacias`). |
| Noches saltadas durmiendo | También cuentan (si se duerme antes de las 13000, la noche se ve pasar en `checkNight`), pero la luna no sale esa noche: si la cuenta llega a 0 así, sale la siguiente noche de verdad. |
| La regla de los 3 | Justo al salir la luna (la noche que toca): con `minJugadores` (3) o más conectados, sale; con menos, **no sale**, se avisa en el chat («hacen falta 3… y hay 2») y la cuenta **vuelve a 10**. Los bots de `/tf bots` no cuentan (las dianas de la prueba en el juego sí). |
| Entre una y otra | Al menos `minMinutosEntre` (120 min reales); si toca antes, se espera a la noche siguiente. |
| Avisos | Al anochecer, «faltan N noches» desde `avisarDesde` (3); la noche de antes, «la luna empieza a teñirse de rojo… mañana». |
| Nadie se la salta | La tarde en que toca (12000–13000, con gente suficiente) no se puede dormir; durante la luna, tampoco. |
| Fin | Al amanecer (23200) o si el tiempo salta al día. |

El estado (cuántas noches faltan, la última, la noche vista) va en el mundo: `tfclient/luna_sangre_estado.json`.

## 2. Qué pasa esa noche

- **La escena** (`luna/client/BloodMoonScene`, 16 s, para todos los que están en un mundo con cielo de Overworld y de
  noche; Esc la salta): la cámara sube por encima del jugador y del terreno y mira al este; la luna roja nace por el
  horizonte (de 5° bajo él a donde está de verdad) y el cielo se tiñe; a los 3 s, el título «☾ La Luna de Sangre se alza
  ☾». A los 7,5 s, en la oscuridad, dos esqueletos y dos zombis con la cabeza gacha: a los 8,6 / 9,6 / 10,6 / 11,6 s (los
  golpes de la música) se les encienden los ojos rojos y levantan la cabeza; las explicaciones («Los monstruos son más
  agresivos y peligrosos», «Los más fuertes atraviesan tu armadura», «Esta noche nadie duerme · Sobrevive hasta el
  amanecer»). Vuelve el mundo. Mientras dura, el servidor no deja que los mobs hagan daño ni pone mobs extra.
- **Cielo**: cielo, nubes y niebla rojos (`mixin/luna/ClientLevelLunaMixin` y la niebla en `BloodMoonClient`), la luna
  de Minecraft cambiada por la roja (`mixin/luna/LevelRendererLunaMixin`) y encima una luna roja grande con su
  resplandor; con paquete de shaders, después de su pasada final y solo donde se ve el cielo (`vacio/AfterShaders` +
  `glDepthRange(1,1)`). Viñeta roja que late y ceniza roja (esporas carmesí) alrededor.
- **Música**: `luna_sangre.musica` en «Discos», al 42 % (la de Minecraft se calla, `MusicManagerMixin`); la escena
  tiene la suya (`luna_sangre.escena`).
- **Ojos rojos** (`luna/client/BloodEyesLayer`): zombi, husk, ahogado, aldeano zombi, esqueleto, stray, esqueleto wither,
  creeper, bruja, saqueador, vindicador e invocador. Como los ojos de la araña (`RenderType.eyes`: suman luz; con Oculus
  son emisivos). Las texturas solo tienen los ojos (y un halo tenue): `textures/entity/luna_ojos/*.png`.
- **Mobs** (`luna/BloodMobs`): todos los mobs hostiles de Minecraft de la lista (no los jefes ni los de otros mods) ven
  más lejos (+24), van +12 % más rápido y pegan +20 %. Algunos llevan **modificadores** (se ven en su nombre al mirarlos
  y en sus partículas): Vampírico, Ardiente, Gélido, Blindado, Veloz, Sombra (aparece a tu espalda), Invocador (a media
  vida, dos esbirros), Explosivo (al morir, sin romper bloques), Espinoso, Escudo arcano (las flechas no le hacen nada),
  Líder de manada, Furioso y Divisor. **Élite** (2 modificadores, ×1,6 de vida, nombre dorado visible) y **Campeón** (3,
  ×2,5 de vida, +4 de armadura, ☠ y nombre rojo): con **daño mágico**: su golpe y sus flechas pasan a ser del tipo
  `tfclient:luna_sangre` (en `bypasses_armor`): la armadura no lo para; el escudo sí. Cerca del spawn (`zonaSegura`,
  150 bloques) no salen ni Élites ni Campeones; lejos (`distanciaMaxima`), más.
- **Mobs extra**: cada 5 s, hasta 3 por jugador, a 22–40 bloques (nunca a menos de 18 de nadie), hasta 16 hostiles
  cerca de cada uno. **No dentro de las protecciones** (`aparecerEnProtecciones`, pregunta del dueño sin contestar:
  se dejó así y se dice en el resumen). Más experiencia (×2, ×4, ×8).
- **Al amanecer**: los que puso el evento (y los esbirros) se deshacen en humo; a los demás se les quita todo.

## 3. Configuración y comandos

`config/tfclient/luna_sangre.json` (se crea la primera vez; `/tf reload` la vuelve a leer): `activa`, `cadaNoches`,
`minJugadores`, `minMinutosEntre`, `contarNochesVacias`, `avisarDesde`, `escena`, `mobsPorJugador`, `porOleada`,
`segundosEntreOleadas`, `aparecerEnProtecciones`, `zonaSegura`, `distanciaMaxima`, `probModificador`, `probElite`,
`probCampeon`, `vidaElite`, `vidaCampeon`, `danoExtra`, `velocidadExtra`, `experiencia*` y `modificadores` (la lista de
los que pueden salir).

Staff: `/tf luna estado | empezar [sinescena] | parar | noches <n> | invocar <normal|elite|campeon>` (empezar pone la
hora del anochecer si es de día; invocar pone un zombi de la luna donde se ejecuta, p. ej. `execute at Ana run …`).

## 4. Música y texturas (hechas aquí)

- `tools/luna/musica_luna.py`: «Nana de la Luna Roja» (re menor a 56, ~103 s en bucle sin corte: cuerdas oscuras,
  bordón, caja de música, campana grave y un coro muy bajo) y la de la escena (16 s, con los golpes de los ojos en sus
  tiempos). Con los instrumentos de `tools/void/instrumentos.py`. Necesita `scipy` y `ffmpeg`.
- `tools/luna/texturas_luna.py`: las capas de ojos (dónde están los ojos se midió en las texturas de Minecraft 1.20.1),
  la luna roja (pixel art propio), su halo y la viñeta.

## 5. Cómo se prueba

- **En el juego**: `[prueba-jefes=luna]` (`bosses/BossTestLuna`): con 1 jugador no sale y la cuenta vuelve a 10; con 3
  (dos dianas) sale con la escena (5 capturas); los ojos rojos (zombi Élite, esqueleto Campeón y un creeper delante), el
  cielo rojo con la luna, la interfaz con la viñeta; el daño mágico (zombi normal y Élite contra armadura de diamante);
  dormir; los mobs extra; el amanecer. Con `[shaders=photon]` o `[shaders]`, con Oculus.
- **En Mohist**: `tf-client/tools/mohist/probar_luna.sh <carpeta> <tfclient.jar>` con bots: 2 no, 3 sí, la cama, el
  daño (la vida de los bots: `VIDA:` en su registro), los mobs extra y el amanecer.
