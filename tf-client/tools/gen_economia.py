"""Genera la economía por defecto (1.3.29): python3 tools/gen_economia.py

Escribe src/main/resources/tfclient-jobs-default.json (oficios: lo que paga cada acción, niveles y las plantillas de
misiones diarias que rotan) y src/main/resources/tfclient-misiones-default.json (Misiones del pad y Cazas). La escala es
«tipo dólar»: un diamante en la tienda vale 150 y una hora de trabajo da entre 40 y 150 monedas según el oficio y el
nivel. Las misiones «diaria» son plantillas: el juego (TFRotation) da 3 por oficio al día durante temporadas de 90 días
sin repetir ninguna igual y subiendo cantidad y premio de x1 a x2,5 a lo largo de la temporada.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')

JOBS_SRC = os.path.join(RES, 'tfclient-jobs-default.json')


def m(mid, name, icon, tipo, target, amount, level, coins, xp=None, repeat='diaria', items=None):
    r = {'monedas': coins, 'xp': xp if xp is not None else round(coins * 3)}
    if items:
        r['objetos'] = items
    return {'id': mid, 'nombre': name, 'icono': icon, 'tipo': tipo, 'objetivo': target, 'cantidad': amount,
            'nivel': level, 'repetir': repeat, 'recompensa': r}


# Lo que paga cada acción: (xp, monedas). Antes pagaban entre 5 y 20 veces más.
ACTIONS = {
    'granjero': [('cosechar', '#minecraft:crops', 3, 0.1), ('cosechar', 'minecraft:melon', 2, 0.06),
                 ('cosechar', 'minecraft:pumpkin', 2, 0.06), ('cosechar', 'minecraft:sugar_cane', 1, 0.03),
                 ('cosechar', 'minecraft:nether_wart', 3, 0.1), ('criar', '*', 5, 0.4)],
    'minero': [('romper', '#forge:ores', 6, 0.6), ('romper', '#minecraft:base_stone_overworld', 0.5, 0.02),
               ('romper', '#minecraft:base_stone_nether', 0.5, 0.02)],
    'lenador': [('romper', '#minecraft:logs', 2, 0.15), ('colocar', '#minecraft:saplings', 1, 0.03)],
    'excavador': [('romper', '#minecraft:mineable/shovel', 1, 0.04)],
    'pescador': [('pescar', '*', 8, 0.8)],
    'cazador': [('matar', 'hostil', 6, 0.6), ('matar', 'animal', 1, 0.05)],
    'alquimista': [('preparar', '*', 6, 0.5)],
    'herrero': [('fundir', '#forge:ingots', 2, 0.1), ('fabricar', '#forge:tools', 8, 0.8), ('fabricar', '#forge:armors', 10, 1.0),
                ('reparar', '*', 4, 0.3)],
    'constructor': [('colocar', '*', 0.4, 0.03)],
    'encantador': [('encantar', '*', 15, 1.5)],
}

MISSIONS = {
    'granjero': [
        m('trigo', 'Cosecha de trigo', 'minecraft:wheat', 'cosechar', 'minecraft:wheat', 96, 1, 20),
        m('zanahorias', 'Huerto de zanahorias', 'minecraft:carrot', 'cosechar', 'minecraft:carrots', 96, 1, 20),
        m('patatas', 'Saco de patatas', 'minecraft:potato', 'cosechar', 'minecraft:potatoes', 96, 2, 20),
        m('remolacha', 'Remolachas rojas', 'minecraft:beetroot', 'cosechar', 'minecraft:beetroots', 96, 3, 22),
        m('vacas', 'Rebaño de vacas', 'minecraft:leather', 'criar', 'minecraft:cow', 8, 2, 20),
        m('ovejas', 'Lana nueva', 'minecraft:white_wool', 'criar', 'minecraft:sheep', 8, 3, 20),
        m('cerdos', 'Corral de cerdos', 'minecraft:porkchop', 'criar', 'minecraft:pig', 8, 4, 20),
        m('cultivos', 'Campos llenos', 'minecraft:hay_block', 'cosechar', '#minecraft:crops', 200, 4, 35),
        m('gallinas', 'Gallinero', 'minecraft:egg', 'criar', 'minecraft:chicken', 8, 5, 20),
        m('melones', 'Temporada de melones', 'minecraft:melon_slice', 'cosechar', 'minecraft:melon', 48, 5, 25),
        m('criar', 'Mano con los animales', 'minecraft:wheat_seeds', 'criar', '*', 20, 6, 35),
        m('calabazas', 'Calabazas para la feria', 'minecraft:pumpkin', 'cosechar', 'minecraft:pumpkin', 48, 6, 25),
        m('cana', 'Cañaveral', 'minecraft:sugar_cane', 'cosechar', 'minecraft:sugar_cane', 128, 7, 25),
        m('conejos', 'Madrigueras', 'minecraft:rabbit_foot', 'criar', 'minecraft:rabbit', 5, 8, 30),
        m('pan', 'Horno de pan', 'minecraft:bread', 'fabricar', 'minecraft:bread', 32, 9, 30),
        m('tartas', 'Tartas de fiesta', 'minecraft:cake', 'fabricar', 'minecraft:cake', 3, 11, 40),
        m('verruga', 'Verruga del Nether', 'minecraft:nether_wart', 'cosechar', 'minecraft:nether_wart', 96, 12, 40),
        m('maestro', 'Maestro de la cosecha', 'minecraft:hay_block', 'cosechar', '#minecraft:crops', 2000, 20, 400, 1000, 'nunca',
          ['minecraft:golden_hoe 1']),
    ],
    'minero': [
        m('piedra', 'Cantera', 'minecraft:cobblestone', 'romper', '#minecraft:base_stone_overworld', 384, 1, 20),
        m('carbon', 'Carbón para la forja', 'minecraft:coal', 'romper', '#forge:ores/coal', 48, 1, 25),
        m('granito', 'Granito', 'minecraft:granite', 'romper', 'minecraft:granite', 128, 2, 15),
        m('diorita', 'Diorita', 'minecraft:diorite', 'romper', 'minecraft:diorite', 128, 2, 15),
        m('andesita', 'Andesita', 'minecraft:andesite', 'romper', 'minecraft:andesite', 128, 2, 15),
        m('cobre', 'Brillo de cobre', 'minecraft:raw_copper', 'romper', '#forge:ores/copper', 48, 2, 25),
        m('hierro', 'Vetas de hierro', 'minecraft:raw_iron', 'romper', '#forge:ores/iron', 36, 3, 30),
        m('pizarra', 'Pizarra profunda', 'minecraft:deepslate', 'romper', 'minecraft:deepslate', 256, 4, 25),
        m('toba', 'Toba', 'minecraft:tuff', 'romper', 'minecraft:tuff', 128, 5, 20),
        m('oro', 'Fiebre del oro', 'minecraft:raw_gold', 'romper', '#forge:ores/gold', 24, 6, 40),
        m('netherrack', 'Rocas del Nether', 'minecraft:netherrack', 'romper', 'minecraft:netherrack', 256, 7, 25),
        m('oronether', 'Oro del Nether', 'minecraft:gold_nugget', 'romper', 'minecraft:nether_gold_ore', 48, 7, 35),
        m('redstone', 'Polvo de redstone', 'minecraft:redstone', 'romper', '#forge:ores/redstone', 36, 8, 35),
        m('cuarzo', 'Cuarzo del Nether', 'minecraft:quartz', 'romper', '#forge:ores/quartz', 48, 8, 40),
        m('negra', 'Piedra negra', 'minecraft:blackstone', 'romper', 'minecraft:blackstone', 128, 9, 30),
        m('basalto', 'Basalto', 'minecraft:basalt', 'romper', 'minecraft:basalt', 128, 9, 30),
        m('lapis', 'Lapislázuli', 'minecraft:lapis_lazuli', 'romper', '#forge:ores/lapis', 24, 10, 40),
        m('amatista', 'Geodas de amatista', 'minecraft:amethyst_shard', 'romper', 'minecraft:amethyst_cluster', 8, 11, 40),
        m('diamantes', 'Diamantes', 'minecraft:diamond', 'romper', '#forge:ores/diamond', 8, 15, 80),
        m('esmeraldas', 'Esmeraldas de la montaña', 'minecraft:emerald', 'romper', '#forge:ores/emerald', 4, 20, 90),
        m('escombros', 'Escombros ancestrales', 'minecraft:ancient_debris', 'romper', 'minecraft:ancient_debris', 3, 25, 130),
        m('leyenda', 'Leyenda de la mina', 'minecraft:diamond_pickaxe', 'romper', '#forge:ores', 2000, 30, 600, 1500, 'nunca',
          ['minecraft:diamond 3']),
    ],
    'lenador': [
        m('roble', 'Roble', 'minecraft:oak_log', 'romper', 'minecraft:oak_log', 96, 1, 20),
        m('abedul', 'Abedul', 'minecraft:birch_log', 'romper', 'minecraft:birch_log', 96, 2, 20),
        m('plantar', 'Reforestar', 'minecraft:oak_sapling', 'colocar', '#minecraft:saplings', 48, 2, 15),
        m('abeto', 'Abeto', 'minecraft:spruce_log', 'romper', 'minecraft:spruce_log', 96, 3, 22),
        m('palos', 'Montón de palos', 'minecraft:stick', 'fabricar', 'minecraft:stick', 128, 3, 15),
        m('tablones', 'Tablones', 'minecraft:oak_planks', 'fabricar', '#minecraft:planks', 256, 4, 20),
        m('jungla', 'Selva', 'minecraft:jungle_log', 'romper', 'minecraft:jungle_log', 96, 5, 25),
        m('cofres', 'Cofres para todos', 'minecraft:chest', 'fabricar', 'minecraft:chest', 16, 5, 20),
        m('troncos', 'Tala grande', 'minecraft:oak_wood', 'romper', '#minecraft:logs', 256, 6, 40),
        m('acacia', 'Acacia de la sabana', 'minecraft:acacia_log', 'romper', 'minecraft:acacia_log', 96, 7, 28),
        m('oscuro', 'Bosque oscuro', 'minecraft:dark_oak_log', 'romper', 'minecraft:dark_oak_log', 96, 9, 30),
        m('cerezo', 'Cerezos en flor', 'minecraft:cherry_log', 'romper', 'minecraft:cherry_log', 96, 11, 32),
        m('mangle', 'Manglar', 'minecraft:mangrove_log', 'romper', 'minecraft:mangrove_log', 96, 13, 35),
        m('carmesi', 'Bosque carmesí', 'minecraft:crimson_stem', 'romper', 'minecraft:crimson_stem', 96, 15, 40),
        m('distorsionado', 'Bosque distorsionado', 'minecraft:warped_stem', 'romper', 'minecraft:warped_stem', 96, 15, 40),
        m('hacha', 'Hacha legendaria', 'minecraft:diamond_axe', 'romper', '#minecraft:logs', 4000, 20, 450, 1200, 'nunca'),
    ],
    'excavador': [
        m('tierra', 'Movimiento de tierras', 'minecraft:dirt', 'romper', 'minecraft:dirt', 384, 1, 20),
        m('hierba', 'Prados', 'minecraft:grass_block', 'romper', 'minecraft:grass_block', 192, 1, 18),
        m('arena', 'Playas', 'minecraft:sand', 'romper', '#minecraft:sand', 192, 2, 22),
        m('grava', 'Grava', 'minecraft:gravel', 'romper', 'minecraft:gravel', 192, 4, 25),
        m('pala', 'Pala incansable', 'minecraft:iron_shovel', 'romper', '#minecraft:mineable/shovel', 768, 5, 40),
        m('arcilla', 'Arcilla del río', 'minecraft:clay_ball', 'romper', 'minecraft:clay', 96, 6, 30),
        m('podzol', 'Suelo de taiga', 'minecraft:podzol', 'romper', 'minecraft:podzol', 96, 7, 28),
        m('nieve', 'Quitanieves', 'minecraft:snowball', 'romper', 'minecraft:snow_block', 96, 8, 30),
        m('arenaroja', 'Arena roja', 'minecraft:red_sand', 'romper', 'minecraft:red_sand', 128, 9, 30),
        m('almas', 'Valle de las almas', 'minecraft:soul_sand', 'romper', '#minecraft:soul_fire_base_blocks', 96, 10, 35),
        m('barro', 'Manos en el barro', 'minecraft:mud', 'romper', 'minecraft:mud', 96, 12, 35),
        m('micelio', 'Isla champiñón', 'minecraft:mycelium', 'romper', 'minecraft:mycelium', 64, 14, 40),
        m('topo', 'El topo', 'minecraft:diamond_shovel', 'romper', '#minecraft:mineable/shovel', 6000, 20, 450, 1200, 'nunca'),
    ],
    'pescador': [
        m('peces', 'Red llena', 'minecraft:cod', 'pescar', '#minecraft:fishes', 24, 1, 25),
        m('bacalao', 'Bacalao', 'minecraft:cod', 'pescar', 'minecraft:cod', 16, 1, 20),
        m('salmon', 'Salmón', 'minecraft:salmon', 'pescar', 'minecraft:salmon', 12, 3, 30),
        m('nenufar', 'Nenúfares', 'minecraft:lily_pad', 'pescar', 'minecraft:lily_pad', 2, 4, 25),
        m('tarde', 'Tarde de pesca', 'minecraft:fishing_rod', 'pescar', '*', 48, 5, 40),
        m('globo', 'Pez globo', 'minecraft:pufferfish', 'pescar', 'minecraft:pufferfish', 4, 6, 35),
        m('tropical', 'Peces tropicales', 'minecraft:tropical_fish', 'pescar', 'minecraft:tropical_fish', 4, 8, 40),
        m('arco', 'Arco del fondo', 'minecraft:bow', 'pescar', 'minecraft:bow', 1, 10, 50),
        m('nautilo', 'Concha de nautilo', 'minecraft:nautilus_shell', 'pescar', 'minecraft:nautilus_shell', 1, 12, 60),
        m('libro', 'Libro mojado', 'minecraft:enchanted_book', 'pescar', 'minecraft:enchanted_book', 1, 14, 70),
        m('silla', 'Silla perdida', 'minecraft:saddle', 'pescar', 'minecraft:saddle', 1, 16, 70),
        m('etiqueta', 'Etiqueta del mar', 'minecraft:name_tag', 'pescar', 'minecraft:name_tag', 1, 18, 80),
        m('cien', 'Cien capturas', 'minecraft:fishing_rod', 'pescar', '*', 150, 10, 150, 500, 'nunca'),
        m('lobo', 'Lobo de mar', 'minecraft:heart_of_the_sea', 'pescar', '*', 1500, 20, 500, 1400, 'nunca'),
    ],
    'cazador': [
        m('zombis', 'Noche de zombis', 'minecraft:rotten_flesh', 'matar', 'minecraft:zombie', 30, 1, 25),
        m('esqueletos', 'Huesos fuera', 'minecraft:bone', 'matar', 'minecraft:skeleton', 30, 2, 25),
        m('aranas', 'Telarañas', 'minecraft:string', 'matar', '#tfclient:aranas', 20, 3, 30),
        m('monstruos', 'Guardián de la noche', 'minecraft:iron_sword', 'matar', 'hostil', 120, 4, 45),
        m('creepers', 'Sin explosiones', 'minecraft:gunpowder', 'matar', 'minecraft:creeper', 15, 5, 40),
        m('ahogados', 'Bajo el agua', 'minecraft:trident', 'matar', 'minecraft:drowned', 20, 6, 35),
        m('husks', 'Momias del desierto', 'minecraft:sand', 'matar', 'minecraft:husk', 20, 7, 35),
        m('strays', 'Errantes del hielo', 'minecraft:arrow', 'matar', 'minecraft:stray', 15, 8, 40),
        m('slimes', 'Pegajoso', 'minecraft:slime_ball', 'matar', 'minecraft:slime', 20, 9, 35),
        m('endermans', 'Ojos del End', 'minecraft:ender_pearl', 'matar', 'minecraft:enderman', 12, 10, 55),
        m('phantoms', 'Cielo nocturno', 'minecraft:phantom_membrane', 'matar', 'minecraft:phantom', 8, 11, 50),
        m('brujas', 'Caza de brujas', 'minecraft:glass_bottle', 'matar', 'minecraft:witch', 6, 12, 60),
        m('saqueadores', 'Patrulla', 'minecraft:crossbow', 'matar', 'minecraft:pillager', 12, 13, 55),
        m('magma', 'Magma', 'minecraft:magma_cream', 'matar', 'minecraft:magma_cube', 20, 14, 55),
        m('blazes', 'Fuego del Nether', 'minecraft:blaze_rod', 'matar', 'minecraft:blaze', 15, 15, 70),
        m('guardianes', 'Templo del mar', 'minecraft:prismarine_shard', 'matar', 'minecraft:guardian', 12, 16, 65),
        m('hoglins', 'Bestias carmesíes', 'minecraft:cooked_porkchop', 'matar', 'minecraft:hoglin', 8, 17, 65),
        m('ghasts', 'Lágrimas de ghast', 'minecraft:ghast_tear', 'matar', 'minecraft:ghast', 4, 18, 75),
        m('wither', 'Esqueletos oscuros', 'minecraft:wither_skeleton_skull', 'matar', 'minecraft:wither_skeleton', 12, 20, 90),
        m('heroe', 'Héroe del reino', 'minecraft:diamond_sword', 'matar', 'hostil', 2000, 25, 600, 1500, 'nunca'),
    ],
    'alquimista': [
        m('pociones', 'Pociones del día', 'minecraft:potion', 'preparar', 'minecraft:potion', 12, 1, 25),
        m('frascos', 'Frascos de cristal', 'minecraft:glass_bottle', 'fabricar', 'minecraft:glass_bottle', 24, 1, 15),
        m('soportes', 'Tu primer soporte', 'minecraft:brewing_stand', 'fabricar', 'minecraft:brewing_stand', 1, 2, 25, 80, 'nunca'),
        m('verruga', 'Verruga para el caldero', 'minecraft:nether_wart', 'cosechar', 'minecraft:nether_wart', 32, 3, 20),
        m('muchas', 'Caldero ocupado', 'minecraft:cauldron', 'preparar', '*', 24, 4, 40),
        m('arrojadizas', 'Pociones arrojadizas', 'minecraft:splash_potion', 'preparar', 'minecraft:splash_potion', 12, 5, 40),
        m('polvo', 'Polvo de blaze', 'minecraft:blaze_powder', 'fabricar', 'minecraft:blaze_powder', 12, 6, 30),
        m('fermentado', 'Ojo fermentado', 'minecraft:fermented_spider_eye', 'fabricar', 'minecraft:fermented_spider_eye', 6, 8, 35),
        m('melonbrillo', 'Melón reluciente', 'minecraft:glistering_melon_slice', 'fabricar', 'minecraft:glistering_melon_slice', 6, 9, 35),
        m('zanahoria', 'Zanahorias doradas', 'minecraft:golden_carrot', 'fabricar', 'minecraft:golden_carrot', 12, 10, 40),
        m('persistentes', 'Pociones persistentes', 'minecraft:lingering_potion', 'preparar', 'minecraft:lingering_potion', 8, 12, 60),
        m('crema', 'Crema de magma', 'minecraft:magma_cream', 'fabricar', 'minecraft:magma_cream', 6, 14, 45),
        m('maestro', 'Maestro alquimista', 'minecraft:dragon_breath', 'preparar', '*', 800, 20, 450, 1200, 'nunca'),
    ],
    'herrero': [
        m('lingotes', 'Lingotes de hierro', 'minecraft:iron_ingot', 'fundir', 'minecraft:iron_ingot', 96, 1, 20),
        m('cobre', 'Lingotes de cobre', 'minecraft:copper_ingot', 'fundir', 'minecraft:copper_ingot', 96, 2, 20),
        m('espadas', 'Espadas para la guardia', 'minecraft:iron_sword', 'fabricar', 'minecraft:iron_sword', 4, 2, 30),
        m('hachas', 'Hachas de hierro', 'minecraft:iron_axe', 'fabricar', 'minecraft:iron_axe', 4, 3, 30),
        m('cubos', 'Cubos', 'minecraft:bucket', 'fabricar', 'minecraft:bucket', 8, 3, 25),
        m('picos', 'Picos de hierro', 'minecraft:iron_pickaxe', 'fabricar', 'minecraft:iron_pickaxe', 4, 4, 35),
        m('botas', 'Botas de hierro', 'minecraft:iron_boots', 'fabricar', 'minecraft:iron_boots', 4, 5, 30),
        m('cascos', 'Cascos de hierro', 'minecraft:iron_helmet', 'fabricar', 'minecraft:iron_helmet', 4, 5, 30),
        m('armadura', 'Petos de hierro', 'minecraft:iron_chestplate', 'fabricar', 'minecraft:iron_chestplate', 3, 6, 45),
        m('cadenas', 'Cadenas', 'minecraft:chain', 'fabricar', 'minecraft:chain', 16, 7, 30),
        m('yunque', 'Al yunque', 'minecraft:anvil', 'reparar', '*', 15, 8, 40),
        m('rieles', 'Vía del tren', 'minecraft:rail', 'fabricar', 'minecraft:rail', 64, 9, 35),
        m('oro', 'Lingotes de oro', 'minecraft:gold_ingot', 'fundir', 'minecraft:gold_ingot', 48, 10, 40),
        m('diamante', 'Pico de diamante', 'minecraft:diamond_pickaxe', 'fabricar', 'minecraft:diamond_pickaxe', 1, 12, 70),
        m('yunques', 'Yunques nuevos', 'minecraft:anvil', 'fabricar', 'minecraft:anvil', 1, 14, 60),
        m('maestro', 'Maestro herrero', 'minecraft:smithing_table', 'fabricar', '#forge:tools', 300, 20, 450, 1200, 'nunca'),
    ],
    'constructor': [
        m('bloques', 'Manos a la obra', 'minecraft:bricks', 'colocar', '*', 384, 1, 20),
        m('tablones', 'Suelo de madera', 'minecraft:oak_planks', 'colocar', '#minecraft:planks', 192, 2, 20),
        m('ladrillos', 'Ladrillos', 'minecraft:brick', 'colocar', 'minecraft:bricks', 96, 3, 25),
        m('lana', 'Tapices', 'minecraft:white_wool', 'colocar', '#minecraft:wool', 96, 4, 25),
        m('cristal', 'Ventanales', 'minecraft:glass', 'colocar', '#forge:glass', 96, 5, 30),
        m('escaleras', 'Escaleras', 'minecraft:oak_stairs', 'colocar', '#minecraft:stairs', 96, 6, 30),
        m('losas', 'Losas', 'minecraft:stone_slab', 'colocar', '#minecraft:slabs', 128, 7, 30),
        m('piedra', 'Piedra labrada', 'minecraft:stone_bricks', 'colocar', 'minecraft:stone_bricks', 192, 8, 35),
        m('vallas', 'Vallas', 'minecraft:oak_fence', 'colocar', '#minecraft:fences', 64, 9, 30),
        m('terracota', 'Terracota', 'minecraft:terracotta', 'colocar', '#minecraft:terracotta', 96, 10, 40),
        m('faroles', 'Faroles', 'minecraft:lantern', 'colocar', 'minecraft:lantern', 16, 11, 35),
        m('cuarzo', 'Palacio de cuarzo', 'minecraft:quartz_block', 'colocar', 'minecraft:quartz_block', 96, 12, 45),
        m('arquitecto', 'Gran arquitecto', 'minecraft:scaffolding', 'colocar', '*', 10000, 20, 500, 1400, 'nunca'),
    ],
    'encantador': [
        m('mesa', 'La mesa de encantar', 'minecraft:enchanting_table', 'fabricar', 'minecraft:enchanting_table', 1, 1, 25, 80, 'nunca'),
        m('papel', 'Papel', 'minecraft:paper', 'fabricar', 'minecraft:paper', 64, 1, 15),
        m('encantos', 'Encantamientos', 'minecraft:enchanted_book', 'encantar', '*', 6, 1, 35),
        m('libros', 'Libros', 'minecraft:book', 'fabricar', 'minecraft:book', 24, 2, 20),
        m('libreria', 'Tu librería', 'minecraft:bookshelf', 'fabricar', 'minecraft:bookshelf', 15, 3, 40, 120, 'nunca'),
        m('estanterias', 'Estanterías', 'minecraft:bookshelf', 'fabricar', 'minecraft:bookshelf', 8, 4, 30),
        m('doce', 'Doce encantamientos', 'minecraft:lapis_lazuli', 'encantar', '*', 12, 5, 60),
        m('veinte', 'Veinte encantamientos', 'minecraft:experience_bottle', 'encantar', '*', 25, 8, 110),
        m('archimago', 'Archimago', 'minecraft:nether_star', 'encantar', '*', 800, 20, 600, 1500, 'nunca'),
    ],
}


def jobs():
    d = json.load(open(JOBS_SRC, encoding='utf-8'))
    d['versionEconomia'] = 2
    d['misionesDiariasPorOficio'] = 3
    d['_ayuda'] = [x for x in d['_ayuda'] if not x.startswith('repetir:')] + [
        'repetir: diaria (plantilla que rota: cada día a cada jugador le tocan misionesDiariasPorOficio de las de su nivel, '
        'en temporadas de 90 días sin repetir ninguna igual y subiendo cantidad y premio de x1 a x2,5), siempre (vuelve al '
        'cobrarla) o nunca (una vez).',
        'Economía (1.3.29): un diamante vale 150 en la tienda; una hora de trabajo da entre 40 y 150 monedas según oficio y nivel.']
    lv = d['niveles']
    lv['xpBase'] = 120
    lv['multiplicador'] = 1.15
    lv['recompensa'] = {'monedas': 10, 'monedasPorNivel': 3, 'comandos': []}
    lv['hitos'] = {'10': {'objetos': ['minecraft:diamond 1']}, '25': {'objetos': ['minecraft:diamond 3']},
                   '50': {'objetos': ['minecraft:netherite_ingot 1'], 'monedas': 1500}}
    for j in d['oficios']:
        j['acciones'] = [{'tipo': t, 'objetivo': o, 'xp': x, 'monedas': c} for t, o, x, c in ACTIONS[j['id']]]
        j['misiones'] = MISSIONS[j['id']]
    with open(JOBS_SRC, 'w', encoding='utf-8') as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write('\n')
    return sum(len(v) for v in MISSIONS.values())


# ------------------------------------------------------------------------------------------------------------------
# Misiones del pad (3 diarias y 3 semanales por jugador) y Cazas (4 cada 12 h para todo el servidor)


def d(mid, name, desc, tipo, target, amount, coins, icon):
    return {'id': mid, 'nombre': name, 'descripcion': desc, 'tipo': tipo, 'objetivo': target, 'cantidad': amount,
            'monedas': coins, 'icono': icon}


DAILY = [
    d('piedra', 'Pico incansable', 'Rompe {n} piedras', 'romper', '#minecraft:base_stone_overworld', 192, 12, 'minecraft:stone_pickaxe'),
    d('pizarra', 'Bajo la pizarra', 'Rompe {n} de pizarra profunda', 'romper', 'minecraft:deepslate', 128, 14, 'minecraft:deepslate'),
    d('carbon', 'Minero de carbón', 'Saca {n} menas de carbón', 'romper', '#minecraft:coal_ores', 36, 15, 'minecraft:coal'),
    d('cobre', 'Brillo de cobre', 'Saca {n} menas de cobre', 'romper', '#minecraft:copper_ores', 24, 15, 'minecraft:raw_copper'),
    d('hierro', 'Vena de hierro', 'Saca {n} menas de hierro', 'romper', '#minecraft:iron_ores', 18, 20, 'minecraft:raw_iron'),
    d('oro', 'Pepitas de oro', 'Saca {n} menas de oro', 'romper', '#minecraft:gold_ores', 10, 22, 'minecraft:raw_gold'),
    d('redstone', 'Chispas rojas', 'Saca {n} menas de redstone', 'romper', '#minecraft:redstone_ores', 12, 20, 'minecraft:redstone'),
    d('lapis', 'Azul profundo', 'Saca {n} menas de lapislázuli', 'romper', '#minecraft:lapis_ores', 8, 22, 'minecraft:lapis_lazuli'),
    d('diamante', 'Brillo de diamante', 'Saca {n} menas de diamante', 'romper', '#minecraft:diamond_ores', 3, 35, 'minecraft:diamond'),
    d('arena', 'Arenero', 'Excava {n} de arena', 'romper', '#minecraft:sand', 96, 12, 'minecraft:sand'),
    d('grava', 'Gravilla', 'Excava {n} de grava', 'romper', 'minecraft:gravel', 64, 12, 'minecraft:gravel'),
    d('tierra', 'Movimiento de tierras', 'Excava {n} de tierra', 'romper', 'minecraft:dirt', 192, 12, 'minecraft:dirt'),
    d('arcilla', 'Arcilla del río', 'Rompe {n} bloques de arcilla', 'romper', 'minecraft:clay', 32, 15, 'minecraft:clay_ball'),
    d('roble', 'Leñador de roble', 'Tala {n} troncos de roble', 'romper', 'minecraft:oak_log', 64, 12, 'minecraft:oak_log'),
    d('abedul', 'Bosque blanco', 'Tala {n} troncos de abedul', 'romper', 'minecraft:birch_log', 64, 12, 'minecraft:birch_log'),
    d('abeto', 'Taiga', 'Tala {n} troncos de abeto', 'romper', 'minecraft:spruce_log', 64, 12, 'minecraft:spruce_log'),
    d('jungla', 'Selva', 'Tala {n} troncos de jungla', 'romper', 'minecraft:jungle_log', 48, 14, 'minecraft:jungle_log'),
    d('acacia', 'Sabana', 'Tala {n} troncos de acacia', 'romper', 'minecraft:acacia_log', 48, 14, 'minecraft:acacia_log'),
    d('oscuro', 'Bosque oscuro', 'Tala {n} troncos de roble oscuro', 'romper', 'minecraft:dark_oak_log', 48, 15, 'minecraft:dark_oak_log'),
    d('cerezo', 'Cerezos', 'Tala {n} troncos de cerezo', 'romper', 'minecraft:cherry_log', 48, 15, 'minecraft:cherry_log'),
    d('troncos', 'Tala grande', 'Tala {n} troncos', 'romper', '#minecraft:logs', 128, 18, 'minecraft:iron_axe'),
    d('trigo', 'Cosecha de trigo', 'Cosecha {n} trigos maduros', 'cosechar', 'minecraft:wheat', 48, 12, 'minecraft:wheat'),
    d('zanahorias', 'Huerto naranja', 'Cosecha {n} zanahorias maduras', 'cosechar', 'minecraft:carrots', 48, 12, 'minecraft:carrot'),
    d('patatas', 'Patatas al punto', 'Cosecha {n} patatas maduras', 'cosechar', 'minecraft:potatoes', 48, 12, 'minecraft:potato'),
    d('remolachas', 'Remolachas', 'Cosecha {n} remolachas maduras', 'cosechar', 'minecraft:beetroots', 48, 12, 'minecraft:beetroot'),
    d('melones', 'Melones', 'Cosecha {n} melones', 'cosechar', 'minecraft:melon', 24, 14, 'minecraft:melon_slice'),
    d('calabazas', 'Calabazas', 'Cosecha {n} calabazas', 'cosechar', 'minecraft:pumpkin', 24, 14, 'minecraft:pumpkin'),
    d('cana', 'Cañaveral', 'Cosecha {n} cañas de azúcar', 'cosechar', 'minecraft:sugar_cane', 64, 12, 'minecraft:sugar_cane'),
    d('verruga', 'Verruga del Nether', 'Cosecha {n} verrugas maduras', 'cosechar', 'minecraft:nether_wart', 48, 18, 'minecraft:nether_wart'),
    d('zombis', 'Noche de zombis', 'Derrota {n} zombis', 'matar', 'minecraft:zombie', 15, 15, 'minecraft:rotten_flesh'),
    d('esqueletos', 'Huesos fuera', 'Derrota {n} esqueletos', 'matar', 'minecraft:skeleton', 12, 15, 'minecraft:bone'),
    d('aranas', 'Telarañas', 'Derrota {n} arañas', 'matar', 'minecraft:spider', 10, 14, 'minecraft:string'),
    d('creepers', 'Sin explosiones', 'Derrota {n} creepers', 'matar', 'minecraft:creeper', 8, 18, 'minecraft:gunpowder'),
    d('endermans', 'Mirada del End', 'Derrota {n} endermans', 'matar', 'minecraft:enderman', 5, 22, 'minecraft:ender_pearl'),
    d('ahogados', 'Bajo el agua', 'Derrota {n} ahogados', 'matar', 'minecraft:drowned', 8, 16, 'minecraft:kelp'),
    d('slimes', 'Pegajoso', 'Derrota {n} slimes', 'matar', 'minecraft:slime', 10, 15, 'minecraft:slime_ball'),
    d('monstruos', 'Guardián de la noche', 'Derrota {n} monstruos', 'matar', 'hostil', 40, 20, 'minecraft:iron_sword'),
    d('pesca', 'Día de pesca', 'Pesca {n} veces', 'pescar', '*', 12, 16, 'minecraft:fishing_rod'),
    d('bacalao', 'Bacalao', 'Pesca {n} bacalaos', 'pescar', 'minecraft:cod', 8, 14, 'minecraft:cod'),
    d('salmon', 'Salmones', 'Pesca {n} salmones', 'pescar', 'minecraft:salmon', 5, 16, 'minecraft:salmon'),
    d('lingotes', 'Fundición', 'Funde {n} lingotes de hierro', 'fundir', 'minecraft:iron_ingot', 24, 16, 'minecraft:iron_ingot'),
    d('cristal', 'Vidriero', 'Funde {n} de cristal', 'fundir', 'minecraft:glass', 48, 12, 'minecraft:glass'),
    d('ladrillo', 'Horno de ladrillos', 'Funde {n} ladrillos', 'fundir', 'minecraft:brick', 48, 12, 'minecraft:brick'),
    d('liso', 'Piedra lisa', 'Funde {n} de piedra', 'fundir', 'minecraft:stone', 64, 12, 'minecraft:stone'),
    d('carne', 'Asador', 'Cocina {n} filetes', 'fundir', 'minecraft:cooked_beef', 16, 14, 'minecraft:cooked_beef'),
    d('pan', 'Panadero', 'Hornea {n} panes', 'fabricar', 'minecraft:bread', 24, 12, 'minecraft:bread'),
    d('antorchas', 'Luz en la mina', 'Fabrica {n} antorchas', 'fabricar', 'minecraft:torch', 64, 10, 'minecraft:torch'),
    d('cofres', 'Almacén', 'Fabrica {n} cofres', 'fabricar', 'minecraft:chest', 8, 12, 'minecraft:chest'),
    d('artesano', 'Artesano', 'Fabrica {n} objetos', 'fabricar', '*', 96, 14, 'minecraft:crafting_table'),
    d('rieles', 'Ferroviario', 'Fabrica {n} rieles', 'fabricar', 'minecraft:rail', 32, 16, 'minecraft:rail'),
    d('constructor', 'Constructor', 'Coloca {n} bloques', 'colocar', '*', 256, 14, 'minecraft:bricks'),
    d('ladrillos', 'Muro de ladrillo', 'Coloca {n} ladrillos', 'colocar', 'minecraft:bricks', 64, 14, 'minecraft:brick'),
    d('ventanas', 'Ventanales', 'Coloca {n} de cristal', 'colocar', '#forge:glass', 48, 14, 'minecraft:glass_pane'),
    d('arboles', 'Reforestar', 'Planta {n} brotes', 'colocar', '#minecraft:saplings', 24, 12, 'minecraft:oak_sapling'),
    d('vacas', 'Rebaño', 'Cría {n} vacas', 'criar', 'minecraft:cow', 4, 12, 'minecraft:leather'),
    d('ovejas', 'Lana nueva', 'Cría {n} ovejas', 'criar', 'minecraft:sheep', 4, 12, 'minecraft:white_wool'),
    d('cerdos', 'Corral', 'Cría {n} cerdos', 'criar', 'minecraft:pig', 4, 12, 'minecraft:porkchop'),
    d('gallinas', 'Gallinero', 'Cría {n} gallinas', 'criar', 'minecraft:chicken', 4, 12, 'minecraft:egg'),
    d('pociones', 'Alquimista', 'Prepara {n} pociones', 'preparar', '*', 6, 18, 'minecraft:brewing_stand'),
    d('encantar', 'Aprendiz de mago', 'Encanta {n} objetos', 'encantar', '*', 2, 20, 'minecraft:enchanted_book'),
]

# Presas de las Cazas. Añade aquí los mobs de tus mods ("mod:id"): cada presa rota con las demás.
HUNTS = [
    d('c_zombi', 'Plaga de zombis', 'Derrota {n} zombis', 'matar', 'minecraft:zombie', 40, 35, 'minecraft:zombie_head'),
    d('c_esqueleto', 'Arqueros de hueso', 'Derrota {n} esqueletos', 'matar', 'minecraft:skeleton', 30, 35, 'minecraft:skeleton_skull'),
    d('c_creeper', 'Silencio verde', 'Derrota {n} creepers', 'matar', 'minecraft:creeper', 20, 45, 'minecraft:creeper_head'),
    d('c_arana', 'Nido de arañas', 'Derrota {n} arañas', 'matar', 'minecraft:spider', 25, 30, 'minecraft:spider_eye'),
    d('c_cueva', 'Arañas de cueva', 'Derrota {n} arañas de cueva', 'matar', 'minecraft:cave_spider', 20, 35, 'minecraft:fermented_spider_eye'),
    d('c_enderman', 'Ojos del End', 'Derrota {n} endermans', 'matar', 'minecraft:enderman', 10, 55, 'minecraft:ender_pearl'),
    d('c_bruja', 'Caza de brujas', 'Derrota {n} brujas', 'matar', 'minecraft:witch', 6, 50, 'minecraft:glass_bottle'),
    d('c_slime', 'Pegajoso', 'Derrota {n} slimes', 'matar', 'minecraft:slime', 20, 35, 'minecraft:slime_ball'),
    d('c_ahogado', 'Bajo el agua', 'Derrota {n} ahogados', 'matar', 'minecraft:drowned', 15, 40, 'minecraft:trident'),
    d('c_husk', 'Momias', 'Derrota {n} husks', 'matar', 'minecraft:husk', 15, 40, 'minecraft:sand'),
    d('c_stray', 'Errantes', 'Derrota {n} strays', 'matar', 'minecraft:stray', 12, 45, 'minecraft:tipped_arrow'),
    d('c_saqueador', 'Patrulla', 'Derrota {n} saqueadores', 'matar', 'minecraft:pillager', 10, 50, 'minecraft:crossbow'),
    d('c_vindicador', 'Hachas en la mansión', 'Derrota {n} vindicadores', 'matar', 'minecraft:vindicator', 5, 60, 'minecraft:iron_axe'),
    d('c_fantasma', 'Cielo nocturno', 'Derrota {n} phantoms', 'matar', 'minecraft:phantom', 8, 50, 'minecraft:phantom_membrane'),
    d('c_lepisma', 'Bichos de piedra', 'Derrota {n} lepismas', 'matar', 'minecraft:silverfish', 15, 35, 'minecraft:stone_bricks'),
    d('c_guardian', 'Templo del mar', 'Derrota {n} guardianes', 'matar', 'minecraft:guardian', 10, 60, 'minecraft:prismarine_shard'),
    d('c_blaze', 'Fuego del Nether', 'Derrota {n} blazes', 'matar', 'minecraft:blaze', 12, 65, 'minecraft:blaze_rod'),
    d('c_magma', 'Magma', 'Derrota {n} cubos de magma', 'matar', 'minecraft:magma_cube', 15, 50, 'minecraft:magma_cream'),
    d('c_ghast', 'Lágrimas de ghast', 'Derrota {n} ghasts', 'matar', 'minecraft:ghast', 4, 70, 'minecraft:ghast_tear'),
    d('c_wither', 'Esqueletos oscuros', 'Derrota {n} esqueletos wither', 'matar', 'minecraft:wither_skeleton', 8, 75, 'minecraft:wither_skeleton_skull'),
    d('c_hoglin', 'Bestias carmesíes', 'Derrota {n} hoglins', 'matar', 'minecraft:hoglin', 6, 60, 'minecraft:cooked_porkchop'),
    d('c_piglin', 'Brutos', 'Derrota {n} piglins brutos', 'matar', 'minecraft:piglin_brute', 3, 80, 'minecraft:golden_axe'),
    d('c_zombiaguerrido', 'Pueblo maldito', 'Derrota {n} aldeanos zombi', 'matar', 'minecraft:zombie_villager', 8, 45, 'minecraft:emerald'),
    d('c_shulker', 'Cajas del End', 'Derrota {n} shulkers', 'matar', 'minecraft:shulker', 6, 80, 'minecraft:shulker_shell'),
    d('c_endermite', 'Plaga del End', 'Derrota {n} endermites', 'matar', 'minecraft:endermite', 6, 45, 'minecraft:ender_eye'),
    d('c_monstruos', 'Gran cacería', 'Derrota {n} monstruos', 'matar', 'hostil', 80, 60, 'minecraft:diamond_sword'),
    d('c_noche', 'Vigilia', 'Derrota {n} monstruos', 'matar', 'hostil', 150, 90, 'minecraft:netherite_sword'),
]


def missions():
    o = {
        '_ayuda': ('Misiones del pad y Cazas (1.3.29). tipo: romper, cosechar, colocar, matar, pescar, fabricar, fundir, preparar, '
                   'criar, encantar. objetivo: un id (minecraft:zombie o mod:mob), una etiqueta (#minecraft:logs), * o, al matar, '
                   'hostil / animal. En descripcion, {n} se cambia por la cantidad. Rotan por temporadas de 90 días sin repetir '
                   'ninguna igual: cada día 3 diarias por jugador, cada semana 3 semanales (cantidad x semanalesPorCantidad, pago x '
                   'semanalesPorPago) y cada 12 h 4 cazas para todo el servidor; a lo largo de la temporada suben cantidad y premio '
                   '(x1 a x2,5). Para los mobs de tus mods, añade presas a «cazas» con su id.'),
        'version': 2,
        'semanalesPorCantidad': 5,
        'semanalesPorPago': 4,
        'diarias': DAILY,
        'cazas': HUNTS,
    }
    with open(os.path.join(RES, 'tfclient-misiones-default.json'), 'w', encoding='utf-8') as f:
        json.dump(o, f, ensure_ascii=False, indent=2)
        f.write('\n')


if __name__ == '__main__':
    n = jobs()
    missions()
    print(f'oficios: {n} misiones; misiones del pad: {len(DAILY)} diarias y {len(HUNTS)} cazas')
