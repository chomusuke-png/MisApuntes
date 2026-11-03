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
