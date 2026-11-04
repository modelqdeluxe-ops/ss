# Lo común de las pruebas de Mohist (se carga con «source»; se usa desde la carpeta de trabajo ya montada).
#   arrancar                 para el servidor de antes (si hay) y arranca Mohist; espera a «Done»
#   conectar <nombres…>      un bot por nombre (bot.js); espera a que entren todos
#   consola <orden> [s]      una orden en la consola del servidor (y espera s segundos, 1 por defecto)
#   di <bot> <línea> [s]     el bot escribe esa línea (mensaje, /comando o «!usar x y z»)
#   mira <qué> <bot> <texto> <sí|no>   ¿le llegó ese texto al bot? apunta bien/MAL
#   comprueba <qué> <sí|no> <orden…>   ¿la orden (de shell) sale bien? apunta bien/MAL
#   parar                    echa a los bots y apaga el servidor
#   final <nombre>           el resumen («<NOMBRE>: TODO BIEN» o los fallos) y sale con el número de fallos
FALLOS=0
BOTS=()

arrancar() {
  pkill -f "[n]ode bot/bot.js" || true
  # un servidor de antes: que se cierre del todo (al cerrarse guarda sus archivos) antes de tocar los datos
  pkill -f "[j]ava -Xms1G -Xmx4G -jar mohist.jar nogui" || true
  for _ in $(seq 1 60); do pgrep -f "[j]ava -Xms1G -Xmx4G -jar mohist.jar nogui" >/dev/null || break; sleep 1; done
  [ -n "${ANTES_DE_ARRANCAR:-}" ] && eval "$ANTES_DE_ARRANCAR"
  : > cmds.txt
  rm -f server.out
  (tail -n0 -f cmds.txt | java -Xms1G -Xmx4G -jar mohist.jar nogui > server.out 2>&1) &
  for _ in $(seq 1 300); do grep -q "Done (" server.out 2>/dev/null && break; sleep 1; done
  grep -q "Done (" server.out || { echo "MAL: el servidor no arrancó"; tail -40 server.out; exit 1; }
  echo "Mohist arrancado: $(grep -m1 -o 'Done ([^)]*)' server.out) · $(unzip -p mods/tfclient.jar META-INF/mods.toml | grep -m1 '^version=')"
}

conectar() {
  mkdir -p bots
  for n in "$@"; do
    rm -f "bots/$n.log" "bots/$n.cmd"
    node bot/bot.js "$n" 25565 "$PWD/bots" > "bots/$n.log" 2>&1 &
    BOTS+=("$n")
    sleep 0.5
  done
  for _ in $(seq 1 60); do
    local dentro=0
    for n in "$@"; do grep -q "dentro del mundo" "bots/$n.log" 2>/dev/null && dentro=$((dentro + 1)); done
    [ "$dentro" -eq "$#" ] && break
    sleep 1
  done
  for n in "$@"; do
    grep -q "dentro del mundo" "bots/$n.log" || { echo "MAL: $n no entró"; tail -5 "bots/$n.log"; exit 1; }
  done
  sleep 2
}

consola() { echo "$1" >> cmds.txt; sleep "${2:-1}"; }
di() { echo "$2" >> "bots/$1.cmd"; sleep "${3:-1.5}"; }

mira() {
  local got=no
  grep -qF -- "$3" "bots/$2.log" && got=sí
  if [ "$got" = "$4" ]; then echo "bien  · $1"; else echo "MAL   · $1 (a $2 le llegó: $got)"; FALLOS=$((FALLOS + 1)); fi
}

comprueba() {
  local que=$1 quiero=$2 got=no
  shift 2
  "$@" >/dev/null 2>&1 && got=sí
  if [ "$got" = "$quiero" ]; then echo "bien  · $que"; else echo "MAL   · $que"; FALLOS=$((FALLOS + 1)); fi
}

parar() {
  for n in "${BOTS[@]}"; do echo quit >> "bots/$n.cmd"; done
  echo stop >> cmds.txt
  for _ in $(seq 1 60); do pgrep -f "[j]ava -Xms1G -Xmx4G -jar mohist.jar nogui" >/dev/null || break; sleep 1; done
  pkill -f "[j]ava -Xms1G -Xmx4G -jar mohist.jar nogui" || true
  pkill -f "[n]ode bot/bot.js" || true
  pkill -f "[t]ail -n0 -f cmds.txt" || true
}

final() {
  if [ "$FALLOS" -eq 0 ]; then echo "$1: TODO BIEN"; else echo "$1: $FALLOS FALLOS"; fi
  exit "$FALLOS"
}
