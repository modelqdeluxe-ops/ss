"""Vistas previas de las apps del pad: python3 tools/pad/sim/pages.py <carpeta de salida>.

Dibuja Misiones, Kits, Clanes, Viajes, Títulos, Ayuda, Comunidad y Cámara con el mismo layout que PadViewPage,
PadCommunityPage y PadCameraPage (con la fuente de Minecraft) y una hoja con todas (hoja_apps.png). Los datos son de
ejemplo. Si cambias posiciones en esas clases Java, cámbialas también en sim.py.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import ROOT, Sim, rgb  # noqa: E402

if len(sys.argv) < 2:
    sys.exit('Uso: python3 tools/pad/sim/pages.py <carpeta de salida>')
OUT = os.path.join(sys.argv[1], '')
os.makedirs(OUT, exist_ok=True)
G, B, R, GR, GO = 3, 0, 2, 4, 1

s = Sim()
s.status('MISIONES')
s.view([('diarias', 'DIARIAS'), ('semanales', 'SEMANALES')], 'diarias', ['Se renuevan en 5 h 12 min.'], [
    ('coal', 'Minero de carbón', 0x18265C, ['Saca 24 menas de carbón  ·  24/24'], 1.0, '+80', ('RECLAMAR', G), None),
    ('rotten_flesh', 'Noche de zombis', 0x18265C, ['Derrota 15 zombis  ·  9/15'], 0.6, '+90', None, None),
    ('fishing_rod', 'Día de pesca', 0x7E8CA8, ['Pesca 10 veces  ·  10/10'], 1.0, '+90', ('HECHA', GR, False), None),
])
s.save(OUT + 'p_misiones.png')

s = Sim()
s.status('KITS')
s.view([], '', ['Kits gratis: reclámalos cuando estén listos.'], [
    ('stone_pickaxe', 'Kit inicial', 0x1E7C2C, ['Lo justo para empezar tu aventura.', '¡Listo!  ·  Una sola vez'], -1, '+100', ('VER', B), ('RECLAMAR', G)),
    ('cooked_beef', 'Kit diario', 0x18265C, ['Comida y un poco de todo, cada día.', 'Listo en 6 h 40 min  ·  Cada 1 d 0 h'], -1, '+50', ('VER', B), ('ESPERA', GR, False)),
    ('iron_pickaxe', 'Kit semanal', 0x18265C, ['Una ayuda más grande cada semana.', 'Listo en 3 d 2 h  ·  Cada 7 d 0 h'], -1, '+300', ('VER', B), ('ESPERA', GR, False)),
])
s.save(OUT + 'p_kits.png')

s = Sim()
s.status('CLANES')
s.view([], '', ['No estás en ningún clan. Funda el tuyo o acepta una invitación.'], [
    ('paper', 'Caballeros del Alba [CDA]', 0x1E7C2C, ['Te invitó a unirte · 6 miembros'], -1, '', ('UNIRSE', G), ('NO', R)),
    ('paper', 'Los Dragones [DRG]', 0x18265C, ['12 miembros · líder Pewez777'], -1, '', None, None),
], input_=('Nombre de tu clan (3 a 20 letras)', 'FUNDAR', ''))
s.save(OUT + 'p_clanes.png')

s = Sim()
s.status('VIAJES')
s.view([], '', ['Elige a dónde ir. 3 s sin moverte y ¡listo!'], [
    ('compass_00', 'Spawn', 0x18265C, ['El corazón del servidor'], -1, '', ('VIAJAR', B), None),
    ('paper', 'Tu cama', 0x18265C, ['Donde reapareces'], -1, '', ('VIAJAR', B), None),
    ('filled_map', 'Mercado central', 0x18265C, ['Mundo normal'], -1, '', ('VIAJAR', B), None),
])
s.save(OUT + 'p_viajes.png')

s = Sim()
s.status('TÍTULOS')
s.view([], '', ['Llevas «Explorador»: sale delante de tu nombre.'], [
    ('name_tag', '«Novato»', 0x7E7E7E, ['Lo tienes desde el principio.'], -1, '', ('PONER', G), None),
    ('name_tag', '«Explorador»', 0x00A000, ['Explora 10 veces · ¡hecho!'], -1, '', ('QUITAR', R), None),
    ('name_tag', '«Cazador»', 0x7E8CA8, ['Completa 10 cazas · 4/10'], 0.4, '', ('BLOQUEADO', GR, False), None),
])
s.save(OUT + 'p_titulos.png')

s = Sim()
s.status('AYUDA')
s.view([('empezar', 'EMPEZAR'), ('normas', 'NORMAS'), ('pad', 'EL PAD'), ('comandos', 'COMANDOS')], 'empezar', [], [
    (None, 'Bienvenido', 0x1854BE, ['Tierras Fantásticas es un mundo de supervivencia, aventura, fantasía y rol. Abre el pad con la C para todo lo del servidor.'], -1, '', None, None),
    (None, 'Tus primeros pasos', 0x1854BE, ['Reclama el Kit inicial en Kits, elige un oficio en Oficios y protege tu casa con una protección.'], -1, '', None, None),
])
s.save(OUT + 'p_ayuda.png')

# Comunidad
s = Sim()
s.status('COMUNIDAD')
X, Y, W, H = s.X, s.Y, s.W, s.H
x = X
for key, label in (('recientes', 'RECIENTES'), ('populares', 'POPULARES'), ('mias', 'MÍAS')):
    w = s.pwidth(label) + 10
    sel = key == 'recientes'
    s.box(x, Y, w, 13, (24, 38, 92))
    s.box(x + 1, Y + 1, w - 2, 11, rgb(0xF6B628 if sel else 0xE8F8FF))
    s.ptext(label, x + w // 2 - s.pwidth(label) // 2, Y + 1, (255, 255, 255) if sel else (24, 38, 92), sel)
    x += w + 3
fw = s.bw('+ FOTO')
s.button(X + W - fw, Y - 1, fw, '+ FOTO', GO)
top = Y + 16
s.panel(X, top, W, H - 16)
px, py = X + 6, top + 5
s.box(px - 1, py - 1, 162, 92, (24, 38, 92))
photo = Image.open(os.path.join(ROOT, 'src/main/resources/assets/tfclient/textures/gui/menu_background.png')).convert('RGBA').resize((160, 90))
s.art.alpha_composite(photo, (px, py))
cx, cw = X + 172, W - 178
s.mtext('Pewez777', cx, top + 6, (24, 38, 92))
s.mtext('hace 12 min', cx, top + 16, rgb(0x4A6694))
s.mtext('1/24', cx + cw - s.mwidth('1/24'), top + 16, rgb(0x4A6694))
s.wrap('«Atardecer desde la torre del spawn»', cx, top + 29, cw, (24, 38, 92), 3)
heart = s.img('heart').resize((14, 12), Image.NEAREST)
s.art.alpha_composite(heart, (cx, top + 62))
s.mtext('18 likes', cx + 20, top + 65, (24, 38, 92))
s.button(cx, top + H - 16 - 19, cw, 'DENUNCIAR', B)
s.save(OUT + 'p_comunidad.png')

# Cámara
s = Sim()
s.status('CÁMARA')
s.panel(X, Y, W, H)
px, py = X + 6, Y + 6
s.box(px - 1, py - 1, 162, 92, (24, 38, 92))
s.art.alpha_composite(photo, (px, py))
s.button(px, Y + H - 18, 160, 'MODO FOTO', G)
cx, cw = X + 172, W - 178
s.mtext('Foto 1 de 7', cx, Y + 7, (24, 38, 92))
s.mtext('8 oct · 18:30', cx, Y + 17, rgb(0x4A6694))
s.button(cx, Y + 32, cw, 'PUBLICAR', GO)
s.button(cx, Y + 51, cw, 'BORRAR', R)
s.button(cx, Y + H - 18, cw, 'CARPETA', B)
s.save(OUT + 'p_camara.png')

# hoja con todo
names = ['p_misiones', 'p_kits', 'p_clanes', 'p_viajes', 'p_titulos', 'p_ayuda', 'p_comunidad', 'p_camara']
ims = [Image.open(OUT + n + '.png').crop((180, 220, 1400, 820)) for n in names]
w, h = ims[0].size
sheet = Image.new('RGBA', (w * 2 + 10, (h + 10) * 4), (20, 24, 40, 255))
for i, im in enumerate(ims):
    sheet.alpha_composite(im, ((i % 2) * (w + 10), (i // 2) * (h + 10)))
sheet = sheet.resize((sheet.width // 2, sheet.height // 2), Image.LANCZOS)
sheet.save(OUT + 'hoja_apps.png')
print('ok')
