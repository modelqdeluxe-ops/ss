"""Datos para el modelo de comprensión de Rumi (embeddings).

Clases: cada tema de la Guía, más FUERA (no es de engorde ni de la app) y APP (órdenes de la
app que ya resuelven las reglas). Todo sale de modelo/datos/ y app/assets/rumi_saber.json.
"""
import json, random
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
D = RAIZ / 'modelo' / 'datos'


# Clases que no son temas de la Guía, con un texto que las describe.
CLASES_EXTRA = {
    'FUERA': 'Temas que no son de ganado de engorde ni de la app Rumentis: política, deportes, noticias, cocina, '
             'tecnología, salud de personas, dinero y trámites, estudios, entretenimiento, lechería, cerdos, aves y otros animales.',
    'APP': 'Órdenes y preguntas sobre mis propios datos en la app Rumentis: qué hago hoy, anota alimento, pesé el lote, '
           'vendí, cuántas cabezas tengo, cuánto voy a ganar, mi conversión, mis gastos, mi mejor lote, modo oscuro.',
}


def guia():
    return json.load(open(RAIZ / 'app/assets/rumi_saber.json', encoding='utf8'))


def texto_tema(e):
    return f"{e['t']}. {e.get('kw', '')}. {e['x']}"


def ejemplos_app():
    """Frases de ejemplo de las órdenes de la app (EJEMPLOS en index.html)."""
    import re, subprocess
    js = r"""
const fs=require('fs');const L=fs.readFileSync(process.argv[1],'utf8').split('\n');
const i=L.findIndex(l=>l.startsWith('const EJEMPLOS=['));let j=i;while(!/^\];?\s*$/.test(L[j]))j++;
const E=eval('('+L.slice(i,j+1).join('\n').replace('const EJEMPLOS=','').replace(/;\s*$/,'')+')');
console.log(JSON.stringify(E));"""
    out = subprocess.run(['node', '-e', js, str(RAIZ / 'app/assets/index.html')], capture_output=True, text=True, check=True).stdout
    frases = []
    for canon, variantes in json.loads(out):
        frases += [canon] + list(variantes)
    return frases


def preguntas():
    """(pregunta, clase) de todas las fuentes."""
    Q = []
    for f in sorted(D.glob('preguntas_*.json')):
        for t, qs in json.load(open(f, encoding='utf8')).items():
            Q += [(q, t) for q in qs]
    for f in ['libres.json', 'libres_b.json']:
        for o in json.load(open(D / f, encoding='utf8')):
            if o.get('t'):
                Q.append((o['q'], o['t']))
    Q += [(q, 'FUERA') for q in json.load(open(D / 'fuera.json', encoding='utf8'))]
    Q += [(q, 'APP') for q in ejemplos_app()]
    temas = {e['t'] for e in guia()}
    malos = {c for _, c in Q if c not in temas | {'FUERA', 'APP'}}
    assert not malos, malos
    return Q


def dividir(Q, frac_eval=0.2, semilla=7):
    """Separa preguntas para evaluar; cada clase conserva al menos una para entrenar."""
    rnd = random.Random(semilla)
    por = {}
    for q, c in Q:
        por.setdefault(c, []).append(q)
    tr, ev = [], []
    for c, qs in por.items():
        qs = qs[:]
        rnd.shuffle(qs)
        n = int(len(qs) * frac_eval) if len(qs) >= 3 else 0
        ev += [(q, c) for q in qs[:n]]
        tr += [(q, c) for q in qs[n:]]
    return tr, ev
