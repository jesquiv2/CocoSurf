# Coco Surf — reglas del proyecto

## Versionado (obligatorio)
- Versión actual: **1.2.0** (`versionName`/`versionCode` en `app/build.gradle.kts`).
- Cada cambio que se entregue al usuario sube la versión **antes de compilar**:
  - correcciones pequeñas → 1.0.x (patch),
  - funciones nuevas → 1.x.0 (minor),
  - cambios grandes/incompatibles → x.0.0 (major).
- `versionCode` sube siempre en 1 con cada build entregado.
- El APK sale como `CocoSurf-<versión>.apk` en la raíz (lo ignora git).
- Apuntar el cambio en `CHANGELOG.md` con la misma versión y fecha.
- Hacer commit con la versión en el mensaje (`v1.0.1: …`).

## Notas
- UI en español, sólo tema oscuro, acento elegible por el usuario.
- Dispositivo objetivo: TV Xiaomi ~1.7 GB RAM (ARMv8, ABI de 32 bits): cuidar la memoria.
