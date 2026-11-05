use std::path::PathBuf;

fn res(name: &str) -> String {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../app/src/test/resources").join(name).to_string_lossy().into_owned()
}

fn tmp(name: &str) -> PathBuf {
    let d = std::env::temp_dir().join("wordbridge-test").join(name);
    let _ = std::fs::remove_dir_all(&d);
    std::fs::create_dir_all(&d).unwrap();
    d
}

#[test]
fn docx_to_html_has_block_ids() {
    let dir = tmp("html");
    let html = wordbridge::to_html(&res("sample.docx"), "docx", dir.to_str().unwrap()).unwrap();
    assert!(html.starts_with("<article id=\"page\" data-src=\"wc\""));
    assert!(html.contains("data-ph=\""));
    assert!(html.contains("data-b=\"0\""));
    assert!(html.contains("data-r=\"0#0\""));
    println!("{html}");
}

#[test]
fn doc_to_html_works() {
    let dir = tmp("dochtml");
    let html = wordbridge::to_html(&res("sample.doc"), "doc", dir.to_str().unwrap()).unwrap();
    assert!(html.contains("data-b=\"0\""));
}

#[test]
fn untouched_document_round_trips() {
    let dir = tmp("keep");
    let orig = wordbridge::open(&res("sample.docx"), "docx").unwrap();
    let blocks: Vec<String> = (0..orig.body.len()).map(|i| format!("{{\"k\":\"{i}\"}}")).collect();
    let model = format!("{{\"blocks\":[{}]}}", blocks.join(","));
    let out = dir.join("out.docx");
    wordbridge::to_docx(&res("sample.docx"), "docx", &model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let again = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    assert_eq!(orig.body.len(), again.body.len());
    for (a, b) in orig.body.iter().zip(again.body.iter()) {
        assert_eq!(a.as_para().map(|p| p.plain_text()), b.as_para().map(|p| p.plain_text()));
    }
}

#[test]
fn edited_paragraph_keeps_untouched_runs_and_applies_changes() {
    let dir = tmp("edit");
    let orig = wordbridge::open(&res("sample.docx"), "docx").unwrap();
    let first = orig.body[0].as_para().unwrap();
    let first_props = first.run_ranges().next().unwrap().1.clone();
    // Párrafo 0: su primer run sin cambios + texto nuevo en rojo y negrita; un párrafo nuevo con
    // título 2; una lista nueva; el resto igual.
    let mut blocks = vec![
        r##"{"p":{"src":"0","tag":"p","segs":[{"t":"Hola ","r":"0#0","f":{}},{"t":"mundo","r":"0#0","f":{"b":true,"c":"#FF0000","bg":"#FFFF00"}}]}}"##.to_string(),
        r##"{"p":{"tag":"h2","segs":[{"t":"Título nuevo"}]}}"##.to_string(),
        r##"{"p":{"tag":"li","list":"ul","list_id":1,"segs":[{"t":"uno"}]}}"##.to_string(),
        r##"{"p":{"tag":"li","list":"ul","list_id":1,"segs":[{"t":"dos"}]}}"##.to_string(),
    ];
    for i in 1..orig.body.len() {
        blocks.push(format!("{{\"k\":\"{i}\"}}"));
    }
    let model = format!("{{\"blocks\":[{}]}}", blocks.join(","));
    let out = dir.join("out.docx");
    wordbridge::to_docx(&res("sample.docx"), "docx", &model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let doc = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    let p0 = doc.body[0].as_para().unwrap();
    assert_eq!(p0.plain_text(), "Hola mundo");
    let runs: Vec<_> = p0.run_ranges().map(|(_, p)| p.clone()).collect();
    assert_eq!(runs[0], first_props);
    assert_eq!(runs[1].bold, Some(true));
    assert_eq!(runs[1].highlight, Some(wordcraft_doc::props::Highlight::Yellow));
    let p1 = doc.body[1].as_para().unwrap();
    let style = doc.styles.get(p1.props.style.as_deref().unwrap()).unwrap();
    assert_eq!(style.name.to_ascii_lowercase(), "heading 2");
    let li = doc.body[2].as_para().unwrap();
    assert!(li.props.numbering.is_some());
    assert_eq!(doc.body.len(), orig.body.len() + 3);
}

#[test]
fn blank_document_from_model() {
    let dir = tmp("blank");
    let out = dir.join("out.docx");
    let model = r##"{"blocks":[{"p":{"tag":"h1","segs":[{"t":"Apuntes"}]}},{"p":{"tag":"p","segs":[{"t":"texto ","f":{}},{"t":"azul","f":{"c":"#0000FF","font":"Georgia","sz":14}}]}}]}"##;
    wordbridge::to_docx("", "", model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let doc = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    assert_eq!(doc.body.len(), 2);
    let p = doc.body[1].as_para().unwrap();
    let blue = p.run_ranges().nth(1).unwrap().1;
    assert_eq!(blue.font.as_deref(), Some("Georgia"));
    assert_eq!(blue.size, Some(14.0));
}

#[test]
fn new_table_link_and_rule() {
    let dir = tmp("insert");
    let out = dir.join("out.docx");
    let model = r##"{"blocks":[
        {"p":{"tag":"p","segs":[{"t":"ver ","f":{}},{"t":"sitio","f":{"link":"https://example.com"}}]}},
        {"t":{"src":null,"cells":[
            {"c":"0:0","blocks":[{"p":{"tag":"p","segs":[{"t":"A1"}]}}]},
            {"c":"0:1","blocks":[{"p":{"tag":"p","segs":[{"t":"B1"}]}}]},
            {"c":"1:0","blocks":[]},
            {"c":"1:1","blocks":[{"p":{"tag":"p","segs":[{"t":"B2"}]}}]}]}},
        {"p":{"tag":"p","rule":true,"segs":[]}}
    ]}"##;
    wordbridge::to_docx("", "", model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let doc = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    let p = doc.body[0].as_para().unwrap();
    assert_eq!(p.run_ranges().nth(1).unwrap().1.link.as_deref(), Some("https://example.com"));
    let t = doc.body[1].as_table().unwrap();
    assert_eq!((t.rows.len(), t.rows[0].cells.len()), (2, 2));
    assert_eq!(t.rows[1].cells[1].blocks[0].as_para().unwrap().plain_text(), "B2");
    let rule = doc.body[2].as_para().unwrap();
    assert!(rule.props.borders.as_ref().and_then(|b| b.bottom.as_ref()).is_some());
    let html = wordbridge::to_html(out.to_str().unwrap(), "docx", dir.to_str().unwrap()).unwrap();
    assert!(html.contains("<a href=\"https://example.com\">sitio</a>"));
    assert!(html.contains("border-bottom:"));
}

#[test]
fn paragraph_spacing_lists_and_quote() {
    let dir = tmp("para");
    let out = dir.join("out.docx");
    let model = r##"{"blocks":[
        {"p":{"tag":"blockquote","segs":[{"t":"cita"}]}},
        {"p":{"tag":"p","lh":1.5,"sb":12,"sa":6,"il":36,"segs":[{"t":"espaciado"}]}},
        {"p":{"tag":"li","list":"ol","list_style":"lower-roman","list_id":1,"segs":[{"t":"uno"}]}}
    ]}"##;
    wordbridge::to_docx("", "", model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let doc = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    assert_eq!(doc.body[0].as_para().unwrap().props.style.as_deref(), Some("Quote"));
    let p = &doc.body[1].as_para().unwrap().props;
    assert_eq!(p.line_spacing, Some(wordcraft_doc::props::LineSpacing::Multiple(1.5)));
    assert_eq!((p.space_before, p.space_after, p.indent_left), (Some(12.0), Some(6.0), Some(36.0)));
    let li = doc.body[2].as_para().unwrap().props.numbering.unwrap();
    let level = doc.numbering.level(li.num, 0).unwrap();
    assert_eq!(format!("{:?}", level.format), "LowerRoman");
    let html = wordbridge::to_html(out.to_str().unwrap(), "docx", dir.to_str().unwrap()).unwrap();
    assert!(html.contains("<blockquote data-b=\"0\""));
}

#[test]
fn word_list_can_be_removed_or_changed_from_editor() {
    let dir = tmp("wordlist");
    let source = dir.join("source.docx");
    let lists = r##"{"blocks":[
        {"p":{"tag":"li","list":"ul","list_id":1,"segs":[{"t":"uno"}]}},
        {"p":{"tag":"li","list":"ul","list_id":1,"segs":[{"t":"dos"}]}}]}"##;
    wordbridge::to_docx("", "docx", lists, dir.to_str().unwrap(), source.to_str().unwrap()).unwrap();
    let html = wordbridge::to_html(source.to_str().unwrap(), "docx", dir.to_str().unwrap()).unwrap();
    assert!(html.contains("data-lk=\"ul\""));

    // Al primero se le quita la lista; el segundo pasa a numeración
    let model = r##"{"blocks":[
        {"p":{"src":"0","tag":"p","nonum":true,"segs":[{"t":"uno","r":"0#0"}]}},
        {"p":{"src":"1","tag":"li","list":"ol","list_style":"decimal","list_id":1,"segs":[{"t":"dos","r":"1#0"}]}}]}"##;
    let out = dir.join("out.docx");
    wordbridge::to_docx(source.to_str().unwrap(), "docx", model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let doc = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    let num = |i: usize| doc.styles.resolve_para(&doc.body[i].as_para().unwrap().props).numbering.filter(|n| n.num != 0);
    assert!(num(0).is_none());
    let n = num(1).expect("numeración");
    assert_eq!(format!("{:?}", doc.numbering.level(n.num, 0).unwrap().format), "Decimal");
}

#[test]
fn resized_original_image_keeps_new_size() {
    let dir = tmp("img");
    let out = dir.join("out.docx");
    let orig = wordbridge::open(&res("sample.docx"), "docx").unwrap();
    let mut blocks: Vec<String> = (0..orig.body.len()).map(|i| format!("{{\"k\":\"{i}\"}}")).collect();
    blocks[8] = r##"{"p":{"src":"8","tag":"p","segs":[{"o":"8#0","w":120,"ih":60},{"t":"Imagen:","r":"8#1"}]}}"##.to_string();
    let model = format!("{{\"blocks\":[{}]}}", blocks.join(","));
    wordbridge::to_docx(&res("sample.docx"), "docx", &model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let doc = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    match &doc.body[8].as_para().unwrap().objects[0] {
        wordcraft_doc::InlineObject::Image { w, h, .. } => assert_eq!((*w, *h), (120.0, 60.0)),
        o => panic!("no es imagen: {o:?}"),
    }
}

#[test]
fn page_size_and_margins_from_editor() {
    let dir = tmp("page");
    let out = dir.join("out.docx");
    // A4 horizontal, márgenes estrechos
    let model = r##"{"page":{"w":841.9,"h":595.3,"mt":36,"mr":36,"mb":36,"ml":36},"blocks":[{"p":{"tag":"p","segs":[{"t":"hoja"}]}}]}"##;
    wordbridge::to_docx("", "", model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let doc = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    let s = &doc.last_section;
    assert!((s.page_w - 841.9).abs() < 1.0 && (s.page_h - 595.3).abs() < 1.0, "{} x {}", s.page_w, s.page_h);
    assert!(s.landscape);
    assert_eq!((s.margin_top, s.margin_left), (36.0, 36.0));
    let html = wordbridge::to_html(out.to_str().unwrap(), "docx", dir.to_str().unwrap()).unwrap();
    assert!(html.contains("data-ph=\"595"));
}

#[test]
fn grid_table_with_merges_shading_and_alignment() {
    use wordcraft_doc::Block;
    use wordcraft_doc::props::{VAlign, VMerge};
    let dir = tmp("grid");
    // 3 columnas: fila 0 con A1 combinada a lo ancho (2) y C1 combinada hacia abajo (2 filas);
    // fila 1: A2 sombreada y centrada, B2, y la continuación de C1. Sin bordes.
    let model = r##"{"blocks":[{"t":{"src":null,"grid":[100,100,200],"bd":false,"rows":[
        {"tr":null,"cells":[
            {"s":2,"m":0,"set":true,"bg":"","va":"","blocks":[{"p":{"tag":"p","segs":[{"t":"A1"}]}}]},
            {"s":1,"m":1,"set":true,"bg":"","va":"bottom","blocks":[{"p":{"tag":"p","segs":[{"t":"C1"}]}}]}]},
        {"tr":null,"cells":[
            {"s":1,"m":0,"set":true,"bg":"#FFFF00","va":"middle","blocks":[{"p":{"tag":"p","segs":[{"t":"A2"}]}}]},
            {"s":1,"m":0,"set":true,"bg":"","va":"","blocks":[]},
            {"s":1,"m":2,"set":true,"bg":"","va":"bottom","blocks":[]}]}
    ]}}]}"##;
    let out = dir.join("out.docx");
    wordbridge::to_docx("", "docx", model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let doc = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    let t = doc.body.iter().find_map(|b| if let Block::Table(t) = &**b { Some(t.clone()) } else { None }).expect("tabla");
    assert_eq!(t.grid.len(), 3);
    assert_eq!(t.rows.len(), 2);
    assert_eq!(t.rows[0].cells.len(), 2);
    assert_eq!(t.rows[0].cells[0].props.span, 2);
    assert_eq!(t.rows[0].cells[1].props.vmerge, VMerge::Restart);
    assert_eq!(t.rows[1].cells[2].props.vmerge, VMerge::Continue);
    assert_eq!(t.rows[0].cells[1].props.valign, Some(VAlign::Bottom));
    assert_eq!(t.rows[1].cells[0].props.valign, Some(VAlign::Center));
    assert!(t.rows[1].cells[0].props.shading.is_some());
    assert!(t.rows[1].cells[1].props.shading.is_none());
    let text: Vec<String> = t.rows[0].cells[0].blocks.iter().filter_map(|b| b.as_para().map(|p| p.plain_text())).collect();
    assert_eq!(text, ["A1"]);
    let b = t.props.borders.as_ref().unwrap();
    assert!(b.top.as_ref().is_none_or(|s| s.width == 0.0 || s.style == wordcraft_doc::props::BorderStyle::None));
}

#[test]
fn rich_header_and_footer_from_editor() {
    use wordcraft_doc::Block;
    use wordcraft_doc::para::InlineObject;
    let dir = tmp("hf");
    // Encabezado con texto y una tabla; pie con "Página N de M"
    let model = r##"{"blocks":[{"p":{"tag":"p","segs":[{"t":"Cuerpo"}]}}],
        "hf":{"hd":{"blocks":[
            {"p":{"tag":"p","align":"center","segs":[{"t":"Colegio","f":{"b":true}}]}},
            {"t":{"src":null,"rows":[{"tr":null,"cells":[
                {"s":1,"m":0,"blocks":[{"p":{"tag":"p","segs":[{"t":"Historia"}]}}]},
                {"s":1,"m":0,"blocks":[{"p":{"tag":"p","segs":[{"t":"2026"}]}}]}]}]}}]},
              "fd":{"blocks":[{"p":{"tag":"p","align":"right","segs":[{"t":"Página "},{"fld":"PAGE"},{"t":" de "},{"fld":"NUMPAGES"}]}}]}}}"##;
    let out = dir.join("out.docx");
    wordbridge::to_docx("", "docx", model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let doc = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    let s = &doc.last_section;
    let h = &doc.parts[&s.headers.default.expect("encabezado")];
    assert_eq!(h.blocks[0].as_para().unwrap().plain_text(), "Colegio");
    assert!(matches!(&*h.blocks[1], Block::Table(_)), "la tabla del encabezado");
    let f = &doc.parts[&s.footers.default.expect("pie")];
    let p = f.blocks[0].as_para().unwrap();
    let fields: Vec<String> = p.objects.iter().filter_map(|o| if let InlineObject::Field { instr, .. } = o { Some(instr.trim().to_string()) } else { None }).collect();
    assert_eq!(fields, ["PAGE", "NUMPAGES"]);

    // El editor lo muestra oculto en la hoja, con rutas propias y los campos marcados
    let html = wordbridge::to_html(out.to_str().unwrap(), "docx", dir.to_str().unwrap()).unwrap();
    assert!(html.contains("data-hf=\"hd\""));
    assert!(html.contains("data-b=\"hfhd.0\""));
    assert!(html.contains("data-f=\"NUMPAGES\""));

    // Sin tocar el primer párrafo (se copia) y con uno nuevo; el pie se quita
    let model2 = r##"{"blocks":[{"k":"0"}],"hf":{
        "hd":{"blocks":[{"k":"hfhd.0"},{"p":{"tag":"p","segs":[{"t":"Unidad 2"}]}}]},
        "fd":{"blocks":[{"p":{"tag":"p","segs":[]}}]}}}"##;
    let out2 = dir.join("out2.docx");
    wordbridge::to_docx(out.to_str().unwrap(), "docx", model2, dir.to_str().unwrap(), out2.to_str().unwrap()).unwrap();
    let doc2 = wordbridge::open(out2.to_str().unwrap(), "docx").unwrap();
    let h2 = &doc2.parts[&doc2.last_section.headers.default.unwrap()];
    let texts: Vec<String> = h2.blocks.iter().filter_map(|b| b.as_para().map(|p| p.plain_text())).collect();
    assert_eq!(texts, ["Colegio", "Unidad 2"]);
    assert_eq!(h2.blocks[0].as_para().unwrap().run_ranges().next().unwrap().1.bold, Some(true));
    assert!(doc2.last_section.footers.default.is_none());
}

#[test]
fn first_page_and_even_headers_from_editor() {
    let dir = tmp("hfo");
    let p = |t: &str| format!(r#"{{"blocks":[{{"p":{{"tag":"p","segs":[{{"t":"{t}"}}]}}}}]}}"#);
    let model = format!(
        r#"{{"blocks":[{{"p":{{"tag":"p","segs":[{{"t":"Cuerpo"}}]}}}}],"hfo":{{"tp":true,"eo":true}},"hf":{{"hd":{},"h1":{},"he":{}}}}}"#,
        p("Impar"),
        p("Portada"),
        p("Par")
    );
    let out = dir.join("out.docx");
    wordbridge::to_docx("", "docx", &model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let doc = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    let s = &doc.last_section;
    assert!(s.title_page);
    assert!(doc.settings.even_odd_headers);
    let text = |id: Option<u32>| doc.parts[&id.unwrap()].blocks[0].as_para().unwrap().plain_text();
    assert_eq!(text(s.headers.default), "Impar");
    assert_eq!(text(s.headers.first), "Portada");
    assert_eq!(text(s.headers.even), "Par");
    let html = wordbridge::to_html(out.to_str().unwrap(), "docx", dir.to_str().unwrap()).unwrap();
    assert!(html.contains("data-tp=\"1\"") && html.contains("data-eo=\"1\""));
    assert!(html.contains("data-hf=\"he\"") && html.contains("data-hf=\"h1\""));
    // Los pies no tienen nada propio: primera y pares quedan en blanco
    assert!(html.contains("data-hf=\"f1\"") && html.contains("data-hf=\"fe\""));
}

#[test]
fn page_numbering_and_header_distance_from_editor() {
    use wordcraft_doc::section::NumFormat;
    let dir = tmp("pnum");
    let model = r##"{"blocks":[{"p":{"tag":"p","segs":[{"t":"Cuerpo"}]}}],
        "hfo":{"tp":false,"eo":false,"fmt":"i","start":3,"hd":50,"fd":20},
        "hf":{"fd":{"blocks":[{"p":{"tag":"p","align":"center","segs":[{"fld":"PAGE"}]}}]}}}"##;
    let out = dir.join("out.docx");
    wordbridge::to_docx("", "docx", model, dir.to_str().unwrap(), out.to_str().unwrap()).unwrap();
    let doc = wordbridge::open(out.to_str().unwrap(), "docx").unwrap();
    let s = &doc.last_section;
    assert_eq!(s.page_num_format, NumFormat::LowerRoman);
    assert_eq!(s.page_num_start, Some(3));
    assert!((s.header - 50.0).abs() < 0.5 && (s.footer - 20.0).abs() < 0.5);
    let html = wordbridge::to_html(out.to_str().unwrap(), "docx", dir.to_str().unwrap()).unwrap();
    assert!(html.contains("data-pn=\"3\" data-pf=\"i\""));
}
