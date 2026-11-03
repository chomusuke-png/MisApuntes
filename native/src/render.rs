//! Documento de WordCraft → HTML para el editor de la app.
//!
//! Cada bloque lleva su ruta en `data-b` ("5" en el cuerpo, "5.0.1.2" para el bloque 2 de la celda
//! fila 0, columna 1 de la tabla 5), cada trozo de texto su run de origen en `data-r` y cada objeto
//! (imagen, nota al pie, marcador…) su índice en `data-o`. Así, al guardar, lo que no se tocó se
//! recupera tal cual del documento original.

use std::collections::HashMap;
use std::fmt::Write;
use std::path::Path;

use wordcraft_doc::numbering::Counters;
use wordcraft_doc::para::{InlineObject, OBJ};
use wordcraft_doc::PartKind;
use wordcraft_doc::props::{Align, BorderStyle, Borders, LineSpacing, Underline, VertAlign};
use wordcraft_doc::styles::TablePart;
use wordcraft_doc::resolve::{ResolvedChar, ResolvedPara};
use wordcraft_doc::{Block, Blocks, CharProps, Document, Paragraph, Rgb, Table, TextColor};

/// Lo que se ve de un trozo de texto: es lo que se compara al guardar para saber si cambió.
#[derive(Clone, Debug, PartialEq)]
pub struct Look {
    pub font: String,
    pub size: f32,
    pub bold: bool,
    pub italic: bool,
    pub underline: bool,
    pub strike: bool,
    /// "", "sup" o "sub".
    pub va: &'static str,
    /// "#RRGGBB" o "" (automático).
    pub color: String,
    /// "#RRGGBB" o "" (sin fondo).
    pub bg: String,
}

impl Look {
    pub fn of(c: &ResolvedChar) -> Look {
        Look {
            font: clean_font(&c.font),
            size: (c.size * 2.0).round() / 2.0,
            bold: c.bold,
            italic: c.italic,
            underline: c.underline != Underline::None,
            strike: c.strike || c.double_strike,
            va: match c.vert_align {
                VertAlign::Superscript => "sup",
                VertAlign::Subscript => "sub",
                VertAlign::Baseline => "",
            },
            color: match c.color {
                TextColor::Rgb(rgb) => hex(rgb),
                TextColor::Auto => String::new(),
            },
            bg: c.highlight.or(c.shading).map(hex).unwrap_or_default(),
        }
    }
}

pub fn hex(c: Rgb) -> String {
    format!("#{:02X}{:02X}{:02X}", c.0, c.1, c.2)
}

pub fn clean_font(f: &str) -> String {
    f.replace(['\'', '"', ';', '<', '>'], "").trim().to_string()
}

/// Estilo de párrafo efectivo ("Normal" si no tiene).
pub fn para_style(p: &Paragraph) -> String {
    p.props.style.clone().unwrap_or_else(|| "Normal".into())
}

/// Nivel de título (1-6) según el nombre del estilo o el nivel de esquema.
pub fn heading_level(doc: &Document, style: &str, rp: &ResolvedPara) -> Option<u8> {
    for s in doc.styles.chain(style) {
        let name = s.name.to_ascii_lowercase();
        if name == "title" {
            return Some(1);
        }
        if let Some(n) = name.strip_prefix("heading ").and_then(|n| n.trim().parse::<u8>().ok()) {
            return Some(n.clamp(1, 6));
        }
    }
    rp.outline_level.filter(|l| *l < 6).map(|l| l + 1)
}

pub struct Renderer<'a> {
    doc: &'a Document,
    media_dir: &'a Path,
    written: HashMap<String, Option<String>>,
    counters: Counters,
    notes: String,
    note_count: u32,
}

impl<'a> Renderer<'a> {
    pub fn new(doc: &'a Document, media_dir: &'a Path) -> Self {
        Renderer { doc, media_dir, written: HashMap::new(), counters: Counters::default(), notes: String::new(), note_count: 0 }
    }

    pub fn render(mut self) -> String {
        let s = &self.doc.last_section;
        let mut out = String::new();
        let _ = write!(
            out,
            "<article id=\"page\" data-src=\"wc\" data-w=\"{w}\" style=\"width:{w}pt;padding:{t}pt {r}pt {b}pt {l}pt\">",
            w = num(s.page_w),
            t = num(s.margin_top),
            r = num(s.margin_right),
            b = num(s.margin_bottom),
            l = num(s.margin_left),
        );
        let body = self.doc.body.clone();
        self.blocks(&body, "", &mut out);
        if self.doc.body.is_empty() {
            out.push_str("<p><br></p>");
        }
        if !self.notes.is_empty() {
            out.push_str("<div class=\"fn-area\" data-x=\"1\" contenteditable=\"false\"><hr class=\"fn-sep\">");
            let notes = std::mem::take(&mut self.notes);
            out.push_str(&notes);
            out.push_str("</div>");
        }
        out.push_str("</article>");
        out
    }

    fn blocks(&mut self, blocks: &Blocks, prefix: &str, out: &mut String) {
        for (i, b) in blocks.iter().enumerate() {
            let id = if prefix.is_empty() { i.to_string() } else { format!("{prefix}.{i}") };
            match &**b {
                Block::Para(p) => self.paragraph(p, &id, out),
                Block::Table(t) => self.table(t, &id, out),
            }
        }
    }

    fn paragraph(&mut self, p: &Paragraph, id: &str, out: &mut String) {
        let doc = self.doc;
        let style = para_style(p);
        let rp = doc.styles.resolve_para(&p.props);
        let base = doc.styles.resolve_char(Some(&style), &CharProps::default());
        let base_look = Look::of(&base);
        let tag = match heading_level(doc, &style, &rp) {
            Some(n) => format!("h{n}"),
            None => "p".into(),
        };

        if rp.page_break_before {
            out.push_str("<hr class=\"pb\" data-x=\"1\" contenteditable=\"false\">");
        }
        let _ = write!(out, "<{tag} data-b=\"{id}\" style=\"");
        para_css(&rp, out);
        full_char_css(&base_look, &base, out);
        out.push_str("\">");

        let start = out.len();
        if let Some(n) = rp.numbering.filter(|n| n.num != 0) {
            if let Some((label, level)) = self.counters.next_label(&doc.numbering, n.num, n.level) {
                if !label.is_empty() {
                    let mut lc = Look::of(&doc.styles.resolve_char(Some(&style), &level.chr));
                    let shown = bullet(&label);
                    // Las viñetas de Symbol/Wingdings ya se cambiaron por caracteres normales
                    if shown != label {
                        lc.font = base_look.font.clone();
                    }
                    let css = diff_css(&lc, &base_look);
                    let _ = write!(out, "<span class=\"wl\" contenteditable=\"false\" style=\"{css}\">{}&emsp;</span>", esc(&shown));
                }
            }
        }

        let mut obj = 0usize;
        for (ri, (range, props)) in p.run_ranges().enumerate() {
            let text = &p.text[range];
            let rc = doc.styles.resolve_char(Some(&style), props);
            let look = Look::of(&rc);
            let hidden = rc.hidden || rc.del.is_some();
            let css = diff_css(&look, &base_look);
            let rref = format!("{id}#{ri}");
            let mut buf = String::new();
            for ch in text.chars() {
                match ch {
                    OBJ => {
                        self.flush(&mut buf, &rref, &css, &look, hidden, out);
                        if let Some(o) = p.objects.get(obj) {
                            self.object(o, &format!("{id}#{obj}"), out);
                        }
                        obj += 1;
                    }
                    '\n' => {
                        self.flush(&mut buf, &rref, &css, &look, hidden, out);
                        out.push_str("<br>");
                    }
                    '\u{000C}' | '\u{000E}' => {
                        self.flush(&mut buf, &rref, &css, &look, hidden, out);
                        let kind = if ch == '\u{000C}' { 1 } else { 2 };
                        let _ = write!(out, "<span class=\"wpb\" data-pb=\"{kind}\" contenteditable=\"false\"></span>");
                    }
                    c => buf.push(c),
                }
            }
            self.flush(&mut buf, &rref, &css, &look, hidden, out);
        }
        if out.len() == start || out.ends_with("<br>") {
            out.push_str("<br>");
        }
        let _ = write!(out, "</{tag}>");
    }

    fn flush(&self, buf: &mut String, rref: &str, css: &str, look: &Look, hidden: bool, out: &mut String) {
        if buf.is_empty() {
            return;
        }
        let mut html = esc(buf);
        buf.clear();
        match look.va {
            "sup" => html = format!("<sup>{html}</sup>"),
            "sub" => html = format!("<sub>{html}</sub>"),
            _ => {}
        }
        let class = if hidden { " class=\"wh\"" } else { "" };
        let _ = write!(out, "<span data-r=\"{rref}\"{class}");
        if !css.is_empty() {
            let _ = write!(out, " style=\"{css}\"");
        }
        let _ = write!(out, ">{html}</span>");
    }

    fn object(&mut self, o: &InlineObject, oref: &str, out: &mut String) {
        match o {
            InlineObject::Image { media, w, h, .. } => match self.media_file(media) {
                Some(name) => {
                    let _ = write!(
                        out,
                        "<img data-o=\"{oref}\" src=\"{}\" style=\"width:{}pt;height:{}pt\">",
                        esc(&name),
                        num(*w),
                        num(*h)
                    );
                }
                None => {
                    let _ = write!(out, "<span class=\"wo noimg\" data-o=\"{oref}\" contenteditable=\"false\">[imagen]</span>");
                }
            },
            InlineObject::NoteRef { kind, id, custom } => {
                let foot = matches!(self.doc.parts.get(id).map(|p| p.kind), Some(PartKind::Footnote | PartKind::Endnote));
                self.note_count += 1;
                let mark = if custom.is_empty() { self.note_count.to_string() } else { custom.clone() };
                let _ = kind;
                let _ = write!(out, "<sup class=\"wo\" data-o=\"{oref}\" contenteditable=\"false\">{}</sup>", esc(&mark));
                if foot {
                    if let Some(part) = self.doc.parts.get(id) {
                        let blocks = part.blocks.clone();
                        let mut note = String::new();
                        let _ = write!(note, "<div class=\"fn\"><sup>{}</sup> ", esc(&mark));
                        self.note_blocks(&blocks, &mut note);
                        note.push_str("</div>");
                        self.notes.push_str(&note);
                    }
                }
            }
            InlineObject::Field { result, .. } => {
                let _ = write!(out, "<span class=\"wo\" data-o=\"{oref}\" contenteditable=\"false\">{}</span>", esc(result));
            }
            InlineObject::Ruby { base, .. } => {
                let _ = write!(out, "<span class=\"wo\" data-o=\"{oref}\" contenteditable=\"false\">{}</span>", esc(base));
            }
            InlineObject::Equation { linear, .. } => {
                let _ = write!(out, "<span class=\"wo eq\" data-o=\"{oref}\" contenteditable=\"false\">{}</span>", esc(linear));
            }
            InlineObject::Opaque { text, .. } => {
                let _ = write!(out, "<span class=\"wo\" data-o=\"{oref}\" contenteditable=\"false\">{}</span>", esc(text));
            }
            InlineObject::PositionalTab { .. } => {
                let _ = write!(out, "<span class=\"wo\" data-o=\"{oref}\" contenteditable=\"false\">\t</span>");
            }
            InlineObject::Graphic { alt, .. } => {
                let label = if alt.is_empty() { "[gráfico]".to_string() } else { format!("[{alt}]") };
                let _ = write!(out, "<span class=\"wo noimg\" data-o=\"{oref}\" contenteditable=\"false\">{}</span>", esc(&label));
            }
            InlineObject::Shape { .. } | InlineObject::Group { .. } => {
                // Formas y cuadros de texto: se muestra el texto de sus cuadros, si lo hay
                let _ = write!(out, "<span class=\"wo wshape\" data-o=\"{oref}\" contenteditable=\"false\"></span>");
            }
            // Marcadores invisibles (campos, controles, marcadores, comentarios, permisos)
            _ => {
                let _ = write!(out, "<span class=\"wm\" data-o=\"{oref}\" contenteditable=\"false\"></span>");
            }
        }
    }

    /// Notas al pie: solo lectura, sin rutas.
    fn note_blocks(&mut self, blocks: &Blocks, out: &mut String) {
        for b in blocks.iter() {
            if let Block::Para(p) = &**b {
                let style = para_style(p);
                for (range, props) in p.run_ranges() {
                    let rc = self.doc.styles.resolve_char(Some(&style), props);
                    if rc.hidden {
                        continue;
                    }
                    let t: String = p.text[range].chars().filter(|c| *c != OBJ).collect();
                    out.push_str(&esc(&t));
                }
                out.push(' ');
            }
        }
    }

    fn table(&mut self, t: &Table, id: &str, out: &mut String) {
        let _ = write!(out, "<table data-b=\"{id}\" style=\"");
        let total: f32 = t.grid.iter().sum();
        if total > 0.0 {
            let _ = write!(out, "width:{}pt;", num(total));
        }
        match t.props.align {
            Some(Align::Center) => out.push_str("margin-left:auto;margin-right:auto;"),
            Some(Align::Right) => out.push_str("margin-left:auto;"),
            _ => {
                if let Some(ind) = t.props.indent {
                    let _ = write!(out, "margin-left:{}pt;", num(ind));
                }
            }
        }
        out.push_str("\">");
        if !t.grid.is_empty() {
            out.push_str("<colgroup>");
            for w in &t.grid {
                let _ = write!(out, "<col style=\"width:{}pt\">", num(*w));
            }
            out.push_str("</colgroup>");
        }

        let borders = table_has_borders(self.doc, t);
        // Celdas combinadas verticalmente: la primera crece, las siguientes no se dibujan
        let mut rowspans: HashMap<(usize, usize), usize> = HashMap::new();
        let mut open: HashMap<usize, (usize, usize)> = HashMap::new();
        for (r, row) in t.rows.iter().enumerate() {
            let mut col = 0usize;
            for (c, cell) in row.cells.iter().enumerate() {
                use wordcraft_doc::props::VMerge;
                match cell.props.vmerge {
                    VMerge::Restart => {
                        open.insert(col, (r, c));
                        rowspans.insert((r, c), 1);
                    }
                    VMerge::Continue => {
                        if let Some(first) = open.get(&col) {
                            *rowspans.entry(*first).or_insert(1) += 1;
                        }
                    }
                    VMerge::None => {
                        open.remove(&col);
                    }
                }
                col += cell.props.span.max(1) as usize;
            }
        }

        for (r, row) in t.rows.iter().enumerate() {
            out.push_str("<tr>");
            for (c, cell) in row.cells.iter().enumerate() {
                if matches!(cell.props.vmerge, wordcraft_doc::props::VMerge::Continue) {
                    continue;
                }
                let _ = write!(out, "<td data-c=\"{r}:{c}\"");
                if cell.props.span > 1 {
                    let _ = write!(out, " colspan=\"{}\"", cell.props.span);
                }
                if let Some(n) = rowspans.get(&(r, c)).filter(|n| **n > 1) {
                    let _ = write!(out, " rowspan=\"{n}\"");
                }
                out.push_str(" style=\"");
                if borders || cell.props.borders.is_some() {
                    out.push_str("border:0.5pt solid #000;");
                }
                if let Some(bg) = cell.props.shading {
                    let _ = write!(out, "background-color:{};", hex(bg));
                }
                out.push_str("\">");
                let start = out.len();
                self.blocks(&cell.blocks, &format!("{id}.{r}.{c}"), out);
                if out.len() == start {
                    out.push_str("<p><br></p>");
                }
                out.push_str("</td>");
            }
            out.push_str("</tr>");
        }
        out.push_str("</table>");
    }

    /// Escribe la imagen junto al HTML (una vez por clave). `None` si el navegador no la muestra.
    fn media_file(&mut self, key: &str) -> Option<String> {
        if let Some(done) = self.written.get(key) {
            return done.clone();
        }
        let result = self.doc.media.get(key).and_then(|bytes| {
            let ext = image_ext(bytes)?;
            let name = format!("m{}.{ext}", self.written.len() + 1);
            std::fs::write(self.media_dir.join(&name), bytes.as_slice()).ok()?;
            Some(name)
        });
        self.written.insert(key.to_string(), result.clone());
        result
    }
}

fn table_has_borders(doc: &Document, t: &Table) -> bool {
    if let Some(b) = &t.props.borders {
        return visible(b);
    }
    t.props
        .style
        .as_deref()
        .and_then(|s| doc.styles.table_style(s))
        .and_then(|ts| ts.parts.part(TablePart::WholeTable).borders)
        .is_some_and(|b| visible(&b))
}

fn visible(b: &Borders) -> bool {
    [&b.top, &b.left, &b.bottom, &b.right, &b.between, &b.inside_v]
        .iter()
        .any(|s| s.as_ref().is_some_and(|s| s.style != BorderStyle::None && s.width > 0.0))
}

/// Formato de imagen por su firma; solo los que muestra el navegador.
pub fn image_ext(b: &[u8]) -> Option<&'static str> {
    if b.starts_with(&[0x89, b'P', b'N', b'G']) {
        Some("png")
    } else if b.starts_with(&[0xFF, 0xD8]) {
        Some("jpg")
    } else if b.starts_with(b"GIF8") {
        Some("gif")
    } else if b.starts_with(b"BM") {
        Some("bmp")
    } else if b.len() > 12 && &b[0..4] == b"RIFF" && &b[8..12] == b"WEBP" {
        Some("webp")
    } else if b.starts_with(b"<svg") || b.starts_with(b"<?xml") {
        Some("svg")
    } else {
        None
    }
}

fn para_css(rp: &ResolvedPara, out: &mut String) {
    match rp.align {
        Align::Center => out.push_str("text-align:center;"),
        Align::Right => out.push_str("text-align:right;"),
        Align::Justify | Align::Distribute => out.push_str("text-align:justify;"),
        Align::Left => {}
    }
    let before = if rp.before_auto { 14.0 } else { rp.space_before };
    let after = if rp.after_auto { 14.0 } else { rp.space_after };
    let _ = write!(
        out,
        "margin:{}pt {}pt {}pt {}pt;",
        num(before),
        num(rp.indent_right),
        num(after),
        num(rp.indent_left)
    );
    if rp.indent_first != 0.0 {
        let _ = write!(out, "text-indent:{}pt;", num(rp.indent_first));
    }
    match rp.line_spacing {
        // El interlineado sencillo del editor ya es 1.2
        LineSpacing::Multiple(m) if (m - 1.0).abs() > 0.01 => {
            let _ = write!(out, "line-height:{};", num(m * 1.2));
        }
        LineSpacing::AtLeast(pt) | LineSpacing::Exactly(pt) => {
            let _ = write!(out, "line-height:{}pt;", num(pt));
        }
        _ => {}
    }
    // Como imagen y no como background-color: así no se confunde con el resaltado del texto
    if let Some(bg) = rp.shading {
        let c = hex(bg);
        let _ = write!(out, "background-image:linear-gradient({c},{c});");
    }
}

fn full_char_css(l: &Look, rc: &ResolvedChar, out: &mut String) {
    if !l.font.is_empty() {
        let _ = write!(out, "font-family:{};", font_stack(&l.font));
    }
    let _ = write!(out, "font-size:{}pt;", num(l.size));
    if !l.color.is_empty() {
        let _ = write!(out, "color:{};", l.color);
    }
    out.push_str(if l.bold { "font-weight:bold;" } else { "font-weight:normal;" });
    out.push_str(if l.italic { "font-style:italic;" } else { "font-style:normal;" });
    if let Some(d) = decoration(l) {
        let _ = write!(out, "text-decoration:{d};");
    }
    if !l.bg.is_empty() {
        let _ = write!(out, "background-color:{};", l.bg);
    }
    if rc.caps {
        out.push_str("text-transform:uppercase;");
    }
    if rc.small_caps {
        out.push_str("font-variant:small-caps;");
    }
}

/// Solo lo que cambia respecto al texto base del párrafo.
fn diff_css(l: &Look, base: &Look) -> String {
    let mut css = String::new();
    if l.font != base.font && !l.font.is_empty() {
        let _ = write!(css, "font-family:{};", font_stack(&l.font));
    }
    if l.size != base.size {
        let _ = write!(css, "font-size:{}pt;", num(l.size));
    }
    if l.color != base.color {
        let _ = write!(css, "color:{};", if l.color.is_empty() { "inherit" } else { &l.color });
    }
    if l.bold != base.bold {
        css.push_str(if l.bold { "font-weight:bold;" } else { "font-weight:normal;" });
    }
    if l.italic != base.italic {
        css.push_str(if l.italic { "font-style:italic;" } else { "font-style:normal;" });
    }
    if decoration(l) != decoration(base) {
        let _ = write!(css, "text-decoration:{};", decoration(l).unwrap_or("none"));
    }
    if l.bg != base.bg {
        let _ = write!(css, "background-color:{};", if l.bg.is_empty() { "transparent" } else { &l.bg });
    }
    css
}

fn decoration(l: &Look) -> Option<&'static str> {
    match (l.underline, l.strike) {
        (true, true) => Some("underline line-through"),
        (true, false) => Some("underline"),
        (false, true) => Some("line-through"),
        _ => None,
    }
}

fn font_stack(name: &str) -> String {
    let n = name.to_ascii_lowercase();
    let generic = if ["courier", "consolas", "mono", "menlo", "lucida console"].iter().any(|k| n.contains(k)) {
        "monospace"
    } else if n.contains("sans") {
        "sans-serif"
    } else if ["times", "georgia", "garamond", "cambria", "book antiqua", "palatino", "serif", "bookman", "century schoolbook"]
        .iter()
        .any(|k| n.contains(k))
    {
        "serif"
    } else if ["script", "brush", "comic", "hand", "segoe print"].iter().any(|k| n.contains(k)) {
        "cursive"
    } else {
        "sans-serif"
    };
    format!("'{}',{generic}", clean_font(name))
}

/// Viñetas de Word en fuentes Symbol/Wingdings (área privada de Unicode) → caracteres normales.
fn bullet(label: &str) -> String {
    let mut chars = label.chars();
    let (Some(c), None) = (chars.next(), chars.clone().next()) else { return label.to_string() };
    match c {
        '\u{F0B7}' | '·' => "•".into(),
        'o' => "◦".into(),
        '\u{F0A7}' | '§' => "▪".into(),
        '\u{F0D8}' => "➢".into(),
        '\u{F076}' => "❖".into(),
        '\u{F0FC}' => "✓".into(),
        '\u{F0A8}' => "□".into(),
        c if ('\u{F000}'..='\u{F0FF}').contains(&c) => "•".into(),
        _ => label.to_string(),
    }
}

pub fn esc(s: &str) -> String {
    let mut o = String::with_capacity(s.len() + 8);
    for c in s.chars() {
        match c {
            '&' => o.push_str("&amp;"),
            '<' => o.push_str("&lt;"),
            '>' => o.push_str("&gt;"),
            '"' => o.push_str("&quot;"),
            c => o.push(c),
        }
    }
    o
}

pub fn num(v: f32) -> String {
    if !v.is_finite() {
        return "0".into();
    }
    if (v - v.round()).abs() < 0.005 {
        format!("{}", v.round() as i64)
    } else {
        let s = format!("{v:.2}");
        s.trim_end_matches('0').trim_end_matches('.').to_string()
    }
}
