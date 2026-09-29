# Publicar los servidores de Rumentis

> **Publicados** en https://rumentis-equipo.modelqdeluxe.workers.dev y https://rumentis-zona.modelqdeluxe.workers.dev.
> Con ellos los registros llegan en vivo y el reporte del día se envía solo a su hora; sin señal, sigue por archivo.
> El flujo "Publicar servidores" corre solo cada vez que cambia algo en `servidor/`.

## ¿Qué son?

Rumentis funciona sin internet. Hay dos cosas que necesitan un "punto de encuentro" en internet:

| Servidor | Para qué sirve | Qué guarda |
|---|---|---|
| **rumentis-equipo** | Buzón del equipo. El jefe y sus vaqueros se pasan los datos solos, sin mandarse archivos a mano. | Sobres **cifrados**. El servidor no puede leerlos: solo el jefe y sus vaqueros tienen la llave. Se borran cuando ya se entregaron. |
| **rumentis-zona** | La comparación anónima con otras fincas de la zona. | Promedios sin nombres ni ubicación exacta. Un grupo solo se muestra si tiene 5 fincas o más. |

Sin estos servidores la app funciona igual. El equipo se sincroniza con archivos (WhatsApp, Bluetooth…) y la comparación con la zona queda apagada.

## ¿Cuesta dinero?

No, a la escala de una app que empieza. Los dos corren en **Cloudflare Workers** con el **plan gratis**, que no pide tarjeta:

- 100 000 peticiones al día. Cada sincronización de un teléfono usa unas 6, así que alcanza para miles de teléfonos al día.
- 5 GB de base de datos (D1). Los sobres pesan unos pocos KB y se borran al entregarse.

Si algún día la app crece mucho y pasa ese límite, el plan pagado de Workers cuesta unos **US$5 al mes**. Cloudflare no te cobra solo: tendrías que activarlo tú. Con el plan gratis, pasado el límite del día, el servidor deja de responder hasta el día siguiente y la app sigue con los archivos.

## Pasos (unos 10 minutos, una sola vez)

1. **Crea una cuenta gratis en Cloudflare:** https://dash.cloudflare.com/sign-up (correo y contraseña; confirma el correo).

2. **Copia tu Account ID.**
   - En https://dash.cloudflare.com entra a **Workers & Pages** (menú de la izquierda).
   - A la derecha aparece **Account ID**. Cópialo; es un texto de 32 letras y números.

3. **Crea un token (llave) para publicar.**
   - Ve a https://dash.cloudflare.com/profile/api-tokens y toca **Create Token**.
   - Elige la plantilla **Edit Cloudflare Workers** y toca **Use template**.
   - En **Permissions** toca **+ Add more** y agrega: `Account` · `D1` · `Edit`.
   - En **Account Resources** elige tu cuenta y en **Zone Resources** deja `All zones`.
   - Toca **Continue to summary** y luego **Create Token**. Copia el token: solo se muestra una vez.
   - ⚠️ No lo pegues en el chat ni en ningún archivo. Es como una contraseña.

4. **Guarda las dos cosas en GitHub como secretos.**
   - En el repositorio ve a **Settings → Secrets and variables → Actions → New repository secret**.
   - Crea `CLOUDFLARE_API_TOKEN` con el token.
   - Crea `CLOUDFLARE_ACCOUNT_ID` con el Account ID.

5. **Publica.**
   - En el repositorio ve a **Actions → Publicar servidores → Run workflow** y elige la rama de la app.
   - En 2–3 minutos:
     - crea las bases de datos;
     - publica los dos servidores;
     - comprueba que respondan;
     - pone sus direcciones en la app (`app/assets/config.js` y `app/assets/zona.js`);
     - pide una compilación nueva.
   - Unos 10 minutos después, las APK nuevas de la sección *Releases* ya usan los servidores.

Se puede volver a correr cuantas veces quieras: no borra datos ni cambia las direcciones.

## Si algo falla

- **"Faltan los secretos":** revisa que los nombres estén escritos exactamente así, en mayúsculas.
- **"Authentication error" / 10000:** al token le falta el permiso `D1 · Edit` o no está asignado a tu cuenta. Crea uno nuevo (paso 3) y reemplaza el secreto.
- **"No responde todavía":** en una cuenta recién creada el subdominio `workers.dev` tarda unos minutos en activarse. Vuelve a correr el flujo.

## Desde tu computadora (opcional)

```bash
export CLOUDFLARE_API_TOKEN=...   CLOUDFLARE_ACCOUNT_ID=...
bash servidor/publicar.sh          # necesita node, curl, jq y openssl
```
