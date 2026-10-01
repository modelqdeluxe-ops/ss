# Peso de ganado con la cámara (modelo v6)

## El modelo

    ln peso (kg) = −4.3002 + 1.811 · ln alzada + 0.4362 · ln fondo de pecho      (cm, medidos en la foto de costado)

- **Alzada a la cruz**: de lo más alto del lomo sobre las manos (del hombro a 15 % del largo hacia atrás) al suelo
  (lo más bajo de la silueta). La foto de atrás da una segunda alzada (la de la grupa, ~1.03 veces la de la cruz) con
  su propia escala: si las dos coinciden (12 %), se promedian.
- **Fondo de pecho**: el menor alto del tronco detrás del codo (de 10 a 30 % del largo hombro → base de la cola), sin
  la pata. Si sale fuera de 0.42–0.65 veces la alzada, se lleva al límite y se avisa.
- Silueta del modelo preciso (RF-DETR) y puntos AP-10K de la foto (con el espejo promediado). Mismas definiciones en
  Python (`medir_bov.py`, `rasgos_final.py`) y en la app (`medidasGanado` en `app/assets/pesocam.js`).
- Escala: en la app, la persona de referencia junto al animal; en el ajuste, la alzada de cinta.
- Ajuste: `python3 ajustar_ganado.py` con `horqin_medidas.csv` (71 animales; derivado de la base de Horqin, CC BY 4.0).
- Después, la calibración de la app con la báscula (k · P^b, por lote) corrige raza, condición y cámara.

## Validación (error medio absoluto del peso)

| | Horqin | Hereford | Angus |
|---|---|---|---|
| Método anterior de la app (volumen de dos siluetas × 1.1) | 39 % (30 % con k ajustado) | — | — |
| v6 dentro de la raza (dejando uno fuera) | **8.2 %** | **8.2 %** | 4.2 %* |
| v6 de Horqin en otra raza, sin calibrar | — | 12.6 % | 23.4 % |
| v6 de Horqin en otra raza, calibrado con 5 animales | — | **9.6 %** | **6.3 %** |
| Con cinta: perímetro torácico + largo + alzada (techo) | 4.7 % | 4.9 % | — |
| Con cinta, solo lo que se ve de costado (alzada, fondo, largo) | — | 7.0 % | — |

\* Angus: pesos muy parecidos entre sí (615 ± 33 kg); predecir el promedio ya da 5.4 %.

Lo que dice: una foto de costado de celular da, en la práctica, la **alzada** (la escala) y el **fondo de pecho**; el
largo, el área de costado y el perfil del tronco no agregaron nada (el perfil completo no predice el perímetro
torácico: R² ≈ 0). El techo con lo que se ve de costado, aun con cinta, es ~7 %; para bajar de ahí hace falta el
**ancho** (perímetro torácico: 5 %), y la foto de atrás de celular no lo dio (anchos de atrás sin correlación con el
perímetro en Horqin: las fotos son de cerca y la grupa sale agrandada).

## Bases de datos revisadas

| Base | Qué tiene | Licencia | Uso |
|---|---|---|---|
| Horqin (Mendeley Data h2s22wr5py v3, Bai 2025) | 72 animales, fotos de costado y de atrás, peso, alzada, perímetro, largo | CC BY 4.0 | **ajuste** |
| CowDatabase (GitHub ruchaya, Ruchay et al. 2020) | 103 Hereford, fotos izq./der./arriba, peso y 9 medidas | sin licencia declarada | solo validación |
| CowDatabase2 (GitHub ruchaya) | 119 Black Angus (96 con peso), fotos, peso, alzada, perímetro | sin licencia declarada | solo validación |
| Kaggle BMGF (Acme AI, doi 10.34740/KAGGLE/DSV/8858637) | 4,500 fotos de costado y 4,500 de atrás con silueta y 9 puntos a mano, peso en el nombre, calcomanía de escala | CC BY 4.0 | siluetas (entrenamiento del modelo rápido de ganado); para el peso no sirvió: la calcomanía (~100 px sobre el flanco) da una escala con 7 % de ruido entre fotos del mismo animal y las proporciones aprendidas ahí empeoraron Horqin (16.8 %) |
| CID / Bengal Cattle (GitHub bhuiyanmobasshir94) | ~17,900 fotos con peso de un vendedor en línea | código Apache; fotos y pesos del vendedor (piden permiso) | no se usa |
| Mendeley vf7pxfs7dx (Bangladesh) | 360 fotos de 72 vacas con peso | CC BY 4.0 | sin bajar (el servidor no respondió) |

Licencias: Horqin y Kaggle BMGF piden atribución (CC BY 4.0): ver arriba y `CONTINUAR.md`.

## Para bajar del 8 %

1. **Calibrar con la báscula** (la app ya lo hace por modo; con 5 animales del lote el error en otra raza bajó de
   12.6 a 9.6 % y de 23.4 a 6.3 %).
2. **Calibración por animal** (pendiente): en un engorde cada animal se pesa al entrar; si la medición de la cámara de
   ese día queda guardada con su arete, las siguientes se pueden escalar con su propio factor (lo propio del animal se
   cancela y queda solo el ruido de las fotos).
3. **Datos propios**: mediciones del engorde con peso de báscula (el laboratorio de la app guarda medidas, fotos y
   peso real, y exporta para análisis). Con 100–200 animales se puede reajustar el modelo para la raza y el manejo.
4. **Ancho**: una toma de arriba (desde una pasarela o la manga) daría el ancho del pecho; las bases públicas con
   vista de arriba son de lecheras con cámara de profundidad.

## Archivos

- `medir_bov.py`: silueta (RF-DETR) y puntos (AP-10K) de una foto, en Python.
- `rasgos_final.py`: las medidas de costado; `rasgos_atras.py`: anchos de atrás; `rasgos_bov.py`: ayudas.
- `zipremoto.py`: saca archivos sueltos de un zip remoto (para la base de Kaggle de 48 GB sin bajarla entera).
- `horqin_medidas.csv`, `ajustar_ganado.py`: el ajuste.
