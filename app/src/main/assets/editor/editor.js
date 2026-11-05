// Editor de texto enriquecido sobre contenteditable. Se comunica con Kotlin por el objeto Android.
(function () {
  'use strict';

  const page = document.getElementById('page');
  // Dónde actúan las herramientas: la hoja, o el encabezado/pie mientras se edita (hfLive)
  let ed = page;
  let hfLive = null;
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
    if (hfLive) hfSync();
    // La foto tiene que llevar las marcas de cambio: deshacer o rehacer la restaura tal cual
    if (observer) markChanges(observer.takeRecords());
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
    if (hfLive) hfReload(); else setCaret(history[i].caret);
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
    if (hfLive) hfSync();
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
    for (let n = node; n && n !== page && n !== hfLive; n = n.parentNode) {
      if (n.nodeType === 1 && n.hasAttribute('data-b') && !n.hasAttribute('data-d')) n.setAttribute('data-d', '1');
    }
  }

  function markChanges(records) {
    records.forEach(function (r) {
      if (r.type === 'attributes' && (r.attributeName === 'data-d' || r.attributeName === 'data-anchor')) return;
      // Marcar una imagen como seleccionada no cambia el documento
      if (r.type === 'attributes' && r.attributeName === 'class' && r.target.tagName === 'IMG') return;
      if (r.target === page || r.target === hfLive) {
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
    for (let n = node.parentNode; n && n !== page && n !== hfLive; n = n.parentNode) {
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
    for (let n = node; n && n !== page && n !== hfLive; n = n.parentNode) {
      const parent = n.parentNode;
      if (n.nodeType === 1 && parent && (parent === page || parent === hfLive || parent.tagName === 'TD')) return n;
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
        // Número de página o total insertado en el editor (los del original traen data-o)
        if (n.classList.contains('wpg') && !n.dataset.o) { push({ fld: n.dataset.f === 'NUMPAGES' ? 'NUMPAGES' : 'PAGE' }); continue; }
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
          let h = sizePt(n.style.height || '') || w * ratio;
          // No más ancha que el texto de la hoja, sin deformarla
          const ps = pageSetup(), max = Math.max(72, ps.w - ps.ml - ps.mr);
          if (w > max) { h *= max / w; w = max; }
          push({ img: n.getAttribute('src'), w: w, ih: h });
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
    ed.querySelectorAll(LEAF_BLOCKS).forEach(function (el) {
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
      // Aumentar la sangría en Chrome deja la lista nueva directamente dentro de la otra, sin <li>
      if (li.tagName === 'UL' || li.tagName === 'OL') {
        listModel(li, level + 1, out, first ? firstSrc : null);
        first = false;
        continue;
      }
      if (li.tagName !== 'LI') continue;
      const nested = [];
      const p = paraBox(li, false);
      p.src = li.dataset.b || (first ? firstSrc || null : null);
      first = false;
      p.tag = 'li';
      p.list = kind;
      p.list_style = list.style.listStyleType || null;
      p.list_id = id;
      // data-lv: nivel que tenía en Word el párrafo convertido a esta lista
      p.level = level + (parseInt(li.dataset.lv || '0', 10) || 0);
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
      // Salto de página suelto entre párrafos (documentos guardados por versiones anteriores)
      if (n.dataset.pb) { out.push({ p: { tag: 'p', segs: [{ pb: parseInt(n.dataset.pb, 10) || 1 }] } }); continue; }
      if (/^(DIV|SECTION|BLOCKQUOTE)$/.test(tag) && n.querySelector('p,h1,h2,h3,h4,h5,h6,div,table,ul,ol')) {
        blocksOf(n, counts, out);
        continue;
      }
      const p = paraBox(n, true);
      const nested = [];
      p.src = b || null;
      p.tag = blockTag(tag);
      p.align = align(n);
      if (n.dataset.nonum) p.nonum = true;
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

  /** Cuántas veces aparece cada ruta de Word en root (el cuerpo no cuenta las del encabezado/pie). */
  function countRefs(root) {
    const counts = {};
    root.querySelectorAll('[data-b]').forEach(function (el) {
      if (root === page && el.closest('.whf')) return;
      counts[el.dataset.b] = (counts[el.dataset.b] || 0) + 1;
    });
    return counts;
  }

  function model() {
    // Cambios recién hechos que el observador aún no procesó (se guardó en el mismo instante)
    if (observer) markChanges(observer.takeRecords());
    const counts = countRefs(page);
    listCount = 0;
    const out = [];
    blocksOf(page, counts, out);
    const result = { blocks: out };
    if (parseFloat(page.dataset.w || '0') > 0) result.page = pageSetup();
    const hf = headersModel();
    if (hf) result.hf = hf;
    if (page.dataset.hfo) result.hfo = hfSettings();
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
    ed.focus({ preventScroll: true });
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
    liftLists();
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

  /** Mueve nodos del documento sin perder la selección (Chrome la colapsa al sacarlos de su lugar). */
  function keepingSelection(fn) {
    const sel = window.getSelection();
    const r = sel.rangeCount ? sel.getRangeAt(0) : null;
    const pos = r && [r.startContainer, r.startOffset, r.endContainer, r.endOffset];
    fn();
    if (!pos || !ed.contains(pos[0]) || !ed.contains(pos[2])) return;
    const again = document.createRange();
    again.setStart(pos[0], pos[1]);
    again.setEnd(pos[2], pos[3]);
    sel.removeAllRanges();
    sel.addRange(again);
    savedRange = again.cloneRange();
  }

  /**
   * Chrome deja la lista nueva dentro del párrafo original. Un <ul> dentro de <p> no sobrevive a
   * volver a leer el HTML (quedan párrafos vacíos), así que la lista pasa a ocupar su lugar.
   */
  function liftLists() {
    const lists = ed.querySelectorAll('p > ul, p > ol, h1 > ul, h1 > ol, h2 > ul, h2 > ol, h3 > ul, h3 > ol, h4 > ul, h4 > ol, h5 > ul, h5 > ol, h6 > ul, h6 > ol');
    if (!lists.length) return;
    keepingSelection(function () {
      lists.forEach(function (list) {
        const p = list.parentNode;
        if (!p || !p.parentNode) return;
        const tail = p.cloneNode(false);
        tail.removeAttribute('data-b');
        tail.removeAttribute('data-d');
        tail.removeAttribute('data-anchor');
        while (list.nextSibling) tail.appendChild(list.nextSibling);
        const rest = function (el) { return !!el.textContent.replace(/[\s​]/g, '') || !!el.querySelector('img,table,.wo'); };
        p.removeChild(list);
        p.parentNode.insertBefore(list, p.nextSibling);
        if (rest(tail)) list.parentNode.insertBefore(tail, list.nextSibling);
        if (!rest(p)) {
          // Sin nada más: la lista toma su origen en Word y el párrafo desaparece
          const li = list.querySelector('li');
          if (li && p.dataset.b && !li.dataset.b) li.dataset.b = p.dataset.b;
          if (li && p.dataset.anchor && !li.dataset.anchor) li.dataset.anchor = p.dataset.anchor;
          p.remove();
        }
      });
    });
  }

  // ---------- Listas que vienen de Word ----------
  // Son párrafos con su número o viñeta en un span (.wl), no <ul>/<ol>: las herramientas de lista
  // no los reconocen, así que se tratan aquí. Quitarla marca el párrafo con data-nonum; cambiarla
  // lo convierte en una lista del editor que mantiene su origen.

  function wordMark(b) {
    return Array.prototype.find.call(b.children, function (c) { return c.classList.contains('wl'); }) || null;
  }

  function wordListKind(mark) {
    return mark.dataset.lk || (/[\p{N}\p{L}]/u.test(mark.textContent) ? 'ol' : 'ul');
  }

  /** Párrafos de lista de Word de la selección. Con solo el cursor, toda su lista. */
  function selectedWordList() {
    const blocks = selectedBlocks().filter(wordMark);
    if (blocks.length !== 1 || !savedRange.collapsed) return blocks;
    const b = blocks[0], ln = wordMark(b).dataset.ln;
    const same = function (el) {
      const m = el && el.nodeType === 1 && el.tagName !== 'TABLE' ? wordMark(el) : null;
      return m && m.dataset.ln === ln;
    };
    const out = [b];
    for (let e = b.previousElementSibling; same(e); e = e.previousElementSibling) out.unshift(e);
    for (let e = b.nextElementSibling; same(e); e = e.nextElementSibling) out.push(e);
    return out;
  }

  function removeWordList(blocks) {
    keepingSelection(function () {
      blocks.forEach(function (b) {
        wordMark(b).remove();
        b.dataset.nonum = '1';
        b.setAttribute('data-d', '1');
      });
    });
  }

  function convertWordList(blocks, kind, style) {
    keepingSelection(function () {
      let list = null, last = null;
      blocks.forEach(function (b) {
        const mark = wordMark(b);
        const li = document.createElement('li');
        ['data-b', 'data-anchor'].forEach(function (a) { if (b.hasAttribute(a)) li.setAttribute(a, b.getAttribute(a)); });
        if (mark.dataset.lv && mark.dataset.lv !== '0') li.dataset.lv = mark.dataset.lv;
        if (b.style.textAlign) li.style.textAlign = b.style.textAlign;
        mark.remove();
        while (b.firstChild) li.appendChild(b.firstChild);
        if (!list || last !== b.previousElementSibling) {
          list = document.createElement(kind);
          if (style) list.style.listStyleType = style;
          b.parentNode.insertBefore(list, b);
        }
        list.appendChild(li);
        last = list;
        b.remove();
      });
    });
  }

  /** execCommand solo conoce 7 tamaños: se usa el 7 como marca y se reemplaza por el tamaño real. */
  function fixFontSizes() {
    if (pendingFontPt == null) return;
    ed.querySelectorAll('font[size="7"]').forEach(function (el) {
      const span = document.createElement('span');
      span.style.fontSize = pendingFontPt + 'pt';
      while (el.firstChild) span.appendChild(el.firstChild);
      el.replaceWith(span);
    });
    ed.querySelectorAll('[style*="xxx-large"]').forEach(function (el) {
      el.style.fontSize = pendingFontPt + 'pt';
    });
  }

  /** Quita una propiedad de estilo aplicada con un color marcador (no hay comando "quitar color"). */
  function clearMarked(prop) {
    ed.querySelectorAll('[style]').forEach(function (el) {
      if (el.style[prop] === CLEAR_COLOR) {
        el.style[prop] = '';
        if (!el.getAttribute('style') && el.tagName === 'SPAN') unwrap(el);
      }
    });
    ed.querySelectorAll('font[color="#010203"]').forEach(unwrap);
  }

  // ---------- Mayúsculas / minúsculas ----------

  /** Cambia el texto seleccionado sin tocar su formato (cada trozo conserva su span). */
  function changeCase(mode) {
    restoreRange();
    const sel = window.getSelection();
    if (!sel.rangeCount || sel.isCollapsed) return;
    const range = sel.getRangeAt(0);
    if (!ed.contains(range.commonAncestorContainer)) return;
    if (typingTimer) snapshot();

    const parts = [];
    const walker = document.createTreeWalker(ed, NodeFilter.SHOW_TEXT);
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
    if (!img || !ed.contains(img) || readOnly) return;
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
    if (!r || !ed.contains(r.startContainer)) return null;
    const el = r.startContainer.nodeType === 1 ? r.startContainer : r.startContainer.parentNode;
    const td = el && el.closest('td,th');
    if (!td || !ed.contains(td)) return null;
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
    if (focus && ed.contains(focus)) caretIn(focus);
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
    for (let n = el; n && n !== ed; n = n.parentNode) {
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
    if (!node || !ed.contains(node)) return null;
    if (node.nodeType === Node.TEXT_NODE) node = node.parentNode;
    return node;
  }

  function backgroundAt(el) {
    for (let n = el; n && n !== ed; n = n.parentNode) {
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

  function wordKindAt(el) {
    const b = el && blockOf(el);
    const mark = b && wordMark(b);
    return mark ? wordListKind(mark) : null;
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
      ul: state('insertUnorderedList') || wordKindAt(el) === 'ul',
      ol: state('insertOrderedList') || wordKindAt(el) === 'ol',
      hasSelection: sel.rangeCount > 0 && !sel.isCollapsed && ed.contains(sel.getRangeAt(0).commonAncestorContainer),
      hf: hfLive ? hfLive.dataset.kind : null,
      hfFirst: hfOptions().tp,
      hfOddEven: hfOptions().eo,
      pageFmt: page.dataset.pf || '1',
      pageStart: pageStart(),
      hfTop: Math.round(hfDistances().hd / PX * 10) / 10,
      hfBottom: Math.round(hfDistances().fd / PX * 10) / 10,
      image: selectedImg && ed.contains(selectedImg) ? {
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
    const H = ps.h * PX, STEP = H + GAP_PX;
    // Un encabezado o pie más alto que su margen empuja el texto (como en Word)
    const hfH = hfHeights(ps);
    function mtOf(i) { return Math.max(ps.mt * PX, hfH[hfFor('h', i)] || 0); }
    function mbOf(i) { return Math.max(ps.mb * PX, hfH[hfFor('f', i)] || 0); }
    const MT = mtOf(0);
    const usable = H - Math.max(mtOf(0), mtOf(1), mtOf(2)) - Math.max(mbOf(0), mbOf(1), mbOf(2));
    // Una imagen nunca más alta que el área útil de la hoja
    sheetEl.style.setProperty('--usable-h', usable + 'px');
    const scale = page.offsetWidth ? page.getBoundingClientRect().width / page.offsetWidth : 1;
    function y(v) { return (v - page.getBoundingClientRect().top) / scale; }
    function pageAt(v) { return Math.max(0, Math.floor(v / STEP)); }
    function bottomOf(i) { return i * STEP + H - mbOf(i); }
    function topOf(i) { return i * STEP + mtOf(i); }

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

    function placeBlock(el, again) {
      if (el.matches('hr.pb')) { breakAfter(el); return; }
      const r = el.getBoundingClientRect();
      if (!r.height) return;
      const top = y(r.top), bottom = y(r.bottom), i = pageAt(top);
      // Empieza dentro de un encabezado alto: baja hasta donde termina
      if (!again && top < topOf(i) - 0.5) { pushTo(el, makeGap('div'), topOf(i)); placeBlock(el, true); return; }
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
    drawHeaders(ps);
    // Números de página escritos en el texto: el de la hoja donde quedan
    page.querySelectorAll('.wpg').forEach(function (f) {
      if (f.closest('.whf')) return;
      const v = f.dataset.f === 'NUMPAGES' ? String(pageCount) : formatPage(pageAt(y(f.getBoundingClientRect().top)) + pageStart(), page.dataset.pf || '1');
      if (f.textContent !== v) f.textContent = v;
    });
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

  // ---------- Encabezado y pie de página ----------
  // Cada uno es un texto aparte, como el cuerpo: vive oculto dentro de la hoja (div.whf, data-hf =
  // h/f + d todas las páginas o 1 la primera) para guardarse con el HTML, y cada hoja dibuja una
  // copia en su margen con su número de página. Para editarlo se abre sobre su hoja (hfLive) y
  // todas las herramientas actúan ahí (ed); el cuerpo queda atenuado. data-hfd: cambió.

  let hfId = null;
  let hfPage = 0;
  const hfObserver = typeof MutationObserver === 'undefined' ? null : new MutationObserver(markChanges);

  function hfDiv(id) {
    return Array.prototype.find.call(page.children, function (c) { return c.classList.contains('whf') && c.dataset.hf === id; }) || null;
  }

  function ensureHf(id) {
    let div = hfDiv(id);
    if (!div) {
      div = document.createElement('div');
      div.className = 'whf';
      div.dataset.x = '1';
      div.dataset.hf = id;
      div.setAttribute('contenteditable', 'false');
      div.hidden = true;
      page.appendChild(div);
    }
    return div;
  }

  const HF_IDS = ['hd', 'h1', 'he', 'fd', 'f1', 'fe'];

  /** Primera página diferente y pares e impares diferentes. */
  function hfOptions() {
    // HTML de antes de guardar la opción: la primera página distinta se reconoce por su encabezado
    const tp = page.dataset.tp ? page.dataset.tp === '1' : !!(hfDiv('h1') || hfDiv('f1'));
    return { tp: tp, eo: page.dataset.eo === '1' };
  }

  /** Cuál se ve en la hoja i: el de la primera página, el de las pares o el de todas. */
  function hfFor(kind, i) {
    const o = hfOptions();
    if (i === 0 && o.tp) return kind + '1';
    const n = i + pageStart();
    if (o.eo && n % 2 === 0) return kind + 'e';
    return kind + 'd';
  }

  /** Todo lo que se guarda de encabezados y pies en la sección de Word. */
  function hfSettings() {
    const o = hfOptions(), d = hfDistances();
    return { tp: o.tp, eo: o.eo, fmt: page.dataset.pf || '1', start: pageStart(), hd: d.hd / PX, fd: d.fd / PX };
  }

  const HF_LABELS ={ d: '', 1: ' de primera página', e: ' de página par' };

  function hfLabel(id) {
    const base = id[0] === 'h' ? 'Encabezado' : 'Pie de página';
    if (id[1] === 'd' && hfOptions().eo) return base + ' de página impar';
    return base + (HF_LABELS[id[1]] || '');
  }

  function hfHasContent(el) {
    return !!el && (!!el.textContent.replace(/[\s​]/g, '') || !!el.querySelector('img,table,.wpg'));
  }

  function htmlText(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function hfDistances() {
    return { hd: (parseFloat(page.dataset.hd || '36') || 36) * PX, fd: (parseFloat(page.dataset.fd || '36') || 36) * PX };
  }

  function pageStart() {
    const n = parseInt(page.dataset.pn || '1', 10);
    return isNaN(n) ? 1 : n;
  }

  /** Número de página con la numeración elegida: 1, i, I, a o A. */
  function formatPage(n, pf) {
    if (pf === 'i' || pf === 'I') {
      if (n <= 0 || n > 3999) return String(n);
      const R = [[1000, 'm'], [900, 'cm'], [500, 'd'], [400, 'cd'], [100, 'c'], [90, 'xc'], [50, 'l'], [40, 'xl'], [10, 'x'], [9, 'ix'], [5, 'v'], [4, 'iv'], [1, 'i']];
      let s = '';
      R.forEach(function (p) { while (n >= p[0]) { s += p[1]; n -= p[0]; } });
      return pf === 'I' ? s.toUpperCase() : s;
    }
    if (pf === 'a' || pf === 'A') {
      // Como Word: a … z, aa … zz, aaa …
      if (n <= 0) return String(n);
      const s = String.fromCharCode(97 + (n - 1) % 26).repeat(Math.floor((n - 1) / 26) + 1);
      return pf === 'A' ? s.toUpperCase() : s;
    }
    return String(n);
  }

  function fillNumbers(root, i, total) {
    const pf = page.dataset.pf || '1';
    root.querySelectorAll('.wpg').forEach(function (f) {
      const v = f.dataset.f === 'NUMPAGES' ? String(total) : formatPage(i + pageStart(), pf);
      if (f.textContent !== v) f.textContent = v;
    });
  }

  /** Hasta dónde llegan encabezado y pie (desde el borde), para que el texto no se les encime. */
  function hfHeights(ps) {
    const out = {};
    const d = hfDistances();
    const box = document.createElement('div');
    box.className = 'hfx';
    box.style.cssText = 'position:absolute;visibility:hidden;left:0;top:0;width:' + (ps.w - ps.ml - ps.mr) * PX + 'px';
    sheetEl.appendChild(box);
    HF_IDS.forEach(function (id) {
      const src = hfLive && id === hfId ? hfLive : hfDiv(id);
      if (!hfHasContent(src)) return;
      box.innerHTML = src.innerHTML;
      out[id] = (id[0] === 'h' ? d.hd : d.fd) + box.offsetHeight + 6;
    });
    box.remove();
    return out;
  }

  function drawHeaders(ps) {
    const sheets = pagesLayer.children;
    const d = hfDistances();
    for (let i = 0; i < sheets.length; i++) {
      ['h', 'f'].forEach(function (k) {
        const id = hfFor(k, i);
        // El que se está editando ya está a la vista
        if (hfLive && id === hfId && i === hfPage) return;
        const src = hfDiv(id);
        if (!hfHasContent(src)) return;
        const box = document.createElement('div');
        box.className = (k === 'h' ? 'whead' : 'wfoot') + ' hfx';
        box.style.left = ps.ml * PX + 'px';
        box.style.right = ps.mr * PX + 'px';
        if (k === 'h') box.style.top = d.hd + 'px';
        else box.style.bottom = d.fd + 'px';
        box.innerHTML = src.innerHTML;
        fillNumbers(box, i, sheets.length);
        sheets[i].appendChild(box);
      });
    }
    if (hfLive) { hfPlace(); fillNumbers(hfLive, hfPage, sheets.length); }
  }

  /** Lleva el encabezado/pie que se edita a su lugar en su hoja. */
  function hfPlace() {
    if (!hfLive) return;
    const ps = pageSetup(), H = ps.h * PX, d = hfDistances();
    const STEP = lastStep || H + GAP_PX;
    hfLive.style.left = ps.ml * PX + 'px';
    hfLive.style.width = (ps.w - ps.ml - ps.mr) * PX + 'px';
    const base = hfPage * STEP;
    hfLive.style.top = (hfLive.dataset.kind === 'h' ? base + d.hd : base + H - d.fd - hfLive.offsetHeight) + 'px';
  }

  /** Copia lo escrito al encabezado/pie guardado. */
  function hfSync() {
    if (!hfLive) return;
    if (hfObserver) markChanges(hfObserver.takeRecords());
    const src = ensureHf(hfId);
    const copy = hfLive.cloneNode(true);
    copy.querySelectorAll('img.wsel').forEach(function (img) {
      img.classList.remove('wsel');
      if (!img.className) img.removeAttribute('class');
    });
    const html = copy.innerHTML.replace(/​/g, '');
    if (src.innerHTML !== html) {
      src.innerHTML = html;
      src.dataset.hfd = '1';
    }
  }

  function caretAtEnd(root) {
    const leaves = root.querySelectorAll(LEAF_BLOCKS);
    const target = leaves.length ? leaves[leaves.length - 1] : root;
    const r = document.createRange();
    r.selectNodeContents(target);
    r.collapse(false);
    const s = window.getSelection();
    s.removeAllRanges();
    s.addRange(r);
    savedRange = r.cloneRange();
  }

  function onEditInput() {
    fixFontSizes();
    if (hfLive) { hfSync(); hfPlace(); }
    scheduleSnapshot();
    scheduleSave();
    queueReport();
    queueNotes();
    queuePaginate();
  }

  function onEditBeforeInput(e) {
    if (e.inputType === 'historyUndo') { e.preventDefault(); undo(); return; }
    if (e.inputType === 'historyRedo') { e.preventDefault(); redo(); return; }
    // En modo lectura solo se permite dar formato (resaltar), no escribir ni borrar
    if (readOnly && e.inputType.indexOf('format') !== 0) e.preventDefault();
  }

  function onEditClick(e) {
    // Los enlaces no navegan dentro del editor
    if (e.target.closest && e.target.closest('a')) e.preventDefault();
    // Tocar una imagen la selecciona (aparece la pestaña Imagen); tocar otra cosa la suelta
    selectImage(!readOnly && e.target.tagName === 'IMG' ? e.target : null);
  }

  /** Abre el encabezado ('h') o pie ('f') de la hoja i para escribir en él. */
  function hfEnter(kind, i) {
    if (readOnly || !paged() || (kind !== 'h' && kind !== 'f')) return false;
    if (hfLive) hfExit(true);
    if (typingTimer) snapshot();
    i = Math.max(0, Math.min(pageCount - 1, i | 0));
    hfId = hfFor(kind, i);
    hfPage = i;
    const src = ensureHf(hfId);
    selectImage(null);
    hfLive = document.createElement('div');
    hfLive.className = 'whf-live hfx';
    hfLive.dataset.kind = kind;
    hfLive.dataset.label = hfLabel(hfId);
    hfLive.setAttribute('contenteditable', 'true');
    hfLive.setAttribute('spellcheck', 'false');
    hfLive.innerHTML = hfHasContent(src) ? src.innerHTML : '<p><br></p>';
    hfLive.addEventListener('beforeinput', onEditBeforeInput);
    hfLive.addEventListener('input', onEditInput);
    hfLive.addEventListener('click', onEditClick);
    hfLive.addEventListener('load', function () { hfPlace(); queuePaginate(); }, true);
    sheetEl.appendChild(hfLive);
    if (hfObserver) hfObserver.observe(hfLive, { subtree: true, childList: true, characterData: true, attributes: true });
    page.setAttribute('contenteditable', 'false');
    document.body.classList.add('hf-editing');
    ed = hfLive;
    hfPlace();
    hfLive.focus({ preventScroll: true });
    caretAtEnd(hfLive);
    queuePaginate(0);
    queueReport();
    return true;
  }

  /** Vuelve al cuerpo. quiet: sin guardar ni repaginar (se abre otro enseguida). */
  function hfExit(quiet) {
    if (!hfLive) return;
    hfSync();
    if (hfObserver) hfObserver.disconnect();
    selectImage(null);
    hfLive.remove();
    hfLive = null;
    hfId = null;
    ed = page;
    savedRange = null;
    page.setAttribute('contenteditable', 'true');
    document.body.classList.remove('hf-editing');
    if (quiet) return;
    snapshot();
    scheduleSave();
    queuePaginate(0);
    queueReport();
  }

  /** Tras deshacer: el encabezado abierto vuelve a mostrar lo guardado. */
  function hfReload() {
    if (!hfLive) return;
    const src = hfDiv(hfId);
    hfLive.innerHTML = hfHasContent(src) ? src.innerHTML : '<p><br></p>';
    if (hfObserver) hfObserver.takeRecords();
    caretAtEnd(hfLive);
    hfPlace();
  }

  /** Hoja que se ve en el centro de la pantalla. */
  function visiblePage() {
    if (!lastStep) return 0;
    const pr = page.getBoundingClientRect();
    const scale = page.offsetWidth ? pr.width / page.offsetWidth : 1;
    const mid = (window.innerHeight / 2 - pr.top) / scale;
    return Math.max(0, Math.min(pageCount - 1, Math.floor(mid / lastStep)));
  }

  const ALIGNS = ['left', 'center', 'right'];
  const PAGE_FIELD = '<span class="wo wpg" data-f="PAGE" contenteditable="false">1</span>';
  const TOTAL_FIELD = '<span class="wo wpg" data-f="NUMPAGES" contenteditable="false">1</span>';

  function numberBlocks(root) {
    return Array.prototype.filter.call(root.querySelectorAll(LEAF_BLOCKS), function (b) {
      return b.querySelector('.wpg') && !b.querySelector(LEAF_BLOCKS);
    });
  }

  window.headerFooter = {
    enter: function (kind, i) { return hfEnter(kind, i == null ? visiblePage() : i); },
    exit: function () { hfExit(false); },
    /** Del encabezado al pie de la misma hoja (o al revés). */
    swap: function () { if (hfLive) hfEnter(hfLive.dataset.kind === 'h' ? 'f' : 'h', hfPage); },
    /**
     * Primera página diferente (tp) y pares e impares diferentes (eo). Al encenderlas, la
     * primera página o las pares empiezan en blanco, como en Word; al apagarlas se guardan igual.
     */
    options: function (tp, eo) {
      if (readOnly) return;
      if (typingTimer) snapshot();
      const kind = hfLive ? hfLive.dataset.kind : null;
      const at = hfPage;
      if (hfLive) hfExit(true);
      page.dataset.tp = tp ? '1' : '0';
      page.dataset.eo = eo ? '1' : '0';
      page.dataset.hfo = '1';
      ['h', 'f'].forEach(function (k) {
        if (tp) ensureHf(k + '1');
        if (eo) ensureHf(k + 'e');
      });
      snapshot();
      scheduleSave();
      // Sigue editando la misma hoja, ahora con el encabezado que le toca
      if (kind) hfEnter(kind, at);
      queuePaginate(0);
      queueReport();
    },
    /** Dónde está el número de página, para la barra. */
    get: function () {
      if (hfLive) hfSync();
      function num(id) {
        const d = hfDiv(id);
        const b = d && numberBlocks(d)[0];
        return b ? { align: b.style.textAlign || 'left', fmt: b.querySelector('.wpg[data-f="NUMPAGES"]') ? 'nofm' : 'n' } : null;
      }
      const o = hfOptions();
      return JSON.stringify({ h: { num: num('hd') }, f: { num: num('fd') }, tp: o.tp, eo: o.eo });
    },
    /** Numeración (1, i, I, a, A) y número de la primera página. */
    numbering: function (pf, start) {
      if (readOnly) return;
      if (typingTimer) snapshot();
      page.dataset.pf = ['1', 'i', 'I', 'a', 'A'].indexOf(pf) >= 0 ? pf : '1';
      page.dataset.pn = String(Math.max(0, Math.min(9999, start | 0)));
      page.dataset.hfo = '1';
      snapshot();
      scheduleSave();
      queuePaginate(0);
      queueReport();
    },
    /** Distancia del encabezado y del pie al borde de la hoja, en puntos. */
    distance: function (hd, fd) {
      if (readOnly) return;
      if (typingTimer) snapshot();
      const ok = function (v) { return Math.max(0, Math.min(300, Math.round((+v || 0) * 10) / 10)); };
      page.dataset.hd = String(ok(hd));
      page.dataset.fd = String(ok(fd));
      page.dataset.hfo = '1';
      hfPlace();
      snapshot();
      scheduleSave();
      queuePaginate(0);
      queueReport();
    },
    /**
     * Diseño listo para el encabezado/pie abierto: 'blank', 'three' (izquierda, centro y
     * derecha), 'title' (el título del documento) o 'logo' (una imagen y el título).
     */
    preset: function (name, title, logo) {
      if (!hfLive || readOnly) return;
      if (typingTimer) snapshot();
      const t = htmlText(title || 'Título del documento');
      const cell = function (al, text) {
        return '<td style="border:none;width:33.3%"><p style="text-align:' + al + '">' + text + '</p></td>';
      };
      let html = '<p><br></p>';
      if (name === 'three') {
        html = '<table data-bd="0" style="width:100%"><tbody><tr>' +
          cell('left', 'Escribe aquí') + cell('center', 'Escribe aquí') + cell('right', 'Escribe aquí') +
          '</tr></tbody></table><p><br></p>';
      } else if (name === 'title') {
        html = '<p style="text-align:center"><span style="font-weight:bold">' + t + '</span></p>';
      } else if (name === 'logo' && logo) {
        html = '<p><img src="' + String(logo).replace(/[&"<>]/g, '') + '" style="height:36pt"> ' +
          '<span style="font-weight:bold">' + t + '</span></p>';
      }
      hfLive.innerHTML = html;
      // El logo, con su proporción y 36 pt de alto
      const img = hfLive.querySelector('img');
      if (img) {
        const size = function () {
          if (!img.naturalWidth) return;
          img.style.width = Math.round(36 * img.naturalWidth / img.naturalHeight) + 'pt';
          hfSync();
          hfPlace();
          scheduleSave();
          queuePaginate(0);
        };
        if (img.complete) size(); else img.onload = size;
      }
      caretAtEnd(hfLive);
      afterCommand();
    },
    /** Número de página arriba ('h'), abajo ('f') o donde está el cursor ('c'); '' lo quita. */
    number: function (where, align, fmt) {
      if (readOnly) return;
      if (where === 'c') {
        // Con un rango propio: insertHTML de Chrome a veces deja el campo fuera del párrafo
        if (typingTimer) snapshot();
        restoreRange();
        const sel = window.getSelection();
        if (!sel.rangeCount || !ed.contains(sel.getRangeAt(0).startContainer)) return;
        const r = sel.getRangeAt(0);
        r.deleteContents();
        const frag = r.createContextualFragment(fmt === 'nofm' ? 'Página ' + PAGE_FIELD + ' de ' + TOTAL_FIELD : PAGE_FIELD);
        const last = frag.lastChild;
        r.insertNode(frag);
        r.setStartAfter(last);
        r.collapse(true);
        sel.removeAllRanges();
        sel.addRange(r);
        savedRange = r.cloneRange();
        afterCommand();
        return;
      }
      if (typingTimer) snapshot();
      hfSync();
      HF_IDS.forEach(function (id) {
        const d = hfDiv(id);
        if (!d) return;
        const blocks = numberBlocks(d);
        if (!blocks.length) return;
        blocks.forEach(function (b) { b.remove(); });
        d.dataset.hfd = '1';
      });
      if (where === 'h' || where === 'f') {
        // En todas las páginas: también en las pares si son distintas (la primera queda como está)
        const al = ALIGNS.indexOf(align) >= 0 ? align : 'center';
        [where + 'd'].concat(hfOptions().eo ? [where + 'e'] : []).forEach(function (id) {
          const d = ensureHf(id);
          if (!hfHasContent(d)) d.innerHTML = '';
          d.insertAdjacentHTML('beforeend', '<p style="text-align:' + al + '">' +
            (fmt === 'nofm' ? 'Página ' + PAGE_FIELD + ' de ' + TOTAL_FIELD : PAGE_FIELD) + '</p>');
          d.dataset.hfd = '1';
        });
      }
      hfReload();
      snapshot();
      scheduleSave();
      queuePaginate(0);
      queueReport();
    }
  };

  /** Encabezados y pies cambiados, para guardarlos en Word: bloques como los del cuerpo. */
  function headersModel() {
    if (hfLive) hfSync();
    const out = {};
    let any = false;
    HF_IDS.forEach(function (id) {
      const d = hfDiv(id);
      if (!d || !d.dataset.hfd) return;
      const blocks = [];
      blocksOf(d, countRefs(d), blocks);
      out[id] = { blocks: blocks };
      any = true;
    });
    return any ? out : null;
  }

  // Doble toque en el margen de arriba o de abajo de una hoja: editar su encabezado o pie.
  // Con uno abierto, doble toque en el texto: volver al cuerpo.
  page.addEventListener('dblclick', function (e) {
    if (!paged() || readOnly || !lastStep) return;
    const ps = pageSetup();
    const pr = page.getBoundingClientRect();
    const scale = page.offsetWidth ? pr.width / page.offsetWidth : 1;
    const yy = (e.clientY - pr.top) / scale;
    const i = Math.floor(yy / lastStep);
    const within = yy - i * lastStep;
    if (within < ps.mt * PX) { e.preventDefault(); hfEnter('h', i); }
    else if (within > (ps.h - ps.mb) * PX && within <= ps.h * PX) { e.preventDefault(); hfEnter('f', i); }
    else if (hfLive) { e.preventDefault(); hfExit(false); }
  });

  // ---------- Eventos ----------

  page.addEventListener('beforeinput', onEditBeforeInput);
  page.addEventListener('input', onEditInput);
  page.addEventListener('click', onEditClick);

  document.addEventListener('selectionchange', function () {
    const sel = window.getSelection();
    if (sel.rangeCount && ed.contains(sel.getRangeAt(0).commonAncestorContainer)) {
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
    cmd: function (name, val) {
      restoreRange();
      const isList = name === 'insertUnorderedList' || name === 'insertOrderedList';
      const isAlign = /^justify/.test(name);
      if (isList || isAlign) {
        const word = isList ? selectedWordList() : selectedBlocks().filter(wordMark);
        if (word.length) {
          if (typingTimer) snapshot();
          if (isAlign) {
            // El número no editable de la lista hace que justify* rompa el párrafo
            const where = { justifyLeft: 'left', justifyCenter: 'center', justifyRight: 'right', justifyFull: 'justify' }[name];
            selectedBlocks().forEach(function (b) { b.style.textAlign = where; });
          } else {
            const kind = name === 'insertOrderedList' ? 'ol' : 'ul';
            if (wordListKind(wordMark(word[0])) === kind) removeWordList(word);
            else convertWordList(word, kind, null);
          }
          afterCommand();
          return;
        }
      }
      exec(name, val);
      afterCommand();
    },
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
      if (typingTimer) snapshot();
      restoreRange();
      // removeFormat también borra el aspecto del párrafo, que es el de su estilo: se conserva
      const LOOK = ['fontFamily', 'fontSize', 'fontWeight', 'fontStyle', 'color', 'textDecoration'];
      const looks = selectedBlocks().map(function (b) {
        return { b: b, s: LOOK.map(function (p) { return b.style[p]; }) };
      });
      exec('removeFormat');
      exec('hiliteColor', CLEAR_COLOR);
      clearMarked('backgroundColor');
      looks.forEach(function (x) {
        if (!ed.contains(x.b)) return;
        LOOK.forEach(function (p, i) { if (x.s[i] && !x.b.style[p]) x.b.style[p] = x.s[i]; });
      });
      // El texto limpiado ya no hereda nada de su trozo original de Word, tampoco fuente ni tamaño
      const sel = window.getSelection();
      if (sel.rangeCount && !sel.isCollapsed) {
        const r = sel.getRangeAt(0);
        ed.querySelectorAll('[data-r]').forEach(function (s) {
          const whole = document.createRange();
          whole.selectNodeContents(s);
          if (r.compareBoundaryPoints(Range.START_TO_START, whole) <= 0 && r.compareBoundaryPoints(Range.END_TO_END, whole) >= 0 &&
              s.textContent && !s.classList.contains('wh')) {
            s.removeAttribute('data-r');
          }
        });
      }
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
      // Con un rango propio: insertHTML de Chrome al final de un párrafo lo deja fuera de él
      if (typingTimer) snapshot();
      restoreRange();
      const sel = window.getSelection();
      if (!sel.rangeCount || !ed.contains(sel.getRangeAt(0).startContainer)) return;
      const r = sel.getRangeAt(0);
      r.deleteContents();
      const pb = document.createElement('span');
      pb.className = 'wpb';
      pb.dataset.pb = '1';
      pb.setAttribute('contenteditable', 'false');
      r.insertNode(pb);
      r.setStartAfter(pb);
      r.collapse(true);
      sel.removeAllRanges();
      sel.addRange(r);
      savedRange = r.cloneRange();
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
      const word = selectedWordList();
      if (word.length) {
        if (typingTimer) snapshot();
        convertWordList(word, kind, style);
        afterCommand();
        return;
      }
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
      if (ro) hfExit(false);
      page.setAttribute('inputmode', ro ? 'none' : 'text');
      document.body.classList.toggle('readonly', ro);
      if (!ro) page.focus({ preventScroll: true });
    },
    setMode: function (mode) {
      if (mode !== 'page') hfExit(false);
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
