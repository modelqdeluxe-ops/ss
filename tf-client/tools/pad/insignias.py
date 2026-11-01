"""Las insignias de los clanes (1.3.158): unas 190, bien dibujadas.

El dueño (1 nov 2026): «Banners o logos de clan pobres y hay muy pocos, haz una basta lista, grande de logos, bien
dibujados sin errores» y «mínimo 100 o más». Las de la 1.3.156 eran símbolos de 16x16 a mano: pequeños y algunos no se
entendían (la «mariposa» era el dragón).

Cómo se hacen (64x64, se dibujan a 256x256 y se reducen con suavizado):
- el escudo (9 formas) con un borde de metal (oro, plata, bronce u obsidiana) en degradado, un contorno oscuro, el
  campo con su degradado de color, una sombra interior y un brillo arriba;
- el símbolo, de game-icons.net (CC BY 3.0: Lorc, Delapouite y los demás autores de https://game-icons.net; ver
  CREDITOS-ICONOS.txt), en su color con un degradado suave, un contorno oscuro y su sombra.

Las 48 de antes conservan su id (los clanes que ya las tenían siguen con ella), redibujadas y con su nombre arreglado.

    python3 tools/pad/insignias.py [--iconos icons.json] [--ver carpeta]

--iconos: el icons.json de @iconify-json/game-icons (npm) para sacar los símbolos que falten en
tools/pad/gameicons.json (el subconjunto que usamos, ya guardado en el repo). --ver: una hoja con todas.
"""
import io
import json
import os
import sys

import cairosvg
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'tfclient'))
BADGES = os.path.join(RES, 'textures', 'gui', 'pad', 'insignias')
INDEX = os.path.join(RES, 'pad', 'insignias.json')
ICONS = os.path.join(HERE, 'gameicons.json')

S = 256          # se dibuja a este tamaño
OUT = 64         # y se guarda a este

# ---------------------------------------------------------------------------------------------------------------------
# Símbolos: clave → (icono de game-icons, nombre en español)
# ---------------------------------------------------------------------------------------------------------------------

SYM = {
    # los de antes (sus ids siguen igual)
    'espada': ('broadsword', 'Espada'), 'corona': ('crown', 'Corona'), 'estrella': ('round-star', 'Estrella'),
    'calavera': ('death-skull', 'Calavera'), 'llama': ('flame', 'Llama'), 'hoja': ('oak-leaf', 'Hoja'),
    'ola': ('big-wave', 'Ola'), 'luna': ('moon', 'Luna'), 'sol': ('sun', 'Sol'), 'arbol': ('oak', 'Árbol'),
    'hacha': ('battle-axe', 'Hacha'), 'arco': ('pocket-bow', 'Arco'), 'corazon': ('hearts', 'Corazón'),
    'gema': ('cut-diamond', 'Gema'), 'rayo': ('lightning-helix', 'Rayo'), 'ojo': ('all-seeing-eye', 'Ojo'),
    'lobo': ('wolf-head', 'Lobo'), 'dragon': ('dragon-head', 'Dragón'), 'castillo': ('castle', 'Castillo'),
    'ancla': ('anchor', 'Ancla'),
    # armas
    'espadas': ('crossed-swords', 'Espadas cruzadas'), 'hachas': ('crossed-axes', 'Hachas cruzadas'),
    'lanzas': ('spears', 'Lanzas'), 'martillo': ('thor-hammer', 'Martillo de trueno'), 'maza': ('spiked-mace', 'Maza'),
    'tridente': ('trident', 'Tridente'), 'katana': ('katana', 'Katana'), 'guadana': ('scythe', 'Guadaña'),
    'alabarda': ('halberd', 'Alabarda'), 'espada_alada': ('winged-sword', 'Espada alada'),
    'espada_rayo': ('zeus-sword', 'Espada de Zeus'), 'puno': ('fist', 'Puño'), 'guantelete': ('gauntlet', 'Guantelete'),
    'shuriken': ('shuriken', 'Shuriken'), 'mangual': ('flail', 'Mangual'), 'sai': ('sai', 'Sai'),
    # animales
    'leon': ('lion', 'León'), 'tigre': ('tiger-head', 'Tigre'), 'oso': ('bear-head', 'Oso'),
    'aguila': ('eagle-emblem', 'Águila'), 'cuervo': ('raven', 'Cuervo'), 'buho': ('barn-owl', 'Búho'),
    'serpiente': ('snake', 'Serpiente'), 'cobra': ('cobra', 'Cobra'), 'escorpion': ('scorpion', 'Escorpión'),
    'arana': ('spider-face', 'Araña'), 'zorro': ('fox-head', 'Zorro'), 'jabali': ('boar', 'Jabalí'),
    'toro': ('bull', 'Toro'), 'caballo': ('horse-head', 'Caballo'), 'ciervo': ('stag-head', 'Ciervo'),
    'aullido': ('wolf-howl', 'Aullido'), 'mamut': ('mammoth', 'Mamut'), 'elefante': ('elephant-head', 'Elefante'),
    'gorila': ('gorilla', 'Gorila'), 'conejo': ('rabbit-head', 'Conejo'), 'sabueso': ('hound', 'Sabueso'),
    'tortuga': ('turtle', 'Tortuga'), 'rana': ('frog', 'Rana'), 'cangrejo': ('crab', 'Cangrejo'),
    'dinosaurio': ('dinosaur-rex', 'Dinosaurio'), 'abeja': ('bee', 'Abeja'), 'mariposa': ('butterfly', 'Mariposa'),
    'paloma': ('dove', 'Paloma'), 'cisne': ('swan', 'Cisne'), 'gallo': ('rooster', 'Gallo'),
    'colibri': ('hummingbird', 'Colibrí'), 'buitre': ('vulture', 'Buitre'), 'delfin': ('dolphin', 'Delfín'),
    'medusa_mar': ('jellyfish', 'Medusa'), 'pinguino': ('penguin', 'Pingüino'), 'salamandra': ('salamander', 'Salamandra'),
    'murcielago': ('bat', 'Murciélago'), 'gato': ('cat', 'Gato'), 'pulpo': ('octopus', 'Pulpo'),
    'tiburon': ('shark-jaws', 'Tiburón'), 'huella': ('paw-print', 'Huella'), 'garras': ('claw-slashes', 'Zarpazo'),
    'colmillos': ('fangs', 'Colmillos'), 'oso_cara': ('bear-face', 'Oso pardo'), 'aguila_cabeza': ('eagle-head', 'Halcón'),
    # criaturas
    'unicornio': ('unicorn', 'Unicornio'), 'grifo': ('griffin-symbol', 'Grifo'), 'pegaso': ('pegasus', 'Pegaso'),
    'hidra': ('hydra', 'Hidra'), 'minotauro': ('minotaur', 'Minotauro'), 'gorgona': ('medusa-head', 'Gorgona'),
    'kraken': ('kraken-tentacle', 'Kraken'), 'dragon_espiral': ('dragon-spiral', 'Dragón enroscado'),
    'calavera_cuernos': ('horned-skull', 'Calavera con cuernos'), 'calaveras': ('triple-skulls', 'Tres calaveras'),
    'pirata': ('skull-crossed-bones', 'Bandera pirata'), 'fantasma': ('ghost', 'Fantasma'), 'diablo': ('devil-mask', 'Diablo'),
    'vampiro': ('vampire-dracula', 'Vampiro'), 'bruja': ('witch-face', 'Bruja'), 'mago': ('wizard-face', 'Mago'),
    'orco': ('orc-head', 'Orco'), 'duende': ('goblin-head', 'Duende'), 'enano': ('dwarf-face', 'Enano'),
    'troll': ('troll', 'Trol'), 'ogro': ('ogre', 'Ogro'), 'golem': ('golem-head', 'Gólem'), 'sirena': ('mermaid', 'Sirena'),
    'hada': ('fairy', 'Hada'), 'diablillo': ('imp-laugh', 'Diablillo'), 'calavera_colmillos': ('fanged-skull', 'Calavera feroz'),
    # cascos y máscaras
    'vikingo': ('viking-helmet', 'Casco vikingo'), 'espartano': ('spartan-helmet', 'Casco espartano'),
    'samurai': ('samurai-helmet', 'Casco samurái'), 'caballero': ('black-knight-helm', 'Caballero negro'),
    'ninja': ('ninja-mask', 'Ninja'), 'tribal': ('tribal-mask', 'Máscara tribal'), 'totem': ('totem', 'Tótem'),
    'yelmo_cuernos': ('horned-helm', 'Yelmo con cuernos'),
    # magia y símbolos
    'alas': ('angel-wings', 'Alas de ángel'), 'alas_murcielago': ('batwing-emblem', 'Alas oscuras'),
    'pluma': ('feather', 'Pluma'), 'cristal': ('crystal-cluster', 'Cristales'), 'baculo': ('wizard-staff', 'Báculo'),
    'orbe': ('frozen-orb', 'Orbe helado'), 'magia': ('magic-swirl', 'Magia'), 'remolino': ('vortex', 'Remolino'),
    'runa': ('rune-stone', 'Runa'), 'santo': ('holy-symbol', 'Símbolo sagrado'), 'ankh': ('ankh', 'Ankh'),
    'yinyang': ('yin-yang', 'Yin y yang'), 'pentaculo': ('pentagram-rose', 'Pentáculo'), 'luna_maldita': ('evil-moon', 'Luna maldita'),
    'libro': ('open-book', 'Libro'), 'pergamino': ('scroll-unfurled', 'Pergamino'), 'pocion': ('health-potion', 'Poción'),
    'caldero': ('cauldron', 'Caldero'), 'caliz': ('jeweled-chalice', 'Cáliz'), 'reloj': ('hourglass', 'Reloj de arena'),
    'llave': ('key', 'Llave'), 'cofre': ('locked-chest', 'Cofre'), 'monedas': ('coins', 'Monedas'),
    'dado': ('dice-twenty-faces-twenty', 'Dado'), 'brujula': ('compass', 'Brújula'), 'rayo_hielo': ('ice-bolt', 'Rayo de hielo'),
    'anillo_fuego': ('fire-ring', 'Anillo de fuego'), 'ojo_escudo': ('eye-shield', 'Escudo vigía'),
    # naturaleza y tierra
    'montana': ('mountains', 'Montañas'), 'volcan': ('volcano', 'Volcán'), 'copo': ('snowflake-2', 'Copo de nieve'),
    'tornado': ('tornado', 'Tornado'), 'amanecer': ('sunrise', 'Amanecer'), 'rosa_flor': ('rose', 'Rosa'),
    'loto': ('lotus-flower', 'Loto'), 'seta': ('mushroom', 'Seta'), 'pino': ('pine-tree', 'Pino'), 'brote': ('sprout', 'Brote'),
    'bellota': ('acorn', 'Bellota'), 'cactus': ('cactus', 'Cactus'), 'bambu': ('bamboo', 'Bambú'),
    'calabaza': ('pumpkin', 'Calabaza'), 'cerezas': ('cherry', 'Cerezas'), 'trigo': ('wheat', 'Trigo'),
    'grieta': ('earth-crack', 'Tierra rota'),
    # oficios y lugares
    'antorcha': ('torch', 'Antorcha'), 'farol': ('lantern-flame', 'Farol'), 'estandarte': ('knight-banner', 'Estandarte'),
    'torre': ('tower-flag', 'Torre'), 'drakkar': ('drakkar', 'Drakkar'), 'timon': ('ship-wheel', 'Timón'),
    'yunque': ('anvil', 'Yunque'), 'cerveza': ('beer-stein', 'Jarra'), 'musica': ('musical-notes', 'Música'),
    'arpa': ('harp', 'Arpa'), 'tambor': ('drum', 'Tambor'), 'faro': ('lighthouse', 'Faro'), 'pagoda': ('pagoda', 'Pagoda'),
    'molino': ('windmill', 'Molino'), 'obelisco': ('obelisk', 'Obelisco'), 'templo': ('temple-gate', 'Templo'),
    'anzuelo': ('fishing-hook', 'Anzuelo'), 'hoz': ('sickle', 'Hoz'), 'horca': ('pitchfork', 'Horca'),
}

# ---------------------------------------------------------------------------------------------------------------------
# Colores
# ---------------------------------------------------------------------------------------------------------------------

# Metales del borde: (claro, medio, oscuro)
METAL = {
    'oro': ((255, 236, 140), (238, 182, 52), (150, 92, 14)),
    'plata': ((250, 252, 255), (186, 196, 214), (92, 102, 124)),
    'bronce': ((255, 196, 140), (206, 120, 58), (112, 56, 22)),
    'obsidiana': ((150, 140, 190), (72, 64, 104), (24, 20, 36)),
}

# Campos: (arriba, abajo, símbolo claro, símbolo oscuro, metal)
FIELD = {
    'oro': ((122, 64, 196), (56, 24, 104), (255, 236, 150), (210, 140, 30), 'oro'),       # el «oro» de antes: morado y oro
    'rojo': ((214, 52, 60), (112, 14, 26), (255, 244, 226), (232, 196, 160), 'oro'),
    'azul': ((60, 120, 226), (18, 44, 120), (240, 246, 255), (190, 214, 250), 'plata'),
    'verde': ((52, 166, 84), (14, 84, 40), (252, 246, 200), (214, 200, 120), 'oro'),
    'negro': ((70, 70, 86), (16, 16, 22), (250, 90, 80), (170, 30, 30), 'obsidiana'),
    'cian': ((44, 186, 204), (10, 86, 106), (240, 255, 255), (170, 236, 244), 'plata'),
    'rosa': ((240, 110, 176), (140, 36, 96), (255, 246, 250), (250, 200, 226), 'plata'),
    'naranja': ((250, 146, 40), (160, 64, 10), (255, 250, 226), (255, 214, 150), 'bronce'),
    'morado': ((146, 76, 220), (62, 22, 116), (246, 232, 255), (210, 180, 250), 'plata'),
    'blanco': ((248, 250, 255), (178, 186, 204), (52, 64, 104), (24, 32, 64), 'oro'),
    'granate': ((150, 24, 44), (58, 6, 16), (255, 214, 130), (210, 150, 50), 'oro'),
    'marino': ((34, 56, 128), (8, 14, 48), (255, 222, 120), (220, 160, 40), 'oro'),
    'esmeralda': ((24, 184, 128), (6, 86, 58), (240, 255, 246), (170, 240, 206), 'plata'),
    'ambar': ((246, 168, 34), (136, 66, 4), (255, 250, 230), (255, 222, 150), 'bronce'),
    'turquesa': ((40, 196, 186), (10, 92, 98), (244, 255, 254), (180, 240, 236), 'plata'),
    'gris': ((150, 158, 176), (58, 62, 76), (250, 250, 255), (200, 206, 220), 'plata'),
    'oliva': ((130, 148, 58), (56, 66, 18), (255, 246, 196), (220, 204, 130), 'bronce'),
    'cobre': ((196, 104, 58), (90, 38, 14), (255, 236, 200), (240, 196, 140), 'bronce'),
    'noche': ((48, 34, 92), (10, 6, 30), (190, 230, 255), (110, 170, 240), 'obsidiana'),
    'sangre': ((120, 8, 16), (30, 0, 4), (255, 210, 200), (230, 120, 110), 'obsidiana'),
}

SHAPES = ['escudo', 'redondo', 'cometa', 'estandarte', 'rombo', 'hexagono', 'blason', 'octogono', 'sello']

# Las 48 de antes: (id, nombre, forma, símbolo, colores). dragon_* eran «mariposas» por el dibujo: ahora son dragones.
OLD = [
    ('espada_oro', 'Espada dorada', 'escudo', 'espada', 'oro'), ('corona_rojo', 'Corona roja', 'escudo', 'corona', 'rojo'),
    ('estrella_azul', 'Estrella azul', 'redondo', 'estrella', 'azul'), ('calavera_negro', 'Calavera', 'escudo', 'calavera', 'negro'),
    ('llama_naranja', 'Llama', 'cometa', 'llama', 'naranja'), ('hoja_verde', 'Hoja', 'redondo', 'hoja', 'verde'),
    ('ola_cian', 'Ola', 'redondo', 'ola', 'cian'), ('luna_morado', 'Luna', 'estandarte', 'luna', 'morado'),
    ('sol_oro', 'Sol', 'redondo', 'sol', 'oro'), ('arbol_verde', 'Árbol', 'escudo', 'arbol', 'verde'),
    ('hacha_rojo', 'Hacha', 'cometa', 'hacha', 'rojo'), ('arco_verde', 'Arco', 'estandarte', 'arco', 'verde'),
    ('corazon_rosa', 'Corazón', 'redondo', 'corazon', 'rosa'), ('gema_cian', 'Gema', 'rombo', 'gema', 'cian'),
    ('rayo_azul', 'Rayo', 'hexagono', 'rayo', 'azul'), ('ojo_morado', 'Ojo', 'rombo', 'ojo', 'morado'),
    ('lobo_blanco', 'Lobo', 'escudo', 'lobo', 'blanco'), ('dragon_rojo', 'Dragón rojo', 'escudo', 'dragon', 'rojo'),
    ('castillo_azul', 'Castillo', 'estandarte', 'castillo', 'azul'), ('ancla_azul', 'Ancla', 'redondo', 'ancla', 'azul'),
    ('espada_negro', 'Espada negra', 'cometa', 'espada', 'negro'), ('corona_oro', 'Corona dorada', 'hexagono', 'corona', 'oro'),
    ('estrella_rosa', 'Estrella rosa', 'estandarte', 'estrella', 'rosa'), ('calavera_morado', 'Calavera morada', 'hexagono', 'calavera', 'morado'),
    ('llama_azul', 'Llama azul', 'escudo', 'llama', 'azul'), ('hoja_naranja', 'Hoja de otoño', 'cometa', 'hoja', 'naranja'),
    ('ola_azul', 'Marea', 'escudo', 'ola', 'azul'), ('luna_negro', 'Luna nueva', 'redondo', 'luna', 'negro'),
    ('sol_naranja', 'Sol poniente', 'estandarte', 'sol', 'naranja'), ('arbol_oro', 'Árbol dorado', 'hexagono', 'arbol', 'oro'),
    ('hacha_negro', 'Hacha negra', 'escudo', 'hacha', 'negro'), ('arco_morado', 'Arco arcano', 'rombo', 'arco', 'morado'),
    ('corazon_rojo', 'Corazón rojo', 'escudo', 'corazon', 'rojo'), ('gema_verde', 'Esmeralda', 'hexagono', 'gema', 'verde'),
    ('rayo_oro', 'Rayo dorado', 'escudo', 'rayo', 'oro'), ('ojo_cian', 'Ojo de cristal', 'redondo', 'ojo', 'cian'),
    ('lobo_negro', 'Lobo negro', 'cometa', 'lobo', 'negro'), ('dragon_oro', 'Dragón dorado', 'hexagono', 'dragon', 'oro'),
    ('castillo_rojo', 'Fortaleza', 'escudo', 'castillo', 'rojo'), ('ancla_blanco', 'Ancla blanca', 'estandarte', 'ancla', 'blanco'),
    ('espada_azul', 'Espada azul', 'rombo', 'espada', 'azul'), ('corona_morado', 'Corona morada', 'redondo', 'corona', 'morado'),
    ('estrella_oro', 'Estrella dorada', 'escudo', 'estrella', 'oro'), ('dragon_verde', 'Dragón verde', 'cometa', 'dragon', 'verde'),
    ('lobo_azul', 'Lobo de hielo', 'hexagono', 'lobo', 'cian'), ('llama_rojo', 'Llama roja', 'redondo', 'llama', 'rojo'),
    ('calavera_blanco', 'Calavera blanca', 'rombo', 'calavera', 'blanco'), ('gema_rosa', 'Gema rosa', 'escudo', 'gema', 'rosa'),
]

# Las nuevas: cada símbolo con unos colores que le van (el primero) y la forma por turno
NEW_COLORS = {
    'espadas': 'granate', 'hachas': 'sangre', 'lanzas': 'marino', 'martillo': 'azul', 'maza': 'gris', 'tridente': 'turquesa',
    'katana': 'negro', 'guadana': 'noche', 'alabarda': 'cobre', 'espada_alada': 'blanco', 'espada_rayo': 'marino',
    'puno': 'rojo', 'guantelete': 'gris', 'shuriken': 'negro', 'mangual': 'oliva', 'sai': 'granate',
    'leon': 'ambar', 'tigre': 'naranja', 'oso': 'cobre', 'aguila': 'marino', 'cuervo': 'noche', 'buho': 'oliva',
    'serpiente': 'esmeralda', 'cobra': 'ambar', 'escorpion': 'sangre', 'arana': 'negro', 'zorro': 'naranja',
    'jabali': 'cobre', 'toro': 'rojo', 'caballo': 'blanco', 'ciervo': 'verde', 'aullido': 'noche', 'mamut': 'cian',
    'elefante': 'gris', 'gorila': 'negro', 'conejo': 'rosa', 'sabueso': 'cobre', 'tortuga': 'esmeralda', 'rana': 'verde',
    'cangrejo': 'naranja', 'dinosaurio': 'oliva', 'abeja': 'ambar', 'mariposa': 'morado', 'paloma': 'azul',
    'cisne': 'turquesa', 'gallo': 'rojo', 'colibri': 'esmeralda', 'buitre': 'sangre', 'delfin': 'cian',
    'medusa_mar': 'rosa', 'pinguino': 'blanco', 'salamandra': 'naranja', 'murcielago': 'noche', 'gato': 'morado',
    'pulpo': 'granate', 'tiburon': 'marino', 'huella': 'cobre', 'garras': 'sangre', 'colmillos': 'negro',
    'oso_cara': 'oliva', 'aguila_cabeza': 'ambar',
    'unicornio': 'rosa', 'grifo': 'ambar', 'pegaso': 'azul', 'hidra': 'esmeralda', 'minotauro': 'granate',
    'gorgona': 'verde', 'kraken': 'turquesa', 'dragon_espiral': 'sangre', 'calavera_cuernos': 'negro',
    'calaveras': 'gris', 'pirata': 'negro', 'fantasma': 'noche', 'diablo': 'rojo', 'vampiro': 'granate',
    'bruja': 'morado', 'mago': 'marino', 'orco': 'oliva', 'duende': 'verde', 'enano': 'cobre', 'troll': 'gris',
    'ogro': 'ambar', 'golem': 'gris', 'sirena': 'turquesa', 'hada': 'rosa', 'diablillo': 'naranja',
    'calavera_colmillos': 'sangre',
    'vikingo': 'azul', 'espartano': 'granate', 'samurai': 'rojo', 'caballero': 'negro', 'ninja': 'noche',
    'tribal': 'cobre', 'totem': 'oliva', 'yelmo_cuernos': 'gris',
    'alas': 'blanco', 'alas_murcielago': 'negro', 'pluma': 'cian', 'cristal': 'morado', 'baculo': 'marino',
    'orbe': 'azul', 'magia': 'morado', 'remolino': 'turquesa', 'runa': 'gris', 'santo': 'blanco', 'ankh': 'ambar',
    'yinyang': 'gris', 'pentaculo': 'sangre', 'luna_maldita': 'noche', 'libro': 'marino', 'pergamino': 'cobre',
    'pocion': 'rosa', 'caldero': 'esmeralda', 'caliz': 'granate', 'reloj': 'ambar', 'llave': 'marino',
    'cofre': 'cobre', 'monedas': 'oro', 'dado': 'blanco', 'brujula': 'azul', 'rayo_hielo': 'cian',
    'anillo_fuego': 'naranja', 'ojo_escudo': 'noche',
    'montana': 'azul', 'volcan': 'sangre', 'copo': 'cian', 'tornado': 'gris', 'amanecer': 'naranja', 'rosa_flor': 'rojo',
    'loto': 'rosa', 'seta': 'rojo', 'pino': 'verde', 'brote': 'esmeralda', 'bellota': 'cobre', 'cactus': 'oliva',
    'bambu': 'esmeralda', 'calabaza': 'naranja', 'cerezas': 'blanco', 'trigo': 'ambar', 'grieta': 'cobre',
    'antorcha': 'granate', 'farol': 'noche', 'estandarte': 'rojo', 'torre': 'azul', 'drakkar': 'marino',
    'timon': 'cobre', 'yunque': 'gris', 'cerveza': 'ambar', 'musica': 'morado', 'arpa': 'oro', 'tambor': 'rojo',
    'faro': 'marino', 'pagoda': 'granate', 'molino': 'blanco', 'obelisco': 'ambar', 'templo': 'oliva',
    'anzuelo': 'turquesa', 'hoz': 'oliva', 'horca': 'cobre',
}

COLOR_NAME = {
    'oro': 'dorado', 'rojo': 'rojo', 'azul': 'azul', 'verde': 'verde', 'negro': 'negro', 'cian': 'cian', 'rosa': 'rosa',
    'naranja': 'naranja', 'morado': 'morado', 'blanco': 'blanco', 'granate': 'granate', 'marino': 'marino',
    'esmeralda': 'esmeralda', 'ambar': 'ámbar', 'turquesa': 'turquesa', 'gris': 'gris', 'oliva': 'oliva',
    'cobre': 'cobre', 'noche': 'nocturno', 'sangre': 'sangriento',
}


def insignias():
    out = list(OLD)
    used = {b[0] for b in out}
    for i, (key, color) in enumerate(NEW_COLORS.items()):
        bid = f'{key}_{color}'
        if bid in used:
            bid = f'{key}_{color}2'
        shape = SHAPES[i % len(SHAPES)]
        out.append((bid, SYM[key][1], shape, key, color))
        used.add(bid)
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Formas (máscaras a 256x256)
# ---------------------------------------------------------------------------------------------------------------------

def bez(p0, p1, p2, n=24):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in [i / n for i in range(n + 1)]]


def outline(shape):
    if shape == 'escudo':
        return [(26, 18), (230, 18), (230, 120)] + bez((230, 120), (226, 200), (128, 246)) + bez((128, 246), (30, 200), (26, 120))
    if shape == 'cometa':
        return bez((30, 70), (128, -10), (226, 70)) + [(226, 70), (128, 248)]
    if shape == 'estandarte':
        return [(46, 14), (210, 14), (210, 246), (128, 196), (46, 246)]
    if shape == 'rombo':
        return [(128, 8), (248, 128), (128, 248), (8, 128)]
    if shape == 'hexagono':
        import math
        return [(128 + 122 * math.sin(math.pi / 3 * k), 128 - 122 * math.cos(math.pi / 3 * k)) for k in range(6)]
    if shape == 'octogono':
        import math
        return [(128 + 124 * math.cos(math.pi / 8 + math.pi / 4 * k), 128 + 124 * math.sin(math.pi / 8 + math.pi / 4 * k)) for k in range(8)]
    if shape == 'blason':
        return [(22, 16), (76, 30), (128, 14), (180, 30), (234, 16), (232, 140)] + bez((232, 140), (220, 214), (128, 248)) + bez((128, 248), (36, 214), (24, 140))
    if shape == 'sello':
        import math
        pts = []
        for k in range(32):
            r = 124 if k % 2 == 0 else 110
            a = math.pi * 2 * k / 32
            pts.append((128 + r * math.cos(a), 128 + r * math.sin(a)))
        return pts
    return None


def mask_of(shape):
    im = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(im)
    if shape == 'redondo':
        d.ellipse((10, 10, 246, 246), fill=255)
    else:
        d.polygon(outline(shape), fill=255)
    return im


def grow(m, px):
    if px == 0:
        return m
    f = ImageFilter.MaxFilter if px > 0 else ImageFilter.MinFilter
    out = m
    for _ in range(abs(px) // 2):
        out = out.filter(f(5))
    if abs(px) % 2:
        out = out.filter(f(3))
    return out


def vgrad(c0, c1, top=0, bottom=S):
    a = np.zeros((S, S, 3), np.float32)
    for y in range(S):
        t = min(1, max(0, (y - top) / max(1, bottom - top)))
        a[y, :, :] = np.array(c0) * (1 - t) + np.array(c1) * t
    return a


# ---------------------------------------------------------------------------------------------------------------------
# El símbolo
# ---------------------------------------------------------------------------------------------------------------------

def load_icons(extra=None):
    icons = json.load(open(ICONS, encoding='utf-8')) if os.path.exists(ICONS) else {}
    need = {SYM[k][0] for k in SYM}
    missing = need - set(icons)
    if missing:
        if not extra:
            sys.exit(f'faltan símbolos en gameicons.json ({sorted(missing)}): pasa --iconos icons.json')
        src = json.load(open(extra, encoding='utf-8'))['icons']
        for n in missing:
            icons[n] = src[n]['body']
    icons = {k: icons[k] for k in sorted(need)}
    with open(ICONS, 'w', encoding='utf-8') as f:
        json.dump(icons, f, ensure_ascii=False, indent=0)
        f.write('\n')
    return icons


def symbol_mask(body, size):
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="%d" height="%d">' % (size, size)
           + body.replace('currentColor', '#ffffff') + '</svg>')
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=size, output_height=size)
    a = Image.open(io.BytesIO(png)).convert('RGBA').split()[3]
    # el icono trae margen: se recorta a lo que hay y se centra
    bbox = a.getbbox()
    return a.crop(bbox) if bbox else a


# ---------------------------------------------------------------------------------------------------------------------
# La insignia
# ---------------------------------------------------------------------------------------------------------------------

# Símbolos de trazo fino que se engordan (px a 256) para que se lean a 32 px en el pad
BOLD = {'espada': 4, 'lanzas': 5, 'katana': 6, 'guadana': 4, 'alabarda': 4, 'tridente': 4, 'sai': 4, 'anzuelo': 5,
        'hoz': 3, 'baculo': 4, 'arco': 3, 'espada_rayo': 3, 'espada_alada': 2, 'hacha': 2, 'reloj': 2, 'remolino': 2}

# Cuánto del círculo inscrito ocupa el símbolo (un poco más: las esquinas de los iconos suelen estar vacías)
FILL = {'escudo': 1.12, 'redondo': 1.08, 'cometa': 1.22, 'estandarte': 1.12, 'rombo': 1.18, 'hexagono': 1.1,
        'blason': 1.12, 'octogono': 1.1, 'sello': 1.06}


def inscribed(mask):
    """El centro y el radio del círculo más grande que cabe en la máscara (por fuerza bruta en una rejilla de 4 px)."""
    m = np.asarray(mask) > 127
    ys, xs = np.nonzero(m)
    edge = m & ~(np.roll(m, 1, 0) & np.roll(m, -1, 0) & np.roll(m, 1, 1) & np.roll(m, -1, 1))
    ey, ex = np.nonzero(edge)
    best = (128, 128, 0)
    for y in range(ys.min(), ys.max() + 1, 4):
        for x in range(xs.min(), xs.max() + 1, 4):
            if not m[y, x]:
                continue
            d = np.sqrt(((ex - x) ** 2 + (ey - y) ** 2).min())
            if d > best[2]:
                best = (x, y, d)
    return best


def badge(shape, symbol, scheme, icons):
    top, bottom, sym_lo, sym_hi_dark, metal = FIELD[scheme]
    light, mid, dark = METAL[metal]
    shape_m = mask_of(shape)
    out_m = grow(shape_m, 8)                       # el contorno oscuro (2 px a 64)
    field_m = grow(shape_m, -16)                   # el campo, dentro del borde de metal (4 px)
    canvas = np.zeros((S, S, 4), np.float32)

    def paint(rgb, alpha_img, a=1.0):
        al = np.asarray(alpha_img, np.float32)[..., None] / 255 * a
        canvas[..., :3] = canvas[..., :3] * (1 - al) + rgb * al
        canvas[..., 3:] = np.maximum(canvas[..., 3:], al * 255)

    # contorno y metal
    paint(np.array([c * 0.28 for c in dark], np.float32), out_m)
    metal_rgb = vgrad(light, dark, 10, 246)
    metal_rgb = np.where(np.arange(S)[:, None, None] < 128, vgrad(light, mid, 10, 128), vgrad(mid, dark, 128, 246))
    paint(metal_rgb, shape_m)
    # filo de luz del metal (arriba) y su sombra (abajo): el borde exterior del metal, más claro arriba
    rim_in = grow(shape_m, -5)
    edge = np.clip(np.asarray(shape_m, np.float32) - np.asarray(rim_in, np.float32), 0, 255)
    ys = np.linspace(1, 0, S)[:, None]
    paint(np.array(light, np.float32), Image.fromarray((edge * ys * 0.7).astype(np.uint8)))
    # el campo
    paint(vgrad(top, bottom, 20, 236), field_m)
    # sombra interior del campo
    inner = grow(field_m, -10)
    ring = np.clip(np.asarray(field_m, np.float32) - np.asarray(inner.filter(ImageFilter.GaussianBlur(6)), np.float32), 0, 255)
    paint(np.zeros(3, np.float32), Image.fromarray((ring * 0.45).astype(np.uint8)))
    # el símbolo: en el sitio más ancho del campo (el centro del círculo más grande que cabe), del tamaño de ese hueco
    bx0, by0, bx1, by1 = field_m.getbbox()
    fw, fh = bx1 - bx0, by1 - by0
    cx, cy, r = inscribed(field_m)
    box = int(2 * r * FILL.get(shape, 1.0))
    sm = symbol_mask(icons[SYM[symbol][0]], 1024)
    k = box / max(sm.size)
    sm = sm.resize((max(1, int(sm.size[0] * k)), max(1, int(sm.size[1] * k))), Image.LANCZOS)
    ox, oy = int(cx - sm.size[0] / 2), int(cy - sm.size[1] / 2)
    sym_full = Image.new('L', (S, S), 0)
    sym_full.paste(sm, (ox, oy))
    sym_full = grow(sym_full, BOLD.get(symbol, 0))
    # un símbolo oscuro sobre un campo claro (el blanco) va sin contorno oscuro ni sombra: si no, sale manchado
    dark_sym = sum(sym_lo) < sum(top)
    # sombra
    if not dark_sym:
        shadow = Image.new('L', (S, S), 0)
        shadow.paste(grow(sym_full, 6), (6, 8))
        paint(np.zeros(3, np.float32), shadow.filter(ImageFilter.GaussianBlur(3)), 0.55)
        # contorno del símbolo
        paint(np.array([c * 0.35 for c in bottom], np.float32), grow(sym_full, 7))
    else:
        # un halo claro, apenas
        paint(np.array([255, 255, 255], np.float32), grow(sym_full, 5), 0.6)
    # el símbolo en degradado
    paint(vgrad(sym_lo, sym_hi_dark, oy, oy + sm.size[1]), sym_full)
    # brillo de arriba (sobre el campo, encima de todo)
    gloss = Image.new('L', (S, S), 0)
    ImageDraw.Draw(gloss).ellipse((bx0 - fw * 0.3, by0 - fh * 0.75, bx1 + fw * 0.3, by0 + fh * 0.42), fill=255)
    gloss = Image.fromarray(np.minimum(np.asarray(gloss), np.asarray(field_m)))
    paint(np.array([255, 255, 255], np.float32), gloss.filter(ImageFilter.GaussianBlur(4)), 0.12)
    img = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8), 'RGBA')
    return img.resize((OUT, OUT), Image.LANCZOS)


def main():
    extra = sys.argv[sys.argv.index('--iconos') + 1] if '--iconos' in sys.argv else None
    icons = load_icons(extra)
    items = insignias()
    os.makedirs(BADGES, exist_ok=True)
    for bid, _, shape, symbol, scheme in items:
        badge(shape, symbol, scheme, icons).save(os.path.join(BADGES, f'{bid}.png'))
    keep = {f'{b[0]}.png' for b in items}
    for f in os.listdir(BADGES):
        if f not in keep:
            os.remove(os.path.join(BADGES, f))
    names = {}
    index = []
    for b in items:
        name = b[1]
        names[name] = names.get(name, 0) + 1
    seen = {}
    for b in items:
        name = b[1]
        if names[name] > 1 and b[0] not in {o[0] for o in OLD}:
            name = f'{name} {COLOR_NAME[b[4]]}'
        seen[name] = seen.get(name, 0) + 1
        if seen[name] > 1:
            name = f'{name} {seen[name]}'
        index.append({'id': b[0], 'nombre': name})
    with open(INDEX, 'w', encoding='utf-8') as f:
        json.dump(index, f, ensure_ascii=False, indent=1)
        f.write('\n')
    if '--ver' in sys.argv:
        dest = sys.argv[sys.argv.index('--ver') + 1]
        os.makedirs(dest, exist_ok=True)
        cols = 16
        rows = (len(items) + cols - 1) // cols
        sheet = Image.new('RGBA', (cols * 76, rows * 76), (226, 234, 246, 255))
        for i, b in enumerate(items):
            im = Image.open(os.path.join(BADGES, f'{b[0]}.png'))
            sheet.alpha_composite(im, ((i % cols) * 76 + 6, (i // cols) * 76 + 6))
        sheet.save(os.path.join(dest, 'insignias.png'))
        # y a tamaño del pad (32 px) sobre su tarjeta
        small = sheet.resize((sheet.width // 2, sheet.height // 2), Image.LANCZOS)
        small.save(os.path.join(dest, 'insignias_32.png'))
    print(f'{len(items)} insignias')


if __name__ == '__main__':
    main()
