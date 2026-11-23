# DaniviaFlix

Cliente propio para tu servidor Emby, para móvil y Android TV. Logo A "Play-D" y animación de inicio incluidos.

## Cómo probarlo

1. Crea en GitHub (cuenta `daniviaworld`) un repositorio **público** llamado **`daniviaflix`** y sube TODO el contenido de esta carpeta.
2. GitHub compila solo el APK (pestaña **Actions**, unos 5-8 min). Al terminar crea una release con el APK.
3. Instálalo la primera vez con Downloader, con esta dirección fija (siempre apunta a la última versión):

   `https://github.com/daniviaworld/daniviaflix/releases/latest/download/DaniviaFlix.apk`

   (si quieres, crea un código corto de aftv.news que apunte a esa dirección, como en tus otras apps).

## Actualizaciones desde la propia app

- Al abrir la app, mira la última release de GitHub. Si es más nueva, pregunta "Nueva versión" → **Actualizar** descarga el APK y abre el instalador.
- También hay un botón abajo del todo en la pantalla de inicio: **"Versión X · Buscar actualización"**.
- La primera vez Android pedirá permitir "Instalar apps desconocidas" para DaniviaFlix; se activa una sola vez.
- Para publicar una versión nueva solo tienes que subir los cambios al repositorio: GitHub compila y crea la release solo (el tag es el número de versión).

## Primer uso

Servidor (`IP:puerto`, ej. `192.168.1.10:8096`, o tu dominio), usuario y contraseña de Emby.

## Reproducción 4K / 1080p / 720p

La app le dice al servidor qué codecs y qué resolución máxima decodifica el dispositivo. Si puede, se reproduce el archivo original (Direct Play, 4K incluido). Si no, Emby convierte solo lo necesario. Si algo falla, la app cambia sola a transcodificación y continúa por donde iba.

## Nota sobre la firma

`keystore/danivia-test.jks` es una clave **solo de pruebas** (mantiene la misma firma para que las actualizaciones se instalen encima). Para publicar en Google Play se usará otra clave, guardada como secreto de GitHub y no dentro del repositorio.
