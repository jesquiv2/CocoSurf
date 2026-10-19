// Extensión interna de Antena · content script.
// 1) Informa la posición de scroll de la página: es la señal que usa la
//    barra superior para plegarse al bajar y desplegarse al subir.
// 2) Gestiona «mantener OK» con el mando: Gecko no expone evaluate(), así que
//    el script cachea el enlace/imagen señalados (tags «foco» y «contexto»)
//    y emula el clic cuando la pulsación es corta. El menú contextual lo
//    abre la app (MainActivity) al mantener OK >= 400 ms, usando ese caché.
(function () {
  if (window.__antenaExt) return;
  window.__antenaExt = true;

  var holdTarget = null;
  var holdStart = 0;

  function enviar(tag, extra) {
    var msg = { tag: tag };
    if (extra) {
      if (extra.y !== undefined) msg.y = extra.y;
      if (extra.title !== undefined) msg.title = extra.title;
      if (extra.url !== undefined) msg.url = extra.url;
      if (extra.link !== undefined) msg.link = extra.link || "";
      if (extra.img !== undefined) msg.img = extra.img || "";
    }
    try {
      browser.runtime.sendNativeMessage("antena", msg);
    } catch (e) {
      /* el canal aún no está listo */
    }
  }

  function informar(tag) {
    enviar(tag, {
      y: Math.round(window.scrollY),
      title: document.title,
      url: location.href,
    });
  }

  informar("carga");
  window.addEventListener(
    "scroll",
    function () {
      informar("scroll");
    },
    { passive: true },
  );

  function esEditable(el) {
    if (!el) return false;
    var tag = el.tagName;
    return (
      tag === "INPUT" ||
      tag === "TEXTAREA" ||
      tag === "SELECT" ||
      el.isContentEditable === true
    );
  }

  /** Elemento accionable (enlace, botón) o nulo si es la página vacía. */
  function resolvable(el) {
    if (!el || el === document.body || el === document.documentElement) return null;
    if (el.closest) {
      var a = el.closest("a[href], button, [role='button'], input[type='submit']");
      if (a) return a;
    }
    return el;
  }

  /** Par (enlace, imagen) del elemento, para el menú contextual. */
  function resolver(el) {
    var target = resolvable(el);
    if (!target) return { link: "", img: "" };
    var link = "";
    var img = "";
    if (target.closest) {
      var a = target.closest("a[href]");
      if (a) link = a.href;
    }
    if (target.tagName === "IMG") img = target.src;
    else if (target.querySelector) {
      var im = target.querySelector("img");
      if (im) img = im.src;
    }
    return { link: link, img: img };
  }

  // Elemento con foco → caché (tag «foco»): se refresca al navegar con el D-pad.
  document.addEventListener(
    "focusin",
    function () {
      var info = resolver(document.activeElement);
      enviar("foco", info);
    },
    true,
  );

  // Pulsación de OK/Enter: se cancela la activación nativa para poder
  // distinguir clic corto de mantener OK (la app abre el menú a los 400 ms).
  // Sólo sobre elementos accionables: se conservan la escritura en
  // formularios y el comportamiento nativo de la página vacía.
  document.addEventListener(
    "keydown",
    function (e) {
      if (e.key !== "Enter" && e.key !== " " && e.key !== "Spacebar") return;
      if (e.repeat) return;
      if (esEditable(e.target)) return;
      var target = resolvable(e.target);
      if (!target) return; // sin foco accionable: gestión nativa
      e.preventDefault();
      holdTarget = target;
      holdStart = Date.now();
      // Caché fresco del elemento: si la app monta el menú a los 400 ms,
      // ya sabe qué enlace/imagen señala el mando.
      enviar("contexto", resolver(target));
    },
    true,
  );

  document.addEventListener(
    "keyup",
    function (e) {
      if (e.key !== "Enter" && e.key !== " " && e.key !== "Spacebar") return;
      var el = holdTarget;
      holdTarget = null;
      if (!el) return;
      if (esEditable(e.target)) return;
      e.preventDefault();
      // Soltar antes del umbral del menú (400 ms) = clic normal.
      // A los 400 ms la app consume el evento y abre el menú; si el keyup
      // llega igual (sin repeat del mando), tampoco se hace clic.
      if (Date.now() - holdStart < 400 && typeof el.click === "function") {
        el.click();
      }
    },
    true,
  );
})();
