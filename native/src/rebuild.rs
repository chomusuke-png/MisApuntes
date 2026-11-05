//! Modelo del editor (JSON) + documento original → documento de WordCraft listo para guardar.
//!
//! Los bloques que el editor no tocó vuelven tal cual del original (`k`). Los editados se rehacen
//! partiendo del párrafo de origen: conservan su estilo, sangrías, numeración y sección, sus
//! objetos (imágenes, notas, campos…) y el formato exacto de cada trozo de texto que no cambió de
//! aspecto. Solo lo que cambió a la vista se escribe como formato directo.

use std::collections::{HashMap, HashSet};
use std::path::Path;
use std::sync::Arc;

use serde::Deserialize;
use wordcraft_doc::numbering::ListKind;
use wordcraft_doc::para::{Float, InlineObject};
use wordcraft_doc::props::{Border, Borders, Highlight, LineSpacing, NumRef, Underline, VertAlign};
use wordcraft_doc::styles::StyleSheet;
use wordcraft_doc::{Align, Block, Blocks, CharProps, Document, Paragraph, Rgb, Table, TextColor};

use crate::render::{BlockKind, Look, block_kind, image_ext, para_style};

#[derive(Deserialize)]
pub struct Model {
    pub blocks: Vec<MBlock>,
    /// Tamaño y márgenes de la hoja elegidos en el editor (puntos).
    pub page: Option<MPage>,
    /// Encabezados y pies cambiados en el editor (solo los que se tocaron), por su lugar:
    /// "hd"/"fd" todas las páginas (o las impares), "h1"/"f1" la primera, "he"/"fe" las pares.
    #[serde(default)]
    pub hf: Option<HashMap<String, MStory>>,
    /// Primera página diferente (tp) y pares e impares diferentes (eo), si se cambiaron.
    pub hfo: Option<MHfOptions>,
}

#[derive(Deserialize)]
pub struct MHfOptions {
    #[serde(default)]
    pub tp: bool,
    #[serde(default)]
    pub eo: bool,
    /// Numeración de páginas: "1", "i", "I", "a" o "A".
    pub fmt: Option<String>,
    /// Número de la primera página.
    pub start: Option<u32>,
    /// Distancia del encabezado y del pie al borde de la hoja, en puntos.
    pub hd: Option<f32>,
    pub fd: Option<f32>,
}

/// Encabezado o pie: bloques como los del cuerpo (las rutas son las de ese encabezado).
#[derive(Deserialize)]
pub struct MStory {
    #[serde(default)]
    pub blocks: Vec<MBlock>,
}

#[derive(Deserialize)]
pub struct MPage {
    pub w: f32,
    pub h: f32,
    pub mt: Option<f32>,
    pub mr: Option<f32>,
    pub mb: Option<f32>,
    pub ml: Option<f32>,
}

#[derive(Deserialize)]
pub enum MBlock {
    /// Bloque sin cambios: su ruta en el original.
    #[serde(rename = "k")]
    Keep(String),
    #[serde(rename = "p")]
    Para(MPara),
    #[serde(rename = "t")]
    Table(MTable),
}

#[derive(Deserialize)]
pub struct MPara {
    pub src: Option<String>,
    #[serde(default)]
    pub tag: String,
    /// "ul" u "ol" si el editor lo puso en una lista.
    pub list: Option<String>,
    #[serde(default)]
    pub list_id: u32,
    #[serde(default)]
    pub level: u8,
    pub align: Option<String>,
    /// Línea horizontal insertada en el editor: párrafo vacío con borde inferior.
    #[serde(default)]
    pub rule: bool,
    /// Se le quitó la lista de Word en el editor.
    #[serde(default)]
    pub nonum: bool,
    /// Tipo de viñeta o numeración (list-style-type de CSS).
    pub list_style: Option<String>,
    /// Interlineado (múltiplo de sencillo), espacio antes/después y sangría izquierda en puntos.
    pub lh: Option<f32>,
    pub sb: Option<f32>,
    pub sa: Option<f32>,
    pub il: Option<f32>,
    #[serde(default)]
    pub segs: Vec<Seg>,
}

#[derive(Deserialize, Default)]
pub struct Seg {
    /// Texto.
    pub t: Option<String>,
    /// Run de origen ("ruta#índice").
    pub r: Option<String>,
    pub f: Option<Fmt>,
    /// Texto oculto o borrado con control de cambios: vuelve igual que en el original.
    #[serde(default)]
    pub hid: bool,
    /// Objeto de origen ("ruta#índice").
    pub o: Option<String>,
    /// Imagen nueva (archivo junto al HTML), con su tamaño en puntos.
    pub img: Option<String>,
    pub w: Option<f32>,
    pub ih: Option<f32>,
    #[serde(default)]
    pub br: bool,
    /// 1 = salto de página, 2 = salto de columna.
    pub pb: Option<u8>,
    /// Campo nuevo: "PAGE" (número de página) o "NUMPAGES" (total).
    pub fld: Option<String>,
}

/// Aspecto que muestra el editor; `None` = lo que diga el estilo del párrafo.
#[derive(Deserialize, Default)]
pub struct Fmt {
    pub b: Option<bool>,
    pub i: Option<bool>,
    pub u: Option<bool>,
    pub s: Option<bool>,
    pub va: Option<String>,
    pub c: Option<String>,
    pub bg: Option<String>,
    pub font: Option<String>,
    pub sz: Option<f32>,
    /// Destino del enlace ("" = sin enlace).
    pub link: Option<String>,
}

#[derive(Deserialize)]
pub struct MTable {
    pub src: Option<String>,
    #[serde(default)]
    pub cells: Vec<MCell>,
    /// Tabla entera fila por fila (nueva o cambiada con las herramientas de tabla).
    pub rows: Option<Vec<MRow>>,
    /// Anchos de las columnas de la cuadrícula, en puntos.
    #[serde(default)]
    pub grid: Vec<f32>,
    /// Bordes puestos o quitados desde la barra.
    pub bd: Option<bool>,
}

#[derive(Deserialize)]
pub struct MRow {
    /// Fila del original de la que sale el formato.
    pub tr: Option<usize>,
    #[serde(default)]
    pub gb: u32,
    #[serde(default)]
    pub cells: Vec<MGridCell>,
}

#[derive(Deserialize)]
pub struct MGridCell {
    /// Celda del original ("fila:celda") de la que sale el formato.
    pub c: Option<String>,
    #[serde(default = "one")]
    pub s: u32,
    /// 0 sola, 1 empieza una combinación vertical, 2 la continúa.
    #[serde(default)]
    pub m: u8,
    #[serde(default)]
    pub blocks: Vec<MBlock>,
    /// bg y va cambiaron (o la celda es nueva).
    #[serde(default)]
    pub set: bool,
    pub bg: Option<String>,
    pub va: Option<String>,
}

fn one() -> u32 {
    1
}

#[derive(Deserialize)]
pub struct MCell {
    pub c: String,
    #[serde(default)]
    pub blocks: Vec<MBlock>,
}

struct Ctx<'a> {
    orig: &'a Document,
    media_dir: &'a Path,
    lists: HashMap<(u32, bool), u32>,
    /// Cuántas veces queda por aparecer cada párrafo de origen (Enter duplica la ruta).
    remaining: HashMap<String, usize>,
}

pub fn rebuild(orig: &Document, model: &Model, media_dir: &Path) -> Document {
    let mut doc = orig.clone();
    let mut remaining = HashMap::new();
    count_sources(&model.blocks, &mut remaining);
    let mut ctx = Ctx { orig, media_dir, lists: HashMap::new(), remaining };
    let mut body = build_blocks(&mut ctx, &mut doc, &model.blocks);
    if body.is_empty() {
        body.push(Arc::new(Block::Para(Paragraph::new())));
    }
    doc.body = body;
    repair_markers(&mut doc.body);
    if let Some(p) = &model.page {
        apply_page(&mut doc, p);
    }
    if let Some(o) = &model.hfo {
        doc.last_section.title_page = o.tp;
        doc.settings.even_odd_headers = o.eo;
        apply_numbering(&mut doc.last_section, o);
    }
    if let Some(hf) = &model.hf {
        apply_headers(&mut doc, orig, media_dir, hf);
    }
    doc
}

fn page_field(instr: &str) -> InlineObject {
    InlineObject::Field { instr: instr.into(), result: "1".into(), locked: false, dirty: false, code: false, form: String::new() }
}

/// Encabezados y pies escritos en el editor. Cada uno se rehace como el cuerpo, con su propio
/// original (el encabezado de ese lugar), así lo no tocado se copia tal cual.
fn apply_headers(doc: &mut Document, orig: &Document, media_dir: &Path, hf: &HashMap<String, MStory>) {
    use wordcraft_doc::PartKind;
    let mut ids: Vec<&String> = hf.keys().collect();
    ids.sort();
    for id in ids {
        let story = &hf[id];
        let mut chars = id.chars();
        let (kind, variant) = match (chars.next(), chars.next()) {
            (Some('h'), Some(v)) => (PartKind::Header, v),
            (Some('f'), Some(v)) => (PartKind::Footer, v),
            _ => continue,
        };
        let slot = |d: &Document| {
            let set = if kind == PartKind::Header { d.last_section.headers } else { d.last_section.footers };
            match variant {
                'd' => Some(set.default),
                '1' => Some(set.first),
                'e' => Some(set.even),
                _ => None,
            }
        };
        let Some(current) = slot(doc) else { continue };
        let source = slot(orig).flatten().and_then(|i| orig.parts.get(&i)).map(|p| p.blocks.clone()).unwrap_or_default();
        let mut hdoc = orig.clone();
        hdoc.body = source;
        let mut remaining = HashMap::new();
        count_sources(&story.blocks, &mut remaining);
        let mut ctx = Ctx { orig: &hdoc, media_dir, lists: HashMap::new(), remaining };
        let mut blocks = build_blocks(&mut ctx, doc, &story.blocks);
        repair_markers(&mut blocks);
        let empty = blocks.iter().all(|b| b.as_para().is_some_and(|p| p.objects.is_empty() && p.plain_text().trim().is_empty()));
        // Sin nada escrito: el de todas las páginas se quita; el de la primera queda en blanco
        let id = if empty && variant == 'd' {
            None
        } else {
            if blocks.is_empty() {
                blocks.push(Arc::new(Block::Para(Paragraph::new())));
            }
            match current.filter(|i| doc.parts.contains_key(i)) {
                Some(i) => {
                    if let Some(part) = doc.parts.get_mut(&i) {
                        part.blocks = blocks;
                    }
                    Some(i)
                }
                None => Some(doc.add_part(kind, blocks)),
            }
        };
        let set = if kind == PartKind::Header { &mut doc.last_section.headers } else { &mut doc.last_section.footers };
        match variant {
            'd' => set.default = id,
            'e' => set.even = id,
            _ => set.first = id,
        }
    }
}

/// Hoja elegida en el editor. Se aplica a la sección final (la que muestra el editor).
fn apply_page(doc: &mut Document, p: &MPage) {
    let ok = |v: f32| v.is_finite() && v > 0.0;
    if !ok(p.w) || !ok(p.h) {
        return;
    }
    let s = &mut doc.last_section;
    s.page_w = p.w.clamp(72.0, 1584.0);
    s.page_h = p.h.clamp(72.0, 1584.0);
    s.landscape = s.page_w > s.page_h;
    let margin = |v: Option<f32>, cur: f32| v.filter(|m| m.is_finite() && *m >= 0.0).map_or(cur, |m| m.min(720.0));
    s.margin_top = margin(p.mt, s.margin_top);
    s.margin_right = margin(p.mr, s.margin_right);
    s.margin_bottom = margin(p.mb, s.margin_bottom);
    s.margin_left = margin(p.ml, s.margin_left);
}

fn count_sources(blocks: &[MBlock], out: &mut HashMap<String, usize>) {
    for b in blocks {
        match b {
            MBlock::Para(p) => {
                if let Some(s) = &p.src {
                    *out.entry(s.clone()).or_insert(0) += 1;
                }
            }
            MBlock::Table(t) => {
                t.cells.iter().for_each(|c| count_sources(&c.blocks, out));
                t.rows.iter().flatten().flat_map(|r| &r.cells).for_each(|c| count_sources(&c.blocks, out));
            }
            MBlock::Keep(_) => {}
        }
    }
}

fn build_blocks(ctx: &mut Ctx, doc: &mut Document, blocks: &[MBlock]) -> Blocks {
    let mut out: Blocks = Vec::new();
    for b in blocks {
        match b {
            MBlock::Keep(path) => {
                if let Some(found) = find_block(&ctx.orig.body, path) {
                    out.push(found);
                }
            }
            MBlock::Para(mp) => out.push(Arc::new(Block::Para(build_para(ctx, doc, mp)))),
            MBlock::Table(mt) if mt.rows.is_some() => {
                if let Some(t) = grid_table(ctx, doc, mt) {
                    out.push(Arc::new(Block::Table(t)));
                }
            }
            MBlock::Table(mt) => {
                let src = mt.src.as_deref().and_then(|s| find_block(&ctx.orig.body, s));
                match src.as_deref() {
                    Some(Block::Table(t)) => {
                        let mut t = t.clone();
                        for mc in &mt.cells {
                            let mut it = mc.c.split(':').map(|n| n.parse::<usize>().ok());
                            let (Some(Some(r)), Some(Some(c))) = (it.next(), it.next()) else { continue };
                            let mut blocks = build_blocks(ctx, doc, &mc.blocks);
                            if blocks.is_empty() {
                                blocks.push(Arc::new(Block::Para(Paragraph::new())));
                            }
                            if let Some(cell) = t.rows.get_mut(r).and_then(|row| row.cells.get_mut(c)) {
                                cell.blocks = blocks;
                            }
                        }
                        out.push(Arc::new(Block::Table(t)));
                    }
                    // Tabla nueva (insertada en el editor o pegada)
                    _ => {
                        if let Some(t) = new_table(ctx, doc, mt) {
                            out.push(Arc::new(Block::Table(t)));
                        }
                    }
                }
            }
        }
    }
    out
}

fn grid_style(doc: &mut Document) {
    if doc.styles.get("TableGrid").is_none() {
        if let Some(st) = StyleSheet::builtin().get("TableGrid").cloned() {
            doc.styles.upsert(st);
        }
    }
}

/// Tabla armada desde lo que se ve: filas, celdas combinadas y anchos del editor; el formato de
/// la tabla, de cada fila y de cada celda se copia de su origen en el original.
fn grid_table(ctx: &mut Ctx, doc: &mut Document, mt: &MTable) -> Option<Table> {
    use wordcraft_doc::props::{BorderStyle, VAlign, VMerge};
    use wordcraft_doc::table::{MAX_COLS, MAX_ROWS};
    use wordcraft_doc::{Cell, Row};

    let rows = mt.rows.as_deref().filter(|r| !r.is_empty())?;
    let orig = match mt.src.as_deref().and_then(|s| find_block(&ctx.orig.body, s)).as_deref() {
        Some(Block::Table(t)) => Some(t.clone()),
        _ => None,
    };
    let used = |r: &MRow| r.gb as usize + r.cells.iter().map(|c| c.s.clamp(1, MAX_COLS as u32) as usize).sum::<usize>();
    let ncols = rows.iter().map(used).max()?.clamp(1, MAX_COLS);
    let s = &doc.last_section;
    let width = (s.page_w - s.margin_left - s.margin_right).max(72.0);

    let mut t = match &orig {
        Some(o) => Table { props: o.props.clone(), grid: Vec::new(), rows: Vec::new(), controls: o.controls.clone() },
        None => {
            grid_style(doc);
            let mut t = Table::new(1, 1, width);
            t.props.borders = Some(Borders::all(Border::single(0.5)));
            t.rows.clear();
            t
        }
    };
    let ok = |w: &f32| w.is_finite() && *w > 0.0;
    t.grid = if mt.grid.len() == ncols && mt.grid.iter().all(ok) {
        mt.grid.iter().map(|w| w.clamp(6.0, 1584.0)).collect()
    } else {
        vec![(width / ncols as f32).max(6.0); ncols]
    };
    let span_w = |grid: &[f32], a: usize, n: usize| grid.iter().skip(a).take(n).sum::<f32>();

    let mut seen = HashSet::new();
    for mr in rows.iter().take(MAX_ROWS) {
        let mut row = Row::default();
        if let Some(or) = mr.tr.and_then(|i| orig.as_ref()?.rows.get(i)) {
            row.props = or.props.clone();
            // Una fila copiada de otra no repite como encabezado
            if !seen.insert(mr.tr) {
                row.props.header = false;
            }
        }
        let gb = (mr.gb as usize).min(ncols - 1);
        let after = ncols.saturating_sub(used(mr).min(ncols));
        row.props.grid_before = gb as u32;
        row.props.w_before = (gb > 0).then(|| span_w(&t.grid, 0, gb));
        row.props.grid_after = after as u32;
        row.props.w_after = (after > 0).then(|| span_w(&t.grid, ncols - after, after));

        let mut g = gb;
        for mc in &mr.cells {
            if g >= ncols {
                break;
            }
            let span = (mc.s.max(1) as usize).min(ncols - g);
            let mut cell = Cell::empty();
            if let Some(oc) = mc.c.as_deref().and_then(|c| orig_cell(orig.as_ref()?, c)) {
                cell.props = oc.props.clone();
            }
            cell.props.span = span as u32;
            cell.props.width = Some(span_w(&t.grid, g, span));
            cell.props.width_pct = None;
            cell.props.vmerge = match mc.m {
                1 => VMerge::Restart,
                2 => VMerge::Continue,
                _ => VMerge::None,
            };
            if mc.set {
                cell.props.shading = mc.bg.as_deref().and_then(Rgb::parse);
                cell.props.valign = Some(match mc.va.as_deref() {
                    Some("middle") => VAlign::Center,
                    Some("bottom") => VAlign::Bottom,
                    _ => VAlign::Top,
                });
            }
            if mc.m != 2 {
                let blocks = build_blocks(ctx, doc, &mc.blocks);
                if !blocks.is_empty() {
                    cell.blocks = blocks;
                }
            }
            row.cells.push(cell);
            g += span;
        }
        if !row.cells.is_empty() {
            t.rows.push(row);
        }
    }
    if t.rows.is_empty() {
        return None;
    }
    if let Some(on) = mt.bd {
        let line = if on { Border::single(0.5) } else { Border { style: BorderStyle::None, width: 0.0, color: None, space: 0.0 } };
        t.props.borders = Some(Borders::all(line));
        // Los bordes de cada celda ganarían a los de la tabla
        t.rows.iter_mut().flat_map(|r| r.cells.iter_mut()).for_each(|c| c.props.borders = None);
    }
    Some(t)
}

fn orig_cell<'a>(t: &'a Table, rc: &str) -> Option<&'a wordcraft_doc::Cell> {
    let (r, c) = rc.split_once(':')?;
    t.rows.get(r.parse::<usize>().ok()?)?.cells.get(c.parse::<usize>().ok()?)
}

/// Tabla con bordes simples (estilo "Tabla con cuadrícula") del ancho del texto de la hoja.
fn new_table(ctx: &mut Ctx, doc: &mut Document, mt: &MTable) -> Option<Table> {
    let cells: Vec<(usize, usize, &MCell)> = mt
        .cells
        .iter()
        .filter_map(|mc| {
            let (r, c) = mc.c.split_once(':')?;
            Some((r.parse().ok()?, c.parse().ok()?, mc))
        })
        .collect();
    let rows = cells.iter().map(|(r, _, _)| r + 1).max()?;
    let cols = cells.iter().map(|(_, c, _)| c + 1).max()?;
    let s = &doc.last_section;
    let width = (s.page_w - s.margin_left - s.margin_right).max(72.0);
    let mut t = Table::new(rows, cols, width);
    if doc.styles.get("TableGrid").is_none() {
        if let Some(st) = StyleSheet::builtin().get("TableGrid").cloned() {
            doc.styles.upsert(st);
        }
    }
    t.props.borders = Some(Borders::all(Border::single(0.5)));
    for (r, c, mc) in cells {
        let mut blocks = build_blocks(ctx, doc, &mc.blocks);
        if blocks.is_empty() {
            blocks.push(Arc::new(Block::Para(Paragraph::new())));
        }
        if let Some(cell) = t.rows.get_mut(r).and_then(|row| row.cells.get_mut(c)) {
            cell.blocks = blocks;
        }
    }
    Some(t)
}

fn build_para(ctx: &mut Ctx, doc: &mut Document, mp: &MPara) -> Paragraph {
    let src = mp.src.as_deref().and_then(|s| find_block(&ctx.orig.body, s));
    let src_para = src.as_deref().and_then(Block::as_para);

    let mut p = Paragraph::new();
    if let Some(sp) = src_para {
        p.props = sp.props.clone();
        p.mark = sp.mark.clone();
        // Si Enter partió el párrafo, el salto de sección queda en el último trozo
        let last = mp.src.as_ref().and_then(|s| ctx.remaining.get_mut(s)).map(|n| {
            *n = n.saturating_sub(1);
            *n == 0
        });
        if last.unwrap_or(true) {
            p.section = sp.section.clone();
        }
    }

    // Título, cita o párrafo normal
    let current = block_kind(doc, &p);
    let wanted = match mp.tag.as_str() {
        "blockquote" => BlockKind::Quote,
        t => t.strip_prefix('h').and_then(|n| n.parse::<u8>().ok()).filter(|n| (1..=6).contains(n)).map_or(BlockKind::Plain, BlockKind::Heading),
    };
    if wanted != current {
        p.props.outline_level = None;
        p.props.style = match wanted {
            BlockKind::Heading(n) => Some(heading_style(doc, n)),
            BlockKind::Quote => Some(builtin_style(doc, "Quote")),
            BlockKind::Plain => None,
        };
    }

    // Listas del editor. Las de Word que no se tocaron llegan como párrafos, con su numeración:
    // un <li> con origen es un párrafo que se puso en una lista o se cambió de tipo en el editor.
    if let Some(list) = mp.list.as_deref() {
        let kind = list_kind(list, mp.list_style.as_deref());
        let key = (mp.list_id, list == "ol");
        let num = match ctx.lists.get(&key) {
            Some(n) => *n,
            None => {
                let n = doc.numbering.add_list(kind);
                ctx.lists.insert(key, n);
                n
            }
        };
        p.props.numbering = Some(NumRef { num, level: mp.level.min(8) });
    } else if mp.nonum {
        // num 0: sin lista, también si la numeración venía del estilo del párrafo
        p.props.numbering = Some(NumRef { num: 0, level: 0 });
    }

    // Interlineado, espacio antes/después y sangría: solo si cambiaron a la vista
    let rp = doc.styles.resolve_para(&p.props);
    if let Some(m) = mp.lh.filter(|m| m.is_finite() && *m > 0.0) {
        let same = matches!(rp.line_spacing, LineSpacing::Multiple(now) if (now - m).abs() < 0.03);
        if !same {
            p.props.line_spacing = Some(LineSpacing::Multiple((m * 100.0).round() / 100.0));
        }
    }
    let before = if rp.before_auto { 14.0 } else { rp.space_before };
    if let Some(sb) = mp.sb.filter(|v| v.is_finite() && (v - before).abs() > 0.5) {
        p.props.space_before = Some(sb.max(0.0));
        p.props.space_before_auto = Some(false);
    }
    let after = if rp.after_auto { 14.0 } else { rp.space_after };
    if let Some(sa) = mp.sa.filter(|v| v.is_finite() && (v - after).abs() > 0.5) {
        p.props.space_after = Some(sa.max(0.0));
        p.props.space_after_auto = Some(false);
    }
    if let Some(il) = mp.il.filter(|v| v.is_finite() && (v - rp.indent_left).abs() > 0.5) {
        p.props.indent_left = Some(il.max(0.0));
        p.props.indent_left_chars = None;
    }

    // Alineación
    if let Some(a) = mp.align.as_deref().and_then(parse_align) {
        let now = doc.styles.resolve_para(&p.props).align;
        let now = if now == Align::Distribute { Align::Justify } else { now };
        if a != now {
            p.props.align = Some(a);
        }
    }

    if mp.rule {
        let mut borders = p.props.borders.clone().unwrap_or_default();
        borders.bottom = Some(Border::single(0.75));
        p.props.borders = Some(borders);
    }

    let style = para_style(&p);
    let keep_nbsp = src_para.is_some_and(|sp| sp.text.contains('\u{00A0}'));
    let mut last_props = CharProps::default();
    for seg in &mp.segs {
        let end = p.len();
        if let Some(text) = &seg.t {
            let mut text = text.replace('\u{200B}', "");
            if !keep_nbsp {
                text = text.replace('\u{00A0}', " ");
            }
            text = text.replace('\u{FFFC}', "");
            if text.is_empty() {
                continue;
            }
            let props = run_props(ctx, doc, &style, seg);
            let _ = p.insert_text(end, &text, &props);
            last_props = props;
        } else if let Some(oref) = &seg.o {
            if let Some(mut obj) = find_object(&ctx.orig.body, oref) {
                // Imagen a la que se le cambió el tamaño en el editor
                if let InlineObject::Image { w, h, .. } = &mut obj {
                    if let (Some(nw), Some(nh)) = (seg.w, seg.ih) {
                        if nw > 0.0 && nh > 0.0 && ((nw - *w).abs() > 0.5 || (nh - *h).abs() > 0.5) {
                            *w = nw;
                            *h = nh;
                        }
                    }
                }
                let _ = p.insert_object(end, obj, &last_props);
            }
        } else if let Some(file) = &seg.img {
            if let Some(obj) = new_image(ctx, doc, file, seg.w, seg.ih) {
                let _ = p.insert_object(end, obj, &last_props);
            }
        } else if seg.br {
            let _ = p.insert_text(end, "\n", &last_props);
        } else if let Some(kind) = seg.pb {
            let _ = p.insert_text(end, if kind == 2 { "\u{000E}" } else { "\u{000C}" }, &last_props);
        } else if let Some(f) = seg.fld.as_deref() {
            let instr = if f == "NUMPAGES" { "NUMPAGES" } else { "PAGE" };
            let props = run_props(ctx, doc, &style, seg);
            let _ = p.insert_object(end, page_field(instr), &props);
            last_props = props;
        }
    }
    p.normalize();
    p
}

/// Formato de un trozo de texto: el de su run de origen, cambiando solo lo que se ve distinto.
fn run_props(ctx: &Ctx, doc: &Document, style: &str, seg: &Seg) -> CharProps {
    let mut props = seg.r.as_deref().and_then(|r| find_run(&ctx.orig.body, r)).unwrap_or_default();
    if seg.hid {
        if seg.r.is_none() {
            props.hidden = Some(true);
        }
        return props;
    }
    let Some(f) = &seg.f else { return props };
    let cur = Look::of(&doc.styles.resolve_char(Some(style), &props));

    if let Some(b) = f.b.filter(|b| *b != cur.bold) {
        props.bold = Some(b);
    }
    if let Some(i) = f.i.filter(|i| *i != cur.italic) {
        props.italic = Some(i);
    }
    if let Some(u) = f.u.filter(|u| *u != cur.underline) {
        props.underline = Some(if u { Underline::Single } else { Underline::None });
    }
    if let Some(s) = f.s.filter(|s| *s != cur.strike) {
        props.strike = Some(s);
        if !s {
            props.double_strike = Some(false);
        }
    }
    if let Some(va) = f.va.as_deref().filter(|va| *va != cur.va) {
        props.vert_align = Some(match va {
            "sup" => VertAlign::Superscript,
            "sub" => VertAlign::Subscript,
            _ => VertAlign::Baseline,
        });
    }
    if let Some(font) = f.font.as_deref().map(str::trim).filter(|f| !f.is_empty() && !f.eq_ignore_ascii_case(&cur.font)) {
        props.font = Some(font.to_string());
    }
    if let Some(sz) = f.sz.filter(|sz| sz.is_finite() && *sz > 0.0 && (sz - cur.size).abs() > 0.25) {
        props.size = Some(((sz * 2.0).round() / 2.0).clamp(1.0, 1638.0));
    }
    if let Some(c) = f.c.as_deref().filter(|c| !c.eq_ignore_ascii_case(&cur.color)) {
        props.color = Some(Rgb::parse(c).map(TextColor::Rgb).unwrap_or(TextColor::Auto));
    }
    if let Some(link) = f.link.as_deref().map(str::trim) {
        let link = (!link.is_empty() && safe_link(link)).then(|| link.to_string());
        if link != props.link {
            props.link = link;
        }
    }
    if let Some(bg) = f.bg.as_deref().filter(|bg| !bg.eq_ignore_ascii_case(&cur.bg)) {
        match Rgb::parse(bg) {
            Some(rgb) => match Highlight::ALL.iter().find(|h| h.rgb() == Some(rgb)) {
                Some(h) => {
                    props.highlight = Some(*h);
                    props.shading = None;
                }
                None => {
                    props.highlight = Some(Highlight::None);
                    props.shading = Some(rgb);
                }
            },
            None => {
                props.highlight = Some(Highlight::None);
                props.shading = None;
            }
        }
    }
    props
}

fn new_image(ctx: &Ctx, doc: &mut Document, file: &str, w: Option<f32>, h: Option<f32>) -> Option<InlineObject> {
    // Solo archivos de la carpeta del documento
    let name = Path::new(file).file_name()?.to_str()?;
    let bytes = std::fs::read(ctx.media_dir.join(name)).ok()?;
    let ext = image_ext(&bytes)?;
    let (w, h) = (w.filter(|v| *v > 0.0).unwrap_or(200.0), h.filter(|v| *v > 0.0).unwrap_or(150.0));
    let media = doc.add_media(bytes, ext);
    Some(InlineObject::Image { media, w, h, alt: String::new(), float: Float::default(), crop: [0.0; 4], ole: None })
}

fn list_kind(list: &str, style: Option<&str>) -> ListKind {
    match (list, style.unwrap_or("")) {
        ("ol", "lower-alpha" | "lower-latin") => ListKind::LowerLetterDot,
        ("ol", "upper-alpha" | "upper-latin") => ListKind::UpperLetter,
        ("ol", "lower-roman") => ListKind::LowerRoman,
        ("ol", "upper-roman") => ListKind::Outline,
        ("ol", _) => ListKind::Numbered,
        (_, "circle") => ListKind::BulletChar('◦'),
        (_, "square") => ListKind::BulletChar('▪'),
        _ => ListKind::Bullet,
    }
}

/// Un estilo de Word por id; si el documento no lo tiene, se agrega el de Word.
fn builtin_style(doc: &mut Document, id: &str) -> String {
    if doc.styles.get(id).is_none() {
        if let Some(s) = StyleSheet::builtin().get(id).cloned() {
            doc.styles.upsert(s);
        }
    }
    id.to_string()
}

/// Estilo de título N del documento (aunque tenga otro id, como "Ttulo1"); si no existe, el de
/// Word se agrega.
fn heading_style(doc: &mut Document, n: u8) -> String {
    let name = format!("heading {n}");
    if let Some(s) = doc.styles.styles.iter().find(|s| s.name.eq_ignore_ascii_case(&name)) {
        return s.id.clone();
    }
    let id = format!("Heading{n}");
    if doc.styles.get(&id).is_none() {
        if let Some(s) = StyleSheet::builtin().get(&id).cloned() {
            doc.styles.upsert(s);
        }
    }
    id
}

pub fn safe_link(url: &str) -> bool {
    let u = url.to_ascii_lowercase();
    u.starts_with("http://") || u.starts_with("https://") || u.starts_with("mailto:")
}

fn parse_align(a: &str) -> Option<Align> {
    match a {
        "center" => Some(Align::Center),
        "right" | "end" => Some(Align::Right),
        "justify" => Some(Align::Justify),
        "left" | "start" => Some(Align::Left),
        _ => None,
    }
}

// ---------- Búsqueda en el original ----------

/// "5" → bloque 5 del cuerpo; "5.0.1.2" → bloque 2 de la celda (fila 0, columna 1) de la tabla 5.
fn find_block(body: &Blocks, path: &str) -> Option<Arc<Block>> {
    // En un encabezado o pie las rutas llevan delante su lugar ("hfhd.0"): se busca en ese texto
    let path = if path.starts_with("hf") { path.split_once('.').map_or(path, |(_, p)| p) } else { path };
    let nums: Vec<usize> = path.split('.').map(|n| n.parse().ok()).collect::<Option<_>>()?;
    let (first, rest) = nums.split_first()?;
    let mut block = body.get(*first)?.clone();
    for step in rest.chunks(3) {
        let [r, c, k] = step else { return None };
        let Block::Table(t) = &*block else { return None };
        let next = t.rows.get(*r)?.cells.get(*c)?.blocks.get(*k)?.clone();
        block = next;
    }
    Some(block)
}

fn split_ref(r: &str) -> Option<(&str, usize)> {
    let (path, i) = r.rsplit_once('#')?;
    Some((path, i.parse().ok()?))
}

fn find_run(body: &Blocks, r: &str) -> Option<CharProps> {
    let (path, i) = split_ref(r)?;
    let block = find_block(body, path)?;
    let p = block.as_para()?;
    p.run_ranges().nth(i).map(|(_, props)| props.clone())
}

fn find_object(body: &Blocks, r: &str) -> Option<InlineObject> {
    let (path, i) = split_ref(r)?;
    let block = find_block(body, path)?;
    block.as_para()?.objects.get(i).cloned()
}

// ---------- Marcas que deben ir en pares ----------

#[derive(Clone, Copy, PartialEq, Eq, Hash)]
enum Pair {
    Bookmark(u64),
    Comment(u32),
    Perm(u32),
}

/// Si al editar se borró una mitad de un par (campo, control, marcador, comentario, permiso), se
/// quita la otra: un archivo con pares rotos puede no abrir en Word.
fn repair_markers(body: &mut Blocks) {
    let mut marks: Vec<(usize, usize, Mark)> = Vec::new();
    let mut seq = 0usize;
    walk(body, &mut |p: &Paragraph| {
        for (k, o) in p.objects.iter().enumerate() {
            if let Some(m) = Mark::of(o) {
                marks.push((seq, k, m));
            }
        }
        seq += 1;
    });

    let mut drop: HashSet<(usize, usize)> = HashSet::new();
    let mut fields: Vec<(usize, usize)> = Vec::new();
    let mut controls: Vec<(usize, usize)> = Vec::new();
    let mut open: HashMap<Pair, (usize, usize)> = HashMap::new();
    for (s, k, m) in &marks {
        let at = (*s, *k);
        match m {
            Mark::FieldStart => fields.push(at),
            Mark::FieldEnd => {
                if fields.pop().is_none() {
                    drop.insert(at);
                }
            }
            Mark::ControlStart => controls.push(at),
            Mark::ControlEnd => {
                if controls.pop().is_none() {
                    drop.insert(at);
                }
            }
            Mark::Start(pair) => {
                open.insert(*pair, at);
            }
            Mark::End(pair) => {
                if open.remove(pair).is_none() {
                    drop.insert(at);
                }
            }
        }
    }
    drop.extend(fields);
    drop.extend(controls);
    drop.extend(open.into_values());
    if drop.is_empty() {
        return;
    }

    let mut seq = 0usize;
    walk_mut(body, &mut |p: &mut Paragraph| {
        let mine: Vec<usize> = drop.iter().filter(|(s, _)| *s == seq).map(|(_, k)| *k).collect();
        seq += 1;
        if mine.is_empty() {
            return;
        }
        let offsets = p.object_offsets();
        let mut cut: Vec<usize> = mine.iter().filter_map(|k| offsets.get(*k).copied()).collect();
        cut.sort_unstable_by(|a, b| b.cmp(a));
        for off in cut {
            let _ = p.delete(off, off + '\u{FFFC}'.len_utf8());
        }
    });
}

enum Mark {
    FieldStart,
    FieldEnd,
    ControlStart,
    ControlEnd,
    Start(Pair),
    End(Pair),
}

impl Mark {
    fn of(o: &InlineObject) -> Option<Mark> {
        Some(match o {
            InlineObject::FieldStart { .. } => Mark::FieldStart,
            InlineObject::FieldEnd => Mark::FieldEnd,
            InlineObject::ControlStart { .. } => Mark::ControlStart,
            InlineObject::ControlEnd => Mark::ControlEnd,
            InlineObject::BookmarkStart { name } => Mark::Start(Pair::Bookmark(hash(name))),
            InlineObject::BookmarkEnd { name } => Mark::End(Pair::Bookmark(hash(name))),
            InlineObject::CommentStart { id } => Mark::Start(Pair::Comment(*id)),
            InlineObject::CommentEnd { id } => Mark::End(Pair::Comment(*id)),
            InlineObject::PermStart { id, .. } => Mark::Start(Pair::Perm(*id)),
            InlineObject::PermEnd { id } => Mark::End(Pair::Perm(*id)),
            _ => return None,
        })
    }
}

fn hash(s: &str) -> u64 {
    use std::hash::{Hash, Hasher};
    let mut h = std::collections::hash_map::DefaultHasher::new();
    s.hash(&mut h);
    h.finish()
}

fn walk(blocks: &Blocks, f: &mut dyn FnMut(&Paragraph)) {
    for b in blocks {
        match &**b {
            Block::Para(p) => f(p),
            Block::Table(t) => t.rows.iter().flat_map(|r| &r.cells).for_each(|c| walk(&c.blocks, f)),
        }
    }
}

fn walk_mut(blocks: &mut Blocks, f: &mut dyn FnMut(&mut Paragraph)) {
    for b in blocks.iter_mut() {
        match Arc::make_mut(b) {
            Block::Para(p) => f(p),
            Block::Table(t) => {
                for c in t.rows.iter_mut().flat_map(|r| r.cells.iter_mut()) {
                    walk_mut(&mut c.blocks, f);
                }
            }
        }
    }
}

/// Numeración de páginas y distancias de encabezado y pie elegidas en el editor.
fn apply_numbering(s: &mut wordcraft_doc::SectionProps, o: &MHfOptions) {
    use wordcraft_doc::section::NumFormat;
    if let Some(f) = o.fmt.as_deref() {
        s.page_num_format = match f {
            "i" => NumFormat::LowerRoman,
            "I" => NumFormat::UpperRoman,
            "a" => NumFormat::LowerLetter,
            "A" => NumFormat::UpperLetter,
            _ => NumFormat::Decimal,
        };
    }
    if let Some(n) = o.start {
        // Empezar en 1 es lo normal: no hace falta escribirlo
        s.page_num_start = (n != 1).then_some(n.clamp(0, 9999));
    }
    let ok = |v: f32| v.is_finite() && v >= 0.0;
    if let Some(v) = o.hd.filter(|v| ok(*v)) {
        s.header = v.min(300.0);
    }
    if let Some(v) = o.fd.filter(|v| ok(*v)) {
        s.footer = v.min(300.0);
    }
}
