#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
La tienda del servidor (config/tfclient/tienda.json; por defecto, src/main/resources/tfclient-shop-default.json) con
TODOS los objetos de Minecraft 1.20.1 agrupados en categorías y con precios sacados de lo que se gana en el servidor
(1.3.134; el dueño: «estudia lo que cuesta ganar monedas con lo que ofrece el mod, de ahí fija precios justos, no
regalados; todos y cada uno de los objetos del juego; la tienda no compra nada más que Tesoros, con límite diario»).

Cómo se fija cada precio (el estudio entero, con los números, en tf-client/TIENDA.md):

  1. Lo que gana un jugador: una hora de oficio da ~75 monedas (de 50 a 140 según el oficio y el nivel), y un día
     normal (2 h, misiones, cazas y el calendario), ~270. Es SALARIO_HORA.
  2. Cada material «de la naturaleza» tiene lo que un jugador consigue en una hora buscándolo él (BASE: unidades por
     hora, con herramientas de hierro y sin granjas). Comprarlo cuesta PRIMA veces lo que ganaría trabajando ese mismo
     rato: precio de una unidad = SALARIO_HORA * PRIMA / unidades por hora. Con PRIMA = 6 salen justo los precios que
     el servidor ya tenía de referencia (el «dólar»: hierro 12, oro 20, carbón 4, esmeralda 40, roca 12 la pila),
     así que comprar nunca sale más barato que trabajar y tampoco es imposible.
  3. Lo fabricado vale lo que sus ingredientes (la receta más barata del juego: mesa, horno, cortapiedras) más un 5 %
     (el trabajo de fabricarlo); en el horno, además, el combustible.
  4. Fuera de la venta: lo que no se consigue en supervivencia, el diamante y la netherita (y lo que los lleva), lo
     del Nether que hace avanzar (vara de blaze, lágrima de ghast, verruga, pociones…), todo lo del End, los huevos
     de criatura y los Tesoros (esos solo los compra la tienda, con límite al día).

Uso (datos de Mojang y de minecraft-data fuera del repo; son datos, se leen como JSON):
  python3 -I tf-client/tools/tienda/precios.py --mc <carpeta con data/minecraft/recipes y tags> \
      --items <minecraft-data/data/pc/1.20/items.json> --blocks <…/blocks.json> --lang <es_es.json de 1.20.1>
Escribe src/main/resources/tfclient-shop-default.json y la tabla de precios en tf-client/TIENDA-PRECIOS.md.
"""
import argparse
import json
import math
import os
import re
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.normpath(os.path.join(AQUI, '..', '..'))

# Sube cuando cambien los precios: el mod cambia la tienda del servidor por la nueva (copia «.antes-vN.json»).
VERSION = 2

SALARIO_HORA = 75.0
PRIMA = 6.0
K = SALARIO_HORA * PRIMA  # precio de una unidad = K / unidades por hora
FABRICAR = 1.05           # lo fabricado: ingredientes + 5 %
COMBUSTIBLE = 0.15        # horno: una fundición (un bloque de algas secas funde 20; el carbón vegetal, 8)

# --- Lo que un jugador consigue en UNA HORA buscándolo él (herramientas de hierro, sin granjas) ---
BASE = {
    # piedra y tierra
    'cobblestone': 2400, 'cobbled_deepslate': 1500, 'granite': 2000, 'diorite': 2000, 'andesite': 2000,
    'calcite': 600, 'tuff': 1200, 'dripstone_block': 800, 'pointed_dripstone': 400, 'smooth_basalt': 500,
    'stone': 900, 'deepslate': 700,  # con toque de seda (fundir la roca suele salir más barato)
    'dirt': 3000, 'coarse_dirt': 1500, 'grass_block': 800, 'podzol': 500, 'rooted_dirt': 400, 'mycelium': 250,
    'mud': 1200, 'clay_ball': 1200, 'sand': 3000, 'red_sand': 2000, 'gravel': 3000, 'snowball': 2400,
    'ice': 600, 'packed_ice': 300, 'blue_ice': 60, 'moss_block': 800, 'obsidian': 60, 'crying_obsidian': 20,
    'sandstone': 1500, 'red_sandstone': 1000,
    # Nether (bloques para construir; lo que hace avanzar no se vende)
    'netherrack': 3000, 'basalt': 1500, 'blackstone': 1500, 'soul_sand': 1200, 'soul_soil': 1200,
    'magma_block': 600, 'crimson_nylium': 250, 'warped_nylium': 250, 'gilded_blackstone': 15,
    'glowstone_dust': 300, 'quartz': 400, 'shroomlight': 150, 'warped_wart_block': 500,
    # madera y plantas
    'oak_log': 700, 'spruce_log': 700, 'birch_log': 700, 'jungle_log': 700, 'acacia_log': 650, 'dark_oak_log': 650,
    'cherry_log': 600, 'mangrove_log': 600, 'crimson_stem': 500, 'warped_stem': 500, 'bamboo': 6000,
    'mangrove_roots': 600, 'oak_leaves': 1500, 'spruce_leaves': 1500, 'birch_leaves': 1500, 'jungle_leaves': 1500,
    'acacia_leaves': 1500, 'dark_oak_leaves': 1500, 'mangrove_leaves': 1200, 'cherry_leaves': 1200,
    'azalea_leaves': 600, 'flowering_azalea_leaves': 500, 'oak_sapling': 400, 'spruce_sapling': 400,
    'birch_sapling': 400, 'jungle_sapling': 300, 'acacia_sapling': 400, 'dark_oak_sapling': 400,
    'cherry_sapling': 300, 'mangrove_propagule': 300, 'azalea': 150, 'flowering_azalea': 120,
    'dandelion': 500, 'poppy': 500, 'blue_orchid': 150, 'allium': 150, 'azure_bluet': 200, 'red_tulip': 200,
    'orange_tulip': 200, 'white_tulip': 200, 'pink_tulip': 200, 'oxeye_daisy': 200, 'cornflower': 200,
    'lily_of_the_valley': 150, 'torchflower': 30, 'pitcher_plant': 30, 'spore_blossom': 20, 'sunflower': 200,
    'lilac': 200, 'rose_bush': 200, 'peony': 200, 'grass': 1500, 'fern': 800, 'tall_grass': 600, 'large_fern': 300,
    'dead_bush': 400, 'seagrass': 800, 'sea_pickle': 300, 'kelp': 1500, 'lily_pad': 300, 'vine': 1200,
    'glow_lichen': 800, 'hanging_roots': 300, 'big_dripleaf': 150, 'small_dripleaf': 150, 'pink_petals': 400,
    'cobweb': 100, 'brown_mushroom': 400, 'red_mushroom': 400, 'brown_mushroom_block': 300,
    'red_mushroom_block': 300, 'mushroom_stem': 300, 'crimson_fungus': 200, 'warped_fungus': 200,
    'crimson_roots': 400, 'warped_roots': 400, 'nether_sprouts': 400, 'weeping_vines': 300, 'twisting_vines': 300,
    'sugar_cane': 900, 'cactus': 600, 'pumpkin': 600, 'melon_slice': 1500, 'sweet_berries': 800,
    'glow_berries': 400, 'cocoa_beans': 600, 'wheat_seeds': 1500, 'wheat': 900, 'carrot': 1000, 'potato': 1000,
    'beetroot': 900, 'beetroot_seeds': 900, 'torchflower_seeds': 30, 'pitcher_pod': 30, 'apple': 150,
    'poisonous_potato': 50, 'honeycomb': 200, 'honey_bottle': 150, 'bee_nest': 20, 'sponge': 10,
    'amethyst_shard': 250, 'small_amethyst_bud': 100, 'medium_amethyst_bud': 80, 'large_amethyst_bud': 60,
    'amethyst_cluster': 60, 'sculk': 800, 'sculk_vein': 600, 'sculk_sensor': 30, 'sculk_catalyst': 15,
    'sculk_shrieker': 15,
    # corales (con toque de seda)
    **{f'{c}_coral_block': 200 for c in ('tube', 'brain', 'bubble', 'fire', 'horn')},
    **{f'{c}_coral': 250 for c in ('tube', 'brain', 'bubble', 'fire', 'horn')},
    **{f'{c}_coral_fan': 250 for c in ('tube', 'brain', 'bubble', 'fire', 'horn')},
    **{f'dead_{c}_coral_block': 300 for c in ('tube', 'brain', 'bubble', 'fire', 'horn')},
    **{f'dead_{c}_coral': 300 for c in ('tube', 'brain', 'bubble', 'fire', 'horn')},
    **{f'dead_{c}_coral_fan': 300 for c in ('tube', 'brain', 'bubble', 'fire', 'horn')},
    # minerales (lo que suelta la mena, con fortuna normal) y las menas con toque de seda
    'coal': 120, 'raw_iron': 40, 'raw_gold': 22, 'raw_copper': 300, 'redstone': 300, 'lapis_lazuli': 250,
    'emerald': 12, 'flint': 400,
    'coal_ore': 50, 'deepslate_coal_ore': 30, 'iron_ore': 22, 'deepslate_iron_ore': 18, 'copper_ore': 60,
    'deepslate_copper_ore': 40, 'gold_ore': 10, 'deepslate_gold_ore': 9, 'redstone_ore': 40,
    'deepslate_redstone_ore': 35, 'emerald_ore': 3, 'deepslate_emerald_ore': 2, 'lapis_ore': 20,
    'deepslate_lapis_ore': 18, 'nether_gold_ore': 25, 'nether_quartz_ore': 100,
    # criaturas (sin granjas)
    'rotten_flesh': 400, 'bone': 300, 'string': 300, 'spider_eye': 120, 'gunpowder': 120, 'slime_ball': 60,
    'leather': 150, 'feather': 300, 'egg': 300, 'beef': 200, 'porkchop': 200, 'chicken': 250, 'mutton': 250,
    'rabbit': 80, 'rabbit_hide': 80, 'rabbit_foot': 15, 'ink_sac': 200, 'glow_ink_sac': 100, 'cod': 120,
    'salmon': 60, 'tropical_fish': 20, 'pufferfish': 25, 'phantom_membrane': 40, 'prismarine_shard': 300,
    'prismarine_crystals': 120, 'nautilus_shell': 15, 'scute': 40, 'turtle_egg': 20, 'white_wool': 600,
    'arrow': 300,
    # botín de cofres, aldeanos y arqueología
    'saddle': 6, 'name_tag': 6, 'iron_horse_armor': 8, 'golden_horse_armor': 8, 'leather_horse_armor': 30,
    'bell': 20, 'experience_bottle': 60, 'goat_horn': 20, 'globe_banner_pattern': 30,
    'chainmail_helmet': 25, 'chainmail_chestplate': 15, 'chainmail_leggings': 18, 'chainmail_boots': 25,
    'zombie_head': 4, 'skeleton_skull': 4, 'creeper_head': 4,
    **{f'music_disc_{d}': 10 for d in ('13', 'cat', 'blocks', 'chirp', 'far', 'mall', 'mellohi', 'stal',
                                         'strad', 'ward', '11', 'wait')},
    **{f'{s}_pottery_sherd': 8 for s in ('angler', 'archer', 'arms_up', 'blade', 'brewer', 'burn', 'danger',
                                         'explorer', 'friend', 'heart', 'heartbreak', 'howl', 'miner', 'mourner',
                                         'plenty', 'prize', 'sheaf', 'shelter', 'skull', 'snort')},
}

# Lo que no sale de una receta del jar pero se hace en el mundo: (ingredientes, unidades que salen, extra)
MUNDO = {
    **{f'{c}_concrete': ({f'{c}_concrete_powder': 1}, 1, 0.0) for c in (
        'white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan',
        'purple', 'blue', 'brown', 'green', 'red', 'black')},
    'exposed_copper': ({'copper_block': 1}, 1, 0.5), 'weathered_copper': ({'copper_block': 1}, 1, 1.0),
    'oxidized_copper': ({'copper_block': 1}, 1, 1.5),
    'carved_pumpkin': ({'pumpkin': 1}, 1, 0.0), 'wet_sponge': ({'sponge': 1}, 1, 0.0),
    'water_bucket': ({'bucket': 1}, 1, 0.0), 'lava_bucket': ({'bucket': 1}, 1, 2.0),
    'milk_bucket': ({'bucket': 1}, 1, 0.5), 'powder_snow_bucket': ({'bucket': 1}, 1, 1.0),
    'cod_bucket': ({'water_bucket': 1, 'cod': 1}, 1, 0.5), 'salmon_bucket': ({'water_bucket': 1, 'salmon': 1}, 1, 0.5),
    'tropical_fish_bucket': ({'water_bucket': 1, 'tropical_fish': 1}, 1, 0.5),
    'pufferfish_bucket': ({'water_bucket': 1, 'pufferfish': 1}, 1, 0.5),
    'tadpole_bucket': ({'water_bucket': 1}, 1, 3.0), 'axolotl_bucket': ({'water_bucket': 1}, 1, 15.0),
    'honey_bottle': ({'glass_bottle': 1}, 1, 3.0),
    'firework_star': ({'gunpowder': 1, 'white_dye': 1}, 1, 0.0),
    'suspicious_stew': ({'bowl': 1, 'brown_mushroom': 1, 'red_mushroom': 1, 'dandelion': 1}, 1, 0.0),
    'decorated_pot': ({'brick': 4}, 1, 0.0),
    # un yunque gastado dura menos: el mellado, dos tercios; el muy dañado, un tercio
    'chipped_anvil': ({'anvil': 1}, 1.5, 0.0), 'damaged_anvil': ({'anvil': 1}, 3, 0.0),
    # con el hacha
    **{f'stripped_{w}_{t}': ({f'{w}_{t}': 1}, 1, 0.0) for w in (
        'oak', 'spruce', 'birch', 'jungle', 'acacia', 'cherry', 'dark_oak', 'mangrove') for t in ('log', 'wood')},
    **{f'stripped_{w}_{t}': ({f'{w}_{t}': 1}, 1, 0.0) for w in ('crimson', 'warped') for t in ('stem', 'hyphae')},
    'stripped_bamboo_block': ({'bamboo_block': 1}, 1, 0.0),
}

# Lo que la receta devuelve (la tarta devuelve los cubos): el ingrediente cuesta lo de dentro, sin el envase
DEVUELVE = {'milk_bucket': 'bucket', 'water_bucket': 'bucket', 'lava_bucket': 'bucket', 'honey_bottle': 'glass_bottle'}

# Nunca a la venta. Lo que no se consigue en supervivencia:
NO_SUPERVIVENCIA = {
    'air', 'bedrock', 'suspicious_sand', 'suspicious_gravel', 'budding_amethyst', 'petrified_oak_slab',
    'chorus_plant', 'spawner', 'farmland', 'dirt_path', 'reinforced_deepslate', 'end_portal_frame',
    'command_block', 'repeating_command_block', 'chain_command_block', 'command_block_minecart', 'barrier',
    'light', 'structure_void', 'structure_block', 'jigsaw', 'knowledge_book', 'debug_stick', 'player_head',
    'frogspawn', 'bundle', 'written_book', 'filled_map', 'enchanted_book', 'potion', 'splash_potion',
    'lingering_potion', 'tipped_arrow',
}
# El diamante y la netherita (y lo que los lleva: se quita solo, por la receta)
DIAMANTE_NETHERITA = {'diamond', 'diamond_block', 'diamond_ore', 'deepslate_diamond_ore', 'diamond_horse_armor',
                      'ancient_debris', 'netherite_scrap', 'netherite_ingot', 'netherite_block',
                      'netherite_upgrade_smithing_template'}
# Lo del Nether que hace avanzar (mitad de partida) y todo lo del End (final de partida)
NETHER = {'blaze_rod', 'blaze_powder', 'ghast_tear', 'magma_cream', 'nether_wart', 'nether_wart_block',
          'wither_rose', 'piglin_head', 'piglin_banner_pattern', 'ochre_froglight', 'verdant_froglight',
          'pearlescent_froglight', 'brewing_stand', 'fire_charge', 'netherite_scrap'}
END = {'ender_pearl', 'ender_eye', 'ender_chest', 'end_stone', 'end_stone_bricks', 'end_rod', 'end_crystal',
       'chorus_fruit', 'chorus_flower', 'popped_chorus_fruit', 'purpur_block', 'purpur_pillar', 'purpur_stairs',
       'purpur_slab', 'shulker_shell', 'shulker_box', 'dragon_breath', 'dragon_egg', 'dragon_head', 'elytra'}
# Las plantillas de herrería se duplican con diamantes: ni se venden ni se compran
PLANTILLAS = re.compile(r'_armor_trim_smithing_template$')

# --- Tesoros: lo único que COMPRA la tienda (objetos rarísimos), con lo que paga y cuántos al día ---
TESOROS = [
    ('dragon_egg', 5000, 1, 'Solo hay uno en el mundo.'),
    ('nether_star', 1800, 1, 'Del Wither.'),
    ('elytra', 1200, 1, 'De los barcos del End. Sin usar.'),
    ('dragon_head', 700, 1, 'De los barcos del End.'),
    ('enchanted_golden_apple', 600, 1, 'Solo sale en cofres.'),
    ('music_disc_5', 500, 1, 'El disco de los 9 fragmentos.'),
    ('heart_of_the_sea', 450, 1, 'De los tesoros enterrados.'),
    ('music_disc_otherside', 400, 1, 'De las fortalezas y ciudades antiguas.'),
    ('music_disc_pigstep', 350, 1, 'De los bastiones.'),
    ('music_disc_relic', 350, 1, 'De la arqueología.'),
    ('trident', 350, 1, 'De los ahogados. Sin usar.'),
    ('totem_of_undying', 300, 1, 'De los evocadores.'),
    ('sniffer_egg', 200, 1, 'De la arqueología en las ruinas del océano.'),
    ('wither_skeleton_skull', 150, 2, 'De los esqueletos del Wither.'),
    ('echo_shard', 80, 4, 'De las ciudades antiguas.'),
    ('disc_fragment_5', 40, 9, 'De las ciudades antiguas.'),
]

# --- Protecciones (piedras del mod): las de 250, 300 y 500 son de pago en la web; 2.500 monedas = 1 $ ---
PROTECCIONES = [
    ('proteccion_10x10', 300), ('proteccion_25x25', 1000), ('proteccion_40x40', 2500), ('proteccion_64x64', 5000),
    ('proteccion_80x80', 8000), ('proteccion_100x100', 12000), ('proteccion_150x150', 20000),
]

CATEGORIAS = [
    # id, nombre, descripción, icono, color, hueco del menú de cofre
    ('construccion', 'Bloques y Materiales de Construcción', 'Piedra, madera, ladrillos, cristal, lana, hormigón y terracota.',
     'minecraft:bricks', '#f59e0b', 10),
    ('decoracion', 'Decorativos', 'Flores, plantas, luces, alfombras, estandartes, camas, carteles y más.',
     'minecraft:flower_pot', '#f472b6', 11),
    ('minerales', 'Minerales', 'Carbón, hierro, oro, cobre, redstone, lapislázuli, esmeralda, cuarzo y amatista. Sin diamante ni netherita.',
     'minecraft:iron_ingot', '#94a3b8', 12),
    ('equipo', 'Equipo', 'Armaduras, espadas, herramientas, arcos, escudos y flechas. Sin diamante ni netherita.',
     'minecraft:iron_sword', '#60a5fa', 13),
    ('botin', 'Drops de mobs', 'Lo que sueltan las criaturas del mundo normal (nada del Nether ni del End).',
     'minecraft:bone', '#a3e635', 14),
    ('granja', 'Granja y Comida', 'Semillas, cultivos, brotes, animales y comida.', 'minecraft:wheat', '#4ade80', 15),
    ('redstone', 'Redstone y Transporte', 'Pistones, tolvas, raíles, vagonetas, barcas y mecanismos.',
     'minecraft:redstone', '#ef4444', 16),
    ('utilidades', 'Utilidades', 'Mesas de trabajo, hornos, cofres, cubos, mapas, libros, tintes y discos.',
     'minecraft:crafting_table', '#22d3ee', 19),
    ('protecciones', 'Protecciones', 'Piedras de protección para tu terreno (las de 250, 300 y 500, en la web).',
     'tfclient:proteccion_25x25', '#c084fc', 20),
    ('tesoros', 'Tesoros', 'La tienda solo compra esto: objetos rarísimos, con un límite al día. Véndelos en VENDER.',
     'minecraft:heart_of_the_sea', '#fcc94a', 22),
]

COLORES = ('white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan',
           'purple', 'blue', 'brown', 'green', 'red', 'black')


def categoria(n, es_bloque, comida):
    """En qué categoría va cada objeto (por su nombre de registro)."""
    if re.search(r'_(sword|pickaxe|axe|shovel|hoe|helmet|chestplate|leggings|boots)$', n) or n in {
            'shield', 'bow', 'crossbow', 'arrow', 'spectral_arrow', 'fishing_rod', 'shears', 'flint_and_steel',
            'brush', 'spyglass', 'turtle_helmet', 'carrot_on_a_stick', 'warped_fungus_on_a_stick',
            'leather_horse_armor', 'iron_horse_armor', 'golden_horse_armor'}:
        return 'equipo'
    if n in {'coal', 'charcoal', 'flint', 'redstone', 'lapis_lazuli', 'emerald', 'quartz', 'amethyst_shard',
             'glowstone_dust', 'raw_iron', 'raw_gold', 'raw_copper', 'iron_ingot', 'gold_ingot', 'copper_ingot',
             'iron_nugget', 'gold_nugget', 'coal_block', 'iron_block', 'gold_block', 'copper_block', 'redstone_block',
             'lapis_block', 'emerald_block', 'raw_iron_block', 'raw_gold_block', 'raw_copper_block', 'quartz_block',
             'amethyst_block'} or n.endswith('_ore'):
        return 'minerales'
    if n in {'rotten_flesh', 'bone', 'bone_block', 'string', 'spider_eye', 'fermented_spider_eye', 'gunpowder',
             'slime_ball', 'slime_block', 'leather', 'rabbit_hide', 'rabbit_foot', 'feather', 'egg', 'ink_sac',
             'glow_ink_sac', 'phantom_membrane', 'prismarine_shard', 'prismarine_crystals', 'nautilus_shell', 'scute',
             'turtle_egg', 'honeycomb', 'cobweb'}:
        return 'botin'
    if n in {'redstone_torch', 'repeater', 'comparator', 'piston', 'sticky_piston', 'observer', 'dispenser',
             'dropper', 'hopper', 'lever', 'tripwire_hook', 'daylight_detector', 'target', 'redstone_lamp',
             'note_block', 'tnt', 'lightning_rod', 'trapped_chest', 'sculk_sensor', 'calibrated_sculk_sensor',
             'sculk_shrieker', 'sculk_catalyst', 'slime_block', 'honey_block', 'tnt_minecart', 'hopper_minecart',
             'chest_minecart', 'furnace_minecart', 'minecart', 'saddle'} or re.search(
            r'(_button|_pressure_plate|rail|_boat|_raft)$', n):
        return 'redstone'
    if n in {'wheat_seeds', 'wheat', 'beetroot_seeds', 'melon_seeds', 'pumpkin_seeds', 'torchflower_seeds',
             'pitcher_pod', 'sugar_cane', 'bamboo', 'cactus', 'pumpkin', 'melon', 'cocoa_beans', 'bone_meal',
             'hay_block', 'sugar', 'cake', 'milk_bucket', 'honey_bottle', 'sweet_berries', 'glow_berries',
             'kelp', 'dried_kelp', 'dried_kelp_block', 'brown_mushroom', 'red_mushroom', 'crimson_fungus',
             'warped_fungus', 'mangrove_propagule', 'composter', 'beehive', 'bee_nest', 'egg', 'lead',
             'cod_bucket', 'salmon_bucket', 'tropical_fish_bucket', 'pufferfish_bucket', 'tadpole_bucket',
             'axolotl_bucket', 'carved_pumpkin', 'turtle_egg', 'sniffer_egg', 'poisonous_potato',
             'glistering_melon_slice', 'golden_carrot', 'golden_apple'} or n.endswith('_sapling') or comida:
        return 'granja'
    if n in {'crafting_table', 'furnace', 'smoker', 'blast_furnace', 'chest', 'barrel', 'anvil', 'chipped_anvil',
             'damaged_anvil', 'grindstone', 'stonecutter', 'loom', 'cartography_table', 'fletching_table',
             'smithing_table', 'cauldron', 'lectern', 'book', 'writable_book', 'paper', 'map', 'compass', 'clock',
             'name_tag', 'bucket', 'water_bucket', 'lava_bucket', 'powder_snow_bucket', 'bowl', 'glass_bottle',
             'experience_bottle', 'firework_rocket', 'firework_star', 'stick', 'goat_horn', 'respawn_anchor',
             'conduit', 'beacon', 'enchanting_table', 'jukebox', 'campfire', 'soul_campfire', 'lodestone',
             'recovery_compass', 'bell', 'scaffolding', 'ladder', 'torch', 'soul_torch', 'item_frame',
             'glow_item_frame', 'armor_stand', 'painting', 'flower_pot', 'nether_brick', 'brick', 'clay_ball',
             'snowball', 'blaze_powder', 'leather', 'globe_banner_pattern', 'creeper_banner_pattern',
             'skull_banner_pattern', 'flower_banner_pattern', 'mojang_banner_pattern', 'piglin_banner_pattern'} \
            or n.endswith('_dye') or n.startswith('music_disc_'):
        return 'utilidades' if n not in {'ladder', 'scaffolding', 'torch', 'soul_torch', 'item_frame', 'glow_item_frame',
                                         'armor_stand', 'painting', 'flower_pot', 'campfire', 'soul_campfire',
                                         'bell'} else ('construccion' if n in {'ladder', 'scaffolding'} else 'decoracion')
    # decorativos: plantas, flores, luces, telas, carteles…
    if (re.search(r'(_carpet|_banner|_bed|_candle|candle|_stained_glass|_stained_glass_pane|_glazed_terracotta|'
                  r'coral|coral_fan|coral_block|_sign|_hanging_sign|_pottery_sherd|_head|_skull|_tulip|_leaves|'
                  r'_roots|_vines|_bud|_petals|_dripleaf|lantern|_froglight)$', n)
            or n in {'grass', 'fern', 'tall_grass', 'large_fern', 'dead_bush', 'seagrass', 'sea_pickle', 'lily_pad',
                     'vine', 'glow_lichen', 'dandelion', 'poppy', 'blue_orchid', 'allium', 'azure_bluet',
                     'oxeye_daisy', 'cornflower', 'lily_of_the_valley', 'torchflower', 'pitcher_plant',
                     'spore_blossom', 'sunflower', 'lilac', 'rose_bush', 'peony', 'azalea', 'flowering_azalea',
                     'moss_carpet', 'amethyst_cluster', 'pointed_dripstone', 'sea_lantern', 'glowstone',
                     'shroomlight', 'jack_o_lantern', 'chain', 'bookshelf', 'chiseled_bookshelf', 'decorated_pot',
                     'nether_sprouts', 'hanging_roots', 'mangrove_roots', 'tinted_glass', 'end_rod',
                     'brown_mushroom_block', 'red_mushroom_block', 'mushroom_stem', 'crimson_roots', 'warped_roots',
                     'sculk', 'sculk_vein', 'big_dripleaf', 'small_dripleaf', 'flowering_azalea_leaves',
                     'azalea_leaves', 'iron_bars', 'glass_pane'}):
        return 'decoracion'
    return 'construccion' if es_bloque else 'utilidades'


def bonito(v):
    """Un precio redondo (2 como poco): de 1 en 1 hasta 100, de 5 en 5 hasta 1.000, de 25 en 25 hasta 10.000 y de 100 en 100."""
    if v <= 100:
        return max(2, int(round(v)))
    if v <= 1000:
        return int(round(v / 5.0)) * 5
    if v <= 10000:
        return int(round(v / 25.0)) * 25
    return int(round(v / 100.0)) * 100


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--mc', required=True)
    ap.add_argument('--items', required=True)
    ap.add_argument('--blocks', required=True)
    ap.add_argument('--lang', required=True)
    ap.add_argument('--salida', default=os.path.join(MOD, 'src', 'main', 'resources', 'tfclient-shop-default.json'))
    ap.add_argument('--tabla', default=os.path.join(MOD, 'TIENDA-PRECIOS.md'))
    a = ap.parse_args()

    items = [i for i in json.load(open(a.items, encoding='utf-8')) if i['name'] != 'air']
    orden = {i['name']: n for n, i in enumerate(items)}
    pila = {i['name']: i['stackSize'] for i in items}
    bloques = {b['name'] for b in json.load(open(a.blocks, encoding='utf-8'))}
    lang = json.load(open(a.lang, encoding='utf-8'))
    foods_path = os.path.join(os.path.dirname(a.items), 'foods.json')
    comidas = {f['name'] for f in json.load(open(foods_path, encoding='utf-8'))} if os.path.exists(foods_path) else set()

    def nombre(n):
        base = lang.get(f'item.minecraft.{n}') or lang.get(f'block.minecraft.{n}') or n
        desc = lang.get(f'item.minecraft.{n}.desc')  # los discos («C418 - cat») y los diseños de estandarte
        return f'{base} ({desc})' if desc else base

    # etiquetas de objetos
    tags = {}
    tdir = os.path.join(a.mc, 'tags', 'items')
    for f in os.listdir(tdir):
        tags[f[:-5]] = json.load(open(os.path.join(tdir, f), encoding='utf-8')).get('values', [])

    def miembros(tag, vistos=None):
        vistos = vistos or set()
        out = []
        for v in tags.get(tag.replace('minecraft:', ''), []):
            v = v if isinstance(v, str) else v.get('id', '')
            if v.startswith('#'):
                if v not in vistos:
                    vistos.add(v)
                    out += miembros(v[1:], vistos)
            else:
                out.append(v.replace('minecraft:', ''))
        return out

    def opciones(ing):
        """Los objetos que valen para un ingrediente (uno, una lista o una etiqueta)."""
        if isinstance(ing, list):
            return [o for x in ing for o in opciones(x)]
        if 'item' in ing:
            return [ing['item'].replace('minecraft:', '')]
        if 'tag' in ing:
            return miembros(ing['tag'])
        return []

    # recetas: (resultado, unidades, [opciones de cada ingrediente], extra)
    recetas = []
    rdir = os.path.join(a.mc, 'recipes')
    for f in sorted(os.listdir(rdir)):
        r = json.load(open(os.path.join(rdir, f), encoding='utf-8'))
        t = r.get('type', '').replace('minecraft:', '')
        res = r.get('result')
        if t == 'crafting_shaped':
            cuenta = {}
            for fila in r['pattern']:
                for ch in fila:
                    if ch != ' ':
                        cuenta[ch] = cuenta.get(ch, 0) + 1
            ings = []
            for ch, n in cuenta.items():
                ings += [opciones(r['key'][ch])] * n
            recetas.append((res['item'], res.get('count', 1), ings, 0.0, FABRICAR))
        elif t == 'crafting_shapeless':
            recetas.append((res['item'], res.get('count', 1), [opciones(i) for i in r['ingredients']], 0.0, FABRICAR))
        elif t in ('smelting', 'blasting', 'smoking', 'campfire_cooking'):
            recetas.append((r['result'], 1, [opciones(r['ingredient'])], COMBUSTIBLE, FABRICAR))
        elif t == 'stonecutting':
            recetas.append((r['result'], r.get('count', 1), [opciones(r['ingredient'])], 0.0, 1.0))
        elif t == 'smithing_transform':
            recetas.append((res['item'], 1, [opciones(r['template']), opciones(r['base']), opciones(r['addition'])],
                            0.0, FABRICAR))
    recetas = [(x.replace('minecraft:', ''), n, ings, extra, m) for x, n, ings, extra, m in recetas]
    for x, v in MUNDO.items():
        if v:
            ings, n, extra = v
            lista = []
            for k, c in ings.items():
                lista += [[k]] * c
            recetas.append((x, n, lista, extra, 1.0))

    tesoros = {t[0] for t in TESOROS}
    prohibido = NO_SUPERVIVENCIA | DIAMANTE_NETHERITA | NETHER | END | tesoros
    prohibido |= {n for n in pila if n.endswith('_spawn_egg') or n.startswith('infested_') or PLANTILLAS.search(n)
                  or n.endswith('_shulker_box')}

    # precio de una unidad: lo de la naturaleza (BASE) o la receta más barata, hasta que no cambie nada
    precio = {n: K / u for n, u in BASE.items() if n not in prohibido}
    for _ in range(60):
        cambio = False
        for x, n, ings, extra, m in recetas:
            if x in prohibido or x not in pila:
                continue
            total = 0.0
            ok = True
            for op in ings:
                vals = [precio[o] - (precio.get(DEVUELVE[o], 0.0) if o in DEVUELVE and x not in MUNDO else 0.0)
                        for o in op if o in precio]
                if not vals:
                    ok = False
                    break
                total += min(vals)
            if not ok:
                continue
            v = (total + extra) * m / n
            if x not in precio or v < precio[x] - 1e-9:
                precio[x] = v
                cambio = True
        if not cambio:
            break
    # una mena con toque de seda vale al menos lo que suelta con fortuna (x2,2)
    for mena, suelta in (('iron_ore', 'raw_iron'), ('deepslate_iron_ore', 'raw_iron'), ('gold_ore', 'raw_gold'),
                         ('deepslate_gold_ore', 'raw_gold'), ('copper_ore', 'raw_copper'),
                         ('deepslate_copper_ore', 'raw_copper'), ('coal_ore', 'coal'), ('deepslate_coal_ore', 'coal'),
                         ('emerald_ore', 'emerald'), ('deepslate_emerald_ore', 'emerald'),
                         ('lapis_ore', 'lapis_lazuli'), ('deepslate_lapis_ore', 'lapis_lazuli'),
                         ('redstone_ore', 'redstone'), ('deepslate_redstone_ore', 'redstone'),
                         ('nether_quartz_ore', 'quartz'), ('nether_gold_ore', 'gold_nugget')):
        if mena in precio and suelta in precio:
            veces = 2.2 * (5 if suelta in ('lapis_lazuli', 'redstone') else 3 if suelta == 'raw_copper' else
                           4 if suelta == 'gold_nugget' else 1)
            precio[mena] = max(precio[mena], precio[suelta] * veces)

    sin_precio = sorted((n for n in pila if n not in precio and n not in prohibido), key=orden.get)

    # categorías
    cats = {c[0]: [] for c in CATEGORIAS}
    for n in sorted(precio, key=lambda k: orden.get(k, 1 << 30)):
        if n not in pila:
            continue
        c = categoria(n, n in bloques, n in comidas)
        u = precio[n]
        st = pila[n]
        # el lote más grande que no pase de TOPE monedas (las pilas para construir; lo demás, casi siempre de 1 en 1)
        tope = 256 if c in ('construccion', 'decoracion') else 24
        lote = next((n for n in (st, 16, 8) if n <= st and u * n <= tope), 1)
        cats[c].append({'objeto': f'minecraft:{n}', 'nombre': nombre(n), 'cantidad': lote,
                        'comprar': bonito(u * lote), 'vender': 0})
    for pid, p in PROTECCIONES:
        n = pid.split('_')[1]
        cats['protecciones'].append({'objeto': f'tfclient:{pid}', 'nombre': f'Protección {n}', 'cantidad': 1,
                                     'comprar': p, 'vender': 0})
    for n, paga, al_dia, de_donde in TESOROS:
        cats['tesoros'].append({'objeto': f'minecraft:{n}', 'nombre': nombre(n), 'descripcion': [de_donde],
                                'cantidad': 1, 'comprar': 0, 'vender': paga, 'limiteVentaDiario': al_dia})

    # la tienda de antes, para lo que no son las categorías (mensajes y ajustes)
    antes = json.load(open(a.salida, encoding='utf-8'))
    salida = {
        '_ayuda': [
            'Tienda del servidor (/tf shop y la app Tienda del pad). Generada por tf-client/tools/tienda/precios.py '
            '(1.3.134): todos los objetos de Minecraft 1.20.1 con precios sacados de lo que se gana (tf-client/TIENDA.md).',
            'cantidad: unidades por lote; comprar / vender: precio de un lote (0 = no). limiteCompraDiario / '
            'limiteVentaDiario: unidades al día por jugador (0 = sin límite).',
            'La tienda solo compra los Tesoros (vender > 0), con límite diario. Cambia lo que quieras desde el pad de '
            'administrador o aquí y usa /tf reload.',
        ],
        'versionTienda': VERSION,
    }
    for k, v in antes.items():
        if k not in ('categorias', '_ayuda', 'versionTienda'):
            salida[k] = v
    salida['categorias'] = []
    for cid, cnom, desc, icono, color, hueco in CATEGORIAS:
        salida['categorias'].append({'id': cid, 'nombre': cnom, 'descripcion': [desc], 'icono': icono,
                                     'color': color, 'hueco': hueco, 'objetos': cats[cid]})
    with open(a.salida, 'w', encoding='utf-8') as f:
        json.dump(salida, f, ensure_ascii=False, indent=1)
        f.write('\n')

    # la tabla
    lineas = ['# Precios de la tienda (generado por `tools/tienda/precios.py`; el estudio, en `TIENDA.md`)', '',
              f'Versión {VERSION}. Una hora de trabajo ≈ {SALARIO_HORA:.0f} monedas; comprar algo cuesta '
              f'{PRIMA:.0f} veces lo que ganarías en el rato de conseguirlo tú; lo fabricado, sus ingredientes + 5 %.', '']
    total = 0
    for cid, cnom, *_ in CATEGORIAS:
        lista = cats[cid]
        total += len(lista)
        lineas += [f'## {cnom} ({len(lista)})', '', '| Objeto | Lote | Compra | Vende la tienda |', '|---|---:|---:|---:|']
        for e in lista:
            v = f"{e['vender']} (máx. {e.get('limiteVentaDiario', 0)}/día)" if e['vender'] else '—'
            lineas.append(f"| {e['nombre']} | {e['cantidad']} | {e['comprar'] or '—'} | {v} |")
        lineas.append('')
    fuera = sorted((n for n in pila if n not in precio or n in prohibido) , key=orden.get)
    lineas += [f'## Fuera de la venta ({len(fuera)})', '', ', '.join(f'{nombre(n)} (`{n}`)' for n in fuera), '']
    with open(a.tabla, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lineas))

    print(f'{total} objetos en {len(CATEGORIAS)} categorías; fuera de la venta: {len(fuera)}')
    for c in cats:
        print(f'  {c}: {len(cats[c])}')
    if sin_precio:
        print(f'fuera por su receta (lleva algo que no se vende) ({len(sin_precio)}): ' + ' '.join(sin_precio))
    return 0


if __name__ == '__main__':
    sys.exit(main())
