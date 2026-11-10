plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// cada release sube este numero y su tag debe coincidir, por ejemplo 1.0.1 y v1.0.1
val appVersion = providers.gradleProperty("appVersion").getOrElse("1.0.2")
val appVersionCode = appVersion.split(".").map(String::toInt).let { (major, minor, patch) ->
    major * 10_000 + minor * 100 + patch
}

android {
    namespace = "com.estudia.app"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.estudia.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    buildTypes {
        release {
            // Firma de prueba (la misma de debug): se actualiza sin borrar los datos.
            // Para publicar en Play Store hay que crear una firma propia.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
    }
}

kotlin {
    jvmToolchain(17)
}

// Núcleo de Word en Rust (native/, adaptado de WordCraft). Necesita cargo y cargo-ndk:
//   rustup target add aarch64-linux-android && cargo install cargo-ndk
val rustJniLibs = layout.buildDirectory.dir("rustJniLibs")
val buildRust by tasks.registering(Exec::class) {
    group = "build"
    description = "Compila native/ como biblioteca de Android"
    val crate = rootProject.file("native")
    workingDir = crate
    inputs.dir(crate.resolve("src"))
    inputs.files(crate.resolve("Cargo.toml"), crate.resolve("Cargo.lock"))
    outputs.dir(rustJniLibs)
    environment("ANDROID_NDK_HOME", android.ndkDirectory.absolutePath)
    // Fuera de OneDrive/Dropbox: la sincronización deja archivos a medias y rompe la compilación
    System.getenv("LOCALAPPDATA")?.let { environment("CARGO_TARGET_DIR", "$it/MisApuntes/cargo-target") }
    // Pocas tareas a la vez: con poca RAM, muchos rustc en paralelo fallan al reservar memoria
    environment("CARGO_BUILD_JOBS", "2")
    commandLine(
        "cargo", "ndk", "-t", "arm64-v8a", "--platform", "26",
        "-o", rustJniLibs.get().asFile.absolutePath, "build", "--release",
    )
}
android.sourceSets["main"].jniLibs.srcDir(rustJniLibs)
tasks.named("preBuild") { dependsOn(buildRust) }

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdfbox.android)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
}
