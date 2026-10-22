# Cambios

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
