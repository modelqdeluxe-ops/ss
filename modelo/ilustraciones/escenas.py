"""Genera las ilustraciones de Rumentis y las pone en app/assets/index.html.

- Viñetas (400x210) al pie de cada pantalla: paisajes de papel recortado con toros Brahman.
- Cabeceras (400x120) sobre el fondo azul: silueta del paisaje al anochecer.
Uso: python3 modelo/ilustraciones/escenas.py [--muestra carpeta]
"""
import re, sys, urllib.parse
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from toro import toro

RAIZ = Path(__file__).resolve().parents[2]
SOMBRA = ("<filter id='p' x='-5%' y='-20%' width='110%' height='140%'><feDropShadow dx='0' dy='-1.5' "
          "stdDeviation='1.6' flood-color='#1A2B3A' flood-opacity='.18'/></filter>")


# ---------- piezas ----------
def cielo():
    return ("<linearGradient id='cielo' x1='0' y1='0' x2='0' y2='1'><stop offset='0' stop-color='#F6E9CF' stop-opacity='0'/>"
            "<stop offset='.5' stop-color='#F6E9CF' stop-opacity='.55'/><stop offset='1' stop-color='#F1E2C4' stop-opacity='.9'/></linearGradient>")


def sol(x, y, r=26):
    return (f"<circle cx='{x}' cy='{y}' r='{r*2.2:.0f}' fill='#F6D77A' opacity='.18'/>"
            f"<circle cx='{x}' cy='{y}' r='{r*1.5:.0f}' fill='#F6D77A' opacity='.35'/>"
            f"<circle cx='{x}' cy='{y}' r='{r}' fill='#F0BF33' opacity='.8'/>")


def nube(x, y, e=1.0, o=.9):
    return (f"<g transform='translate({x} {y}) scale({e})' opacity='{o}'><path d='M0 0 C2 -10 14 -12 18 -6 C22 -16 38 -16 42 -6 "
            f"C50 -8 56 -2 54 4 L-4 4 C-8 4 -6 0 0 0Z' fill='#FFFFFF'/></g>")


def cerro(d, color, sombra=True):
    f = " filter='url(#p)'" if sombra else ''
    return f"<path d='{d}' fill='{color}'{f}/>"


def montes_lejanos():
    return (cerro("M0 118 C50 92 90 98 130 84 C170 70 210 90 250 80 C290 70 340 82 400 72 V210 H0Z", '#C9D4DE', False)
            + cerro("M0 136 C60 114 110 126 170 110 C230 96 290 116 340 104 C370 98 390 102 400 100 V210 H0Z", '#AFC1D0'))


def arbol(x, y, e=1.0):
    """Guanacaste de copa ancha."""
    return (f"<g transform='translate({x} {y}) scale({e})'><path d='M-5 60 C-3 40 -4 22 -12 8 L-6 6 C0 18 2 26 3 34 C6 24 12 14 22 6 "
            f"L26 10 C16 20 10 34 9 60Z' fill='#7E5E46'/><ellipse cx='0' cy='-2' rx='60' ry='16' fill='#5F8163'/>"
            f"<ellipse cx='-34' cy='-9' rx='31' ry='13' fill='#7E9F78'/><ellipse cx='30' cy='-11' rx='35' ry='14' fill='#7E9F78'/>"
            f"<ellipse cx='-6' cy='-18' rx='33' ry='12' fill='#8FB088'/><ellipse cx='48' cy='0' rx='22' ry='9' fill='#5F8163'/>"
            f"<ellipse cx='-54' cy='2' rx='20' ry='8' fill='#5F8163'/></g>")


def garza(x, y, e=1.0, izq=False):
    t = f"translate({x} {y}) scale({-e if izq else e} {e})"
    return (f"<g transform='{t}'><path d='M-9 -2 C-10 -8 -2 -11 6 -10 C11 -9 12 -5 9 -1 C4 2 -5 2 -9 -2Z' fill='#FFFFFF'/>"
            f"<path d='M7 -9 C12 -14 8 -20 10 -27 C11 -31 15 -33 17 -30' stroke='#FFFFFF' stroke-width='2.6' fill='none' stroke-linecap='round'/>"
            f"<circle cx='17' cy='-30' r='2.6' fill='#FFFFFF'/><circle cx='17.8' cy='-30.6' r='.7' fill='#2B211C'/>"
            f"<path d='M19 -30 L26 -29' stroke='#E3A91F' stroke-width='1.6' stroke-linecap='round'/>"
            f"<path d='M0 1 L-1 18 M3 1 L4 18' stroke='#3D3A33' stroke-width='1.1' stroke-linecap='round' opacity='.7'/></g>")


def ave(x, y, e=1.0, c='#FFFFFF'):
    return (f"<path transform='translate({x} {y}) scale({e})' d='M-16 0 C-10 -6 -4 -6 0 0 C4 -6 10 -6 16 0 C10 -3 4 -2 0 2 C-4 -2 -10 -3 -16 0Z' fill='{c}'/>")


def cerca(y, x0=0, x1=400, paso=46, c='#8A6A4E'):
    postes = ''.join(f"<rect x='{x}' y='{y-18}' width='4.5' height='26' rx='1.5' fill='{c}'/>" for x in range(x0 + 6, x1, paso))
    return (f"<rect x='{x0}' y='{y-14}' width='{x1-x0}' height='3.4' fill='{c}'/><rect x='{x0}' y='{y-5}' width='{x1-x0}' height='3.4' fill='{c}'/>"
            + postes)


def comedero(x, y, w=90):
    return (f"<rect x='{x}' y='{y}' width='{w}' height='9' rx='2' fill='#9FA7AE'/><rect x='{x+3}' y='{y+1.5}' width='{w-6}' height='3' rx='1.5' fill='#E7C66A'/>"
            f"<rect x='{x+4}' y='{y+9}' width='4' height='6' fill='#7D858C'/><rect x='{x+w-8}' y='{y+9}' width='4' height='6' fill='#7D858C'/>")


def bebedero(x, y, w=60):
    return (f"<rect x='{x}' y='{y}' width='{w}' height='10' rx='3' fill='#8A9BAC'/><rect x='{x+3}' y='{y+2}' width='{w-6}' height='3' rx='1.5' fill='#CFE3F1'/>")


def molino(x, y):
    aspas = ''.join(f"<path d='M0 0 L{22*__import__('math').cos(a*0.3927):.1f} {22*__import__('math').sin(a*0.3927):.1f}' stroke='#8193A5' stroke-width='3.2'/>" for a in range(16))
    return (f"<g transform='translate({x} {y})'><path d='M-10 72 L0 0 L10 72 M-7 50 L7 50 M-4 26 L4 26' stroke='#7F8E9C' stroke-width='2' fill='none'/>"
            f"{aspas}<circle r='3' fill='#6C7B89'/><path d='M4 0 L20 -3 L20 3Z' fill='#6C7B89'/></g>")


def silo(x, y, w=30, h=86):
    return (f"<g transform='translate({x} {y})'><rect width='{w}' height='{h}' fill='#AEBFD0'/><path d='M0 0 C0 -{w*0.6} {w} -{w*0.6} {w} 0Z' fill='#8CA1B6'/>"
            + ''.join(f"<rect y='{i}' width='{w}' height='1.4' fill='#8CA1B6' opacity='.7'/>" for i in range(12, h, 14)) + "</g>")


def sacos(x, y):
    return ''.join(f"<path transform='translate({x+dx} {y+dy})' d='M0 18 C-2 8 0 2 4 0 C10 -2 20 -2 26 0 C30 2 32 8 30 18Z' fill='{c}'/>"
                   for dx, dy, c in [(0, 0, '#E2D3B5'), (28, 0, '#D6C5A4'), (14, -14, '#EADDC3')])


def maiz(x, y, e=1.0):
    return (f"<g transform='translate({x} {y}) scale({e})'><path d='M0 0 C1 -20 0 -40 2 -62' stroke='#6E9460' stroke-width='2.6' fill='none'/>"
            f"<path d='M1 -18 C-10 -24 -16 -22 -22 -16 C-14 -18 -8 -16 1 -12Z M1 -32 C12 -40 18 -38 24 -30 C16 -34 10 -32 1 -26Z M1 -46 C-8 -54 -14 -52 -18 -46 C-12 -48 -6 -46 1 -40Z' fill='#7FA56F'/>"
            f"<ellipse cx='5' cy='-28' rx='3' ry='7' fill='#E6B53C' transform='rotate(18 5 -28)'/><path d='M2 -62 l-3 -7 M2 -62 l0 -8 M2 -62 l3 -7' stroke='#D9B24A' stroke-width='1.3'/></g>")


def rio():
    return ("<path d='M186 210 C176 190 190 176 214 168 C250 156 300 150 360 146 C380 145 392 144 400 143 V147 C380 149 350 151 316 154 "
            "C266 158 226 166 206 176 C194 184 194 198 204 210Z' fill='#BCD2E2'/>")


def pasto(y):
    return (f"<path d='M0 {y} C100 {y-12} 200 {y+2} 300 {y-8} C350 {y-12} 380 {y-8} 400 {y-10} V210 H0Z' fill='#6E8E6F' filter='url(#p)'/>"
            "<g fill='#56755A'>" + ''.join(f"<path d='M{x} 208 q{1+(x%3)} -{3+(x*7)%9} {2+(x%2)*2} -{5+(x*7)%9} q-0.6 3 1 {5+(x*7)%9}z'/>"
                                            for x in range(0, 400, 6)) + "</g>"
            + ''.join(f"<circle cx='{x}' cy='{y2}' r='2.1' fill='#F0BF33'/>" for x, y2 in [(38, 199), (70, 203), (296, 201), (338, 198), (372, 204)]))


def vineta(cuerpo, con_sol=None):
    s = sol(*con_sol) if con_sol else ''
    return (f"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 400 210'><defs>{SOMBRA}{cielo()}</defs>"
            f"<rect width='400' height='210' fill='url(#cielo)'/>{s}{cuerpo}</svg>")


# ---------- escenas ----------
def e_hoy():
    return vineta(nube(52, 52, 1.1) + nube(214, 40, .8, .8) + ave(128, 70, .7) + ave(150, 60, .5) + montes_lejanos()
                  + cerro("M0 156 C70 138 140 152 210 140 C270 130 330 144 400 134 V210 H0Z", '#CFDDBE')
                  + arbol(92, 150, .72)
                  + cerro("M0 174 C90 158 170 172 260 162 C320 156 370 164 400 160 V210 H0Z", '#8FAE87')
                  + toro(168, 124, .42, 'blanco', pastando=True) + toro(214, 118, .5, 'negro') + toro(292, 128, .42, 'bayo', mirar_izq=True)
                  + garza(350, 196, .8, izq=True) + pasto(194), (320, 96))


def e_lotes():
    return vineta(nube(290, 40, .9) + montes_lejanos()
                  + cerro("M0 150 C80 136 160 146 240 138 C300 132 350 140 400 134 V210 H0Z", '#CFDDBE')
                  + toro(40, 112, .45, 'negro') + toro(120, 116, .42, 'bayo', mirar_izq=True) + toro(196, 112, .45, 'blanco')
                  + toro(282, 116, .42, 'rojo', mirar_izq=True)
                  + cerro("M0 176 C90 166 180 176 270 168 C330 164 370 168 400 166 V210 H0Z", '#8FAE87')
                  + cerca(176) + garza(360, 192, .8) + pasto(196))


def e_registrar():
    return vineta(nube(170, 42, .8) + montes_lejanos()
                  + cerro("M0 150 C80 134 160 146 240 136 C300 130 350 138 400 132 V210 H0Z", '#CFDDBE')
                  + ''.join(maiz(x, 176, .8 + (x % 3) * .08) for x in range(250, 400, 24))
                  + toro(40, 124, .46, 'bayo', pastando=True) + comedero(92, 164, 84) + sacos(190, 158)
                  + pasto(194), (330, 88))


def e_graficos():
    return vineta(nube(80, 54, 1) + nube(300, 40, .7, .8) + ave(110, 76, .6)
                  + cerro("M0 120 L60 84 L120 110 L200 64 L270 104 L330 78 L400 104 V210 H0Z", '#C9D4DE', False)
                  + cerro("M0 140 L80 112 L150 132 L240 100 L320 128 L400 112 V210 H0Z", '#AFC1D0')
                  + cerro("M0 160 C100 146 200 158 300 148 C350 144 380 146 400 144 V210 H0Z", '#B6CDA3') + rio()
                  + arbol(330, 170, .45) + toro(72, 132, .4, 'blanco') + toro(118, 138, .36, 'bayo', pastando=True)
                  + pasto(196))


def e_mas():
    return vineta(nube(60, 48, .9) + montes_lejanos()
                  + cerro("M0 156 C70 140 140 152 210 142 C270 134 330 146 400 136 V210 H0Z", '#CFDDBE')
                  + toro(40, 130, .42, 'bayo') + garza(140, 176, .8)
                  + "<rect x='228' y='176' width='52' height='4' rx='2' fill='#7E5E46'/><rect x='232' y='180' width='4' height='10' fill='#7E5E46'/><rect x='272' y='180' width='4' height='10' fill='#7E5E46'/>"
                  + arbol(300, 164, .75) + pasto(196), (330, 92))


def e_lote():
    return vineta(nube(80, 52, .9) + montes_lejanos()
                  + cerro("M0 154 C80 140 160 150 240 142 C300 136 350 144 400 138 V210 H0Z", '#CFDDBE')
                  + molino(330, 96) + toro(34, 124, .44, 'negro') + toro(98, 128, .42, 'blanco', mirar_izq=True)
                  + toro(160, 126, .44, 'bayo') + bebedero(236, 176, 64) + garza(40, 196, .8) + garza(100, 196, .6, izq=True)
                  + pasto(198))


def e_animal():
    return vineta(montes_lejanos()
                  + cerro("M0 154 C80 140 160 150 240 142 C300 136 350 144 400 138 V210 H0Z", '#CFDDBE')
                  + toro(128, 88, .9, 'rojo') + garza(96, 190, .9) + pasto(198), (330, 90))


def e_formular():
    return vineta(nube(90, 46, .9) + montes_lejanos()
                  + cerro("M0 150 C80 136 160 146 240 138 C300 132 350 140 400 134 V210 H0Z", '#CFDDBE')
                  + ''.join(maiz(x, 180, .8) for x in range(16, 130, 26))
                  + silo(270, 90) + silo(306, 110, 24, 66) + sacos(196, 170) + toro(128, 136, .34, 'blanco', mirar_izq=True)
                  + pasto(198), (340, 86))


# ---------- cabeceras (fondo azul) ----------
def silueta(g):
    return re.sub(r"fill='#[0-9A-Fa-f]{6}'", "fill='#162635'", re.sub(r"stroke='#[0-9A-Fa-f]{6}'", "stroke='#162635'", g))


def cabecera(extra=''):
    estrellas = ''.join(f"<circle cx='{x}' cy='{y}' r='{r}' fill='#F1EDE5' opacity='{o}'/>" for x, y, r, o in
                        [(210, 14, 1, .5), (262, 26, .8, .4), (300, 10, 1.1, .5), (344, 30, .8, .35), (380, 16, 1, .45), (236, 40, .7, .3)])
    toros = silueta(toro(250, 76, .3, 'negro') + toro(300, 80, .26, 'negro', mirar_izq=True))
    return (f"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 400 120' preserveAspectRatio='xMidYMax slice'>"
            f"<defs><radialGradient id='luz' cx='.85' cy='1' r='.7'><stop offset='0' stop-color='#F0BF33' stop-opacity='.22'/>"
            f"<stop offset='1' stop-color='#F0BF33' stop-opacity='0'/></radialGradient></defs>"
            f"<rect width='400' height='120' fill='url(#luz)'/>{estrellas}"
            f"<path d='M0 92 C60 76 120 84 180 74 C240 64 300 78 400 66 V120 H0Z' fill='#2A4459' opacity='.8'/>"
            f"{extra}{toros}"
            f"<path d='M0 104 C80 96 160 104 240 98 C300 94 350 98 400 94 V120 H0Z' fill='#1B2D3D'/></svg>")


def cab_arbol():
    return silueta(arbol(60, 90, .42))


ESCENAS = {'hoy': e_hoy, 'lotes': e_lotes, 'registrar': e_registrar, 'graficos': e_graficos, 'mas': e_mas,
           'lote': e_lote, 'animal': e_animal, 'formular': e_formular}


def datos_url(svg):
    return 'data:image/svg+xml,' + urllib.parse.quote(svg, safe=" /:=',()-.;!*")


def main():
    muestra = sys.argv[sys.argv.index('--muestra') + 1] if '--muestra' in sys.argv else None
    idx = RAIZ / 'app/assets/index.html'
    html = idx.read_text(encoding='utf8')
    for pg, f in ESCENAS.items():
        v, h = f(), cabecera(cab_arbol() if pg in ('hoy', 'mas', 'graficos') else '')
        if muestra:
            Path(muestra, f'v_{pg}.svg').write_text(v)
            Path(muestra, f'h_{pg}.svg').write_text(h)
        for tipo, svg in (('vineta', v), ('hd', h)):
            pat = re.compile(r"(body\[data-pg='" + pg + r"'\] \." + tipo + r"\{background-image:url\(\")data:image/svg\+xml,.*?(\"\))")
            html, n = pat.subn(lambda m: m.group(1) + datos_url(svg) + m.group(2), html)
            assert n == 1, (pg, tipo, n)
    idx.write_text(html, encoding='utf8')
    print('ilustraciones actualizadas:', ', '.join(ESCENAS))


if __name__ == '__main__':
    main()
