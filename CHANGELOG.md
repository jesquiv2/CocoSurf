# Cambios

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
