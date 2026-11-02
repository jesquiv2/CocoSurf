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

  var VENTANA = 60; // s de video que se mantienen bajados por delante de lo que se está viendo
  var VENTANA_MALA = 120; // ídem cuando el servidor da problemas
  var MARGEN = 12; // lo inmediato lo trae el propio reproductor
  var PARALELO = 2; // descargas simultáneas
  var PARALELO_MALO = 4; // con servidor lento se abren más conexiones (muchos limitan cada una)
  var MAX_FALLOS = 3;
  var COLCHON_INICIO = 40; // s de video que se juntan antes de arrancar
  var COLCHON_CORTE = 60; // s que se juntan cuando se queda sin datos a media reproducción

  var listas = {};
  var hls = false; // hay un video HLS bajo control
  var servidor = ""; // servidor de donde salen los trozos
  var LA = null; // lista activa
  var malaHasta = 0; // hasta cuándo se considera que el servidor da problemas
  var ritmo = 0; // s de video bajados por segundo real (media móvil)
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

  function servidorMalo() {
    return Date.now() < malaHasta;
  }

  function marcarMalo(por) {
    var primera = !servidorMalo();
    malaHasta = Date.now() + 120000;
    if (primera) avisar("servidor con problemas (" + servidor + "): " + por + "; la precarga se amplía a " + VENTANA_MALA + " s");
  }

  function medirRitmo(d, ms) {
    var r = d / Math.max(ms / 1000, 0.05);
    ritmo = ritmo ? ritmo * 0.7 + r * 0.3 : r;
    if (ritmo < 2) marcarMalo("descarga lenta (" + Math.round(ritmo * 10) / 10 + "x)");
  }

  // ── Servidores lentos ────────────────────────────────────────────────────────────────────────
  // Muchas CDN reparten los trozos entre cdn1, cdn2, cdn3… y casi siempre alguno va mucho más lento
  // (medido: cdn4 y cdn6 de ducvomes tardan 15–80 s por trozo y cdn1/2/3/5 ~1 s). Cada trozo que cae
  // en uno lento congela el video. Al empezar se prueba un trozo de cada servidor de la familia y, si
  // el mismo archivo existe en uno rápido, el fondo redirige ahí los pedidos del lento.
  var desvios = {}; // servidor lento → servidor rápido

  function hostDe(u) {
    try {
      return new URL(u).host;
    } catch (e) {
      return "";
    }
  }

  /** «cdn4.sitio.com» → «cdn#.sitio.com»: servidores que sólo se distinguen por un número. */
  function familia(h) {
    var m = /^([a-z-]*?)(\d+)(\..+)$/i.exec(h);
    return m ? m[1] + "#" + m[3] : "";
  }

  function mapear(u) {
    var h = hostDe(u);
    var a = desvios[h];
    return a ? u.replace("//" + h + "/", "//" + a + "/") : u;
  }

  function avisarFondo(m) {
    try {
      var p = browser.runtime.sendMessage(m);
      if (p && p.catch) p.catch(function () {});
    } catch (e) {
      /* el fondo aún no está listo */
    }
  }

  function quitarDesvios() {
    if (!Object.keys(desvios).length) return;
    desvios = {};
    avisarFondo({ limpiar: true });
    avisar("desvíos de servidor anulados: el servidor rápido falló");
  }

  /** Baja un trozo entero con tope de tiempo y dice cuánto tardó. */
  function medirTrozo(u, limite) {
    var t0 = Date.now();
    var ctl = typeof AbortController === "function" ? new AbortController() : null;
    var reloj = ctl
      ? setTimeout(function () {
          ctl.abort();
        }, limite)
      : 0;
    return fetch(u, ctl ? { signal: ctl.signal } : undefined)
      .then(function (r) {
        if (!r.ok) throw new Error("http " + r.status);
        return r.arrayBuffer();
      })
      .then(
        function (b) {
          clearTimeout(reloj);
          return { ok: true, bytes: b.byteLength, ms: Math.max(Date.now() - t0, 1), b0: new Uint8Array(b)[0] };
        },
        function () {
          clearTimeout(reloj);
          return { ok: false, bytes: 0, ms: Date.now() - t0, b0: -1 };
        }
      );
  }

  function sondear(L) {
    var primero = {};
    var hosts = [];
    L.segs.forEach(function (sg, i) {
      var h = hostDe(sg.u);
      if (!h || primero[h] !== undefined) return;
      primero[h] = i;
      hosts.push(h);
    });
    var fams = {};
    hosts.forEach(function (h) {
      var f = familia(h);
      if (f) (fams[f] = fams[f] || []).push(h);
    });
    var lista = [];
    Object.keys(fams).forEach(function (f) {
      if (fams[f].length > lista.length) lista = fams[f];
    });
    lista = lista.slice(0, 8);
    if (lista.length < 2) {
      L.listo = true;
      return;
    }
    Promise.all(
      lista.map(function (h) {
        return medirTrozo(L.segs[primero[h]].u, 8000);
      })
    )
      .then(function (res) {
        var vel = {};
        var mejor = 0;
        res.forEach(function (r, k) {
          // Segundos de video bajados por segundo real: no depende del tamaño del trozo (los pequeños son todo latencia).
          vel[lista[k]] = r.ok ? L.segs[primero[lista[k]]].d / r.ms : 0;
          if (vel[lista[k]] > mejor) mejor = vel[lista[k]];
        });
        if (!mejor) return;
        var rapidos = lista.filter(function (h) {
          return vel[h] >= mejor * 0.6;
        });
        var lentos = lista.filter(function (h) {
          return vel[h] < mejor * 0.35;
        });
        var detalle = lista
          .map(function (h, k) {
            return h.split(".")[0] + " " + (res[k].ok ? Math.round(res[k].ms / 100) / 10 + " s" : "sin respuesta");
          })
          .join(", ");
        if (!lentos.length) {
          avisar("servidores del video parejos (" + detalle + ")");
          return;
        }
        var nuevos = {};
        var rr = 0;
        return Promise.all(
          lentos.map(function (h) {
            var alt = rapidos[rr++ % rapidos.length];
            var lenta = res[lista.indexOf(h)];
            var uAlt = L.segs[primero[h]].u.replace("//" + h + "/", "//" + alt + "/");
            return medirTrozo(uAlt, 8000).then(function (r) {
              var igual = lenta.ok ? r.bytes === lenta.bytes : r.b0 === 0x47;
              if (r.ok && r.bytes > 10000 && igual) nuevos[h] = alt;
            });
          })
        ).then(function () {
          var ks = Object.keys(nuevos);
          if (!ks.length) {
            avisar("servidores lentos sin alternativa (" + detalle + ")");
            return;
          }
          desvios = nuevos;
          avisarFondo({ desvios: nuevos });
          avisar(
            "servidores lentos desviados: " +
              ks
                .map(function (h) {
                  return h.split(".")[0] + "→" + nuevos[h].split(".")[0];
                })
                .join(", ") +
              " (" + detalle + ")"
          );
        });
      })
      .catch(function () {})
      .then(function () {
        L.listo = true;
        paso(L);
      });
  }

  function posicion() {
    var v = document.querySelector("video");
    return v && isFinite(v.currentTime) ? v.currentTime : 0;
  }

  function lanzar(L, i) {
    L.pedidos[i] = true;
    L.enVuelo++;
    var t0 = Date.now();
    var url = mapear(L.segs[i].u);
    fetch(url)
      .then(function (r) {
        if (!r.ok) throw new Error("http " + r.status);
        return r.arrayBuffer();
      })
      .then(function (b) {
        L.hechos[i] = true;
        medirRitmo(L.segs[i].d, Date.now() - t0);
        L.bajados += b.byteLength;
        L.n++;
        if (L.n % 20 === 0) {
          avisar(L.n + "/" + L.segs.length + " trozos bajados en total (" + Math.round(L.bajados / 1048576) + " MB)");
        }
      })
      .catch(function () {
        if (url !== L.segs[i].u) quitarDesvios();
        marcarMalo("falla al bajar un trozo");
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
    if (!v || !L.listo) return;
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
    var malo = servidorMalo();
    var tope = pos + (malo ? VENTANA_MALA : VENTANA);
    for (var i = actual; i < L.segs.length && L.segs[i].ini <= tope; i++) {
      var s = L.segs[i];
      if (L.hechos[i] || L.pedidos[i] || s.ini + s.d < pos + MARGEN) continue;
      if (L.enVuelo >= (malo ? PARALELO_MALO : PARALELO)) break;
      lanzar(L, i);
    }
  }

  function iniciar(url, segs) {
    var L = { segs: segs, hechos: {}, pedidos: {}, fallos: {}, enVuelo: 0, n: 0, bajados: 0, reloj: 0, listo: false };
    listas[url] = L;
    hls = true;
    LA = L;
    G.arranco = false;
    try {
      servidor = new URL(segs[0].u).hostname;
    } catch (e) {
      servidor = location.hostname;
    }
    avisar("lista de " + segs.length + " trozos en " + servidor + ": precargando " + VENTANA + " s por delante");
    L.reloj = setInterval(function () {
      paso(L);
    }, 1500);
    sondear(L);
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

  // Aviso en pantalla mientras el video espera: un punto que palpita arriba a la derecha (con el nombre del
  // servidor sólo si da problemas). Va dentro del elemento a pantalla completa si lo hay.
  var panel = null;
  var rotulo = null;
  function mostrar(texto) {
    var host = document.fullscreenElement;
    if (!host || host.tagName === "VIDEO") host = document.body || document.documentElement;
    if (!panel) {
      var css = document.createElement("style");
      css.textContent = "@keyframes antena-pulso{0%,100%{transform:scale(.75);opacity:.45}50%{transform:scale(1.15);opacity:1}}";
      (document.head || host).appendChild(css);
      panel = document.createElement("div");
      panel.style.cssText =
        "position:fixed;top:14px;right:14px;z-index:2147483647;display:flex;align-items:center;gap:8px;" +
        "pointer-events:none;font-family:sans-serif";
      var punto = document.createElement("div");
      punto.style.cssText =
        "width:18px;height:18px;border-radius:50%;background:#ffb020;" +
        "box-shadow:0 0 10px 2px rgba(255,176,32,.7);animation:antena-pulso 1s ease-in-out infinite";
      rotulo = document.createElement("div");
      rotulo.style.cssText = "font-size:12px;color:#fff;background:rgba(0,0,0,.6);padding:3px 7px;border-radius:8px";
      panel.appendChild(rotulo);
      panel.appendChild(punto);
    }
    if (panel.parentNode !== host) host.appendChild(panel);
    rotulo.textContent = texto;
    rotulo.style.display = texto ? "block" : "none";
  }
  function ocultar() {
    if (panel && panel.parentNode) panel.parentNode.removeChild(panel);
  }

  function soltar(v, por) {
    var dur = Date.now() - G.desde;
    G.on = false;
    G.arranco = true;
    ocultar();
    // Si pausar no hizo crecer el colchón, la pausa no sirve: se deja de pausar un rato para no empeorar.
    if ((por === "tiempo máximo" || por === "sin avance") && adelante(v) < 8) {
      G.sinPausaHasta = Date.now() + 300000;
      marcarMalo("pausar no llena el colchón; se deja de pausar 5 min");
    } else if (por === "tiempo máximo") marcarMalo("pausa larga sin llenar el colchón");
    else if (por === "colchón listo" && dur > 8000) marcarMalo("tardó " + Math.round(dur / 1000) + " s en juntar el colchón");
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
      if (G.sinPausaHasta && ahora < G.sinPausaHasta) return;
      var obj = (G.arranco ? COLCHON_CORTE : COLCHON_INICIO) * (servidorMalo() ? 1.5 : 1);
      if (!(ad < 1.5 || (!G.arranco && ad < obj))) return;
      G.on = true;
      G.desde = ahora;
      G.mejor = ad;
      G.tMejor = ahora;
      G.obj = Math.min(obj, resta - 1);
      G.motivo = G.arranco ? "conexión inestable" : "arranque";
      if (G.arranco) marcarMalo("se quedó sin datos");
      v.pause();
      avisar("pausa para juntar " + Math.round(G.obj) + " s de video (" + G.motivo + ") en el minuto " + Math.round(v.currentTime / 6) / 10);
    } else {
      if (!v.paused) v.pause();
      if (ad > G.mejor + 0.5) {
        G.mejor = ad;
        G.tMejor = ahora;
      }
      var meseta = ad >= 6 && ahora - G.tMejor > 6000; // el reproductor ya no guarda más
      var sinAvance = ad < 6 && ahora - G.tMejor > 15000; // 15 s pausado y el colchón no crece
      if (ad >= G.obj || meseta || sinAvance || ahora - G.desde > (servidorMalo() ? 60000 : 45000)) {
        soltar(v, ad >= G.obj ? "colchón listo" : meseta ? "el reproductor no guarda más" : sinAvance ? "sin avance" : "tiempo máximo");
        return;
      }
    }
    mostrar(servidorMalo() ? servidor : "");
  }
  setInterval(vigilarColchon, 500);

  // Estado para el monitor de la app: colchón del reproductor y video ya guardado en disco por delante.
  function enviarEstado() {
    var v = document.querySelector("video");
    if (!hls || !LA || !v) return;
    var pos = v.currentTime;
    var cubierto = pos;
    for (var i = 0; i < LA.segs.length; i++) {
      var fin = LA.segs[i].ini + LA.segs[i].d;
      if (fin <= pos) continue;
      if (LA.hechos[i] || fin < pos + MARGEN) cubierto = fin;
      else break;
    }
    try {
      browser.runtime.sendNativeMessage("antena", {
        tag: "precargaestado",
        host: servidor,
        colchon: Math.round(adelante(v) * 10) / 10,
        cache: Math.max(0, Math.round((cubierto - pos) * 10) / 10),
        malo: servidorMalo(),
        pausa: G.on,
      });
    } catch (e) {
      /* canal no listo */
    }
  }
  setInterval(enviarEstado, 1000);

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
