# Cómo distribuir Coco Surf

El proyecto tiene dos distribuciones del mismo código:

| | **directo** | **play** |
|---|---|---|
| Para | instalar en la TV desde GitHub | Google Play y tiendas que piden AAB/APK limpio |
| Archivo | `CocoSurf-<v>-release.apk` | `CocoSurf-<v>-play.aab` |
| Compilar | `.\gradlew.bat assembleDirectoRelease` | `.\gradlew.bat bundlePlayRelease` |
| Se actualiza | sola, desde las Releases de GitHub | sólo por la tienda |
| Archivos | ve todo el almacenamiento | sólo la carpeta propia de la app |
| Firma | clave de depuración (para que las TV que ya lo tienen puedan actualizar) | **clave de subida propia** (ver abajo) |
| `targetSdk` | 34 | 36 |

> Una app firmada con una clave no se puede actualizar con otra: quien instaló el APK directo y luego quiera pasar a la de Play debe desinstalar primero.

## 1. Crear la clave de subida (una sola vez, la haces tú)
En PowerShell, en la carpeta del proyecto (te pedirá las contraseñas; **no las compartas con nadie y guárdalas**):

```powershell
keytool -genkeypair -v -keystore cocosurf-subida.jks -alias subida -keyalg RSA -keysize 2048 -validity 10000
```

Crea el archivo `keystore.properties` junto al `build.gradle.kts` raíz (git ya lo ignora):

```
storeFile=cocosurf-subida.jks
storePassword=TU_CONTRASEÑA
keyAlias=subida
keyPassword=TU_CONTRASEÑA
```

**Haz copia de `cocosurf-subida.jks` y de las contraseñas en dos lugares.** Si se pierde, hay que pedir a Google que restablezca la clave de subida.

## 2. Google Play
1. Crea una cuenta de desarrollador en <https://play.google.com/console> (pago único de 25 USD y verificación de identidad). Las cuentas personales nuevas deben hacer una prueba cerrada con al menos 12 testers durante 14 días antes de publicar en producción.
2. Crea la app «Coco Surf» (idioma español) y deja activo **Play App Signing**.
3. Compila: `.\gradlew.bat bundlePlayRelease` → `CocoSurf-<v>-play.aab`.
4. Sube el AAB a una pista (pruebas internas primero).
5. Rellena la ficha con `docs/FICHA_GOOGLE_PLAY.md`: política de privacidad (URL), seguridad de los datos, clasificación de contenido, público objetivo, capturas y gráficos.
6. En «Android TV» marca el factor de forma y sube capturas de TV y banner.
7. Revisa el informe previo al lanzamiento y envía a revisión.

## 3. Otras tiendas de Android

| Tienda | Sirve para | Qué subir | Notas |
|---|---|---|---|
| **Xiaomi GetApps (Mi TV)** | Tu TV es Xiaomi | APK `play` | Cuenta de desarrollador Xiaomi (con verificación). |
| **Amazon Appstore (Fire TV)** | Fire TV y tablets Fire | APK `play` | Gratis. El mejor destino para TV después de Play. GeckoView no necesita servicios de Google. |
| **Huawei AppGallery** | Dispositivos Huawei | APK `play` | Cuenta de desarrollador con verificación. |
| **Samsung Galaxy Store** | Móviles Samsung | APK `play` | Las TV Samsung usan Tizen, no Android: no sirve para ellas. |
| **F-Droid** | Usuarios de código abierto | Receta de compilación desde el repositorio | Exige compilar desde el código fuente; hay que revisar que GeckoView y el acceso a la tienda de complementos cumplan sus reglas. Más trabajo. |
| **Aptoide / Uptodown / APKPure** | Alternativas abiertas | APK `play` | Subida sencilla. |
| **GitHub Releases (ya activo)** | TV con instalación manual | APK `directo` | La versión `directo` se actualiza sola desde aquí. |
| **Obtainium** | Usuarios avanzados | nada | Descargan directo de tus Releases de GitHub. |

Para el APK `play` (sin firmar con la clave de depuración) usa: `.\gradlew.bat assemblePlayRelease`.

Orden recomendado: **Google Play → Amazon Appstore → Xiaomi GetApps → el resto**.

## 4. Publicar cada versión
1. Sube `versionCode` y `versionName` en `app/build.gradle.kts` y anota `CHANGELOG.md`.
2. `.\gradlew.bat assembleDirectoRelease bundlePlayRelease`.
3. GitHub: commit, `git tag v<versión>`, push y Release con el APK `directo`.
4. Tiendas: sube el AAB (o el APK `play`) a cada consola.
