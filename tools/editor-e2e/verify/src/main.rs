// Auditoría: fixture + verificación de cada herramienta contra el .docx releído por WordCraft.
use std::path::Path;
use wordbridge::render::{Look, para_style};
use wordcraft_doc::para::InlineObject;
use wordcraft_doc::props::{Align, BorderStyle, LineSpacing, VAlign};
use wordcraft_doc::{Block, Blocks, CharProps, Document, Paragraph, Table};

const SAMPLE: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/../../../app/src/test/resources/sample.docx");
const FIXTURE: &str =r##"{"page":{"w":612,"h":792,"mt":72,"mr":72,"mb":72,"ml":72},"blocks":[
 {"p":{"tag":"p","segs":[{"t":"Normal uno dos tres"}]}},
 {"p":{"tag":"p","segs":[{"t":"Negrita run","f":{"b":true}},{"t":" resto"}]}},
 {"p":{"tag":"p","segs":[{"t":"Subrayado x","f":{"u":true}},{"t":" Tachado y","f":{"s":true}},{"t":" Alto z","f":{"va":"sup"}},{"t":" Rojo w","f":{"c":"#FF0000"}},{"t":" Marcado v","f":{"bg":"#FFFF00"}},{"t":" Cursiva k","f":{"i":true}},{"t":" Grande g","f":{"sz":20}},{"t":" Letra f","f":{"font":"Georgia"}}]}},
 {"p":{"tag":"p","align":"center","segs":[{"t":"Centrado par"}]}},
 {"p":{"tag":"h1","segs":[{"t":"Titulo uno"}]}},
 {"p":{"tag":"li","list":"ul","list_id":1,"segs":[{"t":"Item uno"}]}},
 {"p":{"tag":"li","list":"ul","list_id":1,"segs":[{"t":"Item dos"}]}},
 {"p":{"tag":"li","list":"ol","list_id":2,"segs":[{"t":"Numero uno"}]}},
 {"p":{"tag":"blockquote","segs":[{"t":"Cita base"}]}},
 {"t":{"src":null,"grid":[100,100,100],"bd":true,"rows":[
  {"tr":null,"cells":[{"s":1,"m":0,"blocks":[{"p":{"tag":"p","segs":[{"t":"A1"}]}}]},{"s":1,"m":0,"blocks":[{"p":{"tag":"p","segs":[{"t":"B1"}]}}]},{"s":1,"m":0,"blocks":[{"p":{"tag":"p","segs":[{"t":"C1"}]}}]}]},
  {"tr":null,"cells":[{"s":1,"m":0,"blocks":[{"p":{"tag":"p","segs":[{"t":"A2"}]}}]},{"s":1,"m":0,"blocks":[{"p":{"tag":"p","segs":[{"t":"B2"}]}}]},{"s":1,"m":0,"blocks":[{"p":{"tag":"p","segs":[{"t":"C2"}]}}]}]},
  {"tr":null,"cells":[{"s":1,"m":0,"blocks":[{"p":{"tag":"p","segs":[{"t":"A3"}]}}]},{"s":1,"m":0,"blocks":[{"p":{"tag":"p","segs":[{"t":"B3"}]}}]},{"s":1,"m":0,"blocks":[{"p":{"tag":"p","segs":[{"t":"C3"}]}}]}]}]}},
 {"p":{"tag":"p","segs":[{"img":"test.png","w":100,"ih":50},{"t":"Foto"}]}},
 {"p":{"tag":"p","segs":[{"t":"Sitio","f":{"link":"https://example.com"}},{"t":" fin enlace"}]}},
 {"p":{"tag":"p","segs":[{"t":"Buscar palabra y palabra"}]}},
 {"p":{"tag":"p","segs":[{"t":"Final"}]}}
],"hf":{"hd":{"blocks":[{"p":{"tag":"p","segs":[{"t":"Encabezado base"}]}}]},"fd":{"blocks":[{"p":{"tag":"p","segs":[{"t":"Pie base"}]}}]}}}"##;

fn walk<'a>(blocks: &'a Blocks, out: &mut Vec<&'a Paragraph>) {
    for b in blocks {
        match &**b {
            Block::Para(p) => out.push(p),
            Block::Table(t) => t.rows.iter().flat_map(|r| &r.cells).for_each(|c| walk(&c.blocks, out)),
        }
    }
}
fn paras(d: &Document) -> Vec<&Paragraph> { let mut v = vec![]; walk(&d.body, &mut v); v }
fn para<'a>(d: &'a Document, t: &str) -> Result<&'a Paragraph, String> {
    paras(d).into_iter().find(|p| p.plain_text().contains(t)).ok_or(format!("sin párrafo con '{t}'"))
}
fn look_of(d: &Document, p: &Paragraph, props: &CharProps) -> Look { Look::of(&d.styles.resolve_char(Some(&para_style(p)), props)) }
fn run(d: &Document, t: &str) -> Result<Look, String> {
    let p = para(d, t)?;
    for (r, props) in p.run_ranges() { if p.text[r].contains(t) { return Ok(look_of(d, p, props)); } }
    Err(format!("'{t}' no está en un solo run: {:?}", p.run_ranges().map(|(r, _)| p.text[r].to_string()).collect::<Vec<_>>()))
}
fn head<'a>(d: &'a Document, k: char, v: char) -> Result<&'a Blocks, String> {
    let s = if k == 'h' { &d.last_section.headers } else { &d.last_section.footers };
    let id = match v { 'd' => s.default, '1' => s.first, _ => s.even }.ok_or(format!("sin {k}{v}"))?;
    Ok(&d.parts.get(&id).ok_or("parte inexistente")?.blocks)
}
fn text_of(b: &Blocks) -> String { let mut v = vec![]; walk(b, &mut v); v.iter().map(|p| p.plain_text()).collect::<Vec<_>>().join("|") }
fn table(d: &Document) -> Result<&Table, String> { d.body.iter().find_map(|b| b.as_table()).ok_or("sin tabla".into()) }
fn table_n(d: &Document, n: usize) -> Result<&Table, String> { d.body.iter().filter_map(|b| b.as_table()).nth(n).ok_or("sin tabla n".into()) }
fn shape(t: &Table) -> String { t.rows.iter().map(|r| r.cells.len().to_string()).collect::<Vec<_>>().join(",") }
fn cell_text(t: &Table, r: usize, c: usize) -> String { text_of(&t.rows[r].cells[c].blocks) }
fn images(p: &Paragraph) -> Vec<(String, f32, f32)> {
    p.objects.iter().filter_map(|o| if let InlineObject::Image { media, w, h, .. } = o { Some((media.to_string(), *w, *h)) } else { None }).collect()
}
fn heading(d: &Document, t: &str) -> Result<Option<u8>, String> {
    let p = para(d, t)?;
    let rp = d.styles.resolve_para(&p.props);
    Ok(wordbridge::render::heading_level(d, &para_style(p), &rp))
}
fn num_fmt(d: &Document, t: &str) -> Result<Option<(String, u8)>, String> {
    let p = para(d, t)?;
    Ok(d.styles.resolve_para(&p.props).numbering.filter(|n| n.num != 0).and_then(|n| d.numbering.level(n.num, n.level).map(|l| (format!("{:?}", l.format), n.level))))
}
fn fields(p: &Paragraph) -> Vec<String> { p.objects.iter().filter_map(|o| if let InlineObject::Field { instr, .. } = o { Some(instr.trim().to_string()) } else { None }).collect() }

macro_rules! ok { ($c:expr, $($m:tt)*) => { if !$c { return Err(format!($($m)*)); } } }

fn check(name: &str, d: &Document, o: &Document, media: &Path) -> Result<(), String> {
    let rp = |t: &str| para(d, t).map(|p| d.styles.resolve_para(&p.props));
    let file_len = |n: &str| std::fs::metadata(media.join(n)).map(|m| m.len() as usize).unwrap_or(0);
    match name {
        "fuente" => { let l = run(d, "Normal uno")?; ok!(l.font == "Georgia", "font={}", l.font); ok!(run(d, "dos tres")?.font != "Georgia", "se extendió"); }
        "tamano" => { let l = run(d, "Normal uno")?; ok!(l.size == 18.0, "size={}", l.size); }
        "agrandar" => { let l = run(d, "Grande g")?; ok!(l.size == 22.0, "size={}", l.size); }
        "negrita_on" => { ok!(run(d, "Normal uno")?.bold, "no negrita"); ok!(!run(d, "dos tres")?.bold, "se extendió"); }
        "negrita_off" => { ok!(!run(d, "Negrita run")?.bold, "sigue negrita"); }
        "cursiva_on" => { ok!(run(d, "Normal uno")?.italic, "no cursiva"); }
        "cursiva_off" => { ok!(!run(d, "Cursiva k")?.italic, "sigue cursiva"); }
        "subrayado_on" => { ok!(run(d, "Normal uno")?.underline, "no subrayado"); }
        "subrayado_off" => { ok!(!run(d, "Subrayado x")?.underline, "sigue subrayado"); }
        "tachado_on" => { ok!(run(d, "Normal uno")?.strike, "no tachado"); }
        "tachado_off" => { ok!(!run(d, "Tachado y")?.strike, "sigue tachado"); }
        "sup_on" => { let v = run(d, "Normal uno")?.va; ok!(v == "sup", "va={v}"); }
        "sup_off" => { let v = run(d, "Alto z")?.va; ok!(v == "", "va={v}"); }
        "sub_on" => { let v = run(d, "Normal uno")?.va; ok!(v == "sub", "va={v}"); }
        "mayusculas" => { ok!(para(d, "NORMAL UNO").is_ok(), "texto {:?}", para(d, "Normal").map(|p| p.plain_text())); }
        "minusculas" => { ok!(para(d, "normal uno").is_ok(), "sin cambio"); }
        "color" => { let c = run(d, "Normal uno")?.color; ok!(c == "#0000FF", "color={c}"); }
        "color_quitar" => { let c = run(d, "Rojo w")?.color; ok!(c.is_empty(), "color={c}"); }
        "resaltar_estandar" => { let c = run(d, "Normal uno")?.bg; ok!(c == "#FFFF00", "bg={c}"); }
        "resaltar_propio" => { let c = run(d, "Normal uno")?.bg; ok!(c == "#F48FB1", "bg={c}"); }
        "resaltar_quitar" => { let c = run(d, "Marcado v")?.bg; ok!(c.is_empty(), "bg={c}"); }
        "brocha" => { ok!(run(d, "Normal uno")?.bold, "no copió negrita"); }
        "limpiar" => {
            for t in ["Subrayado x", "Tachado y", "Alto z", "Rojo w", "Marcado v", "Cursiva k", "Grande g", "Letra f"] {
                let l = run(d, t).or_else(|_| run(d, t.split(' ').next().unwrap()))?; let n = run(d, "Normal")?;
                ok!(!l.underline && !l.strike && l.va.is_empty() && l.color.is_empty() && l.bg.is_empty() && !l.italic, "{t}: {l:?}");
                ok!(l.size == n.size, "{t} conserva tamaño {}", l.size); ok!(l.font == n.font, "{t} conserva fuente {}", l.font);
            }
        }
        "deshacer" => { ok!(!run(d, "Normal uno")?.bold, "sigue negrita tras deshacer"); }
        "rehacer" => { ok!(run(d, "Normal uno")?.bold, "no volvió la negrita"); }
        "imagen_insertar" => { let i = images(para(d, "Final")?); ok!(i.len() == 1, "imágenes={}", i.len()); ok!((i[0].1 / i[0].2 - 2.0).abs() < 0.1, "proporción {:?}", i[0]); }
        "tabla_insertar" => { let t = table_n(d, 1).or_else(|_| Err("sin tabla nueva".to_string()))?; ok!(shape(t) == "3,3" || t.rows.len() == 2, "forma {}", shape(t)); }
        "enlace_seleccion" => { let l = run(d, "Normal uno")?.link; ok!(l == "https://ejemplo.cl/", "link={l}"); }
        "enlace_texto" => { let l = run(d, "Mi sitio")?.link; ok!(l == "https://ejemplo.cl/", "link={l}"); }
        "enlace_sin_esquema" => { let l = run(d, "Sin esquema")?.link; ok!(l == "https://ejemplo.cl", "link={l}"); }
        "salto_pagina" => { ok!(para(d, "Final")?.text.contains('\u{000C}'), "sin salto"); }
        "linea" => { ok!(paras(d).iter().any(|p| p.props.borders.as_ref().is_some_and(|b| b.bottom.is_some())), "sin línea"); }
        "simbolo" => { ok!(para(d, "Final©").is_ok(), "sin símbolo"); }
        "titulo1" => { ok!(heading(d, "Normal uno")? == Some(1), "{:?}", heading(d, "Normal uno")?); }
        "titulo2" => { ok!(heading(d, "Normal uno")? == Some(2), "{:?}", heading(d, "Normal uno")?); }
        "titulo3" => { ok!(heading(d, "Normal uno")? == Some(3), "{:?}", heading(d, "Normal uno")?); }
        "titulo_a_normal" => { ok!(heading(d, "Titulo uno")?.is_none(), "sigue título"); }
        "cita" => { ok!(wordbridge::render::block_kind(d, para(d, "Normal uno")?) == wordbridge::render::BlockKind::Quote, "no es cita"); }
        "codigo" => { let f = run(d, "Normal uno")?.font; ok!(f == "Courier New", "font={f}"); }
        "centrar" => { ok!(rp("Normal uno")?.align == Align::Center, "{:?}", rp("Normal uno")?.align); }
        "derecha" => { ok!(rp("Normal uno")?.align == Align::Right, "{:?}", rp("Normal uno")?.align); }
        "justificar" => { ok!(rp("Normal uno")?.align == Align::Justify, "{:?}", rp("Normal uno")?.align); }
        "izquierda_de_centrado" => { ok!(rp("Centrado par")?.align == Align::Left, "{:?}", rp("Centrado par")?.align); }
        "vinetas" => { let f = num_fmt(d, "Normal uno")?; ok!(f.is_some(), "sin lista"); }
        "vineta_cuadrada" => { let f = num_fmt(d, "Normal uno")?; ok!(f.is_some(), "sin lista"); let n = para(d, "Normal uno")?.props.numbering.unwrap(); let t = format!("{:?}", d.numbering.level(n.num, 0).unwrap()); ok!(t.contains('▪'), "{t}"); }
        "num_decimal" => { let f = num_fmt(d, "Normal uno")?.ok_or("sin lista")?; ok!(f.0 == "Decimal", "{}", f.0); }
        "num_romana" => { let f = num_fmt(d, "Normal uno")?.ok_or("sin lista")?; ok!(f.0 == "LowerRoman", "{}", f.0); }
        "num_letra" => { let f = num_fmt(d, "Normal uno")?.ok_or("sin lista")?; ok!(f.0.contains("Letter"), "{}", f.0); }
        "lista_quitar_nueva" => { ok!(num_fmt(d, "Normal uno")?.is_none(), "sigue en lista"); }
        "lista_cambiar_nueva" => { let f = num_fmt(d, "Normal uno")?.ok_or("sin lista")?; ok!(f.0 == "Decimal", "{}", f.0); }
        "lista_word_a_numerada" | "lista_word_otro_tipo" => {
            for t in ["Item uno", "Item dos"] { let f = num_fmt(d, t)?.ok_or(format!("{t} sin lista"))?; ok!(f.0 == "Decimal", "{t} sigue {}", f.0); }
            ok!(num_fmt(d, "Numero uno")?.is_some(), "tocó otra lista");
            ok!(d.body.len() == o.body.len(), "bloques {} vs {}", d.body.len(), o.body.len());
        }
        "lista_word_quitar" => {
            for t in ["Item uno", "Item dos"] { ok!(num_fmt(d, t)?.is_none(), "{t} sigue en lista"); }
            ok!(num_fmt(d, "Numero uno")?.is_some(), "tocó otra lista");
        }
        "lista_word_quitar_seleccion" => { ok!(num_fmt(d, "Item uno")?.is_none(), "sigue en lista"); ok!(num_fmt(d, "Item dos")?.is_some(), "se extendió al otro ítem"); }
        "lista_word_numerada_a_vinetas" => { let f = num_fmt(d, "Numero uno")?.ok_or("sin lista")?; ok!(f.0 == "Bullet", "sigue {}", f.0); }
        "lista_word_mantiene" => { ok!(num_fmt(d, "Item uno")?.is_some(), "perdió la lista"); ok!(para(d, "Item unoX").is_ok(), "sin texto"); }
        "sangria_mas" => { ok!(rp("Normal uno")?.indent_left == 36.0, "{}", rp("Normal uno")?.indent_left); }
        "sangria_menos" => { ok!(rp("Normal uno")?.indent_left == 0.0, "{}", rp("Normal uno")?.indent_left); }
        "sangria_lista_nueva" => { let f = num_fmt(d, "Normal uno")?.ok_or("sin lista")?; ok!(f.1 == 1, "nivel {}", f.1); }
        "sangria_lista_word" => { let f = num_fmt(d, "Item dos")?.ok_or("sin lista")?; ok!(f.1 == 1 || rp("Item dos")?.indent_left > rp("Item uno")?.indent_left, "nivel {} sangría {}", f.1, rp("Item dos")?.indent_left); }
        "interlineado" => { ok!(rp("Normal uno")?.line_spacing == LineSpacing::Multiple(1.5), "{:?}", rp("Normal uno")?.line_spacing); }
        "interlineado_sencillo" => { ok!(rp("Normal uno")?.line_spacing == LineSpacing::Multiple(1.0), "{:?}", rp("Normal uno")?.line_spacing); }
        "espacio_antes" => { ok!(rp("Normal uno")?.space_before == 12.0, "{}", rp("Normal uno")?.space_before); }
        "espacio_despues" => { ok!(rp("Normal uno")?.space_after == 18.0, "{}", rp("Normal uno")?.space_after); }
        "espacio_cero" => { ok!(rp("Normal uno")?.space_after == 0.0, "{}", rp("Normal uno")?.space_after); }
        "hoja_a4" => { let s = &d.last_section; ok!((s.page_w - 595.3).abs() < 1.0 && (s.page_h - 841.9).abs() < 1.0, "{}x{}", s.page_w, s.page_h); }
        "hoja_horizontal" => { let s = &d.last_section; ok!(s.landscape && s.page_w > s.page_h, "{}x{}", s.page_w, s.page_h); }
        "margenes" => { let s = &d.last_section; ok!((s.margin_top, s.margin_right, s.margin_bottom, s.margin_left) == (36.0, 40.0, 50.0, 60.0), "{:?}", (s.margin_top, s.margin_right, s.margin_bottom, s.margin_left)); }
        "reemplazar" => { ok!(para(d, "Buscar termino y palabra").is_ok(), "{:?}", para(d, "Buscar").map(|p| p.plain_text())); }
        "reemplazar_todo" => { ok!(para(d, "Buscar termino y termino").is_ok(), "{:?}", para(d, "Buscar").map(|p| p.plain_text())); }
        "img_porcentaje" => { let i = images(para(d, "Foto")?); ok!((i[0].1 - 234.0).abs() < 2.0 && (i[0].1 / i[0].2 - 2.0).abs() < 0.05, "{:?}", i[0]); }
        "img_100" => { let i = images(para(d, "Foto")?); ok!((i[0].1 / i[0].2 - 2.0).abs() < 0.02, "proporción deformada {:?} (w/h={})", i[0], i[0].1 / i[0].2); }
        "img_tamano" => { let i = images(para(d, "Foto")?); ok!((i[0].1, i[0].2) == (150.0, 50.0), "{:?}", i[0]); }
        "img_alinear" => { ok!(rp("Foto")?.align == Align::Center, "{:?}", rp("Foto")?.align); }
        "img_girar" => { let i = images(para(d, "Foto")?); ok!(i.len() == 1, "imágenes={}", i.len()); ok!((i[0].1 - 50.0).abs() < 1.0 && (i[0].2 - 100.0).abs() < 1.0, "{:?}", i[0]); ok!(d.media.get(&i[0].0).map(|b| b.len()) == Some(file_len("rot.png")), "no es el archivo girado"); }
        "img_reemplazar" => { let i = images(para(d, "Foto")?); ok!(i.len() == 1, "imágenes={}", i.len()); ok!(d.media.get(&i[0].0).map(|b| b.len()) == Some(file_len("other.png")), "no es el archivo nuevo"); }
        "img_eliminar" => { ok!(images(para(d, "Foto")?).is_empty(), "sigue la imagen"); }
        "tabla_fila_arriba" => { let t = table(d)?; ok!(t.rows.len() == 4 && cell_text(t, 2, 0) == "" && cell_text(t, 3, 0) == "A3", "{} filas; {}", t.rows.len(), cell_text(t, 2, 0)); }
        "tabla_fila_abajo" => { let t = table(d)?; ok!(t.rows.len() == 4 && cell_text(t, 3, 0) == "" && cell_text(t, 2, 0) == "A3", "{} filas", t.rows.len()); }
        "tabla_col_izq" => { let t = table(d)?; ok!(t.grid.len() == 4 && cell_text(t, 0, 0) == "" && cell_text(t, 0, 1) == "A1", "{} cols, {}", t.grid.len(), cell_text(t, 0, 0)); }
        "tabla_col_der" => { let t = table(d)?; ok!(t.grid.len() == 4 && cell_text(t, 0, 1) == "" && cell_text(t, 0, 2) == "B1", "{} cols", t.grid.len()); }
        "tabla_borrar_fila" => { let t = table(d)?; ok!(t.rows.len() == 2 && cell_text(t, 1, 0) == "A3", "{} filas", t.rows.len()); }
        "tabla_borrar_col" => { let t = table(d)?; ok!(t.grid.len() == 2 && cell_text(t, 0, 1) == "C1", "{} cols", t.grid.len()); }
        "tabla_borrar" => { ok!(table(d).is_err(), "sigue la tabla"); }
        "tabla_combinar" => { let t = table(d)?; ok!(t.rows[0].cells[0].props.span == 2 && t.rows[0].cells.len() == 2, "span {}", t.rows[0].cells[0].props.span); ok!(cell_text(t, 0, 0).contains("A1") && cell_text(t, 0, 0).contains("B1"), "texto {}", cell_text(t, 0, 0)); }
        "tabla_combinar_vertical" => { use wordcraft_doc::props::VMerge; let t = table(d)?; ok!(t.rows[0].cells[0].props.vmerge == VMerge::Restart && t.rows[1].cells[0].props.vmerge == VMerge::Continue, "{:?}", t.rows[0].cells[0].props.vmerge); }
        "tabla_dividir_col" => { let t = table(d)?; ok!(t.grid.len() == 4, "{} cols forma {}", t.grid.len(), shape(t)); ok!(t.rows[1].cells.len() == 4, "forma {}", shape(t)); }
        "tabla_dividir_fila" => { let t = table(d)?; ok!(t.rows.len() == 4, "{} filas", t.rows.len()); }
        "tabla_sombra" => { let t = table(d)?; ok!(t.rows[0].cells[0].props.shading.is_some(), "sin sombra"); ok!(t.rows[1].cells[0].props.shading.is_none(), "se extendió"); }
        "tabla_sombra_quitar" => { let t = table(d)?; ok!(t.rows[0].cells[0].props.shading.is_none(), "sigue sombra"); }
        "tabla_sin_bordes" => { let t = table(d)?; let b = t.props.borders.as_ref().ok_or("sin dato")?; ok!(b.top.as_ref().is_some_and(|s| s.style == BorderStyle::None), "{:?}", b.top); }
        "tabla_con_bordes" => { let t = table(d)?; let b = t.props.borders.as_ref().ok_or("sin dato")?; ok!(b.top.as_ref().is_some_and(|s| s.style != BorderStyle::None), "{:?}", b.top); }
        "tabla_centro" => { let t = table(d)?; ok!(t.rows[0].cells[0].props.valign == Some(VAlign::Center), "{:?}", t.rows[0].cells[0].props.valign); }
        "tabla_abajo" => { let t = table(d)?; ok!(t.rows[0].cells[0].props.valign == Some(VAlign::Bottom), "{:?}", t.rows[0].cells[0].props.valign); }
        "tabla_igualar" => { let t = table(d)?; ok!(t.grid.iter().all(|w| (w - t.grid[0]).abs() < 0.5), "{:?}", t.grid); }
        "tabla_texto" => { let t = table(d)?; ok!(cell_text(t, 0, 0) == "A1 nuevo", "{}", cell_text(t, 0, 0)); ok!(t.rows.len() == 3, "filas"); }
        "tabla_negrita_celda" => { ok!(run(d, "B2")?.bold, "no negrita"); ok!(table(d)?.rows.len() == 3, "filas"); }
        "encabezado_texto" => { ok!(text_of(head(d, 'h', 'd')?).contains("Encabezado baseXYZ"), "{}", text_of(head(d, 'h', 'd')?)); ok!(text_of(head(d, 'f', 'd')?) == "Pie base", "pie tocado"); }
        "encabezado_negrita" => { let h = head(d, 'h', 'd')?; let p = h[0].as_para().ok_or("no párrafo")?; ok!(look_of(d, p, p.run_ranges().next().unwrap().1).bold, "no negrita"); }
        "encabezado_tres" => { let h = head(d, 'h', 'd')?; ok!(h.iter().any(|b| b.as_table().is_some()), "sin tabla"); ok!(text_of(h).contains("Escribe aquí"), "{}", text_of(h)); }
        "encabezado_titulo" => { ok!(text_of(head(d, 'h', 'd')?) == "Mi doc", "{}", text_of(head(d, 'h', 'd')?)); }
        "encabezado_logo" => { let h = head(d, 'h', 'd')?; let p = h[0].as_para().ok_or("no párrafo")?; ok!(images(p).len() == 1, "sin logo"); ok!(p.plain_text().contains("Mi doc"), "sin título"); }
        "encabezado_vacio" => { ok!(d.last_section.headers.default.is_none(), "sigue: {}", text_of(head(d, 'h', 'd')?)); }
        "encabezado_tabla" => { ok!(head(d, 'h', 'd')?.iter().any(|b| b.as_table().is_some()), "sin tabla"); }
        "encabezado_imagen" => { let mut v = vec![]; walk(head(d, 'h', 'd')?, &mut v); ok!(v.iter().any(|p| !images(p).is_empty()), "sin imagen"); }
        "pie_pagina_num" => { let f = head(d, 'f', 'd')?; let mut v = vec![]; walk(f, &mut v); let fl: Vec<String> = v.iter().flat_map(|p| fields(p)).collect(); ok!(fl == ["PAGE", "NUMPAGES"], "{fl:?}"); ok!(v.iter().any(|p| d.styles.resolve_para(&p.props).align == Align::Right), "sin alinear"); }
        "num_en_cursor" => { ok!(fields(para(d, "Final")?) == ["PAGE"], "{:?}", fields(para(d, "Final")?)); }
        "num_quitar" => { let f = head(d, 'f', 'd')?; let mut v = vec![]; walk(f, &mut v); ok!(v.iter().all(|p| fields(p).is_empty()), "sigue campo"); ok!(text_of(f).contains("Pie base"), "se perdió el pie"); }
        "primera_diferente" => { ok!(d.last_section.title_page, "sin title_page"); ok!(text_of(head(d, 'h', '1')?).contains("Portada"), "{}", text_of(head(d, 'h', '1')?)); ok!(text_of(head(d, 'h', 'd')?).contains("Encabezado base"), "cambió el general"); }
        "pares_impares" => { ok!(d.settings.even_odd_headers, "sin ajuste"); ok!(text_of(head(d, 'h', 'e')?).contains("Par"), "{}", text_of(head(d, 'h', 'e')?)); }
        "numeracion" => { use wordcraft_doc::section::NumFormat; let s = &d.last_section; ok!(s.page_num_format == NumFormat::LowerRoman && s.page_num_start == Some(5), "{:?} {:?}", s.page_num_format, s.page_num_start); }
        "distancia" => { let s = &d.last_section; ok!((s.header - 50.0).abs() < 0.5 && (s.footer - 20.0).abs() < 0.5, "{} {}", s.header, s.footer); }
        "encabezado_a_pie" => { ok!(text_of(head(d, 'f', 'd')?).contains("Pie baseXYZ"), "{}", text_of(head(d, 'f', 'd')?)); }
        "nota_exporta_formato" => { ok!(run(d, "Hola")?.bold, "no negrita"); ok!(run(d, "mundo")?.color == "#FF0000", "sin color"); }
        "deshacer_parcial" => { ok!(run(d, "Normal uno")?.bold, "el 1er cambio visible no llega a Word"); ok!(!run(d, "Final")?.bold, "no se deshizo"); }
        "deshacer_texto_previo" => { ok!(para(d, "FinalABC").is_ok(), "texto escrito antes no llega a Word: {:?}", para(d, "Final").map(|p| p.plain_text())); ok!(!run(d, "Normal uno")?.italic, "no se deshizo"); }
        "lista_dos_items_sangria" => { ok!(para(d, "Normal uno").is_ok(), "se perdió 'Normal uno'"); let f = num_fmt(d, "resto"); ok!(f.is_ok(), "se perdió el 2º ítem"); ok!(f.unwrap().map(|x| x.1) == Some(1), "nivel"); }
        "lista_sangria_y_vuelta" => { let f = num_fmt(d, "Normal uno")?.ok_or("sin lista")?; ok!(f.1 == 0, "nivel {}", f.1); }
        "img_nueva_100" => { let i = images(para(d, "Final")?); ok!(i.len() == 1, "imágenes {}", i.len()); ok!((i[0].1 / i[0].2 - 2.0).abs() < 0.02, "deformada: {} x {} (w/h={:.3}, debería ser 2)", i[0].1, i[0].2, i[0].1 / i[0].2); }
        "img_girar_100" => { let i = images(para(d, "Foto")?); ok!((i[0].1 / i[0].2 - 0.5).abs() < 0.01, "deformada: {} x {} (w/h={:.3}, debería ser 0.5)", i[0].1, i[0].2, i[0].1 / i[0].2); }
        "seleccion_varios_parrafos" => { ok!(run(d, "Normal uno")?.color == "#00FF00", "1"); ok!(run(d, "Negrita run")?.color == "#00FF00", "2"); ok!(run(d, "Negrita run")?.bold, "perdió negrita"); }
        "color_varias_celdas" => { ok!(run(d, "A1")?.color == "#00FF00", "A1"); ok!(run(d, "B1")?.color == "#00FF00", "B1"); ok!(table(d)?.rows.len() == 3 && table(d)?.grid.len() == 3, "forma"); }
        "enter_parte_parrafo" => { ok!(para(d, "Normal uno").is_ok(), "1ª parte"); ok!(para(d, "Z dos tres").is_ok() || para(d, "Zdos tres").is_ok(), "2ª parte {:?}", paras(d).iter().take(3).map(|p| p.plain_text()).collect::<Vec<_>>()); ok!(d.body.len() == o.body.len() + 1, "bloques {} vs {}", d.body.len(), o.body.len()); }
        "titulo_con_negrita_previa" => { ok!(heading(d, "Negrita")? == Some(2), "no título"); ok!(run(d, "Negrita run")?.bold, "perdió negrita"); }
        "alinear_lista_word" => { ok!(rp("Item uno")?.align == Align::Center, "{:?}", rp("Item uno")?.align); ok!(num_fmt(d, "Item uno")?.is_some(), "perdió la lista"); }
        "interlineado_lista_nueva" => { ok!(rp("Normal uno")?.line_spacing == LineSpacing::Multiple(2.0), "{:?}", rp("Normal uno")?.line_spacing); ok!(num_fmt(d, "Normal uno")?.is_some(), "sin lista"); }
        "lectura_resaltar" => { let c = run(d, "Normal uno")?.bg; ok!(c == "#FFFF00", "bg={c}"); }
        "borrar_parrafo" => { ok!(para(d, "Normal uno").is_err(), "sigue el texto"); ok!(para(d, "Negrita run").is_ok(), "colateral"); }
        "tabla_ops_y_texto" => { let t = table(d)?; ok!(t.rows[0].cells[0].props.span == 2, "span"); ok!(t.rows.len() == 4, "{} filas", t.rows.len()); ok!(para(d, "C2!").is_ok(), "texto"); }
        "pagina_sin_margenes_tabla" => { let s = &d.last_section; ok!(s.landscape && s.margin_left == 36.0, "hoja"); ok!(table(d)?.rows.len() == 4, "filas"); }
        "salto_medio_parrafo" | "salto_fin_parrafo" => { ok!(para(d, "Normal uno")?.text.contains('\u{000C}'), "sin salto"); }
        "img_nueva_100_horizontal" => { let i = images(para(d, "Final")?); ok!((i[0].1 / i[0].2 - 2.0).abs() < 0.02, "deformada: {} x {} (w/h={:.3}, debería ser 2)", i[0].1, i[0].2, i[0].1 / i[0].2); }
        "fidelidad_sample" => {
            let s = wordbridge::open(SAMPLE, "docx").map_err(|e| e.to_string())?;
            let (a, b) = (paras(&s), paras(d));
            ok!(a.len() == b.len(), "párrafos {} vs {}", a.len(), b.len());
            let mut diffs = vec![];
            for (i, (x, y)) in a.iter().zip(b.iter()).enumerate() {
                if x.plain_text() != y.plain_text() { diffs.push(format!("#{i} texto '{}' vs '{}'", x.plain_text(), y.plain_text())); continue; }
                if x.objects.len() != y.objects.len() { diffs.push(format!("#{i} objetos {} vs {}", x.objects.len(), y.objects.len())); }
                // Letra por letra: dos trozos iguales pueden unirse en uno sin que nada cambie a la vista
                let ly = |p: &Paragraph, doc: &Document| p.run_ranges().flat_map(|(r, c)| { let l = format!("{:?}", look_of(doc, p, c)); p.text[r].chars().filter(|ch| *ch != '\u{FFFC}').map(move |ch| (ch, l.clone())).collect::<Vec<_>>() }).collect::<Vec<_>>();
                if ly(x, &s) != ly(y, d) { diffs.push(format!("#{i} formato")); }
                let (rx, rz) = (s.styles.resolve_para(&x.props), d.styles.resolve_para(&y.props));
                if rx.align != rz.align || rx.indent_left != rz.indent_left || rx.line_spacing != rz.line_spacing || rx.numbering.is_some() != rz.numbering.is_some() { diffs.push(format!("#{i} párrafo")); }
            }
            ok!(diffs.is_empty(), "{} diferencias: {:?}", diffs.len(), diffs.iter().take(6).collect::<Vec<_>>());
        }
        "sin_tocar" => {
            ok!(d.body.len() == o.body.len(), "bloques {} vs {}", d.body.len(), o.body.len());
            for (a, b) in d.body.iter().zip(o.body.iter()) { ok!(a.as_para().map(|p| p.plain_text()) == b.as_para().map(|p| p.plain_text()), "texto distinto"); }
        }
        _ => return Err("SIN VERIFICADOR".into()),
    }
    Ok(())
}

fn main() {
    let a: Vec<String> = std::env::args().collect();
    let base = Path::new(&a[2]);
    let media = base.join("media");
    let fixture = base.join("fixture.docx");
    match a[1].as_str() {
        "fixture" => {
            wordbridge::to_docx("", "docx", FIXTURE, media.to_str().unwrap(), fixture.to_str().unwrap()).unwrap();
            let html = wordbridge::to_html(fixture.to_str().unwrap(), "docx", media.to_str().unwrap()).unwrap();
            std::fs::write(base.join("fixture.html"), html).unwrap();
            let m2 = base.join("media2");
            std::fs::create_dir_all(&m2).unwrap();
            std::fs::write(base.join("sample.html"), wordbridge::to_html(SAMPLE, "docx", m2.to_str().unwrap()).unwrap()).unwrap();
        }
        "check" => {
            let cases: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(base.join("cases.json")).unwrap()).unwrap();
            let orig = wordbridge::open(fixture.to_str().unwrap(), "docx").unwrap();
            let out_dir = base.join("out");
            std::fs::create_dir_all(&out_dir).unwrap();
            let (mut pass, mut fail) = (0, 0);
            for c in cases.as_array().unwrap() {
                let name = c["name"].as_str().unwrap();
                let group = c["group"].as_str().unwrap_or("");
                let model = c["model"].as_str().unwrap_or("");
                let mut res: Result<(), String> = if !c["error"].is_null() {
                    Err(format!("JS: {}", c["error"]))
                } else {
                    let out = out_dir.join(format!("{name}.docx"));
                    let source = if c["blank"].as_bool().unwrap_or(false) { "" } else if c["source"].as_str() == Some("sample") { SAMPLE } else { fixture.to_str().unwrap() };
                    wordbridge::to_docx(source, "docx", model, media.to_str().unwrap(), out.to_str().unwrap())
                        .and_then(|_| wordbridge::open(out.to_str().unwrap(), "docx"))
                        .and_then(|d| {
                            // También debe poder volver a mostrarse en el editor
                            wordbridge::to_html(out.to_str().unwrap(), "docx", out_dir.to_str().unwrap())?;
                            check(name, &d, &orig, &media)
                        })
                };
                if res.is_ok() && !c["reopenSame"].as_bool().unwrap_or(true) {
                    res = Err("el modelo cambia al reabrir el HTML guardado".into());
                }
                match &res {
                    Ok(()) => { pass += 1; println!("OK    {group:9} {name}"); }
                    Err(e) => { fail += 1; println!("FALLA {group:9} {name}: {e}"); }
                }
            }
            println!("\nTOTAL ok={pass} fallan={fail}");
            if fail > 0 { std::process::exit(1); }
        }
        _ => {}
    }
}
