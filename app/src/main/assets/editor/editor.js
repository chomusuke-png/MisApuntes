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
    const html = cleanInner();
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
    // La foto ya trae sus propias marcas de cambio: no se marca nada por reemplazarla
    if (observer) observer.takeRecords();
    setCaret(history[i].caret);
    scheduleSave();
    queueReport();
    queueNotes();
    queuePaginate(0);
  }

  /** HTML de la hoja sin los separadores de página (no son parte del documento). */
  function cleanInner() {
    if (!page.querySelector('.wgap')) return page.innerHTML;
    const copy = page.cloneNode(true);
    copy.querySelectorAll('.wgap').forEach(function (g) { g.remove(); });
    return copy.innerHTML;
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
    copy.querySelectorAll('.wgap').forEach(function (g) { g.remove(); });
    copy.querySelectorAll('img.wsel').forEach(function (img) {
      img.classList.remove('wsel');
      if (!img.className) img.removeAttribute('class');
    });
    return copy.outerHTML.replace(/​/g, '');
  }

  function save() {
    clearTimeout(saveTimer);
    saveTimer = null;
    // Las marcas de cambio tienen que quedar también en el HTML guardado
    if (observer) markChanges(observer.takeRecords());
    const html = getHtml();
    if (html === lastSaved) return;
    lastSaved = html;
    Android.save(html);
    sendModel();
  }

  // ---------- Modelo para guardar como Word ----------
  // Los bloques que vienen de Word llevan data-b (su ruta en el original), los trozos de texto
  // data-r (su run) y los objetos data-o. Lo que se modifica queda marcado con data-d: al guardar,
  // lo no marcado se copia tal cual del original y lo marcado se rehace desde lo que se ve.

  const fromWord = page.dataset.src === 'wc';
  const FONT_SIZES = [0, 8, 10, 12, 13.5, 18, 24, 36];

  function markDirty(node) {
    for (let n = node; n && n !== page; n = n.parentNode) {
      if (n.nodeType === 1 && n.hasAttribute('data-b') && !n.hasAttribute('data-d')) n.setAttribute('data-d', '1');
    }
  }

  function markChanges(records) {
    records.forEach(function (r) {
      if (r.type === 'attributes' && (r.attributeName === 'data-d' || r.attributeName === 'data-anchor')) return;
      // Marcar una imagen como seleccionada no cambia el documento
      if (r.type === 'attributes' && r.attributeName === 'class' && r.target.tagName === 'IMG') return;
      if (r.target === page) {
        r.addedNodes.forEach(markDirty);
        return;
      }
      markDirty(r.target);
    });
  }
  const observer = typeof MutationObserver === 'undefined' ? null : new MutationObserver(markChanges);
  if (observer) observer.observe(page, { subtree: true, childList: true, characterData: true, attributes: true });

  function hexColor(c) {
    if (!c || c === 'inherit' || c === 'initial') return null;
    if (c === 'transparent') return '';
    if (c[0] === '#') return c.length === 4 ? '#' + c[1] + c[1] + c[2] + c[2] + c[3] + c[3] : c.toUpperCase();
    const m = /rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?/.exec(c);
    if (!m) return null;
    if (m[4] !== undefined && parseFloat(m[4]) === 0) return '';
    return '#' + [m[1], m[2], m[3]].map(function (x) { return (+x).toString(16).padStart(2, '0'); }).join('').toUpperCase();
  }

  function sizePt(s) {
    const v = parseFloat(s);
    if (isNaN(v)) return null;
    if (/pt$/.test(s)) return v;
    if (/px$/.test(s)) return v * 0.75;
    return null;
  }

  /** Aspecto de un texto: lo declarado más cerca gana. withBlock: incluye el estilo del párrafo. */
  function look(node, block, withBlock) {
    const f = {};
    for (let n = node.parentNode; n && n !== page; n = n.parentNode) {
      if (n === block && !withBlock) break;
      if (n.nodeType !== 1) continue;
      const s = n.style, tag = n.tagName;
      if (f.b === undefined) {
        if (s.fontWeight) f.b = s.fontWeight === 'bold' || s.fontWeight === 'bolder' || parseInt(s.fontWeight, 10) >= 600;
        else if (tag === 'B' || tag === 'STRONG') f.b = true;
      }
      if (f.i === undefined) {
        if (s.fontStyle) f.i = s.fontStyle !== 'normal';
        else if (tag === 'I' || tag === 'EM') f.i = true;
      }
      const deco = s.textDecorationLine || s.textDecoration || '';
      if (f.u === undefined) {
        if (/underline/.test(deco) || tag === 'U') f.u = true;
        else if (/none/.test(deco)) f.u = false;
      }
      if (f.s === undefined) {
        if (/line-through/.test(deco) || tag === 'S' || tag === 'STRIKE' || tag === 'DEL') f.s = true;
        else if (/none/.test(deco)) f.s = false;
      }
      if (f.va === undefined) {
        if (tag === 'SUP' || s.verticalAlign === 'super') f.va = 'sup';
        else if (tag === 'SUB' || s.verticalAlign === 'sub') f.va = 'sub';
      }
      if (f.c === undefined) {
        let c = hexColor(s.color);
        if (c === null && tag === 'FONT') c = hexColor(n.getAttribute('color'));
        if (c !== null) f.c = c;
      }
      if (f.bg === undefined && n !== block) {
        const bg = hexColor(s.backgroundColor);
        if (bg !== null) f.bg = bg;
      }
      if (f.font === undefined) {
        const fam = s.fontFamily || (tag === 'FONT' ? n.getAttribute('face') : '');
        if (fam) f.font = fam.split(',')[0].replace(/['"]/g, '').trim();
      }
      if (f.sz === undefined) {
        let sz = s.fontSize ? sizePt(s.fontSize) : null;
        if (sz === null && tag === 'FONT' && n.getAttribute('size')) sz = FONT_SIZES[parseInt(n.getAttribute('size'), 10)] || null;
        if (sz !== null) f.sz = Math.round(sz * 2) / 2;
      }
      if (f.link === undefined && tag === 'A') f.link = n.getAttribute('href') || '';
    }
    if (withBlock && fromWord) {
      // Del párrafo de Word se conoce todo su aspecto: lo no declarado es "sin"
      if (f.b === undefined) f.b = false;
      if (f.i === undefined) f.i = false;
      if (f.u === undefined) f.u = false;
      if (f.s === undefined) f.s = false;
      if (f.va === undefined) f.va = '';
      if (f.c === undefined) f.c = '';
      if (f.bg === undefined) f.bg = '';
      if (f.link === undefined) f.link = '';
    }
    return f;
  }

  /** Párrafo (hijo directo de la hoja o de una celda) que contiene a node. */
  function blockOf(node) {
    for (let n = node; n && n !== page; n = n.parentNode) {
      const parent = n.parentNode;
      if (n.nodeType === 1 && parent && (parent === page || parent.tagName === 'TD')) return n;
    }
    return null;
  }

  function within(node, block, selector) {
    const el = node.nodeType === 1 ? node : node.parentNode;
    const found = el && el.closest(selector);
    return found && block.contains(found) ? found : null;
  }

  function segments(block, nestedLists) {
    // data-h: párrafo que cambió de estilo; su aspecto base lo da el estilo nuevo
    const withBlock = !fromWord || (block.hasAttribute('data-b') && !block.dataset.h);
    const segs = [];
    function push(seg) {
      const last = segs[segs.length - 1];
      if (seg.t !== undefined && last && last.t !== undefined && last.r === seg.r && last.hid === seg.hid &&
          JSON.stringify(last.f) === JSON.stringify(seg.f)) {
        last.t += seg.t;
      } else {
        segs.push(seg);
      }
    }
    (function walk(node) {
      for (let n = node.firstChild; n; n = n.nextSibling) {
        if (n.nodeType === 3) {
          if (!n.nodeValue) continue;
          const run = within(n, block, '[data-r]');
          const seg = { t: n.nodeValue, r: run ? run.dataset.r : null, f: look(n, block, withBlock) };
          // Estilo Código: letra de máquina de escribir si el texto no tiene otra
          if (block.tagName === 'PRE' && !seg.f.font) seg.f.font = 'Courier New';
          if (within(n, block, '.wh')) seg.hid = true;
          push(seg);
          continue;
        }
        if (n.nodeType !== 1) continue;
        const tag = n.tagName;
        if (n.classList.contains('wl') || n.dataset.x) continue;
        if (n.dataset.o) {
          // Imagen del original: puede venir con otro tamaño
          if (tag === 'IMG') push({ o: n.dataset.o, w: sizePt(n.style.width || '') || undefined, ih: sizePt(n.style.height || '') || undefined });
          else push({ o: n.dataset.o });
          continue;
        }
        if (n.dataset.pb) { push({ pb: parseInt(n.dataset.pb, 10) || 1 }); continue; }
        if (tag === 'BR') { push({ br: true }); continue; }
        if (tag === 'IMG') {
          const ratio = n.naturalWidth ? n.naturalHeight / n.naturalWidth : 0.75;
          let w = sizePt(n.style.width || '') || (n.naturalWidth ? n.naturalWidth * 0.75 : 300);
          w = Math.min(w, 450);
          push({ img: n.getAttribute('src'), w: w, ih: sizePt(n.style.height || '') || w * ratio });
          continue;
        }
        if ((tag === 'UL' || tag === 'OL') && nestedLists) { nestedLists.push(n); continue; }
        if (/^(P|DIV|H[1-6]|LI|BLOCKQUOTE)$/.test(tag) && segs.length) push({ br: true });
        walk(n);
      }
    })(block);
    // El <br> final solo mantiene abierta la línea vacía
    if (segs.length && segs[segs.length - 1].br) segs.pop();
    return segs;
  }

  function align(el) {
    return el.style.textAlign || (el.getAttribute('align') || null);
  }

  /** Interlineado (múltiplo de sencillo = 1.2 en el editor), espacios y sangría declarados. */
  function paraBox(el, withIndent) {
    const s = el.style, o = {};
    if (s.lineHeight && /^[\d.]+$/.test(s.lineHeight)) o.lh = Math.round(parseFloat(s.lineHeight) / 1.2 * 100) / 100;
    const sb = sizePt(s.marginTop || ''), sa = sizePt(s.marginBottom || ''), il = sizePt(s.marginLeft || '');
    if (sb !== null) o.sb = sb;
    if (sa !== null) o.sa = sa;
    if (withIndent && il !== null) o.il = il;
    return o;
  }

  function blockTag(tag) {
    if (/^H[1-6]$/.test(tag)) return tag.toLowerCase();
    if (tag === 'BLOCKQUOTE') return 'blockquote';
    if (tag === 'PRE') return 'pre';
    return 'p';
  }

  /** Párrafos (sin otros párrafos dentro) que toca la selección. */
  const LEAF_BLOCKS = 'p,h1,h2,h3,h4,h5,h6,li,blockquote,pre';
  function selectedBlocks() {
    const r = savedRange;
    if (!r) return [];
    const out = [];
    page.querySelectorAll(LEAF_BLOCKS).forEach(function (el) {
      if (el.closest('[data-x]') || el.querySelector(LEAF_BLOCKS)) return;
      if (r.intersectsNode(el)) out.push(el);
    });
    if (!out.length) {
      const b = blockOf(r.startContainer);
      if (b) out.push(b);
    }
    return out;
  }

  function formatBlocks(fn) {
    if (typingTimer) snapshot();
    restoreRange();
    selectedBlocks().forEach(fn);
    afterCommand();
  }

  let listCount = 0;

  function listModel(list, level, out, firstSrc) {
    const id = ++listCount;
    const kind = list.tagName.toLowerCase();
    let first = true;
    for (let li = list.firstElementChild; li; li = li.nextElementSibling) {
      if (li.tagName !== 'LI') continue;
      const nested = [];
      const p = paraBox(li, false);
      p.src = li.dataset.b || (first ? firstSrc || null : null);
      first = false;
      p.tag = 'li';
      p.list = kind;
      p.list_style = list.style.listStyleType || null;
      p.list_id = id;
      p.level = level;
      p.align = align(li);
      p.segs = segments(li, nested);
      out.push({ p: p });
      nested.forEach(function (n) { listModel(n, level + 1, out); });
    }
  }

  function blocksOf(container, counts, out) {
    for (let n = container.firstChild; n; n = n.nextSibling) {
      if (n.nodeType === 3) {
        if (n.nodeValue.trim()) out.push({ p: { tag: 'p', segs: [{ t: n.nodeValue, f: {} }] } });
        continue;
      }
      if (n.nodeType !== 1 || n.dataset.x || n.id === 'notes-layer') continue;
      const tag = n.tagName;
      const b = n.dataset.b;
      if (b && !n.hasAttribute('data-d') && counts[b] === 1) { out.push({ k: b }); continue; }
      if (tag === 'HR') {
        // Línea horizontal insertada; las demás <hr> son saltos de página de la versión anterior
        out.push({ p: n.classList.contains('wrule') ? { tag: 'p', rule: true, segs: [] } : { tag: 'p', segs: [{ pb: 1 }] } });
        continue;
      }
      if (tag === 'TABLE' && (n.dataset.tx || !b)) {
        // Tabla nueva o cambiada con las herramientas: va entera, fila por fila
        out.push({ t: gridModel(n, b, counts) });
        continue;
      }
      if (tag === 'TABLE') {
        // Solo cambió el texto: cada celda va a su posición en Word (data-c)
        const cells = [];
        let r = -1;
        Array.prototype.forEach.call(n.rows, function (tr) {
          // Las filas separadoras de la vista de página no son del documento
          if (tr.closest('table') !== n || tr.classList.contains('wgap')) return;
          r++;
          Array.prototype.forEach.call(tr.cells, function (td, c) {
            const inner = [];
            blocksOf(td, counts, inner);
            cells.push({ c: td.dataset.c || (r + ':' + c), blocks: inner });
          });
        });
        out.push({ t: { src: b || null, cells: cells } });
        continue;
      }
      if (tag === 'UL' || tag === 'OL') { listModel(n, 0, out); continue; }
      if (/^(DIV|SECTION|BLOCKQUOTE)$/.test(tag) && n.querySelector('p,h1,h2,h3,h4,h5,h6,div,table,ul,ol')) {
        blocksOf(n, counts, out);
        continue;
      }
      const p = paraBox(n, true);
      const nested = [];
      p.src = b || null;
      p.tag = blockTag(tag);
      p.align = align(n);
      p.segs = segments(n, nested);
      // Chrome a veces deja la lista nueva dentro del párrafo original: la lista hereda su origen
      if (p.segs.length || !nested.length) out.push({ p: p });
      nested.forEach(function (list, i) { listModel(list, 0, out, i === 0 && !p.segs.length ? p.src : null); });
    }
  }

  /**
   * Tabla como la guarda Word: cada fila con todas sus celdas, también las que continúan una
   * combinación vertical (m: 0 sola, 1 empieza, 2 continúa). c y tr dicen de qué celda y fila
   * del original sale el formato; bg/va van solo si se cambiaron (o si la celda es nueva).
   */
  function gridModel(n, src, counts) {
    const m = readTable(n);
    const rows = m.rows.map(function (tr, r) {
      const gb = parseInt(tr.dataset.gb || '0', 10) || 0;
      let last = -1;
      m.occ[r].forEach(function (c, g) { if (c) last = g; });
      const cells = [];
      let g = gb;
      while (g <= last) {
        const c = m.occ[r][g];
        if (!c) { cells.push({ s: 1, m: 0, blocks: [] }); g++; continue; }
        const top = c.r === r;
        const cell = { c: c.td.dataset.c || null, s: c.cs, m: c.rs > 1 ? (top ? 1 : 2) : 0, blocks: [] };
        if (top) blocksOf(c.td, counts, cell.blocks);
        if (!c.td.dataset.c || c.td.dataset.cs) {
          cell.set = true;
          cell.bg = hexColor(c.td.style.backgroundColor) || '';
          cell.va = c.td.style.verticalAlign || '';
        }
        cells.push(cell);
        g = c.g + c.cs;
      }
      return { tr: tr.dataset.tr ? parseInt(tr.dataset.tr, 10) : null, gb: gb, cells: cells };
    });
    return { src: src || null, rows: rows, grid: m.cols, bd: n.dataset.bd ? n.dataset.bd === '1' : null };
  }

  function model() {
    // Cambios recién hechos que el observador aún no procesó (se guardó en el mismo instante)
    if (observer) markChanges(observer.takeRecords());
    const counts = {};
    page.querySelectorAll('[data-b]').forEach(function (el) { counts[el.dataset.b] = (counts[el.dataset.b] || 0) + 1; });
    listCount = 0;
    const out = [];
    blocksOf(page, counts, out);
    const result = { blocks: out };
    if (parseFloat(page.dataset.w || '0') > 0) result.page = pageSetup();
    return JSON.stringify(result);
  }

  function sendModel() {
    if (typeof Android.model !== 'function') return;
    try { Android.model(model()); } catch (e) { /* el modelo es opcional: sin él se exporta lo último guardado */ }
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
    queuePaginate(50);
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

  // ---------- Mayúsculas / minúsculas ----------

  /** Cambia el texto seleccionado sin tocar su formato (cada trozo conserva su span). */
  function changeCase(mode) {
    restoreRange();
    const sel = window.getSelection();
    if (!sel.rangeCount || sel.isCollapsed) return;
    const range = sel.getRangeAt(0);
    if (!page.contains(range.commonAncestorContainer)) return;
    if (typingTimer) snapshot();

    const parts = [];
    const walker = document.createTreeWalker(page, NodeFilter.SHOW_TEXT);
    let n;
    while ((n = walker.nextNode())) {
      if (!range.intersectsNode(n)) continue;
      const start = n === range.startContainer ? range.startOffset : 0;
      const end = n === range.endContainer ? range.endOffset : n.nodeValue.length;
      if (end > start) parts.push({ node: n, start: start, end: end });
    }
    const text = parts.map(function (p) { return p.node.nodeValue.slice(p.start, p.end); }).join('');
    let out;
    switch (mode) {
      case 'upper': out = text.toLocaleUpperCase(); break;
      case 'lower': out = text.toLocaleLowerCase(); break;
      case 'title':
        out = text.toLocaleLowerCase().replace(/(^|[\s\-("¿¡«])(\p{L})/gu, function (m, a, b) { return a + b.toLocaleUpperCase(); });
        break;
      case 'sentence':
        out = text.toLocaleLowerCase().replace(/(^\s*|[.!?¿¡]\s+|\n\s*)(\p{L})/gu, function (m, a, b) { return a + b.toLocaleUpperCase(); });
        break;
      case 'toggle':
        out = Array.from(text).map(function (c) {
          const u = c.toLocaleUpperCase();
          return c === u ? c.toLocaleLowerCase() : u;
        }).join('');
        break;
      default: return;
    }
    // Algunas letras cambian de largo (ß → SS): entonces se cambia trozo por trozo
    const sameLength = out.length === text.length;
    let pos = 0;
    parts.forEach(function (p) {
      const v = p.node.nodeValue;
      const piece = v.slice(p.start, p.end);
      const len = piece.length;
      const repl = sameLength ? out.slice(pos, pos + len) : (mode === 'lower' ? piece.toLocaleLowerCase() : piece.toLocaleUpperCase());
      pos += len;
      p.node.nodeValue = v.slice(0, p.start) + repl + v.slice(p.end);
      p.end = p.start + repl.length;
    });
    if (parts.length) {
      const r = document.createRange();
      r.setStart(parts[0].node, parts[0].start);
      r.setEnd(parts[parts.length - 1].node, parts[parts.length - 1].end);
      sel.removeAllRanges();
      sel.addRange(r);
      savedRange = r.cloneRange();
    }
    afterCommand();
  }

  // ---------- Imagen seleccionada ----------

  let selectedImg = null;

  function selectImage(img) {
    if (selectedImg === img) return;
    if (selectedImg) selectedImg.classList.remove('wsel');
    selectedImg = img;
    if (img) {
      img.classList.add('wsel');
      const r = document.createRange();
      r.selectNode(img);
      const sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(r);
      savedRange = r.cloneRange();
    }
    queueReport();
  }

  /** Ancho útil de la hoja en puntos (sin márgenes). */
  function contentWidthPt() {
    const cs = getComputedStyle(page);
    const pad = parseFloat(cs.paddingLeft) + parseFloat(cs.paddingRight);
    const w = parseFloat(page.dataset.w || '0');
    if (w > 0 && document.body.classList.contains('paged')) {
      return w - (sizePt(page.style.paddingLeft || '') || 0) - (sizePt(page.style.paddingRight || '') || 0);
    }
    return Math.max(100, (page.clientWidth - pad) * 0.75);
  }

  function imageWidthPt(img) {
    return sizePt(img.style.width || '') || img.getBoundingClientRect().width * 0.75;
  }

  function imagePct(img) {
    return Math.max(5, Math.min(100, Math.round(imageWidthPt(img) / contentWidthPt() * 100)));
  }

  function imageHeightPt(img) {
    return sizePt(img.style.height || '') || img.getBoundingClientRect().height * 0.75;
  }

  /** Proporción original del archivo (alto / ancho). */
  function imageRatio(img) {
    if (img.naturalWidth) return img.naturalHeight / img.naturalWidth;
    const w = imageWidthPt(img);
    return w ? imageHeightPt(img) / w : 0.75;
  }

  /** Cambia el tamaño con la proporción original. */
  function setImageWidth(img, pt) {
    const w = Math.max(12, Math.round(pt));
    img.style.width = w + 'pt';
    img.style.height = Math.round(w * imageRatio(img)) + 'pt';
  }

  function imageCommand(fn) {
    const img = selectedImg;
    if (!img || !page.contains(img) || readOnly) return;
    if (typingTimer) snapshot();
    fn(img);
    afterCommand();
  }

  window.imageTools = {
    /** Ancho y alto libres, en puntos (la barra decide si van vinculados). */
    setSize: function (w, h) {
      imageCommand(function (img) {
        img.style.width = Math.max(12, Math.round(w)) + 'pt';
        img.style.height = Math.max(12, Math.round(h)) + 'pt';
      });
    },
    size: function (pct) {
      imageCommand(function (img) { setImageWidth(img, contentWidthPt() * Math.max(5, Math.min(100, pct)) / 100); });
    },
    align: function (where) {
      imageCommand(function (img) {
        const b = blockOf(img);
        if (b) b.style.textAlign = where;
      });
    },
    /** Gira el archivo (Kotlin escribe uno nuevo) y cambia ancho por alto. */
    rotate: function (degrees) {
      imageCommand(function (img) {
        if (typeof Android.rotateImage !== 'function') return;
        const name = Android.rotateImage(img.getAttribute('src') || '', degrees);
        if (!name) return;
        const w = imageWidthPt(img), h = sizePt(img.style.height || '') || w * imageRatio(img);
        img.removeAttribute('data-o');
        img.setAttribute('src', name);
        // El ancho nuevo es el alto anterior, sin pasarse de la hoja
        const nw = Math.min(h, contentWidthPt());
        img.style.width = Math.round(nw) + 'pt';
        img.style.height = Math.round(nw * (w / h)) + 'pt';
      });
    },
    /** Otra imagen en el mismo lugar y con el mismo ancho. */
    replace: function (name) {
      imageCommand(function (img) {
        const w = imageWidthPt(img);
        img.removeAttribute('data-o');
        img.onload = function () {
          img.onload = null;
          setImageWidth(img, w);
          scheduleSave();
          queueReport();
        };
        img.setAttribute('src', String(name).replace(/[&"<>]/g, ''));
      });
    },
    remove: function () {
      imageCommand(function (img) {
        selectImage(null);
        img.remove();
      });
    }
  };

  // ---------- Tabla con el cursor ----------
  // La tabla se lee como cuadrícula: cada celda es un rectángulo (fila r, columna g, alto rs,
  // ancho cs). Las herramientas cambian esos rectángulos y la tabla se vuelve a armar con las
  // mismas celdas, así el texto no se toca. Al guardar, la tabla marcada con data-tx se rehace
  // en Word desde lo que se ve; el formato de filas y celdas sale de su origen (data-tr, data-c).

  /** Filas propias de la tabla (sin las de tablas anidadas ni los separadores de página). */
  function ownRows(t) {
    return Array.prototype.filter.call(t.rows, function (tr) { return tr.closest('table') === t && !tr.classList.contains('wgap'); });
  }

  function readTable(t) {
    const rows = ownRows(t);
    const cells = [], occ = rows.map(function () { return []; });
    rows.forEach(function (tr, r) {
      let g = parseInt(tr.dataset.gb || '0', 10) || 0;
      Array.prototype.forEach.call(tr.cells, function (td) {
        while (occ[r][g]) g++;
        const c = { td: td, r: r, g: g, rs: Math.max(1, Math.min(td.rowSpan || 1, rows.length - r)), cs: Math.max(1, td.colSpan || 1) };
        for (let i = 0; i < c.rs; i++) for (let j = 0; j < c.cs; j++) occ[r + i][g + j] = c;
        cells.push(c);
        g += c.cs;
      });
    });
    let ncols = 0;
    occ.forEach(function (row) { ncols = Math.max(ncols, row.length); });
    return { t: t, rows: rows, cells: cells, occ: occ, ncols: ncols, cols: columnWidths(t, ncols) };
  }

  /** Anchos de las columnas de la cuadrícula en puntos (los de Word, o repartidos por igual). */
  function columnWidths(t, n) {
    const cg = Array.prototype.find.call(t.children, function (c) { return c.tagName === 'COLGROUP'; });
    const own = cg ? Array.prototype.map.call(cg.children, function (c) { return sizePt(c.style.width || '') || 0; }) : [];
    if (own.length === n && own.every(function (w) { return w > 0; })) return own;
    const total = sizePt(t.style.width || '') || (own.length ? own.reduce(function (a, b) { return a + b; }, 0) : 0) || contentWidthPt();
    return Array.from({ length: n }, function () { return total / Math.max(1, n); });
  }

  function slotAt(m, r, g) { return m.occ[r] ? m.occ[r][g] : undefined; }

  /** Vuelve a armar la tabla con los rectángulos de m (mismas celdas, nuevo orden y tamaño). */
  function writeTable(m) {
    const t = m.t;
    let body = t.tBodies[0];
    if (!body) { body = document.createElement('tbody'); t.appendChild(body); }
    ownRows(t).forEach(function (tr) { if (m.rows.indexOf(tr) < 0) tr.remove(); });
    m.rows.forEach(function (tr) {
      while (tr.cells.length) tr.removeChild(tr.cells[0]);
      body.appendChild(tr);
    });
    m.cells.sort(function (a, b) { return a.r - b.r || a.g - b.g; }).forEach(function (c) {
      if (c.rs > 1) c.td.rowSpan = c.rs; else c.td.removeAttribute('rowspan');
      if (c.cs > 1) c.td.colSpan = c.cs; else c.td.removeAttribute('colspan');
      m.rows[c.r].appendChild(c.td);
    });
    // Columnas: mismo ancho total de la tabla
    let cg = Array.prototype.find.call(t.children, function (c) { return c.tagName === 'COLGROUP'; });
    if (!cg) { cg = document.createElement('colgroup'); t.insertBefore(cg, t.firstChild); }
    cg.innerHTML = m.cols.map(function (w) { return '<col style="width:' + Math.round(w * 10) / 10 + 'pt">'; }).join('');
    t.dataset.tx = '1';
  }

  function emptyCell(like) {
    const td = document.createElement('td');
    td.innerHTML = '<p><br></p>';
    if (like) {
      if (like.dataset.c) td.dataset.c = like.dataset.c;
      ['border', 'borderTop', 'borderRight', 'borderBottom', 'borderLeft', 'backgroundColor', 'verticalAlign'].forEach(function (p) {
        if (like.style[p]) td.style[p] = like.style[p];
      });
    }
    return td;
  }

  function newRow(like) {
    const tr = document.createElement('tr');
    if (like && like.dataset.tr) tr.dataset.tr = like.dataset.tr;
    if (like && like.dataset.gb) tr.dataset.gb = like.dataset.gb;
    return tr;
  }

  function isEmptyCell(td) {
    return !td.textContent.replace(/[\s​]/g, '') && !td.querySelector('img,table,hr');
  }

  /** Celda del cursor y rectángulo de celdas que toca la selección. */
  function tableTarget() {
    const r = savedRange;
    if (!r || !page.contains(r.startContainer)) return null;
    const el = r.startContainer.nodeType === 1 ? r.startContainer : r.startContainer.parentNode;
    const td = el && el.closest('td,th');
    if (!td || !page.contains(td)) return null;
    const t = td.closest('table');
    const m = readTable(t);
    const first = m.cells.find(function (c) { return c.td === td; });
    if (!first) return null;
    const endEl = r.endContainer.nodeType === 1 ? r.endContainer : r.endContainer.parentNode;
    const endTd = endEl && endEl.closest('td,th');
    const last = endTd && endTd.closest('table') === t ? m.cells.find(function (c) { return c.td === endTd; }) || first : first;
    // Rectángulo que cubre ambas celdas, agrandado hasta no cortar ninguna celda combinada
    let r0 = Math.min(first.r, last.r), r1 = Math.max(first.r + first.rs, last.r + last.rs) - 1;
    let g0 = Math.min(first.g, last.g), g1 = Math.max(first.g + first.cs, last.g + last.cs) - 1;
    for (let changed = true; changed;) {
      changed = false;
      m.cells.forEach(function (c) {
        if (c.r > r1 || c.r + c.rs - 1 < r0 || c.g > g1 || c.g + c.cs - 1 < g0) return;
        if (c.r < r0) { r0 = c.r; changed = true; }
        if (c.r + c.rs - 1 > r1) { r1 = c.r + c.rs - 1; changed = true; }
        if (c.g < g0) { g0 = c.g; changed = true; }
        if (c.g + c.cs - 1 > g1) { g1 = c.g + c.cs - 1; changed = true; }
      });
    }
    const sel = m.cells.filter(function (c) { return c.r >= r0 && c.r + c.rs - 1 <= r1 && c.g >= g0 && c.g + c.cs - 1 <= g1; });
    return { m: m, cell: first, rect: { r0: r0, r1: r1, g0: g0, g1: g1 }, sel: sel };
  }

  function caretIn(td) {
    const r = document.createRange();
    const leaf = td.querySelector(LEAF_BLOCKS) || td;
    r.selectNodeContents(leaf);
    r.collapse(true);
    const s = window.getSelection();
    s.removeAllRanges();
    s.addRange(r);
    savedRange = r.cloneRange();
  }

  function tableCommand(fn) {
    if (readOnly) return;
    if (typingTimer) snapshot();
    const target = tableTarget();
    if (!target) return;
    // Sin separadores de página mientras se reacomoda (se vuelven a poner al final)
    if (observer) markChanges(observer.takeRecords());
    clearGaps();
    if (observer) observer.takeRecords();
    const focus = fn(target);
    if (focus && page.contains(focus)) caretIn(focus);
    afterCommand();
  }

  /** Ancho total igual: la columna nueva sale de las demás. */
  function keepTotal(m, total) {
    const now = m.cols.reduce(function (a, b) { return a + b; }, 0);
    if (now > 0 && total > 0) m.cols = m.cols.map(function (w) { return w * total / now; });
  }

  function insertRow(m, at, like) {
    const tr = newRow(m.rows[like]);
    let g = 0;
    while (g < m.ncols) {
      const c = slotAt(m, like, g);
      if (!c) { g++; continue; }
      // Una celda combinada que sigue por encima y por debajo crece; si no, celda nueva
      if (c.r < at && c.r + c.rs > at) c.rs++;
      else m.cells.push({ td: emptyCell(c.td), r: at, g: g, rs: 1, cs: c.cs, fresh: true });
      g = c.g + c.cs;
    }
    m.cells.forEach(function (c) { if (!c.fresh && c.r >= at) c.r++; delete c.fresh; });
    m.rows.splice(at, 0, tr);
    writeTable(m);
    const first = m.cells.find(function (c) { return c.r === at; });
    return first && first.td;
  }

  function deleteRow(m, row) {
    m.cells = m.cells.filter(function (c) {
      if (c.r === row && c.rs === 1) return false;
      if (c.r <= row && c.r + c.rs > row) c.rs--;
      else if (c.r > row) c.r--;
      return true;
    });
    m.rows.splice(row, 1);
  }

  function insertCol(m, at) {
    const total = m.cols.reduce(function (a, b) { return a + b; }, 0);
    const w = m.cols[Math.min(at, m.cols.length - 1)] || 72;
    m.cells.forEach(function (c) {
      if (c.g >= at) c.g++;
      else if (c.g + c.cs > at) c.cs++;
    });
    m.cols.splice(at, 0, w);
    m.rows.forEach(function (tr, r) {
      const covered = m.cells.some(function (c) { return c.r <= r && c.r + c.rs > r && c.g <= at && c.g + c.cs > at; });
      if (covered) return;
      const near = m.cells.find(function (c) { return c.r <= r && c.r + c.rs > r && (c.g + c.cs === at || c.g === at + 1); });
      m.cells.push({ td: emptyCell(near && near.td), r: r, g: at, rs: 1, cs: 1 });
    });
    keepTotal(m, total);
  }

  function deleteCol(m, col) {
    const total = m.cols.reduce(function (a, b) { return a + b; }, 0);
    m.cells = m.cells.filter(function (c) {
      if (c.g <= col && c.g + c.cs > col) {
        if (c.cs === 1) return false;
        c.cs--;
      } else if (c.g > col) c.g--;
      return true;
    });
    m.cols.splice(col, 1);
    keepTotal(m, total);
  }

  /** Corta la cuadrícula para que una celda (g, cs) se pueda partir en n anchos iguales. */
  function gridLines(m, g, cs, n) {
    const EPS = 0.5;
    let end = g + cs;
    const total = m.cols.slice(g, end).reduce(function (a, b) { return a + b; }, 0);
    const lines = [];
    for (let k = 1; k < n; k++) {
      const x = total * k / n;
      let acc = 0;
      for (let j = g; j < end; j++) {
        const w = m.cols[j];
        if (j > g && Math.abs(x - acc) <= EPS) { lines.push(j); break; }
        if (x < acc + w - EPS) {
          if (x > acc + EPS) {
            // Columna nueva dentro de j: las celdas que la cruzan se ensanchan
            m.cols.splice(j, 1, x - acc, w - (x - acc));
            m.cells.forEach(function (c) {
              if (c.g <= j && c.g + c.cs > j) c.cs++;
              else if (c.g > j) c.g++;
            });
            end++;
            lines.push(j + 1);
          }
          break;
        }
        acc += w;
      }
    }
    return lines;
  }

  window.tableTools = {
    insertRow: function (below) {
      tableCommand(function (x) {
        const at = below ? x.rect.r1 + 1 : x.rect.r0;
        return insertRow(x.m, at, below ? x.rect.r1 : x.rect.r0);
      });
    },
    insertCol: function (right) {
      tableCommand(function (x) {
        const at = right ? x.rect.g1 + 1 : x.rect.g0;
        insertCol(x.m, at);
        writeTable(x.m);
        const c = slotAt(readTable(x.m.t), x.cell.r, at);
        return c ? c.td : x.cell.td;
      });
    },
    deleteRow: function () {
      tableCommand(function (x) {
        const m = x.m;
        for (let r = x.rect.r1; r >= x.rect.r0; r--) deleteRow(m, r);
        if (!m.rows.length) return removeTable(m.t);
        writeTable(m);
        const row = Math.min(x.rect.r0, m.rows.length - 1);
        const c = m.cells.find(function (c) { return c.r <= row && c.r + c.rs > row; });
        return c && c.td;
      });
    },
    deleteCol: function () {
      tableCommand(function (x) {
        const m = x.m;
        for (let g = x.rect.g1; g >= x.rect.g0; g--) deleteCol(m, g);
        if (!m.cols.length || !m.cells.length) return removeTable(m.t);
        writeTable(m);
        const c = m.cells.find(function (c) { return c.r <= x.cell.r && c.r + c.rs > x.cell.r && c.g <= Math.min(x.rect.g0, m.cols.length - 1) && c.g + c.cs > Math.min(x.rect.g0, m.cols.length - 1); });
        return c && c.td;
      });
    },
    deleteTable: function () {
      tableCommand(function (x) { return removeTable(x.m.t); });
    },
    merge: function () {
      tableCommand(function (x) {
        if (x.sel.length < 2) return x.cell.td;
        const m = x.m, rc = x.rect;
        const keep = x.sel.find(function (c) { return c.r === rc.r0 && c.g === rc.g0; }) || x.sel[0];
        // El texto de las demás celdas pasa a la primera, en orden de lectura
        if (isEmptyCell(keep.td)) keep.td.innerHTML = '';
        x.sel.sort(function (a, b) { return a.r - b.r || a.g - b.g; }).forEach(function (c) {
          if (c === keep) return;
          if (!isEmptyCell(c.td)) while (c.td.firstChild) keep.td.appendChild(c.td.firstChild);
        });
        if (!keep.td.firstChild) keep.td.innerHTML = '<p><br></p>';
        m.cells = m.cells.filter(function (c) { return c === keep || x.sel.indexOf(c) < 0; });
        keep.r = rc.r0; keep.g = rc.g0;
        keep.rs = rc.r1 - rc.r0 + 1; keep.cs = rc.g1 - rc.g0 + 1;
        writeTable(m);
        return keep.td;
      });
    },
    /** Divide la celda del cursor en cols × rows celdas. false si el alto no se puede repartir. */
    split: function (cols, rows) {
      cols = Math.max(1, Math.min(20, cols | 0));
      rows = Math.max(1, Math.min(50, rows | 0));
      let ok = true;
      tableCommand(function (x) {
        const m = x.m, cell = x.cell;
        if (cell.rs > 1 && cell.rs % rows !== 0) { ok = false; return cell.td; }
        let parts = [cell];
        if (rows > 1) {
          if (cell.rs === 1) {
            // Filas nuevas debajo: el resto de la fila se estira sobre ellas
            const after = cell.r + 1;
            m.cells.forEach(function (c) {
              if (c.r > cell.r) c.r += rows - 1;
              else if (c !== cell && c.r + c.rs > cell.r) c.rs += rows - 1;
            });
            for (let k = 1; k < rows; k++) {
              m.rows.splice(after, 0, newRow(m.rows[cell.r]));
              const p = { td: emptyCell(cell.td), r: cell.r + k, g: cell.g, rs: 1, cs: cell.cs };
              m.cells.push(p);
              parts.push(p);
            }
          } else {
            const h = cell.rs / rows;
            cell.rs = h;
            for (let k = 1; k < rows; k++) {
              const p = { td: emptyCell(cell.td), r: cell.r + k * h, g: cell.g, rs: h, cs: cell.cs };
              m.cells.push(p);
              parts.push(p);
            }
          }
        }
        if (cols > 1) {
          const lines = gridLines(m, cell.g, cell.cs, cols);
          parts.forEach(function (p) {
            let a = p.g;
            const end = p.g + p.cs;
            lines.concat([end]).forEach(function (b, k) {
              if (b <= a) return;
              if (k === 0 || a === p.g) { p.cs = b - a; }
              else m.cells.push({ td: emptyCell(p.td), r: p.r, g: a, rs: p.rs, cs: b - a });
              a = b;
            });
          });
        }
        m.ncols = m.cols.length;
        writeTable(m);
        return cell.td;
      });
      return ok;
    },
    /** Color de fondo de las celdas tocadas ("" = sin color). */
    shade: function (color) {
      tableCommand(function (x) {
        x.sel.forEach(function (c) {
          c.td.style.backgroundColor = color || '';
          c.td.dataset.cs = '1';
        });
        x.m.t.dataset.tx = '1';
        return null;
      });
    },
    /** Texto arriba, al centro o abajo de la celda. */
    valign: function (where) {
      tableCommand(function (x) {
        x.sel.forEach(function (c) {
          c.td.style.verticalAlign = where === 'top' ? '' : where;
          c.td.dataset.cs = '1';
        });
        x.m.t.dataset.tx = '1';
        return null;
      });
    },
    borders: function (on) {
      tableCommand(function (x) {
        x.m.cells.forEach(function (c) {
          ['borderTop', 'borderRight', 'borderBottom', 'borderLeft'].forEach(function (p) { c.td.style[p] = ''; });
          c.td.style.border = on ? '0.5pt solid #000' : 'none';
        });
        x.m.t.dataset.bd = on ? '1' : '0';
        x.m.t.dataset.tx = '1';
        return null;
      });
    },
    /** Todas las columnas del mismo ancho. */
    distribute: function () {
      tableCommand(function (x) {
        const m = x.m, total = m.cols.reduce(function (a, b) { return a + b; }, 0);
        m.cols = m.cols.map(function () { return total / m.cols.length; });
        writeTable(m);
        return null;
      });
    }
  };

  function removeTable(t) {
    const next = t.nextElementSibling;
    const p = document.createElement('p');
    p.innerHTML = '<br>';
    // El cursor necesita dónde quedar
    if (!next || next.tagName === 'TABLE') t.parentNode.insertBefore(p, t);
    const focus = next && next.tagName !== 'TABLE' ? next : p;
    t.remove();
    return focus;
  }

  /** Estado de la tabla del cursor para la barra (null fuera de una tabla). */
  function tableState() {
    if (selectedImg) return null;
    const x = tableTarget();
    if (!x) return null;
    const td = x.cell.td, cs = getComputedStyle(td);
    return {
      rows: x.m.rows.length,
      cols: x.m.ncols,
      cells: x.sel.length,
      merged: x.cell.rs > 1 || x.cell.cs > 1,
      bg: hexColor(td.style.backgroundColor) || '',
      valign: cs.verticalAlign === 'middle' ? 'middle' : cs.verticalAlign === 'bottom' ? 'bottom' : 'top',
      borders: cs.borderTopStyle !== 'none' && parseFloat(cs.borderTopWidth) > 0
    };
  }

  // ---------- Buscar y reemplazar ----------

  let found = [];
  let foundIndex = -1;
  let foundQuery = '';
  const canHighlight = typeof CSS !== 'undefined' && CSS.highlights && typeof Highlight === 'function';

  /** Texto visible del documento (sin números de lista, texto oculto ni notas al pie). */
  function textNodes() {
    const out = [];
    const walker = document.createTreeWalker(page, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n) {
        const el = n.parentNode;
        return el && el.closest('.wl, .wh, [data-x]') ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT;
      }
    });
    let n;
    while ((n = walker.nextNode())) out.push(n);
    return out;
  }

  function fold(s) {
    return s.toLocaleLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  }

  /** Busca sin distinguir mayúsculas ni tildes; las coincidencias pueden cruzar varios trozos. */
  function search(query) {
    foundQuery = query || '';
    found = [];
    if (foundQuery) {
      const nodes = textNodes();
      let text = '';
      const starts = [];
      nodes.forEach(function (n) {
        starts.push(text.length);
        // Se pliega letra por letra para que las posiciones sigan coincidiendo con el original
        text += Array.from(n.nodeValue).map(function (c) { const f = fold(c); return f.length === c.length ? f : c; }).join('');
      });
      const q = fold(foundQuery);
      function locate(pos) {
        let lo = 0, hi = nodes.length - 1;
        while (lo < hi) {
          const mid = (lo + hi + 1) >> 1;
          if (starts[mid] <= pos) lo = mid; else hi = mid - 1;
        }
        return [nodes[lo], pos - starts[lo]];
      }
      for (let i = text.indexOf(q); i >= 0 && found.length < 5000; i = text.indexOf(q, i + q.length)) {
        const a = locate(i), b = locate(i + q.length - 1);
        const r = document.createRange();
        r.setStart(a[0], a[1]);
        r.setEnd(b[0], b[1] + 1);
        found.push(r);
      }
    }
    if (foundIndex >= found.length) foundIndex = found.length - 1;
    if (foundIndex < 0 && found.length) foundIndex = 0;
    paintFound();
  }

  function paintFound() {
    if (canHighlight) {
      CSS.highlights.set('find', new Highlight(...found));
      if (foundIndex >= 0 && found[foundIndex]) CSS.highlights.set('find-current', new Highlight(found[foundIndex]));
      else CSS.highlights.delete('find-current');
    }
    if (typeof Android.found === 'function') Android.found(found.length ? foundIndex + 1 : 0, found.length);
  }

  function showFound() {
    const r = found[foundIndex];
    if (!r) return;
    const box = r.getBoundingClientRect();
    if (box.top < 80 || box.bottom > window.innerHeight - 80) {
      window.scrollBy({ top: box.top - window.innerHeight / 3, behavior: 'smooth' });
    }
    if (!canHighlight) {
      const sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(r.cloneRange());
    }
  }

  function step(dir) {
    if (!found.length) return;
    foundIndex = (foundIndex + dir + found.length) % found.length;
    paintFound();
    showFound();
  }

  function replaceRange(r, text) {
    const sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(r);
    savedRange = r.cloneRange();
    if (text) document.execCommand('insertText', false, text);
    else document.execCommand('delete');
  }

  window.finder = {
    search: function (q) { foundIndex = 0; search(q); showFound(); },
    next: function () { step(1); },
    prev: function () { step(-1); },
    replace: function (text) {
      if (readOnly || !found[foundIndex]) return;
      if (typingTimer) snapshot();
      replaceRange(found[foundIndex], text);
      afterCommand();
      search(foundQuery);
      showFound();
    },
    replaceAll: function (text) {
      if (readOnly || !found.length) return 0;
      if (typingTimer) snapshot();
      const n = found.length;
      // De atrás hacia adelante: cada reemplazo no corre las posiciones de los anteriores
      for (let i = found.length - 1; i >= 0; i--) replaceRange(found[i], text);
      afterCommand();
      search(foundQuery);
      return n;
    },
    clear: function () {
      found = [];
      foundIndex = -1;
      foundQuery = '';
      if (canHighlight) { CSS.highlights.delete('find'); CSS.highlights.delete('find-current'); }
    }
  };

  /** Palabras, caracteres y párrafos del documento. */
  window.docStats = function () {
    const text = textNodes().map(function (n) { return n.nodeValue; }).join(' ');
    const words = text.split(/\s+/).filter(function (w) { return /[\p{L}\p{N}]/u.test(w); }).length;
    const visible = textNodes().map(function (n) { return n.nodeValue; }).join('');
    const paragraphs = Array.prototype.filter.call(page.querySelectorAll(LEAF_BLOCKS), function (el) {
      return !el.closest('[data-x]') && el.textContent.trim().length > 0;
    }).length;
    return JSON.stringify({
      words: words,
      chars: visible.length,
      charsNoSpaces: visible.replace(/\s/g, '').length,
      paragraphs: paragraphs
    });
  };

  // ---------- Copiar formato (brocha) ----------

  let painter = null;

  /** Formato del texto donde está el cursor. */
  function formatAt(el) {
    const cs = getComputedStyle(el);
    let underline = false, strike = false;
    for (let n = el; n && n !== page; n = n.parentNode) {
      const d = getComputedStyle(n).textDecorationLine || '';
      if (d.indexOf('underline') >= 0) underline = true;
      if (d.indexOf('line-through') >= 0) strike = true;
    }
    return {
      bold: parseInt(cs.fontWeight, 10) >= 600,
      italic: cs.fontStyle !== 'normal',
      underline: underline,
      strike: strike,
      sup: !!el.closest('sup'),
      sub: !!el.closest('sub'),
      color: cs.color,
      back: backgroundAt(el),
      font: cs.fontFamily,
      sizePt: Math.round(parseFloat(cs.fontSize) * 0.75 * 2) / 2
    };
  }

  function togglePainter() {
    if (painter === null) {
      const el = anchorElement();
      if (el) painter = formatAt(el);
      queueReport();
      return;
    }
    const f = painter;
    painter = null;
    const sel = window.getSelection();
    if (!savedRange || savedRange.collapsed) { queueReport(); return; }
    // Cada comando cambia el DOM: la selección se vuelve a tomar después de cada uno
    function apply(name, value) {
      exec(name, value);
      if (sel.rangeCount) savedRange = sel.getRangeAt(0).cloneRange();
    }
    apply('removeFormat');
    if (f.bold) apply('bold');
    if (f.italic) apply('italic');
    if (f.underline) apply('underline');
    if (f.strike) apply('strikeThrough');
    if (f.sup) apply('superscript');
    if (f.sub) apply('subscript');
    apply('foreColor', f.color);
    if (f.back) apply('hiliteColor', f.back);
    apply('fontName', f.font);
    pendingFontPt = f.sizePt;
    apply('fontSize', '7');
    afterCommand();
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
      sup: state('superscript'),
      sub: state('subscript'),
      painter: painter !== null,
      page: pageSetup(),
      block: (value('formatBlock') || 'p').toLowerCase(),
      font: value('fontName'),
      size: Math.round(sizePx * 0.75 * 2) / 2,
      color: value('foreColor'),
      back: el ? backgroundAt(el) : '',
      align: state('justifyCenter') ? 'center' : state('justifyRight') ? 'right' : state('justifyFull') ? 'justify' : 'left',
      ul: state('insertUnorderedList'),
      ol: state('insertOrderedList'),
      hasSelection: sel.rangeCount > 0 && !sel.isCollapsed && page.contains(sel.getRangeAt(0).commonAncestorContainer),
      image: selectedImg && page.contains(selectedImg) ? {
        pct: imagePct(selectedImg),
        align: align(blockOf(selectedImg) || selectedImg) || 'left',
        w: Math.round(imageWidthPt(selectedImg)),
        h: Math.round(imageHeightPt(selectedImg)),
        maxW: Math.round(contentWidthPt())
      } : null,
      table: tableState(),
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

  let fitRetry = null;

  /** Ancho real de la pantalla. Al abrir, Android puede informar un ancho provisorio (980 px). */
  function screenWidth() {
    return document.documentElement.clientWidth || window.innerWidth || 0;
  }

  function fit() {
    clearTimeout(fitRetry);
    if (document.body.classList.contains('paged')) {
      const widthPt = parseFloat(page.dataset.w || '0');
      if (widthPt > 0) {
        const sw = screenWidth();
        // Todavía sin medida: se reintenta en cuanto el WebView tenga su tamaño
        if (sw < 50) { fitRetry = setTimeout(fit, 50); return; }
        // La hoja ocupa todo el ancho de la pantalla (agrandando o achicando), con un borde mínimo
        document.body.style.zoom = (sw - 8) / (widthPt * 4 / 3);
        return;
      }
    }
    document.body.style.zoom = '';
  }

  // ---------- Hojas (vista de página) ----------
  // Detrás del texto se dibujan hojas del tamaño elegido. El texto sigue siendo un solo flujo:
  // separadores invisibles (.wgap) lo empujan para que nada caiga en el espacio entre hojas ni
  // en los márgenes. Se rehacen tras cada cambio y nunca se guardan.

  const GAP_PX = 24;
  const PX = 4 / 3;
  const LETTER = { w: 612, h: 792, mt: 72, mr: 72, mb: 72, ml: 72 };
  const sheetEl = document.getElementById('sheet');
  const pagesLayer = document.createElement('div');
  pagesLayer.id = 'pages-layer';
  sheetEl.insertBefore(pagesLayer, sheetEl.firstChild);
  let paginateTimer = null;
  let pageCount = 1;

  function paged() { return document.body.classList.contains('paged'); }

  /** Hoja actual en puntos. Sin tamaño guardado (notas), Carta. */
  function pageSetup() {
    const w = parseFloat(page.dataset.w || '0');
    if (!(w > 0)) return LETTER;
    const s = page.style;
    function m(v, d) { const p = sizePt(v || ''); return p === null ? d : p; }
    return {
      w: w,
      h: parseFloat(page.dataset.ph || '0') || Math.round(w * 11 / 8.5),
      mt: m(s.paddingTop, 72), mr: m(s.paddingRight, 72), mb: m(s.paddingBottom, 72), ml: m(s.paddingLeft, 72)
    };
  }

  function applyPage(p) {
    page.dataset.w = Math.round(p.w * 10) / 10;
    page.dataset.ph = Math.round(p.h * 10) / 10;
    page.style.width = p.w + 'pt';
    page.style.padding = p.mt + 'pt ' + p.mr + 'pt ' + p.mb + 'pt ' + p.ml + 'pt';
  }

  function queuePaginate(delay) {
    clearTimeout(paginateTimer);
    paginateTimer = setTimeout(paginate, delay == null ? 250 : delay);
  }

  function clearGaps() {
    page.querySelectorAll('.wgap').forEach(function (g) { g.remove(); });
  }

  function makeGap(tag) {
    const g = document.createElement(tag);
    g.className = 'wgap';
    g.dataset.x = '1';
    g.setAttribute('contenteditable', 'false');
    return g;
  }

  function paginate() {
    paginateTimer = null;
    // Lo que el usuario cambió se registra antes; lo que cambia la paginación se descarta
    if (observer) markChanges(observer.takeRecords());
    clearGaps();
    if (!paged()) {
      pagesLayer.innerHTML = '';
      sheetEl.style.removeProperty('--pages-h');
      lastStep = 0;
      if (observer) observer.takeRecords();
      reportPages();
      return;
    }
    if (!(parseFloat(page.dataset.w || '0') > 0)) { applyPage(LETTER); fit(); }
    const ps = pageSetup();
    const H = ps.h * PX, MT = ps.mt * PX, MB = ps.mb * PX, STEP = H + GAP_PX;
    const usable = H - MT - MB;
    // Una imagen nunca más alta que el área útil de la hoja
    sheetEl.style.setProperty('--usable-h', usable + 'px');
    const scale = page.offsetWidth ? page.getBoundingClientRect().width / page.offsetWidth : 1;
    function y(v) { return (v - page.getBoundingClientRect().top) / scale; }
    function pageAt(v) { return Math.max(0, Math.floor(v / STEP)); }
    function bottomOf(i) { return i * STEP + H - MB; }
    function topOf(i) { return i * STEP + MT; }

    /** Separador antes de node que lleva su borde superior a target (corrige márgenes que colapsan). */
    function pushTo(node, gap, target) {
      gap.style.height = Math.max(0, target - y(node.getBoundingClientRect().top)) + 'px';
      node.parentNode.insertBefore(gap, node);
      const diff = target - y(node.getBoundingClientRect().top);
      if (Math.abs(diff) > 0.5) gap.style.height = Math.max(0, parseFloat(gap.style.height) + diff) + 'px';
    }

    /** Separador después de un salto de página manual: lo que sigue va a la hoja siguiente. */
    function breakAfter(node) {
      if (node.nextSibling && node.nextSibling.classList && node.nextSibling.classList.contains('wgap')) return;
      const r = node.getBoundingClientRect();
      const gap = makeGap(node.tagName === 'SPAN' ? 'span' : 'div');
      gap.style.height = Math.max(0, topOf(pageAt(y(r.top)) + 1) - y(r.bottom)) + 'px';
      node.parentNode.insertBefore(gap, node.nextSibling);
    }

    /** Primer punto del bloque que cae fuera del área útil de su hoja (o un salto manual). */
    function firstCrossing(block) {
      const walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT, {
        acceptNode: function (n) {
          if (n.nodeType === 1 && n.classList.contains('wgap')) return NodeFilter.FILTER_REJECT;
          return NodeFilter.FILTER_ACCEPT;
        }
      });
      const range = document.createRange();
      function crosses(r) {
        if (!r || (!r.width && !r.height)) return false;
        const top = y(r.top), i = pageAt(top);
        return y(r.bottom) > bottomOf(i) + 0.5 || top > bottomOf(i);
      }
      let n;
      while ((n = walker.nextNode())) {
        if (n.nodeType === 1) {
          if (n.classList.contains('wpb') || n.matches('hr.pb')) {
            const next = n.nextSibling;
            if (!(next && next.classList && next.classList.contains('wgap'))) return { forced: n };
            continue;
          }
          if (n.tagName === 'IMG' && crosses(n.getBoundingClientRect())) return { el: n };
          continue;
        }
        const len = n.nodeValue.length;
        if (!len) continue;
        range.selectNodeContents(n);
        const rects = range.getClientRects();
        let any = false;
        for (let k = 0; k < rects.length; k++) if (crosses(rects[k])) { any = true; break; }
        if (!any) continue;
        // El primer carácter que cae fuera es el primero de esa línea
        for (let o = 0; o < len; o++) {
          range.setStart(n, o);
          range.setEnd(n, o + 1);
          const rr = range.getClientRects();
          if (rr.length && crosses(rr[rr.length - 1])) return { node: n, offset: o, top: y(rr[rr.length - 1].top) };
        }
      }
      return null;
    }

    /** Corta un bloque largo entre líneas (o tras un salto manual) hasta que todo quepa. */
    function splitLines(block) {
      for (let guard = 0; guard < 300; guard++) {
        const hit = firstCrossing(block);
        if (!hit) return;
        if (hit.forced) { breakAfter(hit.forced); continue; }
        const gap = makeGap('span');
        if (hit.el) {
          const top = y(hit.el.getBoundingClientRect().top);
          if (top <= topOf(pageAt(top)) + 1) return; // más alta que la hoja: queda así
          pushTo(hit.el, gap, topOf(pageAt(top) + 1));
          continue;
        }
        const i = pageAt(hit.top);
        // Una línea que ya empieza arriba de la hoja y aun así no cabe: nada más que hacer
        if (hit.top <= topOf(i) + 1) return;
        let node = hit.node;
        if (hit.offset > 0) node = node.splitText(hit.offset);
        node.parentNode.insertBefore(gap, node);
        const r = document.createRange();
        r.setStart(node, 0);
        r.setEnd(node, Math.min(1, node.nodeValue.length));
        const target = topOf(i + 1);
        gap.style.height = Math.max(0, target - hit.top) + 'px';
        const rects = r.getClientRects();
        if (rects.length) {
          const diff = target - y(rects[rects.length - 1].top);
          if (Math.abs(diff) > 0.5) gap.style.height = Math.max(0, parseFloat(gap.style.height) + diff) + 'px';
        }
      }
    }

    function placeTable(t) {
      Array.prototype.slice.call(t.rows).forEach(function (row, idx) {
        if (row.classList.contains('wgap')) return;
        const r = row.getBoundingClientRect();
        const top = y(r.top), bottom = y(r.bottom), i = pageAt(top);
        const outside = top > bottomOf(i);
        if (!outside && (bottom <= bottomOf(i) + 0.5 || bottom - top > usable)) return;
        const target = outside ? topOf(i + 1) : topOf(i + 1);
        if (idx === 0) { pushTo(t, makeGap('div'), target); return; }
        const gap = makeGap('tr');
        const td = document.createElement('td');
        td.colSpan = 99;
        gap.appendChild(td);
        pushTo(row, gap, target);
      });
    }

    function placeBlock(el) {
      if (el.matches('hr.pb')) { breakAfter(el); return; }
      const r = el.getBoundingClientRect();
      if (!r.height) return;
      const top = y(r.top), bottom = y(r.bottom), i = pageAt(top);
      if (el.tagName === 'TABLE') { placeTable(el); return; }
      if (top > bottomOf(i) || (bottom > bottomOf(i) + 0.5 && bottom - top <= usable && top > topOf(i) + 1)) {
        // Cabe en una hoja: pasa entero a la siguiente
        pushTo(el, makeGap('div'), topOf(i + 1));
      }
      splitLines(el);
    }

    Array.prototype.slice.call(page.children).forEach(function (el) {
      if (!el.classList.contains('wgap')) placeBlock(el);
    });

    // Hojas detrás del texto
    let end = MT;
    for (let c = page.lastElementChild; c; c = c.previousElementSibling) {
      const rr = c.getBoundingClientRect();
      if (rr.height || rr.width) { end = y(rr.bottom); break; }
    }
    pageCount = Math.max(1, pageAt(Math.max(0, end - 1)) + 1);
    const total = pageCount * STEP - GAP_PX;
    sheetEl.style.setProperty('--pages-h', total + 'px');
    let html = '';
    for (let p = 0; p < pageCount; p++) {
      html += '<div class="wsheet" style="top:' + p * STEP + 'px;height:' + H + 'px"></div>';
    }
    pagesLayer.innerHTML = html;
    lastStep = STEP;
    if (observer) observer.takeRecords();
    queueNotes();
    queueScroll();
    reportPages();
  }

  let lastStep = 0;

  /** Cuántas hojas hay y dónde están (en unidades de desplazamiento), para "Página N de M". */
  function reportPages() {
    if (typeof Android.pages !== 'function') return;
    if (!paged() || !lastStep) { Android.pages(0, 0, 0, window.innerHeight); return; }
    const pr = page.getBoundingClientRect();
    const scale = page.offsetWidth ? pr.width / page.offsetWidth : 1;
    Android.pages(pageCount, lastStep * scale, pr.top + window.scrollY, window.innerHeight);
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
    queuePaginate();
  });

  page.addEventListener('click', function (e) {
    // Los enlaces no navegan dentro del editor
    if (e.target.closest && e.target.closest('a')) e.preventDefault();
    // Tocar una imagen la selecciona (aparece la pestaña Imagen); tocar otra cosa la suelta
    selectImage(!readOnly && e.target.tagName === 'IMG' ? e.target : null);
  });

  document.addEventListener('selectionchange', function () {
    const sel = window.getSelection();
    if (sel.rangeCount && page.contains(sel.getRangeAt(0).commonAncestorContainer)) {
      savedRange = sel.getRangeAt(0).cloneRange();
    }
    queueReport();
  });

  window.addEventListener('resize', function () { fit(); queueNotes(); queuePaginate(100); });

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
      // Tamaño propio de cada pin (el ícono, un 60 %)
      const sz = Math.max(16, Math.min(72, n.size || 30));
      m.style.width = m.style.height = sz + 'px';
      m.firstChild.style.width = m.firstChild.style.height = Math.round(sz * 0.6) + 'px';
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
    block: function (tag) {
      // formatBlock crea un elemento nuevo: se le pasa el origen en Word del párrafo que reemplaza
      const before = blockOf(savedRange ? savedRange.startContainer : anchorElement());
      const src = before && before.dataset.b;
      exec('formatBlock', '<' + tag + '>');
      const after = blockOf(anchorElement());
      if (src && after && !after.dataset.b) {
        after.dataset.b = src;
        after.setAttribute('data-d', '1');
      }
      // El aspecto del párrafo pasa a ser el del nuevo estilo (título o normal), no el anterior
      if (after && after.dataset.b) {
        after.dataset.h = '1';
        ['fontFamily', 'fontSize', 'fontWeight', 'fontStyle', 'color', 'textDecoration', 'backgroundColor', 'margin', 'lineHeight'].forEach(function (p) {
          after.style[p] = '';
        });
      }
      afterCommand();
    },
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
    insertTable: function (rows, cols) {
      rows = Math.max(1, Math.min(50, rows | 0));
      cols = Math.max(1, Math.min(12, cols | 0));
      let html = '<table class="doc" style="width:100%"><tbody>';
      for (let r = 0; r < rows; r++) {
        html += '<tr>';
        for (let c = 0; c < cols; c++) html += '<td><p><br></p></td>';
        html += '</tr>';
      }
      exec('insertHTML', html + '</tbody></table><p><br></p>');
      afterCommand();
    },
    insertLink: function (url, text) {
      url = String(url || '').trim();
      if (!/^(https?:|mailto:)/i.test(url)) url = 'https://' + url;
      restoreRange();
      const sel = window.getSelection();
      if (sel.rangeCount && !sel.isCollapsed && !text) {
        exec('createLink', url);
      } else {
        const a = document.createElement('a');
        a.href = url;
        a.textContent = text || url;
        exec('insertHTML', a.outerHTML);
      }
      afterCommand();
    },
    insertPageBreak: function () {
      exec('insertHTML', '<span class="wpb" data-pb="1" contenteditable="false"></span>');
      afterCommand();
    },
    insertRule: function () {
      exec('insertHTML', '<hr class="wrule"><p><br></p>');
      afterCommand();
    },
    insertText: function (text) {
      exec('insertText', String(text));
      afterCommand();
    },
    lineSpacing: function (m) {
      formatBlocks(function (b) { b.style.lineHeight = String(Math.round(m * 1.2 * 100) / 100); });
    },
    spacing: function (which, pt) {
      formatBlocks(function (b) { b.style[which === 'before' ? 'marginTop' : 'marginBottom'] = pt + 'pt'; });
    },
    /** Sangría: en listas cambia el nivel; en párrafos mueve el margen izquierdo de a 36 pt (como Word). */
    indent: function (dir) {
      restoreRange();
      const blocks = selectedBlocks();
      if (blocks.some(function (b) { return b.tagName === 'LI'; })) {
        exec(dir > 0 ? 'indent' : 'outdent');
        afterCommand();
        return;
      }
      formatBlocks(function (b) {
        const cur = sizePt(b.style.marginLeft || '') || 0;
        b.style.marginLeft = Math.max(0, Math.round((cur + dir * 36) / 36) * 36) + 'pt';
      });
    },
    /** Lista con viñeta o numeración de un tipo (list-style-type). */
    list: function (kind, style) {
      restoreRange();
      let el = anchorElement();
      let list = el && el.closest('ul,ol');
      if (!list || list.tagName.toLowerCase() !== kind) {
        exec(kind === 'ol' ? 'insertOrderedList' : 'insertUnorderedList');
        el = anchorElement();
        list = el && el.closest('ul,ol');
      }
      if (list) list.style.listStyleType = style;
      afterCommand();
    },
    changeCase: changeCase,
    painter: togglePainter,
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
      queuePaginate(0);
    },
    /** Ajusta la hoja al ancho de la pantalla (Kotlin lo pide cuando el WebView ya tiene tamaño). */
    fit: function () { fit(); queueNotes(); queueScroll(); },
    /** Tamaño y márgenes de la hoja, en puntos (pestaña Diseño). */
    setPage: function (w, h, mt, mr, mb, ml) {
      if (!(w > 0 && h > 0)) return;
      applyPage({ w: w, h: h, mt: mt, mr: mr, mb: mb, ml: ml });
      fit();
      scheduleSave();
      queueReport();
      queuePaginate(0);
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
  setTimeout(sendModel, 0);
  queueReport();
  queuePaginate(0);
  window.addEventListener('load', function () { fit(); queuePaginate(0); });
  if (window.visualViewport) window.visualViewport.addEventListener('resize', fit);
  // Imágenes que terminan de cargar cambian la altura del texto
  page.addEventListener('load', function () { queuePaginate(); }, true);
})();
