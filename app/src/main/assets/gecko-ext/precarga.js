// Extensión interna de Antena · precarga de video por trozos (HLS).
//
// Los reproductores HLS (hls.js) sólo guardan ~30 s por delante y dejan de bajar al llenar ese
// colchón; si el servidor se atasca un momento, el video se queda «esperando». Este script lee la
// lista de trozos (.m3u8) que la página ya pidió y los va bajando por adelantado con fetch normal:
// quedan en la caché HTTP del navegador (en DISCO, no en RAM) y cuando el reproductor los pide se
// sirven al instante. No toca el reproductor ni la reproducción: sólo calienta la caché.
//
// Corre en todos los marcos (el reproductor casi siempre va en un iframe).
(function () {
  if (window.__antenaPrecarga) return;
  window.__antenaPrecarga = true;

  var ADELANTE = 12; // trozos (~1 min) que se mantienen bajados por delante del que se está viendo
  var MARGEN = 12; // lo inmediato lo trae el propio reproductor
  var PARALELO = 2; // descargas simultáneas
  var MAX_FALLOS = 3;

  var listas = {};

  function avisar(texto) {
    try {
      browser.runtime.sendNativeMessage("antena", { tag: "precarga", msg: texto });
    } catch (e) {
      /* el canal aún no está listo */
    }
  }

  /** Trozos de una lista de reproducción de VOD, o null si no sirve (maestra, en vivo, por rangos). */
  function analizar(texto, base) {
    if (texto.indexOf("#EXTINF") < 0) return null; // lista maestra: el reproductor pedirá la de medios
    if (texto.indexOf("#EXT-X-ENDLIST") < 0) return null; // en vivo
    if (texto.indexOf("#EXT-X-BYTERANGE") >= 0) return null;
    var segs = [];
    var t = 0;
    var dur = -1;
    var lineas = texto.split(/\r?\n/);
    for (var i = 0; i < lineas.length; i++) {
      var l = lineas[i].trim();
      if (l.indexOf("#EXTINF:") === 0) {
        dur = parseFloat(l.slice(8)) || 0;
      } else if (l && l.charAt(0) !== "#" && dur >= 0) {
        try {
          segs.push({ u: new URL(l, base).href, ini: t, d: dur });
        } catch (e) {
          /* url rara: se omite */
        }
        t += dur;
        dur = -1;
      }
    }
    return segs;
  }

  function posicion() {
    var v = document.querySelector("video");
    return v && isFinite(v.currentTime) ? v.currentTime : 0;
  }

  function lanzar(L, i) {
    L.pedidos[i] = true;
    L.enVuelo++;
    fetch(L.segs[i].u)
      .then(function (r) {
        if (!r.ok) throw new Error("http " + r.status);
        return r.arrayBuffer();
      })
      .then(function (b) {
        L.hechos[i] = true;
        L.bajados += b.byteLength;
        L.n++;
        if (L.n % 20 === 0) {
          avisar(L.n + "/" + L.segs.length + " trozos bajados en total (" + Math.round(L.bajados / 1048576) + " MB)");
        }
      })
      .catch(function () {
        L.fallos[i] = (L.fallos[i] || 0) + 1;
        if (L.fallos[i] >= MAX_FALLOS) L.hechos[i] = true;
        else delete L.pedidos[i];
      })
      .then(function () {
        L.enVuelo--;
        paso(L);
      });
  }

  function paso(L) {
    var v = document.querySelector("video");
    if (!v) return;
    if (v.ended) {
      clearInterval(L.reloj);
      return;
    }
    var pos = posicion();
    // Trozo que se está viendo; se baja una ventana corta por delante y se avanza con la reproducción.
    var actual = 0;
    while (actual < L.segs.length - 1 && L.segs[actual].ini + L.segs[actual].d <= pos) actual++;
    var fin = Math.min(L.segs.length - 1, actual + ADELANTE);
    for (var i = actual; i <= fin; i++) {
      var s = L.segs[i];
      if (L.hechos[i] || L.pedidos[i] || s.ini + s.d < pos + MARGEN) continue;
      if (L.enVuelo >= PARALELO) break;
      lanzar(L, i);
    }
  }

  function iniciar(url, segs) {
    var L = { segs: segs, hechos: {}, pedidos: {}, fallos: {}, enVuelo: 0, n: 0, bajados: 0, reloj: 0 };
    listas[url] = L;
    avisar("lista de " + segs.length + " trozos en " + location.hostname + ": precargando " + ADELANTE + " trozos por delante");
    L.reloj = setInterval(function () {
      paso(L);
    }, 1500);
    paso(L);
  }

  function ver(e) {
    var url = e.name;
    if (url.indexOf(".m3u8") < 0 || listas[url]) return;
    listas[url] = {};
    fetch(url)
      .then(function (r) {
        return r.ok ? r.text() : "";
      })
      .then(function (t) {
        var segs = t && analizar(t, url);
        if (segs && segs.length) iniciar(url, segs);
      })
      .catch(function () {});
  }

  // Diagnóstico: los trozos que pide el reproductor y tardan pocos ms salieron de la caché.
  var reuso = { rapidos: 0, lentos: 0 };
  function medir(e) {
    if (e.name.indexOf(".m3u8") >= 0 || e.name.indexOf(".ts") < 0) return;
    if (e.duration < 60) reuso.rapidos++;
    else reuso.lentos++;
    var total = reuso.rapidos + reuso.lentos;
    if (total % 10 === 0) avisar("trozos de la página: " + reuso.rapidos + " rápidos (caché) de " + total);
  }

  // Esperas del reproductor (para la Bitácora): cuánto tarda en volver a sonar tras quedarse sin datos o tras un salto.
  var vigilado = null;
  function vigilar() {
    var v = document.querySelector("video");
    if (!v || v === vigilado) return;
    vigilado = v;
    var desde = 0;
    var causa = "";
    v.addEventListener("waiting", function () {
      if (!desde) {
        desde = Date.now();
        causa = v.seeking ? "salto" : "sin datos";
      }
    });
    v.addEventListener("seeking", function () {
      desde = Date.now();
      causa = "salto";
    });
    v.addEventListener("playing", function () {
      if (desde) {
        var ms = Date.now() - desde;
        if (ms > 400) avisar("espera tras " + causa + ": " + ms + " ms (minuto " + Math.round(v.currentTime / 60 * 10) / 10 + ")");
        desde = 0;
      }
    });
  }
  setInterval(vigilar, 1000);

  try {
    new PerformanceObserver(function (lista) {
      lista.getEntries().forEach(function (e) {
        ver(e);
        if (Object.keys(listas).length) medir(e);
      });
    }).observe({ type: "resource", buffered: true });
  } catch (e) {
    /* sin PerformanceObserver: no hay precarga */
  }
})();
