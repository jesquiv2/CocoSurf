# Plan: navegador TV estilo Edge + Brave

> Documento de trabajo para discutir antes de escribir la primera línea de código.
> Nombre provisional: **Antena** (candidatos: Antena · Vigía · Faro).

## 1. Visión en una frase

Un navegador **ligero, privado y pensado para el control remoto**, montado como APK de
Android TV: dashboard cómodo tipo Edge, escudos tipo Brave (bloqueo de anuncios y
rastreadores) y navegación real con D‑pad —incluido cursor circular— para pantallas
Xiaomi y cualquier otro TV Android viejo o actual.

## 2. Qué copiamos de Edge y qué de Brave

| Tema | Fuente | Concreto |
|---|---|---|
| Dashboard de inicio configurable | Edge | Saludo + buscador con voz, tiles "más visitados", fila "reanudar", favoritos, chips de estado |
| Botón de historial arriba a la izquierda | Edge | Panel deslizante: Hoy / Ayer / 7 días / Recién cerradas + búsqueda |
| Pestañas | Edge | Cuadrícula de pestañas tipo TV, límite configurable, pestañas privadas |
| Traducir página | Edge | Botón contextual + opción automática por idioma |
| Guardar como PDF / imprimir | Edge | Imprimir → PDF nativo del sistema |
| Guardar contraseñas | Edge | Aviso "¿Guardar contraseña?" + bóveda cifrada |
| Bloqueo de anuncios y rastreadores | Brave | Escudos por sitio (Agresivo/Estándar/Desactivado), contador de bloqueos |
| Actualización a HTTPS | Brave | HTTPS‑only con aviso antes de saltar |
| Protección contra huella digital | Brave | Básica en v1 (cabeceras + JS mínimos), completa si el motor lo permite |
| Limpieza de datos | Ambos | Borrado selectivo (historial, cookies, caché, contraseñas, descargas) al cerrar sesión privada |

## 3. Decisión #1: motor de render (la más importante)

Hay exactamente tres caminos posibles; todo lo demás del plan depende de esta elección.

| | A. WebView del sistema (Chromium) | B. GeckoView embebido | C. Fork de Chromium con extensiones (herencia Kiwi → Edge) |
|---|---|---|---|
| Peso del APK | ~5–15 MB extra | +60–80 MB | 150–250 MB |
| Extensiones Chrome Web Store | **Imposible** (el API no existe en WebView) | Parcial (WebExtensions curadas: uBO, Dark Reader…) | Sí, completas |
| Códecs | Excelentes: delega en MediaCodec del TV (HEVC, AV1 si el chip lo trae) | Buenos, pero **HEVC débil** | Excelentes |
| DRM (Widevine) | Usa MediaDrm del dispositivo (los TV suelen traer L1) | MediaPipeline/MP‑CDM, hay que validar | Excelente |
| Versión del motor | **La del TV** (Xiaomi puede traer Chromium viejo) | Siempre la nuestra, actualizada | Siempre la nuestra |
| Esfuerzo de ingeniería | Bajo | Medio | Muy alto (checkout de 40–80 GB, builds de horas, CI propia) |
| Peso en RAM | Bajo (procesos del sistema compartidos) | Medio | Alto |

**Recomendación para discutir:** arrancar con **A (WebView)** + bloqueo integrado
(propio, tipo Brave Shields, que no necesita extensiones), dejando la interfaz
`WebEngine` abstracta desde el día 1 para enchufar **B (GeckoView)** en la fase 2 como
"modo compatibilidad con extensiones". C queda fuera de alcance salvo que el proyecto
crezca mucho.

Por qué A primero: es el más ligero (requisito: "corra en los peores hardware"),
tiene el mejor soporte de códecs/DRM en TV y entrega el 100 % de las funciones
pididas **sin** extensiones; las extensiones de Chrome son el único que A no da, y
ese hueco se cubre primero con bloqueadores/funciones propios embebidos y luego con B.

**Riesgo de A:** cada TV trae su versión de WebView (las Xiaomi antiguas se quedan en
Chromium viejos y algunas sin Google Play no lo actualizan). Se mitiga con una
**sonda de capacidades al primer arranque** (versión de WebView, códecs, DRM, GMS)
y con un M0 de validación en la pantalla Xiaomi real.

## 4. Estrategia de extensiones (pregunta #2)

1. **v1 — complementos propios embebidos**: Escudos (uBO‑equivalente), Traductor,
   Lector oscuro, Bloqueo de avisos de cookies. Se presentan en la pantalla
   "Complementos" con el mismo formato que una tienda.
2. **Fase 2 — spike con GeckoView** (hito M5): instalar extensiones reales
   (uBlock Origin, Dark Reader) en el motor B y medir peso/rendimiento en el TV.
   Si sale bien, el usuario elige motor en Ajustes → Motor.
3. **Fork de Chromium**: sólo como horizonte lejano si el proyecto crece; se basaría
   en el código de extensiones que Kiwi abrió y que terminó en Edge Canary.

## 5. Arquitectura (stack propuesto)

- **Kotlin**, una sola Activity, **Jetpack Compose for TV** (`tv-material`) para el
  chrome de la app (APIs de foco hechas para D‑pad); el contenido web es un `WebView`
  dentro de `AndroidView`. Si en TVs de 1 GB aprieta el rendimiento, los caminos
  calientes (barra de pestañas, cuadrícula de pestañas) se rehacen en vistas
  clásicas sin tocar el resto.
- **Capa de motor abstracta**: interfaz `WebEngine` (cargar, cookies, peticiones,
  JS, captura, fullscreen). v1: `WebViewEngine`. fase 2: `GeckoEngine`.
- **Datos**: Room (favoritos, historial, descargas, pestañas), DataStore (ajustes),
  `EncryptedSharedPreferences` + Keystore (contraseñas y cofre).
- **Descargas**: `DownloadManager` del sistema para enlaces directos simples y
  descargador propio con OkHttp para lo que necesita cookies, cabecera UA, POST o
  reanudar por Range (los sitios de descargas reales lo exigen).
- **Media**: reproducción en la propia página (WebView) + reproductor nativo
  **Media3/ExoPlayer** como respaldo (modo pantalla completa y HLS/DASH detectados).
- **DI**: Hilt. **Almacenamiento en red**: OkHttp. **Sin servicios de Google
  obligatorios**: voz, traducción y analíticas van con sondas de capacidades y
  alternativas (hay TVs Xiaomi sin GMS).

## 6. Inventario de funciones

**v1 (must):**
- Dashboard configurable (buscador con voz, tiles, reanudar, favoritos, chips)
- Barra de navegación + pestañas (cuadrícula, límite, restaurar cerradas)
- Navegación por elementos D‑pad **y** cursor circular, teclado en pantalla
- Favoritos con carpetas, importar/exportar (bookmarks HTML de Chrome)
- Historial por días + "recién cerradas" + panel superior izquierdo con búsqueda
- Escudos: bloqueo de anuncios/rastreadores + contador + por sitio
- Pestañas privadas + limpieza de datos al salir
- Descargas: gestor con progreso, reanudar, notificación, abrir, borrar
- Capturas de pantalla (ventana y página completa) y guardar como PDF
- Traducir página, contraseñas cifradas, HTTPS‑only, control de UA
- Ajustes por secciones con tooltip/explicación en cada control (requisito)
- Modo oscuro, es‑419 por defecto, i18n completo

**v1.5 (should):** feed de noticias opcional por RSS (apagado por defecto por
privacidad), modo lectura/reducción de texto, gesto de volumen como zoom,
sincronización cifrada (fuera del alcance inicial: sin servidores).

**Después:** motor Gecko con extensiones reales, aire‑mouse/gyroscopio, perfiles
múltiples, bloqueo por listas propias del usuario (hosts), búsqueda de texto en página.

## 7. Navegación con el control remoto

Cuatro modos, conmutables (menú largo en OK o en Ajustes):

1. **Elementos** (defecto): el D‑pad mueve el foco; el WebView reparte las flechas y
   una capa JS propia correge sitios que no son navegables por teclado (cálculo del
   siguiente candidato por geometría, como hace TV Bro).
2. **Cursor**: OK mantenido activa un **retículo circular** que el D‑pad mueve con
   aceleración; OK = clic, atrás = salir, mantener = clic derecho; también sirve para
   sitios densos (tablas, carruseles).
3. **Lectura/scroll**: flechas = scroll por bloques + zoom de texto, para páginas de
   artículos.
4. **Voz**: botón del micrófono del mando (sonda de GMS; si no hay, se oculta).

Extras: teclado en pantalla tipo leanback con predicciones, compatibilidad con
teclados USB/Bluetooth, foco siempre visible (anillo ámbar ≥ 56 dp), zonas seguras
de sobrepantalla (overscan) de 48 dp, y un "mapa de foco" para que nunca se pierda
en iframes o en páginas enormes.

## 8. Privacidad y bloqueo (los escudos)

- **Motor de filtrado propio**: matcher de peticiones sobre listas EasyList,
  EasyPrivacy y Fanboy (actualización en aplazado + empaquetado por defecto),
  aplicado en `shouldInterceptRequest` (nivel de red) + inyección JS para filtrado
  cosmético (ocultar esqueletos de anuncios ya cargados).
- **Tres niveles por sitio** (Estándar / Agresivo / Apagado) con contador de
  bloqueos en la barra, igual que Brave.
- Cookies de terceros bloqueadas en nivel estándar; "limpiar datos al cerrar la
  pestaña privada"; lista blanca por sitio persistida.
- HTTPS‑only con pantalla de aviso; control total de cabeceras (UA, Do Not Track),
  sin telemetría propia por defecto.
- Si en fase 2 llega GeckoView, el filtro pasa a usar el propio motor y/uBO real.

## 9. Vídeo y códecs ("compatible con todos los videos")

| Soporte | Origen |
|---|---|
| H.264/AVC, AAC | Siempre (todo Android) |
| HEVC/H.265, AC3/EAC3 | Decodificación por hardware del TV (los Xiaomi la traen) |
| VP9, AV1 | Si el chip lo trae (MediaCodec); fallback por software si el motor lo tiene |
| MSE + EME/Widevine | Plataforma (nivel L1/L3 según el TV; sondear en M0) |
| HLS / DASH | WebView moderno; respaldo con Media3 (ExoPlayer) |
| Fullscreen | `onShowCustomView` → reproductor propio con controles de mando |
| Descargar y ver local | Media3 abre MKV/MP4 que el WebView no abre |

Estrategia: **primero la página, después nuestro reproductor**. Si el stream es
HLS/DASH o la página no reproduce bien con el mando, se detecta la URL del medio y
se abre en el reproductor nativo (patrón del proyecto open source tv‑browser / TV Bro,
que además usamos como referencia de UX de remoto).

## 10. Descargas (vital, según el brief)

- Gestor propio: cola con pausa/reanudar/borrar/reintentar, progreso en notificación
  y en el panel lateral; archivo grande sin cargar todo en RAM (streaming a disco).
- Compatibilidad con sitios de descargas: cookies de sesión y UA reenviados,
  soporte de POST, `Content-Disposition`, varios enlaces en paralelo, reanudado por
  `Range`, enlaces `blob:` (puente JS que exporta el Blob), y detección de
  enlaces directos que el DownloadManager del sistema gestione mejor.
- Persistencia en Room: historial de descargas con estado, ubicación y tamaño;
  abrir con la app que toque (APK, ZIP, PDF, vídeo) vía FileProvider + MediaScanner.
- Ajustes: carpeta de destino, límite simultáneo, solo con Wi‑Fi, borrar
  automáticamente el archivo al abrirlo (opcional).

## 11. Datos guardados

- **Favoritos**: árbol con carpetas, icono + miniatura, orden manual, import/export
  bookmarks HTML, acceso desde el dashboard y desde la barra lateral.
- **Historial**: Room con índice por fecha; panel superior izquierdo (tipo Edge) con
  secciones Hoy / Ayer / 7 días / Recién cerradas (pila de pestañas y ventanas
  cerradas con "restaurar"), búsqueda global sobre URL+título, borrar por día.
- **Contraseñas**: **no hay cofre propio** (decisión P0). Se usa lo que ya da el
  sistema: *Autofill Framework* (API 26+) para rellenar y guardar los formularios
  del WebView con el gestor del televisor (Google Password Manager, Bitwarden…),
  y *Credential Manager* (`androidx.credentials`, con su fallback) para passkeys.
  Regla: si no hay proveedor (Android 7 o TV sin GMS) la sección **se oculta** y se
  explica dónde gestionarlas — sonda en «Información del sistema» y en la bitácora.
  Sin cuenta, sin servidor y sin sincronización propia.
- **Limpieza**: pantalla única con casillas (historial, caché, cookies, contraseñas,
  descargas, datos de sitios) + "al cerrar pestañas privadas" + "al salir".

## 12. Capturas, PDF y traducción

- **Captura de ventana**: `PixelCopy` sobre nuestra propia ventana → sin permisos.
- **Captura de página completa**: se recorre el contenido con `WebView.draw` por
  bloques y se compone un bitmap (propio de la app, también sin permisos).
- **Pantalla completa del TV**: sólo hace falta `MediaProjection` si algún día se
  captura fuera de la app; en Android 14+ exige consentimiento cada vez → no es el
  flujo principal.
- **Guardar como PDF**: `WebView.createPrintDocumentAdapter` → `PrintManager` →
  PDF en Descargas + compartir vía FileProvider. Mismo flujo para "Imprimir".
- **Traducir**: proveedor enchufable — ML Kit on‑device si hay GMS (paquetes de
  idioma descargables), API cloud (p. ej. Lingvanex/DeepL con clave) si no; botón
  en la barra + "traducir automáticamente si el idioma difiere". Decisión #3.

## 13. Rendimiento y livianea (presupuesto medible)

Objetivos a validar en M0 con la pantalla Xiaomi:

| Métrica | Objetivo |
|---|---|
| APK (arm64, v1) | < 45 MB |
| Arranque en frío hasta el dashboard | < 1.5 s en TV Amlogic 2 GB |
| RAM con 4 pestañas abiertas | < 350 MB |
| Idle sin pestañas | < 150 MB |
| Frames al mover el foco | sin caídas perceptibles (10-foot UI) |

Técnicas: R8 + resource shrinking, sin dependencias pesadas innecesarias, pestañas
en segundo plano congeladas (`saveState`/`restoreState`), límite de pestañas
configurable, sin blur ni fondos de vídeo, animaciones cortas y respetando
`prefers-reduced-motion`, Baseline Profile si el arranque se queda corto.

## 14. Compatibilidad y distribución (APK para Android TV / Xiaomi)

- **minSdk 24** (Android 7) → cubre Android TV 7/8/9/11/12/14; los Xiaomi actuales
  (Google TV sobre Android 12/14) y los viejos PatchWall (Android 9/11).
- Manifest TV: intent `LEANBACK_LAUNCHER`, banner 320×180, `leanback` **no
  requerido** (que también funcione en móvil/tablet), sin `touchscreen` requerido,
  manejo de D‑pad/remote en todas las vistas, `android:isGame=false`, overscan.
- **Sin dependencia obligatoria de Google Play Services**: sondear GMS en el primer
  arranque (voz, traducción on‑device) y ocultar lo que no esté disponible.
- Distribución: APK firmado para sideload (adb over Wi‑Fi: Xiaomi → Opciones de
  desarrollador → depuración ADB) +, si más adelante se quiere Play Store, AAB con
  targetSdk actual (política TV exige API objetivo reciente).
- Sonda de capacidades al arranque: versión de WebView, códecs soportados
  (`MediaCodecList`), nivel Widevine, presencia de GMS → pantalla "Acerca de" con
  el diagnóstico, útil para soporte.
- **Dónde vive el APK (convención fijada por el usuario)**: cada
  `./gradlew assembleDebug` / `assembleRelease` copia el resultado a **la raíz del
  repo** como `Antena-<versionName>-<variant>.apk` (vigente:
  `Antena-0.2.0-m1-debug.apk`). Las copias de versiones anteriores se retiran al
  compilar (sólo vive la versión vigente en la raíz); la copia intermedia queda en
  `app/build/outputs/apk/debug/app-debug.apk`. Ese APK de la raíz es el que se
  instala con `adb install -r`.

## 15. Dirección de diseño (10-foot UI)

**Contexto**: público = persona a 3 metros del TV con un mando de 12 botones; el
trabajo del diseño es que todo se lea y se navegue sin mouse, de noche, en hardware
lento. El sujeto del producto es la *señal*: broadcast, sintonía, escudos.

**Paleta** (oscura por razones funcionales —sala, panel LED—, no por moda):

| Token | Hex | Uso |
|---|---|---|
| `noche` | `#0A0D12` | Fondo base |
| `casco` | `#161B24` | Superficie elevada (barra, paneles) |
| `fosforo` | `#ECE6D8` | Texto primario (blanco cálido de fósforo, no #FFF) |
| `ambar` | `#E9A13B` | **Único acento**: foco, retículo, acciones — ámbar de sintonizador |
| `azul` | `#5A78AD` | Secundario apagado (bordes, metadatos, enlaces fríos) |
| `escudo` | `#7FB08A` | Sólo el contador de bloqueos (verde salvia, no neón) |

**Tipografía** (dos familias, cargadas como recursos para no depender del TV):

- **Atkinson Hyperlegible** — UI, títulos y cuerpo. Elegida por legibilidad a
  distancia y con baja visión (razón específica del producto, no la fuente
  por defecto de siempre). Display en Bold a 32/48 sp; cuerpo 18 sp mínimo (10‑foot).
- **IBM Plex Mono** — URL, contadores, tamaños y cifras (retroceso a la esfera de
  canales / slate de producción). Sólo ahí.
- Escala: 14 (metadatos) / 18 (cuerpo) / 24 (títulos de panel) / 32 (sección) /
  48 (saludo del dashboard). Interlineado holgado en cuerpo (1.5).

**Layout**: rejilla de 6 columnas, márgenes seguros de 48 dp, alineación a la
izquierda salvo el buscador del dashboard (centrado, como punto de entrada único);
rail de iconos a la izquierda; nada depende de hover.

```
DASHBOARD                    HISTORIAL (panel izquierdo)      PESTAÑAS
┌──┬──────────────────────┐   ╔═══════════════╦═══════════┐   ┌────┬────┬────┐
│▣ │  Hola, buenas noches │   ║  Historial     ║  vista    │   │ ●  │    │    │
│his│  [ 🔍 buscar o decir ]│  ║  Hoy · Ayer   ║  de la    │   ├────┼────┼────┤
│fav│  ── tiles ────────── │   ║  7 días       ║  página  │   │    │    │    │
│dl │  [yt] [wiki] [news] │   ║  ────────────  ║  (peek)   │   ├────┼────┼────┤
│cfg│  Reanudar: ─────     │   ║  Recién       ║           │   │    │ +  │    │
│   │  [sesión] [video]   │   ║  cerradas ↺   ║           │   └────┴────┴────┘
└──┴──────────────────────┘   ╚═══════════════╩═══════════╝
     ▭ foco ámbar 56 dp, retículo circular en modo cursor
```

**Principios:**
1. Primero el foco: toda pantalla se recorre con D‑pad y el foco nunca se pierde.
2. Legible a 3 m: 18 sp mínimo, contraste AA, objetivos ≥ 48 dp.
3. Chrome callado, **un solo momento ruidoso**: el retículo ámbar + el contador de
   bloqueos que sube al cargar la página.
4. Barato en hardware: sin blur, sin sombras múltiples, sin degradados de fondo;
   divisor de tres franjas discretas (guiño a las barras de color de sintonía) como
   único ornamento estructural.
5. Copy en es‑419, verbos claros, y tooltip explicando qué cambia en **cada**
   control de Ajustes (requisito explícito del brief).

## 16. Roadmap (hitos con criterios de aceptación)

| Hito | Contenido | Criterio de aceptación |
|---|---|---|
| **M0 · Sonda (1–2 sem)** | APK vacío con banner TV; sonda de capacidades en la Xiaomi real; prototipo de retículo + foco; prueba de códecs/DRM (páginas de prueba HLS/DASH/Widevine) | Informe: versión WebView, códecs, DRM, GMS, FPS al mover foco. **Puerta de decisión: confirmar motor A** |
| **M1 · Navegación** | Barra + URL, back/forward, pestañas (cuadrícula), dashboard v1, modos Elementos/Cursor, teclado en pantalla | Se navega YouTube, Wikipedia y un sitio denso solo con el mando |
| **M2 · Escudos** | Motor de filtrado (EasyList + cosmético), contador, whitelist por sitio, pestañas privadas, HTTPS‑only, limpieza de datos | Anuncios bloqueados en 5 sitios de prueba; datos borrados al cerrar privado |
| **M3 · Datos** | Favoritos con carpetas + import, historial por días, panel "recién cerradas", búsqueda global, panel superior izquierdo | Cerrar pestaña → restaurar desde el panel en ≤ 2 pulsaciones |
| **M4 · Descargas + documentos** | Gestor con reanudar, notificación, blob:, capturas (ventana y página), PDF | Descarga grande >1 GB reanuda tras cortar Wi‑Fi; PDF generado se abre fuera |
| **M5 · Credenciales + traducción + spike Gecko** | Autofill del sistema y passkeys (`androidx.credentials`), traducción (ML Kit/cloud), pantalla de Complementos, **spike GeckoView con uBO real** | Informe de peso/rendimiento/extensiones → decisión de motor fase 2 |
| **M6 · Pulido 10-foot** | Sistema de diseño completo, ajustes con tooltips, i18n, rendimiento, guion de pruebas en el TV | Presupuestos de §13 cumplidos; beta instalable por adb |

Estimación orientativa: M0–M2 son el camino crítico; cada hito deja APK instalable.

## 17. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| WebView del TV viejo rompe sitios | Sonda + M0; fallback: recomendar/actualizar WebView, y horizonte motor Gecko |
| Extensiones no posibles en WebView | Bloqueo propio en v1 (cubre el 90 % real) + spike Gecko en M5, comunicado con claridad |
| DRM/códecs fallan en ciertos TV | Matriz de pruebas de streams en M0; reproductor nativo Media3 como respaldo |
| Sin GMS en algunos modelos | Cero dependencias obligatorias de GMS; funciones se ocultan si no hay |
| RAM baja con muchas pestañas | Límite + congelado de pestañas en segundo plano |
| Sitios que exigen mouse | Cursor circular + capa JS de foco por geometría |
| Alcance gigante (Edge+Brave es mucho) | MoSCoW estricto: v1 = §6 must; el resto vive en la columna "después" |

## 18. Preguntas abiertas para el siguiente comentario

1. **Motor**: ¿aceptas la recomendación A (WebView) + spike Gecko en M5, o quieres
   arrancar directo con GeckoView por las extensiones?
2. **Extensiones en v1**: ¿los "complementos propios" embebidos te bastan para la
   primera entrega, o las extensiones reales son bloqueador para lanzar?
3. **Traducción**: ¿ML Kit on‑device (requiere GMS) o API cloud con clave?
4. **Modelo exacto de tu Xiaomi** para fijar la matriz de pruebas (y Android TV
   mínimo: ¿dejamos minSdk 24 o subimos a 26/28?).
5. **Nombre**: ¿Antena, Vigía, Faro u otro? (afecta a icono, banner y copy)
6. **Distribución**: ¿sólo APK por adb, o también Google Play a futuro?
7. **Dashboard**: ¿feed de noticias incluido o sólo enlaces propios (más privado)?

## 19. Tanda de mejoras post‑M1 (lista aprobada, paso a paso)

Retroalimentación del usuario tras probar la nueva interfaz (reorganización de UI
con tabs tipo Edge, dashboard y registro de descargas). Correcciones ya incorporadas:
la fila de dirección se acorta de **altura** (no de ancho), y el botón flotante
"ir arriba" debe poder desactivarse desde Barra de herramientas. Se ejecuta en
fases; cada fase deja APK instalable y verificado en el emulador.

### Fase 1 · Rediseño 10‑foot (solo visual)

| # | Cambio | Criterio de aceptación |
|---|---|---|
| 1.1 | Tabs: quitar forma trapecial y borde; **solo una línea divisoria** entre tabs | Sin contornos en los chips; separación visible de 1 px |
| 1.2 | Tabs **mucho más pequeños** (alto y fuente) | La fila de tabs ocupa ~½ del alto actual |
| 1.3 | **`+` sin borde**; ningún botón con borde en la fila superior | Todos los controles sin stroke/outline |
| 1.4 | Fila de dirección **sin bordes** en `←` `↻` `⌂` ni en el campo URL | Iconos "fantasma" sobre el casco |
| 1.5 | **Altura única**: campo de dirección, botones de iconos y tabs **comparten la misma altura**, más baja que hoy | Todos miden lo mismo; nunca más altos que los botones |
| 1.6 | Fuente de la URL: salir de IBM Plex Mono por una **sans normal** (fuente UI de Antena), tamaño menor | URL legible, sin look de consola |
| 1.7 | **Iconos nuevos** para `←` `↻` `⌂` y el resto (catálogo limpio, ej. Material Symbols) | Iconos consistentes, sin adornos |
| 1.8 | Botón ☰: **quitar la palabra "Menú"**, solo las rayías | Icono solo |
| 1.9 | **Botón flotante** abajo‑derecha, transparente, "ir al inicio de la página"; **interruptor en Barra de herramientas** para activarlo/desactivarlo | Aparece solo al hacer scroll; se puede ocultar desde el menú |

### Fase 2 · Scroll inteligente (más espacio para la página)

| # | Cambio | Criterio de aceptación |
|---|---|---|
| 2.1 | Al deslizar **hacia abajo** se oculta la fila de dirección | Página gana el alto de la barra |
| 2.2 | En ese estado los tabs se reducen **a la mitad** (con su fuente) | Tabs mínimos mientras se lee |
| 2.3 | Al llegar **arriba** de la página, la fila se vuelve a desplegar y los tabs recuperan tamaño | Transición tipo "parte de la página" |
| 2.4 | El foco remoto no se pierde al ocultarse/mostrarse la fila | D‑pad sigue funcionando en todo momento |

### Fase 3 · Barra de direcciones funcional

| # | Cambio | Criterio de aceptación |
|---|---|---|
| 3.1 | Tocar la barra **no borra la URL**: conserva el texto actual, editable; cancelar restaura | Nunca queda vacía sin querer |
| 3.2 | **Desplegable de sugerencias en vivo**: historial + sitios escritos antes, filtrando conforme se digita | Aparece bajo el campo mientras se escribe |
| 3.3 | **Favorito instantáneo**: ★ guarda y se refleja de inmediato en Favoritos y dashboard | Sin espera perceptible |
| 3.4 | **Historial inmediato**: se registra al confirmarse la carga de la página | Al entrar a un sitio aparece en Historial de inmediato |

### Fase 4 · Descargas multiarchivo (profundidad)

| # | Cambio | Criterio de aceptación |
|---|---|---|
| 4.1 | Link a **PDF**: tocarlo **lo muestra en el lugar** (visor embebido) **y** lo descarga en segundo plano | El usuario no sale de la app |
| 4.2 | **Word / Excel / otros**: descarga + **Abrir con** (selector de apps instaladas) | Mismo flujo del selector del sistema |
| 4.3 | **Vídeo / audio**: link abre un **modal reproductor** (con opción de ventana completa) | Reproduce dentro de Antena |
| 4.4 | **Imágenes y cualquier archivo descargable** entran al registro | Aparecen en Descargas al terminar |
| 4.5 | Registro de descargas end-to-end: **Abrir con / Eliminar / Ir a carpeta** | Las 3 acciones probadas en el emulador |
| 4.6 | Arreglar que hoy **los PDF en enlaces no abren ni descargan** | Prueba con un PDF real |

### Fase 5 · Menú contextual ("clic derecho" / mantener pulsado)

Se abre con mantener OK (TV) o long‑press (tablet) sobre un enlace o imagen:

| # | Opción | Notas |
|---|---|---|
| 5.1 | **Copiar vínculo** | Portapapeles |
| 5.2 | **Guardar vínculo como…** | Usa el tipo real del archivo (PDF→PDF, video→video) |
| 5.3 | **Abrir en pestaña nueva** | También "abrir en pestaña nueva (fondo)" si hay espacio |
| 5.4 | **Guardar imagen** / **Copiar imagen** | Sobre imágenes |
| 5.5 | **Copiar vínculo de la imagen** | Sobre imágenes |
| 5.6 | **Búsqueda visual de la imagen** (Google Lens u otro) | Deseable; abrir búsqueda inversa en pestaña |

### Fase 6 · Bitácora de la aplicación

| # | Cambio | Criterio de aceptación |
|---|---|---|
| 6.1 | **Registro** visible desde el menú: errores internos, sitios visitados, eventos de página y del sistema | Se puede leer qué pasó sin conectar adb |
| 6.2 | Se usa para diagnosticar los sitios que fallan (ver Fase 8) | Entrada con timestamp por evento |

### Fase 7 · Ajustes: Barra de herramientas (en el menú)

| # | Cambio | Criterio de aceptación |
|---|---|---|
| 7.1 | Interruptores para mostrar/ocultar: **Favoritos, Historial, Descargas, Botón flotante** | El cambio se aplica al instante |
| 7.2 | **Atrás, Recargar e Inicio siempre fijos** | No se pueden ocultar |
| 7.3 | Diseñado para **agregar más botones** sin tocar el menú otra vez | Lista de herramientas declarativa |

### Fase 8 · Compatibilidad de sitios con publicidad

| # | Cambio | Criterio de aceptación |
|---|---|---|
| 8.1 | Diagnosticar sitios como **animeflv**: User‑Agent, cookies, localStorage, popups/ventanas nuevas | Se carga igual que en otros navegadores de TV |
| 8.2 | Lista de sitios de prueba + comparación | Al menos 5 sitios densos cargan sin error |

**Lista de sitios de prueba (8.2)** — verificado en el emulador API 33, 6/10/2026:

| Sitio | URL | Resultado |
|---|---|---|
| Página local de pruebas | `http://10.0.2.2:8877/fase8.html` | localStorage ✓ · cookies ✓ · `window.open()` abre pestaña ✓ |
| Wikipedia | `https://es.wikipedia.org/wiki/Android` | Carga sin error ✓ |
| BBC Mundo | `https://www.bbc.com/mundo` | Carga sin error ✓ |
| AnimeFLV | `https://animeflv.lat/` | Carga sin error ✓ (el sitio ya no está en `m.animeflv.lat`) |
| YouTube (móvil) | `https://m.youtube.com/` | Carga sin error ✓ |
| Marca | `https://www.marca.com/` | Carga sin error ✓ (sitio denso con publicidad) |
| Reddit | `https://www.reddit.com/` | No probado en emulador: swiftshader se cae (limitación del emulador, no de la app) |

Cambios que hizo posible la Fase 8 (en `WebTab.kt` / `BrowserScreen.kt`):

- UA de escritorio: se elimina `; wv` y `Version/4.0` del UA de WebView → los sitios no ven "WebView".
- Cookies + cookies de terceros + `databaseEnabled` + `mixedContentMode = COMPATIBILITY_MODE`.
- `setSupportMultipleWindows` + `javaScriptCanOpenWindowsAutomatically` + `onCreateWindow`:
  `target=_blank` y `window.open()` crean una pestaña nueva (al frente si hay gesto del usuario;
  en segundo plano si es un popunder de publicidad) y `window.close()` la cierra.
- Límite de 8 pestañas para no perseguir popunders infinitos (se registra en la Bitácora).

### Fase 9 · "Buscar pestañas" (estilo Edge)

| # | Cambio | Criterio de aceptación |
|---|---|---|
| 9.1 | Botón arriba‑izquierda **antes de `←`** → panel "Buscar pestañas" | Accesible con 1 pulsación |
| 9.2 | **Buscador de texto** sobre títulos/URLs vistos | Filtra mientras se escribe |
| 9.3 | Secciones desplegables: **"Pestañas abiertas"** y **"Cerrado recientemente"** | Estilo Edge |
| 9.4 | Agrupado **por día, de más nuevo a más viejo** | Encabezados por fecha |
| 9.5 | Fuera de alcance: organizar pestañas, áreas de trabajo | No se tocan |

### Orden de ejecución

1. **Fase 1 + 2 + 3** (lo que se ve para la presentación).
2. **Fase 4 + 5** (descargas multiarchivo y menú contextual).
3. **Fase 7 + 6** (barra de herramientas y bitácora).
4. **Fase 8 + 9** (compatibilidad y buscar pestañas).

**Fuera de esta tanda:** quitar `-debug` del nombre del APK (próxima sesión);
probar acceso a los buscadores desde el menú (lo pone a prueba el usuario).

---

## 20. Spike GeckoView (M5) — resultado · 8/10/2026

Se ejecutó el spike planeado en §4/§16 (M5) en un módulo aislado `:spike`
(`com.antena.tv.spike`, sin tocar `:app`). Verificado en el emulador API 33.

### Qué se demostró ✓

| Objetivo del spike | Resultado |
|---|---|
| Cargar páginas con GeckoView | ✓ Wikipedia carga con título correcto, FATAL=0 |
| Mando D-pad sobre la página | ✓ `DPAD_DOWN` → scroll (y=285) informado por la extensión |
| Extensión empaquetada (`ensureBuiltIn`) | ✓ Banner "EXTENSIÓN GECKO ACTIVA ✓" visible en la página |
| Native messaging (content → app) | ✓ Mensajes `{tag, y, ext, title, url}` llegan a la app |
| **uBO real por `install(url)` desde AMO** | ✓ `Loading extension 'uBlock0@raymondhill.net'` en GeckoConsole; sólo warnings de manifest (permiso `menus` no existe en Android), sin errores |
| Usuario ingresa la extensión que quiera | ✓ `WebExtensionController.install(urlXpi)` instaló uBO desde `addons.mozilla.org` — el camino para la pantalla "Complementos" |

### Coste medido

| Métrica | Valor |
|---|---|
| APK arm64-v8a (TV) | **180.5 MB** (x86_64: 200 MB; universal 4 ABIs: 686 MB) |
| Memoria (PSS, 1 pestaña + uBO + ext. de prueba) | **207 MB** vs **95 MB** de Antena con WebView → 2.2× |
| minSdk exigido por GeckoView | **26** (hoy minSdk=24 → decisión §18.4) |
| Versión usada | `geckoview:141.0.20250806102122` (release) — la última compatible con Kotlin 2.1.20/AGP 8.11.1/SDK 35 |
| Toolchain para GeckoView actual (153+) | Kotlin ≥2.2 + AGP 9.1 + compileSdk 37 (subida grande) |

### Conclusión y decisión pendiente

- **Factibilidad técnica: ALTA.** Extensiones reales (uBO incluido) funcionan en
  Android vía GeckoView, con la API `install(url)` lista para que el usuario
  cargue la que quiera.
- **Coste: APK +170 MB, RAM +112 MB, minSdk 26, y una migración completa de
  `WebTab.kt` a `GeckoSession`** (o dos flavors WebView/Gecko con matriz de
  pruebas doble).
- **Siguiente paso sugerido (decisión del usuario):** migración piloto de UNA
  pestaña a GeckoView dentro de `:app` (interfaz `MotorWeb`) antes de
  comprometer el motor completo; o bien mantener WebView + Escudos (M2) y
  reevaluar en M5 completo.
- Pendiente de una fase posterior: verificar bloqueo real de anuncios de uBO en
  una página con publicidad y medir rendimiento en el TV físico (no el emulador).

### Menú «Complementos» en el spike · 9/10/2026 (spike-2)

Se añadió al `:spike` la barra de herramientas (**🧩 Complementos**, **↻
Recargar**) y un panel (tecla MENU o botón) que administra las extensiones:

| Función | Cómo | Verificado en emulador |
|---|---|---|
| Listar instaladas | `WebExtensionController.list()` | ✓ 3 filas (Prueba, uBO, Dark Reader) con nombre, id, versión y estado |
| Activar / desactivar | `enable()` / `disable()` · `EnableSource.USER` | ✓ ENTER alterna; **persistido en `extensions.json`** (`userDisabled:true/false`, `active`) y reflejado en la UI (`● ACTIVA` / `○ DESACTIVA`) |
| Descargar de la tienda | Fila «⬇ … — instalar desde AMO» → `install(urlXpi)` | ✓ Dark Reader 4.9.133 descargada y montada desde `addons.mozilla.org` |
| Instalar URL libre | Diálogo con URL editable (`.xpi` de AMO u otra) | ✓ `install OK: addon@darkreader.org` |
| Ir a la tienda | Fila «🛒 Explorar la tienda» → navega a `addons.mozilla.org/es-ES/firefox/extensions/` | ✓ página cargada en el propio spike |
| Desinstalar | Fila «✕ Desinstalar» + diálogo de confirmación → `uninstall()` | ✓ Dark Reader eliminada de la lista y del disco |
| Navegación con mando | D-pad mueve el foco entre filas; ENTER activa; BACK cierra | ✓ |

**Hallazgo clave (bug real resuelto):** sin `WebExtensionController.PromptDelegate`
no hay "listener" para `GeckoView:WebExtension:InstallPrompt` y **todo `install()`
falla** (`InstallException`) — por eso uBO no aparecía en `list()` aunque estuviera
en `addons.json`. Registrando un delegate que concede los permisos que pide el
usuario al pulsar «Instalar», el ciclo completo descarga→monta→lista→activa→
desactiva funciona de punta a punta. También: el foco debe pedirse en el frame
posterior al layout (una vista de tamaño 0 rechaza `requestFocus`).

Alcance/limitaciones: los permisos se conceden **automáticamente** (en el producto
real habría un diálogo de permisos estilo Firefox); instalación probada sólo con
`.xpi` firmados de AMO; pruebas en emulador API 33 (no TV físico).

### GeckoView + «Complementos» en `:app` (0.3.0-m3) · 9/10/2026

Decidido por el usuario: llevar el pilot de fase 2 a la app real (§20, "decisión
pendiente") sin romper el motor actual. WebView sigue siendo el motor por
defecto; GeckoView entra como **motor de pestañas dedicado** con su panel de
extensiones dentro de Antena.

| Cambio | Detalle |
|---|---|
| `app/build.gradle.kts` | `minSdk 24 → 26`, `geckoview:141…` (misma versión del spike), `abiFilters` con `-Pabi=arm64-v8a` para el TV (por defecto x86_64) |
| `gecko/GeckoMotor.kt` | `GeckoRuntime` singleton + `WebExtensionController` con **PromptDelegate** (lección clave del spike: sin él, todo `install()` falla) + estado `StateFlow` de extensiones |
| `gecko/GeckoTab.kt` | Pestaña embebible: `AndroidView(GeckoSession)` con scroll por D-pad y callbacks de URL/progreso |
| `browser/ComplementosPage.kt` | Panel: listar (● ACTIVA/○ DESACTIVA), alternar (`enable/disable`), desinstalar con confirmación, instalar desde AMO o URL `.xpi` libre, ir a la tienda, «🧩 Abrir pestaña con motor Gecko» |
| `Overlay.Complementos` en ☰ | La entrada **Complementos** vive en el menú desplegable de la barra de direcciones (junto a Favoritos/Historial/Descargas) |
| `assets/gecko-ext/` | Extensión interna `navegador@antena.tv` (banner + native messaging) instalada con `ensureBuiltIn` |

**Verificado en emulador (API 33), de punta a punta:** menú ☰ → Complementos
abre el panel; la extensión interna aparece ACTIVA y el toggle la desactiva y
reactiva (eventos `OnDisabling/OnDisabled`, persistido); «Abrir pestaña con
motor Gecko» carga `addons.mozilla.org` con la extensión ligada a la sesión
(`session=GeckoSession@…`); FATAL=0.

**Bug preexistente corregido de paso:** `AddressBar` hacía
`starFr.requestFocus()` con la flecha → aunque la estrella no estuviera compuesta
(sólo se renderiza si la URL empieza por `http`) → `IllegalStateException` que
mataba la app. Guard añadido en `BrowserScreen.kt`.

Pendiente (fase siguiente): verificar bloqueo real de anuncios de uBO en una
página con publicidad, medir RAM/APK en el TV físico (x86_64=208 MB medido en
emulador; arm64 con `-Pabi=arm64-v8a`), y decidir el swap completo del motor
(interfaz `MotorWeb` / migrar `WebTab.kt` a `GeckoSession`).

### Chip 🛡 con el contador real de uBO + fin del motor WebView (0.3.0-m4) · 12/10/2026

Cierre del swap: **GeckoView es el único motor** (la última WebView — el
reproductor de `MediaPlayerScreen` — pasó a GeckoView con sesión propia sobre el
runtime compartido, y la sonda M0 quedó etiquetada como «WebView del TV ·
informativo»). `GeckoTab.kt` (gemelo muerto de `WebTab`) borrado.

| Pieza | Qué hace |
|---|---|
| `WebExtension.ActionDelegate` (registrado en `GeckoMotor` y por sesión con `enganchaAcciones`) | Gecko **descartaba en silencio** los contadores por pestaña sin delegate: uBO no llegaba nunca al chip. Ahora cada `onBrowserAction` actualiza el badge por sesión |
| `WebExtensionController.setTabActive` | Gecko no adivina cuál es la pestaña visible: sin marcarla, `tabs.activeTab` da null y el clic del chip revienta dentro de GeckoView («nativeTab is null», `ext-android.js wrapTab`) → ni contador ni panel |
| `GeckoMotor.fijarSesion()` | El popup de uBO se abre en **su propia pestaña** (GeckoView no tiene panel flotante). Si esa pestaña queda «activa», uBO resuelve `tabs.getCurrent()` contra ella y el panel muestra «0 bloqueados en esta página»: se fija la sesión de origen mientras el panel está en pantalla |
| `WebTab(laAbreGecko = true)` | `OnNewSession` (target=_blank / window.open / el propio botón de AMO) **exige recibir la sesión SIN abrir**; Gecko la abre él con su id interno. Devolver una sesión ya abierta lanzaba `AssertionError: Must use an unopened GeckoSession instance` y mataba la app |
| `onDownload` → `.xpi` | El clic en «Agregar a Firefox» de AMO navega al `.xpi`; la app lo monta con `install(url)` + `PromptDelegate` |

**Verificado en emulador (API 33), de punta a punta:**

| Prueba | Resultado |
|---|---|
| Contador por pestaña | `marca.com`: chip **🛡 18** = anuncios que uBO lleva bloqueados en esa pestaña (en `example.com`, sin anuncios, sólo 🛡) |
| Clic en el chip → panel | Panel de uBO en pestaña: **«Blocked on this page 18 (6%)»**, «Domains connected 22 of 32», zapper/picker/dashboard — antes salía 0 |
| Instalar desde AMO en pestaña | Botón «Agregar a Firefox» → `.xpi` → `install OK: sponsorBlocker@ajay.app v6.1.7`, aparece en Complementos y se alterna ACTIVA/DESACTIVA |
| Ventana emergente (target=_blank) | «Central del desarrollador» (AMO) abre pestaña nueva **sin crash** (antes: FATAL por la sesión ya abierta) |
| Plegado de la barra al hacer scroll | `GeckoSession.ScrollDelegate` dispara y la barra se pliega (`example.com` y `marca.com`) |
| Reproductor | `mov_bbb.mp4` abre el reproductor nativo de Gecko en la superposición «Reproducir» |
| Limpieza | Cinta diagnóstica «BADGE: SIN API» retirada del content script; `background.js` (badge de prueba) borrado; sin `FATAL EXCEPTION` en ninguna prueba |

Límite conocido: los permisos de la extensión se conceden **automáticamente**
(el clic del usuario ya es el consentimiento) — en el producto final haría
falta el diálogo de permisos estilo Firefox.

### Favoritos en la página de inicio (0.3.0-m5) · 14/10/2026

El usuario reportó dos cosas: los favoritos no se veían en la página de inicio
y había favoritos prefabricados que no podía eliminar. Al probarlo aparecieron
tres causas raíz:

| Pieza | Qué hace |
|---|---|
| Guard en `webTab()` → `onUrl`/`onTitle` (`BrowserScreen`) | `goHome()` carga `about:blank` en la sesión (para cortar el sonido de la página anterior) y el callback de ubicación **pisaba** `antena://inicio`: el dashboard desaparecía y «Inicio» se quedaba en una página en blanco **sin favoritos** — el fallo reportado. Ahora, mientras la pestaña está en `antena://`, las ubicaciones `about:` y los títulos se ignoran; el resto (p. ej. volver atrás) se acepta con normalidad |
| `rememberCenterHandler` (`Panels`) | El sistema **reenvía la tecla** mientras se mantiene (key repeat) y cada reenvío reiniciaba el cronómetro: «mantener OK» nunca llegaba a los 500 ms y se registraba como clic corto — por eso no se podía eliminar ningún favorito. Sólo cuenta el primer KeyDOWN (el mismo criterio que `MainActivity` con `repeatCount == 0`); un down huérfano de más de 5 s se descarta |
| `DashboardPage` | Rejilla con la ficha **«+ Añadir»** como primera celda (abre el modal de favorito), mantener OK elimina, `verticalScroll` para cuando haya muchas filas y estado vacío amable |
| `Favicons.kt` (nuevo) | Favicon con caché en memoria (DuckDuckGo → Google s2 → inicial del nombre, fallos también cacheados). `FaviconBox` se usa en las fichas del inicio y en las filas del panel de Favoritos |
| `Overlay.Star` desde `antena://` | El modal arranca en blanco (antes precargaba «Inicio» / `antena://inicio`) y una dirección sin esquema se guarda como `https://…` |
| `LaunchedEffect(showDashboard)` | El dashboard refresca los favoritos cada vez que se muestra |

**Verificado en emulador (API 33), de punta a punta:**

| Prueba | Resultado |
|---|---|
| Página → chip Inicio | GitHub → Inicio: dashboard con las 6 fichas, barra `antena://inicio` y título «Inicio» (antes: `about:blank` sin favoritos) |
| Atrás desde el dashboard | Vuelve a `https://github.com/`; pulsar Inicio de nuevo regresa al dashboard |
| Añadir | Ficha «+ Añadir» → modal en blanco → «Prueba» / `example.com` → aparece en la rejilla al guardar |
| Eliminar | Mantener OK (evento crudo de 1 s, con el mismo timing que el mando) → «Prueba» desaparece; el resto queda intacto. Antes del fix el mismo gesto abría la página |
| Clic corto | Sigue abriendo la ficha (Reddit, GitHub) |
| Logos | GitHub, MDN, Reddit, Wikipedia y YouTube con su favicon real; sin favicon → inicial del nombre (p. ej. «Prueba») |
| Crashes | 0 `FATAL EXCEPTION` en toda la pasada |

Nota de prueba: `input keyevent --longpress` del emulador dura ~400 ms y por eso
no sirve para probar «mantener OK» (el umbral es 500 ms); la verificación usó
eventos crudos sobre `/dev/input/event1` (Enter mantenido 1 s).

### Instalación de SponsorBlock: «InstallException null» (Paso 1) · 14/10/2026

El usuario reportó que SponsorBlock no se instalaba desde el panel Complementos
mientras uBO y Dark Reader sí. Causa raíz (con evidencia, no supuesta): el
catálogo de la tienda usaba un **slug de AMO inexistente**.

| Comprobación | Resultado |
|---|---|
| `.../latest/sponsorblock-for-youtube/latest.xpi` (el que usaba la app) | **HTTP 404** |
| `.../latest/sponsorblock/latest.xpi` (slug real) | 200 → `sponsorblock-6.1.7.xpi` |
| `.../latest/ublock-origin/latest.xpi` y `darkreader/latest.xpi` | 200 (de ahí que esos dos sí instalaran) |
| Reproducción en el emulador (APK anterior) | `GeckoConsole: addons.xpi WARN Download of …sponsorblock-for-youtube… failed: 404` → `GeckoMotor: install ERROR: InstallException null` (código **-1** = `ERROR_NETWORK_FAILURE`) |

El «null» no venía de Gecko por un fallo misterioso: `WebExtension.InstallException`
**no rellena `getMessage()`** (sólo `code`, `extensionId`, `extensionName`), y
GeckoMotor imprimía `simpleName + message`. Además nadie escuchaba
`GeckoView:WebExtension:OnInstallationFailed` («No listener», aviso que Gecko
descarta).

Correcciones: slug correcto + ficha AMO por addon (chip 🛒) en
`ComplementosPage.kt`; en `GeckoMotor.instalar()` un **HEAD de comprobación** antes
de pedirle la descarga a Gecko (aborta con el HTTP real si es 404/410 y sigue si el
servidor no contesta al HEAD), más un **mapeo de los códigos** de
`InstallException` a texto en es-419 y registro en la Bitácora. La lista de
INSTALADAS sigue siendo la que informa Gecko, así que un intento fallido no deja
ninguna entrada fantasma.

### Configuración, Historial y Descargas como pestañas (Paso 2) · 15/10/2026

Dejaron de ser **overlays** encima del navegador (`Overlay.History`,
`Overlay.Downloads`, `Overlay.Engine/Homepage/Toolbar`) y pasaron a ser
**pestañas normales**, reutilizando el mecanismo que ya existía para la página de
inicio: una URL interna (`antena://historial`, `antena://descargas`,
`antena://config`) que el área de contenido dibuja con Compose en lugar de
GeckoView. Ningún cambio en el motor ni en `WebTab`.

| Pieza | Qué resuelve |
|---|---|
| `abrirInterna(url)` | Abre la vista como pestaña y, si ya existe una con esa URL, la **trae al frente** (no duplica: pulsar Configuración dos veces deja una sola pestaña) |
| `rememberSaveableStateHolder()` + `SaveableStateProvider(activeId)` | Conserva el estado propio de cada pestaña interna (filtro y scroll del historial) al cambiar a otra y volver |
| `HistoryPage(onOpen = abrirDesdeVistaInterna)` | Tocar una entrada del historial abre la página en una **pestaña web nueva**: la del historial no se destruye (antes `openUrl()` cerraba el overlay y se perdía el sitio) |
| `MenuDropdown` con una sola entrada «Configuración» | Motor de búsqueda + Página de inicio + Barra de herramientas viven en una pestaña; los chips Clock/Download de la barra abren las suyas |
| `tabLabel()` + `TabChip` rediseñado | Título propio por vista interna; chip plano sin contornos, franja ámbar bajo la activa, favicon del sitio o icono de la vista interna, 34 dp (20 dp al leer) y botón × con más área |
| `FaviconBox(iconPadding)` | El parámetro tiene valor por defecto 7 dp: las fichas grandes del inicio no cambian |

**Verificado en emulador (API 33), de punta a punta:**

| Prueba | Resultado |
|---|---|
| Web → Historial | Pestaña propia (`antena://historial`) con «Limpiar» y «✕ Cerrar» |
| Alternar historial ↔ web | Filtro «Todo» y posición de scroll intactos (misma primera fila al volver) |
| Entrada del historial → página | Se abre en pestaña web nueva; el historial sigue en la tira con su estado |
| Descargas con un PDF real | `dummy.pdf` listado y conservado al cambiar de pestaña y volver |
| Varias páginas web | Wikipedia y MARCA siguen cargando y alternándose |
| Cerrar la pestaña Configuración | Sólo esa se cierra (5 → 4 pestañas) |
| Configuración dos veces | No duplica: misma pestaña |
| Ajustes desde la pestaña | Motor → `search_engine=duckduckgo` y la búsqueda tras reiniciar va a DuckDuckGo; «Favoritos en la barra» quita/añade su chip (10 → 9 chips) y escribe `tb_fav` |
| Estabilidad | 0 `FATAL EXCEPTION` |

Capturas: `artifacts/paso2-pestanas.png` y `artifacts/paso2-pestanas-mixtas.png`.
Nota: la tira mantiene sus dos estados como antes (`revealChrome()` → 34 dp y
`onScroll` con la barra plegada → 20 dp); ambos medidos en el dispositivo con
`example.com` y el D-pad: texto del chip 44 px (18 sp) normal y 27 px (11 sp) con la
barra plegada, incluido el respaldo de favicon (inicial «E»).

### La tienda sólo ofrece lo que falta (Paso 3) · 15/10/2026

La lista de la tienda mostraba los tres complementos del catálogo siempre, ya
estuvieran instalados o no. Ahora la sección «disponibles» es **el catálogo menos
lo que Gecko informa como instalado**, y el addon desaparece al instalarse y
vuelve al desinstalarse sin que el usuario tenga que salir y entrar.

| Pieza | Qué resuelve |
|---|---|
| `AddonTienda.id` (`ComplementosPage.kt`) | Cada ficha del catálogo lleva ahora el **id real del manifiesto** (`uBlock0@raymondhill.net`, `addon@darkreader.org`, `sponsorBlocker@ajay.app`), verificado contra los ids que el emulador muestra en INSTALADAS. Es la referencia para saber si ya está montado |
| `addonInstalado()` / `addonsDisponibles()` (puras, `internal`) | La prueba se hace contra la lista **real** de Gecko (`list()`): pulsar «Instalar» no marca nada. El id es la referencia y el nombre visible es respaldo por si el id cambiase |
| Texto de catálogo vacío | Con todo instalado la tienda no queda muda: «(los complementos del catálogo ya están instalados: se administran arriba)» |
| `addonManagerDelegate` (`GeckoMotor.kt`) | `WebExtensionController.AddonManagerDelegate` —existe en `geckoview-141.0.20250806102122`, comprobado en el bytecode del AAR— conecta los avisos reales de Gecko (`OnInstalled`, `OnUninstalled`, `OnInstallationFailed`, `OnEnabled`, `OnDisabled`) con `refrescar()`: la lista se sincroniza también con lo que **no** pidió la app (p. ej. el botón «Agregar a Firefox» de AMO) y sin sondear ni dejar temporizadores |
| `anunciar()` (id + estado) | Un mismo suceso llega por dos caminos (la respuesta de la operación y el aviso del AddonManager): se publica **una sola vez** en el aviso y en la Bitácora. Si la instalación la pidió la app, el aviso lo da ella (conoce la URL y el código de error) y el delegate sólo refresca |
| `ComplementosTiendaTest` (JVM, 8 casos) | Fija la regla pura: sin nada instalado está todo el catálogo, una instalada desaparece (por id y por nombre, activa o no), al desinstalar vuelve sólo esa, el catálogo no repite ids ni nombres. Primera prueba de unidad del proyecto (`testImplementation("junit:junit:4.13.2")`) |

**Verificado en emulador (API 33), de punta a punta** (instalaciones y
desinstalaciones **reales** contra AMO, no simuladas):

| Prueba | Resultado |
|---|---|
| Disponible y no instalada aparece | Al desinstalar SponsorBlock aparece «⬇ SponsorBlock — instalar desde AMO» mientras Dark Reader y uBlock siguen ocultos (están montados) |
| Instalada desaparece | `⬇ SponsorBlock` → AMO: `OnInstalling` → `OnInstalled sponsorBlocker@ajay.app v6.1.7` → pasa a INSTALADAS y la fila de la tienda se va (con todo instalado sólo queda «Explorar la tienda» + el aviso de catálogo completo) |
| Instalación fallida sigue disponible | URL `https://example.com/` (pasa el HEAD, no es un .xpi): `GeckoView:WebExtension:OnInstallationFailed` → «ERROR al instalar: el archivo descargado está dañado (código -3)». La tienda y INSTALADAS quedan igual: nada fantasma |
| Desinstalada vuelve | ✕ → «Desinstalar» → `OnUninstalling` + `OnUninstalled sponsorBlocker@ajay.app` → fuera de INSTALADAS y de vuelta en la tienda |
| Salir y volver | ← Volver + ☰ → Complementos: misma lista que la realidad; tras reiniciar la app, los cuatro (incluido el interno) |
| uBlock y Dark Reader intactos | Chip **🛡 18** en `marca.com` (sigue bloqueando) y Dark Reader medido por luminancia de la misma página: **30/255** con la extensión activa frente a **233/255** desactivada |
| Sin duplicados ni crash | 0 `FATAL EXCEPTION` en toda la pasada; la Bitácora muestra **una** entrada por transición (`Extensión instalada: SponsorBlock … v6.1.7` / `Extensión desinstalada: SponsorBlock …`), sin repetir el mismo suceso por las dos vías |
| Pruebas de unidad | `./gradlew testDebugUnitTest`: **8 pruebas, 0 fallos**; `assembleDebug` en exit 0 |

Capturas: `artifacts/paso3-complementos.png`, `artifacts/paso3-tienda.png`
(catálogo completo), `artifacts/paso3-darkreader-on.png` y
`artifacts/paso3-darkreader-off.png` (misma página, 30 vs 233 de luminancia) y
`artifacts/paso3-bitacora.png`.

Observaciones y límites:

- La desinstalación **fallida** no se pudo reproducir a propósito: no hay forma
de provocar el fallo de `uninstall()` desde la interfaz. Lo que la protege: el
aviso de éxito sólo se publica en el callback de `uninstall()`, un fallo llama a
`refrescar()` (que relee la verdad de Gecko) y el delegate sólo reacciona a
`OnUninstalled`, que no llega si la operación no terminó.
- Probando ese hueco se descubrió que Gecko **sí** deja desinstalar la extensión
interna (`navegador@antena.tv`) y que la lista lo reflejó bien (desapareció).
Es un comportamiento anterior a este paso (el chip ✕ siempre estuvo ahí) y se
recupera solo: `ensureBuiltIn` la vuelve a montar al arrancar (verificado:
`gecko: OnInstalled navegador@antena.tv v1.2` con la app reiniciada).
- Una extensión **desactivada** sigue contando como instalada y por eso no vuelve
a la tienda: se administra arriba con ENTER (ACTIVA/DESACTIVA).
- Si AMO cambiara el id de un complemento, entra el respaldo por nombre; si
cambiaran los dos, la ficha volvería a ofrecerse como instalable (nunca al revés:
nada se oculta sin que Gecko lo confirme).
- Durante las pruebas se desactivaron los teclados en pantalla del emulador (la
tira de sugerencias se comía los toques sobre los chips de los diálogos) y se
volvieron a activar al terminar: no es nada del producto.

### Barra de direcciones: conserva el texto y sugiere del historial (Paso 4) · 15/10/2026

**Lo que estaba mal (medido antes de tocar nada).** Tocar la barra en una página
cargada y teclear una sola letra **perdía la dirección entera**: con
`https://example.com/` en la barra, tocar + `w` + ENTER acababa en
`https://duckduckgo.com/?q=w&ia=web`. La causa no era la selección de texto sino
el **gesto congelado**: el `pointerInput(Unit)` de `AddressBar` captura el lambda
de la **primera composición** y no se reinicia nunca, así que el toque llamaba a
un `onStartEdit` que todavía leía `showDashboard`/`activeTab.url` del estado del
arranque (la página de inicio → `""`). Encima, cuando el camino sí llegaba
fresco (mando), el texto se entregaba **seleccionado entero**, así que la primera
tecla lo sustituía. Por otra parte el historial guardaba **una fila por carga**
(la base del emulador llegó a tener **128 filas para 32 direcciones**, 18 de
ellas repetidas) y una búsqueda sólo existía como su URL de resultados: no había
forma de ofrecer «repite esta búsqueda» ni de ordenar por uso.

| Pieza | Qué resuelve |
|---|---|
| `pointerInput(onStartEdit)` | El gesto se reinicia con cada lambda: el toque ya lee la pestaña **actual** (era la raíz del borrado) |
| `editarBarra(seleccionarTodo)` | Conserva el texto y deja el **cursor al final**: se puede ampliar, corregir o borrar una parte. Mantener OK sobre la barra marca todo, para sustituir de un golpe (por eso la selección completa no se pierde, pero ya no es la única opción) |
| `HistoryEntry.visits` + `registrar()` (`AppDatabase.kt`) | La visita se **actualiza** en la fila que ya existe (fecha, título y contador) en vez de añadir otra: ni duplicados ni «más usadas» sin datos. Migración **2 → 3** con `ALTER TABLE` (sólo añade columna): el historial del usuario se conserva entero |
| `Sugerencias.kt` (nuevo, puro) | `busquedaDe()` reconoce la página de resultados de los motores por su plantilla (`…?q=%s`) y saca el **texto buscado** (sirve también para el historial anterior a este paso); `sugerencias()` agrupa por URL y por consulta, acumula uso, filtra por URL, **dominio**, título o consulta (sin acentos ni mayúsculas, no sólo al principio) y ordena: primero lo que empieza igual, luego lo más usado y por último lo más reciente. Máximo **15** |
| `SuggestionRow(tipo)` | 🌐 dirección (título + URL) o 🔍 búsqueda anterior (texto, motor con el que se hizo y veces usada): se distinguen de un vistazo |
| `usarSugerencia()` | Dirección → navega (una sola vez, y la entrada de historial la añade la carga real). Búsqueda → **devuelve el texto a la barra** con el cursor al final, sin navegar y **sin tocar el motor** elegido; ENTER la ejecuta con el motor configurado |
| Sugerencias en memoria | Se filtran sobre el historial ya cargado (`recent()`, ≤500 filas) con 90 ms de respiro: **ni una consulta a disco por pulsación**. `HistoryDao.search()` (LIMIT 8) se retiró por tener un solo camino |
| `verticalScroll` en el desplegable | Con 15 sugerencias la lista se recorre con el mando; el foco lleva la fila a la vista (y al entrar en la lista el teclado se cierra, devolviendo la pantalla) |
| `hostOf()` con expresión regular | Mismo resultado que `Uri.parse(...).host ?: url`, pero **sin depender de Android**: la lógica de la barra es pura y se prueba en la JVM |
| `SugerenciasTest` (JVM, 16 casos) | Dirección vs búsqueda, texto recuperado, agrupado por URL/consulta, filtro por dominio/título/consulta sin acentos, prefijo antes que coincidencia parcial, tope de 15, historial vacío y `hostOf` |

**Verificado en emulador (API 33) sobre la APK instalada** (la evidencia es la
base de datos del propio navegador: `run-as … databases/antena.db`, leída junto
al `-wal`, y las capturas de pantalla):

| Prueba | Resultado |
|---|---|
| 1 · Tocar la barra conserva la dirección | En `https://example.com/`, tocar + `w` + ENTER cargó **`https://example.com/w`** (visitas 1 → 2), no una búsqueda |
| 2 · Editar una parte sin perder el resto | En `https://example.com/w`, tocar + **borrar** + ENTER cargó `https://example.com/` (visitas 9 → 10): se fue la `w`, no la dirección |
| 3 · Barra vacía muestra recientes | En Inicio, tocar la barra lista 🌐 Example Domain, 🌐 MARCA, 🌐 Example Domains… con su URL |
| 4 · Escribir filtra | `wiki` deja «Wikipedia, la enciclopedia libre» + `https://es.wikipedia.org/wiki/Wikipedia:Portada` |
| 5 · Coincidencia parcial en dominio o título | `wiki` casa dentro de `es.wikipedia.org/wiki/…`; `diario` casa por el **título** de MARCA |
| 6 · Elegir una dirección navega | Tocar la sugerencia de MARCA cargó `https://www.marca.com/` y la entrada de historial la añadió la carga |
| 7 · Recuperar una búsqueda y ejecutarla | Elegir 🔍 «prueba» **no navegó** (la base quedó igual); ENTER cargó `https://duckduckgo.com/?q=prueba` y `search_engine` siguió en `duckduckgo` (no se cambió el motor) |
| 8 · Desplazamiento con más de lo visible | Escribiendo `e` hay 15 filas (≈8 por pantalla); tras 12×↓ la ventana se desplazó a las filas 4-12 y OK abrió una fila que no estaba a la vista (`https://m.youtube.com/`) |
| 9 · Teclado virtual | Con el teclado abierto (frame `[0,340][1920,1080]`) las sugerencias se dibujan por encima (filas en y=224-272 y 332) y al mover el foco a la lista el teclado se cierra y se ve la lista entera |
| 10 · Historial ni perdido ni duplicado | 0 direcciones del historial anterior perdidas en la migración (128 → 155 filas); desde la instalación las repeticiones **suman visitas** (5 filas con `visits>1`) y sólo entran URLs nuevas (4). Nada borrado |
| 11 · Resto del navegador intacto | Pestañas (crear/alternar), menú ☰ completo, **Historial** con sus entradas, **Complementos** con uBlock Origin, Dark Reader y SponsorBlock y sus fichas de la tienda: todo operativo. 0 `FATAL EXCEPTION` |
| Pruebas de unidad | `./gradlew testDebugUnitTest`: **24 pruebas (16 nuevas), 0 fallos**; `assembleDebug` en exit 0 |

Capturas: `artifacts/paso4-sugerencias-teclado.png` (sugerencias con el teclado
abierto) y `artifacts/paso4-sugerencias-desplazadas.png` (la lista tras bajar con
el mando).

Observaciones y límites:

- La coincidencia se busca en URL, dominio, título y consulta; no en el cuerpo
de las páginas (no se guarda) ni en los favoritos: las sugerencias son siempre
del historial real, nunca inventadas.
- Las filas repetidas **anteriores** a la migración se conservan (no se borra
nada): la lista las agrupa al mostrarlas, así que se ven una sola vez, pero la
página Historial sigue mostrándolas como entradas sueltas. La migración sólo
añade la columna `visits`; no compacta lo ya guardado.
- Si una dirección aparece dos veces con títulos distintos, la sugerencia se
queda con el de la visita más reciente.
- Con el teclado en pantalla sólo caben 2-3 sugerencias encima de él; el resto se
alcanza desplazando (el foco mantiene la fila a la vista). En el mando, bajar a
la lista cierra el teclado y se ve la lista completa.
- Al observar las pruebas se vio un comportamiento **anterior** a este paso: tras
cargar una página, `onScroll` de Gecko puede dejar la barra plegada y en páginas
cortas (p. ej. `example.com`) no hay desplazamiento que la vuelva a desplegar. No
se tocó (es el «scroll inteligente» de la Fase 2 y este paso no debía alterar la
navegación); queda anotado para revisarlo en un paso propio.
- Durante la reproducción del fallo se cambió varias veces el estado del emulador
(teclado activado/desactivado): al terminar quedó activado.

### Menú contextual de las páginas web (Paso 5) · 16/10/2026

**Lo que había antes (leído del código, sin tocar nada).** El proyecto ya abría
un menú con la pulsación larga **sobre un enlace o una imagen**: `WebTab`
implementaba `ContentDelegate.onContextMenu` y entregaba `linkUri` y, si el
elemento era `TYPE_IMAGE`, su `srcUri`; `BrowserScreen` lo pintaba como una lista
plana (`Overlay.ContextMenu`) con acciones de imagen y enlace. No había **nada
para el texto seleccionado** ni para la **propia página**, no existía un modelo
que decidiera *qué se puede hacer* con cada elemento (las filas se componían a
mano) y no había ninguna prueba de esa lógica.

De la versión instalada (`geckoview-141.0.20250806102122`, comprobada en el
bytecode del AAR) se usan las dos vías reales del motor, sin inventar API:
`ContentDelegate.onContextMenu(session, screenX, screenY, ContextElement)` para la
pulsación larga táctil (con `element.type` y `linkUri`/`srcUri`) y
`SelectionActionDelegate` (`onShowActionRequest` con `Selection.text`,
`flags & FLAG_IS_EDITABLE` y el propio handle `Selection` para copiar/pegarsin
tocar el portapapeles a mano) para el texto seleccionado.

| Pieza | Qué resuelve |
|---|---|
| `Contexto.kt` (nuevo, puro) | `filasContexto(target)` decide las filas (sección + etiqueta + acción) para cada elemento; `tituloContexto()` y `muestraContexto()` el encabezado; `urlDeBusqueda()` la búsqueda con el **motor configurado**. Aloja `ContextTarget` (antes en `Panels.kt`) para poder probarlo en la JVM, sin Gecko ni Compose |
| `ContextoTest.kt` (JVM, **11 casos**) | Fija la regla central: **no aparece ninguna acción que no se pueda ejecutar** (sin enlace no hay filas de enlace, sin selección no hay filas de texto, sin nada sólo «Copiar dirección de la página»); orden de secciones Texto → Enlace → Imagen → Página; sección declarada una sola vez; la búsqueda codifica el texto y un motor sin `%s` **no** genera búsqueda |
| `WebTab.kt` · `selectionActionDelegate` | Texto **no editable** → menú propio (aporta «Buscar» con el motor y «Compartir», que la barra nativa no da). **Campos editables** (y de contraseña) → `super.onShowActionRequest`, es decir, la barra nativa del motor: es la que corta/pega con el portapapeles y respeta el formulario |
| `WebTab.kt` · guarda `textoSeleccionadoDescartado` | Al cerrar el menú de una selección, ese mismo texto no lo vuelve a abrir (se pueden ajustar sus tiradores); una selección distinta sí. Se limpia sola con `HideSelectionAction(NO_SELECTION)` |
| `WebTab.kt` · `onContextMenu` | Sigue entregando enlace e imagen y ahora también el tipo, para que el menú sepa qué elemento se pulsó |
| `Panels.kt` · `ContextMenuCard` | Tarjeta con título, **previsualización** (texto/imagen/enlace/página, nunca una contraseña), secciones y filas con icono; foco reclamado al abrir y **cierre por toque fuera** sin secuestrar después la navegación entre filas |
| `Panels.kt` · iconos `ToolIcon` | `Link`, `Share`, `Download`, `Image`, `Text`, `Copy`, `Page`: trazos vectoriales en el catálogo que ya existía, ningún recurso nuevo |
| `BrowserScreen.kt` · acciones | `Overlay.ContextMenu` con dos salidas: `closeCtx` (acción) y `cancelCtx` (BACK/toque fuera, que **retira la selección** como cualquier navegador). Copiar/compartir/buscar/abrir en pestaña nueva o en segundo plano, portapapeles y avisos con el mismo tono del navegador |

**Verificado en emulador (API 33) sobre la APK instalada** (página de prueba
propia con texto, enlace, imagen, campo de texto y campo de contraseña;
coordenadas y filas leídas del árbol de accesibilidad, el portapapeles
comprobado **pegando en la barra** y las cargas por el registro de Gecko):

| Escenario del paso | Resultado |
|---|---|
| 1 · Mantener pulsado un texto: seleccionar y copiar | Larga sobre la palabra → menú con **Copiar · Buscar · Compartir · Seleccionar texto**; «Copiar» y al pegar en la barra aparece **`palabraunica`**: el portapapeles tiene el texto seleccionado |
| 2 · Buscar con el motor configurado | «Buscar» cargó **`https://duckduckgo.com/?q=palabraunica`** (DuckDuckGo seguía siendo el motor: no se cambió la configuración) |
| 3 · Copiar la dirección **real** del vínculo | Larga sobre el enlace → **Abrir · Abrir en pestaña nueva · Abrir en segundo plano · Copiar vínculo · Compartir vínculo · Guardar vínculo como…**; al pegar en la barra: **`https://example.com/`** (el del vínculo, no el de la página) |
| 4 · Abrir en pestaña nueva sin perder la original | La tira pasa a mostrar **«Prueba menú contextual» y «example.com»**: la original sigue ahí |
| 5 · Copiar y pegar **dentro** de un campo | Parcial. El campo **conserva la barra nativa** (aparece una `PopupWindow` nueva del motor y **ningún** menú propio) y su contenido no cambia. El pegado dentro del campo **no se pudo ejecutar**: el emulador no adjunta el teclado a los campos de la página (`mInputShown=false` en 3 intentos) |
| 6 · Contraseñas y formularios intactos | El valor del campo no cambia en ninguna prueba; la contraseña se expone sólo como `••••••••••` y **`secreto123` aparece 0 veces** en el árbol de accesibilidad y 0 en logcat |
| 7 · El menú se cierra bien | Con **BACK** y con **toque fuera**, en ambos casos desaparecen las filas y la página sigue cargada |
| 8 · No se muestran acciones incompatibles | Texto → 4 filas de texto; enlace → 6 de enlace; imagen → 4 de imagen (ninguna de texto ni de enlace); elemento sin nada → sólo «Copiar dirección de la página» |
| 9 · Menú con el teclado virtual abierto y cerrado | **No conclusivo.** Con el teclado **cerrado** el menú se centra en la ventana; el caso con teclado abierto sobre la página no se pudo montar (mismo motivo que el 5). La app no fija `windowSoftInputMode`, así que la ventana se redimensiona y la tarjeta se centra en el área visible |
| 10 · La navegación sigue funcionando | Tocar el enlace carga `https://example.com/`, **atrás** vuelve a la página de prueba, pestañas y barra operativas, **0 `FATAL EXCEPTION`** |
| Pruebas de unidad | `./gradlew testDebugUnitTest`: **35 pruebas (11 nuevas), 0 fallos**; `assembleDebug` en exit 0 |

Capturas: `artifacts/paso5-menu-texto.png`, `artifacts/paso5-menu-enlace.png` y
`artifacts/paso5-menu-imagen.png` (los tres menús, tal como salen en el
emulador).

Observaciones y límites (lo que no se pudo verificar, con el motivo):

- **El emulador sólo entrega una petición de selección por página cargada:**
después del primer menú, una segunda pulsación larga en la misma página no
produce ningún `onShowActionRequest` (medido con `GeckoSession:ShowSelectionAction`:
`1` y luego `0`, también al pulsar otra palabra). Se reprodujo con mi delegado **y
con el delegado nativo de stock** (`super.onShowActionRequest`), así que **no es
consecuencia de este paso**: es comportamiento de Gecko/emulador y queda pendiente
de confirmar en un dispositivo real (tablet/TV).
- **Pegar dentro de un campo web no es ejecutable en este emulador** (no adjunta
el IME a los campos de la página; ya pasaba antes de este paso). Lo que sí queda
probado es lo que importa para no romper formularios: los campos **no** pasan por
el menú propio y su contenido no se altera.
- Se mantienen **sin tocar** las filas de imagen (`Guardar imagen`, `Copiar
imagen`, `Copiar vínculo de la imagen`, `Búsqueda visual de la imagen`) y
`Guardar vínculo como…`, que ya existían sobre el sistema de descargas: son
añadiduras anteriores al paso y borrarlas sería una regresión. Este paso **no
añade ninguna capacidad nueva de guardado**: eso corresponde al paso 6.
- Durante la verificación, el emulador se cayó y hubo que relanzarlo; la página
de prueba y el APK se volvieron a poner. Varios toques de prueba caían sobre el
desplegable de sugerencias de la barra (BACK #1 cierra el teclado, no el
desplegable), lo que explica falsos negatives iniciales: se resolvió cerrando la
edición en bucle antes de cada prueba.
- El `Ctrl+A` de la barra (Compose) no selecciona todo en el emulador, así que el
pegado de comprobación quedaba **añadido** al final del texto; no afecta a la
prueba (lo que se mira es que el texto pegado sea el copiado).
- No se tocaron barra de direcciones ni sugerencias, pestañas, complementos,
favoritos, historial, descargas ni el motor.

### Identidad y controles individuales por extensión (Paso 7) · 15/10/2026

**Lo que había antes (leído del código, sin tocar nada).** Las extensiones se
montan con `GeckoMotor` (`GeckoRuntime` único del proceso + su
`WebExtensionController`): `install(url)` desde AMO o cualquier `.xpi`,
instalaciones hechas desde la web de AMO por `AddonManagerDelegate`, y la
persistencia en el perfil de Gecko (`extensions.json`). La app **no** guarda
nada: la única fuente de verdad es `list()` → `ExtInfo(id, nombre, version,
activa)`. En la interfaz había una lista completa en el panel **Complementos**
(activar, desactivar, desinstalar, catálogo, URL) pero en la **barra de
herramientas** todas las extensiones se representaban con **un solo chip «🛡»
genérico** (el contador de la primera que informara badge) y ese clic llamaba a
`Action.click()`: no existía icono propio ni ningún control individual por
extensión en la barra.

De la versión instalada (`geckoview-141.0.20250806102122`, comprobada en el
bytecode del AAR con `javap`, sin suponer nada) se usan estas capacidades reales:

| API de GeckoView 141 | Para qué se usa aquí |
|---|---|
| `WebExtension.MetaData.getIcon()` → `Image.getBitmap(size)` → `GeckoResult<Bitmap>` | **icono real** de cada extensión (identidad propia) |
| `Action.getIcon()/getTitle()/getBadgeText()/getBadgeBackgroundColor()/getEnabled()` + `Action.click()` | contador junto al icono y clic que llega a la extensión |
| `MetaData.getOptionsPageUrl()/getOpenOptionsPageInTab()/getHomepageUrl()/getAmoListingUrl()/getCreatorName()/getDescription()` | filas y datos **reales** del panel individual (si faltan, la fila no existe) |
| `WebExtension.TabDelegate.onOpenOptionsPage(ext)` (público, vía `setTabDelegate`) | la extensión pide sus opciones (`runtime.openOptionsPage()`): Gecko **no** las abre por su cuenta |
| `TabDelegate.onNewTab(ext, CreateTabDetails)` | la extensión pide una pestaña (`browser.tabs.create()`) |
| `WebExtensionController.enable/disable(ext, EnableSource.USER)`, `list()`, `setTabActive` | activar/desactivar real, persistido y reflejado desde el motor |

`WebExtensionController.openOptionsPage(...)` existe **pero no es público**
(package-private): la app no puede llamarlo, así que las opciones se abren
cargando `optionsPageUrl` en una pestaña. Tampoco hay API para saber si una
extensión tiene popup, ni de estadísticas, ni `menus` (no existe en Android).

| Pieza | Qué resuelve |
|---|---|
| `Extensiones.kt` (nuevo, puro) | `repartoIconos()` (un hueco por extensión; si no caben, el último es la salida «＋N» sin perder accesos), `filasExtension()` (sólo filas ejecutables), `etiquetaEstado()`, `subtituloExtension()`, `inicialExtension()` (icono alternativo estable), `urlSegura()` (sólo `moz-extension:` y http/https) |
| `ExtensionesTest.kt` (JVM, **17 casos**) | Fija la regla: sin interfaz no se ofrece «abrir su panel»; sin URL no hay fila de opciones/sitio/ficha; `javascript:` se descarta; el estado visible sale del estado real; el desborde se rotula con lo que queda dentro; la extensión interna nunca ocupa hueco |
| `GeckoMotor.kt` · `iconos: StateFlow<Map<String, Bitmap>>` | Pide una vez por extensión el icono al motor y lo publica; si el motor no da imagen o falla la decodificación, la UI usa las iniciales (nunca un hueco en blanco). Los iconos de extensiones que ya no están se descartan |
| `GeckoMotor.kt` · `ExtInfo` ampliado + `TabDelegate` | Trae opciones, autor, sitio, ficha y descripción reales; atiende `onOpenOptionsPage` y `onNewTab` con los callbacks `alOpciones` y `alNuevaPestana` |
| `GeckoMotor.kt` · `clicAccion()` devuelve `Boolean` y `puedeAbrirInterfaz()` | Sin pestaña con sesión (p. ej. el panel de inicio) Gecko **no puede** abrir el popup: su `wrapTab` revienta con «nativeTab is null». Ahora se comprueba antes y la UI no ofrece lo que no se puede ejecutar |
| `Panels.kt` · `ExtIcono` + `RemoteChip(imagen/glifo/tenue)` | El chip pinta el **bitmap real** (o las iniciales) con el mismo tamaño, alineación y foco que el resto de la barra; la extensión desactivada se ve **apagada** |
| `Panels.kt` · `ExtensionCard` | Panel de UNA extensión: icono, nombre, versión · autor, estado real, descripción real, contador **si la extensión lo informa** y las filas de `filasExtension()`; muestra también el aviso de error del motor, sin esconderlo |
| `BrowserScreen.kt` | Un chip por extensión (pulsar = su interfaz; mantener OK = su panel), chip «＋N» cuando no caben, `Overlay.Extension(id)` y las acciones (abrir panel, opciones, sitio, ficha, activar/desactivar, administrar) |
| `ComplementosPage.kt` | La lista usa la **misma** identidad visual (icono real o iniciales) que la barra |

**Verificado en emulador (API 33) sobre la APK instalada**, con **tres
extensiones reales** ya montadas (uBlock Origin 1.75.0, Dark Reader 4.9.133 y
SponsorBlock 6.1.7) y leyendo el árbol de accesibilidad, el registro del motor y
los píxeles de la captura:

| Escenario del paso | Resultado |
|---|---|
| 1 · Un icono identificable por extensión | La barra muestra **tres chips** con imagen propia: sin texto de iniciales en el árbol (⇒ el motor entregó los bitmaps) y con las **tres regiones de píxel distintas entre sí** (media y varianza medidas sobre la captura: diferencia 81–146 entre chips, varianza 10 000–16 000). Ningún chip genérico |
| 2 · Pulsar cada icono abre su interfaz | Chip 1 → popup de **Dark Reader** (`moz-extension://464e2b8f…/ui/popup/index.html`); chip 2 → popup de **SponsorBlock** (`…45060574…/popup.html`); chip 3 → popup de **uBlock Origin** (`…4ccbfde7…/popup-fenix.html`). Tres UUID de extensión distintos: cada chip va a la suya |
| 3 · Activar/desactivar de verdad | «Desactivar esta extensión» → `WebExtension:OnDisabled` + `toggle uBlock0@raymondhill.net → DESACTIVADA`; el panel pasa a **○ DESACTIVADA** y la fila a «Activar esta extensión»; Complementos muestra **○ uBlock Origin — ENTER para activar / DESACTIVA** con Dark Reader y SponsorBlock aún ACTIVA; al reactivar → `OnEnabled` y **● ACTIVA** |
| 4 · El estado visual es el real | Nada se marca antes de que el motor confirme: el texto del panel sale de `list()` (`metaData.enabled`) y sólo cambia al llegar la respuesta o el aviso real de Gecko |
| 5 · Sólo lo que existe | En el panel de inicio (sin pestaña con sesión) **no** aparece «Abrir su panel» y sí las filas reales que uBO publica; en una pestaña con sesión aparece también «Abrir su panel». El contador sólo se pinta si la extensión lo informa (en `example.com`/`marca.com` uBO informa badge vacío ⇒ **no** se muestra ningún número inventado) |
| 6 · Muchos iconos no rompen la barra | Con la capacidad bajada temporalmente a 2 (misma ruta de código que 5+ extensiones) la barra muestra **1 chip + «＋2»**, el resto de la barra intacto, y el chip de desborde abre **Complementos**, donde cada extensión conserva su icono y sus controles. Restaurada la capacidad a 4 vuelven los tres chips |
| 7 · Instalaciones y funciones existentes | La lista de Complementos sigue leyendo de `list()` (con el estado real tras el ciclo desactivar/activar) y el catálogo de la tienda no cambia; la barra de direcciones, pestañas («+» crea), navegación (`example.com`, `marca.com`) y el panel de inicio funcionan igual; **0 `FATAL EXCEPTION`** |
| 8 · Opciones de la extensión | «Opciones y panel de control» abrió **`moz-extension://…/dashboard.html#settings.html`** («uBlock₀ — Dashboard») en una pestaña nueva |
| Pruebas de unidad | `./gradlew testDebugUnitTest`: **52 pruebas (17 nuevas), 0 fallos**; `assembleDebug` en exit 0 |

Capturas: `artifacts/paso7-barra.png` (tres chips con su icono),
`artifacts/paso7-panel.png` (panel individual de uBlock Origin) y
`artifacts/paso7-desborde.png` (barra con «＋2» y Complementos abierto).

Observaciones y límites (lo que no se pudo verificar, con el motivo):

- **La pulsación mantenida sobre un chip (que abre el panel individual) no se
  pudo ejercitar en el emulador.** El único camino es la tecla OK del mando
  (`rememberCenterHandler`, el mismo que usan «mantener OK para eliminar» de una
  ficha o el cierre de pestañas) y el emulador sólo sabe inyectar una pulsación
  mantenida de unos 500 ms exactos (`input keyevent --longpress`, 518 ms de
  comando medidos), que el handler compartido clasifica como **corta** porque
  exige más de 500 ms; la consola del emulador (`adb emu event send`) no llega a
  la pila de entrada en esta imagen. **Verificado en cambio su destino**: el
  mismo `overlay = Overlay.Extension(id)` se ejercitó (a) pulsando un chip cuando
  Gecko no puede abrir su interfaz y (b) navegando y activando las filas del
  panel con el D-pad. Queda por confirmar en un TV real el gesto mantenido.
- **Gecko no expone si una extensión tiene popup**: se deduce de que declare
  acción de barra (`ActionDelegate.onBrowserAction/PageAction`). Una extensión con
  acción que no abra nada al pulsarla no se puede distinguir de una que sí; el
  panel individual cubre ese caso con lo que sí esté disponible.
- **No hay API de estadísticas** de extensiones: el panel muestra únicamente el
  contador que la propia extensión pone en su acción (`badgeText`), sin inventar
  cifras.
- La extensión **interna** del navegador (`navegador@antena.tv`, la del scroll)
  sigue apareciendo en la lista de Complementos —como antes— pero **no ocupa
  hueco** en la barra; sí recibe el icono alternativo de iniciales («AN»).
- Si la extensión pide sus opciones y **no publica** ninguna página, se avisa tal
  cual («no publica página de opciones») en vez de abrir algo vacío.
- No se tocaron el motor, las pestañas, el historial, las descargas, los
  favoritos, la barra de direcciones ni el sistema de descargas; **no** se
  instalaron extensiones nuevas ni se empezó el paso 8.

### Extensiones adicionales: evaluación e integración (Paso 8) · 16/10/2026

**Resumen inicial (estado de partida, leído del código y del aparato antes de
tocar nada).**

- **Instaladas y funcionando**: uBlock Origin 1.75.0, Dark Reader 4.9.133 y
  SponsorBlock 6.1.7 —las tres ya montadas en los pasos 3 y 7— más la extensión
  **interna** del navegador (`navegador@antena.tv`, la del scroll). El motor de
  la app es **GeckoView 141.0.20250806102122** (`geckoview-omni`, el mismo de
  Firefox 141); no hay WebView en ninguna pestaña.
- **Cómo se administran**: `GeckoMotor` mantiene **un** `GeckoRuntime` por
  proceso y su `WebExtensionController`; instalar, activar, desactivar y
  desinstalar van por `install(url)` / `enable` / `disable` / `uninstall` y la
  **única fuente de verdad es `list()`** (la app no guarda nada: `ExtInfo` sale
  del motor). La interfaz son el panel **Complementos** (tienda AMO + lista con
  icono real, estado real y desinstalar) y los **chips por extensión** de la
  barra (paso 7).
- **Compatible / potencialmente compatible**: Chrome Mask (popup propio,
  `webRequestBlocking`), Privacy Badger (bloqueo heurístico, popup, página de
  primera ejecución), Google Search Fixer (sin interfaz: sólo reescribe la UA de
  las páginas de Google). Los tres son **Firefox para Android de escritorio**
  (`strict_min_version` 115/128) y se montan por `.xpi` firmado de AMO.
- **Archivos y componentes a tocar** (y los que se tocaron): catálogo
  `ComplementosPage.TIENDA` (+ sus pruebas `ComplementosTiendaTest`),
  `GeckoMotor` (sesión que las extensiones ven como activa cuando un popup abre
  pestaña) y `BrowserScreen` (seguir la pestaña de origen mientras el popup está
  en pantalla).
- **Riesgos identificados antes de empezar**: (1) dos extensiones que reescriben
  la **UA** a la vez (Chrome Mask y Google Search Fixer) pueden pisarse; (2)
  uBO + Privacy Badger bloqueando a la vez ralentizan y sus contadores no son
  comparables; (3) PB usa la API `privacy` y su popup depende de
  `tabs.create` —hay que comprobar que la app lo atiende—; (4) 5-6 extensiones
  engordan la barra y consumen memoria en un televisor (el chip «＋N» lo absorbe
  hasta cierto punto); (5) **no** tocar uBO/Dark Reader/SponsorBlock más allá de
  volver a comprobarlos; (6) Search by Image no debía forzarse si su función no
  existe en Android.

**Lo que se cambió.**

| Pieza | Qué resuelve |
|---|---|
| `ComplementosPage.kt` · `TIENDA` | Tres fichas nuevas con su **id real** de manifiesto (`chrome-mask@overengineer.dev`, `jid1-MnnxcxisBPnSXQ@jetpack`, `{58c32ac4-0d6c-4d6f-ae2c-96aaf8ffcb66}`) y `nota` visible en la fila de Google Search Fixer (no trae icono ni página de opciones propias: no hay botón que pulsar) |
| `GeckoMotor.kt` · `sesionFijada` / `fijarSesion()` / `sesionParaExtensiones()` | El panel de una extensión abre **su** pestaña y GeckoView marca como activa la sesión que acaba de acoplar (el propio panel): las extensiones preguntaban por «la pestaña activa», recibían la suya y respondían «no funciona en este sitio» (visto con Chrome Mask: ni aplicar ni quitar la máscara). Ahora la pestaña de origen queda **fijada** y vuelve a marcarse activa con un ciclo `setTabActive(false)` → `setTabActive(true)` |
| `BrowserScreen.kt` · `popupId` | Mientras el popup está en pantalla, `sesionActiva` sigue siendo la pestaña del sitio; al salir de esa pestaña se desfija (`fijarSesion(null)`) y, si la pestaña se cierra, `GeckoMotor` limpia la marca por sí mismo |
| `ComplementosTiendaTest.kt` | 6 casos nuevos (13 en total): las tres extensiones entran en el catálogo por su id, `slugDeAmox()` coincide entre `.xpi` y ficha en **todas** las fichas, el slug inexistente de SponsorBlock no vuelve, y todo `.xpi` es de `addons.mozilla.org/firefox/downloads/latest/` |

**Causa del `InstallException null` de SponsorBlock (confirmada).** El catálogo
llevaba un slug inventado. Medido ahora contra AMO:

| URL | Respuesta |
|---|---|
| `…/downloads/latest/sponsorblock-for-youtube/latest.xpi` (el viejo) | **404** |
| `…/downloads/latest/sponsorblock/latest.xpi` (el corregido) | **206** (descarga parcial correcta) |

Gecko no distingue «me han dado una página HTML 404» de cualquier otro fallo de
instalación y lo cuenta todo como `InstallException` **sin causa**. Con el slug
bueno, el ciclo completo quedó verificado en el emulador: **desinstalar**
(`OnUninstalling` → `OnUninstalled`, `DESINSTALADA sponsorBlocker@ajay.app`, el
`.xpi` desaparece del perfil y la fila sale de la lista) y **volver a instalar
desde la tienda** (`prompt instalar …: permisos=0 orígenes=3` → `OnInstalled
v6.1.7` → `install OK: … v6.1.7`), sin ningún `InstallException`.

**Verificado en emulador (API 33, TV 1920×1080) sobre la APK instalada.** Las
mediciones de red se hicieron con un **servidor propio** en el PC
(`10.0.2.2:8099`, `artifacts/paso8-mediciones.log`) y, cuando hacía falta,
poniendo ese servidor como **proxy HTTP del aparato** para poder ver la cabecera
que Gecko manda de verdad a cada host.

| Escenario del paso | Resultado real |
|---|---|
| 1 · Chrome Mask: máscara aplicada y quitada | Popup de la extensión abierto en su pestaña con el interruptor; con la máscara **puesta** la petición sale con `…Chrome/155.0.0.0 Mobile Safari/537.36` y `sec-ch-ua: "Chromium";v="155", "Google Chrome";v="155"`; con la máscara **quitada** vuelve `Mozilla/5.0 (Android 13; Mobile; rv:141.0) Gecko/141.0 Firefox/141.0` y **sin** `sec-ch-ua`. Ambas leídas en el servidor |
| 2 · Privacy Badger: instalar y efecto medible | Instalada v2026.9.15 y su página de primera ejecución se abrió en una pestaña (la app atiende `tabs.create`). Efecto medido por cabeceras en el servidor: **activa** → `DNT=1; Sec-GPC=1`; **desactivada** (interruptor de Complementos) → la misma petición sale **sin** `DNT` ni `Sec-GPC`. Su popup abre `moz-extension://<uuid>/popup.html` y carga su interfaz (nodos `breakageWarning` y `reportBrokenSite`) |
| 3 · Google Search Fixer: efecto medible | **Activa** → la petición a `www.google.com` sale con `Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) FxQuantum/141.0 AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36`; **desactivada** → la misma petición sale con la UA normal de Antena (`Firefox/141.0`). Medido en el proxy local, sin depender de lo que sirva Google |
| 4 · Search by Image | **Descartada** (ver abajo) |
| 5 · SponsorBlock: desinstalar y reinstalar | Ciclo completo sin errores (tabla de la causa, arriba) |
| 6 · uBlock Origin sigue bloqueando | Con uBO **activa**, navegar a `http://doubleclick.net/px.png` produce `PageStart` pero **ninguna petición** llega a la red (el servidor no ve nada); con uBO **desactivada**, la misma navegación **sí** llega (`http://doubleclick.net/` y `…/px.png`) |
| 7 · Dark Reader sigue oscureciendo | Misma página blanca (`#ffffff`) medida por píxel: con Dark Reader **activa** luminancia media **31,4**; **desactivada** **248,3**. Capturas en `artifacts/paso8-darkreader-on.png` / `-off.png` |
| 8 · Todo sobrevive al reinicio | `force-stop` + relanzar la app (varias veces en el paso): las **6** extensiones siguen instaladas y **● ACTIVA** en Complementos, con sus `.xpi` en el perfil de Gecko; la tienda sigue mostrando sólo lo que falta |
| 9 · Nada se rompió | Navegación, pestañas, favoritos, historial, Complementos y la barra funcionan igual; **0 `FATAL EXCEPTION`** en logcat en toda la sesión |
| Pruebas de unidad | `./gradlew :app:testDebugUnitTest`: **57 pruebas (13 de catálogo), 0 fallos**; `assembleDebug` en exit 0 |

**Descartadas y por qué.**

- **Search by Image** (id `{2e5ff8c8-32fe-46d0-9fc8-6b8986621f3c}`). Leído su
  `.xpi` firmado: su vía principal es un **ítem de menú contextual**
  (`contextMenus.create`) y su permiso incluye `contextMenus`; la API
  `menus`/`contextMenus` **no está soportada en Firefox para Android** (MDN:
  «Firefox for Android — No support»), o sea que en GeckoView no hay dónde
  colgar ese ítem: no se puede elegir «esta imagen». Lo único que le queda es su
  botón de barra, que busca **entre todas las imágenes de la página**, no la que
  el usuario señala. Y la app **ya ofrece esa función**: su propio menú
  contextual de imagen incluye «Búsqueda visual de la imagen» (paso 5, ver
  `artifacts/paso5-menu-imagen.png`). Añadirla al catálogo sería ofrecer algo que
  no hace lo que promete. **No se instaló ni se añadió al catálogo.**
- **Comprobación de compatibilidad (medida)**: Chrome Mask, Privacy Badger y
  Google Search Fixer **no declaran** `browser_specific_settings.gecko_android`,
  y aun así GeckoView 141 las instala, las ejecuta y les da acción/popup. Ese
  campo no es un portero en GeckoView; lo que de verdad limita es la **API** que
  use la extensión (caso de `contextMenus`).

**Límites de lo verificado (para no dar por hecho lo que no se midió).**

- El **bloqueo heurístico de Privacy Badger no se midió**: PB aprende dominio a
  dominio tras verlo en varias páginas, así que su «bloqueo real» exige una
  navegación larga que no cabe en una comprobación de este paso. Lo que sí está
  medido es que la extensión **se ejecuta y altera de verdad las peticiones**
  (DNT/GPC, arriba) y que su interruptor la para.
- El **contador (badge)** de Privacy Badger y el de Google Search Fixer llegan
  vacíos: la app sólo pinta el número si la extensión lo publica, así que no se
  muestra ningún contador inventado.
- **Chrome Mask y Google Search Fixer reescriben la UA a la vez**: si ambas están
  activas, la última que actúe manda (medido: GSF gana en `www.google.com`). No
  es un fallo de la app, pero conviene saberlo antes de activar las dos.
- El emulador **arrancó de cero a mitad del paso** (se perdió la sesión en
  curso): la APK, los `.xpi` y el perfil de Gecko **se conservaron** y las
  mediciones de esta tabla se repitieron **después** del reinicio, sobre el
  binario recién compilado con los cambios de este paso.
- Para medir sin depender de la red se puso un **proxy del sistema** en el
  emulador apuntando al servidor propio; se retiró al terminar
  (`settings put global http_proxy :0`) y **no** forma parte de la app.
- No se tocaron el motor, las pestañas, el historial, las descargas, los
  favoritos ni la barra de direcciones. uBO, Dark Reader y SponsorBlock se
  activaron/desactivaron sólo para medirlos y quedaron **activas** las tres.

Capturas y datos: `artifacts/paso8-instaladas.png` (las seis extensiones en
Complementos), `artifacts/paso8-tienda-nuevas.png` (las tres fichas nuevas en la
tienda), `artifacts/paso8-sponsorblock-reinstalado.png`,
`artifacts/paso8-darkreader-on.png` / `artifacts/paso8-darkreader-off.png`
(media de luminancia 31,4 vs 248,3) y `artifacts/paso8-mediciones.log` (registro
del servidor de pruebas con las cabeceras de cada medición).

**Paso 9 identificado (no ejecutado).** Por el orden del plan, el siguiente paso
es el que aún no toca extensiones: **descargas y archivos** (guardar imagen,
vínculo y página sobre el `DownloadManager`, con su lista, su progreso y su
borrado), la deuda más antigua que sigue abierta (las filas `Guardar vínculo
como…` y `Guardar imagen` ya existen, pero no hay pantalla propia de Descargas).
