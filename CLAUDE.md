# Coco Surf — reglas del proyecto

## Versionado (obligatorio)
- Versión actual: **1.4.2** (`versionName`/`versionCode` en `app/build.gradle.kts`).
- Cada cambio que se entregue al usuario sube la versión **antes de compilar**:
  - correcciones pequeñas → 1.0.x (patch),
  - funciones nuevas → 1.x.0 (minor),
  - cambios grandes/incompatibles → x.0.0 (major).
- `versionCode` sube siempre en 1 con cada build entregado.
- Se entrega la variante **release** (`.\gradlew.bat assembleRelease`): `CocoSurf-<versión>-release.apk` en la raíz (lo ignora git). La debug (`assembleDebug`) sólo para desarrollo: pesa ~30 MB más de RAM.
- Apuntar el cambio en `CHANGELOG.md` con la misma versión y fecha.
- Hacer commit con la versión en el mensaje (`v1.0.1: …`).
- Publicar en GitHub **en la misma entrega**: `git tag v<versión>`, `git push origin main v<versión>` y luego la Release con el APK adjunto, que es lo que lee el buscador de actualizaciones de la app:
  `gh release create v<versión> CocoSurf-<versión>-release.apk --title "Coco Surf <versión>" --notes-file <notas de esa versión del CHANGELOG>`
  (`gh` ya está instalado; requiere que el usuario haya hecho `gh auth login` una vez. Sin Release con `.apk` la app no ve la actualización.)

## Notas
- UI en español, sólo tema oscuro, acento elegible por el usuario.
- Dispositivo objetivo: TV Xiaomi ~1.7 GB RAM (ARMv8, ABI de 32 bits): cuidar la memoria.
