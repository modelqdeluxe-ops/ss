# Traducciones de Rumentis

La app se escribe en español. `app/assets/i18n.js` traduce la pantalla antes de pintarla
usando los catálogos `app/assets/i18n_en.js` e `i18n_pt.js`. Las claves son las frases en
español normalizadas: nombres de lotes, fechas, números y unidades se vuelven `{0}`, `{1}`…

## Agregar o corregir frases

1. Recorrer la app en modo grabación para juntar las frases que aparecen en pantalla:
   servir `app/assets` en el puerto 8111 (`npx http-server app/assets -p 8111`) y correr
   `node modelo/traducciones/recorrer.js` (usa Playwright). Con el idioma `xx`, `i18n.js`
   guarda cada clave en `window.I18N_REC`.
2. Añadir las frases nuevas a un archivo `.tsv` de esta carpeta: `clave<TAB>inglés<TAB>portugués`.
   Las marcas `{n}` deben ser las mismas en las tres columnas.
3. `python3 modelo/armar_i18n.py` revisa las marcas y vuelve a escribir los catálogos.

## Otro idioma

Añadir una columna a los `.tsv`, generar `i18n_xx.js` en `armar_i18n.py`, cargarlo en
`index.html` y sumar el idioma a `IDIOMAS` en `i18n.js`.

## La Guía de Rumi

Los temas de la guía (`rumi_guia.js`) se traducen aparte, en `guia/*.tsv`: `id<TAB>inglés<TAB>portugués`.
Los ids siguen el orden de `RUMI_FICHAS` y `RUMI_GUIA`: `F<n>.t` título, `F<n>.r` resumen, `F<n>.<s>h`
título de una parte, `F<n>.<s>.<p>` un punto; `G<n>.t` y `G<n>.x` los datos rápidos.
`node modelo/armar_guia_idiomas.js` escribe `app/assets/rumi_guia_en.js` y `_pt.js` (lo que falte queda en
español; `-v` lo lista). `guia/origen.es` guarda el español que se tradujo: si cambias o mueves un tema,
el script avisa qué ids cambiaron y los deja en español hasta que actualices su traducción y corras
`node modelo/armar_guia_idiomas.js --fijar`.
