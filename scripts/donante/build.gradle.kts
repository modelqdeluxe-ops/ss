/* La app del dueño para Google Play: los mismos recursos, assets y manifiesto de app/ (una variante ya preparada
   por scripts/variante.py) más Google Play Billing con todas sus dependencias (AndroidX, Play Services), que Gradle
   resuelve y une. Nuestro código (smali) no pasa por aquí: scripts/pagos.sh lo agrega después como un dex más.
   Los recursos de la app conservan sus números (--stable-ids, sacados de public.xml), porque el código smali los
   usa como constantes. */
plugins { id("com.android.application") version "8.7.3" }

fun p(n: String) = providers.gradleProperty(n).get()

android {
    namespace = "hn.hato.ganadero"
    compileSdk = 35
    defaultConfig {
        applicationId = p("paquete")
        minSdk = 21
        targetSdk = p("target").toInt()
        versionCode = p("vc").toInt()
        versionName = p("vn")
    }
    sourceSets["main"].apply {
        manifest.srcFile("manifiesto/AndroidManifest.xml")
        res.setSrcDirs(listOf("res"))
        assets.setSrcDirs(listOf(p("assets")))
        java.setSrcDirs(listOf("java"))
    }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    androidResources { additionalParameters += listOf("--stable-ids", file("ids.txt").absolutePath) }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

dependencies { implementation("com.android.billingclient:billing:" + p("billing")) }
