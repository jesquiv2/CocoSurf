// Extensión interna de CocoSurf · lista de dominios de anuncios.
// Bloquea por red las peticiones a dominios de publicidad, rastreo y estafas de la lista HaGeZi «Pro Mini»
// (la versión pensada para aparatos con poca memoria). Funciona con cualquier bloqueador que elija el
// usuario (o con ninguno): no depende de uBlock. La lista se baja sola, se guarda en el aparato y se
// renueva cada día. La app muestra su estado en Configuración (mensajes con tag «lista»).
(function () {
  var NOMBRE = "HaGeZi Pro Mini";
  var URLS = [
    "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/pro.mini.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.mini.txt",
  ];
  var CADA_MS = 24 * 3600 * 1000; // renovar si la copia tiene más de un día
  var REVISAR_MS = 3 * 3600 * 1000; // cada cuánto se mira si toca renovar

  var dominios = new Set();
  var estado = "cargando"; // cargando | lista | error
  var detalle = "";
  var fecha = 0; // cuándo se bajó la copia que está en uso
  var bloqueadas = 0;
  var avisadas = -1;

  function avisar(extra) {
    var m = { tag: "lista", nombre: NOMBRE, estado: estado, detalle: detalle, total: dominios.size, fecha: fecha, bloqueadas: bloqueadas };
    if (extra) for (var k in extra) m[k] = extra[k];
    avisadas = bloqueadas;
    try {
      browser.runtime.sendNativeMessage("cocosurf", m);
    } catch (e) {
      /* el canal aún no está listo */
    }
  }

  // Líneas «||dominio^» → conjunto de dominios. Sin partir el texto en un arreglo gigante (memoria).
  function parsear(texto) {
    var s = new Set();
    var n = texto.length;
    var i = 0;
    while (i < n) {
      var j = texto.indexOf("\n", i);
      if (j < 0) j = n;
      if (texto.charCodeAt(i) === 124 && texto.charCodeAt(i + 1) === 124) {
        var fin = j;
        if (texto.charCodeAt(fin - 1) === 13) fin--;
        if (texto.charCodeAt(fin - 1) === 94) {
          var d = texto.slice(i + 2, fin - 1);
          if (d.length > 3 && !/[\/*$|^:]/.test(d)) s.add(d);
        }
      }
      i = j + 1;
    }
    return s;
  }

  function coincide(host) {
    var h = host;
    for (;;) {
      if (dominios.has(h)) return true;
      var p = h.indexOf(".");
      if (p < 0) return false;
      h = h.slice(p + 1);
    }
  }

  function dominioDe(url) {
    var i = url.indexOf("://");
    if (i < 0) return "";
    var j = i + 3;
    var f = j;
    while (f < url.length) {
      var c = url.charCodeAt(f);
      if (c === 47 || c === 58 || c === 63 || c === 35) break; // / : ? #
      f++;
    }
    return url.slice(j, f).toLowerCase();
  }

  function filtrar(d) {
    if (dominios.size === 0 || d.type === "main_frame") return;
    var host = dominioDe(d.url);
    if (!host) return;
    // Lo propio del sitio que se visita no se toca (así no se rompe la página).
    var doc = dominioDe(d.documentUrl || d.originUrl || "");
    if (doc && (host === doc || host.endsWith("." + doc) || doc.endsWith("." + host))) return;
    if (coincide(host)) {
      bloqueadas++;
      return { cancel: true };
    }
  }

  browser.webRequest.onBeforeRequest.addListener(filtrar, { urls: ["http://*/*", "https://*/*"] }, ["blocking"]);

  async function bajar() {
    var ultimo = "";
    for (var k = 0; k < URLS.length; k++) {
      try {
        var ctl = new AbortController();
        var t = setTimeout(function () { ctl.abort(); }, 180000);
        var r = await fetch(URLS[k], { cache: "no-store", signal: ctl.signal });
        clearTimeout(t);
        if (!r.ok) throw new Error("HTTP " + r.status);
        return await r.text();
      } catch (e) {
        ultimo = String((e && e.message) || e);
      }
    }
    throw new Error(ultimo || "sin conexión");
  }

  async function renovar() {
    try {
      var texto = await bajar();
      var nuevo = parsear(texto);
      texto = null;
      if (nuevo.size < 1000) throw new Error("la lista bajó incompleta (" + nuevo.size + " dominios)");
      var primera = dominios.size === 0;
      dominios = nuevo;
      fecha = Date.now();
      estado = "lista";
      detalle = "";
      await browser.storage.local.set({ lista: Array.from(nuevo).join("\n"), listaFecha: fecha });
      avisar({ nueva: primera });
    } catch (e) {
      // Si ya había una copia, sigue en uso; sólo se anota el fallo.
      if (dominios.size === 0) estado = "error";
      detalle = String((e && e.message) || e);
      avisar();
    }
  }

  async function iniciar() {
    try {
      var g = await browser.storage.local.get(["lista", "listaFecha"]);
      if (g.lista) {
        var s = new Set();
        var partes = g.lista.split("\n");
        for (var i = 0; i < partes.length; i++) if (partes[i]) s.add(partes[i]);
        dominios = s;
        fecha = g.listaFecha || 0;
        estado = "lista";
        avisar();
      }
    } catch (e) {
      detalle = String((e && e.message) || e);
    }
    if (dominios.size === 0 || Date.now() - fecha > CADA_MS) await renovar();
    setInterval(function () {
      if (Date.now() - fecha > CADA_MS) renovar();
    }, REVISAR_MS);
    // El contador de bloqueos llega a la app de vez en cuando (sólo si cambió).
    setInterval(function () {
      if (bloqueadas !== avisadas) avisar();
    }, 30000);
  }

  iniciar();
})();
