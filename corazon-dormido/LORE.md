# Corazón Dormido: la biblia del mundo

Este documento manda sobre el arte, los nombres, los textos y los jefes. Si algo del juego no encaja con esto, se
cambia el juego.

## 1. Tono

**Fantasía oscura bajo tierra.** Un mundo hermoso que se está pudriendo. La belleza la pone la luz: hongos que
brillan, cristales, la antorcha del jugador. Lo que vive en la oscuridad no es simpático: tiene hambre, está
enfermo o es algo que ya murió y sigue andando.

- El miedo viene de lo que se intuye: ojos que brillan en lo oscuro antes de ver el cuerpo, siluetas encorvadas y
  ruidos de cosas que se arrastran.
- Nada de ojos de dibujo animado, caras redondas ni colores de caramelo. Los ojos son puntos de luz, racimos de
  ojos o nada (criaturas ciegas que buscan con la boca).
- Sin gore gratuito: huesos, dientes, carne enferma y podredumbre sí; tripas y sangre a chorros no.
- El jugador es lo único cálido del mundo. Por eso su luz importa.

## 2. La historia

**Ásor.** Antes del mundo cayó Ásor, el Primer Titán. Su cuerpo se hundió en la roca y la roca se hizo con él:
- las raíces son sus venas;
- la arcilla roja es su carne;
- los cristales son su sangre seca.

En el centro quedó su **Corazón**, que no dejó de latir. Cada latido manda luz por la piedra, y de esa luz vive
todo lo que vive aquí abajo.

**Los Veladores.** Un pueblo que aprendió a vivir del latido levantó el **Santuario** alrededor del Corazón y lo
cuidó durante mil años. Grababan su historia en tablillas de piedra. Tres criaturas les servían:
- el **Topo Rey** abría los túneles;
- la **Madre de la Colmena** limpiaba la carne muerta del dios;
- el sumo sacerdote **Ozmar** rezaba al Corazón.

**La Podre.** Del fondo, donde el latido no llega, subió **la Podre**: un hongo negro y morado que no come carne,
come **luz**. Lo que toca no muere; se queda vacío por dentro y sigue andando con hambre. Se reconoce siempre igual:
bultos negros con poros que brillan en **magenta**. En el juego, el magenta es la Podre y la Podre es peligro.

**La caída.** Ozmar vio que la Podre avanzaba. Para salvar al Corazón, quiso darle de comer la propia Podre, como
quien vacuna a un enfermo. El Corazón enfermó y se durmió. Sin latido, la Podre lo cubrió todo:
- los tres servidores se pudrieron y se volvieron sus guardianes;
- los Veladores que quedaron bajaron a la oscuridad, se olvidaron de la luz y se volvieron **grutos**, que hoy
  adoran a la Podre y llevan como máscaras los cráneos de sus antepasados.

**El Despierto.** El último latido del Corazón hizo una sola cosa: un cuerpo nuevo en el altar. Eres tú. No
recuerdas nada porque no hay nada que recordar; naciste para despertarlo. Cada guardián lleva dentro un
**cristal**, un trozo del latido que la Podre le robó. Matarlo libera el cristal; devolverlo al Corazón le
devuelve un latido.

**La Gran Muralla.** Las tablillas del interior dicen que los Veladores levantaron la Muralla para proteger el
Corazón. Las tablillas de fuera cuentan otra cosa, demasiado tarde: la Muralla no protegía el Corazón, **encerraba
lo que nació de la sangre de Ásor cuando cayó**, los **Titanes Menores**. Cuando el Corazón vuelve a latir y tú
derribas la Muralla, los despiertas.

**Los Titanes Menores**, uno por cada sector del anillo exterior:
- **Hiedra, la Flor de Sangre** (Selva Esmeralda): lo que brotó donde la sangre de Ásor empapó la tierra. Se
  alimenta de cadáveres y guarda en su centro el cuerpo del Velador que intentó quemarla.
- **Tamar, el Ojo del Cielo** (Selva Esmeralda): el ojo de Ásor, que salió volando al caer. Un ave de hueso y
  plumas rotas que lleva siglos sin posarse.
- Fase 3: **el Ahogado** (Mar Hundido), el aliento de Ásor, que se quedó bajo el agua.
- Fase 4: **la Sed** (Desierto del Origen), lo que quedó de su piel al sol.

**El final.** Con los titanes muertos y el Corazón despierto del todo, el latido llega hasta el cuerpo de Ásor.
Lo que pasa entonces se deja para la última fase. La pregunta del juego: ¿despertar al dios es salvar el mundo
o terminarlo?

## 3. Cómo se cuenta (sin muros de texto)

- **Tablillas** de los Veladores repartidas por el mundo, de una o dos frases. Las del interior mienten sin
  querer; las del exterior dicen la verdad.
- **Presentación de cada jefe** al despertar: su nombre, su título y una frase.
- **Bestiario**: cada criatura que matas desbloquea su ficha con una línea de historia.
- **El Corazón habla** con una voz corta y rara cuando le devuelves un cristal.
- **Lo que se ve**: restos de Veladores, altares rotos, grutos rezando ante bultos de Podre.

## 4. Dirección de arte

- **Silueta primero.** Encorvadas, angulosas y asimétricas, con extremidades largas, dientes y garras. Cada
  criatura se reconoce solo por su sombra.
- **Paleta.** Oscuros fríos y apagados, y carne enferma: gris verdoso, morado de moretón, hueso amarillento. Los
  acentos brillan solos:
  - magenta de la Podre (`#ff3cb4`);
  - rojo de ojos (`#ff3020`);
  - verde bilis del ácido (`#a8f040`);
  - ámbar de los grutos (`#ffb030`).
- **Ojos.** Puntos que brillan en la oscuridad (capa `_luz`): se ven antes que el cuerpo.
- **La marca de la Podre.** Toda criatura corrompida lleva bultos negros con poros magenta que brillan. Más Podre,
  más fuerte.
- **Escala.** Grutos de la altura del jugador pero encorvados; brutos el doble; jefes de 3 a 6 casillas.
- **Movimiento.** Nervioso y a tirones; respiración en reposo; avisos de ataque claros pero amenazantes.

## 5. Bestiario (los ids del código no cambian; cambian el nombre, el aspecto y la ficha)

| id | Nombre | Cómo es | Ficha |
|---|---|---|---|
| babosa | **Baba carroñera** | Masa de baba amarillenta y medio transparente con huesos y un cráneo asomando; ojillos rojos flotando dentro. | «Se arrastra sobre los muertos y los disuelve despacio. Lo que asoma de ella es lo último que se comió.» |
| babosa_roja | **Baba sangrante** | Igual pero roja oscura, con vísceras; al golpearla se parte. | «Cuando se parte, cada mitad sigue teniendo hambre.» |
| babosa_veneno | **Pústula** | Morada y negra, cubierta de bultos que revientan en veneno. | «La Podre la llenó hasta reventar. Literalmente.» |
| seton | **Portador** | Cadáver de minero encorvado; de la espalda le sale un hongo enorme que le tapa la cabeza; cuencas vacías. | «Ya no es un minero. Es la maceta de algo.» |
| larva | **Gusano roedor** | Gusano pálido, ciego y venoso; se alza y abre una boca redonda llena de dientes. | «No tiene ojos. No los necesita.» |
| larva_grande | **Gusano escupidor** | Hinchado, con sacos de ácido que brillan verdes bajo la piel. | «Lo que escupe ya ha disuelto a otros antes que a ti.» |
| larva_moho | **Gusano de Podre** | Ennegrecido, con bultos y poros magenta. | «La Podre también se come a los gusanos. Luego los usa.» |
| gruto_lancero | **Gruto lancero** | Flaco, gris verdoso y encorvado; máscara de cráneo de animal con ojos ámbar; lanza con punta de hueso. | «Llevan la cara de sus abuelos para que la luz no los reconozca.» |
| gruto_chaman | **Gruto vidente** | Máscara de cráneo con cuernos, pieles, bastón con un bulto de Podre enjaulado. | «Reza a lo que se comió a su pueblo. Y lo que se lo comió le contesta.» |
| gruto_bruto | **Gruto mole** | Enorme y cosido, mandíbula de hierro, cuchilla oxidada. | «Lo engordan con lo que cazan los demás. Solo sirve para una cosa.» |
| gruto_jardinero | **Sembrador** | Encapuchado de hojas, saco de semillas de Podre. | «Allí donde siembra, la tierra se pone morada a la semana.» |
| gruto_cazador | **Acechador** | Capa de musgo, arco de hueso, ojos ámbar entre las hojas. | «Si lo ves, es que ya ha disparado.» |
| planta_trampa | **Fauce** | Tallo con una boca vertical de dos filas de dientes que babea. | «Las flores de la Selva aprendieron de Hiedra a comer carne.» |
| tentaculo_moho | **Brazo de la Podre** | Tentáculo negro con ojos a lo largo, saliendo de una pústula del suelo. | «No es un animal. Es un dedo de algo mucho más grande.» |
| avispa | **Aguijonera** | Avispa grande con cara de calavera y alas venosas. | «Su nido zumba como una voz que reza.» |
| escarabajo_musgo | **Lápida** | Escarabajo con un caparazón como una lápida cubierta de musgo; ojos rojos. | «Los Veladores enterraban a sus muertos bajo estas piedras. Las piedras se levantaron.» |

## 6. Los jefes

| id | Nombre | Título | Cómo es | Frase al despertar |
|---|---|---|---|---|
| topo | **Murdo** | el Rey Ciego | Topo colosal, calvo a parches, sin ojos; nariz de estrella de tentáculos; corona de clavos de hierro hundidos en el cráneo; garras de pala. | «Cavé la carne de un dios durante mil años. Ahora solo oigo tu corazón.» |
| reina | **Vhessa** | Madre de la Colmena | Gusana reina hinchada; saco de huevos translúcido con larvas que se mueven dentro; racimo de ojos negros, mandíbulas. | «Todo lo muerto es mío. Pronto tú también.» |
| ozmar | **Ozmar** | el Último Velador | Cáscara flotante en túnica de sacerdote; dentro solo Podre; máscara de porcelana rota con un ojo; brazos largos como raíces. | «Lo hice para salvarlo. Lo hice para salvarlo. Lo hice…» |
| hiedra | **Hiedra** | la Flor de Sangre | Flor gigante de pétalos de carne; en el centro, el cadáver del Velador que intentó quemarla; látigos de espinas con bocas. | «Él quiso quemarme. Ahora es mi corazón.» |
| tamar | **Tamar** | el Ojo del Cielo | Ave colosal de hueso y plumas rotas; un solo ojo enorme en el pecho; pico de calavera. | «Lo he visto todo desde arriba. Te he visto a ti.» |

## 7. Lo que hay que cambiar en el juego

1. Modelos 3D de todas las criaturas y jefes con esta dirección (escultor), con la capa de luz de ojos y Podre.
2. Nombres nuevos en `CRIATURAS` y `JEFES`, presentación del jefe (nombre, título y frase).
3. Tablillas reescritas como historia (las de ahora son consejos de juego: esos pasan a «consejos»).
4. Bestiario en la mochila.
5. Grutos rezando ante bultos de Podre, restos de Veladores y altares rotos en el mundo.
