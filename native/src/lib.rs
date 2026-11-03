//! Puente entre la app y el núcleo de WordCraft (lectura y escritura de Word).

pub mod rebuild;
pub mod render;

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::Path;

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;
use wordcraft_doc::Document;

/// Lee un .docx/.doc; sin ruta, un documento en blanco.
pub fn open(path: &str, ext: &str) -> Result<Document, String> {
    if path.is_empty() {
        return Ok(Document::new());
    }
    let bytes = std::fs::read(path).map_err(|e| e.to_string())?;
    let mut doc = match ext.to_ascii_lowercase().as_str() {
        "doc" => wordcraft_docbin::read(&bytes).map_err(|e| e.to_string())?,
        "docx" | "docm" | "dotx" | "dotm" => wordcraft_docx::read(&bytes).map_err(|e| e.to_string())?,
        _ => Document::new(),
    };
    doc.ensure_nonempty();
    Ok(doc)
}

/// HTML para el editor; las imágenes se escriben en `media_dir`.
pub fn to_html(path: &str, ext: &str, media_dir: &str) -> Result<String, String> {
    let doc = open(path, ext)?;
    Ok(render::Renderer::new(&doc, Path::new(media_dir)).render())
}

/// Escribe en `out` el .docx que resulta de aplicar el modelo del editor al original. Sin modelo
/// (documento que no se abrió en el editor), el original tal cual.
pub fn to_docx(path: &str, ext: &str, model: &str, media_dir: &str, out: &str) -> Result<(), String> {
    let orig = open(path, ext)?;
    let doc = if model.trim().is_empty() {
        orig
    } else {
        let model: rebuild::Model = serde_json::from_str(model).map_err(|e| format!("modelo inválido: {e}"))?;
        rebuild::rebuild(&orig, &model, Path::new(media_dir))
    };
    let bytes = wordcraft_docx::write(&doc).map_err(|e| e.to_string())?;
    std::fs::write(out, bytes).map_err(|e| e.to_string())
}

// ---------- JNI ----------

fn arg(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(Into::into).unwrap_or_default()
}

fn guarded<T>(f: impl FnOnce() -> Result<T, String>) -> Result<T, String> {
    catch_unwind(AssertUnwindSafe(f)).unwrap_or_else(|_| Err("error interno al procesar el documento".into()))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_estudia_app_importers_WordBridge_toHtml<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    path: JString<'l>,
    ext: JString<'l>,
    media_dir: JString<'l>,
) -> jstring {
    let (path, ext, media_dir) = (arg(&mut env, &path), arg(&mut env, &ext), arg(&mut env, &media_dir));
    match guarded(|| to_html(&path, &ext, &media_dir)) {
        Ok(html) => env.new_string(html).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()),
        Err(e) => {
            let _ = env.throw_new("java/io/IOException", e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_estudia_app_importers_WordBridge_toDocx<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    path: JString<'l>,
    ext: JString<'l>,
    model: JString<'l>,
    media_dir: JString<'l>,
    out: JString<'l>,
) {
    let path = arg(&mut env, &path);
    let ext = arg(&mut env, &ext);
    let model = arg(&mut env, &model);
    let media_dir = arg(&mut env, &media_dir);
    let out = arg(&mut env, &out);
    if let Err(e) = guarded(|| to_docx(&path, &ext, &model, &media_dir, &out)) {
        let _ = env.throw_new("java/io/IOException", e);
    }
}
