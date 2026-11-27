# Cambios

## 2.3.0 — 2026-11-27
- Las páginas cargan más rápido: ya no esperan a que uBlock termine de arrancar (en la TV tarda unos 13 s). Esperan solo a la lista de anuncios propia (como mucho 4 s) y cargan filtradas por ella; cuando uBlock está listo, la página se recarga una vez para sumar su filtro. A cambio, la primera carga tras abrir la app puede mostrar un momento algo que solo uBlock bloquea.

## 2.2.1 — 2026-11-27
- El punto de aviso de actualización del botón del menú ya no choca con el borde del botón al poner el cursor encima: va por encima del borde y con un aro del color de la barra.
- Ya no sale el chip «Extensión instalada: Coco Surf v1.11» al actualizar: la extensión interna de la app se actualiza sola y no es un aviso para ti.
- La lista de anuncios suma dos cargadores de publicidad que ninguna lista pública trae (`eruptcapone.com`, `agl003.com`): eran los que dibujaban los botones flotantes con check y «Skip Ad» que quedaban visibles aunque la imagen del anuncio estuviera bloqueada.

## 2.2.0 — 2026-11-27
- Lista de anuncios propia de la app (HaGeZi «Pro Mini», la versión para aparatos con poca memoria: ~95 mil dominios). Bloquea por red la publicidad, el rastreo y las estafas con el bloqueador que escojas, o sin ninguno, y no depende de uBlock. Se baja sola la primera vez, se renueva sola cada día y las páginas abiertas se recargan al activarse. En Configuración → General → «Bloqueo de anuncios» se ve la lista, su estado, cuántos dominios tiene, cuándo se actualizó y cuántos bloqueó. Hallazgo en la TV: uBlock funciona, pero sus listas no traían los dominios de los banners de 1XBET de JK Anime; esta lista sí.
- La lista no frena los anuncios que abren pestañas nuevas ni las redirecciones (eso lo hacen las protecciones de uBlock); por eso conviene tener uBlock Origin además.

## 2.1.0 — 2026-11-27
- La actualización se hace de principio a fin con un solo toque. La fila del menú ocupa una sola línea («Actualizar a X»); al tocarla muestra un anillo con el porcentaje, y si cierras el menú el botón del menú parpadea con su anillo mientras descarga. Al terminar abre el instalador sola (la primera vez pide el permiso de «apps desconocidas» y, al volver, sigue sin que repitas nada). Android siempre pide una confirmación final en el instalador: eso no se puede saltar.
- El bloqueador responde a lo que haces: si una página cargó antes de que hubiera bloqueador (instalación nueva, o se estaba instalando), se recarga sola unos segundos después de que llega, esperando a que pueda filtrar (si uBlock marca «!», reintenta). Y al instalar, activar, desactivar o quitar un bloqueador desde Complementos, las pestañas abiertas se recargan solas. Probado en el emulador con anuncios reales de JK Anime.
- Resultado de pruebas por bloqueador en JK Anime: uBlock Origin bloquea los anuncios (banner de apuestas y ventana flotante); AdGuard AdBlocker y Privacy Badger, solos, NO los bloquean.
- Los respaldos ahora se guardan también en Descargas/CocoSurf (con el permiso de archivos), fuera de la carpeta de la app, para que sobrevivan a una reinstalación o a un cambio de identificador. Importar lista los de las dos carpetas.

## 2.0.0 — 2026-11-27
- Arreglo de fondo de los anuncios: las pestañas que la app restaura al abrir (o tras actualizar) cargaban antes de que el bloqueador terminara de arrancar y se quedaban sin filtrar hasta cerrarlas y volver a abrirlas. Ahora toda carga espera a que el bloqueador esté listo (si no hay ninguno activo no espera; si tarda más de 40 s carga igual y recarga la página en cuanto llega). Sirve con el bloqueador que escojas.
- Se cambia el nombre interno anterior en todo el proyecto: paquete, clases, archivos, extensión interna, base de datos y textos. El identificador de la app cambia a `com.cocosurf.app`, así que en la TV se instala como una app nueva (desinstala la anterior; los datos guardados no pasan solos: usa «Respaldo» antes).

## 1.13.1 — 2026-11-26
- Se regresa al bloqueador de antes: uBlock Origin completo es otra vez el que se instala por defecto (una sola vez, si no está) y la tienda de complementos lo ofrece primero. Con la «Lite» y la limpieza automática de bloqueadores de la 1.11.0 los anuncios volvieron por todas partes. Se quita la migración, la lista de «pendientes por quitar» y la desinstalación automática. Lo demás (historial de pestañas, onda del clic, barras, distribución para Play) se mantiene. Cambio esperado: la primera página puede volver a tardar al abrir, como antes de la 1.10.3.

## 1.13.0 — 2026-11-25
- Versión para Google Play (distribución «play»): sin autoactualizador, sin «acceso a todos los archivos» ni permisos de fotos/videos/audio, con `targetSdk` 36 y paquete AAB. La distribución «directo» (APK de GitHub) sigue igual. Se agregan `docs/PRIVACIDAD.md`, `docs/FICHA_GOOGLE_PLAY.md` y `docs/DISTRIBUCION.md` (cómo publicar en Play y otras tiendas).
- La onda del clic ahora es una vista nativa animada en el hilo de render: ya no se corta cuando el clic abre un panel, una pestaña o hace trabajar a la interfaz.

## 1.12.1 — 2026-11-25
- Fluidez en la TV: al subir o bajar las barras, la página se redimensionaba unas 15 veces durante la animación (cada una obliga al motor a recomponer la página) y se pegaba. Ahora la página cambia de tamaño una sola vez y la barra se desliza por encima sólo al dibujar.

## 1.12.0 — 2026-11-25
- Pestañas restauradas con su historial: al reabrir la app, «atrás» y «adelante» siguen funcionando en cada pestaña (antes volvían sólo con la última página y no había a dónde regresar). Lo mismo para las pestañas que se sueltan por falta de memoria y se recargan.
- Toque del cursor: el círculo ámbar relleno se cambió por una onda difusa de agua que se abre y se desvanece sin tapar nada. Además, el botón pulsado ya no pierde su borde al hacer clic.
- Tope de arriba dos veces: al llegar arriba el asa se ensancha; si empujas ↑ otra vez, bajan las barras (como Atrás).
- Configuración → Control remoto: «Quitar la tecla» ahora explica qué hace (deja el atajo sin tecla).

## 1.11.0 — 2026-11-25
- Primera página lenta, causa real (Bitácora de la TV): estaban cargando a la vez uBlock Origin completo, AdGuard y Privacy Badger, y Gecko retiene las páginas hasta que cada uno termina de iniciar. Las desinstalaciones que hacías no se guardaban (el sistema cerraba la app antes de que Gecko las escribiera) y volvían al abrir. Ahora cada desinstalación se anota como pendiente y se repite en cada arranque hasta que de verdad desaparece. Migración única: se instala uBlock Origin Lite y se quitan uBlock completo, AdGuard y Privacy Badger. La Bitácora anota «Complementos al arrancar» con lo que encontró y lo que quita.
- Actualizaciones: al abrir el menú o «Acerca de» se busca versión nueva en segundo plano (sin bloquear nada). Si hay, el menú muestra «Actualizar a X» y «Acerca de» lleva un punto de aviso. «Actualizar ahora» descarga y abre el instalador de una vez (antes había que pulsar Descargar y luego Instalar).

## 1.10.5 — 2026-11-25
- Primera página lenta: la Bitácora de la TV mostró que seguían corriendo uBlock Origin completo y la Lite a la vez. La Lite sí se instaló en la 1.10.3, pero el completo nunca se quitó (la lista de extensiones llegaba vacía cuando se decidía) y la migración quedó marcada como hecha, así que no se reintentaba. Ahora se usa una marca nueva, se espera a tener la lista, y si hay Lite y completo a la vez se quita el completo. La Bitácora anota «Bloqueador: extensiones […]» con lo que encontró.

## 1.10.4 — 2026-11-21
- Inicio: ya no asoma la página de Inicio antes de las pestañas que tenías abiertas; se ve el fondo liso hasta restaurarlas y aparecen directamente tus pestañas con la última activa.
- Nuevo ajuste (Configuración → Control remoto → «Cursor o foco»): asigna una tecla del mando que apaga el cursor (las flechas mueven el foco entre los botones) o lo vuelve a encender.
- Trabas del cursor: según la Bitácora de la TV la interfaz se paraba ~2,3 s cada ~40 s durante un video. La Bitácora escribía en disco desde el hilo principal; ahora lo hace un hilo aparte. Además la interfaz parada anota «Interfaz parada en: …» con lo que estaba ejecutando, para saber qué la frena si sigue pasando.
- Bloqueador: el cambio al uBlock Origin Lite no se había hecho en la TV porque la lista de extensiones tarda en llegar; ahora la espera antes de decidir, anota en la Bitácora lo que ve y quita el completo cuando la Lite ya está instalada.

## 1.10.3 — 2026-11-17
- Arranque rápido: la espera de 15–40 s hasta ver la primera página venía de uBlock Origin completo (cargaba sus listas en cada arranque y retenía las páginas hasta terminar). Medido en el emulador: con él, 21–43 s hasta el primer contenido; con uBlock Origin Lite, ~5 s. La Lite bloquea con reglas que el propio motor ya tiene compiladas.
- uBlock Origin Lite es ahora el bloqueador por defecto. Quien tenía el completo pasa a la Lite una sola vez (el completo se quita sólo cuando la Lite ya quedó instalada). Se pierden los ajustes propios del completo; el selector de elementos y los filtros personalizados no existen en la Lite. Si prefieres el completo puedes instalarlo desde Complementos (a costa del arranque lento).

## 1.10.2 — 2026-11-17
- Arregla un cierre al abrir la app introducido en la 1.10.1 (el motor se creaba con la actividad ya activa y GeckoView se caía). Se vuelve al arranque anterior; la 1.10.1 no debe usarse.

## 1.10.1 — 2026-11-17
- Buscador: ya no atrapa al usuario. Tiene un botón ✕, Atrás lo cierra siempre (aunque el teclado tenga el foco) y el cursor sigue activo para tocar fuera.
- Arranque: el motor arranca justo después de pintar la primera pantalla, a la par de la carga de ajustes y pestañas, en vez de bloquearlas.

## 1.10.0 — 2026-11-03
- «Acerca de» rediseñado: el icono, más pequeño, va a la izquierda y junto a él la versión, el motor y el dispositivo. Un botón «Otros» despliega (y vuelve a plegar) Android, arquitectura y memoria. Se quita la línea del motor bajo los créditos; quedan los de JEM Desarrollos y «Invítame un café».
- Doble clic sobre un botón de la interfaz (p. ej. «Exportar» en Bitácora o Diagnóstico): el segundo clic ya no cae en lo que aparece debajo —la barra de direcciones abría el teclado y apagaba el cursor—. El cursor ignora un clic repetido en el mismo punto dentro de 0,45 s sobre la interfaz propia. Además, soltar OK tras una pulsación larga siempre levanta el toque del cursor.
- Mantener OK sobre un favorito, una entrada del historial o una descarga abre un modal con lo que se puede hacer: abrir, mover a una carpeta, copiar la dirección, compartir el enlace, enviar el archivo a otra app (LocalSend, Bluetooth…) y eliminar. Borrar siempre pide confirmación y el foco nace en «Cancelar». Lo mismo en los paneles rápidos, donde se quita la «X» de cada fila. Mantener OK sobre una carpeta de favoritos ofrece eliminarla.
- Respaldo: Configuración → Respaldo exporta a un solo .json los favoritos y carpetas, el historial, el registro de descargas, los motores, los ajustes y (opcional) las contraseñas, y lo ofrece enviar a otra app. «Importar respaldo» lista los de la carpeta de Descargas o abre el selector del sistema, y suma el contenido a lo que ya hay.
- La búsqueda es ahora un modal pequeño bajo la barra, con la página a la vista: «En la página» (busca el texto, resalta y salta entre coincidencias), «Pestañas» (abiertas y cerradas hace poco) e «Historial».

## 1.9.2 — 2026-11-02
- Causa real de las trabas de video medida contra el servidor: de los 6 servidores que reparten los trozos (cdn1…cdn6.ducvomes.com), cdn4 y cdn6 tardan 3–80 s por trozo y los otros ~1,3 s; como el 32 % de los trozos cae en esos dos, el video se congelaba a cada rato. El mismo archivo existe en los servidores rápidos.
- Al empezar un video HLS se prueba un trozo de cada servidor de la familia; los lentos (menos de 0,35 de la velocidad del mejor) se redirigen a uno rápido, previa verificación de que el trozo llega igual. Si el servidor rápido falla se anula el desvío. La Bitácora anota «servidores lentos desviados: cdn4→cdn2, …» con lo que tardó cada uno.
- La precarga espera a ese sondeo (unos segundos) para no gastar conexiones en servidores lentos.

## 1.9.1 — 2026-11-02
- Con un servidor de video lento (según la Bitácora de la TV) la pausa para juntar colchón se repetía cada 90 s y casi no dejaba reproducir. Ahora, si tras 15 s pausado el colchón no crece, se reanuda y no se vuelve a pausar durante 5 minutos; la espera máxima baja a 45 s (60 s con servidor con problemas).

## 1.9.0 — 2026-11-02
- Medición de velocidad en la Bitácora, para tener datos reales de dónde se va la espera:
  - Arranque: «ventana creada», «motor Firefox creado», «extensión interna lista», «complemento listo (uBlock)» y «proceso de contenido listo», cada uno con los segundos desde que se abre la app.
  - Cada página que se abre: tiempo hasta el primer contenido visible, tiempo hasta la carga completa, cuánto de eso fue esperar al motor, memoria libre en ese momento, y media y peor tiempo de la sesión. La primera lleva la marca «PRIMERA página».
  - Interfaz sin responder: si la app deja de atender al usuario más de 0,5 s (cursor pegado, botones sin respuesta) lo anota con la memoria libre.

## 1.8.1 — 2026-11-02
- Las barras (pestañas y dirección) se esconden siempre al bajar la página, también cuando las habías abierto con Atrás o ↑; antes solo se escondían al terminar de cargar.
- Menú en mosaico más compacto: botones cuadrados de 66 dp y ventana mucho más estrecha; «Salir» queda centrado.
- Iconos nuevos: engranaje con dientes y agujero (Configuración), pieza de rompecabezas (Complementos), «i» en círculo para Información del sistema y una etiqueta para Acerca de.

## 1.8.0 — 2026-11-01
- Menú principal en mosaico: botones de solo icono en 3 columnas (mucho menos alto). El nombre del botón con el foco o el cursor encima se lee debajo del mosaico. «Salir de Coco Surf» sigue aparte, en rojo.

## 1.7.2 — 2026-11-01
- Descarga de la actualización resistente a cortes: si la conexión se cae, el archivo parcial se conserva y se retoma donde quedó (hasta 30 reintentos seguidos), en vez de botarlo y empezar de cero. Si se rinde, pulsar de nuevo continúa desde ese punto.
- Monitor: la velocidad de red cambia de unidad sola (KB/s → MB/s al pasar de 1024 KB/s).
- Mantener «Atrás» unos 3 segundos abre la confirmación para cerrar la aplicación.

## 1.7.1 — 2026-11-01
- Colchón de video más grande: tras quedarse sin datos junta ~60 s (hasta 90 s si el servidor da problemas) y al arrancar ~40 s; cada pausa puede durar hasta 60 s (90 s con servidor lento).
- Monitor: el ⚠ de servidor con problemas pasa delante del número de «Colchón» y la fila «Servidor» es más ancha para leer el nombre completo.

## 1.7.0 — 2026-11-01
- Aviso de espera discreto: un punto ámbar que palpita arriba a la derecha (con el nombre del servidor sólo si da problemas), en vez del cuadro grande.
- Detección de servidor con problemas (descarga más lenta que ~2x el video, fallos de trozos, o el video se queda sin datos): durante 2 minutos la precarga sube de 60 a 120 s por delante, abre 4 descargas a la vez y las pausas juntan un colchón 60 % mayor (hasta 60 s de espera).
- La precarga se mide en segundos (60 s normal) en vez de en trozos.
- Monitor: nuevas cifras «Caché video» (segundos ya guardados en disco por delante), «Colchón» (lo que tiene el reproductor) y «Servidor» (con ⚠ si da problemas). «Caché video» y «Colchón» se activan por defecto en instalaciones nuevas; si ya tenías el monitor configurado, actívalas en Configuración → Monitor.

## 1.6.2 — 2026-11-01
- Video HLS (JKAnime y similares): al arrancar, el video se pausa hasta tener ~25 s cargados y luego sigue; si a media reproducción se queda sin datos, hace una pausa breve para juntar ~15 s en vez de trabarse a cada rato. Mientras tanto muestra en pantalla «Cargando video» / «Conexión inestable» con los segundos juntados. Cada pausa tiene tope de 30 s.
- La precarga a disco cede el ancho de banda al reproductor mientras este llena su propio colchón.

## 1.6.1 — 2026-11-01
- Precarga de video en ventana corta: en vez de 10 minutos, mantiene bajados unos 12 trozos (~1 min) por delante del que estás viendo y avanza con la reproducción. Menos disco y datos, y deja de bajar al terminar el video.

## 1.6.0 — 2026-11-01
- Precarga de video por trozos (HLS, como el de JKAnime): la app baja por adelantado hasta 10 minutos de video a la caché de DISCO (no RAM) aunque el reproductor de la página solo guarde unos segundos. Si el servidor se atasca un momento, el video sigue sin esperas porque los trozos ya están en el televisor.
- La caché de disco sube a 512 MB. La Bitácora anota cuántos trozos hay en caché y cada espera de más de 0,4 s del reproductor.

## 1.5.0 — 2026-11-01
- Página para el PC (compartir por red) rehecha como un explorador de archivos: iconos por tipo, orden por nombre/tamaño/fecha, búsqueda, selección múltiple, renombrar, borrar con confirmación, nueva carpeta y vista previa (imágenes, video, audio, PDF y texto) con ← →.
- Subidas arrastrando y soltando (también carpetas enteras) o con los botones, con barra de progreso por archivo; si el nombre ya existe se guarda como «nombre (1)».
- Descarga de carpetas o de la selección como ZIP, sin armarlo antes en el televisor.

## 1.4.5 — 2026-10-31
- Archivos: «Dar permiso» ahora muestra el diálogo normal de Android («¿Permitir el acceso a archivos?») en vez de mandar a Ajustes; el acceso total por Ajustes queda como botón aparte.

## 1.4.4 — 2026-10-31
- «Dar permiso» (Archivos) ya no falla en silencio: si el televisor no tiene la pantalla de «Acceso a todos los archivos» prueba otras de Ajustes y, si ninguna existe, muestra en pantalla el comando ADB para concederlo.
- El permiso se vuelve a comprobar solo al regresar de Ajustes; «Ya lo di: actualizar» ahora avisa si todavía no está concedido.

## 1.4.3 — 2026-10-31
- Diálogo «¿Salir de Coco Surf?»: el foco ya no se escapa. ↑ y ↓ no hacen nada y ← → alternan entre Cancelar y Salir, así que no se puede perder el control hacia la página.

## 1.4.2 — 2026-10-31
- El reproductor se abre en su propia pestaña (ya no tapa todo): puedes irte a otra pestaña y volver; al salir se guarda por dónde ibas y al abrir el archivo se retoma ahí (historial de los últimos 60 archivos).
- Lista de reproducción: con ↓ sale un panel a la izquierda con los videos y audios de la misma carpeta (con el minuto donde quedó cada uno); OK reproduce, y al terminar pasa solo al siguiente.
- Errores de reproducción con detalle para diagnosticar: código del error, causa, nombre y tamaño del archivo, cabecera (formato real), pistas con su códec y si el televisor las soporta. También queda en la Bitácora.
- Si lo descargado no es un video (una página web guardada como .mp4, como pasa con MediaFire) lo dice claro en vez de fallar con «parsing container».
- En un error, OK abre el archivo con otra aplicación del televisor.

## 1.4.1 — 2026-10-31
- Actualizaciones en segundo plano: se busca al abrir (a los 20 s) y cada 6 h. Si hay versión nueva, el botón del menú lleva un punto ámbar y el menú un renglón «Actualización X» arriba.
- El instalador descargado se conserva entre sesiones: si ya está completo (mismo tamaño que en GitHub) la app lo reconoce como «Listo» y no lo baja otra vez. Se descarga a un archivo parcial y solo se da por bueno si queda completo.
- Limpieza: al comprobar se borran los APK de versiones ya instaladas o viejas y los parciales, para no acumular cientos de MB.
- Paneles (Descargas, Favoritos, Historial…) y menú: suben cuando la barra superior se esconde y bajan cuando vuelve, ya no quedan flotando solos.

## 1.4.0 — 2026-10-31
- Archivos: nueva página (menú → Archivos) para explorar las descargas y todo el almacenamiento (pide «Acceso a todos los archivos»), instalar APK, abrir archivos con otra app, crear carpetas y eliminar (mantener OK).
- Reproductor interno de video y audio (ExoPlayer): OK pausa, ← → saltan 10 s (mantenidas, 30 s), Atrás cierra.
- Compartir carpeta por red: servidor HTTP con IP, puerto (8080–8090) y PIN; desde un PC se puede ver, bajar, subir, crear carpetas y borrar. Se apaga al cerrar la app.
- Descargas: ya no se abre una página en blanco; el panel de Descargas muestra el avance del archivo y la ventana vacía se cierra sola.
- Cursor: corregido que hiciera falta pulsar 2–3 veces (toques y cursor usaban el mismo puntero); aceleración desde el reposo y parada inmediata como TV Bro; se quita el imán.
- Cursor: tecla configurable (Configuración) para alternar movimiento largo o corto.

## 1.3.1 — 2026-10-24
- APK release con R8 (código recortado): el dex ya no se carga entero; baja unos 30–40 MB el proceso de la interfaz. Firmado con la clave de depuración hasta tener una propia.
- Precalentar el motor: en Inicio se abre una sesión vacía para que el proceso de contenido ya esté listo cuando se abre la primera página (ajustable en Configuración). La Bitácora anota «Motor precalentado» y «Complemento listo».

## 1.3.0 — 2026-10-24
- Recordar pestañas: al cerrar la app se guardan las páginas abiertas y se restauran al abrirla (se puede apagar en Configuración).
- Buscar actualización en «Acerca de» y aviso diario: lee la última Release de GitHub, descarga el APK y abre el instalador (requiere repositorio/Release público).
- AnimeFLV: la «OPCIÓN 1» (HLS, sólo Safari) ya no es la predeterminada; se elige la primera opción con reproductor web.
- Menú contextual (mantener OK): queda fijo donde nació, el cursor se mueve libre encima.
- Cursor: todas las velocidades al doble; imán que centra el cursor en iconos y botones pequeños (se puede apagar).
- Asas de arriba/abajo: una sola pastilla que crece al llegar al tope (ya no aparece una segunda).
- Diálogo «¿Salir?»: se elige con las flechas del control, sin depender del cursor.
- Bitácora: anota cuánto tarda el bloqueador (uBlock) en estar listo tras arrancar el motor; aviso en pantalla durante la primera carga.

## 1.2.1 — 2026-10-24
- Video: con un video en curso, la Bitácora anota cada medición (libre, páginas, gráficos, complementos, interfaz, caché, apps, kernel, controladores) para ver qué se come la memoria.
- Video: con memoria baja y video sonando, se le pide al motor que limpie cachés (una vez por minuto).
- Corregido: el registro de recursos mostraba siempre la página y las pestañas del inicio, no las actuales.

## 1.2.0 — 2026-10-22
- Diagnóstico: reparto de la RAM de TODO el aparato (libre, caché, apps, kernel, controladores de vídeo/gráficos, CMA, swap) y procesos de Coco Surf.
- Monitor: nuevas cifras de RAM del aparato (apps, caché, kernel, vídeo/gráficos).
- Bitácora: el reparto de RAM del aparato se anota con cada cambio de presión y cada ~5 min.
- herramientas/memoria-tv.ps1: registra desde el PC, con adb, la memoria de todas las apps del televisor (CSV + avisos de picos).

## 1.1.1 — 2026-10-20
- Repositorio: .gitignore ampliado (capturas, borradores, .claude, .remember, graft, .kotlin, logs, APK) y archivos sueltos fuera del control de versiones.

## 1.1.0 — 2026-10-19
- Scroll: la rueda va directo al motor (las asas ya no la tapan): corrige no poder subir. Las asas se ocultan mientras se desplaza y queda un solo indicador.
- Prioridad al video: el motor de la pestaña con video sonando pasa a prioridad alta y la app reduce trabajo de fondo.
- Contraseñas: importar desde CSV (Chrome/Edge/Firefox/Bitwarden).

## 1.0.0 — 2026-10-19
- Primera versión numerada como 1.0 (antes 0.4.0).
- Scroll interno con rueda en el cursor (YouTube, Facebook…), reciclaje de páginas por memoria,
  preferencias de motor ligeras (sin AV1/VP9), pausa de media al cambiar de pestaña.
- Inicio: buscador y fondo configurable. Gestor de contraseñas propio. Ko-fi en «Acerca de».
