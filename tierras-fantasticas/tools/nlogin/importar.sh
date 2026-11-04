#!/usr/bin/env bash
# Carga en /metricas los registros de nLogin de una carpeta (la de plugins/nLogin del servidor, o el zip que mande el
# dueño ya descomprimido) cuando el servidor no tiene el TF Client 1.3.174 (que los manda solo por el puente).
#   METRICAS_PASSWORD=… importar.sh <carpeta nLogin> [web]
# Manda los registros desde el día de config/metricas.json, empezando donde va la web (no repite nada), y las cuentas de
# nlogin.db (solo nombre, creación y última vez). Si no se pone METRICAS_PASSWORD, la pide.
set -euo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
RAIZ=$(cd "$AQUI/../../.." && pwd)
NL=$(cd "${1:?carpeta de nLogin}" && pwd)
WEB=${2:-https://xn--tierrasfantsticas-hpb.store}
if [ -z "${METRICAS_PASSWORD:-}" ]; then read -r -s -p "Contraseña de /metricas: " METRICAS_PASSWORD; echo; fi
export METRICAS_PASSWORD
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
javac -d "$TMP/clases" "$RAIZ/tf-client/src/main/java/net/tierrasfantasticas/tfclient/server/NLoginLog.java" "$AQUI/ImportarNLogin.java"
CUENTAS=""
if [ -f "$NL/nlogin.db" ]; then
  python3 -I - "$NL/nlogin.db" "$TMP/cuentas.json" <<'PY'
import json, sqlite3, sys
db = sqlite3.connect("file:" + sys.argv[1] + "?mode=ro", uri=True)
rows = [[n, c, s] for n, c, s in db.execute("SELECT last_name, creation_date, last_seen FROM nlogin")]
json.dump(rows, open(sys.argv[2], "w"))
print(f"{len(rows)} cuentas en nlogin.db")
PY
  CUENTAS="$TMP/cuentas.json"
fi
java -cp "$TMP/clases" ImportarNLogin "$NL" "$WEB" "$CUENTAS"
