// Spike M5 · content script: prueba visual + informes por native messaging.
(function () {
  if (document.getElementById("antena-ext-banner")) return;
  var d = document.createElement("div");
  d.id = "antena-ext-banner";
  d.textContent = "EXTENSIÓN GECKO ACTIVA ✓";
  d.style.cssText =
    "position:fixed;top:0;left:0;right:0;background:#7FB08A;color:#000;" +
    "font:bold 22px sans-serif;text-align:center;padding:10px;" +
    "z-index:2147483647;";
  (document.body || document.documentElement).appendChild(d);

  function informar(tag) {
    try {
      browser.runtime.sendNativeMessage("antena", {
        tag: tag,
        y: Math.round(window.scrollY),
        ext: !!document.getElementById("antena-ext-banner"),
        title: document.title,
        url: location.href,
      });
    } catch (e) {
      /* el canal aún no está listo */
    }
  }

  informar("carga");
  window.addEventListener(
    "scroll",
    function () {
      informar("scroll");
    },
    { passive: true },
  );
})();
