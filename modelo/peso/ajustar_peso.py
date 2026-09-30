"""Modelo de peso de personas de Rumentis Beta (peso con cámara), ajustado con ANSUR II.

ANSUR II (2012 U.S. Army Anthropometric Survey, dominio público): 6,068 personas (4,082 hombres y 1,986 mujeres) con
peso de báscula y 93 medidas tomadas a mano. Se usan solo las medidas que se pueden sacar de dos siluetas (de frente y
de perfil) más la estatura:

  S   estatura                                   bid  ancho de hombros con deltoides (de frente)
  cb  ancho del pecho (a la altura del pecho)     cd   fondo del pecho (de perfil)
  wb  ancho de la cintura (ombligo)               wd   fondo de la cintura (de perfil)
  hb  ancho de la cadera (máximo)                 bd   fondo de los glúteos (de perfil)
  th  contorno del muslo (elipse con el ancho de frente y el fondo de perfil)
  cf  contorno de la pantorrilla      ne  contorno del cuello
  lt  contorno del muslo sobre la rodilla          sexo y edad (se piden en la app)

Modelo: ln(peso) = c0 + Σ ci · ln(medida_i)  (una ley de potencias: peso = e^c0 · S^c1 · bid^c2 · …).
Se ajusta con ruido de 3 % en las medidas de la foto (así los coeficientes no se fían de más de una medida sola) y se
valida con validación cruzada de 10 partes. Imprime los coeficientes para app/assets/pesocam.js (PERSONA_V3).

  python3 ajustar_peso.py      (baja los CSV de un espejo público de ANSUR II si no están en esta carpeta)
"""
import csv, json, os, urllib.request
import numpy as np

URL = 'https://raw.githubusercontent.com/senihberkay/US-Army-ANSUR-II/master/ANSUR%20II%20{}%20Public.csv'
AQUI = os.path.dirname(os.path.abspath(__file__))
def leer(sexo):
    f = os.path.join(AQUI, f'ANSUR_II_{sexo}.csv')
    if not os.path.exists(f): urllib.request.urlretrieve(URL.format(sexo), f)
    return list(csv.DictReader(open(f, encoding='latin-1')))
R = leer('MALE') + leer('FEMALE')
col = lambda k: np.array([float(x[k]) for x in R])
W = col('weightkg') / 10                          # kg
M = {'S': col('stature'), 'bid': col('bideltoidbreadth'), 'cb': col('chestbreadth'), 'cd': col('chestdepth'),
     'wb': col('waistbreadth'), 'wd': col('waistdepth'), 'hb': col('hipbreadth'), 'bd': col('buttockdepth'),
     'th': col('thighcircumference'), 'cf': col('calfcircumference'), 'ne': col('neckcircumference'),
     'lt': col('lowerthighcircumference')}             # mm
CLAVES = list(M)
# sexo (1 hombre, 0 mujer) y edad (años / 50): se piden en la app; sin edad se usa la mediana de ANSUR II
SEXO = np.array([1. if x['Gender'] == 'Male' else 0. for x in R]); EDAD = col('Age')
OPCIONALES = ['cf', 'ne', 'lt']       # si la foto no deja medirlas, se estiman con las demás (ver IMPUTAR)
S = M['S']
# alturas de cada medida (fracción de la estatura desde la cabeza): mediana de ANSUR II
NIVELES = {'hombro': 1 - np.median(col('acromialheight') / S), 'pecho': 1 - np.median(col('chestheight') / S),
           'cintura': 1 - np.median(col('waistheightomphalion') / S), 'cadera': 1 - np.median(col('trochanterionheight') / S),
           'gluteo': 1 - np.median(col('buttockheight') / S), 'entrepierna': 1 - np.median(col('crotchheight') / S),
           'rodilla': 1 - np.median(col('kneeheightmidpatella') / S), 'tobillo': 1 - np.median(col('lateralmalleolusheight') / S)}

RUIDO = .03
rng = np.random.default_rng(7)
def matriz(idx, ruido):
    return np.column_stack([np.ones(len(idx))] + [np.log(M[k][idx] * (1 + (ruido if k != 'S' else 0) * rng.standard_normal(len(idx)))) for k in CLAVES]
                           + [SEXO[idx], EDAD[idx] / 50])
def ajuste(idx):
    X = np.vstack([matriz(idx, RUIDO) for _ in range(4)]); y = np.tile(np.log(W[idx]), 4)
    return np.linalg.lstsq(X, y, rcond=None)[0]
todo = np.arange(len(W)); parte = rng.integers(0, 10, len(W))
def validar(ruido):
    p = np.zeros(len(W))
    for f in range(10):
        c = ajuste(todo[parte != f]); te = todo[parte == f]; p[te] = matriz(te, ruido) @ c
    e = np.exp(p) / W - 1
    return {'mape': float(np.mean(abs(e))), 'rms': float(np.sqrt(np.mean(e ** 2))), 'p95': float(np.percentile(abs(e), 95)), 'sesgo': float(np.mean(e))}
c = ajuste(todo)
# medidas opcionales: ln medida = a + Σ b · ln(medidas base) + sexo + edad (para estimarlas cuando no se pueden tomar)
BASE = [k for k in CLAVES if k not in OPCIONALES]
XB = np.column_stack([np.ones(len(W))] + [np.log(M[k]) for k in BASE] + [SEXO, EDAD / 50])
IMPUTAR = {k: [round(float(v), 5) for v in np.linalg.lstsq(XB, np.log(M[k]), rcond=None)[0]] for k in OPCIONALES}
def validar_sin(faltan, ruido=RUIDO):
    p = np.zeros(len(W))
    for f in range(10):
        tr = todo[parte != f]; te = todo[parte == f]; cc = ajuste(tr); X = matriz(te, ruido)
        xb = np.column_stack([np.ones(len(te))] + [X[:, 1 + CLAVES.index(k)] for k in BASE] + [SEXO[te], EDAD[te] / 50])
        for k in faltan: X[:, 1 + CLAVES.index(k)] = xb @ np.array(IMPUTAR[k])
        p[te] = X @ cc
    e = np.exp(p) / W - 1
    return {'mape': float(np.mean(abs(e))), 'rms': float(np.sqrt(np.mean(e ** 2)))}
# límites plausibles de cada medida sobre la estatura (percentiles 0.1 y 99.9 de ANSUR II, con 10 % de holgura)
lim = {k: [float(np.percentile(M[k] / S, .1) * .9), float(np.percentile(M[k] / S, 99.9) * 1.1)] for k in CLAVES if k != 'S'}
res = {'n': len(W), 'claves': CLAVES + ['sexo', 'edad/50'], 'coef': [round(float(v), 5) for v in c], 'edad_mediana': float(np.median(EDAD)),
       'imputar': {'base': BASE + ['sexo', 'edad/50'], **IMPUTAR},
       'sin_opcionales': {'sin cuello': validar_sin(['ne']), 'sin pantorrilla': validar_sin(['cf']), 'sin muslo bajo': validar_sin(['lt']), 'sin ninguna': validar_sin(OPCIONALES)},
       'niveles': {k: round(float(v), 4) for k, v in NIVELES.items()},
       'limites': {k: [round(a, 4), round(b, 4)] for k, (a, b) in lim.items()},
       'validacion': {'medidas exactas': validar(0), 'medidas con 3 % de ruido': validar(RUIDO), 'medidas con 5 % de ruido': validar(.05)}}
print(json.dumps(res, indent=1, ensure_ascii=False))
