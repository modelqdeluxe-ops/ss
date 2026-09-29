# Servidor de relevo del equipo

La app del jefe (`app/assets/equipo.js`) y la de los vaqueros (`app/assets/vaquero.js`) se mandan sobres
cifrados de punta a punta: altas, bienvenidas, registros del día y el estado de la finca. Este servidor solo
los guarda y los entrega; no tiene las llaves para leerlos. Corre en **Cloudflare Workers** con una base
**D1** (el plan gratis alcanza de sobra para miles de fincas).

Sin servidor el equipo también funciona: el jefe y los vaqueros se pasan los datos como archivo (WhatsApp).
Con servidor todo llega solo y en vivo mientras la app está abierta.

## Qué se guarda

- `equipos`: el número al azar del equipo y la llave pública del jefe.
- `licencias`: el **SHA-256** de cada licencia (el código no) y la ficha del equipo firmada por el jefe
  (sus llaves públicas y el nombre de la finca), para que un teléfono nuevo encuentre a su jefe.
- `miembros`: los vaqueros que el jefe aceptó (número y llave pública).
- `buzon`: los sobres cifrados. Se borran a los 60 días; del estado de la finca solo queda el último.

Cada pedido va firmado (Ed25519) y con la hora; el servidor comprueba quién es y qué puede hacer: solo el jefe
sube licencias, acepta o da de baja; un vaquero solo le escribe al jefe y solo lee lo suyo.

## En la finca, sin internet (lo que se usa por ahora)

El servidor corre en cualquier computadora de la finca (Windows, Mac o Linux con [Node.js](https://nodejs.org) 20 o
más). Los teléfonos se conectan por el mismo Wi-Fi, o por el punto de acceso del teléfono del jefe.

```sh
cd servidor/equipo
npm install          # solo la primera vez
npm run local
```

Muestra una dirección como `192.168.1.10:8790`. En la app del jefe: **Equipo → Servidor del equipo**, se escribe esa
dirección y listo; los vaqueros la reciben solos con la ficha del equipo. Mientras la computadora esté encendida y
los teléfonos en el Wi-Fi, todo llega **en vivo**: cada teléfono mantiene abierta una conexión (el "timbre") y el
servidor le avisa en cuanto hay algo para él. Los datos se guardan en `datos/` y no se pierden al apagar.

Por la red de la finca va por `http`, pero no se puede leer nada: cada sobre va cifrado y firmado de punta a punta.

## Probarlo

```sh
cd servidor/equipo
npm install
npm test
```

## Publicarlo

```sh
cd servidor/equipo
npm install
npx wrangler login
npx wrangler d1 create rumentis-equipo          # copia el database_id en wrangler.toml
npx wrangler d1 execute rumentis-equipo --remote --file=schema.sql
npx wrangler deploy                             # te da la dirección: https://rumentis-equipo.<tu-cuenta>.workers.dev
```

Después pon esa dirección en `servidorEquipo`, en `app/assets/config.js`, y compila las apps. Las licencias
llevan la dirección del servidor dentro, así que los vaqueros la toman de su licencia.
Para probar sin compilar: en la app del jefe, Equipo, «Servidor del equipo».
