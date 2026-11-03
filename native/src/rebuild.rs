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
use wordcraft_doc::props::{Highlight, NumRef, Underline, VertAlign};
use wordcraft_doc::styles::StyleSheet;
use wordcraft_doc::{Align, Block, Blocks, CharProps, Document, Paragraph, Rgb, TextColor};

use crate::render::{Look, heading_level, image_ext, para_style};

#[derive(Deserialize)]
pub struct Model {
    pub blocks: Vec<MBlock>,
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
}

#[derive(Deserialize)]
pub struct MTable {
    pub src: Option<String>,
    #[serde(default)]
    pub cells: Vec<MCell>,
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
    doc
}

fn count_sources(blocks: &[MBlock], out: &mut HashMap<String, usize>) {
    for b in blocks {
        match b {
            MBlock::Para(p) => {
                if let Some(s) = &p.src {
                    *out.entry(s.clone()).or_insert(0) += 1;
                }
            }
            MBlock::Table(t) => t.cells.iter().for_each(|c| count_sources(&c.blocks, out)),
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
                    // Tabla pegada desde otro lado: su texto pasa a párrafos
                    _ => {
                        for mc in &mt.cells {
                            out.extend(build_blocks(ctx, doc, &mc.blocks));
                        }
                    }
                }
            }
        }
    }
    out
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

    // Título / párrafo normal
    let rp = doc.styles.resolve_para(&p.props);
    let current = heading_level(doc, &para_style(&p), &rp);
    let wanted = mp.tag.strip_prefix('h').and_then(|n| n.parse::<u8>().ok()).filter(|n| (1..=6).contains(n));
    if wanted != current {
        p.props.outline_level = None;
        p.props.style = wanted.map(|n| heading_style(doc, n));
    }

    // Listas creadas en el editor (las de Word conservan su numeración)
    let had_numbering = src_para.and_then(|sp| doc.styles.resolve_para(&sp.props).numbering).is_some_and(|n| n.num != 0);
    if let Some(list) = mp.list.as_deref() {
        if !had_numbering {
            let ordered = list == "ol";
            let key = (mp.list_id, ordered);
            let num = match ctx.lists.get(&key) {
                Some(n) => *n,
                None => {
                    let n = doc.numbering.add_list(if ordered { ListKind::Numbered } else { ListKind::Bullet });
                    ctx.lists.insert(key, n);
                    n
                }
            };
            p.props.numbering = Some(NumRef { num, level: mp.level.min(8) });
        }
    }

    // Alineación
    if let Some(a) = mp.align.as_deref().and_then(parse_align) {
        let now = doc.styles.resolve_para(&p.props).align;
        let now = if now == Align::Distribute { Align::Justify } else { now };
        if a != now {
            p.props.align = Some(a);
        }
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
            if let Some(obj) = find_object(&ctx.orig.body, oref) {
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
