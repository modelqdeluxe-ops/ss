# Lo aprendido con el peso por cámara (personas) y cómo llevarlo al ganado

Este documento guarda lo que funcionó, lo que no y cómo se midió, para repetirlo en ganado sin volver a probar lo
que ya se descartó. Las personas son el banco de pruebas: el mismo camino (silueta → puntos → medidas → modelo de
peso → calibración con báscula) es el que se usará con los animales. Detalle de cada versión en `CONTINUAR.md`;
tablas de los modelos de visión en `modelo/vision/LEEME.md`; modelo de peso en `modelo/peso/LEEME.md`.

## 1. El camino completo

1. **En vivo** (cada cuadro de video): silueta rápida (LR-ASPP MobileNetV3 afinado, 256 px, ~60 ms) y puntos del
   cuerpo (DWPose-s en personas, AP-10K en animales), en Web Workers. Sirven para guiar (distancia, ángulo, quieto)
   y para decidir cuándo tomar la foto. No miden.
2. **Fotos** (3 por ángulo, 140 ms entre una y otra): el modelo preciso (RF-DETR Seg Nano int8) da la silueta sobre un
   recorte alrededor del sujeto y el modelo de puntos de fotos (RTMW-m, con el espejo promediado) los puntos.
3. **Medidas**: anchos y fondos a alturas fijas del cuerpo (fracciones de la estatura), con los puntos para saber
   dónde están los hombros, caderas, brazos y manos. La escala sale de una estatura conocida (personas) o de una
   persona de referencia junto al animal (ganado).
4. **Modelo de peso**: una ley de potencias ajustada con una base de datos de medidas y básculas (personas: ANSUR II).
5. **Calibración**: kg = k · pred^b con las mediciones que tienen peso de báscula (Theil–Sen en logaritmos; con
   menos de 6, solo k). Es lo que lleva el error del rango de la base de datos al del lugar real.

## 2. Silueta

Cómo se mide (siempre igual, para comparar): fotos de COCO val2017 que el modelo no vio (`ev/`, 124 personas de
calle y 180 de casa con camas, sillones, mesas), recorte alrededor del sujeto (como en vivo) y cuadro completo.
Métricas: IoU, error de área, error de alto y error de ancho entre 20 y 60 % del alto (lo que más pesa en el
volumen). Comparar siempre contra el modelo actual en las dos series; si no mejora en las dos, no se sube.

Lo que funcionó:
- Afinar el LR-ASPP de torchvision (BSD-3) con COCO (fondo / persona / vaca): IoU persona 0.73 → 0.75.
- Refinamiento a 1/4 de la entrada (bordes más finos, +2 ms): mejor en personas, peor en vacas → un modelo por clase.
- Escenas de casa al doble de peso: el fondo que más confunde. Error de área en casa 19 → 17 %.
- **Destilar del modelo preciso** (beta.18): RF-DETR marca la silueta en las fotos de entrenamiento y el rápido
  aprende a copiarla (sus bordes son más fieles que los polígonos a mano de COCO). Error de área con el sujeto
  grande: casa 19.7 → 12.8 %, calle 13.4 → 9.9 %. Es la mejora más grande que tuvo la silueta rápida.
- Limpiar la silueta en vivo con los puntos (quitar lo que está lejos del esqueleto): casa 19 → 16 %. Solo en vivo.

Lo que no funcionó (no repetir):
- Más épocas con los mismos datos cuando ya se estancó (dos veces: v2 y v4).
- Espejo promediado, filtro guiado, cortar en mosaicos, MediaPipe Selfie (peor: 0.73 contra 0.77 en casa).
- Entrada de 224 o 192 px (más rápido, −2 a −6 puntos de IoU); int8 estático (más lento en WASM).
- RF-DETR Seg **Large** como maestro: no es mejor que el Nano contra las siluetas a mano de COCO (IoU casa 0.862
  contra 0.867). El maestro sigue siendo el Nano.
- Limpiar con los puntos la silueta de la **foto** que se mide: cortaba cabeza, pies o brazos.

**Para el ganado**: el modelo de vacas (`silueta.onnx`) no se ha destilado. Pasos: (1) `maestro_silueta.py` con la
clase vaca (COCO 21) en las fotos 'v' (1,635) y en más fotos de vacas (COCO train tiene unas 1,900 imágenes con
vacas; buscar más con licencia comercial, p. ej. CC BY); (2) `entrenar_silueta2.py` con `SOLO_MAESTRO=1` desde el
modelo de vacas; (3) medir contra `ev/` de vacas y en fotos de corral (piso de tierra, cercas, otros animales: el
equivalente a "casa" para personas).

## 3. Puntos del cuerpo

- En vivo: DWPose-s (133 puntos) en 2–3 workers a turnos; recorte de seguimiento desde los puntos anteriores;
  suavizado tipo One Euro (55 % lo nuevo si se movió menos de 1.2 % del alto).
- **Mostrar solo puntos que describen un cuerpo** (`poseBuena`: hombros, caderas y ≥ 10 de 17 confiables; dedos solo
  con muñeca confiable y mano de tamaño lógico). Con la mano de cerca el modelo inventaba un cuerpo entero.
- Fotos: RTMW-m con el espejo promediado (cada punto con su par del otro lado).
- Destilar el modelo de puntos con tasa alta lo empeoró: descartado.
- **Para el ganado**: AP-10K (17 puntos) ya está (`animal_m.onnx`). Para medir ganado hacen falta puntos propios del
  bovino (cruz, punta de la nalga, punta del encuentro, hueso de la cadera): si no hay un modelo con licencia
  comercial, marcar unas cientos de fotos propias y afinar.

## 4. De la silueta a las medidas (lo que más error mete)

Cómo se mide: `pruebas/banco_medidas/` corre el camino de la app (modelo
preciso + puntos de fotos) sobre fotos reales de personas de pie (COCO, 286 de cuerpo completo) y compara cada medida
(en fracción de la estatura) con la distribución de ANSUR II. Una medida sesgada o que se sale seguido del rango
humano es un error de la medición, no de la persona.

Lecciones:
- **Los brazos**: de frente, a la altura del pecho, los brazos relajados tocan el tronco y la silueta es un solo
  tramo. Suponer un ancho de brazo fijo falla con manga (el brazo se ve más ancho): el pecho salía con los brazos
  (55 cm en vez de 28). Se mide el medio brazo (del borde a la línea de sus puntos) y se quita el brazo entero.
- **De perfil el brazo queda encima del tronco**: no se corta. Cortarlo quitaba hasta la mitad del fondo del pecho y
  del glúteo (el aviso "una medida salió fuera" salía siempre).
- Aun así, con el brazo pegado la silueta es ambigua. Se mide de tres maneras (sin cortar, brazo de tabla, brazo
  medido) y se queda la que cuadra con los hombros (ANSUR II: pecho = f(hombros, estatura) con 4.2 % de error típico;
  cintura 7.2 %, cadera 6.5 %; un brazo de más o de menos cambia el ancho 20–50 %). Banco de 33 adultos de pie (COCO):
  fuera de rango pecho 18 → 6 %, cintura 12 → 6 %, cadera 15 → 3 %; mediana del pecho 0.164 de la estatura (ANSUR
  0.165). Herramienta: `pruebas/banco_medidas/`.
- Las alturas de medida se anclan a los puntos (hombros 22 %, caderas 51.5 %, rodillas 71.4 %): la silueta puede
  tener algo pegado arriba o abajo.
- Si la silueta no llega a la cabeza o los pies, la estatura y la escala salen de los puntos (`marco`).
- La toma de perfil nunca es perfecta: el giro se mide con la separación de hombros y caderas y se corrige el fondo
  (corte ovalado).
- Límites: percentiles 0.1–99.9 de la base de datos con holgura; lo que se sale se lleva al límite y se avisa.
- **Para el ganado**: lo mismo con las patas (de costado, cada pata es un tramo aparte), la cola y la cabeza (cortar
  por los puntos), y con otros animales o la cerca pegados (limpiar con los puntos del animal).

## 5. Modelo de peso

- ANSUR II (6,068 personas, dominio público): ln peso = c0 + Σ ci ln medida, solo con forma y estatura, sin sexo ni
  edad (con ellos bajaba apenas 2.25 → 2.23 %). Validación cruzada de 10 partes: 1.65 % con medidas exactas,
  **2.3 % con 3 % de ruido** (el de una foto). Piso con las 93 medidas a mano: 1.1 %. No hay forma de bajar del
  piso sin datos del lugar: lo que falta (agua, músculo, grasa, hueso) no se ve en una foto.
- Si una medida no se puede tomar, se estima con las demás (regresión de la misma base).
- Siempre validar con ruido del tamaño del de las fotos, no solo con medidas exactas.
- **Para el ganado**: el mejor predictor es el perímetro torácico (con el largo del cuerpo). Hace falta una base de
  medidas y básculas de bovinos con licencia que permita uso comercial; si no la hay, las mediciones del propio
  corral con báscula (el laboratorio de la app ya guarda medidas, fotos y peso real, y exporta para análisis).

## 6. La captura (que la foto se tome sola sin pelear)

- Reglas: completo (2 % de margen), distancia (55–88 % del alto del cuadro), ángulo, quieto (cajas que casi no se
  mueven) y 3 cuadros buenos.
- Un cuadro que falla por poco (un temblor) no reinicia la cuenta; dos seguidos sí (beta.19). Antes un solo cuadro
  malo la reiniciaba y costaba mucho que se tomara.
- De perfil: los umbrales pueden ser un poco holgados porque el giro se corrige en la medida.
- **Para el ganado**: el animal no obedece. Hará falta tomar fotos en ráfaga cuando esté de costado y quedarse con
  las mejores, más que esperar a que se quede quieto.

## 7. Herramientas

- `maestro_silueta.py` (en el repo): siluetas del maestro para entrenar el rápido.
- `entrenar_silueta2.py` (en el repo): `SOLO_MAESTRO=1` para destilar.
- `pruebas/banco_medidas/`: una página de Playwright que carga `Vision.motor('preciso')` y `Vision.motor('cuerpo')`,
  corre cada foto como la app y guarda silueta y puntos; un script de Node extrae de `pesocam.js` las funciones de
  medida (de `const med=` a `function combinar`) y compara contra los percentiles de la base de datos.

## 8. Licencias (producto comercial)

Solo permisivas: torchvision (BSD-3), RF-DETR Nano/Small/Medium/Large (Apache 2.0; los XLarge no), OpenMMLab
(Apache 2.0), onnxruntime (MIT), ANSUR II (dominio público), COCO (CC BY 4.0 las anotaciones). No Ultralytics YOLO
(AGPL), no BodyM (no comercial).
