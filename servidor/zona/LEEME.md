# Servidor de la comparación con la zona

La app (`app/assets/zona.js`) manda, solo si el ganadero acepta, los indicadores de su finca una vez por semana
y recibe los rangos de las fincas de su zona. Este servidor los guarda y los resume. Corre en
**Cloudflare Workers** con una base **D1** (el plan gratis alcanza para miles de fincas).

## Qué se guarda

Una fila por finca y mes: hash del número al azar de la finca, mes, país, cuadro de 1 grado (unos 110 km),
moneda, ganancia diaria, conversión, mortalidad, costo por kilo ganado (en su moneda y en dólares), días de
engorde y un tamaño aproximado (4 rangos). Nada de nombres, ubicación exacta, lotes ni montos totales.
La IP solo se usa, como hash, para limitar a 30 envíos por día y se borra a los 2 días.

Un grupo (cuadro, cuadros vecinos o país) se muestra solo si tiene `MINIMO` fincas distintas (5) y solo con
sus cuartiles. Quien sale de la comparación borra todas sus filas (`DELETE /v1/aporte`).

## Publicarlo

```sh
cd servidor/zona
npm install
npx wrangler login
npx wrangler d1 create rumentis-zona          # copia el database_id en wrangler.toml
npx wrangler d1 execute rumentis-zona --remote --file=schema.sql
npx wrangler secret put SAL                  # una frase larga al azar; no la cambies después
npx wrangler deploy                          # te da la dirección: https://rumentis-zona.<tu-cuenta>.workers.dev
```

Después pon esa dirección en `SERVIDOR`, al inicio de `app/assets/zona.js`, y compila la app.
Para probar sin compilar: en la app, Análisis de Rumi, Tú contra tu zona, «Servidor de la comparación».

## Probarlo

`npm test` levanta el Worker con su base D1 en local (miniflare) y prueba el envío, los grupos de 5, los
vecinos y el país, el reemplazo del mismo mes, el borrado y el límite por IP.

## API

| Ruta | Qué hace |
| --- | --- |
| `POST /v1/aporte` | `{v:1, id, pais, celda, mes, moneda, m:{gdp, conv, mort, costo, costo_usd, dias, tam}}` |
| `GET /v1/zona?pais=HN&celda=14_-87&moneda=HNL` | `{nivel, fincas, minimo, metr:{gdp:{p25,p50,p75,n}, …}}` |
| `DELETE /v1/aporte?id=…` | borra todo lo de esa finca |
| `GET /v1/salud` | `{ok:true}` |
