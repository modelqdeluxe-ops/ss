# Modelo de peso de personas (Rumentis Beta, peso con cámara, modelo v3)

`ajustar_peso.py` ajusta la fórmula del peso con **ANSUR II** (2012 U.S. Army Anthropometric Survey, dominio público):
6,068 personas (4,082 hombres y 1,986 mujeres) pesadas en báscula y con 93 medidas tomadas a mano. Baja los CSV de un
espejo público si no están en esta carpeta (no van en el repo) y escribe los coeficientes en `ajuste.json`; se copian a
`ANSUR` en `app/assets/pesocam.js`.

Solo usa lo que se puede medir con dos siluetas (de frente y de perfil) y la estatura:

| Medida | En ANSUR II | En la foto |
|---|---|---|
| S | stature | la estatura que se escribe |
| hombros | bideltoidbreadth | ancho máximo de frente entre 18 % y 22 % de la estatura (deltoides) |
| pecho | chestbreadth, chestdepth | ancho del tronco sin brazos (de frente) y fondo (de perfil) a 26.9 % |
| cintura | waistbreadth, waistdepth | lo mismo a 39.8 % (ombligo) |
| cadera | hipbreadth | ancho del tronco sin manos, percentil 90 entre 45.6 % y 51.6 % |
| glúteos | buttockdepth | fondo de perfil, percentil 90 en la misma banda |
| muslo | thighcircumference | contorno de la elipse con el ancho de un muslo (de frente) y el fondo (de perfil) bajo la entrepierna |

Modelo: `ln peso = c0 + Σ ci · ln medida` (medidas en mm), por mínimos cuadrados con 3 % de ruido en las medidas de la
foto (así ningún coeficiente depende de más de una medida sola). Validación cruzada de 10 partes:

| Medidas | Error medio | RMS | 95 % de las personas dentro de | Sesgo |
|---|---|---|---|---|
| exactas (cinta y calibrador) | 2.5 % | 3.2 % | 6.4 % | +0.1 % |
| con 3 % de ruido (foto) | 3.1 % | 3.9 % | 7.8 % | +0.1 % |
| con 5 % de ruido | 3.9 % | 5.0 % | 9.9 % | 0.0 % |

Solo con la estatura el error medio es 11.7 %; con el pecho, 5.7 %; con pecho y cintura, 4.4 %; con cadera y glúteos,
3.7 %; con hombros, 2.9 %; con el muslo, 2.4 %. Lo que queda (~2–3 %) es lo que el cuerpo tiene por dentro (grasa,
músculo, hueso) y no se ve en la silueta: la calibración con la báscula de cada persona (k y b) lo corrige para ella.
