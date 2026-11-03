# wordbridge

Núcleo nativo de lectura y escritura de Word de la app. Usa las bibliotecas `wordcraft-doc`,
`wordcraft-docx` y `wordcraft-docbin` de [WordCraft](https://github.com/storytold/wordcraft)
(MIT o Apache-2.0; ver `LICENSE-WORDCRAFT-MIT` y `NOTICE-WORDCRAFT`).

- `render.rs`: documento → HTML del editor, con la ruta de cada bloque (`data-b`), run (`data-r`)
  y objeto (`data-o`) del original.
- `rebuild.rs`: modelo del editor (`editor.js`) + original → documento a guardar como .docx.

Gradle lo compila solo (`buildRust`). Requisitos: `rustup target add aarch64-linux-android` y
`cargo install cargo-ndk`. Pruebas en la PC: `cargo test`.
