# Prueba de punta a punta del editor

Comprueba que cada herramienta del editor de documentos llegue al `.docx`:

1. `cases.js` ejecuta cada herramienta con el `editor.js` real en Edge sin interfaz.
2. El modelo resultante pasa por `native/` (WordCraft) y se escribe un `.docx`.
3. `verify` relee el archivo y comprueba el efecto de cada herramienta.

También revisa que el HTML guardado dé el mismo modelo al reabrir el documento.

```powershell
./tools/editor-e2e/run.ps1
```

Necesita `cargo`, `node` y Microsoft Edge. Lo generado queda en `build/`, que no se versiona.

No cubre los botones de la app ni el WebView del teléfono, y los archivos se releen con WordCraft,
no con Microsoft Word. El giro de imagen de Kotlin está simulado.
