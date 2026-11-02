# PDFBox (lectura de PDF para extraer texto)
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**

# Puente JavaScript del editor
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
