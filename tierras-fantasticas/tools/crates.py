"""Escribe los rangos y los crates en config/products.json a partir de esta tabla y de los modelos exportados.

Para añadir un pack: súmalo a SETS en build_items.py, ejecuta build_items.py y compose_cover.py,
añade su línea aquí y ejecuta: python3 tools/crates.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_items as B  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
PRODUCTS = os.path.join(HERE, '..', 'config', 'products.json')

# set, nombre, rareza, frase, descripción, llave, precio (céntimos), colores (claro, intenso)
CRATES = [
    ('valentine', 'Crate Valentine', 'Edición limitada', 'Oro rosa forjado con corazones',
     'Armas y herramientas animadas de oro rosa con corazones y pétalos. Incluye armadura completa, alas y casco a juego.', 'pink', 299, ('#ff9cc2', '#ff4f8b')),
    ('necros', 'Crate Necros', 'Legendario', 'Acero maldito y fuego del vacío',
     'El set del nigromante: acero oscuro envuelto en llamas violetas animadas. Incluye armadura completa, tridente, alas y casco.', 'demon', 399, ('#d59cff', '#a855f7')),
    ('luminite', 'Crate Luminite', 'Mítico', 'Cristal de luz y energía arcana',
     'Cristal azul que brilla con energía arcana: armas, herramientas, armadura, alas y casco con destellos animados.', 'ice', 499, ('#9ad8ff', '#4aa8ff')),
    ('mecha', 'Crate Dragón Mecha', 'Mítico', 'Acero de dragón y núcleos de energía',
     'El arsenal del Dragón Mecha Overlord: placas negras con núcleos verde ácido, armadura mecánica, alas y sombrero a juego.', 'slime', 499, ('#d8ff6a', '#a3e635')),
    ('shadow', 'Crate Cazador de Sombras', 'Épico', 'Plata, oro y sombra',
     'El set del Shadow Slayer: hojas plateadas con filos de oro y núcleos oscuros, armadura completa, alas y sombrero.', 'royalty2', 399, ('#d7cbff', '#8b6cf6')),
    ('aether', 'Crate Aetherburn', 'Mítico', 'El éter que arde',
     'Metal turquesa con runas incandescentes: hacha de batalla, hoz, lanza, bastón, armadura y alas del Aetherburn.', 'water', 449, ('#7ff0e0', '#14b8a6')),
    ('conqueror', 'Crate Legado del Conquistador', 'Legendario', 'La herencia de los reyes guerreros',
     'Oro, jade y acero de los antiguos conquistadores: espadas, estoques, guadaña, armadura completa, alas y sombrero.', 'royalty1', 449, ('#ffd77a', '#e2a72e')),
    ('ender', 'Crate Dragón del End', 'Mítico', 'Escamas y fuego del End',
     'Forjado con escamas del dragón del End: armas, herramientas, guantelete, armadura, alas y casco con energía violeta.', 'endportal', 499, ('#e3a6ff', '#c026d3')),
    ('unicorn', 'Crate Pequeño Unicornio', 'Edición limitada', 'Magia de arcoíris y estrellas',
     'Pastel, brillos y cuernos de unicornio: un set adorable con armas, herramientas, armadura, alas y casco animados.', 'rainbow1', 349, ('#ffc4f2', '#e879f9')),
    ('cosmo', 'Crate Cosmos', 'Épico', 'Galaxias en tus manos',
     'Armas con el cielo estrellado dentro: hoja, alabarda, hoz, daga, armadura y alas del pack Cosmo.', 'celestial', 399, ('#a8c8ff', '#3b82f6')),
    ('dracula', 'Crate Drácula', 'Legendario', 'La sangre del conde',
     'El arsenal del conde vampiro: hojas carmesí, alabarda, hoz, armadura gótica y alas de murciélago.', 'redcracked', 449, ('#ff8a8a', '#dc2626')),
    ('lightning', 'Crate Poder del Rayo', 'Mítico', 'La tormenta hecha acero',
     'Armas cargadas de electricidad con rayos animados: cuchillo, guadaña, tridente, armadura, alas, mochila y casco.', 'lightning', 499, ('#fff07a', '#eab308')),
    ('wither', 'Crate Wither Oscuro', 'Legendario', 'La maldición del Wither',
     'Hueso negro y almas en pena: gran espada, guadaña, tridente, armadura, capa y casco del Dark Wither.', 'blackstone', 449, ('#d5dbe6', '#7c8799')),
    ('pink', 'Crate Legado Rosa', 'Épico', 'Elegancia en rosa y oro',
     'El set Pink Legacy: alabarda, hacha de batalla, martillo, armadura y alas en rosa y oro.', 'rainbow2', 349, ('#ffb8da', '#f472b6')),
    ('skeleton', 'Crate Señor Esqueleto', 'Legendario', 'El ejército de los huesos',
     'El arsenal del Skeleton Overlord: gran espada, guantelete, maza, alabarda, armadura y alas de hueso.', 'herobrine', 449, ('#ecebe4', '#a8a29e')),
    ('lunar', 'Crate Dragón Lunar', 'Mítico', 'La luz plateada de la luna',
     'El dragón lunar de 2024: gran espada, guadaña, tridente, armadura completa, alas y cola animadas.', 'nightsky', 499, ('#c7d2fe', '#818cf8')),
    ('cardael', 'Crate Cardael', 'Legendario', 'Oro, rubí y naipes del rey',
     'El arsenal del rey de las cartas: oro y rubí con naipes. Martillo, maza, guadaña, tridente, ballesta, armadura completa, alas y casco.', 'items/cardael/key', 449, ('#fca5a5', '#dc2626')),
    ('azure', 'Crate Azure', 'Legendario', 'Acero azul de las profundidades',
     'Hojas de cristal azul con núcleos dorados: gran espada, hacha, guadaña, tridente, armadura, alas y casco.', 'blue', 449, ('#9fd8ff', '#38bdf8')),
    ('littledragon', 'Crate Pequeño Dragón', 'Mítico', 'Fuego y escamas de dragón',
     'Armas de lava y escamas rojas: gran espada, guadaña, tridente, bandera, mochila, armadura completa, alas y casco.', 'lava', 499, ('#ffb36b', '#f97316')),
    ('bahamut', 'Crate Bahamut', 'Mítico', 'El rey de los dragones',
     'Acero oscuro y escamas de dragón: gran espada, garrote, guantelete, maza, alabarda, armadura completa, alas y casco.', 'netherite', 499, ('#d6dbe4', '#8f9bb3')),
    ('fairy', 'Crate Hada', 'Edición limitada', 'Plata, oro y alas de hada',
     'Blanco perla con filos de oro y alitas: gran espada, guadaña, tridente, ballesta, armadura completa, alas y casco.', 'wings', 449, ('#fff1b8', '#facc15')),
    ('bonita', 'Crate Bonita', 'Épico', 'Rosa pastel con un toque turquesa',
     'Rosa pastel con detalles turquesa: gran espada, guadaña, tridente, ballesta, armadura completa, alas y casco.', 'crystal', 399, ('#ffc2d6', '#f472b6')),
    ('chronicles', 'Crate Crónicas', 'Legendario', 'Reliquias de oro y zafiro',
     'Las armas de las Crónicas: oro antiguo con núcleos de zafiro. Hacha de batalla, hoja, alabarda, armadura completa, alas y casco.', 'diamonds', 449, ('#9fe3ff', '#22a6e6')),
    ('protocal', 'Crate Protocolo', 'Mítico', 'Tecnología de combate',
     'Acero gris y energía roja de tecnología militar: gran espada, guadaña, tridente, ballesta, armadura completa, alas y casco.', 'red', 499, ('#ff9b9b', '#ef4444')),
    ('berserker', 'Crate Berserker', 'Legendario', 'La furia del guerrero',
     'Acero negro con ojos de fuego: hacha de batalla, hoja, alabarda, martillo, armadura completa y alas del Berserker.', 'magma', 449, ('#ffab8a', '#e5483b')),
    # La llave de estos crates es la del propio pack (img/items/<set>/key.webp).
    ('pirate', 'Crate Tesoro Pirata', 'Legendario', 'El botín de los siete mares',
     'Oro, calaveras y plumas del capitán: espada, estoque, guadaña, tridente, ballesta, armadura completa, alas y sombrero pirata.', 'items/pirate/key', 449, ('#ffd36b', '#d9822b')),
    ('cupido', 'Crate Cupido', 'Edición limitada', 'Flechazos en rosa y negro',
     'El lado oscuro del amor: rosa y negro con corazones y lazos. Estoque, guadaña, tridente, ballesta, armadura completa, alas y sombrero.', 'items/cupido/key', 349, ('#ffb3cf', '#e0457b')),
    ('evergreen', 'Crate Evergreen', 'Épico', 'Piedra antigua y hojas eternas',
     'Piedra rúnica, cobre y hojas que nunca se marchitan: gran espada, martillo, guadaña, tridente, ballesta, armadura, alas y corona.', 'items/evergreen/key', 399, ('#86efac', '#22c55e')),
    ('thunderbolt', 'Crate Thunderbolt', 'Mítico', 'Rayos de plata y zafiro',
     'Plata y zafiro cargados de electricidad: espada, martillo, guadaña, lanza, bastón, armadura, alas y casco.', 'items/thunderbolt/key', 499, ('#bae6fd', '#38bdf8')),
    ('cyber', 'Crate Cyber', 'Mítico', 'Neón rojo del futuro',
     'Acero negro con neón rojo de otro siglo: espada, cuchillo, hoz, lanza, bastón, armadura completa, alas y casco.', 'items/cyber/key', 499, ('#fda4af', '#e11d48')),
    ('bat', 'Crate Murciélago Sombrío', 'Legendario', 'Alas de la noche',
     'Acero negro y ojos carmesí de la colonia de murciélagos: espada, hacha de batalla, hoz, lanza, bastón, ballesta, armadura completa y alas.', 'items/bat/key', 449, ('#d4d4d8', '#9f1239')),
    ('fox', 'Crate Zorro de Nueve Colas', 'Mítico', 'La tribu del kitsune',
     'Oro y rojo de la tribu del zorro de nueve colas: espada, estoque, guadaña, tridente, ballesta, armadura, alas y sombrero.', 'items/fox/key', 499, ('#fde68a', '#f59e0b')),
    ('nanoedge', 'Crate Nano Edge', 'Épico', 'Filo de nanotecnología',
     'Blanco y verde ácido de alta tecnología: gran espada, guadaña, tridente, ballesta, armadura completa, alas y casco.', 'items/nanoedge/key', 399, ('#bbf7d0', '#22c55e')),
    ('blueflame', 'Crate Llama Azul', 'Legendario', 'Fuego azul que no se apaga',
     'Metal oscuro envuelto en llamas azules: hoja, hacha de batalla, hoz, lanza, bastón, ballesta, armadura completa y alas.', 'items/blueflame/key', 449, ('#93c5fd', '#2563eb')),
    ('wild', 'Crate Salvaje', 'Épico', 'La fuerza de la selva',
     'Madera, hojas y jade de la naturaleza salvaje: gran espada, guadaña, tridente, ballesta, mochila, armadura completa, alas y casco.', 'items/wild/key', 399, ('#99f6e4', '#0d9488')),
    ('akira', 'Crate Akira', 'Mítico', 'Acero negro y fuego dorado',
     'Acero negro con filos de fuego y oro: gran espada, guadaña, lanza, bastón, escudo, armadura, alas y casco.', 'items/akira/key', 499, ('#fdba74', '#f97316')),
    ('frostbite', 'Crate Congelación', 'Legendario', 'El frío que corta',
     'Hielo eterno y acero escarchado: martillo, maza, guadaña, tridente, ballesta, armadura completa, alas y casco.', 'items/frostbite/key', 449, ('#cffafe', '#06b6d4')),
    ('darkloyal', 'Crate Lealtad Oscura', 'Legendario', 'Juramento de sombra',
     'Acero negro y brasas de los caballeros leales a la oscuridad: guadaña doble, hacha de batalla, tridente, ballesta, armadura completa, alas y casco.', 'items/darkloyal/key', 449, ('#fdba74', '#ea580c')),
    ('ifrit', 'Crate Ifrit', '', 'Obsidiana y fuego carmesí',
     'El arsenal del genio Ifrit: obsidiana verde con llamas carmesí. Espada, martillo, hoz, lanza, bastón, ballesta, armadura completa, alas, carcaj y casco.', '', 449, ('#fda4af', '#be123c')),
    ('easter', 'Crate Conejo de Pascua', 'Edición limitada', 'Huevos, zanahorias y conejos',
     'El arsenal del Conejo de Pascua: gran espada, garrote, maza, guadaña, ballesta, armadura, alas y sombrero de conejo.', 'items/easter/key', 349, ('#f5d0fe', '#d946ef')),
    ('soulskull', 'Crate Soul Skull', 'Legendario', 'Calaveras y fuego de almas',
     'Calaveras y fuego de almas turquesa: hacha de batalla, hoz, lanza, bastón, ballesta, armadura completa y alas.', 'items/soulskull/key', 449, ('#99f6e4', '#14b8a6')),
]

# Nivel de los atributos de los sets (lo usa el TF Client; la web no los enseña). «netherite+1» es la netherita con 1
# punto más de daño, de armadura en cada pieza y de dureza.
CRATE_TIER = 'netherite+1'

# Rangos, de menor a mayor. Cada uno da su grupo de LuckPerms y su set completo (con el nivel de atributos del rango).
# clave, nombre, set del rango, nombre del set, precio (céntimos), color del prefijo (Minecraft, hex), nivel
RANKS = [
    ('mortal', 'Mortal', 'patrick', 'San Patricio', 499, ('green', '#4ade80'), 'iron'),
    ('inmortal', 'Inmortal', 'beats', 'Beats', 799, ('blue', '#7c6cff'), 'diamond'),
    ('magico', 'Mágico', 'darkworld', 'Dark World', 1199, ('dark_purple', '#c026d3'), 'netherite'),
    ('eterno', 'Eterno', 'malika', 'Malika', 1599, ('red', '#ef4444'), 'netherite+1'),
    ('cosmico', 'Cósmico', 'oni', 'Oni', 1999, ('aqua', '#22d3ee'), 'netherite+1.5'),
    ('celestial', 'Celestial', 'eagle', 'Eagle Ascendant', 2499, ('yellow', '#fde047'), 'netherite+2'),
    ('fantastico', 'Fantástico', 'starlight', 'Luz de Estrella', 2999, ('gold', '#f59e0b'), 'netherite+3'),
]


# Cosméticos: se venden por pieza en la pestaña «Cosméticos» (no dan atributos). Por set: colección, colores y temas;
# cada tema: (nombre, [(pieza, tipo, precio en céntimos)]). Tipos: head (cabeza), back (espalda), held (en la mano) y
# balloon (globo en la mano).
COSMETICS = {
    'halloween23': ('Halloween', ('#fdba74', '#f97316'), [
        ('Brujo Calabaza', [('pumpkin_warlock_hat', 'head', 199), ('pumpkin_warlock_backpack', 'back', 249),
                            ('pumpkin_warlock_staff', 'held', 149), ('pumpkin_warlock_balloon', 'balloon', 99)]),
        ('Araña', [('spider_hat', 'head', 199), ('spider_legs_backpack', 'back', 249),
                   ('spider_scythe', 'held', 149), ('spider_balloon', 'balloon', 99)]),
        ('Sepulturero', [('undertaker_hat', 'head', 199), ('undertaker_backpack', 'back', 249),
                         ('undertaker_shovel', 'held', 149), ('undertaker_balloon', 'balloon', 99)]),
    ]),
    'halloweenbundle': ('Halloween', ('#c4b5fd', '#8b5cf6'), [
        ('Bruja', [('halloween_witch_hat', 'head', 199), ('halloween_witch_cauldron', 'back', 249),
                   ('halloween_witch_broom', 'held', 149), ('halloween_ghost_balloon', 'balloon', 99)]),
        ('Calabaza Tenebrosa', [('spooky_pumpkin_hat', 'head', 199), ('spooky_pumpkin_wings', 'back', 299),
                                ('spooky_pumpkin_staff', 'held', 149), ('spooky_pumpkin_balloon', 'balloon', 99)]),
        ('Caramelo', [('candy_pumpkin_beret', 'head', 199), ('candy_pumpkin_backpack', 'back', 249),
                      ('candy_pumpkin_basket', 'held', 149), ('candy_pumpkin_balloon', 'balloon', 99)]),
    ]),
    'cosmeticsv1': ('Aventura', ('#93c5fd', '#3b82f6'), [
        ('Caballero', [('knight_hat', 'head', 199), ('knight_backpack', 'back', 249), ('knight_hand', 'held', 149)]),
        ('Mago', [('wizard_hat', 'head', 199), ('wizard_backpack', 'back', 249), ('wizard_hand', 'held', 149)]),
        ('Ninja', [('ninja_hat', 'head', 199), ('ninja_backpack', 'back', 249), ('ninja_hand', 'held', 149)]),
        ('Hielo', [('frozen_hat', 'head', 199), ('frozen_backpack', 'back', 249), ('frozen_hand', 'held', 149)]),
        ('End', [('ender_hat', 'head', 199), ('ender_backpack', 'back', 249), ('ender_hand', 'held', 149)]),
    ]),
    'unicorncos': ('Unicornio', ('#fbcfe8', '#ec4899'), [
        ('Unicornio', [('unicorn_hat', 'head', 199), ('unicorn_backpack', 'back', 249),
                       ('unicorn_staff', 'held', 149), ('unicorn_balloon', 'balloon', 99)]),
    ]),
    'springcos': ('Primavera', ('#fda4af', '#f43f5e'), [
        ('Primavera', [('helmet', 'head', 199), ('wings', 'back', 299)]),
    ]),
}


def cosmetic_type(set_id, slug):
    for _theme, pieces in COSMETICS.get(set_id, ('', '', []))[2]:
        for piece, kind, _price in pieces:
            if piece == slug:
                return kind
    return None


def cosmetic_products():
    out = []
    for set_id, (collection, (c1, c2), themes) in COSMETICS.items():
        names = {m['id']: m['name'] for m in listing(set_id)}
        for theme, pieces in themes:
            for piece, kind, price in pieces:
                if piece not in names:
                    raise SystemExit(f'falta el cosmético {set_id}/{piece} (ejecuta build_items.py)')
                where = {'head': 'en la cabeza', 'back': 'en la espalda', 'held': 'en la mano',
                         'balloon': 'flotando sobre ti (lo llevas en la mano)'}[kind]
                out.append({
                    'id': f'cos-{set_id}-{piece}'.replace('_', '-'), 'category': 'cosmeticos', 'name': names[piece],
                    'description': f'Cosmético animado que se lleva {where}, de la colección {theme}. Solo cambia tu aspecto.',
                    'collection': collection, 'theme': theme, 'slot': kind, 'price': price, 'maxQuantity': 1,
                    'colors': [c1, c2], 'set': set_id, 'item': piece, 'image': f'img/items/{set_id}/{piece}.webp',
                    'commands': [f'tf web sets give {{player}} {set_id} {piece}'],
                })
    return out


def mod_sets():
    """Sets que registra el TF Client: los de las crates, los de los rangos y el de la ruleta, con su nivel."""
    out = [{'id': r[0], 'name': r[1].replace('Crate ', ''), 'color': r[7][1], 'tier': CRATE_TIER} for r in CRATES]
    out += [{'id': set_id, 'name': set_name, 'color': hexc, 'tier': tier} for _k, _n, set_id, set_name, _p, (_c, hexc), tier in RANKS]
    out.append({'id': ROULETTE_ROW[0], 'name': ROULETTE_ROW[1], 'color': ROULETTE_ROW[7][1], 'tier': CRATE_TIER})
    out += [{'id': set_id, 'name': f'Cosméticos {coll}', 'color': c2, 'tier': 'netherite'}
            for set_id, (coll, (_c1, c2), _themes) in COSMETICS.items()]
    return out


def lighten(hexc, k=0.45):
    n = int(hexc[1:], 16)
    rgb = [(n >> 16) & 255, (n >> 8) & 255, n & 255]
    return '#' + ''.join(f'{round(c + (255 - c) * k):02x}' for c in rgb)


# Lo que cuenta cada rango en la tienda (sin nombrar el set: el dueño no quiere que se vea el nombre del kit)
RANK_PITCH = {
    'mortal': ('Tu leyenda empieza aquí',
               'El primer paso del reino: tu nombre con color y un equipo completo para salir a la aventura desde el primer día.'),
    'inmortal': ('Más fuerte que la muerte',
                 'Un equipo de neón que late al ritmo de la batalla y un prefijo que todo el servidor va a reconocer.'),
    'magico': ('Domina la magia oscura',
               'Armas envueltas en energía arcana, armadura sombría y alas violetas que se ven desde lejos.'),
    'eterno': ('Forjado en fuego eterno',
               'Acero, fuego y alas en llamas: el equipo de quienes no piensan rendirse nunca.'),
    'cosmico': ('Más allá de las estrellas',
                'Armadura de guerrero demonio, armas de otro mundo y unas alas oscuras coronadas por un aro carmesí.'),
    'celestial': ('Bendecido por los cielos',
                  'Oro y sombra para los que vuelan más alto: alas de águila majestuosas y armas dignas de los elegidos.'),
    'fantastico': ('La cima de Tierras Fantásticas',
                   'El rango más alto del reino. Oro y luz de las constelaciones, alas blancas que brillan de verdad y el equipo más espectacular del servidor.'),
}


# Lo que trae cada rango en el servidor, con su comando. Cada rango tiene todo lo del anterior más lo suyo (los
# hogares se sustituyen por el número nuevo). Solo comodidad y aspecto, nada que dé ventaja (normas de Mojang). El
# dueño lo configura en LuckPerms/EssentialsX: los permisos de cada línea están en HANDOFF.md («Rangos: comandos»).
# (clave, comando o None, texto)
RANK_SERVER_PERKS = {
    'mortal': [('homes', '/sethome', 'Hasta 2 hogares; vuelve a ellos con /home'),
               ('prefix', None, 'Prefijo con color en el chat y sobre tu nombre'),
               ('discord', None, 'Rol del rango en Discord')],
    'inmortal': [('homes', '/sethome', 'Hasta 3 hogares'),
                 ('hat', '/hat', 'Ponte en la cabeza el bloque que tengas en la mano')],
    'magico': [('homes', '/sethome', 'Hasta 5 hogares'),
               ('fly', '/fly', 'Vuela en el lobby'),
               ('particles', '/pp', 'Partículas cosméticas a tu alrededor')],
    'eterno': [('homes', '/sethome', 'Hasta 8 hogares'),
               ('nick', '/nick', 'Apodo con colores'),
               ('chatcolor', '&a…&f', 'Escribe en el chat con colores')],
    'cosmico': [('homes', '/sethome', 'Hasta 12 hogares'),
                ('ptime', '/ptime', 'Tu propia hora del día (solo la ves tú)'),
                ('queue', None, 'Cola prioritaria: entras antes cuando el servidor está lleno')],
    'celestial': [('homes', '/sethome', 'Hasta 20 hogares'),
                  ('pweather', '/pweather', 'Tu propio clima (solo lo ves tú)'),
                  ('join', None, 'Aviso especial en el chat cuando entras al servidor')],
    'fantastico': [('homes', '/sethome', 'Hogares ilimitados'),
                   ('title', None, 'Título propio junto a tu nombre (te lo pone el staff)'),
                   ('glow', None, 'Nombre con el color dorado de Fantástico')],
}


def rank_server_perks(key):
    """Lo que trae el rango en el servidor (acumulado), marcando lo que es nuevo respecto al rango anterior."""
    order = [r[0] for r in RANKS]
    out = {}
    for k in order[:order.index(key) + 1]:
        for perk_id, cmd, text in RANK_SERVER_PERKS[k]:
            out[perk_id] = {'cmd': cmd, 'text': text, 'new': k == key}
    return list(out.values())


def rank_products():
    out = []
    for tier, (key, name, set_id, _set_name, price, (mc, hexc), _attrs) in enumerate(RANKS, 1):
        models = listing(set_id)
        tagline, pitch = RANK_PITCH[key]
        has = lambda pred: any(pred(m['id']) for m in models)  # noqa: E731
        armor = has(lambda i: i.startswith('armor_'))
        wings = has(lambda i: i in ('wings', 'wing'))
        pieces = 'Set completo animado: armas, herramientas' + (', armadura' if armor else '') + \
            (' y alas' if wings else '')
        out.append({
            'id': f'rango-{key}', 'category': 'rangos', 'name': f'Rango {name}', 'tagline': tagline,
            'description': pitch,
            'price': price, 'maxQuantity': 1, 'discordRoles': [f'ID_ROL_{key.upper()}'],
            'commands': [f'lp user {{player}} parent add {key}', f'tf web sets give {{player}} {set_id}'],
            'image': f'img/crates/{set_id}.webp', 'tier': tier, 'colors': [lighten(hexc), hexc],
            'set': set_id, 'models': models,
            'perks': [f'Prefijo «{name}» con color en el chat y sobre tu nombre', pieces, f'Rol {name} en Discord'],
            'serverPerks': rank_server_perks(key),
            'rank': {'group': key, 'prefix': name, 'color': mc, 'hex': hexc},
        })
    return out


# Lo que no es una recompensa: el cofre y la llave del propio crate y las mascotas.
NOT_REWARDS = {'chest', 'key', 'little_dragon'}


def listing(set_id):
    labels = {n.split('/')[-1].replace('/', '_'): label for n, label in B.SETS[set_id][2]}
    labels.update({f'armor_{p}': label for p, label in B.ARMOR})
    order = [n.split('/')[-1] for n, _ in B.SETS[set_id][2]] + [f'armor_{p}' for p, _ in B.ARMOR]
    folder = os.path.join(B.PUBLIC, 'models', set_id)
    have = {f[:-5] for f in os.listdir(folder) if f.endswith('.json')}
    out, seen = [], set()
    for slug in order:
        if slug in have and slug not in seen and slug not in NOT_REWARDS:
            seen.add(slug)
            out.append({'id': slug, 'name': labels[slug]})
    return out


# Ruleta: cada giro da un premio al azar de esta lista (la tienda lo elige al confirmarse el pago). Casi todo son cosas
# útiles del servidor; las armas legendarias de Nazgul son el premio raro. Las probabilidades se enseñan en la web.
ROULETTE_SET = 'nazgul'
# Fila del set de la ruleta con el formato de CRATES, para que el TF Client siga creando sus armas.
ROULETTE_ROW = ('nazgul', 'Arsenal de Nazgul', '', 'Treinta armas legendarias de la forja de Nazgul', '', '', 0,
                ('#c9a8ff', '#9061f9'))
# id, nombre, probabilidad (%), comandos, icono
ROULETTE_POOL = [
    ('monedas-2000', '2.000 monedas', 26, ['tf web monedas dar {player} 2000'], 'img/coins-small.png'),
    ('monedas-5000', '5.000 monedas', 16, ['tf web monedas dar {player} 5000'], 'img/coins-big.png'),
    ('experiencia', '32 botellas de experiencia', 14, ['give {player} minecraft:experience_bottle 32'], 'mc:experience_bottle'),
    ('manzanas', '4 manzanas doradas', 12, ['give {player} minecraft:golden_apple 4'], 'mc:golden_apple'),
    ('diamantes', '5 diamantes', 10, ['give {player} minecraft:diamond 5'], 'mc:diamond'),
    ('esmeraldas', '16 esmeraldas', 8, ['give {player} minecraft:emerald 16'], 'mc:emerald'),
    ('netherita', '1 lingote de netherita', 5, ['give {player} minecraft:netherite_ingot 1'], 'mc:netherite_ingot'),
    ('totem', 'Tótem de la inmortalidad', 4, ['give {player} minecraft:totem_of_undying 1'], 'mc:totem_of_undying'),
    ('arma', 'Arma legendaria de Nazgul', 5, None, f'img/items/{ROULETTE_SET}/great_sword.webp'),
]
# Precio de un giro con las monedas del servidor (se cobra en el juego, con la economía del TF Client).
ROULETTE_COIN_PRICE = 3000
# La ruleta está RETIRADA de la tienda: las normas de Mojang para servidores prohíben lo que se parezca al juego de
# azar y las cajas al azar con premios que dan ventaja. Su código sigue en la web (y en los tests, con productos de
# ejemplo) por si algún día se rehace de forma permitida. Con False no se escribe en products.json.
ROULETTE_ENABLED = False
# Categorías que ya no se venden: las llaves (las crates son el set completo) y las monedas con dinero (las monedas
# se ganan jugando; venderlas con dinero para comprar objetos en la tienda de monedas daría ventaja).
RETIRED = ('llaves', 'monedas')
ROULETTE = [
    ('ruleta-1', '1 giro de la ruleta', 1, 99),
    ('ruleta-5', '5 giros de la ruleta', 5, 399),
    ('ruleta-10', '10 giros de la ruleta', 10, 699),
]


def roulette_pool():
    assert sum(p[2] for p in ROULETTE_POOL) == 100, 'las probabilidades de la ruleta tienen que sumar 100'
    out = []
    for pid, name, chance, commands, icon in ROULETTE_POOL:
        entry = {'id': pid, 'name': name, 'chance': chance,
                 'icon': '/api/itemicon/' + icon[3:] if icon.startswith('mc:') else '/' + icon}
        if commands is None:
            entry['weapon'] = True
        else:
            entry['give'] = commands
        out.append(entry)
    return out


def main():
    data = json.load(open(PRODUCTS, encoding='utf-8'))
    old = {x['id']: x for x in data}
    crates = []
    for set_id, name, _rarity, tagline, desc, _key, price, (c1, c2) in CRATES:
        pid = f'crate-{set_id}'
        prev = old.get(pid, {})
        models = listing(set_id)
        crate = {
            'id': pid, 'category': 'crates', 'name': name, 'theme': set_id, 'colors': [c1, c2],
            'tagline': tagline, 'description': desc, 'image': f'img/crates/{set_id}.webp',
            'price': prev.get('price', price), 'maxQuantity': 1, 'set': set_id, 'models': models,
            # La crate es el set completo: se entrega entero con el comando del TF Client.
            'commands': [f'tf web sets give {{player}} {set_id}'],
        }
        crates.append(crate)
    weapons = listing(ROULETTE_SET)
    roulette = []
    for pid, name, spins, price in ROULETTE if ROULETTE_ENABLED else []:
        roulette.append({
            'id': pid, 'category': 'ruleta', 'name': name, 'spins': spins, 'price': price,
            'maxQuantity': 1, 'coinPrice': ROULETTE_COIN_PRICE * spins, 'set': ROULETTE_SET, 'colors': ['#c9a8ff', '#9061f9'],
            'description': f'{spins} premio{"s" if spins > 1 else ""} al azar: monedas, recursos o, con suerte, un arma legendaria.',
            'image': f'img/crates/{ROULETTE_SET}.webp', 'models': weapons, 'pool': roulette_pool(),
            # Los comandos de cada giro los pone la tienda al confirmarse el pago (premio al azar).
            'commands': [],
        })
    gifts = [x for x in data if x['category'] == 'gratis']
    cosmetics = cosmetic_products()
    ranks = rank_products()
    rest = [x for x in data if x['category'] not in ('rangos', 'crates', 'gratis', 'ruleta', 'cosmeticos') + RETIRED]
    with open(PRODUCTS, 'w', encoding='utf-8') as fh:
        json.dump(gifts + ranks + crates + cosmetics + roulette + rest, fh, ensure_ascii=False, indent=2)
        fh.write('\n')
    print(len(ranks), 'rangos,', len(cosmetics), 'cosméticos,', len(crates), 'crates,', sum(len(c['models']) for c in crates), 'objetos;',
          f'ruleta con {len(weapons)} armas' if ROULETTE_ENABLED else 'ruleta retirada')


if __name__ == '__main__':
    main()
