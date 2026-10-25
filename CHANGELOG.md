# Cambios

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
