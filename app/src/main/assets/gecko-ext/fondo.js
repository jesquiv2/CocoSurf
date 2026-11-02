// Extensión interna de Antena · fondo: redirige los pedidos de trozos de video de un servidor lento
// a uno rápido de la misma familia (lo decide precarga.js tras probarlos; ver «Servidores lentos»).
(function () {
  var desvios = {}; // servidor lento → servidor rápido
  var hasta = 0; // los desvíos caducan: el servidor lento puede recuperarse
  var escuchando = false;

  function redirigir(d) {
    if (Date.now() > hasta) return;
    var u = d.url;
    var i = u.indexOf("://");
    if (i < 0) return;
    var j = u.indexOf("/", i + 3);
    var a = desvios[u.slice(i + 3, j < 0 ? u.length : j)];
    if (!a) return;
    return { redirectUrl: u.slice(0, i + 3) + a + (j < 0 ? "" : u.slice(j)) };
  }

  browser.runtime.onMessage.addListener(function (m) {
    if (!m) return;
    if (m.limpiar) {
      desvios = {};
    } else if (m.desvios) {
      desvios = m.desvios;
      hasta = Date.now() + 1800000;
      // El filtro sólo se registra cuando hace falta: sin desvíos no se mira ningún pedido.
      if (!escuchando) {
        escuchando = true;
        browser.webRequest.onBeforeRequest.addListener(
          redirigir,
          { urls: ["<all_urls>"], types: ["xmlhttprequest", "media", "other"] },
          ["blocking"]
        );
      }
    }
  });
})();
