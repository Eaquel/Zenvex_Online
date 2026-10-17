plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "online.zenvex"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "online.zenvex"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        // Sabit anahtar: telefona her yeni sürüm, eskisini silmeden üstüne kurulabilsin.
        // (Mağaza sürümü için ayrı, gizli bir anahtar kullanılacak.)
        create("shared") {
            storeFile = rootProject.file("keystore/zenvex-dev.jks")
            storePassword = "zenvexdev"
            keyAlias = "zenvex"
            keyPassword = "zenvexdev"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("png", "json", "wav")
    }
}

// Rust kütüphanesini (libzenvex.so) cargo-ndk ile derleyip jniLibs'e koyar.
val rustDir = rootProject.file("../rust")
val jniLibsDir = file("src/main/jniLibs")
val cargoBuild by tasks.registering(Exec::class) {
    group = "rust"
    description = "Rust çekirdeğini Android için derler"
    workingDir = rustDir
    val targets = (System.getenv("RUST_ANDROID_TARGETS") ?: "arm64-v8a,armeabi-v7a,x86_64").split(",")
    val args = mutableListOf("cargo", "ndk")
    targets.forEach { args += listOf("-t", it.trim()) }
    args += listOf("-o", jniLibsDir.absolutePath, "build", "--release", "-p", "zenvex_jni")
    commandLine(args)
    inputs.dir(rustDir.resolve("zenvex_core/src"))
    inputs.dir(rustDir.resolve("zenvex_jni/src"))
    outputs.dir(jniLibsDir)
}
tasks.named("preBuild") { dependsOn(cargoBuild) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
