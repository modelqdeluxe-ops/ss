"""Textos legales de la web: aviso legal, términos y condiciones y aviso de privacidad.

Cada documento es una lista de secciones (id, título, HTML). tools/pages.py los convierte en /legal, /terminos y
/privacidad. Si cambia algo de lo que hace la web (datos que guarda, servicios externos, productos), hay que
actualizarlo aquí y cambiar LEGAL_DATE en pages.py.
"""

EMAIL = 'tierrasfantasticasmc@gmail.com'
MAIL = f'<a href="mailto:{EMAIL}">{EMAIL}</a>'
OWNER = 'el Equipo de Tierras Fantásticas'

# --- Aviso legal: quiénes somos, no afiliación y cómo funciona la tienda ---
AVISO = [
    ('quienes', 'Quiénes somos', f'''
<p>Tierras Fantásticas es un <b>servidor de Minecraft independiente</b> gestionado por {OWNER}, desde México. Esta
web (tierrasfantásticas.store) informa sobre el servidor y tiene una tienda cuyos ingresos se usan para mantenerlo:
el alojamiento, el desarrollo de sus mods y su mantenimiento.</p>
<p>Contacto: {MAIL}. Puedes escribirnos sin crear ninguna cuenta ni entrar a Discord.</p>'''),

    ('no-afiliacion', 'Sin relación con Mojang ni Microsoft', '''
<div class="legal-callout">
  <p><b>No es un producto oficial de Minecraft. No está aprobado por Mojang ni Microsoft ni asociado con ellos.</b></p>
  <p lang="en"><b>NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.</b></p>
</div>
<p>Minecraft es una marca de Mojang AB, empresa de Microsoft. Tierras Fantásticas no pertenece a Mojang ni a
Microsoft, no trabaja para ellas y no tiene ningún acuerdo con ellas. Usamos la palabra «Minecraft» solo para decir
con qué juego es compatible el servidor; no usamos su logotipo y nuestro nombre no empieza por «Minecraft».</p>
<p>Para jugar en el servidor necesitas tu propia copia original de <i>Minecraft: Java Edition</i>, comprada a
Mojang. Mojang y Microsoft no atienden consultas, pagos ni reclamaciones de este servidor: escríbenos a nosotros.</p>'''),

    ('tienda', 'Cómo funciona la tienda', f'''
<ul>
  <li><b>Sin cajas sorpresa ni juegos de azar.</b> Cada crate tiene un contenido fijo que se ve completo antes de
    comprar (puedes probártelo en 3D). No vendemos llaves, ruletas ni premios al azar.</li>
  <li><b>Las monedas del servidor se ganan jugando.</b> No se venden con dinero, no tienen valor real y no se pueden
    cambiar por dinero ni por nada fuera del servidor.</li>
  <li><b>No vendemos</b> desbaneos, quitar silencios, herramientas del staff ni partes del servidor: el mundo y sus
    zonas son para todos por igual. Entrar al servidor es gratis.</li>
  <li><b>Contenido para todas las edades.</b></li>
</ul>
<p>Si tienes dudas sobre algo de la tienda, escríbenos a {MAIL}.</p>'''),

    ('propiedad', 'Propiedad intelectual', f'''
<p>El nombre, el emblema, los textos y el diseño de Tierras Fantásticas son del equipo del servidor. Las marcas y
contenidos de terceros que aparecen en la web o en el juego (mods, modelos, texturas, tipografías) pertenecen a sus
autores. Si eres autor de algún contenido y crees que se usa sin tu permiso, escríbenos a {MAIL} y lo revisaremos
enseguida.</p>'''),

    ('responsabilidad', 'Disponibilidad y responsabilidad', '''
<p>Hacemos lo posible por que el servidor y la web funcionen siempre, pero puede haber cortes, mantenimientos o
fallos. El contenido del servidor puede cambiar con el tiempo (por equilibrio del juego, por actualizaciones o para
seguir las normas de Mojang). Lo que pasa con tus compras en esos casos está en los
<a href="/terminos">Términos y condiciones</a>.</p>'''),

    ('ley', 'Ley aplicable', '''
<p>Esta web se rige por las leyes de los Estados Unidos Mexicanos. Los detalles de las compras están en los
<a href="/terminos">Términos y condiciones</a> y el uso de tus datos, en el <a href="/privacidad">Aviso de
privacidad</a>.</p>'''),
]

# --- Términos y condiciones de uso y de compra ---
TERMINOS = [
    ('aceptacion', 'Aceptación', f'''
<p>Estos términos regulan el uso de esta web, de las cuentas y de la tienda de Tierras Fantásticas, un servidor de
Minecraft independiente gestionado por {OWNER} (México, {MAIL}). Al crear una cuenta o comprar, los aceptas. Si no
estás de acuerdo, no uses la tienda. Jugar en el servidor es gratis y no necesita cuenta en la web.</p>
<p>Tierras Fantásticas no tiene relación con Mojang ni Microsoft. Más información en el <a href="/legal">Aviso
legal</a>.</p>'''),

    ('menores', 'Menores de edad', '''
<p>Si eres menor de edad, necesitas el permiso de tu madre, padre o tutor para crear una cuenta y para comprar, y
son ellos quienes aceptan estos términos y pagan. Si tienes menos de 13 años, tu madre, padre o tutor debe crear la
cuenta por ti. Si un menor compró sin permiso, escríbenos y lo resolvemos (ver «Cancelaciones y reembolsos»).</p>'''),

    ('cuentas', 'Cuentas', '''
<ul>
  <li>La cuenta se crea con tu nombre de Minecraft (tienes que haber entrado al servidor al menos una vez) y una
    contraseña. Un jugador, una cuenta.</li>
  <li>Cuida tu contraseña: lo que se haga con tu cuenta es responsabilidad tuya. Si crees que alguien entró, cámbiala
    en «Mi cuenta» y avísanos.</li>
  <li>Conectar Discord es opcional y sirve para darte los roles de tus rangos.</li>
  <li>Puedes pedir que borremos tu cuenta cuando quieras (ver el <a href="/privacidad">Aviso de privacidad</a>).</li>
</ul>'''),

    ('que-compras', 'Qué compras', '''
<p>Lo que se vende en la tienda es <b>contenido digital para usar dentro de Tierras Fantásticas</b>: rangos (prefijo con
color, rol de Discord y un set completo), cosméticos (piezas de aspecto: sombreros, mochilas, alas, globos y objetos de
mano, que no cambian nada más) y crates (sets completos con armas, herramientas, armadura y cosméticos). Al
comprar recibes un permiso de uso personal dentro del servidor:</p>
<ul>
  <li>No es dinero, no tiene valor fuera del servidor y no se puede cambiar por dinero, revender ni pasar a otro
    jugador ni a otro servidor.</li>
  <li>Cada producto dice antes de pagar qué incluye exactamente. Las crates tienen un contenido fijo, sin nada al azar,
    y lo puedes ver completo (y probártelo en 3D) antes de comprar.</li>
  <li>Las <b>monedas del servidor</b> solo se ganan jugando. La «Tienda de monedas» de la web se paga con esas monedas,
    nunca con dinero.</li>
  <li>Las <b>recompensas gratis</b> se pueden reclamar una vez por jugador, con la cuenta iniciada.</li>
</ul>'''),

    ('precios', 'Precios y pago', '''
<ul>
  <li>Los precios están en <b>dólares estadounidenses (USD)</b> y el precio que ves antes de pagar es el total que
    pagas: no se añade nada después. Tu banco puede cobrarte por convertir la moneda; eso depende de tu banco.</li>
  <li>Si ya tienes un rango y compras uno superior, pagas solo la diferencia. No se puede comprar un rango que ya
    tienes o uno inferior.</li>
  <li>El pago se hace en la página segura de <b>Stripe</b> (tarjeta, Apple Pay, Google Pay o Link). Nosotros nunca vemos
    ni guardamos los datos de tu tarjeta.</li>
  <li>Antes de cobrar, la tienda comprueba que el nombre de jugador existe en el servidor y guarda la compra con su
    UUID, para que llegue al jugador que eliges. Revisa bien el nombre: si te equivocas de jugador, escríbenos.</li>
</ul>'''),

    ('entrega', 'Entrega', '''
<p>Cuando Stripe confirma el pago, el servidor te entrega la compra automáticamente: en unos segundos si estás
conectado y, si no, en cuanto entres. Verás un aviso en el juego y el estado en la página de tu compra y en «Mi
cuenta». Si algo falla, el staff la entrega a mano. Si en 48 horas no la has recibido, escríbenos con tu número de
pedido.</p>'''),

    ('reembolsos', 'Cancelaciones y reembolsos', f'''
<ul>
  <li><b>5 días hábiles para cancelar.</b> Puedes cancelar cualquier compra y pedir que te devolvamos el dinero dentro de
    los 5 días hábiles siguientes a la entrega, sin dar explicaciones. Escríbenos a {MAIL} con tu número de pedido y tu
    nombre de jugador. Al cancelarla, retiramos del juego lo que se te entregó.</li>
  <li><b>En cualquier momento</b> te devolvemos el dinero si no recibimos la compra en el servidor, si se cobró dos veces
    o por error, o si un menor compró sin permiso de su madre, padre o tutor.</li>
  <li>Respondemos en un máximo de 5 días hábiles. El reembolso se hace por Stripe al mismo medio de pago; tu banco puede
    tardar de 5 a 10 días hábiles en mostrarlo.</li>
  <li>Si abres una disputa o contracargo con tu banco en lugar de escribirnos, lo entregado se suspende mientras se
    resuelve. Escríbenos primero: casi siempre es más rápido.</li>
  <li>Las recompensas gratis y lo comprado con monedas del servidor no tienen reembolso en dinero, porque no se pagaron
    con dinero.</li>
</ul>'''),

    ('cambios-contenido', 'Cambios en el servidor', '''
<p>El servidor puede cambiar: actualizaciones, equilibrio del juego o las normas de Mojang. Si tenemos que retirar o
cambiar algo que compraste, te ofreceremos algo equivalente de valor similar. Si algún día el servidor fuera a cerrar,
lo avisaremos con al menos 30 días de antelación en la web y en Discord, y dejaremos de vender en cuanto se decida.</p>'''),

    ('normas', 'Normas y sanciones', '''
<p>Dentro del servidor se aplican sus <a href="/mundo#normas">normas</a>. Comprar no da derecho a saltárselas ni
protege de las sanciones, y el staff no vende desbaneos ni favores. Si te sancionan por romperlas, lo comprado no se
reembolsa, salvo lo que diga la ley o lo indicado en «Cancelaciones y reembolsos».</p>
<p>No se permite usar la web para estafar, suplantar a otro jugador, atacar el servidor o intentar saltarse las
protecciones de la tienda.</p>'''),

    ('responsabilidad', 'Responsabilidad', '''
<p>Prestamos el servicio con cuidado, pero no podemos garantizar que el servidor o la web estén siempre disponibles ni
libres de fallos. No respondemos de problemas causados por tu conexión, tus mods o tu equipo, ni por servicios de
terceros (Stripe, Discord, tu banco). Nada de esto limita los derechos que te da la ley como consumidor.</p>'''),

    ('cambios', 'Cambios en estos términos', '''
<p>Si cambiamos estos términos, publicaremos la versión nueva aquí con su fecha. Las compras se rigen por los
términos vigentes el día en que las hiciste.</p>'''),

    ('ley', 'Ley aplicable y reclamaciones', f'''
<p>Estos términos se rigen por las leyes de los Estados Unidos Mexicanos, incluida la Ley Federal de Protección al
Consumidor. Si tienes un problema, escríbenos primero a {MAIL} e intentaremos resolverlo. También puedes acudir a la
Procuraduría Federal del Consumidor (PROFECO), por ejemplo por Concilianet.</p>'''),
]

# --- Aviso de privacidad (Ley Federal de Protección de Datos Personales en Posesión de los Particulares, 2025) ---
PRIVACIDAD = [
    ('responsable', 'Quién es el responsable', f'''
<p>{OWNER[0].upper() + OWNER[1:]} (Tierras Fantásticas), con domicilio en México, es responsable del tratamiento de tus
datos personales. Para cualquier asunto sobre tus datos, el medio de contacto es {MAIL}.</p>'''),

    ('datos', 'Qué datos tratamos', '''
<p>Solo los que la web necesita para funcionar. <b>No tratamos datos sensibles</b> y no te pedimos tu nombre real,
dirección ni teléfono.</p>
<ul>
  <li><b>Tu cuenta:</b> nombre de jugador de Minecraft y su UUID, la contraseña (guardada cifrada con PBKDF2: nadie
    puede leerla, ni nosotros) y las fechas de alta y de cambios.</li>
  <li><b>Discord, solo si lo conectas:</b> tu ID, nombre de usuario, nombre visible, avatar y si estás en nuestro servidor
    de Discord.</li>
  <li><b>Compras:</b> número de pedido, producto, cantidad, importe, moneda, fechas, estado del pago y de la entrega, y el
    identificador del pago en Stripe. Los datos de tu tarjeta y el correo que te pide Stripe los trata Stripe; nosotros
    no los vemos ni los guardamos.</li>
  <li><b>Jugadores del servidor:</b> el servidor nos envía el nombre y el UUID de quienes han entrado, con la primera y
    la última vez que se les vio, para comprobar los nombres antes de vender.</li>
  <li><b>Seguridad:</b> intentos fallidos de inicio de sesión, para frenar a quien prueba contraseñas.</li>
  <li><b>Técnicos:</b> nuestro proveedor de alojamiento (Cloudflare) procesa tu dirección IP y datos de navegación para
    servir la web y protegerla de ataques. Nosotros no guardamos tu IP.</li>
</ul>'''),

    ('finalidades', 'Para qué los usamos', f'''
<p><b>Finalidades necesarias</b> (sin ellas no podemos darte el servicio):</p>
<ul>
  <li>Crear y mantener tu cuenta e iniciar tu sesión.</li>
  <li>Comprobar que el jugador existe, procesar tus compras y entregarlas en el juego.</li>
  <li>Darte en Discord el rol de tu rango, si conectas Discord.</li>
  <li>Atender tus mensajes, cancelaciones, reembolsos y reclamaciones.</li>
  <li>Proteger la web y las cuentas, y cumplir las obligaciones legales (por ejemplo, conservar los registros de las
    ventas).</li>
</ul>
<p><b>Finalidad voluntaria:</b> anunciar tu compra en el chat del juego y en nuestro Discord (con tu nombre de jugador
y lo que compraste). Si no quieres, escríbenos a {MAIL} y dejaremos de anunciar tus compras.</p>
<p>No vendemos tus datos, no los usamos para publicidad y no hacemos perfiles.</p>'''),

    ('terceros', 'Con quién se comparten', '''
<p>Para que la web funcione, algunos datos pasan por estos servicios, que los tratan por cuenta nuestra o para
prestar su servicio (algunos están fuera de México, sobre todo en Estados Unidos):</p>
<ul>
  <li><b>Cloudflare:</b> alojamiento de la web y de su base de datos.</li>
  <li><b>Stripe:</b> pagos. Ve tu tarjeta y el correo que le das, según su propia política de privacidad.</li>
  <li><b>Discord:</b> solo si conectas tu cuenta, para iniciar sesión y darte roles.</li>
  <li><b>mc-heads.net:</b> recibe tu nombre de jugador para mostrar tu cabeza y tu skin.</li>
  <li><b>api.mcsrvstat.us:</b> tu navegador le pregunta si el servidor está en línea cuando nuestro servidor no lo
    sabe.</li>
  <li><b>Google Fonts:</b> tu navegador descarga de Google las tipografías de la web.</li>
  <li><b>El servidor de Minecraft de Tierras Fantásticas:</b> recibe tu nombre, UUID y tus compras para entregarlas.</li>
</ul>
<p>No compartimos tus datos con nadie más, salvo que una autoridad nos lo exija conforme a la ley.</p>'''),

    ('derechos', 'Tus derechos (ARCO) y cómo ejercerlos', f'''
<p>Puedes <b>acceder</b> a tus datos, <b>rectificarlos</b>, <b>cancelarlos</b> (borrarlos), <b>oponerte</b> a su uso
y <b>revocar tu consentimiento</b>. Muchas cosas las puedes hacer tú en «Mi cuenta» (ver tus compras, cambiar la
contraseña, desconectar Discord). Para lo demás, escribe a {MAIL} indicando:</p>
<ul>
  <li>tu nombre de jugador y, si puedes, escribe desde un medio con el que podamos comprobar que la cuenta es tuya
    (por ejemplo, entrando al servidor o desde tu cuenta de Discord conectada);</li>
  <li>qué derecho quieres ejercer y sobre qué datos;</li>
  <li>el correo donde quieres la respuesta.</li>
</ul>
<p>Te respondemos en un máximo de 20 días hábiles (ampliables una vez por otros 20 si hace falta y lo justificamos) y,
si procede, lo aplicamos en los 15 días hábiles siguientes. Al borrar tu cuenta conservamos solo los registros de tus
compras que la ley nos obliga a guardar (hasta 5 años), bloqueados y sin usarlos para nada más.</p>
<p>Si crees que no hemos atendido bien tu solicitud, puedes acudir a la autoridad de protección de datos personales
(la Secretaría Anticorrupción y Buen Gobierno).</p>'''),

    ('conservacion', 'Cuánto tiempo los guardamos', '''
<ul>
  <li>Cuenta y Discord: mientras tengas la cuenta. Si pides borrarla, se borran.</li>
  <li>Sesión: la cookie de sesión dura 60 días y se renueva al usar la web.</li>
  <li>Compras: el tiempo que exige la ley (hasta 5 años).</li>
  <li>Intentos fallidos de inicio de sesión: unos minutos.</li>
</ul>'''),

    ('menores', 'Menores de edad', f'''
<p>El servidor es para todas las edades. Si eres menor, tu madre, padre o tutor debe dar su consentimiento para que
crees una cuenta o compres; si tienes menos de 13 años, debe crearla por ti. Si eres madre, padre o tutor y quieres ver
o borrar los datos de tu hijo o hija, escríbenos a {MAIL}.</p>'''),

    ('cookies', 'Cookies y almacenamiento del navegador', '''
<p>Solo usamos lo necesario para que la web funcione. <b>No usamos cookies de publicidad ni de analítica</b>, por eso
no te pedimos permiso con un aviso de cookies.</p>
<ul>
  <li><code>tf_session</code> (cookie, 60 días): mantiene tu sesión iniciada. Es <i>HttpOnly</i>: ningún script puede
    leerla.</li>
  <li>Cookie de inicio de sesión con Discord (10 minutos): protege el inicio de sesión con Discord.</li>
  <li><code>tf-username</code> y <code>tf-skin</code> (almacenamiento local del navegador): recuerdan el último nombre de
    jugador y la skin que usaste, para no tener que escribirlos otra vez.</li>
  <li><code>tf-seen</code> y el estado del servidor (almacenamiento de la sesión del navegador): evitan repetir la
    pantalla de carga y preguntar el estado a cada momento. Se borran al cerrar el navegador.</li>
</ul>
<p>Puedes borrar todo esto desde la configuración de tu navegador; si borras la cookie de sesión, tendrás que volver a
entrar.</p>'''),

    ('seguridad', 'Seguridad', '''
<p>La web va siempre cifrada (HTTPS), las contraseñas se guardan cifradas, la sesión usa una cookie protegida y los
pagos se hacen en Stripe. Ningún sistema es infalible: si notas algo raro en tu cuenta, cambia la contraseña y
escríbenos.</p>'''),

    ('cambios', 'Cambios en este aviso', '''
<p>Si cambiamos este aviso de privacidad, publicaremos la versión nueva en esta página con su fecha. Si el cambio
afecta a finalidades que necesitan tu consentimiento, te lo pediremos.</p>'''),
]
