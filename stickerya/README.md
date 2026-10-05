# StickerYa

App de Android para crear stickers de WhatsApp **sin marcas de agua**, con un editor completo y fácil, que se
añaden con un toque a **WhatsApp** y a **WhatsApp Business**.

## Descargar

Cada cambio en `stickerya/` compila el APK y lo publica siempre en el mismo enlace:

**https://github.com/modelqdeluxe-ops/ss/releases/download/stickerya/StickerYa.apk**

Ábrelo desde el teléfono, instálalo (Android pedirá permitir "instalar apps de origen desconocido") y listo.
Las versiones nuevas se instalan encima de la anterior sin perder tus paquetes.

## Cómo se usa

1. **Galería**, **Cámara** o **Texto** en la pantalla de inicio (o *Compartir → StickerYa* desde cualquier app).
2. En el editor:
   - **Recortar**: *Quitar fondo* (IA, en el teléfono), *Fondo liso* (para fondos de un color), *Borrar*,
     *Restaurar*, *Varita* (toca un color y se borra esa zona), *A mano* (rodea lo que quieres conservar) y
     *Formas* (círculo, redondeado, corazón, estrella). Con dos dedos haces zoom para afinar.
   - **Texto**: 7 fuentes, color, contorno y fondo. Doble toque en un texto para editarlo.
   - **Emoji**, **Dibujar** (pincel con color y grosor), **Borde** (el contorno blanco típico, o de cualquier
     color), **Ajustes** (brillo, contraste y color) e **Imagen** (añadir otra foto encima).
   - **Mover**: arrastra; con dos dedos (o el círculo de la esquina) giras y cambias el tamaño. Espejo, duplicar,
     delante/detrás, centrar y borrar.
   - **Deshacer / Rehacer** (hasta 20 pasos).
3. **Guardar**: eliges 1–3 emojis (WhatsApp los usa para sugerir el sticker) y el paquete.
4. En el paquete, con **3 stickers o más**, aparecen los botones **Añadir a WhatsApp** y
   **Añadir a WhatsApp Business** (los que tengas instalados). Cuando cambias algo, WhatsApp lo actualiza solo.

Los stickers se pueden volver a editar con sus capas (los textos siguen siendo texto).

## Lo que garantiza la exportación

WhatsApp rechaza paquetes que no cumplan sus reglas; StickerYa las cumple siempre:

| Regla de WhatsApp | StickerYa |
| --- | --- |
| Sticker de 512×512 en WebP | Siempre 512×512 WebP con transparencia |
| Máximo 100 KB por sticker | Prueba sin pérdida y, si no cabe, baja la calidad hasta que quepa |
| Icono de 96×96 PNG, < 50 KB | Se genera solo con el primer sticker (lo puedes cambiar) |
| 3 a 30 stickers por paquete | La app lo avisa y no deja pasar de 30 |
| 1 a 3 emojis por sticker | Se eligen al guardar |

El paquete se comparte con WhatsApp con el mismo mecanismo oficial (proveedor de contenido
`com.stickerya.app.stickercontentprovider` con el permiso `com.whatsapp.sticker.READ`) para la app normal
(`com.whatsapp`) y la Business (`com.whatsapp.w4b`). No hay marcas de agua ni se añade el nombre de la app: el
autor del paquete es el que tú escribas.

## Compilar

```sh
cd stickerya
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Requiere JDK 17 y el SDK de Android (API 35). La clave de firma (`app/release.jks`, contraseña `stickerya`)
está en el repo a propósito para que cada versión se instale encima de la anterior; es para uso personal.

El workflow `.github/workflows/stickerya.yml` compila, publica el APK y pasa las pruebas en un emulador
(`app/src/androidTest`): comprueba con el proveedor real que los stickers son WebP de 512×512 y < 100 KB, que el
icono es de 96×96, y recorre la app haciendo capturas.
