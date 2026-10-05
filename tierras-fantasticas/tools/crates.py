"""Escribe los crates en config/products.json a partir de esta tabla y de los modelos exportados.

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
    ('nazgul', 'Crate Arsenal de Nazgul', 'Legendario', 'Treinta armas legendarias de la forja de Nazgul',
     'Los tres volúmenes de la forja de Nazgul: espadas rúnicas, mazas de fuego, guadañas, lanzas y arcos con brillos animados. Cada llave te da una de sus treinta armas.', 'amethyst', 449, ('#c9a8ff', '#9061f9')),
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
    ('beats', 'Crate Beats', 'Épico', 'Armas al ritmo del neón',
     'Neón violeta y azul que late como un altavoz: gran espada, guadaña, tridente, armadura, alas y casco animados.', 'purple', 399, ('#b9b4ff', '#7c6cff')),
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
    ('patrick', 'Crate San Patricio', 'Edición limitada', 'La suerte del trébol',
     'Esmeralda y oro de los duendes: gran espada, guadaña, tridente, ballesta, armadura completa, alas de trébol y sombrero.', 'items/patrick/key', 349, ('#86efac', '#16a34a')),
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
    ('starlight', 'Crate Luz de Estrella', 'Mítico', 'El brillo de las constelaciones',
     'Oro y cristal estelar: hoja, hacha de batalla, daga, hoz, lanza, bastón, armadura completa y alas con destellos.', 'items/starlight/key', 499, ('#fef08a', '#eab308')),
    ('frostbite', 'Crate Congelación', 'Legendario', 'El frío que corta',
     'Hielo eterno y acero escarchado: martillo, maza, guadaña, tridente, ballesta, armadura completa, alas y casco.', 'items/frostbite/key', 449, ('#cffafe', '#06b6d4')),
    ('darkloyal', 'Crate Lealtad Oscura', 'Legendario', 'Juramento de sombra',
     'Acero negro y brasas de los caballeros leales a la oscuridad: guadaña doble, hacha de batalla, tridente, ballesta, armadura completa, alas y casco.', 'items/darkloyal/key', 449, ('#fdba74', '#ea580c')),
    ('easter', 'Crate Conejo de Pascua', 'Edición limitada', 'Huevos, zanahorias y conejos',
     'El arsenal del Conejo de Pascua: gran espada, garrote, maza, guadaña, ballesta, armadura, alas y sombrero de conejo.', 'items/easter/key', 349, ('#f5d0fe', '#d946ef')),
]


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


def main():
    data = json.load(open(PRODUCTS, encoding='utf-8'))
    old = {x['id']: x for x in data if x['category'] == 'crates'}
    crates = []
    for set_id, name, rarity, tagline, desc, key, price, (c1, c2) in CRATES:
        pid = f'crate-{set_id}'
        prev = old.get(pid, {})
        crate = {
            'id': pid, 'category': 'crates', 'name': name, 'theme': set_id, 'colors': [c1, c2], 'rarity': rarity,
            'tagline': tagline, 'description': desc, 'image': f'img/crates/{set_id}.webp',
            'keyImage': f'img/{key}.webp' if '/' in key else f'img/keys/{key}.webp', 'price': prev.get('price', price), 'set': set_id,
            'models': listing(set_id), 'commands': prev.get('commands', [f'crate key give {{player}} {set_id} 1']),
        }
        if prev.get('featured'):
            crate['featured'] = True
        crates.append(crate)
    ranks = [x for x in data if x['category'] == 'rangos']
    gifts = [x for x in data if x['category'] == 'gratis']
    rest = [x for x in data if x['category'] not in ('rangos', 'crates', 'gratis')]
    with open(PRODUCTS, 'w', encoding='utf-8') as fh:
        json.dump(gifts + ranks + crates + rest, fh, ensure_ascii=False, indent=2)
        fh.write('\n')
    print(len(crates), 'crates,', sum(len(c['models']) for c in crates), 'objetos')


if __name__ == '__main__':
    main()
