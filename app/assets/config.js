/* Configuración de esta compilación de Rumentis. scripts/variantes.sh reescribe la primera línea para cada app:
   - app: 'jefe' (Rumentis, la app de pago de la administración) o 'vaquero' (Rumentis Equipo, gratis, para el personal).
   - prueba: true en las apps de prueba (las compras se simulan y no se cobra nada).
   Lo demás lo llena el dueño de la app una vez: */
window.RUMENTIS={app:'jefe',prueba:false,
  // servidor de relevo del equipo (servidor/equipo). Vacío: el equipo se pasa los datos por archivo (WhatsApp).
  servidorEquipo:'https://rumentis-equipo.modelqdeluxe.workers.dev',
  // llave pública RSA de Rumentis en Google Play (Play Console > Monetización > Configuración de la monetización).
  // Con ella, Rumentis Equipo comprueba que la licencia salió de una compra real. Vacía: no se comprueba.
  playLlave:'',
  // producto de Google Play para cada licencia (consumible, "Productos integrados en la aplicación")
  productoLicencia:'licencia_vaquero',
  // precio que se muestra mientras Google Play no dice el suyo (el precio de verdad se pone en Play Console)
  precioLicencia:'US$1.99',
  tiendaVaquero:'https://play.google.com/store/apps/details?id=hn.hato.ganadero.vaquero',
  paginaVaquero:'https://modelqdeluxe-ops.github.io/ss/campo/'};
if(window.RUMENTIS.app==='vaquero')document.documentElement.classList.add('vaquero');
if(window.RUMENTIS.prueba)document.documentElement.classList.add('prueba');
