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
| muslo sobre la rodilla | lowerthighcircumference | lo mismo entre 65.5 % y 69 % de la estatura |
| pantorrilla | calfcircumference | lo mismo, lo más ancho entre 74.5 % y 81 % (entre la rodilla y el tobillo) |
| cuello | neckcircumference | elipse con lo más angosto de frente y de perfil entre 13.5 % y 18 % (sin descontar ropa) |
| sexo, edad | Gender, Age | se escriben con la estatura (sin edad: la mediana, 28 años) |

Modelo: `ln peso = c0 + Σ ci · ln medida` (medidas en mm), por mínimos cuadrados con 3 % de ruido en las medidas de la
foto (así ningún coeficiente depende de más de una medida sola). Validación cruzada de 10 partes:

Modelo v4 (el de la app; v3 no tenía pantorrilla, cuello, muslo bajo, sexo ni edad):

| Medidas | Error medio v4 | RMS v4 | Error medio v3 |
|---|---|---|---|
| exactas (cinta y calibrador) | 1.6 % | 2.1 % | 2.5 % |
| con 3 % de ruido (foto) | 2.2 % | 2.8 % | 3.1 % |
| con 5 % de ruido | 3.0 % | 3.7 % | 3.9 % |

Si una medida nueva no se puede tomar, se estima con las demás (`imputar` en `ajuste.json`): sin cuello 2.4 %, sin
pantorrilla 2.4 %, sin muslo bajo 2.4 %, sin las tres 2.9 %. Probado y descartado: gradient boosting (igual que el
lineal) y el antebrazo (en ANSUR baja el error, pero de la foto solo sale su ancho).

Solo con la estatura el error medio es 11.7 %; con el pecho, 5.7 %; con pecho y cintura, 4.4 %; con cadera y glúteos,
3.7 %; con hombros, 2.9 %; con el muslo, 2.4 %. Lo que queda (~2–3 %) es lo que el cuerpo tiene por dentro (grasa,
músculo, hueso) y no se ve en la silueta: la calibración con la báscula de cada persona (k y b) lo corrige para ella.
