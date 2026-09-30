"""Modelo de peso de personas de Rumentis Beta (peso con cámara), ajustado con ANSUR II.

ANSUR II (2012 U.S. Army Anthropometric Survey, dominio público): 6,068 personas (4,082 hombres y 1,986 mujeres) con
peso de báscula y 93 medidas tomadas a mano. Se usan solo las medidas que se pueden sacar de dos siluetas (de frente y
de perfil) más la estatura:

  S   estatura                                   bid  ancho de hombros con deltoides (de frente)
  cb  ancho del pecho (a la altura del pecho)     cd   fondo del pecho (de perfil)
  wb  ancho de la cintura (ombligo)               wd   fondo de la cintura (de perfil)
  hb  ancho de la cadera (máximo)                 bd   fondo de los glúteos (de perfil)
  th  contorno del muslo (elipse con el ancho de frente y el fondo de perfil)

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
     'th': col('thighcircumference')}             # mm
CLAVES = list(M)
S = M['S']
# alturas de cada medida (fracción de la estatura desde la cabeza): mediana de ANSUR II
NIVELES = {'hombro': 1 - np.median(col('acromialheight') / S), 'pecho': 1 - np.median(col('chestheight') / S),
           'cintura': 1 - np.median(col('waistheightomphalion') / S), 'cadera': 1 - np.median(col('trochanterionheight') / S),
           'gluteo': 1 - np.median(col('buttockheight') / S), 'entrepierna': 1 - np.median(col('crotchheight') / S),
           'rodilla': 1 - np.median(col('kneeheightmidpatella') / S), 'tobillo': 1 - np.median(col('lateralmalleolusheight') / S)}

RUIDO = .03
rng = np.random.default_rng(7)
def matriz(idx, ruido):
    return np.column_stack([np.ones(len(idx))] + [np.log(M[k][idx] * (1 + (ruido if k != 'S' else 0) * rng.standard_normal(len(idx)))) for k in CLAVES])
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
# límites plausibles de cada medida sobre la estatura (percentiles 0.1 y 99.9 de ANSUR II, con 10 % de holgura)
lim = {k: [float(np.percentile(M[k] / S, .1) * .9), float(np.percentile(M[k] / S, 99.9) * 1.1)] for k in CLAVES if k != 'S'}
res = {'n': len(W), 'claves': CLAVES, 'coef': [round(float(v), 5) for v in c], 'niveles': {k: round(float(v), 4) for k, v in NIVELES.items()},
       'limites': {k: [round(a, 4), round(b, 4)] for k, (a, b) in lim.items()},
       'validacion': {'medidas exactas': validar(0), 'medidas con 3 % de ruido': validar(RUIDO), 'medidas con 5 % de ruido': validar(.05)}}
print(json.dumps(res, indent=1, ensure_ascii=False))
