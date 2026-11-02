(function () {
  'use strict';
  var g = function (i) { return document.getElementById(i); };
  var NS = 'http://www.w3.org/2000/svg';

  // ── Utilidades de DOM ──────────────────────────────────────────────
  function h(t, a, c) {
    var e = document.createElement(t);
    for (var k in a) {
      if (k === 'class') e.className = a[k];
      else if (k.slice(0, 2) === 'on') e[k] = a[k];
      else e.setAttribute(k, a[k]);
    }
    (c || []).forEach(function (x) { e.appendChild(typeof x === 'string' ? document.createTextNode(x) : x); });
    return e;
  }
  var P = {
    folder: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z',
    file: 'M6 3h8l5 5v13H6z M14 3v5h5',
    txt: 'M6 3h8l5 5v13H6z M14 3v5h5 M9 13h6 M9 17h6',
    pdf: 'M6 3h8l5 5v13H6z M14 3v5h5 M9 16h6',
    img: 'M4 5h16v14H4z M4 16l5-5 4 4 3-3 4 4 M9 9.5h.01',
    vid: 'M4 6h12v12H4z M16 10l5-3v10l-5-3',
    aud: 'M9 18V6l10-2v12 M9 18a3 3 0 1 1-3-3 M19 16a3 3 0 1 1-3-3',
    arc: 'M5 3h14v18H5z M12 3v8 M10 7h4 M10 14h4v3h-4z',
    apk: 'M6 9h12v9a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1z M8 9a4 4 0 0 1 8 0 M10 5.5L9 3.5 M14 5.5l1-2',
    down: 'M12 4v11 M7 11l5 5 5-5 M5 20h14',
    edit: 'M4 20h4L19 9l-4-4L4 16z M13 7l4 4',
    trash: 'M5 7h14 M10 7V4h4v3 M7 7l1 13h8l1-13 M10 11v5 M14 11v5',
    up: 'M12 20V9 M7 13l5-5 5 5 M5 4h14',
    newf: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M12 10v6 M9 13h6',
    upf: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M12 16v-6 M9.5 12l2.5-2.5 2.5 2.5',
    home: 'M4 11l8-7 8 7 M6 10v10h12V10',
    search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z M20 20l-4-4',
    x: 'M6 6l12 12 M18 6L6 18',
    left: 'M15 5l-7 7 7 7',
    right: 'M9 5l7 7-7 7'
  };
  function ico(k, cl) {
    var s = document.createElementNS(NS, 'svg');
    s.setAttribute('viewBox', '0 0 24 24');
    s.setAttribute('aria-hidden', 'true');
    if (cl) s.setAttribute('class', cl);
    var p = document.createElementNS(NS, 'path');
    p.setAttribute('d', P[k]);
    s.appendChild(p);
    return s;
  }
  function boton(cl, icono, texto, fn, etiqueta) {
    var b = h('button', { class: cl, type: 'button', onclick: fn });
    if (icono) b.appendChild(ico(icono));
    if (texto) b.appendChild(document.createTextNode(texto));
    if (etiqueta) { b.setAttribute('aria-label', etiqueta); b.title = etiqueta; }
    return b;
  }

  // ── Estado ─────────────────────────────────────────────────────────
  var EXT = {};
  function reg(k, l) { l.split(' ').forEach(function (x) { EXT[x] = k; }); }
  reg('img', 'jpg jpeg png gif webp bmp svg');
  reg('vid', 'mp4 mkv webm avi mov m4v ts mpg mpeg flv wmv 3gp m2ts');
  reg('aud', 'mp3 m4a ogg oga wav flac aac opus wma');
  reg('arc', 'zip rar 7z tar gz');
  reg('apk', 'apk');
  reg('txt', 'txt log json xml csv md srt ini');
  reg('pdf', 'pdf');
  function tipo(it) {
    if (it.d) return 'folder';
    var i = it.n.lastIndexOf('.');
    return i < 0 ? 'file' : (EXT[it.n.slice(i + 1).toLowerCase()] || 'file');
  }
  var VISTA = { img: 1, vid: 1, aud: 1, pdf: 1, txt: 1 };

  var S = { ruta: '', items: [], sel: {}, campo: 'n', asc: true, q: '' };

  function unir(n) { return S.ruta ? S.ruta + '/' + n : n; }
  function urlF(n, d) { return '/f?r=' + encodeURIComponent(unir(n)) + (d ? '&d=1' : ''); }
  function tam(b) {
    if (b >= 1073741824) return (b / 1073741824).toFixed(1) + ' GB';
    if (b >= 1048576) return (b / 1048576).toFixed(1) + ' MB';
    if (b >= 1024) return Math.round(b / 1024) + ' KB';
    return b + ' B';
  }
  function fecha(ms) {
    return new Date(ms).toLocaleString('es', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }
  var tt = 0;
  function toast(t) {
    var e = g('toast');
    e.textContent = t; e.hidden = false;
    clearTimeout(tt); tt = setTimeout(function () { e.hidden = true; }, 3500);
  }
  function post(url, cuerpo, cb) {
    var x = new XMLHttpRequest();
    x.open('POST', url);
    if (cuerpo != null) x.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
    x.onload = function () { if (x.status === 401) { location.href = '/'; return; } cb(x.status < 300, x.responseText); };
    x.onerror = function () { cb(false, ''); };
    x.send(cuerpo == null ? null : cuerpo);
  }

  // ── Carga y pintado ────────────────────────────────────────────────
  function cargar(ruta, push) {
    fetch('/api/lista?r=' + encodeURIComponent(ruta), { credentials: 'same-origin' })
      .then(function (r) {
        if (r.status === 401) { location.href = '/'; return null; }
        if (r.status === 404) { toast('Esa carpeta no existe.'); return null; }
        return r.json();
      })
      .then(function (d) {
        if (!d) return;
        var cambia = d.ruta !== S.ruta;
        S.ruta = d.ruta; S.items = d.items; S.sel = {};
        if (cambia) { S.q = ''; g('q').value = ''; }
        if (push) history.pushState(null, '', d.ruta ? '/?r=' + encodeURIComponent(d.ruta) : '/');
        g('sub').textContent = 'Compartiendo: ' + d.raiz;
        g('libre').textContent = d.libre >= 0 ? tam(d.libre) + ' libres en el televisor' : '';
        document.title = (d.ruta ? d.ruta.split('/').pop() : 'Archivos') + ' · Coco Surf';
        pintar();
      })
      .catch(function () { toast('No se pudo leer la carpeta.'); });
  }

  function migas() {
    var n = g('migas'); n.textContent = '';
    var partes = S.ruta ? S.ruta.split('/') : [];
    var inicio = h('a', { href: '/', class: partes.length ? '' : 'ult', 'aria-label': 'Inicio' }, [ico('home'), 'Inicio']);
    inicio.onclick = function (e) { e.preventDefault(); cargar('', true); };
    n.appendChild(inicio);
    var acum = '';
    partes.forEach(function (p, i) {
      acum = acum ? acum + '/' + p : p;
      var r = acum;
      n.appendChild(h('i', { 'aria-hidden': 'true' }, ['/']));
      var a = h('a', { href: '/?r=' + encodeURIComponent(r), class: i === partes.length - 1 ? 'ult' : '' }, [p]);
      a.onclick = function (e) { e.preventDefault(); cargar(r, true); };
      n.appendChild(a);
    });
  }

  function cabecera() {
    var tr = h('tr');
    var all = h('input', { type: 'checkbox', 'aria-label': 'Seleccionar todo' });
    all.id = 'todos';
    all.onchange = function () {
      S.sel = {};
      if (all.checked) visibles().forEach(function (i) { S.sel[i.n] = true; });
      pintar();
    };
    tr.appendChild(h('th', { class: 'c' }, [all]));
    function col(txt, campo, cl) {
      var activo = S.campo === campo;
      var b = h('button', { type: 'button' }, [txt + (activo ? (S.asc ? ' ↑' : ' ↓') : '')]);
      b.onclick = function () {
        if (S.campo === campo) S.asc = !S.asc; else { S.campo = campo; S.asc = campo === 'n'; }
        pintar();
      };
      var th = h('th', { class: cl || '', 'aria-sort': activo ? (S.asc ? 'ascending' : 'descending') : 'none' }, [b]);
      return th;
    }
    tr.appendChild(col('Nombre', 'n'));
    tr.appendChild(col('Tamaño', 't', 'num'));
    tr.appendChild(col('Modificado', 'm', 'fcol'));
    tr.appendChild(h('th', { class: 'acc' }));
    var cab = g('cab'); cab.textContent = ''; cab.appendChild(tr);
  }

  function visibles() {
    var lista = S.items.filter(function (i) { return !S.q || i.n.toLowerCase().indexOf(S.q) >= 0; });
    var c = S.campo, a = S.asc ? 1 : -1;
    lista.sort(function (x, y) {
      if (x.d !== y.d) return x.d ? -1 : 1;
      var r = c === 'n' ? x.n.localeCompare(y.n, 'es', { numeric: true, sensitivity: 'base' }) : (x[c] - y[c]);
      return r * a;
    });
    return lista;
  }

  function pintar() {
    migas();
    var lista = visibles();
    cabecera();
    var cuerpo = g('cuerpo'); cuerpo.textContent = '';
    lista.forEach(function (it) { cuerpo.appendChild(fila(it, lista)); });
    var vacio = g('vacio');
    g('tabla').hidden = lista.length === 0;
    vacio.hidden = lista.length !== 0;
    if (lista.length === 0) {
      vacio.textContent = '';
      vacio.appendChild(ico('folder'));
      vacio.appendChild(h('div', {}, [S.q ? 'Nada coincide con «' + S.q + '».' : 'Esta carpeta está vacía. Arrastra archivos aquí o pulsa «Subir archivos».']));
    }
    var nc = S.items.filter(function (i) { return i.d; }).length;
    var nf = S.items.length - nc, bytes = 0;
    S.items.forEach(function (i) { if (!i.d) bytes += i.t; });
    g('resumen').textContent = nc + (nc === 1 ? ' carpeta' : ' carpetas') + ' · ' + nf + (nf === 1 ? ' archivo' : ' archivos') + (nf ? ' · ' + tam(bytes) : '');
    var n = Object.keys(S.sel).length;
    g('selbar').hidden = n === 0;
    g('selN').textContent = n + (n === 1 ? ' elemento seleccionado' : ' elementos seleccionados');
    var todos = g('todos');
    if (todos) { todos.checked = n > 0 && n === lista.length; todos.indeterminate = n > 0 && n < lista.length; }
  }

  function fila(it, lista) {
    var k = tipo(it);
    var tr = h('tr', { class: S.sel[it.n] ? 'on' : '' });
    var cb = h('input', { type: 'checkbox', 'aria-label': 'Seleccionar ' + it.n });
    cb.checked = !!S.sel[it.n];
    cb.onchange = function () { if (cb.checked) S.sel[it.n] = true; else delete S.sel[it.n]; pintar(); };
    tr.appendChild(h('td', { class: 'c', style: 'text-align:center' }, [cb]));
    var enlace;
    if (it.d) {
      enlace = h('a', { href: '/?r=' + encodeURIComponent(unir(it.n)), title: it.n }, [it.n]);
      enlace.onclick = function (e) { e.preventDefault(); cargar(unir(it.n), true); };
    } else if (VISTA[k]) {
      enlace = h('button', { type: 'button', title: 'Vista previa de ' + it.n }, [it.n]);
      enlace.onclick = function () { vista(it, lista); };
    } else {
      enlace = h('a', { href: urlF(it.n, true), title: 'Descargar ' + it.n }, [it.n]);
    }
    tr.appendChild(h('td', {}, [h('div', { class: 'nom' }, [ico(k, 'k-' + k), enlace])]));
    tr.appendChild(h('td', { class: 'num' }, [it.d ? '—' : tam(it.t)]));
    tr.appendChild(h('td', { class: 'fecha fcol' }, [fecha(it.m)]));
    var acc = h('td', { class: 'acc' });
    if (it.d) acc.appendChild(boton('b icono', 'down', '', function () { location.href = zipUrl([it.n]); }, 'Descargar ' + it.n + ' como ZIP'));
    else acc.appendChild(boton('b icono', 'down', '', function () { location.href = urlF(it.n, true); }, 'Descargar ' + it.n));
    acc.appendChild(boton('b icono', 'edit', '', function () { renombrar(it); }, 'Renombrar ' + it.n));
    acc.appendChild(boton('b icono pel', 'trash', '', function () { borrar([it.n]); }, 'Borrar ' + it.n));
    tr.appendChild(acc);
    return tr;
  }

  // ── Diálogos ───────────────────────────────────────────────────────
  var foco = null;
  function dlg(o, cb) {
    foco = document.activeElement;
    var caja = g('caja'); caja.textContent = '';
    caja.appendChild(h('h2', {}, [o.t]));
    if (o.p) caja.appendChild(h('p', {}, [o.p]));
    var inp = null;
    if (o.v != null) { inp = h('input', { type: 'text', 'aria-label': o.t, autocomplete: 'off' }); inp.value = o.v; caja.appendChild(inp); }
    function cerrar() { g('ov').hidden = true; if (foco && foco.focus) foco.focus(); }
    var no = boton('b', '', 'Cancelar', cerrar);
    var ok = boton('b ' + (o.peligro ? 'peligro' : 'prim'), '', o.ok, function () {
      var v = inp ? inp.value.trim() : null;
      if (inp && !v) return;
      cerrar(); cb(v);
    });
    if (inp) inp.onkeydown = function (e) { if (e.key === 'Enter') ok.click(); };
    caja.appendChild(h('div', { class: 'fila' }, [no, ok]));
    g('ov').hidden = false;
    if (inp) { inp.focus(); inp.select(); } else ok.focus();
  }

  function borrar(nombres) {
    var uno = nombres.length === 1;
    dlg({ t: uno ? '¿Borrar «' + nombres[0] + '»?' : '¿Borrar ' + nombres.length + ' elementos?', p: 'No se puede deshacer. Si es una carpeta, se borra con todo lo que tiene dentro.', ok: 'Borrar', peligro: true }, function () {
      var pend = nombres.length, fallos = 0;
      nombres.forEach(function (n) {
        post('/borrar?r=' + encodeURIComponent(unir(n)), '', function (ok) {
          if (!ok) fallos++;
          if (--pend === 0) { toast(fallos ? 'No se pudieron borrar ' + fallos + '.' : 'Borrado.'); cargar(S.ruta, false); }
        });
      });
    });
  }
  function renombrar(it) {
    dlg({ t: 'Renombrar', p: it.n, v: it.n, ok: 'Renombrar' }, function (v) {
      if (v === it.n) return;
      post('/renombrar?r=' + encodeURIComponent(unir(it.n)), 'nombre=' + encodeURIComponent(v), function (ok) {
        if (ok) cargar(S.ruta, false); else toast('No se pudo renombrar (¿ya existe ese nombre?).');
      });
    });
  }
  function nuevaCarpeta() {
    dlg({ t: 'Nueva carpeta', v: '', ok: 'Crear' }, function (v) {
      post('/carpeta?r=' + encodeURIComponent(S.ruta), 'nombre=' + encodeURIComponent(v), function (ok) {
        if (ok) cargar(S.ruta, false); else toast('No se pudo crear (¿ya existe?).');
      });
    });
  }
  function zipUrl(nombres) {
    var u = '/zip?r=' + encodeURIComponent(S.ruta);
    nombres.forEach(function (n) { u += '&n=' + encodeURIComponent(n); });
    return u;
  }

  // ── Vista previa ───────────────────────────────────────────────────
  var vistaLista = [], vistaPos = 0;
  function vista(it, lista) {
    vistaLista = lista.filter(function (i) { return !i.d && VISTA[tipo(i)]; });
    vistaPos = vistaLista.indexOf(it);
    foco = document.activeElement;
    mostrar();
  }
  function mostrar() {
    var it = vistaLista[vistaPos], k = tipo(it), v = g('vista');
    v.textContent = ''; v.hidden = false;
    var cab = h('header', {}, [
      boton('b icono', 'left', '', function () { mover(-1); }, 'Anterior'),
      boton('b icono', 'right', '', function () { mover(1); }, 'Siguiente'),
      h('b', {}, [it.n]),
      h('a', { class: 'b', href: urlF(it.n, true) }, [ico('down'), 'Descargar']),
      boton('b icono', 'x', '', cerrarVista, 'Cerrar')
    ]);
    var cuerpo = h('div', { class: 'cuerpo' });
    var src = urlF(it.n);
    var el;
    if (k === 'img') el = h('img', { src: src, alt: it.n });
    else if (k === 'vid') el = h('video', { src: src, controls: '', autoplay: '' });
    else if (k === 'aud') el = h('audio', { src: src, controls: '', autoplay: '' });
    else if (k === 'pdf') el = h('iframe', { src: src, title: it.n });
    else {
      el = h('pre', {}, ['Cargando…']);
      fetch(src, { credentials: 'same-origin' }).then(function (r) { return r.text(); }).then(function (t) {
        el.textContent = t.length > 200000 ? t.slice(0, 200000) + '\n\n… (recortado)' : t;
      });
    }
    if (k === 'vid') el.onerror = function () { toast('El navegador no puede reproducir este formato; descárgalo.'); };
    cuerpo.appendChild(el);
    v.appendChild(cab); v.appendChild(cuerpo);
    v.querySelector('button').focus();
  }
  function mover(d) {
    vistaPos = (vistaPos + d + vistaLista.length) % vistaLista.length;
    mostrar();
  }
  function cerrarVista() {
    var v = g('vista'); v.hidden = true; v.textContent = '';
    if (foco && foco.focus) foco.focus();
  }

  // ── Subidas ────────────────────────────────────────────────────────
  var cola = [], activos = 0, total = 0, hechos = 0;
  function panelSubida(nombre) {
    g('subidas').hidden = false;
    var barra = h('div', {});
    var estado = h('span', {}, ['En cola']);
    var fila = h('div', { class: 'up' }, [
      h('div', { class: 'n' }, [h('span', { title: nombre }, [nombre]), estado]),
      h('div', { class: 'p' }, [barra])
    ]);
    g('subL').appendChild(fila);
    return {
      set: function (f) { barra.style.width = Math.round(f * 100) + '%'; estado.textContent = Math.round(f * 100) + '%'; },
      fin: function (ok) { fila.className = 'up ' + (ok ? 'ok' : 'mal'); barra.style.width = '100%'; estado.textContent = ok ? 'Listo' : 'Error'; }
    };
  }
  function encolar(lista) {
    if (!lista.length) return;
    lista.forEach(function (e) {
      total++;
      cola.push({ f: e.file, p: e.p || '', r: S.ruta, it: panelSubida((e.p ? e.p + '/' : '') + e.file.name) });
    });
    g('subT').textContent = 'Subiendo…';
    bombear();
  }
  function bombear() {
    while (activos < 2 && cola.length) { activos++; enviar(cola.shift()); }
  }
  function enviar(j) {
    var x = new XMLHttpRequest();
    x.open('POST', '/subir?r=' + encodeURIComponent(j.r) + '&p=' + encodeURIComponent(j.p));
    x.upload.onprogress = function (e) { if (e.lengthComputable) j.it.set(e.loaded / e.total); };
    function fin(ok) {
      activos--; hechos++;
      j.it.fin(ok);
      bombear();
      if (!activos && !cola.length) {
        g('subT').textContent = 'Subidas terminadas (' + hechos + ')';
        total = 0; hechos = 0;
        cargar(S.ruta, false);
      }
    }
    x.onload = function () { if (x.status === 401) { location.href = '/'; return; } fin(x.status < 300); };
    x.onerror = function () { fin(false); };
    var fd = new FormData();
    fd.append('archivo', j.f, j.f.name);
    x.send(fd);
  }

  // Recorre carpetas soltadas (arrastrar y soltar) y devuelve [{file, p}].
  function recorrer(entradas, listo) {
    var salida = [], pend = 1;
    function ok() { if (--pend === 0) listo(salida); }
    function visita(en, padre) {
      if (en.isFile) {
        pend++;
        en.file(function (f) { salida.push({ file: f, p: padre }); ok(); }, ok);
      } else if (en.isDirectory) {
        var ruta = padre ? padre + '/' + en.name : en.name;
        var rd = en.createReader();
        pend++;
        (function lee() {
          rd.readEntries(function (es) {
            if (!es.length) { ok(); return; }
            es.forEach(function (x) { visita(x, ruta); });
            lee();
          }, ok);
        })();
      }
    }
    entradas.forEach(function (e) { visita(e, ''); });
    ok();
  }

  var arrastres = 0;
  function conArchivos(e) { return e.dataTransfer && [].indexOf.call(e.dataTransfer.types || [], 'Files') >= 0; }
  document.addEventListener('dragenter', function (e) { if (conArchivos(e)) { arrastres++; g('soltar').hidden = false; } });
  document.addEventListener('dragleave', function (e) { if (conArchivos(e) && --arrastres <= 0) { arrastres = 0; g('soltar').hidden = true; } });
  document.addEventListener('dragover', function (e) { if (conArchivos(e)) e.preventDefault(); });
  document.addEventListener('drop', function (e) {
    if (!conArchivos(e)) return;
    e.preventDefault(); arrastres = 0; g('soltar').hidden = true;
    var dt = e.dataTransfer, ents = [];
    if (dt.items && dt.items.length && dt.items[0].webkitGetAsEntry) {
      for (var i = 0; i < dt.items.length; i++) { var en = dt.items[i].webkitGetAsEntry(); if (en) ents.push(en); }
      recorrer(ents, encolar);
    } else {
      encolar([].map.call(dt.files, function (f) { return { file: f, p: '' }; }));
    }
  });

  // ── Eventos fijos ──────────────────────────────────────────────────
  g('buscar').appendChild(ico('search'));
  var q = h('input', { type: 'search', id: 'q', placeholder: 'Buscar en esta carpeta', 'aria-label': 'Buscar en esta carpeta' });
  q.oninput = function () { S.q = q.value.trim().toLowerCase(); pintar(); };
  g('buscar').appendChild(q);
  function etiquetar(id, icono, texto) { var b = g(id); b.appendChild(ico(icono)); b.appendChild(document.createTextNode(texto)); }
  etiquetar('bNueva', 'newf', 'Nueva carpeta');
  etiquetar('bCarp', 'upf', 'Subir carpeta');
  etiquetar('bSubir', 'up', 'Subir archivos');
  g('subX').appendChild(ico('x'));
  g('bNueva').onclick = nuevaCarpeta;
  g('bSubir').onclick = function () { g('inArch').click(); };
  g('bCarp').onclick = function () { g('inCarp').click(); };
  g('inArch').onchange = function (e) {
    encolar([].map.call(e.target.files, function (f) { return { file: f, p: '' }; }));
    e.target.value = '';
  };
  g('inCarp').onchange = function (e) {
    encolar([].map.call(e.target.files, function (f) {
      var rel = f.webkitRelativePath || f.name, i = rel.lastIndexOf('/');
      return { file: f, p: i < 0 ? '' : rel.slice(0, i) };
    }));
    e.target.value = '';
  };
  g('subX').onclick = function () { g('subidas').hidden = true; g('subL').textContent = ''; };
  g('bSelNo').onclick = function () { S.sel = {}; pintar(); };
  g('bSelDel').onclick = function () { borrar(Object.keys(S.sel)); };
  g('bSelZip').onclick = function () { location.href = zipUrl(Object.keys(S.sel)); };

  document.addEventListener('keydown', function (e) {
    var enCampo = /^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName) && document.activeElement.type !== 'checkbox';
    if (!g('vista').hidden) {
      if (e.key === 'Escape') cerrarVista();
      else if (e.key === 'ArrowLeft') mover(-1);
      else if (e.key === 'ArrowRight') mover(1);
      return;
    }
    if (!g('ov').hidden) { if (e.key === 'Escape') g('ov').hidden = true; return; }
    if (enCampo) return;
    if (e.key === 'Delete' && Object.keys(S.sel).length) borrar(Object.keys(S.sel));
    else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'a') {
      e.preventDefault(); S.sel = {}; visibles().forEach(function (i) { S.sel[i.n] = true; }); pintar();
    } else if (e.key === 'Escape' && Object.keys(S.sel).length) { S.sel = {}; pintar(); }
    else if (e.key === '/') { e.preventDefault(); q.focus(); }
  });
  window.addEventListener('popstate', function () {
    cargar(new URLSearchParams(location.search).get('r') || '', false);
  });

  cargar(new URLSearchParams(location.search).get('r') || '', false);
})();
