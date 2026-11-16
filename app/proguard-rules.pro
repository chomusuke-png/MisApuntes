# PDFBox (lectura de PDF para extraer texto)
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**

# Puente JavaScript del editor
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Voz natural: el codigo nativo de sherpa-onnx lee estas clases por nombre
-keep class com.k2fsa.sherpa.onnx.** { *; }

# commons-compress: formatos opcionales que la app no usa
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
