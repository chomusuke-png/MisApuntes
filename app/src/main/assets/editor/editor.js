// Editor de texto enriquecido sobre contenteditable. Se comunica con Kotlin por el objeto Android.
(function () {
  'use strict';

  const page = document.getElementById('page');
  const MAX_HISTORY = 150;
  const CLEAR_COLOR = 'rgb(1, 2, 3)';

  let readOnly = document.body.classList.contains('readonly');
  let savedRange = null;
  let pendingFontPt = null;
  let history = [];
  let hIndex = -1;
  let typingTimer = null;
  let saveTimer = null;
  let reportQueued = false;
  let lastSaved = null;

  page.setAttribute('contenteditable', 'true');
  page.setAttribute('spellcheck', 'false');
  page.setAttribute('inputmode', readOnly ? 'none' : 'text');
  if (!page.firstChild) page.innerHTML = '<p><br></p>';
  document.execCommand('defaultParagraphSeparator', false, 'p');

  // ---------- Posición del cursor como desplazamiento de texto (sobrevive a reemplazar el HTML) ----------

  function textOffset(container, offset) {
    const range = document.createRange();
    range.setStart(page, 0);
    try { range.setEnd(container, offset); } catch (e) { return 0; }
    return range.toString().length;
  }

  function caret() {
    const sel = window.getSelection();
    if (!sel.rangeCount) return null;
    const r = sel.getRangeAt(0);
    if (!page.contains(r.commonAncestorContainer)) return null;
    return [textOffset(r.startContainer, r.startOffset), textOffset(r.endContainer, r.endOffset)];
  }

  function locate(offset) {
    const walker = document.createTreeWalker(page, NodeFilter.SHOW_TEXT);
    let node, pos = 0, last = null;
    while ((node = walker.nextNode())) {
      const len = node.nodeValue.length;
      if (pos + len >= offset) return [node, offset - pos];
      pos += len;
      last = node;
    }
    return last ? [last, last.nodeValue.length] : [page, 0];
  }

  function setCaret(c) {
    if (!c) return;
    const [sn, so] = locate(c[0]);
    const [en, eo] = locate(c[1]);
    const r = document.createRange();
    r.setStart(sn, so);
    r.setEnd(en, eo);
    const sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(r);
  }

  // ---------- Deshacer / rehacer: fotos del HTML ----------

  function snapshot() {
    clearTimeout(typingTimer);
    typingTimer = null;
    const html = page.innerHTML;
    if (hIndex >= 0 && history[hIndex].html === html) {
      history[hIndex].caret = caret();
      return;
    }
    history.splice(hIndex + 1);
    history.push({ html: html, caret: caret() });
    if (history.length > MAX_HISTORY) history.shift();
    hIndex = history.length - 1;
    queueReport();
  }

  /** Agrupa lo escrito en ráfagas, para no deshacer letra por letra. */
  function scheduleSnapshot() {
    clearTimeout(typingTimer);
    typingTimer = setTimeout(snapshot, 700);
  }

  function restore(i) {
    hIndex = i;
    page.innerHTML = history[i].html;
    setCaret(history[i].caret);
    scheduleSave();
    queueReport();
    queueNotes();
  }

  function undo() {
    if (typingTimer) snapshot();
    if (hIndex > 0) restore(hIndex - 1);
  }

  function redo() {
    if (hIndex < history.length - 1) restore(hIndex + 1);
  }

  // ---------- Guardado ----------

  function getHtml() {
    const copy = page.cloneNode(true);
    ['contenteditable', 'spellcheck', 'inputmode'].forEach(function (a) { copy.removeAttribute(a); });
    return copy.outerHTML.replace(/​/g, '');
  }

  function save() {
    clearTimeout(saveTimer);
    saveTimer = null;
    const html = getHtml();
    if (html === lastSaved) return;
    lastSaved = html;
    Android.save(html);
  }

  function scheduleSave() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(save, 800);
  }

  window.flushSave = function () {
    if (typingTimer) snapshot();
    save();
  };

  // ---------- Formato ----------

  function restoreRange() {
    page.focus({ preventScroll: true });
    if (!savedRange) return;
    const sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(savedRange);
  }

  function exec(name, value) {
    if (typingTimer) snapshot();
    restoreRange();
    document.execCommand('styleWithCSS', false, true);
    document.execCommand(name, false, value === undefined ? null : value);
  }

  function afterCommand() {
    fixFontSizes();
    snapshot();
    scheduleSave();
    queueReport();
    queueNotes();
  }

  function unwrap(el) {
    const parent = el.parentNode;
    while (el.firstChild) parent.insertBefore(el.firstChild, el);
    parent.removeChild(el);
  }

  /** execCommand solo conoce 7 tamaños: se usa el 7 como marca y se reemplaza por el tamaño real. */
  function fixFontSizes() {
    if (pendingFontPt == null) return;
    page.querySelectorAll('font[size="7"]').forEach(function (el) {
      const span = document.createElement('span');
      span.style.fontSize = pendingFontPt + 'pt';
      while (el.firstChild) span.appendChild(el.firstChild);
      el.replaceWith(span);
    });
    page.querySelectorAll('[style*="xxx-large"]').forEach(function (el) {
      el.style.fontSize = pendingFontPt + 'pt';
    });
  }

  /** Quita una propiedad de estilo aplicada con un color marcador (no hay comando "quitar color"). */
  function clearMarked(prop) {
    page.querySelectorAll('[style]').forEach(function (el) {
      if (el.style[prop] === CLEAR_COLOR) {
        el.style[prop] = '';
        if (!el.getAttribute('style') && el.tagName === 'SPAN') unwrap(el);
      }
    });
    page.querySelectorAll('font[color="#010203"]').forEach(unwrap);
  }

  // ---------- Estado para la barra de herramientas ----------

  function anchorElement() {
    const sel = window.getSelection();
    let node = sel.rangeCount ? sel.getRangeAt(0).startContainer : null;
    if (!node || !page.contains(node)) return null;
    if (node.nodeType === Node.TEXT_NODE) node = node.parentNode;
    return node;
  }

  function backgroundAt(el) {
    for (let n = el; n && n !== page; n = n.parentNode) {
      const bg = n.style && n.style.backgroundColor;
      if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') return bg;
    }
    return '';
  }

  function state(cmd) {
    try { return document.queryCommandState(cmd); } catch (e) { return false; }
  }

  function value(cmd) {
    try { return document.queryCommandValue(cmd) || ''; } catch (e) { return ''; }
  }

  function report() {
    reportQueued = false;
    const el = anchorElement();
    const sel = window.getSelection();
    const sizePx = el ? parseFloat(getComputedStyle(el).fontSize) : 16;
    const st = {
      bold: state('bold'),
      italic: state('italic'),
      underline: state('underline'),
      strike: state('strikeThrough'),
      block: (value('formatBlock') || 'p').toLowerCase(),
      font: value('fontName'),
      size: Math.round(sizePx * 0.75 * 2) / 2,
      color: value('foreColor'),
      back: el ? backgroundAt(el) : '',
      align: state('justifyCenter') ? 'center' : state('justifyRight') ? 'right' : state('justifyFull') ? 'justify' : 'left',
      ul: state('insertUnorderedList'),
      ol: state('insertOrderedList'),
      hasSelection: sel.rangeCount > 0 && !sel.isCollapsed && page.contains(sel.getRangeAt(0).commonAncestorContainer),
      canUndo: hIndex > 0 || typingTimer !== null,
      canRedo: hIndex < history.length - 1
    };
    Android.onState(JSON.stringify(st));
  }

  function queueReport() {
    if (reportQueued) return;
    reportQueued = true;
    requestAnimationFrame(report);
  }

  // ---------- Vista ----------

  function fit() {
    if (document.body.classList.contains('paged')) {
      const widthPt = parseFloat(page.dataset.w || '0');
      if (widthPt > 0) {
        const widthPx = widthPt * 4 / 3;
        document.body.style.zoom = Math.min(1, (window.innerWidth - 12) / widthPx);
        return;
      }
    }
    document.body.style.zoom = '';
  }

  // ---------- Eventos ----------

  page.addEventListener('beforeinput', function (e) {
    if (e.inputType === 'historyUndo') { e.preventDefault(); undo(); return; }
    if (e.inputType === 'historyRedo') { e.preventDefault(); redo(); return; }
    // En modo lectura solo se permite dar formato (resaltar), no escribir ni borrar
    if (readOnly && e.inputType.indexOf('format') !== 0) e.preventDefault();
  });

  page.addEventListener('input', function () {
    fixFontSizes();
    scheduleSnapshot();
    scheduleSave();
    queueReport();
    queueNotes();
  });

  page.addEventListener('click', function (e) {
    // Los enlaces no navegan dentro del editor
    if (e.target.closest && e.target.closest('a')) e.preventDefault();
  });

  document.addEventListener('selectionchange', function () {
    const sel = window.getSelection();
    if (sel.rangeCount && page.contains(sel.getRangeAt(0).commonAncestorContainer)) {
      savedRange = sel.getRangeAt(0).cloneRange();
    }
    queueReport();
  });

  window.addEventListener('resize', function () { fit(); queueNotes(); });

  // ---------- Notas adhesivas ----------
  // Cada nota se ancla a un párrafo (atributo data-anchor) y a un punto relativo dentro de él,
  // así sigue a su párrafo si el texto se reacomoda. docY es el respaldo si el párrafo se borra.

  const sheet = document.getElementById('sheet');
  const layer = document.getElementById('notes-layer');
  let notes = [];
  let placing = false;
  let notesQueued = false;

  function clamp01(v) { return Math.max(0, Math.min(1, v)); }

  /** Zoom real de la hoja (CSS zoom del modo página), medido en lugar de supuesto. */
  function sheetScale() {
    const w = sheet.offsetWidth;
    return w ? sheet.getBoundingClientRect().width / w : 1;
  }

  function toSheet(clientX, clientY) {
    const r = sheet.getBoundingClientRect();
    const s = sheetScale();
    return [(clientX - r.left) / s, (clientY - r.top) / s];
  }

  function blockAt(el) {
    if (!el || !page.contains(el) || el === page) return null;
    let b = el.closest('p,h1,h2,h3,h4,h5,h6,li,td,th,blockquote,pre,img');
    if (!b || !page.contains(b)) {
      b = el;
      while (b && b.parentNode !== page) b = b.parentNode;
    }
    return b && b !== page ? b : null;
  }

  function blockBox(b) {
    const r = b.getBoundingClientRect();
    const top = toSheet(r.left, r.top)[1];
    return { top: top, height: r.height / sheetScale() };
  }

  function anchorAt(clientX, clientY, hide) {
    if (hide) hide.style.visibility = 'hidden';
    const el = document.elementFromPoint(clientX, clientY);
    if (hide) hide.style.visibility = '';
    const p = toSheet(clientX, clientY);
    const w = sheet.offsetWidth || 1;
    const h = sheet.offsetHeight || 1;
    const b = blockAt(el);
    const anchor = { x: clamp01(p[0] / w), y: clamp01(p[1] / h), block: null, docY: clamp01(p[1] / h) };
    if (b) {
      if (!b.dataset.anchor) {
        b.dataset.anchor = 'n' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
        scheduleSave();
      }
      const box = blockBox(b);
      anchor.block = b.dataset.anchor;
      anchor.y = box.height ? clamp01((p[1] - box.top) / box.height) : 0;
    }
    return anchor;
  }

  function notePosition(n) {
    const w = sheet.offsetWidth;
    let top = n.docY * sheet.offsetHeight;
    if (n.block) {
      const b = page.querySelector('[data-anchor="' + CSS.escape(n.block) + '"]');
      if (b) {
        const box = blockBox(b);
        top = box.top + n.y * box.height;
      }
    }
    return [n.x * w, top];
  }

  function renderNotes() {
    notesQueued = false;
    const s = sheetScale();
    const existing = {};
    layer.querySelectorAll('.note-marker').forEach(function (m) { existing[m.dataset.id] = m; });
    notes.forEach(function (n) {
      let m = existing[n.id];
      delete existing[n.id];
      if (!m) {
        m = document.createElement('div');
        m.className = 'note-marker';
        m.dataset.id = n.id;
        m.appendChild(document.createElement('img'));
        layer.appendChild(m);
      }
      if (m.classList.contains('dragging')) return;
      m.style.background = n.color;
      m.firstChild.src = n.img;
      m.style.transform = 'translate(-50%, -50%) scale(' + (1 / s) + ')';
      const pos = notePosition(n);
      m.style.left = pos[0] + 'px';
      m.style.top = pos[1] + 'px';
    });
    Object.keys(existing).forEach(function (id) { existing[id].remove(); });
  }

  function queueNotes() {
    if (notesQueued) return;
    notesQueued = true;
    requestAnimationFrame(renderNotes);
  }

  // Tocar abre la nota; mantener presionado y arrastrar la mueve
  layer.addEventListener('pointerdown', function (e) {
    const m = e.target.closest('.note-marker');
    if (!m) return;
    e.preventDefault();
    e.stopPropagation();
    const id = m.dataset.id;
    const startX = e.clientX;
    const startY = e.clientY;
    let dragging = false;
    let moved = false;
    m.setPointerCapture(e.pointerId);
    const timer = setTimeout(function () {
      dragging = true;
      m.classList.add('dragging');
      if (navigator.vibrate) navigator.vibrate(25);
    }, 450);

    function onMove(ev) {
      if (dragging) {
        const p = toSheet(ev.clientX, ev.clientY);
        m.style.left = p[0] + 'px';
        m.style.top = p[1] + 'px';
      } else if (Math.hypot(ev.clientX - startX, ev.clientY - startY) > 10) {
        moved = true;
        clearTimeout(timer);
      }
    }

    function onUp(ev) {
      clearTimeout(timer);
      m.removeEventListener('pointermove', onMove);
      m.removeEventListener('pointerup', onUp);
      m.removeEventListener('pointercancel', onUp);
      if (dragging) {
        m.classList.remove('dragging');
        if (ev.type === 'pointerup') Android.moveNote(id, JSON.stringify(anchorAt(ev.clientX, ev.clientY, m)));
        queueNotes();
      } else if (!moved && ev.type === 'pointerup') {
        Android.openNote(id);
      }
    }

    m.addEventListener('pointermove', onMove);
    m.addEventListener('pointerup', onUp);
    m.addEventListener('pointercancel', onUp);
  });
  layer.addEventListener('contextmenu', function (e) { e.preventDefault(); });

  // Modo "añadir nota": el siguiente toque en la hoja elige el lugar
  sheet.addEventListener('click', function (e) {
    if (!placing) return;
    e.preventDefault();
    e.stopPropagation();
    window.notes.cancelPlacing();
    Android.placeNote(JSON.stringify(anchorAt(e.clientX, e.clientY)));
  }, true);

  window.notes = {
    set: function (list) { notes = list || []; queueNotes(); },
    startPlacing: function () {
      placing = true;
      page.setAttribute('inputmode', 'none');
      document.body.classList.add('placing');
    },
    cancelPlacing: function () {
      placing = false;
      page.setAttribute('inputmode', readOnly ? 'none' : 'text');
      document.body.classList.remove('placing');
    },
    focus: function (id) {
      renderNotes();
      const m = layer.querySelector('[data-id="' + id + '"]');
      if (!m) return;
      const r = m.getBoundingClientRect();
      window.scrollTo({ top: Math.max(0, window.scrollY + r.top - window.innerHeight / 3), behavior: 'smooth' });
      m.classList.remove('pulse');
      void m.offsetWidth;
      m.classList.add('pulse');
    }
  };

  // Posición de lectura, para volver al mismo lugar tras abrir una nota
  // y el máximo desplazable, también para la barra de desplazamiento rápido
  let scrollQueued = false;
  function reportScroll() {
    scrollQueued = false;
    const max = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
    Android.scrolled(Math.round(window.scrollY), Math.round(max));
  }
  function queueScroll() {
    if (scrollQueued) return;
    scrollQueued = true;
    requestAnimationFrame(reportScroll);
  }
  window.addEventListener('scroll', queueScroll);
  window.addEventListener('resize', queueScroll);
  page.addEventListener('input', queueScroll);
  function restoreScroll() {
    const y = parseInt(document.body.dataset.scroll || '0', 10);
    if (y > 0) window.scrollTo(0, y);
  }
  window.addEventListener('load', function () { restoreScroll(); queueNotes(); queueScroll(); });
  page.addEventListener('load', queueNotes, true); // imágenes que cambian la altura

  // ---------- API para Kotlin ----------

  window.editor = {
    cmd: function (name, val) { exec(name, val); afterCommand(); },
    block: function (tag) { exec('formatBlock', '<' + tag + '>'); afterCommand(); },
    fontSize: function (pt) { pendingFontPt = pt; exec('fontSize', '7'); afterCommand(); },
    color: function (c) {
      exec('foreColor', c || CLEAR_COLOR);
      if (!c) clearMarked('color');
      afterCommand();
    },
    highlight: function (c) {
      exec('hiliteColor', c || CLEAR_COLOR);
      if (!c) clearMarked('backgroundColor');
      afterCommand();
    },
    clear: function () {
      exec('removeFormat');
      exec('hiliteColor', CLEAR_COLOR);
      clearMarked('backgroundColor');
      afterCommand();
    },
    insertImage: function (src) {
      exec('insertHTML', '<img src="' + String(src).replace(/[&"<>]/g, '') + '"><br>');
      afterCommand();
    },
    undo: undo,
    redo: redo,
    setReadOnly: function (ro) {
      readOnly = ro;
      page.setAttribute('inputmode', ro ? 'none' : 'text');
      document.body.classList.toggle('readonly', ro);
      if (!ro) page.focus({ preventScroll: true });
    },
    setMode: function (mode) {
      document.body.classList.toggle('paged', mode === 'page');
      document.body.classList.toggle('mobile', mode !== 'page');
      fit();
      queueNotes();
    },
    scrollToFraction: function (f) {
      const max = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
      window.scrollTo(0, Math.round(max * Math.max(0, Math.min(1, f))));
    },
    setDark: function (dark) { document.body.classList.toggle('dark', dark); }
  };

  fit();
  snapshot();
  lastSaved = getHtml();
  queueReport();
})();
