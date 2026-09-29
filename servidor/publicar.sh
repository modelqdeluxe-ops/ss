#!/usr/bin/env bash
# Publica los dos servidores de Rumentis en Cloudflare (plan gratis) y pone sus direcciones en la app.
# Lo corre .github/workflows/servidores.yml con los secretos CLOUDFLARE_API_TOKEN y CLOUDFLARE_ACCOUNT_ID
# (ver servidor/PUBLICAR.md). También sirve en tu computadora con esas dos variables.
#   1. el subdominio workers.dev de la cuenta (si no tiene, se crea: rumentis-xxxxxx);
#   2. por cada servidor: su base D1 (se crea la primera vez), las tablas, la publicación y, en el de la zona,
#      su frase secreta SAL (se crea una sola vez y no se cambia);
#   3. comprueba que respondan y escribe sus direcciones en app/assets/config.js y app/assets/zona.js.
# Se puede correr las veces que quieras: no borra datos.
set -euo pipefail

: "${CLOUDFLARE_API_TOKEN:?falta CLOUDFLARE_API_TOKEN}" "${CLOUDFLARE_ACCOUNT_ID:?falta CLOUDFLARE_ACCOUNT_ID}"
AQUI="$(cd "$(dirname "$0")" && pwd)"; RAIZ="$(cd "$AQUI/.." && pwd)"
API="https://api.cloudflare.com/client/v4/accounts/$CLOUDFLARE_ACCOUNT_ID"
cf() { curl -sS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" -H "Content-Type: application/json" "$@"; }
W() { npx --yes wrangler@4 "$@"; }

# ---- 1. subdominio workers.dev ----
SUB=$(cf "$API/workers/subdomain" | jq -r '.result.subdomain // empty')
if [ -z "$SUB" ]; then
  SUB="rumentis-$(echo "$CLOUDFLARE_ACCOUNT_ID" | cut -c1-6)"
  R=$(cf -X PUT -d "{\"subdomain\":\"$SUB\"}" "$API/workers/subdomain")
  [ "$(echo "$R" | jq -r .success)" = true ] || { echo "No se pudo crear el subdominio workers.dev: $R"; exit 1; }
fi
echo "subdominio: $SUB.workers.dev"

# ---- 2. los servidores ----
publicar() {
  local nombre="$1" dir="$AQUI/$2" id
  id=$(cf "$API/d1/database?name=$nombre" | jq -r '.result[]? | select(.name=="'"$nombre"'") | .uuid' | head -1)
  if [ -z "$id" ]; then
    id=$(cf -X POST -d "{\"name\":\"$nombre\"}" "$API/d1/database" | jq -r '.result.uuid // empty')
    [ -n "$id" ] || { echo "No se pudo crear la base $nombre"; exit 1; }
    echo "$nombre: base nueva $id"
  fi
  sed -i "s/^database_id = \".*\"/database_id = \"$id\"/" "$dir/wrangler.toml"
  (cd "$dir" && W d1 execute "$nombre" --remote --file=schema.sql --yes >/dev/null)
  (cd "$dir" && W deploy)
  if [ "$nombre" = rumentis-zona ]; then
    if ! (cd "$dir" && W secret list --format json 2>/dev/null) | jq -e '.[]? | select(.name=="SAL")' >/dev/null; then
      openssl rand -hex 32 | (cd "$dir" && W secret put SAL)
      echo "rumentis-zona: frase SAL creada"
    fi
  fi
}
publicar rumentis-equipo equipo
publicar rumentis-zona zona
EQ="https://rumentis-equipo.$SUB.workers.dev"; ZO="https://rumentis-zona.$SUB.workers.dev"

# ---- 3. comprobar y poner las direcciones en la app ----
for u in "$EQ" "$ZO"; do
  ok=0; for i in $(seq 1 20); do if curl -fsS "$u/v1/salud" | jq -e .ok >/dev/null 2>&1; then ok=1; break; fi; sleep 6; done
  [ "$ok" = 1 ] && echo "en línea: $u" || { echo "No responde todavía: $u (una cuenta nueva puede tardar unos minutos)"; exit 1; }
done
sed -i "s#^  servidorEquipo:'[^']*',#  servidorEquipo:'$EQ',#" "$RAIZ/app/assets/config.js"
sed -i "s#^const SERVIDOR='[^']*';#const SERVIDOR='$ZO';#" "$RAIZ/app/assets/zona.js"
grep -q "servidorEquipo:'$EQ'" "$RAIZ/app/assets/config.js" && grep -q "SERVIDOR='$ZO'" "$RAIZ/app/assets/zona.js"
echo "EQUIPO=$EQ"; echo "ZONA=$ZO"
