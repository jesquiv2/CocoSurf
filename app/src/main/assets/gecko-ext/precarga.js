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
  var COLCHON_INICIO = 25; // s de video que se juntan antes de arrancar
  var COLCHON_CORTE = 15; // s que se juntan cuando se queda sin datos a media reproducción
  var ESPERA_MAX = 30000; // ms: tope de cada pausa, para no dejar el video detenido

  var listas = {};
  var hls = false; // hay un video HLS bajo control
  var G = { on: false, arranco: false, obj: 0, desde: 0, mejor: 0, tMejor: 0, crec: 0, ultimo: 0, motivo: "" };

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
    // Mientras el reproductor llena su propio colchón, no se le quita ancho de banda.
    if (G.ultimo < 6 && Date.now() - G.crec < 4000) return;
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
    hls = true;
    G.arranco = false;
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

  /** Segundos de video ya cargados por delante del punto de reproducción. */
  function adelante(v) {
    var t = v.currentTime;
    var b = v.buffered;
    for (var i = 0; i < b.length; i++) {
      if (b.start(i) <= t + 0.3 && b.end(i) >= t) return b.end(i) - t;
    }
    return 0;
  }

  // Aviso en pantalla mientras el video espera (se pone dentro del elemento a pantalla completa si lo hay).
  var panel = null;
  var linea1 = null;
  var linea2 = null;
  function mostrar(t1, t2) {
    var host = document.fullscreenElement;
    if (!host || host.tagName === "VIDEO") host = document.body || document.documentElement;
    if (!panel) {
      var css = document.createElement("style");
      css.textContent = "@keyframes antena-giro{to{transform:rotate(360deg)}}";
      (document.head || host).appendChild(css);
      panel = document.createElement("div");
      panel.style.cssText =
        "position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);z-index:2147483647;" +
        "background:rgba(0,0,0,.85);color:#fff;font-family:sans-serif;padding:20px 30px;" +
        "border-radius:16px;text-align:center;pointer-events:none;max-width:80%";
      var rueda = document.createElement("div");
      rueda.style.cssText =
        "width:36px;height:36px;margin:0 auto 12px;border:4px solid rgba(255,255,255,.25);" +
        "border-top-color:#fff;border-radius:50%;animation:antena-giro .9s linear infinite";
      linea1 = document.createElement("div");
      linea1.style.cssText = "font-size:22px;font-weight:700";
      linea2 = document.createElement("div");
      linea2.style.cssText = "font-size:16px;margin-top:6px;opacity:.85";
      panel.appendChild(rueda);
      panel.appendChild(linea1);
      panel.appendChild(linea2);
    }
    if (panel.parentNode !== host) host.appendChild(panel);
    linea1.textContent = t1;
    linea2.textContent = t2;
  }
  function ocultar() {
    if (panel && panel.parentNode) panel.parentNode.removeChild(panel);
  }

  function soltar(v, por) {
    G.on = false;
    G.arranco = true;
    ocultar();
    avisar(
      "reanudado tras " + Math.round((Date.now() - G.desde) / 1000) + " s con " + Math.round(adelante(v)) + " s de colchón (" + por + ")"
    );
    if (por === "fin") return;
    try {
      var p = v.play();
      if (p && p.catch) p.catch(function () {});
    } catch (e) {
      /* el reproductor decide */
    }
  }

  // Pausa el video hasta tener unos segundos cargados (al arrancar o cuando se queda sin datos)
  // y lo dice en pantalla; así no se traba a cada rato con la conexión inestable.
  function vigilarColchon() {
    if (!hls) return;
    var v = document.querySelector("video");
    if (!v) return;
    var ahora = Date.now();
    var ad = adelante(v);
    if (ad > G.ultimo + 0.3) G.crec = ahora;
    G.ultimo = ad;
    var resta = isFinite(v.duration) ? v.duration - v.currentTime : 1e9;
    if (v.ended || resta < 3) {
      if (G.on) soltar(v, "fin");
      return;
    }
    if (!G.arranco && v.currentTime >= 3) G.arranco = true;
    if (!G.on) {
      if (v.paused || resta <= 6) return;
      var obj = G.arranco ? COLCHON_CORTE : COLCHON_INICIO;
      if (!(ad < 1.5 || (!G.arranco && ad < obj))) return;
      G.on = true;
      G.desde = ahora;
      G.mejor = ad;
      G.tMejor = ahora;
      G.obj = Math.min(obj, resta - 1);
      G.motivo = G.arranco ? "conexión inestable" : "arranque";
      v.pause();
      avisar("pausa para juntar " + Math.round(G.obj) + " s de video (" + G.motivo + ") en el minuto " + Math.round(v.currentTime / 6) / 10);
    } else {
      if (!v.paused) v.pause();
      if (ad > G.mejor + 0.5) {
        G.mejor = ad;
        G.tMejor = ahora;
      }
      var meseta = ad >= 6 && ahora - G.tMejor > 6000; // el reproductor ya no guarda más
      if (ad >= G.obj || meseta || ahora - G.desde > ESPERA_MAX) {
        soltar(v, ad >= G.obj ? "colchón listo" : meseta ? "el reproductor no guarda más" : "tiempo máximo");
        return;
      }
    }
    mostrar(
      (G.arranco ? "Conexión inestable" : "Cargando video") + " · " + Math.floor(ad) + " de " + Math.round(G.obj) + " s",
      G.arranco ? "Pausa breve para juntar video y evitar cortes" : "Juntando unos segundos antes de empezar"
    );
  }
  setInterval(vigilarColchon, 500);

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
