//! Herramienta de prueba: `cli html <docx> <dir>` o `cli docx <docx> <model.json> <dir> <out>`.
fn main() {
    let a: Vec<String> = std::env::args().collect();
    match a[1].as_str() {
        "html" => println!("{}", wordbridge::to_html(&a[2], "docx", &a[3]).unwrap()),
        "docx" => {
            let model = std::fs::read_to_string(&a[3]).unwrap();
            wordbridge::to_docx(&a[2], "docx", &model, &a[4], &a[5]).unwrap();
            let doc = wordbridge::open(&a[5], "docx").unwrap();
            for b in &doc.body {
                if let Some(p) = b.as_para() {
                    let runs: Vec<String> = p
                        .run_ranges()
                        .map(|(r, c)| format!("[{}|b={:?} hl={:?} col={:?} sz={:?}]", &p.text[r], c.bold, c.highlight, c.color, c.size))
                        .collect();
                    println!("P style={:?} num={:?} :: {}", p.props.style, p.props.numbering, runs.join(""));
                } else {
                    println!("TABLE");
                }
            }
        }
        _ => {}
    }
}
