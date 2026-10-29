// La app de Android de Corazón Dormido: una actividad a pantalla completa con el juego dentro (sin navegador).
// El juego lo mete en assets/juego herramientas/android.py; la versión llega con -PversionCode y -PversionName.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val codigo = (findProperty("versionCode") as String?)?.toInt() ?: 1
val nombre = (findProperty("versionName") as String?) ?: "0.7.0"

android {
    namespace = "com.corazondormido.juego"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.corazondormido.juego"
        minSdk = 26
        targetSdk = 36
        versionCode = codigo
        versionName = nombre
    }

    signingConfigs {
        // Llave de prueba (pública, como la de debug): todos los APK de prueba se firman igual, así que se
        // actualizan encima del anterior sin perder la partida. Para la Play Store se usa la llave de subida
        // de los secretos del repo (ver README).
        create("prueba") {
            storeFile = file("prueba.keystore")
            storePassword = "android"
            keyAlias = "prueba"
            keyPassword = "android"
        }
        val llave = System.getenv("CD_LLAVE")
        if (!llave.isNullOrEmpty() && file(llave).exists()) {
            create("tienda") {
                storeFile = file(llave)
                storePassword = System.getenv("CD_LLAVE_CLAVE")
                keyAlias = System.getenv("CD_ALIAS")
                keyPassword = System.getenv("CD_ALIAS_CLAVE")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("tienda") ?: signingConfigs.getByName("prueba")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("prueba")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // la música ya va comprimida: así se lee directamente del APK
        noCompress += listOf("ogg")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.webkit:webkit:1.14.0")
}
