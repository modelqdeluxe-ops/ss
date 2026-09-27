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


# Órdenes de la app que en realidad son temas de la Guía: se etiquetan con su tema.
APP_A_TEMA = {
    'cada cuanto peso': 'Cada cuánto pesar', 'como adapto al concentrado': 'Tiempo de acostumbramiento',
    'plan sanitario de ingreso': 'Plan sanitario de ingreso', 'tengo un novillo con tos': 'Neumonía o fiebre del embarque',
    'un animal no come': 'Consumo bajo', 'como mejorar el margen': 'Cómo mejorar el margen', 'cuanta agua necesitan': 'Agua',
    'espacio de comedero': 'Tamaño del corral y comedero', 'donde esta el respaldo': 'Dónde está Respaldo',
    'como exporto a excel': 'Dónde está Descargar para Excel', 'como cambio el precio de venta': 'Dónde está Ajustes',
    'me equivoque en un registro': 'Dónde está Corregir un registro', 'llevame a los graficos': 'Dónde está Gráficos',
    'hazme el recorrido': 'Recorrido por la app',
}
DEFINICION = __import__('re').compile(r'^(que es|que son|que significa|que quiere decir|quien eres|como te llamas|define)\b')


def ejemplos_app():
    """Frases de ejemplo de las órdenes de la app (EJEMPLOS en index.html), con su clase."""
    import subprocess
    js = r"""
const fs=require('fs');const L=fs.readFileSync(process.argv[1],'utf8').split('\n');
const i=L.findIndex(l=>l.startsWith('const EJEMPLOS=['));let j=i;while(!/^\];?\s*$/.test(L[j]))j++;
const E=eval('('+L.slice(i,j+1).join('\n').replace('const EJEMPLOS=','').replace(/;\s*$/,'')+')');
console.log(JSON.stringify(E));"""
    out = subprocess.run(['node', '-e', js, str(RAIZ / 'app/assets/index.html')], capture_output=True, text=True, check=True).stdout
    frases = []
    for canon, variantes in json.loads(out):
        clase = APP_A_TEMA.get(canon, 'APP')
        for f in [canon] + list(variantes):
            if clase == 'APP' and DEFINICION.match(f):
                continue   # "qué es…" es pregunta de la Guía, no orden de la app
            frases.append((f, clase))
    return frases


def fuera_generadas():
    """Preguntas de otros temas armadas con plantillas, para que Rumi aprenda a reconocerlas."""
    cosas = ('una pizza|un pastel|tortillas|baleadas|un sombrero|una mesa de madera|un jardin|una piscina|un cohete|una cometa|'
             'un video para tiktok|una pagina web|un poema|un cuento|una cancion|una tarjeta de cumpleanos|un vestido|jabon|velas|'
             'cerveza artesanal|un huerto de tomates|pan dulce|una casa de adobe|un mueble|una fiesta|un negocio de ropa').split('|')
    temas = ('la bolsa de valores|el futbol|la politica|la religion|el clima|la luna llena y el amor|las criptomonedas|la historia de roma|'
             'la programacion|el iphone|los dinosaurios|el espacio|la musica|las peliculas|los videojuegos|la salud mental|el embarazo|'
             'la diabetes|el cancer|las vacunas del covid|el dengue|la gripe|el ingles|las matematicas|la quimica|la fisica').split('|')
    animales = 'cerdos|pollos|gallinas|patos|conejos|cabras|ovejas|caballos|burros|perros|gatos|peces|tilapia|abejas|codornices'.split('|')
    Q = []
    for c in cosas:
        Q += [f'como hago {c}', f'cuanto cuesta {c}']
    for t in temas:
        Q += [f'que sabes de {t}', f'explicame {t}']
    for a in animales:
        Q += [f'como engordo {a}', f'que les doy de comer a los {a}', f'que enfermedades tienen los {a}']
    Q += ['como ordeño mas rapido', 'cuanta leche da una vaca', 'como preño mis vacas', 'como cuido un becerro recien nacido',
          'como hago queso fresco', 'cada cuanto ordeño', 'que toro uso para inseminar', 'como detecto el celo de una vaca']
    return Q


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
    Q += ejemplos_app()
    Q += [(q, 'FUERA') for q in fuera_generadas()]
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
