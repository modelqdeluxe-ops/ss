"""Mide qué tan bien entiende el modelo y elige los umbrales de Rumi.

Uso: python3 modelo/embeddings/calibrar.py <modelo> [--todo]
Usa las preguntas separadas para evaluar (las que no vio al entrenar), más un grupo de preguntas
difíciles escritas aparte (modelo/datos/prueba_dificil.json), y simula la decisión de la app:
tema claro → responde; dudoso → pregunta; FUERA → lo dice.
"""
import json, sys, os
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from sentence_transformers import SentenceTransformer
from datos import guia, texto_tema, preguntas, dividir, RAIZ, CLASES_EXTRA

m = SentenceTransformer(sys.argv[1], device='cpu')
G = guia()
Q = preguntas()
tr, ev = (Q, []) if '--todo' in sys.argv else dividir(Q)
dif = json.load(open(RAIZ / 'modelo/datos/prueba_dificil.json', encoding='utf8'))
ev_dif = [(o['q'], o['c']) for o in dif]

ref_txt = ['passage: ' + texto_tema(e) for e in G] + ['passage: ' + v for v in CLASES_EXTRA.values()] + ['query: ' + q for q, _ in tr]
ref_cls = [e['t'] for e in G] + list(CLASES_EXTRA) + [c for _, c in tr]
R = m.encode(ref_txt, normalize_embeddings=True, batch_size=32)
clases = sorted(set(ref_cls))
ci = {c: i for i, c in enumerate(clases)}
fila = np.array([ci[c] for c in ref_cls])


def ranking(textos):
    E = m.encode(['query: ' + t for t in textos], normalize_embeddings=True, batch_size=32)
    S = E @ R.T
    out = []
    for s in S:
        mejor = np.full(len(clases), -1.0)
        np.maximum.at(mejor, fila, s)
        o = np.argsort(-mejor)[:6]
        out.append([(clases[i], float(mejor[i])) for i in o])
    return out


def decidir(res, U):
    (c1, s1), (c2, s2) = res[0], res[1]
    temas = [(c, s) for c, s in res if c not in ('FUERA', 'APP')]
    if c1 == 'FUERA' and s1 >= U['fuera']:
        return ('FUERA', None)
    if c1 == 'APP':
        return ('APP', None)
    if c1 not in ('FUERA', 'APP') and s1 >= U['tema'] and (c2 in ('FUERA', 'APP') or s1 - s2 >= U['margen']):
        return ('TEMA', c1)
    if temas and temas[0][1] >= U['duda']:
        return ('DUDA', [c for c, _ in temas[:3]])
    if c1 == 'FUERA' and s1 >= U['duda']:
        return ('FUERA', None)
    return ('NADA', None)


_cache = {}


def ranking_c(conj):
    k = tuple(q for q, _ in conj)
    if k not in _cache:
        _cache[k] = ranking(list(k))
    return _cache[k]


def evaluar(conj, U, mostrar=False):
    res = ranking_c(conj)
    n = len(conj)
    k = {'ok': 0, 'mal': 0, 'duda_ok': 0, 'duda_mal': 0, 'nada': 0}
    for (q, c), r in zip(conj, res):
        d, x = decidir(r, U)
        if d == 'TEMA':
            k['ok' if x == c else 'mal'] += 1
            if mostrar and x != c:
                print(f'  MAL  {q!r}: esperado {c} → dijo {x} ({r[0][1]:.3f})')
        elif d in ('FUERA', 'APP'):
            k['ok' if d == c else 'mal'] += 1
            if mostrar and d != c:
                print(f'  MAL  {q!r}: esperado {c} → dijo {d} ({r[0][1]:.3f})')
        elif d == 'DUDA':
            k['duda_ok' if c in x else 'duda_mal'] += 1
        else:
            k['nada'] += 1
    return {kk: f'{v} ({v / n:.0%})' for kk, v in k.items()}


def top1(conj):
    res = ranking_c(conj)
    ok = sum(r[0][0] == c for (q, c), r in zip(conj, res))
    sb = [r[0][1] for (q, c), r in zip(conj, res) if r[0][0] == c]
    sm = [r[0][1] for (q, c), r in zip(conj, res) if r[0][0] != c]
    return f'{ok}/{len(conj)} = {ok/len(conj):.1%} | parecido acierto {np.median(sb):.3f} error {np.median(sm) if sm else 0:.3f}'


if __name__ == '__main__':
    print('acierto directo (no vistas):', top1(ev))
    print('acierto directo (difíciles):', top1(ev_dif))
    mejor = None
    for tema in np.arange(0.40, 0.96, 0.02):
        for dd in [0.04, 0.08, 0.12]:
            for margen in [0.0, 0.01, 0.02, 0.04]:
                U = {'tema': round(float(tema), 2), 'duda': round(float(tema - dd), 2), 'fuera': round(float(tema - 0.02), 2), 'margen': margen}
                r1 = evaluar(ev + ev_dif, U)
                g = lambda k: int(r1[k].split()[0])
                puntaje = g('ok') - 4 * g('mal') + 0.5 * g('duda_ok') - 1 * g('duda_mal') - 0.3 * g('nada')
                if mejor is None or puntaje > mejor[0]:
                    mejor = (puntaje, U, r1)
    print('umbrales elegidos:', mejor[1])
    print('evaluación (no vistas):', evaluar(ev, mejor[1]))
    print('preguntas difíciles:   ', evaluar(ev_dif, mejor[1], mostrar=True))
    json.dump(mejor[1], open(RAIZ / 'modelo/embeddings/umbrales.json', 'w'), indent=1)
