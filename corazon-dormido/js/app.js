'use strict';
// Puente con la app de Android (android/): la app pone window.CorazonApp (vibrar, salir, version) y llama a
// atrasAndroid() con el botón «atrás», y a appEnPausa() / appVuelve() cuando el juego se va al fondo y vuelve.
// En el navegador nada de esto existe y el juego sigue igual.
const APP = window.CorazonApp || null;
function vibrar(ms) {
  try { if (APP) APP.vibrar(ms | 0); else if (navigator.vibrate) navigator.vibrate(ms | 0); } catch (e) { /* sin vibración */ }
}
// «atrás»: cierra el panel abierto o pausa; en el título devuelve false y la app se va al fondo
window.atrasAndroid = () => {
  if (J.estado === 'titulo') { if (UI.creando) { UI.creando = false; return true; } return false; }
  if (J.estado !== 'juego') return true;
  if (J.panel === 'muerto') return true;
  if (J.panel) { if (J.panel === 'dialogo') J.dialogo = null; J.panel = null; } else J.panel = 'pausa';
  Sonido.sfx.menu();
  return true;
};
window.appEnPausa = () => {
  try { if (J.estado === 'juego') { guardarPartida(); if (!J.panel) J.panel = 'pausa'; } } catch (e) { /* nada */ }
  Sonido.pausar(); Musica.pausar();
};
window.appVuelve = () => { Sonido.seguir(); Musica.seguir(); };
