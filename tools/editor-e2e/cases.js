const sleep = ms => new Promise(r => setTimeout(r, ms));

function frameDoc(html, opts) {
  const close = '</' + 'script>';
  const article = html.trimStart().startsWith('<article') ? html : '<article id="page">' + html + '</article>';
  return '<!DOCTYPE html><html><head><meta charset="utf-8"><base href="' + DATA.mediaUrl + '"><style>' + DATA.css + '</style></head>' +
    '<body class="' + (opts.paged ? 'paged' : 'mobile') + '" data-scroll="0"><div id="sheet">' + article + '<div id="notes-layer"></div></div>' +
    '<script>window.__models=[];window.__saves=[];window.__err=[];window.onerror=function(m){window.__err.push(String(m))};' +
    'window.Android={save:function(h){window.__saves.push(h)},model:function(m){window.__models.push(m)},onState:function(){},pages:function(){},found:function(){},scrolled:function(){},' +
    'rotateImage:function(s,d){return "rot.png"},moveNote:function(){},openNote:function(){},placeNote:function(){}};' + close +
    '<script>' + DATA.js + close + '</body></html>';
}

async function open(html, opts) {
  const f = document.createElement('iframe');
  f.style.cssText = 'width:900px;height:1000px;border:0';
  await new Promise(res => { f.onload = res; f.srcdoc = frameDoc(html, opts || { paged: true }); document.body.appendChild(f); });
  await sleep(60);
  return f;
}

function helpers(f) {
  const w = f.contentWindow, doc = f.contentDocument;
  const root = () => doc.querySelector('.whf-live') || doc.getElementById('page');
  function findText(t) {
    const walker = doc.createTreeWalker(root(), NodeFilter.SHOW_TEXT);
    let n;
    while ((n = walker.nextNode())) {
      if (n.parentNode.closest('.whf,.wl')) continue;
      const i = n.nodeValue.indexOf(t);
      if (i >= 0) return [n, i];
    }
    throw new Error('sin texto: ' + t);
  }
  function setRange(r) {
    const s = w.getSelection();
    s.removeAllRanges();
    s.addRange(r);
    doc.dispatchEvent(new Event('selectionchange'));
  }
  return {
    w, doc,
    sel(t) { const [n, i] = findText(t); const r = doc.createRange(); r.setStart(n, i); r.setEnd(n, i + t.length); setRange(r); },
    between(a, b) { const [n, i] = findText(a); const [m, j] = findText(b); const r = doc.createRange(); r.setStart(n, i); r.setEnd(m, j + b.length); setRange(r); },
    caret(t) { const [n, i] = findText(t); const r = doc.createRange(); r.setStart(n, i + t.length); r.collapse(true); setRange(r); },
    selectAllIn(selector) { const r = doc.createRange(); r.selectNodeContents(root().querySelector(selector)); setRange(r); },
    img() { const i = root().querySelector('img'); i.click(); return i; },
    sleep
  };
}

const NORMAL = 'Normal uno';
const CASES = [
  ['inicio', 'sin_tocar', async t => {}],
  ['inicio', 'fuente', async t => { t.sel(NORMAL); t.w.editor.cmd('fontName', 'Georgia'); }],
  ['inicio', 'tamano', async t => { t.sel(NORMAL); t.w.editor.fontSize(18); }],
  ['inicio', 'agrandar', async t => { t.sel('Grande g'); t.w.editor.fontSize(22); }],
  ['inicio', 'negrita_on', async t => { t.sel(NORMAL); t.w.editor.cmd('bold'); }],
  ['inicio', 'negrita_off', async t => { t.sel('Negrita run'); t.w.editor.cmd('bold'); }],
  ['inicio', 'cursiva_on', async t => { t.sel(NORMAL); t.w.editor.cmd('italic'); }],
  ['inicio', 'cursiva_off', async t => { t.sel('Cursiva k'); t.w.editor.cmd('italic'); }],
  ['inicio', 'subrayado_on', async t => { t.sel(NORMAL); t.w.editor.cmd('underline'); }],
  ['inicio', 'subrayado_off', async t => { t.sel('Subrayado x'); t.w.editor.cmd('underline'); }],
  ['inicio', 'tachado_on', async t => { t.sel(NORMAL); t.w.editor.cmd('strikeThrough'); }],
  ['inicio', 'tachado_off', async t => { t.sel('Tachado y'); t.w.editor.cmd('strikeThrough'); }],
  ['inicio', 'sup_on', async t => { t.sel(NORMAL); t.w.editor.cmd('superscript'); }],
  ['inicio', 'sup_off', async t => { t.sel('Alto z'); t.w.editor.cmd('superscript'); }],
  ['inicio', 'sub_on', async t => { t.sel(NORMAL); t.w.editor.cmd('subscript'); }],
  ['inicio', 'mayusculas', async t => { t.sel(NORMAL); t.w.editor.changeCase('upper'); }],
  ['inicio', 'minusculas', async t => { t.sel(NORMAL); t.w.editor.changeCase('lower'); }],
  ['inicio', 'color', async t => { t.sel(NORMAL); t.w.editor.color('#0000FF'); }],
  ['inicio', 'color_quitar', async t => { t.sel('Rojo w'); t.w.editor.color(null); }],
  ['inicio', 'resaltar_estandar', async t => { t.sel(NORMAL); t.w.editor.highlight('#FFFF00'); }],
  ['inicio', 'resaltar_propio', async t => { t.sel(NORMAL); t.w.editor.highlight('#F48FB1'); }],
  ['inicio', 'resaltar_quitar', async t => { t.sel('Marcado v'); t.w.editor.highlight(null); }],
  ['inicio', 'brocha', async t => { t.caret('Negrita'); t.w.editor.painter(); t.sel(NORMAL); t.w.editor.painter(); }],
  ['inicio', 'limpiar', async t => { t.between('Subrayado', 'Letra f'); t.w.editor.clear(); }],
  ['barra', 'deshacer', async t => { t.sel(NORMAL); t.w.editor.cmd('bold'); await sleep(20); t.w.editor.undo(); }],
  ['barra', 'rehacer', async t => { t.sel(NORMAL); t.w.editor.cmd('bold'); await sleep(20); t.w.editor.undo(); await sleep(20); t.w.editor.redo(); }],

  ['insertar', 'imagen_insertar', async t => { t.caret('Final'); t.w.editor.insertImage('test.png'); await sleep(80); }],
  ['insertar', 'tabla_insertar', async t => { t.caret('Final'); t.w.editor.insertTable(2, 3); }],
  ['insertar', 'enlace_seleccion', async t => { t.sel(NORMAL); t.w.editor.insertLink('https://ejemplo.cl/', ''); }],
  ['insertar', 'enlace_texto', async t => { t.caret('Final'); t.w.editor.insertLink('https://ejemplo.cl/', 'Mi sitio'); }],
  ['insertar', 'enlace_sin_esquema', async t => { t.caret('Final'); t.w.editor.insertLink('ejemplo.cl', 'Sin esquema'); }],
  ['insertar', 'salto_pagina', async t => { t.caret('Final'); t.w.editor.insertPageBreak(); }],
  ['insertar', 'linea', async t => { t.caret('Final'); t.w.editor.insertRule(); }],
  ['insertar', 'simbolo', async t => { t.caret('Final'); t.w.editor.insertText('©'); }],

  ['parrafo', 'titulo1', async t => { t.caret('Normal'); t.w.editor.block('h1'); }],
  ['parrafo', 'titulo2', async t => { t.caret('Normal'); t.w.editor.block('h2'); }],
  ['parrafo', 'titulo3', async t => { t.caret('Normal'); t.w.editor.block('h3'); }],
  ['parrafo', 'titulo_a_normal', async t => { t.caret('Titulo'); t.w.editor.block('p'); }],
  ['parrafo', 'cita', async t => { t.caret('Normal'); t.w.editor.block('blockquote'); }],
  ['parrafo', 'codigo', async t => { t.caret('Normal'); t.w.editor.block('pre'); }],
  ['parrafo', 'centrar', async t => { t.caret('Normal'); t.w.editor.cmd('justifyCenter'); }],
  ['parrafo', 'derecha', async t => { t.caret('Normal'); t.w.editor.cmd('justifyRight'); }],
  ['parrafo', 'justificar', async t => { t.caret('Normal'); t.w.editor.cmd('justifyFull'); }],
  ['parrafo', 'izquierda_de_centrado', async t => { t.caret('Centrado'); t.w.editor.cmd('justifyLeft'); }],
  ['parrafo', 'vinetas', async t => { t.caret('Normal'); t.w.editor.list('ul', 'disc'); }],
  ['parrafo', 'vineta_cuadrada', async t => { t.caret('Normal'); t.w.editor.list('ul', 'square'); }],
  ['parrafo', 'num_decimal', async t => { t.caret('Normal'); t.w.editor.list('ol', 'decimal'); }],
  ['parrafo', 'num_romana', async t => { t.caret('Normal'); t.w.editor.list('ol', 'lower-roman'); }],
  ['parrafo', 'num_letra', async t => { t.caret('Normal'); t.w.editor.list('ol', 'lower-alpha'); }],
  ['parrafo', 'lista_quitar_nueva', async t => { t.caret('Normal'); t.w.editor.list('ul', 'disc'); await sleep(20); t.caret('Normal'); t.w.editor.cmd('insertUnorderedList'); }],
  ['parrafo', 'lista_cambiar_nueva', async t => { t.caret('Normal'); t.w.editor.list('ul', 'disc'); await sleep(20); t.caret('Normal'); t.w.editor.list('ol', 'decimal'); }],
  ['parrafo', 'lista_word_a_numerada', async t => { t.caret('Item uno'); t.w.editor.list('ol', 'decimal'); }],
  ['parrafo', 'lista_word_quitar', async t => { t.caret('Item uno'); t.w.editor.cmd('insertUnorderedList'); }],
  ['parrafo', 'lista_word_quitar_seleccion', async t => { t.sel('Item uno'); t.w.editor.cmd('insertUnorderedList'); }],
  ['parrafo', 'lista_word_otro_tipo', async t => { t.caret('Item uno'); t.w.editor.cmd('insertOrderedList'); }],
  ['parrafo', 'lista_word_numerada_a_vinetas', async t => { t.caret('Numero uno'); t.w.editor.list('ul', 'square'); }],
  ['parrafo', 'lista_word_mantiene', async t => { t.caret('Item uno'); t.w.editor.insertText('X'); }],
  ['parrafo', 'sangria_mas', async t => { t.caret('Normal'); t.w.editor.indent(1); }],
  ['parrafo', 'sangria_menos', async t => { t.caret('Normal'); t.w.editor.indent(1); await sleep(20); t.caret('Normal'); t.w.editor.indent(-1); }],
  ['parrafo', 'sangria_lista_nueva', async t => { t.caret('Normal'); t.w.editor.list('ul', 'disc'); await sleep(20); t.caret('Normal'); t.w.editor.indent(1); }],
  ['parrafo', 'sangria_lista_word', async t => { t.caret('Item dos'); t.w.editor.indent(1); }],
  ['parrafo', 'interlineado', async t => { t.caret('Normal'); t.w.editor.lineSpacing(1.5); }],
  ['parrafo', 'interlineado_sencillo', async t => { t.caret('Normal'); t.w.editor.lineSpacing(1.5); await sleep(20); t.caret('Normal'); t.w.editor.lineSpacing(1); }],
  ['parrafo', 'espacio_antes', async t => { t.caret('Normal'); t.w.editor.spacing('before', 12); }],
  ['parrafo', 'espacio_despues', async t => { t.caret('Normal'); t.w.editor.spacing('after', 18); }],
  ['parrafo', 'espacio_cero', async t => { t.caret('Normal'); t.w.editor.spacing('after', 0); }],

  ['diseno', 'hoja_a4', async t => { t.w.editor.setPage(595.3, 841.9, 72, 72, 72, 72); }],
  ['diseno', 'hoja_horizontal', async t => { t.w.editor.setPage(792, 612, 72, 72, 72, 72); }],
  ['diseno', 'margenes', async t => { t.w.editor.setPage(612, 792, 36, 40, 50, 60); }],

  ['revisar', 'reemplazar', async t => { t.w.finder.search('palabra'); t.w.finder.replace('termino'); }],
  ['revisar', 'reemplazar_todo', async t => { t.w.finder.search('palabra'); t.w.finder.replaceAll('termino'); }],

  ['imagen', 'img_porcentaje', async t => { t.img(); await sleep(20); t.w.imageTools.size(50); }],
  ['imagen', 'img_100', async t => { t.img(); await sleep(20); t.w.imageTools.size(100); }],
  ['imagen', 'img_tamano', async t => { t.img(); await sleep(20); t.w.imageTools.setSize(150, 50); }],
  ['imagen', 'img_alinear', async t => { t.img(); await sleep(20); t.w.imageTools.align('center'); }],
  ['imagen', 'img_girar', async t => { t.img(); await sleep(20); t.w.imageTools.rotate(90); await sleep(40); }],
  ['imagen', 'img_reemplazar', async t => { t.img(); await sleep(20); t.w.imageTools.replace('other.png'); await sleep(80); }],
  ['imagen', 'img_eliminar', async t => { t.img(); await sleep(20); t.w.imageTools.remove(); }],

  ['tabla', 'tabla_texto', async t => { t.caret('A1'); t.w.editor.insertText(' nuevo'); }],
  ['tabla', 'tabla_negrita_celda', async t => { t.sel('B2'); t.w.editor.cmd('bold'); }],
  ['tabla', 'tabla_fila_arriba', async t => { t.caret('A3'); t.w.tableTools.insertRow(false); }],
  ['tabla', 'tabla_fila_abajo', async t => { t.caret('A3'); t.w.tableTools.insertRow(true); }],
  ['tabla', 'tabla_col_izq', async t => { t.caret('A1'); t.w.tableTools.insertCol(false); }],
  ['tabla', 'tabla_col_der', async t => { t.caret('A1'); t.w.tableTools.insertCol(true); }],
  ['tabla', 'tabla_borrar_fila', async t => { t.caret('A2'); t.w.tableTools.deleteRow(); }],
  ['tabla', 'tabla_borrar_col', async t => { t.caret('B1'); t.w.tableTools.deleteCol(); }],
  ['tabla', 'tabla_borrar', async t => { t.caret('A1'); t.w.tableTools.deleteTable(); }],
  ['tabla', 'tabla_combinar', async t => { t.between('A1', 'B1'); t.w.tableTools.merge(); }],
  ['tabla', 'tabla_combinar_vertical', async t => { t.between('A1', 'A2'); t.w.tableTools.merge(); }],
  ['tabla', 'tabla_dividir_col', async t => { t.caret('B2'); t.w.tableTools.split(2, 1); }],
  ['tabla', 'tabla_dividir_fila', async t => { t.caret('B2'); t.w.tableTools.split(1, 2); }],
  ['tabla', 'tabla_sombra', async t => { t.caret('A1'); t.w.tableTools.shade('#FFE699'); }],
  ['tabla', 'tabla_sombra_quitar', async t => { t.caret('A1'); t.w.tableTools.shade('#FFE699'); await sleep(20); t.caret('A1'); t.w.tableTools.shade(''); }],
  ['tabla', 'tabla_sin_bordes', async t => { t.caret('A1'); t.w.tableTools.borders(false); }],
  ['tabla', 'tabla_con_bordes', async t => { t.caret('A1'); t.w.tableTools.borders(false); await sleep(20); t.caret('A1'); t.w.tableTools.borders(true); }],
  ['tabla', 'tabla_centro', async t => { t.caret('A1'); t.w.tableTools.valign('middle'); }],
  ['tabla', 'tabla_abajo', async t => { t.caret('A1'); t.w.tableTools.valign('bottom'); }],
  ['tabla', 'tabla_igualar', async t => { t.caret('A1'); t.w.tableTools.insertCol(true); await sleep(20); t.caret('A1'); t.w.tableTools.distribute(); }],

  ['encabezado', 'encabezado_texto', async t => { t.w.headerFooter.enter('h', 0); await sleep(20); t.w.editor.insertText('XYZ'); await sleep(20); t.w.headerFooter.exit(); }],
  ['encabezado', 'encabezado_negrita', async t => { t.w.headerFooter.enter('h', 0); await sleep(20); t.sel('Encabezado base'); t.w.editor.cmd('bold'); await sleep(20); t.w.headerFooter.exit(); }],
  ['encabezado', 'encabezado_tres', async t => { t.w.headerFooter.enter('h', 0); await sleep(20); t.w.headerFooter.preset('three', 'Mi doc', null); await sleep(20); t.w.headerFooter.exit(); }],
  ['encabezado', 'encabezado_titulo', async t => { t.w.headerFooter.enter('h', 0); await sleep(20); t.w.headerFooter.preset('title', 'Mi doc', null); await sleep(20); t.w.headerFooter.exit(); }],
  ['encabezado', 'encabezado_logo', async t => { t.w.headerFooter.enter('h', 0); await sleep(20); t.w.headerFooter.preset('logo', 'Mi doc', 'other.png'); await sleep(80); t.w.headerFooter.exit(); }],
  ['encabezado', 'encabezado_vacio', async t => { t.w.headerFooter.enter('h', 0); await sleep(20); t.w.headerFooter.preset('blank', 'Mi doc', null); await sleep(20); t.w.headerFooter.exit(); }],
  ['encabezado', 'encabezado_tabla', async t => { t.w.headerFooter.enter('h', 0); await sleep(20); t.w.editor.insertTable(1, 2); await sleep(20); t.w.headerFooter.exit(); }],
  ['encabezado', 'encabezado_imagen', async t => { t.w.headerFooter.enter('h', 0); await sleep(20); t.w.editor.insertImage('other.png'); await sleep(80); t.w.headerFooter.exit(); }],
  ['encabezado', 'pie_pagina_num', async t => { t.w.headerFooter.number('f', 'right', 'nofm'); }],
  ['encabezado', 'num_en_cursor', async t => { t.caret('Final'); t.w.headerFooter.number('c', 'left', 'n'); }],
  ['encabezado', 'num_quitar', async t => { t.w.headerFooter.number('f', 'right', 'n'); await sleep(20); t.w.headerFooter.number('', 'right', 'n'); }],
  ['encabezado', 'primera_diferente', async t => { t.w.headerFooter.options(true, false); await sleep(20); t.w.headerFooter.enter('h', 0); await sleep(20); t.w.editor.insertText('Portada'); await sleep(20); t.w.headerFooter.exit(); }],
  ['encabezado', 'pares_impares', async t => { t.w.editor.setPage(612, 300, 72, 72, 72, 72); await sleep(120); t.w.headerFooter.options(false, true); await sleep(120); t.w.headerFooter.enter('h', 1); await sleep(20); t.w.editor.insertText('Par'); await sleep(20); t.w.headerFooter.exit(); }],
  ['extra', 'fidelidad_sample', async t => { t.doc.querySelectorAll('[data-b]').forEach(e => e.setAttribute('data-d', '1')); }, { html: DATA.sample, paged: false, source: 'sample' }],
  ['extra', 'salto_medio_parrafo', async t => { t.caret('Normal uno'); t.w.editor.insertPageBreak(); }],
  ['extra', 'salto_fin_parrafo', async t => { t.caret('dos tres'); t.w.editor.insertPageBreak(); }],
  ['extra', 'img_nueva_100_horizontal', async t => { t.w.editor.setPage(792, 612, 72, 72, 72, 72); await sleep(30); t.caret('Final'); t.w.editor.insertImage('test.png'); await sleep(80); const i = t.doc.querySelector('img:not([data-o])'); i.click(); await sleep(20); t.w.imageTools.size(100); }],
  ['extra', 'deshacer_parcial', async t => { t.sel(NORMAL); t.w.editor.cmd('bold'); await sleep(20); t.sel('Final'); t.w.editor.cmd('bold'); await sleep(20); t.w.editor.undo(); }],
  ['extra', 'deshacer_texto_previo', async t => { t.caret('Final'); t.w.editor.insertText('ABC'); await sleep(20); t.sel(NORMAL); t.w.editor.cmd('italic'); await sleep(20); t.w.editor.undo(); }],
  ['extra', 'lista_dos_items_sangria', async t => { t.between('Normal uno', 'resto'); t.w.editor.list('ul', 'disc'); await sleep(20); t.caret('resto'); t.w.editor.indent(1); }],
  ['extra', 'lista_sangria_y_vuelta', async t => { t.caret('Normal'); t.w.editor.list('ul', 'disc'); await sleep(20); t.caret('Normal'); t.w.editor.indent(1); await sleep(20); t.caret('Normal'); t.w.editor.indent(-1); }],
  ['extra', 'img_nueva_100', async t => { t.caret('Final'); t.w.editor.insertImage('test.png'); await sleep(80); const i = t.doc.querySelector('img:not([data-o])'); i.click(); await sleep(20); t.w.imageTools.size(100); }],
  ['extra', 'img_girar_100', async t => { t.img(); await sleep(20); t.w.imageTools.rotate(90); await sleep(40); t.w.imageTools.size(100); }],
  ['extra', 'seleccion_varios_parrafos', async t => { t.between('Normal uno', 'resto'); t.w.editor.color('#00FF00'); }],
  ['extra', 'color_varias_celdas', async t => { t.between('A1', 'B1'); t.w.editor.color('#00FF00'); }],
  ['extra', 'enter_parte_parrafo', async t => { t.caret('Normal uno'); t.w.document.execCommand('insertParagraph'); t.w.document.dispatchEvent(new Event('input')); await sleep(20); t.w.editor.insertText('Z'); }],
  ['extra', 'titulo_con_negrita_previa', async t => { t.caret('Negrita'); t.w.editor.block('h2'); }],
  ['extra', 'alinear_lista_word', async t => { t.caret('Item uno'); t.w.editor.cmd('justifyCenter'); }],
  ['extra', 'interlineado_lista_nueva', async t => { t.caret('Normal'); t.w.editor.list('ul', 'disc'); await sleep(20); t.caret('Normal'); t.w.editor.lineSpacing(2); }],
  ['extra', 'lectura_resaltar', async t => { t.w.editor.setReadOnly(true); await sleep(20); t.sel(NORMAL); t.w.editor.highlight('#FFFF00'); }],
  ['extra', 'borrar_parrafo', async t => { t.between('Normal uno', 'tres'); t.w.document.execCommand('delete'); }],
  ['extra', 'tabla_ops_y_texto', async t => { t.between('A1', 'B1'); t.w.tableTools.merge(); await sleep(20); t.caret('C2'); t.w.editor.insertText('!'); await sleep(20); t.caret('C2'); t.w.tableTools.insertRow(true); }],
  ['extra', 'pagina_sin_margenes_tabla', async t => { t.w.editor.setPage(792, 612, 36, 36, 36, 36); await sleep(20); t.caret('A1'); t.w.tableTools.insertRow(true); }],
  ['encabezado', 'numeracion', async t => { t.w.headerFooter.numbering('i', 5); }],
  ['encabezado', 'distancia', async t => { t.w.headerFooter.distance(50, 20); }],
  ['encabezado', 'encabezado_a_pie', async t => { t.w.headerFooter.enter('h', 0); await sleep(20); t.w.headerFooter.swap(); await sleep(20); t.w.editor.insertText('XYZ'); await sleep(20); t.w.headerFooter.exit(); }],

  ['nota', 'nota_exporta_formato', async t => { t.sel('Hola'); t.w.editor.cmd('bold'); await sleep(20); t.sel('mundo'); t.w.editor.color('#FF0000'); }, { html: '<p>Hola mundo</p>', paged: false, blank: true }],
];

(async () => {
  const out = [];
  for (const [group, name, run, o] of CASES) {
    if (DATA.only && DATA.only !== name) continue;
    if (DATA.only && o) o.paged = true;
    const rec = { group, name, error: null, blank: !!(o && o.blank), source: o && o.source || null };
    let f;
    try {
      const opts = { paged: !o || o.paged !== false };
      f = await open(o && o.html ? o.html : DATA.fixture, opts);
      const t = helpers(f);
      await run(t);
      await sleep(70);
      t.w.flushSave();
      await sleep(10);
      const ms = t.w.__models;
      rec.model = ms[ms.length - 1] || '';
      rec.saves = t.w.__saves.length;
      if (t.w.__err.length) rec.error = t.w.__err.join('; ');
      // El HTML guardado debe dar el mismo modelo al reabrir el documento (la exportación usa model.json)
      const saved = t.w.__saves[t.w.__saves.length - 1];
      if (saved && name !== 'sin_tocar') {
        const g = await open(saved, opts);
        await sleep(20);
        const again = g.contentWindow.__models;
        rec.reopenSame = again[again.length - 1] === rec.model;
        if (!rec.reopenSame) rec.reopenModel = again[again.length - 1];
        g.remove();
      }
    } catch (e) {
      rec.error = String(e && e.message || e);
    }
    if (f) f.remove();
    out.push(rec);
  }
  document.getElementById('results').textContent = btoa(unescape(encodeURIComponent(JSON.stringify(out))));
  document.title = 'DONE';
})();
