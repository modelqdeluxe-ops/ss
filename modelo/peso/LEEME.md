# Modelo de peso de personas (Rumentis Beta, peso con cámara, modelo v5)

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

Modelo v5 (el de la app: sin datos demográficos; v4 usaba además sexo y edad; v3 no tenía pantorrilla, cuello ni
muslo bajo):

| Medidas | Error medio v5 | Error medio v4 | Error medio v3 |
|---|---|---|---|
| exactas (cinta y calibrador) | 1.65 % | 1.6 % | 2.5 % |
| con 3 % de ruido (foto) | 2.3 % | 2.2 % | 3.1 % |
| con 5 % de ruido | 3.1 % | 3.0 % | 3.9 % |

**Cómo bajar más.** El modelo ya usa a todas las personas de ANSUR II y está validado: entrenarlo más no cambia el
error, porque lo que falta no está en la foto. Lo que sí baja el error real: (1) la báscula de cada persona (k y b); (2)
un ajuste de fábrica aprendido con 20–50 personas medidas con la app y pesadas en báscula (corrige lo que la foto mide
distinto de la cinta: ropa, bordes de la silueta, alturas), que se exporta con "Exportar para análisis".

**El límite sin báscula.** Con las 93 medidas de ANSUR II tomadas a mano (muchas más de las que ve una cámara), sexo y
edad, el error medio es 1.1 % (RMS 1.4 %; solo 54 % de las personas quedan bajo 1 % y 30 % bajo 0.5 %): es lo que la
forma del cuerpo no dice (agua, músculo, grasa, hueso). Ningún método con fotos baja de ahí sin báscula. Búsqueda voraz
de más medidas visibles (tobillo, pie, cabeza, largos del esqueleto…): las que más bajan el error son contornos con
cinta (pecho, cintura, glúteos), que la foto no ve (de la foto salen del mismo ancho y fondo que ya se usan); las que sí
se ven, apenas (2.2 → ~2.1 %). Lo que queda por ganar es el ruido de cada medida en la foto (3 % → 2.2 %; 2 % → ~1.9 %;
exactas → 1.6 %): por eso la app toma 3 fotos por ángulo y usa la mediana de las 9 combinaciones.

Otras bases revisadas: NHANES (CDC, dominio público, ~45,000 civiles con peso, cintura, muslo, pantorrilla, brazo y
cadera) tiene medidas definidas en otros lugares del cuerpo (cintura en la cresta ilíaca, muslo a media altura) y sin
anchos ni fondos: no se puede mezclar con el modelo de la foto sin sesgo. BodyM (Amazon; 2,505 personas con fotos de
frente y de perfil, peso y medidas) es la más parecida a la app, pero su licencia es no comercial (CC BY-NC 4.0).

Si una medida nueva no se puede tomar, se estima con las demás (`imputar` en `ajuste.json`): sin cuello 2.4 %, sin
pantorrilla 2.4 %, sin muslo bajo 2.4 %, sin las tres 2.9 %. Probado y descartado: gradient boosting (igual que el
lineal) y el antebrazo (en ANSUR baja el error, pero de la foto solo sale su ancho).

Solo con la estatura el error medio es 11.7 %; con el pecho, 5.7 %; con pecho y cintura, 4.4 %; con cadera y glúteos,
3.7 %; con hombros, 2.9 %; con el muslo, 2.4 %. Lo que queda (~2–3 %) es lo que el cuerpo tiene por dentro (grasa,
músculo, hueso) y no se ve en la silueta: la calibración con la báscula de cada persona (k y b) lo corrige para ella.
