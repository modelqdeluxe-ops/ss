"""Toro Brahman para las ilustraciones de Rumentis (estilo papel recortado, plano).

toro(x, y, escala, pelaje, mirar_izq=False, pastando=False) devuelve un <g> SVG.
El dibujo mide unas 150 x 104 unidades; (x, y) es la esquina superior izquierda.
"""

PELAJES = {
    # nombre: (cuerpo, sombra, claro, pezuña/nariz)
    'blanco': ('#ECE6DA', '#CFC6B5', '#F8F4EC', '#6B5E55'),
    'gris':   ('#BDB8AE', '#9C968B', '#D6D2CA', '#4F4741'),
    'bayo':   ('#C9A27A', '#A9825C', '#DDBC98', '#5A4234'),
    'rojo':   ('#A5623F', '#834A2E', '#BF7D58', '#3F2A20'),
    'negro':  ('#4E433C', '#3A312B', '#6A5D54', '#231C18'),
}


def _cabeza(c, s, cl, pz, papada=True):
    """Cabeza y cuello mirando a la derecha. Pivote del cuello en (100, 44)."""
    PAPADA = (f"<path d='M104 58 C108 70 110 80 106 90 C100 86 96 76 96 64Z' fill='{c}'/>"
              f"<path d='M106 72 C108 80 108 86 106 90 C103 88 101 84 100 80 C103 80 105 77 106 72Z' fill='{s}' opacity='.55'/>")
    return f"""
  <path d='M92 30 C100 26 110 26 118 30 L124 34 C128 44 128 60 124 70 C118 74 108 72 100 66 C94 58 90 44 92 30Z' fill='{c}'/>
{PAPADA if papada else ''}
  <path d='M116 30 C124 24 134 24 140 30 C146 38 150 50 150 58 C150 64 146 67 141 66 C136 64 132 58 128 52 C124 46 118 40 116 30Z' fill='{c}'/>
  <path d='M142 56 C146 56 150 58 150 61 C150 65 146 67 141 66 C139 63 139 58 142 56Z' fill='{s}'/>
  <ellipse cx='146.2' cy='61.4' rx='1.3' ry='0.9' fill='{pz}' opacity='.8'/>
  <path d='M129 26 C129 19 133 14 138 14 C135 18 134 22 134 27Z' fill='#CDBFA6'/>
  <path d='M123 26 C121 18 124 12 130 11 C128 16 128 21 129 26Z' fill='#E7DCC6'/>
  <path d='M121 34 C112 36 106 44 106 54 C106 58 110 58 112 55 C114 49 118 42 124 38Z' fill='{s}'/>
  <path d='M120 37 C114 40 110 46 109.5 52 C111 51 113 47 115 44 C117 41 119 39 121.5 38Z' fill='#E7B3A2' opacity='.55'/>
  <path d='M133.2 33.6 C135.2 32.2 138.2 32.4 139.6 34.4' stroke='{pz}' stroke-width='0.9' fill='none' stroke-linecap='round' opacity='.7'/>
  <ellipse cx='136.6' cy='36.2' rx='2.3' ry='1.9' fill='#241C17'/>
  <circle cx='137.4' cy='35.5' r='0.7' fill='#FFFFFF'/>
  <path d='M128 30 C132 27 137 27 140 30' stroke='{cl}' stroke-width='2' fill='none' stroke-linecap='round' opacity='.6'/>"""


def toro(x, y, escala=1.0, pelaje='blanco', mirar_izq=False, pastando=False):
    c, s, cl, pz = PELAJES[pelaje]
    cab = _cabeza(c, s, cl, pz, papada=not pastando)
    if pastando:
        cab = f"<g transform='rotate(52 100 44) translate(-4 6)'>{cab}</g>"
    t = f"translate({x} {y}) scale({escala})"
    if mirar_izq:
        t += " translate(150 0) scale(-1 1)"
    return f"""<g transform='{t}'>
  <path d='M44 70 L42 96 C42 99 48 99 48 96 L52 72Z' fill='{s}'/>
  <path d='M86 70 L87 96 C87 99 93 99 93 96 L95 70Z' fill='{s}'/>
  <path d='M42 94.5 h6.5 v3.5 h-6.5z' fill='{pz}' opacity='.85'/>
  <path d='M86.8 94.5 h6.4 v3.5 h-6.4z' fill='{pz}' opacity='.85'/>
  <path d='M22 44 C16 54 15 68 17 82' stroke='{c}' stroke-width='2.6' fill='none' stroke-linecap='round'/>
  <path d='M17 80 C13 85 14 91 18 92 C21 90 21 85 17 80Z' fill='{s}'/>
  <path d='M24 40 C24 30 34 26 46 27 L76 27 C82 22 86 14 94 12 C101 11 104 18 104 26 C108 30 110 42 108 54 C106 64 100 72 92 75 L54 76 C42 76 32 73 27 67 C22 60 21 50 24 40Z' fill='{c}'/>
  <path d='M30 64 C40 72 60 74 80 73 C88 73 94 71 98 68 C96 74 92 76 86 77 L54 78 C42 78 32 74 30 64Z' fill='{s}' opacity='.8'/>
  <path d='M84 16 C88 13 92 12 96 13 C99 14 101 18 101 22 C96 18 90 16 84 16Z' fill='{cl}' opacity='.8'/>
  <path d='M30 34 C40 30 56 30 72 31' stroke='{cl}' stroke-width='3' fill='none' stroke-linecap='round' opacity='.55'/>
  {cab}
  <path d='M34 66 L32 96 C32 99 38 99 38 96 L42 70Z' fill='{c}'/>
  <path d='M92 66 L94 96 C94 99 100 99 100 96 L101 64Z' fill='{c}'/>
  <path d='M31.8 94.5 h6.6 v3.5 h-6.6z' fill='{pz}'/>
  <path d='M93.9 94.5 h6.3 v3.5 h-6.3z' fill='{pz}'/>
</g>"""


if __name__ == '__main__':
    partes = []
    xs = [(0, 'blanco', False, False), (160, 'bayo', False, True), (320, 'rojo', True, False), (480, 'negro', False, False), (640, 'gris', True, True)]
    for x, p, izq, pas in xs:
        partes.append(toro(x + 5, 10, 1, p, izq, pas))
    svg = f"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 800 120'><rect width='800' height='120' fill='#DCE6CF'/>{''.join(partes)}</svg>"
    open('/tmp/claude-0/-home-user-ss/9a449691-a7b1-5476-82ec-bb13f5ed73e9/scratchpad/toros.svg', 'w').write(svg)
