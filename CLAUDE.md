# Coco Surf — reglas del proyecto

## Versionado (obligatorio)
- Versión actual: **2.2.1** (`versionName`/`versionCode` en `app/build.gradle.kts`).
- Cada cambio que se entregue al usuario sube la versión **antes de compilar**:
  - correcciones pequeñas → 1.0.x (patch),
  - funciones nuevas → 1.x.0 (minor),
  - cambios grandes/incompatibles → x.0.0 (major).
- `versionCode` sube siempre en 1 con cada build entregado.
- Hay dos distribuciones (ver `docs/DISTRIBUCION.md`): **directo** (APK de GitHub, la que se entrega al usuario) y **play** (AAB para Google Play, sin autoactualizador ni permisos de archivos). Se entrega la variante **directo release** (`.\gradlew.bat assembleDirectoRelease`): `CocoSurf-<versión>-release.apk` en la raíz (lo ignora git). La debug (`assembleDebug`) sólo para desarrollo: pesa ~30 MB más de RAM.
- Apuntar el cambio en `CHANGELOG.md` con la misma versión y fecha.
- Hacer commit con la versión en el mensaje (`v1.0.1: …`).
- Publicar en GitHub **en la misma entrega**: `git tag v<versión>`, `git push origin main v<versión>` y luego la Release con el APK adjunto, que es lo que lee el buscador de actualizaciones de la app:
  `gh release create v<versión> CocoSurf-<versión>-release.apk --title "Coco Surf <versión>" --notes-file <notas de esa versión del CHANGELOG>`
  (`gh` ya está instalado; requiere que el usuario haya hecho `gh auth login` una vez. Sin Release con `.apk` la app no ve la actualización.)

- Para tiendas: `.\gradlew.bat bundlePlayRelease` → `CocoSurf-<versión>-play.aab` (con la clave de `keystore.properties`; nunca se sube a git).

## Prueba obligatoria de bloqueadores de anuncios (siempre que se toque algo relacionado)
Aplica a cualquier cambio en complementos/extensiones (`GeckoMotor`, `WebTab`, `Actualizador`, tienda de Complementos, restauración de pestañas, arranque del motor, `BrowserScreen` al cargar páginas…). Antes de entregar:
1. **Ver los anuncios primero**: en el emulador (el AVD que haya instalado; APK con `-Pabi=x86_64`), instalación limpia, abrir `https://jkanime.net/` **antes** de que haya bloqueador y comprobar que salen anuncios (banner de apuestas y ventana flotante con X). Si no salen, la prueba no vale.
2. **Probar CADA bloqueador por separado**, con sólo ése activo: uBlock Origin, AdGuard AdBlocker y Privacy Badger (los de la tienda de Complementos; si se añade otro, también). Para cada uno: abrir jkanime, mover/tocar/desplazar como una persona (los anuncios sólo nacen con interacción), y comprobar que NO hay banners ni ventanas emergentes y que el contador del icono es mayor que 0 (si marca «!» no está filtrando). Anotar el resultado de cada uno en la respuesta, incluido el que no logre bloquear.
3. **Casos de arranque**: (a) cerrar y reabrir la app con la pestaña abierta (restaurada, debe salir filtrada); (b) abrir la página antes de que el bloqueador termine de instalarse (debe recargarse sola y quedar filtrada); (c) instalar/desinstalar/cambiar de bloqueador desde Complementos y comprobar que la pestaña abierta responde.
4. **La lista propia** (`assets/gecko-ext/lista.js`): comprobar en Configuración → General → «Bloqueo de anuncios» que dice «Activa», con dominios > 0, y que con uBlock APAGADO la página de jkanime no muestra los banners de apuestas.
5. **Probar en la TV real si está conectada por adb** (el emulador arranca el bloqueador en segundos y sirve otros anuncios; la TV es lenta y de 32 bits): abrir jkanime, ver en el panel de uBlock su «Registro de peticiones» y los contadores. Una prueba sólo en emulador NO basta para decir que funciona en la TV; dilo en la respuesta.
6. Nunca decir «no salen anuncios» a partir de una prueba sin anuncios de base: los de Costa Rica (`crc`) son del mismo sitio y salen con la sesión normal.
## Notas
- UI en español, sólo tema oscuro, acento elegible por el usuario.
- Dispositivo objetivo: TV Xiaomi ~1.7 GB RAM (ARMv8, ABI de 32 bits): cuidar la memoria.
