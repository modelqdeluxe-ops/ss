# Peso de ganado con la cámara (modelo v7)

## El modelo

    ln peso (kg) = a(tipo) [− 0.40 si tiene menos de 1 año] + 2.0 · ln alzada + 0.54 · ln(fondo de pecho / (0.52 · alzada))

(cm, medidos en la foto de costado; la razón fondo/alzada se limita a 0.42–0.65 y se avisa si se sale)

| Tipo de animal | a | De dónde sale |
|---|---|---|
| Europeo de carne | −3.392 | Horqin (71 animales con fotos y báscula) |
| Cebú o criollo | −3.876 | promedio de Bororo (292), Indonesia adultos (29) y Curraleiro adultos (437) |
| Cruce de cebú y europeo | −3.634 | en medio de los dos |
| Lechero | −3.392 | sin base abierta con báscula: usa el de europeo (calibrar) |

- **Alzada a la cruz**: de lo más alto del lomo sobre las manos (del hombro a 15 % del largo hacia atrás) al suelo
  (lo más bajo de la silueta). La foto de atrás da una segunda alzada (la de la grupa, ~1.03 veces la de la cruz) con
  su propia escala: si las dos coinciden (12 %), se promedian.
- **Fondo de pecho**: el menor alto del tronco detrás del codo (de 10 a 30 % del largo hombro → base de la cola), sin
  la pata.
- Silueta del modelo preciso (RF-DETR) y puntos AP-10K de la foto (con el espejo promediado). Mismas definiciones en
  Python (`medir_bov.py`, `rasgos_final.py`) y en la app (`medidasGanado`, `pesoGanado` en `app/assets/pesocam.js`).
- Escala: en la app, la persona de referencia junto al animal; en el ajuste, la alzada de cinta.
- Tipo y edad: se eligen por lote en la pantalla de la cámara; de inicio salen de la raza escrita en el lote
  (`tipoDeRaza`: Brangus, Braford, F1… → cruce; Brahman, Nelore, Gyr, criollos… → cebú; Angus, Hereford, Charolais,
  Simmental… → europeo; Holstein, Jersey… → lechero). Sin raza: cruce, y la app pide elegir.
- Después, la calibración de la app con la báscula (k · P^b, b entre 0.4 y 1.15) corrige lo propio de cada lote.
- Ajuste: `python3 ajustar_ganado.py` (con `VALIDAR=<carpeta>` valida además con CowDatabase, sin licencia comercial).

### Por qué un punto de partida por tipo y edad

| Base | peso / alzada² (kg/m²) | fondo / alzada | perímetro / alzada |
|---|---|---|---|
| Indonesia (locales) | 179 | 0.524 | 1.34 |
| Bororo (cebú) | 192 | — | — |
| Curraleiro adultos (criollo) | 220 | — | — |
| Hereford | 306 | 0.525 | 1.52 |
| Horqin | 338 | 0.511 (foto) | 1.54 |
| Angus de engorde | 384 | — | — |

Con el mismo fondo de pecho relativo, el peso por alzada cambia 2 veces entre razas: lo que cambia es el ancho
(perímetro), que de costado no se ve. Y los animales jóvenes pesan mucho menos a igual alzada (el esqueleto crece
antes que la masa): destetados de Curraleiro, jóvenes de Indonesia y terneros Simmental, ~0.67 veces (a −0.40).

### Por qué exponente 2.0 para la alzada

Entre edades (terneros a toros) el peso crece como alzada^2.8–3, pero dentro de un lote la alzada de la foto trae
~4 % de ruido, que con exponentes altos se vuelve error. Error dentro de cada base (alzada con 4 % de ruido simulado):
1.8 → 12.4 %, 2.0 → 12.8 %, 2.2 → 13.3 %, 2.5 → 13.9 %, 2.8 → 14.9 %. Con 2.0 Horqin queda igual que v6.

## Validación (error medio absoluto del peso)

| Base | n | v6 sin calibrar | v7 sin calibrar* | v7 calibrado con 5 | v7 dentro de la base |
|---|---|---|---|---|---|
| Horqin (fotos) | 71 | 7.9 % | 7.9 % (su propia base) | 8.5 % | 7.9 % |
| Bororo, cebú (cinta) | 292 | 75.9 % | 16.7 % | 12.3 % | 11.7 % |
| Indonesia adultos (cinta) | 29 | 54.3 % | 7.9 % | 9.1 % | 8.3 % |
| Curraleiro adultos (cinta) | 437 | 49.5 % | 14.2 % | 14.4 % | 13.4 % |
| Indonesia jóvenes (cinta) | 66 | 99.5 % | 15.9 % | 11.3 % | 10.7 % |
| Curraleiro destetados (cinta) | 283 | 122.1 % | 17.2 % | 18.9 % | 17.7 % |
| Terneros Simmental (LiDAR) | 45 | 103.7 % | 38.7 % | 6.7 % | 6.5 % |
| Hereford (cinta; validación) | 103 | 11.0 % | 12.0 % | 8.7 % | 8.4 % |
| JXcow (foto 224 px + nube de puntos; validación) | 64 | 16.2 % | 14.2 % (sesgo 0.97) | 15.2 % | — |

\* con el punto de partida de su tipo calculado sin esa base (cebú) y la corrección de jóvenes donde corresponde.

- JXcow: la foto (224 px, detrás de barandas) mide con tanto ruido que predecir el promedio del lote da 10.7 %. Con
  b libre hasta 0.4 la calibración lo descubre sola: 13.2 → 10.1 % con 10 animales de báscula (en Horqin y Hereford b
  queda ~1 y no cambia nada).
- Con cinta: perímetro torácico + largo + alzada da 4.7–4.9 % (Horqin, Hereford). Lo que falta desde el costado es el
  ancho: en búfalas (figshare 33134612), con medidas de FOTO, costado solo 8–12 % y con el ancho del barril visto
  desde arriba 4.5 %.
- Lo que NO sirvió: el largo de la foto (r = 0.45 con la cinta; empeora Horqin a 10.3 %; la cinta lo baja a 5.6 %),
  la razón ancho/alto de la foto de atrás (en adultos de Kaggle ≥ 250 kg, 9.8 → 10.0 %; solo separa jóvenes de
  adultos; en Horqin r = 0.12), las razones de forma aprendidas en Kaggle (empeoran Horqin a 13 %).

## Bases de datos revisadas

Búsqueda de una hora (1 oct 2026) con scripts sobre Kaggle (60+ términos en 8 idiomas), Hugging Face, Zenodo, figshare,
Mendeley Data, DataCite, Dryad, Harvard Dataverse, Embrapa Redape, Science Data Bank, Crossref, Europe PMC y GitHub,
más el catálogo `cowcv_ruchay2026` (134 bases de ganado).

| Base | Qué tiene | Licencia | Uso |
|---|---|---|---|
| Horqin (Mendeley Data h2s22wr5py v3, Bai 2025) | 72 animales, fotos de costado y de atrás, peso, alzada, perímetro, largo | CC BY 4.0 | **ajuste** (forma y a europeo) |
| Bororo de Níger (Zenodo 10.5281/zenodo.22911607) | 292 cebú adultos: edad, sexo, alzada, perímetro, largo, peso | CC BY 4.0 | **ajuste** (a cebú) |
| Curraleiro Pé-Duro (Zenodo 8364889) | 1,023 criollos de Brasil, terneros a toros: peso, perímetro, alzada, largo | CC BY 4.0 | **ajuste** (a cebú, jóvenes) |
| Indonesia (Kaggle jameswisnuaryatama/multivariate-morphometric-physiological-cattle) | 95 bovinos: peso, alzada, fondo de pecho, perímetro, largo | CC BY 4.0 | **ajuste** (fondo de pecho, a cebú, jóvenes) |
| Simmental con LiDAR de dron (Zenodo 11277007 y 13364663, Sci Data 2025) | 95 terneros (~210 kg), medidas de LiDAR, peso; fotos de la pesada desde una cámara alta | CC BY 4.0 | **ajuste** (jóvenes) |
| Búfalas Khuzestani (figshare 33134612) | 100 búfalas: medidas de fotos de costado y de arriba, cinta, peso | CC BY 4.0 | estudio (el ancho de arriba) |
| Kaggle BMGF (Acme AI, doi 10.34740/KAGGLE/DSV/8858637) | 4,500 fotos de costado y 4,500 de atrás con silueta y 9 puntos a mano, peso en el nombre, calcomanía de escala | CC BY 4.0 | siluetas; para el peso no sirvió (calcomanía con 7 % de ruido, proporciones que no pasan a otras razas) |
| CowDatabase (GitHub ruchaya, Ruchay et al. 2020) | 103 Hereford, fotos izq./der./arriba, peso y 9 medidas | sin licencia declarada | solo validación |
| CowDatabase2 / 3 (GitHub ruchaya) | Angus de engorde con peso, alzada, ancho de pecho, perímetro | sin licencia declarada | solo validación |
| JXcow (Kaggle dcaoren) | 112 animales de engorde en báscula, de costado: RGB, profundidad, nube de puntos en metros | CC BY-NC-SA 4.0 | solo validación |
| Hararge (Mendeley w9wbn9mpr8) | 61 animales con medidas y peso | CC BY 4.0 | no: el peso parece calculado con fórmula de cinta (1.1 % de error con el perímetro) |
| Zebu girth (figshare 1291085) | 703 registros de perímetro y peso, casi todos terneros | CC0 | no (sin alzada) |
| Mendeley vhv6fph7w7 (razas indias, medidas y peso) | — | CC BY 4.0 | embargada hasta el 17 oct 2026: revisar |
| Mendeley vf7pxfs7dx (Bangladesh, 2026) | 360 fotos de 72 vacas desde 5 ángulos, con peso | CC BY 4.0 | no: establos con varios animales, sin escala, pesos redondeados (estimados) |
| CID / Bengal Cattle (GitHub bhuiyanmobasshir94) | ~17,900 fotos con peso de un vendedor en línea | código Apache; fotos y pesos del vendedor | no se usa |
| IPCLab-NEAU (RGB-D en video, 3.3 %) | — | Baidu, clave por correo | no accesible |
| Artículos 2026 con celular, profundidad o doble vista (Animals, Sci Rep, Virginia Tech) | — | "a pedido" | no publicados |

Licencias: las bases CC BY 4.0 piden atribución: Bai (Horqin, Mendeley Data, doi 10.17632/h2s22wr5py.3); Bororo de
Níger (Zenodo, doi 10.5281/zenodo.22911607); Curraleiro Pé-Duro (Zenodo, record 8364889); Indonesia (Kaggle,
jameswisnuaryatama); Simmental con LiDAR (Zenodo 11277007, Sci Data doi 10.1038/s41597-025-04783-6); Kaggle BMGF
(Acme AI, bhalo y mPower). Ver también `CONTINUAR.md`.

## Para bajar del 8 %

1. **Calibrar con la báscula** (la app lo hace por modo; b libre de 0.4 a 1.15).
2. **Calibración por animal** (pendiente): cada animal tiene su peso de entrada; si se mide con la cámara ese día, su
   factor queda guardado y lo propio del animal se cancela.
3. **El ancho**: una toma de arriba (pasarela o manga) daría el ancho del barril, que en búfalas bajó de ~10 a 4.5 %.
4. **Datos propios**: el laboratorio de la app guarda medidas, fotos, tipo de animal y peso real, y exporta.

## Archivos

- `medir_bov.py`: silueta (RF-DETR) y puntos (AP-10K) de una foto, en Python.
- `rasgos_final.py`: las medidas de costado; `rasgos_atras.py`: anchos de atrás; `rasgos_bov.py`: ayudas.
- `zipremoto.py`: saca archivos sueltos de un zip remoto (Kaggle o Zenodo, sin bajar el zip entero).
- `horqin_medidas.csv`, `tablas/*.csv`, `ajustar_ganado.py`: el ajuste.
