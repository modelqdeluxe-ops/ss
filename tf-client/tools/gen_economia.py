"""Genera la economía por defecto (1.3.29): python3 tools/gen_economia.py

Escribe src/main/resources/tfclient-jobs-default.json (oficios: lo que paga cada acción, niveles y las plantillas de
misiones diarias que rotan) y src/main/resources/tfclient-misiones-default.json (Misiones del pad y Cazas). La escala es
«tipo dólar»: un diamante en la tienda vale 150 y una hora de trabajo da entre 40 y 150 monedas según el oficio y el
nivel. Las misiones «diaria» son plantillas: el juego (TFRotation) da 3 por oficio al día durante temporadas de 90 días
sin repetir ninguna igual y subiendo cantidad y premio de x1 a x2,5 a lo largo de la temporada.
"""
import json
import math
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


# (1.3.143, el dueño: «a los oficios agrega muchos niveles más de misiones, sin pasarte con los pagos») misiones hasta el
# nivel 50 (antes llegaban al 20-30): más cantidad y algo más de premio, pero por debajo de lo que vale lo que se saca
# (ver la tienda: tools/tienda/precios.py y TIENDA.md).
MISSIONS_HIGH = {
    'granjero': [
        m('cacao', 'Plantación de cacao', 'minecraft:cocoa_beans', 'cosechar', 'minecraft:cocoa', 48, 14, 28),
        m('bayas', 'Bayas dulces', 'minecraft:sweet_berries', 'cosechar', 'minecraft:sweet_berry_bush', 64, 15, 28),
        m('bambu', 'Bambú', 'minecraft:bamboo', 'cosechar', 'minecraft:bamboo', 192, 16, 28),
        m('cactus', 'Cactus del desierto', 'minecraft:cactus', 'cosechar', 'minecraft:cactus', 96, 17, 30),
        m('trigo2', 'Granero lleno', 'minecraft:hay_block', 'cosechar', 'minecraft:wheat', 256, 18, 38),
        m('patatas2', 'Cosecha de patatas grande', 'minecraft:baked_potato', 'cosechar', 'minecraft:potatoes', 256, 22, 40),
        m('zanahorias2', 'Zanahorias para el mercado', 'minecraft:golden_carrot', 'cosechar', 'minecraft:carrots', 256, 24, 40),
        m('cabras', 'Rebaño de cabras', 'minecraft:goat_horn', 'criar', 'minecraft:goat', 6, 26, 40),
        m('caballos', 'Cuadra de caballos', 'minecraft:saddle', 'criar', 'minecraft:horse', 4, 28, 45),
        m('verruga2', 'Bodega del Nether', 'minecraft:nether_wart_block', 'cosechar', 'minecraft:nether_wart', 256, 30, 50),
        m('abejas', 'Colmenar', 'minecraft:honeycomb', 'criar', 'minecraft:bee', 6, 32, 50),
        m('cultivos2', 'Gran granja', 'minecraft:composter', 'cosechar', '#minecraft:crops', 600, 35, 60),
        m('pasteles', 'Pastelería', 'minecraft:pumpkin_pie', 'fabricar', 'minecraft:pumpkin_pie', 24, 38, 50),
        m('animales2', 'Ganadero', 'minecraft:lead', 'criar', '*', 60, 42, 65),
        m('melones2', 'Huerta de melones', 'minecraft:glistering_melon_slice', 'cosechar', 'minecraft:melon', 160, 46, 65),
        m('leyenda', 'Leyenda del campo', 'minecraft:golden_hoe', 'cosechar', '#minecraft:crops', 8000, 40, 700, 1800, 'nunca',
          ['minecraft:diamond 2']),
    ],
    'minero': [
        m('calcita', 'Calcita', 'minecraft:calcite', 'romper', 'minecraft:calcite', 128, 12, 25),
        m('goteo', 'Bloques de goteo', 'minecraft:pointed_dripstone', 'romper', 'minecraft:dripstone_block', 96, 13, 28),
        m('obsidiana', 'Obsidiana', 'minecraft:obsidian', 'romper', 'minecraft:obsidian', 32, 16, 40),
        m('pizarra2', 'Túneles de pizarra', 'minecraft:cobbled_deepslate', 'romper', 'minecraft:deepslate', 640, 18, 45),
        m('hierro2', 'Mina de hierro', 'minecraft:iron_block', 'romper', '#forge:ores/iron', 96, 22, 60),
        m('cobre2', 'Mina de cobre', 'minecraft:copper_block', 'romper', '#forge:ores/copper', 128, 24, 55),
        m('oro2', 'Mina de oro', 'minecraft:gold_block', 'romper', '#forge:ores/gold', 64, 27, 65),
        m('redstone2', 'Circuitos', 'minecraft:redstone_block', 'romper', '#forge:ores/redstone', 96, 29, 60),
        m('endpiedra', 'Piedra del End', 'minecraft:end_stone', 'romper', 'minecraft:end_stone', 256, 32, 55),
        m('lapis2', 'Encargo de lapislázuli', 'minecraft:lapis_block', 'romper', '#forge:ores/lapis', 64, 34, 65),
        m('cuarzo2', 'Cuarzo para el palacio', 'minecraft:quartz_block', 'romper', '#forge:ores/quartz', 128, 37, 65),
        m('diamantes2', 'Bolsa de diamantes', 'minecraft:diamond_block', 'romper', '#forge:ores/diamond', 16, 40, 95),
        m('escombros2', 'Restos antiguos', 'minecraft:netherite_scrap', 'romper', 'minecraft:ancient_debris', 6, 45, 140),
        m('esmeraldas2', 'Veta de esmeraldas', 'minecraft:emerald_block', 'romper', '#forge:ores/emerald', 8, 48, 110),
        m('rey', 'Rey de las profundidades', 'minecraft:netherite_pickaxe', 'romper', '#forge:ores', 6000, 45, 900, 2400, 'nunca',
          ['minecraft:diamond 3']),
    ],
    'lenador': [
        m('bambu', 'Bambú para la obra', 'minecraft:bamboo', 'romper', 'minecraft:bamboo', 192, 17, 28),
        m('cofres2', 'Cofres de encargo', 'minecraft:barrel', 'fabricar', 'minecraft:chest', 32, 18, 32),
        m('barriles', 'Barriles', 'minecraft:barrel', 'fabricar', 'minecraft:barrel', 16, 19, 32),
        m('puertas', 'Puertas de madera', 'minecraft:oak_door', 'fabricar', '#minecraft:wooden_doors', 24, 21, 32),
        m('vallas', 'Cercados', 'minecraft:oak_fence', 'fabricar', '#minecraft:wooden_fences', 48, 23, 34),
        m('troncos2', 'Aserradero', 'minecraft:stripped_oak_log', 'romper', '#minecraft:logs', 512, 25, 55),
        m('plantar2', 'Bosque nuevo', 'minecraft:spruce_sapling', 'colocar', '#minecraft:saplings', 128, 27, 40),
        m('escaleras', 'Escaleras de madera', 'minecraft:oak_stairs', 'fabricar', '#minecraft:wooden_stairs', 64, 29, 38),
        m('barcos', 'Astillero', 'minecraft:oak_boat', 'fabricar', '#minecraft:boats', 6, 31, 40),
        m('carmesi2', 'Tala carmesí', 'minecraft:crimson_hyphae', 'romper', 'minecraft:crimson_stem', 256, 34, 60),
        m('distorsionado2', 'Tala distorsionada', 'minecraft:warped_hyphae', 'romper', 'minecraft:warped_stem', 256, 36, 60),
        m('mangle2', 'Pilotes de mangle', 'minecraft:mangrove_roots', 'romper', 'minecraft:mangrove_log', 256, 40, 60),
        m('troncos3', 'Maderero mayor', 'minecraft:oak_wood', 'romper', '#minecraft:logs', 900, 45, 80),
        m('guardabosques', 'Guardabosques', 'minecraft:netherite_axe', 'romper', '#minecraft:logs', 12000, 40, 700, 1800, 'nunca',
          ['minecraft:diamond 2']),
    ],
    'excavador': [
        m('almas2', 'Suelo de almas', 'minecraft:soul_soil', 'romper', 'minecraft:soul_soil', 128, 16, 35),
        m('arena2', 'Arenal', 'minecraft:sandstone', 'romper', '#minecraft:sand', 640, 18, 45),
        m('grava2', 'Gravera', 'minecraft:flint', 'romper', 'minecraft:gravel', 512, 21, 45),
        m('tierra2', 'Explanada', 'minecraft:coarse_dirt', 'romper', 'minecraft:dirt', 1000, 24, 50),
        m('nieve2', 'Expedición de nieve', 'minecraft:powder_snow_bucket', 'romper', 'minecraft:snow_block', 256, 27, 50),
        m('arcilla2', 'Alfarero', 'minecraft:terracotta', 'romper', 'minecraft:clay', 256, 30, 55),
        m('barro2', 'Ciénaga', 'minecraft:packed_mud', 'romper', 'minecraft:mud', 256, 33, 55),
        m('pala2', 'Pala de oro', 'minecraft:golden_shovel', 'romper', '#minecraft:mineable/shovel', 2000, 36, 70),
        m('podzol2', 'Taiga profunda', 'minecraft:rooted_dirt', 'romper', 'minecraft:podzol', 256, 40, 55),
        m('micelio2', 'Champiñonal', 'minecraft:red_mushroom_block', 'romper', 'minecraft:mycelium', 192, 45, 65),
        m('cavador', 'El gran cavador', 'minecraft:netherite_shovel', 'romper', '#minecraft:mineable/shovel', 20000, 40, 700, 1800, 'nunca',
          ['minecraft:diamond 2']),
    ],
    'pescador': [
        m('bacalao2', 'Lonja de bacalao', 'minecraft:cooked_cod', 'pescar', 'minecraft:cod', 48, 20, 45),
        m('salmon2', 'Remontando el río', 'minecraft:cooked_salmon', 'pescar', 'minecraft:salmon', 32, 22, 50),
        m('peces2', 'Barco pesquero', 'minecraft:barrel', 'pescar', '#minecraft:fishes', 64, 25, 55),
        m('globo2', 'Pez globo venenoso', 'minecraft:pufferfish', 'pescar', 'minecraft:pufferfish', 10, 28, 55),
        m('tropical2', 'Arrecife', 'minecraft:tropical_fish_bucket', 'pescar', 'minecraft:tropical_fish', 8, 31, 55),
        m('tarde2', 'Jornada de pesca', 'minecraft:fishing_rod', 'pescar', '*', 120, 34, 70),
        m('tesoros', 'Tesoros del fondo', 'minecraft:chest', 'pescar', 'minecraft:enchanted_book', 2, 38, 85),
        m('nautilo2', 'Coleccionista de conchas', 'minecraft:nautilus_shell', 'pescar', 'minecraft:nautilus_shell', 2, 42, 90),
        m('peces3', 'Gran pesquería', 'minecraft:cod_bucket', 'pescar', '#minecraft:fishes', 160, 46, 90),
        m('capitan', 'Capitán de pesca', 'minecraft:conduit', 'pescar', '*', 5000, 40, 700, 1800, 'nunca', ['minecraft:diamond 2']),
    ],
    'cazador': [
        m('zombis2', 'Horda de zombis', 'minecraft:zombie_head', 'matar', 'minecraft:zombie', 80, 22, 50),
        m('esqueletos2', 'Cementerio', 'minecraft:skeleton_skull', 'matar', 'minecraft:skeleton', 70, 23, 50),
        m('piglins', 'Piglins zombi', 'minecraft:gold_nugget', 'matar', 'minecraft:zombified_piglin', 40, 24, 50),
        m('cueva', 'Arañas de cueva', 'minecraft:cobweb', 'matar', 'minecraft:cave_spider', 25, 26, 50),
        m('vindicadores', 'Mansión del bosque', 'minecraft:iron_axe', 'matar', 'minecraft:vindicator', 10, 28, 70),
        m('lepismas', 'Fortaleza', 'minecraft:stone_bricks', 'matar', 'minecraft:silverfish', 30, 30, 55),
        m('devastadores', 'Asalto', 'minecraft:saddle', 'matar', 'minecraft:ravager', 3, 32, 80),
        m('shulkers', 'Ciudad del End', 'minecraft:shulker_shell', 'matar', 'minecraft:shulker', 10, 35, 85),
        m('endermans2', 'Granja de endermans', 'minecraft:ender_eye', 'matar', 'minecraft:enderman', 40, 37, 80),
        m('blazes2', 'Granja de blazes', 'minecraft:blaze_powder', 'matar', 'minecraft:blaze', 40, 39, 85),
        m('brutos', 'Bastión', 'minecraft:golden_axe', 'matar', 'minecraft:piglin_brute', 4, 42, 95),
        m('wither2', 'Fortaleza oscura', 'minecraft:coal', 'matar', 'minecraft:wither_skeleton', 30, 44, 100),
        m('monstruos2', 'Noche de caza', 'minecraft:diamond_sword', 'matar', 'hostil', 400, 46, 100),
        m('leyenda', 'Leyenda de la caza', 'minecraft:netherite_sword', 'matar', 'hostil', 6000, 45, 900, 2400, 'nunca', ['minecraft:diamond 3']),
    ],
    'alquimista': [
        m('pociones2', 'Botica', 'minecraft:potion', 'preparar', 'minecraft:potion', 36, 16, 45),
        m('arrojadizas2', 'Granadas de cristal', 'minecraft:splash_potion', 'preparar', 'minecraft:splash_potion', 24, 19, 50),
        m('frascos2', 'Vidriería', 'minecraft:glass_bottle', 'fabricar', 'minecraft:glass_bottle', 96, 21, 30),
        m('polvo2', 'Polvo para el caldero', 'minecraft:blaze_powder', 'fabricar', 'minecraft:blaze_powder', 32, 24, 45),
        m('azucar', 'Azúcar', 'minecraft:sugar', 'fabricar', 'minecraft:sugar', 64, 26, 35),
        m('persistentes2', 'Nubes mágicas', 'minecraft:lingering_potion', 'preparar', 'minecraft:lingering_potion', 16, 30, 75),
        m('fermentado2', 'Fermentos', 'minecraft:fermented_spider_eye', 'fabricar', 'minecraft:fermented_spider_eye', 16, 33, 55),
        m('zanahoria2', 'Zanahorias de la vista', 'minecraft:golden_carrot', 'fabricar', 'minecraft:golden_carrot', 32, 36, 60),
        m('muchas2', 'Laboratorio', 'minecraft:brewing_stand', 'preparar', '*', 72, 40, 85),
        m('muchas3', 'Gran laboratorio', 'minecraft:dragon_breath', 'preparar', '*', 120, 46, 100),
        m('archialquimista', 'Archialquimista', 'minecraft:beacon', 'preparar', '*', 2500, 40, 700, 1800, 'nunca', ['minecraft:diamond 2']),
    ],
    'herrero': [
        m('hierro2', 'Fundición grande', 'minecraft:iron_block', 'fundir', 'minecraft:iron_ingot', 256, 16, 45),
        m('cobre2', 'Tejados de cobre', 'minecraft:copper_block', 'fundir', 'minecraft:copper_ingot', 256, 18, 40),
        m('escudos', 'Escudos', 'minecraft:shield', 'fabricar', 'minecraft:shield', 6, 20, 45),
        m('faroles', 'Faroles', 'minecraft:lantern', 'fabricar', 'minecraft:lantern', 24, 22, 40),
        m('tolvas', 'Tolvas', 'minecraft:hopper', 'fabricar', 'minecraft:hopper', 8, 24, 50),
        m('barras', 'Barrotes', 'minecraft:iron_bars', 'fabricar', 'minecraft:iron_bars', 64, 26, 40),
        m('armadura2', 'Armería', 'minecraft:iron_chestplate', 'fabricar', '#forge:armors', 12, 28, 60),
        m('herramientas', 'Taller de herramientas', 'minecraft:iron_pickaxe', 'fabricar', '#forge:tools', 16, 30, 60),
        m('yunque2', 'Reparaciones', 'minecraft:anvil', 'reparar', '*', 40, 33, 65),
        m('oro2', 'Orfebre', 'minecraft:gold_block', 'fundir', 'minecraft:gold_ingot', 128, 36, 70),
        m('rieles2', 'Red de vías', 'minecraft:powered_rail', 'fabricar', 'minecraft:rail', 128, 39, 65),
        m('vagonetas', 'Vagonetas', 'minecraft:minecart', 'fabricar', 'minecraft:minecart', 8, 42, 60),
        m('diamante2', 'Equipo de diamante', 'minecraft:diamond_chestplate', 'fabricar', 'minecraft:diamond_chestplate', 1, 46, 90),
        m('forjador', 'Forjador legendario', 'minecraft:netherite_ingot', 'fabricar', '#forge:tools', 800, 40, 700, 1800, 'nunca',
          ['minecraft:diamond 2']),
    ],
    'constructor': [
        m('ladrillos2', 'Casa de ladrillo', 'minecraft:bricks', 'colocar', 'minecraft:bricks', 256, 14, 40),
        m('arenisca', 'Arenisca', 'minecraft:sandstone', 'colocar', 'minecraft:sandstone', 192, 15, 35),
        m('troncos', 'Cabaña de troncos', 'minecraft:oak_log', 'colocar', '#minecraft:logs', 192, 17, 35),
        m('cristal2', 'Invernadero', 'minecraft:glass', 'colocar', '#forge:glass', 256, 19, 45),
        m('lana2', 'Alfombras de colores', 'minecraft:red_wool', 'colocar', '#minecraft:wool', 256, 21, 45),
        m('escaleras2', 'Escalinatas', 'minecraft:stone_stairs', 'colocar', '#minecraft:stairs', 256, 23, 45),
        m('losas2', 'Tejados', 'minecraft:stone_slab', 'colocar', '#minecraft:slabs', 384, 25, 45),
        m('muros', 'Murallas', 'minecraft:cobblestone_wall', 'colocar', '#minecraft:walls', 128, 27, 45),
        m('terracota2', 'Barro cocido', 'minecraft:orange_terracotta', 'colocar', '#minecraft:terracotta', 256, 30, 55),
        m('hormigon', 'Hormigón', 'minecraft:white_concrete', 'colocar', '#tfclient:hormigon', 256, 33, 55),
        m('piedra2', 'Castillo', 'minecraft:stone_bricks', 'colocar', 'minecraft:stone_bricks', 512, 36, 60),
        m('cuarzo2', 'Templo de cuarzo', 'minecraft:quartz_pillar', 'colocar', 'minecraft:quartz_block', 256, 40, 65),
        m('bloques2', 'Gran obra', 'minecraft:bricks', 'colocar', '*', 2000, 45, 85),
        m('maestro', 'Maestro de obras', 'minecraft:beacon', 'colocar', '*', 40000, 40, 700, 1800, 'nunca', ['minecraft:diamond 2']),
    ],
    'encantador': [
        m('papel2', 'Papelería', 'minecraft:paper', 'fabricar', 'minecraft:paper', 192, 10, 30),
        m('libros2', 'Biblioteca', 'minecraft:book', 'fabricar', 'minecraft:book', 64, 12, 40),
        m('treinta', 'Treinta encantamientos', 'minecraft:enchanted_book', 'encantar', '*', 30, 14, 120),
        m('atriles', 'Atriles', 'minecraft:lectern', 'fabricar', 'minecraft:lectern', 4, 16, 40),
        m('estanterias2', 'Sala de lectura', 'minecraft:bookshelf', 'fabricar', 'minecraft:bookshelf', 24, 18, 55),
        m('yunque', 'Yunque del encantador', 'minecraft:anvil', 'reparar', '*', 20, 20, 50),
        m('cuarenta', 'Cuarenta encantamientos', 'minecraft:experience_bottle', 'encantar', '*', 40, 24, 140),
        m('afilar', 'Muela', 'minecraft:grindstone', 'fabricar', 'minecraft:grindstone', 2, 28, 45),
        m('cincuenta', 'Cincuenta encantamientos', 'minecraft:lapis_block', 'encantar', '*', 50, 34, 160),
        m('reparar2', 'Taller mágico', 'minecraft:chipped_anvil', 'reparar', '*', 40, 40, 70),
        m('setenta', 'Setenta encantamientos', 'minecraft:enchanting_table', 'encantar', '*', 70, 46, 190),
        m('hechicero', 'Gran hechicero', 'minecraft:end_crystal', 'encantar', '*', 2500, 40, 700, 1800, 'nunca', ['minecraft:diamond 2']),
    ],
}


# (1.3.115) La economía exigente: lo que se gana trabajando, al 60 %. Aquí los pagos se escriben como antes y al guardar se
# aplica lo mismo que PadEconomy.scale (menos de 10: con 2 decimales; si no, redondeado), así que las configs ya salen
# con «economia»: 3 y el servidor no las vuelve a bajar.
ECON = 0.6


def scale(o, fields=('monedas', 'monedasPorNivel')):
    if isinstance(o, list):
        for c in o:
            scale(c, fields)
    elif isinstance(o, dict):
        for k, v in o.items():
            if k in fields and isinstance(v, (int, float)) and not isinstance(v, bool) and v > 0:
                x = v * ECON
                o[k] = math.floor(x * 100 + 0.5) / 100 if x < 10 else float(max(1, math.floor(x + 0.5)))
            elif isinstance(v, (dict, list)):
                scale(v, fields)
    return o


def check(name, lst):
    ids = [x['id'] for x in lst]
    dup = sorted({i for i in ids if ids.count(i) > 1})
    assert not dup, f'{name}: ids repetidos {dup}'
    names = [x['nombre'] for x in lst]
    dup = sorted({i for i in names if names.count(i) > 1})
    assert not dup, f'{name}: nombres repetidos {dup}'


def jobs():
    d = json.load(open(JOBS_SRC, encoding='utf-8'))
    # (1.3.143) versión 3: las misiones nuevas (niveles 10 a 50) llegan a los servidores (la de antes se guarda)
    d['versionEconomia'] = 3
    d['economia'] = 3
    d['misionesDiariasPorOficio'] = 3
    d['_ayuda'] = [x for x in d['_ayuda'] if not x.startswith('repetir:')] + [
        'repetir: diaria (plantilla que rota: cada día a cada jugador le tocan misionesDiariasPorOficio de las de su nivel, '
        'en temporadas de 90 días sin repetir ninguna igual y subiendo cantidad y premio de x1 a x2,5), siempre (vuelve al '
        'cobrarla) o nunca (una vez).',
        'Economía (1.3.115, exigente): una hora de trabajo da unas 40-90 monedas según oficio y nivel. (1.3.143) Misiones de oficio hasta el nivel 50 y un objetivo «nunca» de leyenda en cada uno.']
    lv = d['niveles']
    lv['xpBase'] = 120
    lv['multiplicador'] = 1.15
    lv['recompensa'] = {'monedas': 10, 'monedasPorNivel': 3, 'comandos': []}
    lv['hitos'] = {'10': {'objetos': ['minecraft:diamond 1']}, '25': {'objetos': ['minecraft:diamond 3']},
                   '50': {'objetos': ['minecraft:netherite_ingot 1'], 'monedas': 1500}}
    for j in d['oficios']:
        j['acciones'] = [{'tipo': t, 'objetivo': o, 'xp': x, 'monedas': c} for t, o, x, c in ACTIONS[j['id']]]
        j['misiones'] = MISSIONS[j['id']] + MISSIONS_HIGH.get(j['id'], [])
        check('oficio ' + j['id'], j['misiones'])
    scale(lv)
    for j in d['oficios']:
        scale(j['acciones'])
        scale(j['misiones'])
    with open(JOBS_SRC, 'w', encoding='utf-8') as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write('\n')
    return sum(len(j['misiones']) for j in d['oficios'])


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


# (1.3.143, el dueño: «agrega un colchón de 2 meses de misiones, no idénticas») cada día tocan 3 diarias por jugador:
# con ~190 plantillas pasan más de 2 meses antes de que se repita ninguna (y entonces con más cantidad y premio).
DAILY_MORE = [
    # minería y excavación
    d('granito', 'Granito rosado', 'Rompe {n} de granito', 'romper', 'minecraft:granite', 96, 10, 'minecraft:granite'),
    d('diorita', 'Diorita', 'Rompe {n} de diorita', 'romper', 'minecraft:diorite', 96, 10, 'minecraft:diorite'),
    d('andesita', 'Andesita', 'Rompe {n} de andesita', 'romper', 'minecraft:andesite', 96, 10, 'minecraft:andesite'),
    d('toba', 'Toba volcánica', 'Rompe {n} de toba', 'romper', 'minecraft:tuff', 96, 12, 'minecraft:tuff'),
    d('calcita', 'Calcita blanca', 'Rompe {n} de calcita', 'romper', 'minecraft:calcite', 48, 14, 'minecraft:calcite'),
    d('goteo', 'Cuevas de goteo', 'Rompe {n} bloques de espeleotema', 'romper', 'minecraft:dripstone_block', 48, 14, 'minecraft:pointed_dripstone'),
    d('amatista', 'Geoda', 'Rompe {n} racimos de amatista', 'romper', 'minecraft:amethyst_cluster', 4, 18, 'minecraft:amethyst_shard'),
    d('esmeralda', 'Montaña de esmeraldas', 'Saca {n} menas de esmeralda', 'romper', '#minecraft:emerald_ores', 2, 30, 'minecraft:emerald'),
    d('piedra2', 'Cantera grande', 'Rompe {n} piedras', 'romper', '#minecraft:base_stone_overworld', 384, 18, 'minecraft:iron_pickaxe'),
    d('pizarra2', 'Profundidades', 'Rompe {n} de pizarra profunda', 'romper', 'minecraft:deepslate', 256, 20, 'minecraft:cobbled_deepslate'),
    d('carbon2', 'Carbonera', 'Saca {n} menas de carbón', 'romper', '#minecraft:coal_ores', 64, 20, 'minecraft:coal_block'),
    d('hierro2', 'Pico de hierro', 'Saca {n} menas de hierro', 'romper', '#minecraft:iron_ores', 32, 26, 'minecraft:iron_pickaxe'),
    d('cobre2', 'Cobre a montones', 'Saca {n} menas de cobre', 'romper', '#minecraft:copper_ores', 48, 22, 'minecraft:copper_block'),
    d('nieve', 'Quitanieves', 'Rompe {n} bloques de nieve', 'romper', 'minecraft:snow_block', 64, 12, 'minecraft:snowball'),
    d('hielo', 'Bloques de hielo', 'Rompe {n} de hielo compacto', 'romper', 'minecraft:packed_ice', 32, 14, 'minecraft:packed_ice'),
    d('arenaroja', 'Mesa roja', 'Excava {n} de arena roja', 'romper', 'minecraft:red_sand', 96, 14, 'minecraft:red_sand'),
    d('barro', 'Ciénaga', 'Excava {n} de barro', 'romper', 'minecraft:mud', 64, 14, 'minecraft:mud'),
    d('podzol', 'Suelo de taiga', 'Excava {n} de podzol', 'romper', 'minecraft:podzol', 48, 14, 'minecraft:podzol'),
    d('micelio', 'Isla champiñón', 'Excava {n} de micelio', 'romper', 'minecraft:mycelium', 32, 16, 'minecraft:mycelium'),
    d('hierba', 'Prados', 'Excava {n} bloques de hierba', 'romper', 'minecraft:grass_block', 96, 10, 'minecraft:grass_block'),
    d('musgo', 'Musgo', 'Rompe {n} bloques de musgo', 'romper', 'minecraft:moss_block', 32, 12, 'minecraft:moss_block'),
    d('arenisca', 'Desierto', 'Rompe {n} de arenisca', 'romper', 'minecraft:sandstone', 64, 12, 'minecraft:sandstone'),
    d('terracota', 'Badlands', 'Rompe {n} de terracota', 'romper', '#minecraft:terracotta', 64, 14, 'minecraft:terracotta'),
    d('pala', 'Pala incansable', 'Excava {n} bloques con pala', 'romper', '#minecraft:mineable/shovel', 256, 16, 'minecraft:iron_shovel'),
    # Nether y End
    d('netherrack', 'Rocas del Nether', 'Rompe {n} de netherrack', 'romper', 'minecraft:netherrack', 192, 14, 'minecraft:netherrack'),
    d('negra', 'Piedra negra', 'Rompe {n} de piedra negra', 'romper', 'minecraft:blackstone', 96, 15, 'minecraft:blackstone'),
    d('basalto', 'Deltas de basalto', 'Rompe {n} de basalto', 'romper', 'minecraft:basalt', 96, 15, 'minecraft:basalt'),
    d('almas', 'Valle de las almas', 'Excava {n} de arena de almas', 'romper', 'minecraft:soul_sand', 64, 15, 'minecraft:soul_sand'),
    d('tierraalmas', 'Tierra de almas', 'Excava {n} de tierra de almas', 'romper', 'minecraft:soul_soil', 64, 15, 'minecraft:soul_soil'),
    d('cuarzo', 'Cuarzo del Nether', 'Saca {n} menas de cuarzo', 'romper', 'minecraft:nether_quartz_ore', 32, 20, 'minecraft:quartz'),
    d('oronether', 'Oro del Nether', 'Saca {n} menas de oro del Nether', 'romper', 'minecraft:nether_gold_ore', 32, 18, 'minecraft:gold_nugget'),
    d('piedrabrillo', 'Piedra luminosa', 'Rompe {n} de piedra luminosa', 'romper', 'minecraft:glowstone', 32, 18, 'minecraft:glowstone_dust'),
    d('magma', 'Bloques de magma', 'Rompe {n} bloques de magma', 'romper', 'minecraft:magma_block', 24, 16, 'minecraft:magma_block'),
    d('carmesi', 'Bosque carmesí', 'Tala {n} tallos carmesí', 'romper', 'minecraft:crimson_stem', 48, 16, 'minecraft:crimson_stem'),
    d('distorsionado', 'Bosque distorsionado', 'Tala {n} tallos distorsionados', 'romper', 'minecraft:warped_stem', 48, 16, 'minecraft:warped_stem'),
    d('endpiedra', 'Isla del End', 'Rompe {n} de piedra del End', 'romper', 'minecraft:end_stone', 128, 20, 'minecraft:end_stone'),
    d('obsidiana', 'Obsidiana', 'Rompe {n} de obsidiana', 'romper', 'minecraft:obsidian', 12, 20, 'minecraft:obsidian'),
    d('escombros', 'Restos antiguos', 'Encuentra {n} escombros ancestrales', 'romper', 'minecraft:ancient_debris', 2, 40, 'minecraft:ancient_debris'),
    # árboles
    d('mangle', 'Manglar', 'Tala {n} troncos de mangle', 'romper', 'minecraft:mangrove_log', 48, 15, 'minecraft:mangrove_log'),
    d('troncos2', 'Aserradero', 'Tala {n} troncos', 'romper', '#minecraft:logs', 256, 24, 'minecraft:diamond_axe'),
    d('hojas', 'Poda', 'Rompe {n} hojas', 'romper', '#minecraft:leaves', 128, 10, 'minecraft:oak_leaves'),
    d('champinones', 'Champiñones gigantes', 'Rompe {n} bloques de champiñón', 'romper', 'minecraft:red_mushroom_block', 32, 14, 'minecraft:red_mushroom'),
    # cosechas
    d('cacao', 'Cacao', 'Cosecha {n} vainas de cacao maduras', 'cosechar', 'minecraft:cocoa', 24, 14, 'minecraft:cocoa_beans'),
    d('bayas', 'Bayas dulces', 'Cosecha {n} arbustos de bayas maduros', 'cosechar', 'minecraft:sweet_berry_bush', 24, 14, 'minecraft:sweet_berries'),
    d('cactus', 'Cactus', 'Cosecha {n} cactus', 'cosechar', 'minecraft:cactus', 48, 12, 'minecraft:cactus'),
    d('bambu2', 'Bambusal', 'Cosecha {n} de bambú', 'cosechar', 'minecraft:bamboo', 96, 12, 'minecraft:bamboo'),
    d('trigo2', 'Granero', 'Cosecha {n} trigos maduros', 'cosechar', 'minecraft:wheat', 128, 20, 'minecraft:hay_block'),
    d('zanahorias2', 'Huerto grande', 'Cosecha {n} zanahorias maduras', 'cosechar', 'minecraft:carrots', 128, 20, 'minecraft:golden_carrot'),
    d('patatas2', 'Saco de patatas', 'Cosecha {n} patatas maduras', 'cosechar', 'minecraft:potatoes', 128, 20, 'minecraft:baked_potato'),
    d('cultivos', 'Campos llenos', 'Cosecha {n} cultivos maduros', 'cosechar', '#minecraft:crops', 160, 22, 'minecraft:composter'),
    d('melones2', 'Melonar', 'Cosecha {n} melones', 'cosechar', 'minecraft:melon', 64, 20, 'minecraft:glistering_melon_slice'),
    d('calabazas2', 'Feria de calabazas', 'Cosecha {n} calabazas', 'cosechar', 'minecraft:pumpkin', 64, 20, 'minecraft:carved_pumpkin'),
    d('cana2', 'Azucarera', 'Cosecha {n} cañas de azúcar', 'cosechar', 'minecraft:sugar_cane', 160, 20, 'minecraft:sugar'),
    d('verruga2', 'Bodega del Nether', 'Cosecha {n} verrugas maduras', 'cosechar', 'minecraft:nether_wart', 96, 24, 'minecraft:nether_wart_block'),
    # combate
    d('zombis2', 'Horda', 'Derrota {n} zombis', 'matar', 'minecraft:zombie', 30, 22, 'minecraft:zombie_head'),
    d('esqueletos2', 'Cementerio', 'Derrota {n} esqueletos', 'matar', 'minecraft:skeleton', 25, 22, 'minecraft:skeleton_skull'),
    d('aranas2', 'Nido', 'Derrota {n} arañas', 'matar', 'minecraft:spider', 20, 20, 'minecraft:spider_eye'),
    d('cueva', 'Arañas de cueva', 'Derrota {n} arañas de cueva', 'matar', 'minecraft:cave_spider', 10, 18, 'minecraft:cobweb'),
    d('husks', 'Momias del desierto', 'Derrota {n} husks', 'matar', 'minecraft:husk', 10, 18, 'minecraft:sand'),
    d('strays', 'Errantes del hielo', 'Derrota {n} strays', 'matar', 'minecraft:stray', 8, 18, 'minecraft:tipped_arrow'),
    d('brujas', 'Caza de brujas', 'Derrota {n} brujas', 'matar', 'minecraft:witch', 3, 22, 'minecraft:glass_bottle'),
    d('fantasmas', 'Cielo nocturno', 'Derrota {n} phantoms', 'matar', 'minecraft:phantom', 4, 20, 'minecraft:phantom_membrane'),
    d('saqueadores', 'Patrulla', 'Derrota {n} saqueadores', 'matar', 'minecraft:pillager', 6, 22, 'minecraft:crossbow'),
    d('aldeanoszombi', 'Pueblo maldito', 'Derrota {n} aldeanos zombi', 'matar', 'minecraft:zombie_villager', 4, 18, 'minecraft:emerald'),
    d('lepismas', 'Bichos de piedra', 'Derrota {n} lepismas', 'matar', 'minecraft:silverfish', 10, 16, 'minecraft:stone_bricks'),
    d('guardianes', 'Templo del mar', 'Derrota {n} guardianes', 'matar', 'minecraft:guardian', 6, 26, 'minecraft:prismarine_shard'),
    d('blazes', 'Fuego del Nether', 'Derrota {n} blazes', 'matar', 'minecraft:blaze', 6, 26, 'minecraft:blaze_rod'),
    d('cubosmagma', 'Magma', 'Derrota {n} cubos de magma', 'matar', 'minecraft:magma_cube', 8, 22, 'minecraft:magma_cream'),
    d('ghasts', 'Lágrimas', 'Derrota {n} ghasts', 'matar', 'minecraft:ghast', 2, 28, 'minecraft:ghast_tear'),
    d('wither', 'Fortaleza', 'Derrota {n} esqueletos wither', 'matar', 'minecraft:wither_skeleton', 5, 28, 'minecraft:wither_skeleton_skull'),
    d('hoglins', 'Bestias carmesíes', 'Derrota {n} hoglins', 'matar', 'minecraft:hoglin', 4, 24, 'minecraft:cooked_porkchop'),
    d('piglinszombi', 'Piglins zombi', 'Derrota {n} piglins zombi', 'matar', 'minecraft:zombified_piglin', 15, 18, 'minecraft:gold_nugget'),
    d('piglins', 'Piglins', 'Derrota {n} piglins', 'matar', 'minecraft:piglin', 8, 22, 'minecraft:gold_ingot'),
    d('endermites', 'Plaga del End', 'Derrota {n} endermites', 'matar', 'minecraft:endermite', 3, 18, 'minecraft:ender_eye'),
    d('shulkers', 'Cajas del End', 'Derrota {n} shulkers', 'matar', 'minecraft:shulker', 3, 30, 'minecraft:shulker_shell'),
    d('vindicadores', 'Mansión', 'Derrota {n} vindicadores', 'matar', 'minecraft:vindicator', 3, 28, 'minecraft:iron_axe'),
    d('monstruos2', 'Noche larga', 'Derrota {n} monstruos', 'matar', 'hostil', 80, 28, 'minecraft:diamond_sword'),
    d('creepers2', 'Silencio verde', 'Derrota {n} creepers', 'matar', 'minecraft:creeper', 15, 24, 'minecraft:creeper_head'),
    d('endermans2', 'Ojos del vacío', 'Derrota {n} endermans', 'matar', 'minecraft:enderman', 10, 28, 'minecraft:ender_eye'),
    d('ahogados2', 'Ruinas sumergidas', 'Derrota {n} ahogados', 'matar', 'minecraft:drowned', 15, 22, 'minecraft:trident'),
    d('slimes2', 'Pantano pegajoso', 'Derrota {n} slimes', 'matar', 'minecraft:slime', 20, 20, 'minecraft:slime_block'),
    # pesca
    d('globo', 'Pez globo', 'Pesca {n} peces globo', 'pescar', 'minecraft:pufferfish', 2, 16, 'minecraft:pufferfish'),
    d('tropical', 'Arrecife', 'Pesca {n} peces tropicales', 'pescar', 'minecraft:tropical_fish', 2, 16, 'minecraft:tropical_fish'),
    d('peces', 'Red llena', 'Pesca {n} peces', 'pescar', '#minecraft:fishes', 16, 18, 'minecraft:cod_bucket'),
    d('pesca2', 'Tarde de pesca', 'Pesca {n} veces', 'pescar', '*', 24, 24, 'minecraft:fishing_rod'),
    d('bacalao2', 'Lonja', 'Pesca {n} bacalaos', 'pescar', 'minecraft:cod', 16, 20, 'minecraft:cooked_cod'),
    d('salmon2', 'Río arriba', 'Pesca {n} salmones', 'pescar', 'minecraft:salmon', 10, 22, 'minecraft:cooked_salmon'),
    # horno
    d('orofundir', 'Orfebre', 'Funde {n} lingotes de oro', 'fundir', 'minecraft:gold_ingot', 16, 18, 'minecraft:gold_ingot'),
    d('cobrefundir', 'Calderero', 'Funde {n} lingotes de cobre', 'fundir', 'minecraft:copper_ingot', 32, 14, 'minecraft:copper_ingot'),
    d('lingotes2', 'Altos hornos', 'Funde {n} lingotes de hierro', 'fundir', 'minecraft:iron_ingot', 64, 24, 'minecraft:blast_furnace'),
    d('lisa', 'Losas lisas', 'Funde {n} de piedra lisa', 'fundir', 'minecraft:smooth_stone', 48, 14, 'minecraft:smooth_stone'),
    d('carbonvegetal', 'Carbón vegetal', 'Funde {n} de carbón vegetal', 'fundir', 'minecraft:charcoal', 32, 12, 'minecraft:charcoal'),
    d('cerdo', 'Chuletas', 'Cocina {n} chuletas', 'fundir', 'minecraft:cooked_porkchop', 16, 14, 'minecraft:cooked_porkchop'),
    d('pollo', 'Pollo asado', 'Cocina {n} pollos', 'fundir', 'minecraft:cooked_chicken', 16, 14, 'minecraft:cooked_chicken'),
    d('cordero', 'Cordero asado', 'Cocina {n} de cordero', 'fundir', 'minecraft:cooked_mutton', 16, 14, 'minecraft:cooked_mutton'),
    d('pescadoasado', 'Pescado asado', 'Cocina {n} salmones', 'fundir', 'minecraft:cooked_salmon', 12, 14, 'minecraft:cooked_salmon'),
    d('patatasasadas', 'Patatas asadas', 'Asa {n} patatas', 'fundir', 'minecraft:baked_potato', 32, 12, 'minecraft:baked_potato'),
    d('algas', 'Algas secas', 'Seca {n} algas', 'fundir', 'minecraft:dried_kelp', 48, 12, 'minecraft:dried_kelp'),
    d('ladrillonether', 'Ladrillo del Nether', 'Funde {n} ladrillos del Nether', 'fundir', 'minecraft:nether_brick', 48, 16, 'minecraft:nether_brick'),
    d('terracotafundir', 'Alfarería', 'Funde {n} de terracota', 'fundir', 'minecraft:terracotta', 32, 14, 'minecraft:terracotta'),
    d('tinteverde', 'Tinte verde', 'Funde {n} tintes verdes', 'fundir', 'minecraft:green_dye', 16, 12, 'minecraft:green_dye'),
    d('cristal2', 'Vidriería', 'Funde {n} de cristal', 'fundir', 'minecraft:glass', 96, 18, 'minecraft:glass_pane'),
    # mesa de trabajo
    d('palos', 'Palos', 'Fabrica {n} palos', 'fabricar', 'minecraft:stick', 64, 8, 'minecraft:stick'),
    d('tablones', 'Tablones', 'Fabrica {n} tablones', 'fabricar', '#minecraft:planks', 128, 10, 'minecraft:oak_planks'),
    d('mesas', 'Mesas de trabajo', 'Fabrica {n} mesas de trabajo', 'fabricar', 'minecraft:crafting_table', 4, 10, 'minecraft:crafting_table'),
    d('hornos', 'Hornos', 'Fabrica {n} hornos', 'fabricar', 'minecraft:furnace', 4, 12, 'minecraft:furnace'),
    d('escaleras', 'Escaleras de mano', 'Fabrica {n} escaleras de mano', 'fabricar', 'minecraft:ladder', 24, 10, 'minecraft:ladder'),
    d('puertas', 'Puertas', 'Fabrica {n} puertas de madera', 'fabricar', '#minecraft:wooden_doors', 6, 12, 'minecraft:oak_door'),
    d('vallas', 'Cercas', 'Fabrica {n} vallas de madera', 'fabricar', '#minecraft:wooden_fences', 24, 12, 'minecraft:oak_fence'),
    d('barcos', 'Barcas', 'Fabrica {n} barcas', 'fabricar', '#minecraft:boats', 2, 12, 'minecraft:oak_boat'),
    d('cubos', 'Cubos', 'Fabrica {n} cubos', 'fabricar', 'minecraft:bucket', 3, 14, 'minecraft:bucket'),
    d('faroles', 'Faroles', 'Fabrica {n} faroles', 'fabricar', 'minecraft:lantern', 6, 14, 'minecraft:lantern'),
    d('hogueras', 'Hogueras', 'Fabrica {n} hogueras', 'fabricar', 'minecraft:campfire', 3, 12, 'minecraft:campfire'),
    d('barriles', 'Barriles', 'Fabrica {n} barriles', 'fabricar', 'minecraft:barrel', 4, 12, 'minecraft:barrel'),
    d('tolvas', 'Tolvas', 'Fabrica {n} tolvas', 'fabricar', 'minecraft:hopper', 2, 18, 'minecraft:hopper'),
    d('papel', 'Papel', 'Fabrica {n} de papel', 'fabricar', 'minecraft:paper', 48, 10, 'minecraft:paper'),
    d('libros', 'Libros', 'Fabrica {n} libros', 'fabricar', 'minecraft:book', 8, 12, 'minecraft:book'),
    d('estanterias', 'Biblioteca', 'Fabrica {n} estanterías', 'fabricar', 'minecraft:bookshelf', 4, 14, 'minecraft:bookshelf'),
    d('flechas', 'Flechas', 'Fabrica {n} flechas', 'fabricar', 'minecraft:arrow', 64, 12, 'minecraft:arrow'),
    d('arcos', 'Arqueros', 'Fabrica {n} arcos', 'fabricar', 'minecraft:bow', 2, 12, 'minecraft:bow'),
    d('canas', 'Cañas de pescar', 'Fabrica {n} cañas de pescar', 'fabricar', 'minecraft:fishing_rod', 2, 10, 'minecraft:fishing_rod'),
    d('camas', 'Dormitorio', 'Fabrica {n} camas', 'fabricar', '#minecraft:beds', 2, 14, 'minecraft:red_bed'),
    d('alfombras', 'Alfombras', 'Fabrica {n} alfombras', 'fabricar', '#minecraft:wool_carpets', 24, 12, 'minecraft:red_carpet'),
    d('estandartes', 'Estandartes', 'Fabrica {n} estandartes', 'fabricar', '#minecraft:banners', 2, 14, 'minecraft:white_banner'),
    d('velas', 'Velas', 'Fabrica {n} velas', 'fabricar', '#minecraft:candles', 6, 14, 'minecraft:candle'),
    d('galletas', 'Galletas', 'Hornea {n} galletas', 'fabricar', 'minecraft:cookie', 32, 12, 'minecraft:cookie'),
    d('tartacalabaza', 'Tarta de calabaza', 'Fabrica {n} tartas de calabaza', 'fabricar', 'minecraft:pumpkin_pie', 8, 14, 'minecraft:pumpkin_pie'),
    d('pasteles', 'Pastelería', 'Fabrica {n} pasteles', 'fabricar', 'minecraft:cake', 2, 18, 'minecraft:cake'),
    d('zanahoriasoro', 'Zanahorias doradas', 'Fabrica {n} zanahorias doradas', 'fabricar', 'minecraft:golden_carrot', 8, 18, 'minecraft:golden_carrot'),
    d('piedralabrada', 'Cantero', 'Fabrica {n} ladrillos de piedra', 'fabricar', 'minecraft:stone_bricks', 64, 12, 'minecraft:stone_bricks'),
    d('pistones', 'Pistones', 'Fabrica {n} pistones', 'fabricar', 'minecraft:piston', 4, 16, 'minecraft:piston'),
    d('repetidores', 'Repetidores', 'Fabrica {n} repetidores', 'fabricar', 'minecraft:repeater', 4, 16, 'minecraft:repeater'),
    d('vagonetas', 'Vagonetas', 'Fabrica {n} vagonetas', 'fabricar', 'minecraft:minecart', 2, 16, 'minecraft:minecart'),
    d('andamios', 'Andamios', 'Fabrica {n} andamios', 'fabricar', 'minecraft:scaffolding', 24, 12, 'minecraft:scaffolding'),
    d('composteras', 'Composteras', 'Fabrica {n} composteras', 'fabricar', 'minecraft:composter', 3, 10, 'minecraft:composter'),
    d('marcos', 'Marcos', 'Fabrica {n} marcos', 'fabricar', 'minecraft:item_frame', 6, 12, 'minecraft:item_frame'),
    d('artesano2', 'Gran artesano', 'Fabrica {n} objetos', 'fabricar', '*', 256, 22, 'minecraft:crafting_table'),
    # construir
    d('tablones2', 'Suelo de madera', 'Coloca {n} tablones', 'colocar', '#minecraft:planks', 128, 14, 'minecraft:oak_planks'),
    d('troncoscolocar', 'Cabaña', 'Coloca {n} troncos', 'colocar', '#minecraft:logs', 64, 12, 'minecraft:oak_log'),
    d('lana', 'Tapices', 'Coloca {n} de lana', 'colocar', '#minecraft:wool', 64, 14, 'minecraft:white_wool'),
    d('escalerascolocar', 'Escalinatas', 'Coloca {n} escaleras', 'colocar', '#minecraft:stairs', 64, 14, 'minecraft:oak_stairs'),
    d('losas', 'Tejados', 'Coloca {n} losas', 'colocar', '#minecraft:slabs', 96, 14, 'minecraft:stone_slab'),
    d('muros', 'Murallas', 'Coloca {n} muros', 'colocar', '#minecraft:walls', 48, 14, 'minecraft:cobblestone_wall'),
    d('vallascolocar', 'Cercados', 'Coloca {n} vallas', 'colocar', '#minecraft:fences', 48, 12, 'minecraft:oak_fence'),
    d('piedralabradacolocar', 'Castillo', 'Coloca {n} ladrillos de piedra', 'colocar', 'minecraft:stone_bricks', 96, 16, 'minecraft:stone_bricks'),
    d('arenisca2', 'Templo del desierto', 'Coloca {n} de arenisca', 'colocar', 'minecraft:sandstone', 96, 14, 'minecraft:sandstone'),
    d('terracotacolocar', 'Barro cocido', 'Coloca {n} de terracota', 'colocar', '#minecraft:terracotta', 64, 16, 'minecraft:orange_terracotta'),
    d('cuarzo2', 'Palacio de cuarzo', 'Coloca {n} bloques de cuarzo', 'colocar', 'minecraft:quartz_block', 48, 18, 'minecraft:quartz_block'),
    d('flores', 'Jardinero', 'Planta {n} flores', 'colocar', '#minecraft:flowers', 16, 12, 'minecraft:poppy'),
    d('faroles2', 'Iluminación', 'Coloca {n} faroles', 'colocar', 'minecraft:lantern', 8, 12, 'minecraft:lantern'),
    d('constructor2', 'Gran obra', 'Coloca {n} bloques', 'colocar', '*', 512, 22, 'minecraft:scaffolding'),
    d('arboles2', 'Bosque nuevo', 'Planta {n} brotes', 'colocar', '#minecraft:saplings', 48, 16, 'minecraft:spruce_sapling'),
    # animales, pociones y encantamientos
    d('conejos', 'Madrigueras', 'Cría {n} conejos', 'criar', 'minecraft:rabbit', 3, 14, 'minecraft:rabbit_foot'),
    d('cabras', 'Cabras', 'Cría {n} cabras', 'criar', 'minecraft:goat', 3, 14, 'minecraft:goat_horn'),
    d('caballos', 'Cuadra', 'Cría {n} caballos', 'criar', 'minecraft:horse', 2, 16, 'minecraft:saddle'),
    d('lobos', 'Manada', 'Cría {n} lobos', 'criar', 'minecraft:wolf', 2, 16, 'minecraft:bone'),
    d('abejas', 'Colmena', 'Cría {n} abejas', 'criar', 'minecraft:bee', 2, 16, 'minecraft:honeycomb'),
    d('llamas', 'Caravana', 'Cría {n} llamas', 'criar', 'minecraft:llama', 2, 16, 'minecraft:lead'),
    d('criar', 'Granjero', 'Cría {n} animales', 'criar', '*', 10, 18, 'minecraft:wheat'),
    d('pociones2', 'Botica', 'Prepara {n} pociones', 'preparar', 'minecraft:potion', 12, 22, 'minecraft:potion'),
    d('arrojadizas', 'Granadas', 'Prepara {n} pociones arrojadizas', 'preparar', 'minecraft:splash_potion', 6, 24, 'minecraft:splash_potion'),
    d('encantar2', 'Mago', 'Encanta {n} objetos', 'encantar', '*', 5, 30, 'minecraft:enchanting_table'),
    d('reparar', 'Yunque', 'Repara {n} objetos en el yunque', 'reparar', '*', 3, 18, 'minecraft:anvil'),
]

HUNTS_MORE = [
    d('c_piglinzombi', 'Horda dorada', 'Derrota {n} piglins zombi', 'matar', 'minecraft:zombified_piglin', 30, 35, 'minecraft:golden_sword'),
    d('c_piglin2', 'Bastión', 'Derrota {n} piglins', 'matar', 'minecraft:piglin', 12, 45, 'minecraft:gold_ingot'),
    d('c_zoglin', 'Zoglins', 'Derrota {n} zoglins', 'matar', 'minecraft:zoglin', 3, 60, 'minecraft:rotten_flesh'),
    d('c_devastador', 'Asalto', 'Derrota {n} devastadores', 'matar', 'minecraft:ravager', 2, 75, 'minecraft:saddle'),
    d('c_invocador', 'Magia oscura', 'Derrota {n} invocadores', 'matar', 'minecraft:evoker', 2, 80, 'minecraft:totem_of_undying'),
    d('c_vex', 'Espíritus', 'Derrota {n} vexes', 'matar', 'minecraft:vex', 8, 50, 'minecraft:iron_sword'),
    d('c_zombi2', 'Asedio zombi', 'Derrota {n} zombis', 'matar', 'minecraft:zombie', 70, 50, 'minecraft:rotten_flesh'),
    d('c_esqueleto2', 'Lluvia de flechas', 'Derrota {n} esqueletos', 'matar', 'minecraft:skeleton', 50, 50, 'minecraft:arrow'),
    d('c_enderman2', 'Granja del End', 'Derrota {n} endermans', 'matar', 'minecraft:enderman', 20, 70, 'minecraft:ender_eye'),
    d('c_blaze2', 'Spawner de blazes', 'Derrota {n} blazes', 'matar', 'minecraft:blaze', 25, 80, 'minecraft:blaze_powder'),
    d('c_bruja2', 'Cabaña del pantano', 'Derrota {n} brujas', 'matar', 'minecraft:witch', 10, 65, 'minecraft:redstone'),
    d('c_slime2', 'Chunk de slimes', 'Derrota {n} slimes', 'matar', 'minecraft:slime', 40, 50, 'minecraft:slime_block'),
    d('c_creeper2', 'Pólvora', 'Derrota {n} creepers', 'matar', 'minecraft:creeper', 35, 65, 'minecraft:gunpowder'),
    d('c_guardian2', 'Monumento', 'Derrota {n} guardianes', 'matar', 'minecraft:guardian', 20, 75, 'minecraft:prismarine_crystals'),
    d('c_wither2', 'Calaveras', 'Derrota {n} esqueletos wither', 'matar', 'minecraft:wither_skeleton', 15, 90, 'minecraft:coal_block'),
]


def missions():
    o = {
        '_ayuda': ('Misiones del pad y Cazas (1.3.29). tipo: romper, cosechar, colocar, matar, pescar, fabricar, fundir, preparar, '
                   'criar, encantar. objetivo: un id (minecraft:zombie o mod:mob), una etiqueta (#minecraft:logs), * o, al matar, '
                   'hostil / animal. En descripcion, {n} se cambia por la cantidad. Rotan por temporadas de 90 días sin repetir '
                   'ninguna igual: cada día 3 diarias por jugador, cada semana 3 semanales (cantidad x semanalesPorCantidad, pago x '
                   'semanalesPorPago) y cada 12 h 4 cazas para todo el servidor; a lo largo de la temporada suben cantidad y premio '
                   '(x1 a x2,5). Para los mobs de tus mods, añade presas a «cazas» con su id.'),
        'version': 3,
        'economia': 3,
        'semanalesPorCantidad': 5,
        'semanalesPorPago': 4,
        'diarias': scale(json.loads(json.dumps(DAILY + DAILY_MORE))),
        'cazas': scale(json.loads(json.dumps(HUNTS + HUNTS_MORE))),
    }
    check('diarias', o['diarias'])
    check('cazas', o['cazas'])
    with open(os.path.join(RES, 'tfclient-misiones-default.json'), 'w', encoding='utf-8') as f:
        json.dump(o, f, ensure_ascii=False, indent=2)
        f.write('\n')


if __name__ == '__main__':
    n = jobs()
    missions()
    print(f'oficios: {n} misiones; misiones del pad: {len(DAILY + DAILY_MORE)} diarias y {len(HUNTS + HUNTS_MORE)} cazas')
