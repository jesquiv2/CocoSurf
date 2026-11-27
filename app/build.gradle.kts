import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.cocosurf.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cocosurf.app"
        // M5 · GeckoView exige minSdk 26 (PLAN §18.4/§20). La TV objetivo
        // (Xiaomi, Android 11 / API 30) queda cubierta.
        minSdk = 26
        targetSdk = 34
        versionCode = 50
        versionName = "2.3.0"

        ndk {
            // M5 · GeckoView arrastra nativos por ABI; sin filtro el APK sale
            // universal de ~690 MB. Por defecto x86_64 (emulador de desarrollo);
            // para el TV real: ./gradlew assembleDebug -Pabi=arm64-v8a
            //
            // `-Pabi` admite varios separados por coma
            // (`-Pabi=arm64-v8a,armeabi-v7a`) para un APK ARM universal, que
            // instala tanto en televisores de 64 bits como de 32 bits a cambio
            // de ocupar el doble. Un APK con una ABI que el aparato no tiene
            // **no instala**: el instalador del TV sólo dice «no se instaló».
            val abis = (project.findProperty("abi") as String?)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf("arm64-v8a", "armeabi-v7a")
            abiFilters += abis
        }
    }

    // Clave de subida para Google Play: se lee de keystore.properties (fuera de git). Sin ese archivo
    // el paquete sale firmado con la clave de depuración (sirve para probar, no para subir).
    val clavesArchivo = rootProject.file("keystore.properties")
    val clavesSubida = Properties().apply {
        if (clavesArchivo.exists()) clavesArchivo.inputStream().use { load(it) }
    }
    signingConfigs {
        if (clavesArchivo.exists()) {
            create("subida") {
                storeFile = rootProject.file(clavesSubida.getProperty("storeFile"))
                storePassword = clavesSubida.getProperty("storePassword")
                keyAlias = clavesSubida.getProperty("keyAlias")
                keyPassword = clavesSubida.getProperty("keyPassword")
            }
        }
    }

    // Dos distribuciones del mismo código:
    //  · directo: el APK de GitHub para instalar en la TV (se actualiza solo desde GitHub y el
    //    administrador de archivos ve todo el almacenamiento).
    //  · play: el paquete (AAB) para Google Play. Sin autoactualizador (Play prohíbe actualizarse
    //    por fuera), sin «acceso a todos los archivos» ni permisos de medios, y con el nivel de API
    //    que exige la tienda.
    flavorDimensions += "dist"
    productFlavors {
        create("directo") {
            dimension = "dist"
            buildConfigField("boolean", "ACTUALIZADOR_PROPIO", "true")
            buildConfigField("boolean", "ARCHIVOS_TOTAL", "true")
            // Las TV que ya tienen el APK sólo aceptan una actualización firmada con la misma clave.
            signingConfig = signingConfigs.getByName("debug")
        }
        create("play") {
            dimension = "dist"
            targetSdk = 36
            buildConfigField("boolean", "ACTUALIZADOR_PROPIO", "false")
            buildConfigField("boolean", "ARCHIVOS_TOTAL", "false")
            signingConfig = signingConfigs.findByName("subida") ?: signingConfigs.getByName("debug")
        }
    }

    buildTypes {
        release {
            // Menos código cargado en memoria (el dex sin recortar pesaba ~30 MB en la interfaz).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // La firma la pone cada distribución (ver productFlavors).
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.room:room-runtime:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")
    // M5 · motor con extensiones WebExtension reales (uBO, etc.). Misma versión
    // probada en el spike: la última compatible con Kotlin 2.1.20/AGP 8.11.1/SDK 35.
    implementation("org.mozilla.geckoview:geckoview:141.0.20250806102122")

    // Archivos: servidor HTTP para compartir una carpeta por la red y reproductor de video/audio local.
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")

    // Pruebas JVM de la lógica pura (sincronización catálogo ↔ instaladas).
    testImplementation("junit:junit:4.13.2")
}

// Al compilar, el APK se copia siempre a la raíz del proyecto con la versión en
// el nombre (CocoSurf-<versión>-<variante>.apk). Las copias anteriores se retiran
// para que sólo exista la versión vigente; si Windows retiene un archivo (visor
// de archivos, antivirus), se reintenta y en último caso se deja nombre alterno.
val versionForFile = android.defaultConfig.versionName ?: "0"
val rootDirRef = rootProject.layout.projectDirectory.asFile
val buildDirRef = layout.buildDirectory.get().asFile
mapOf("assembleDirectoDebug" to "directo/debug", "assembleDirectoRelease" to "directo/release").forEach { (taskName, variant) ->
    tasks.matching { it.name == taskName }.configureEach {
        doLast {
            val outDir = File(buildDirRef, "outputs/apk/$variant")
            val apks = outDir.listFiles { f: File -> f.isFile && f.extension == "apk" }
                ?: return@doLast

            fun deleteRetry(f: File): Boolean {
                repeat(6) {
                    if (!f.exists() || f.delete()) return true
                    Thread.sleep(250)
                }
                return !f.exists()
            }

            apks.forEach { apk ->
                val suffix = apk.name.removePrefix("app-directo-").removeSuffix(".apk")
                // Fase 8 · el debug no se anuncia en el nombre: CocoSurf-<v>.apk
                val targetName = if (suffix == "debug") {
                    "CocoSurf-$versionForFile.apk"
                } else {
                    "CocoSurf-$versionForFile-$suffix.apk"
                }
                val target = File(rootDirRef, targetName)

                rootDirRef.listFiles { f: File ->
                    f.isFile && f.extension == "apk" &&
                        (f.name.startsWith("CocoSurf-") || f.name.startsWith("CocoSurf-")) &&
                        f.name != targetName
                }?.forEach { old ->
                    if (!deleteRetry(old)) {
                        println("AVISO: ${old.name} esta bloqueado; no se pudo retirar de la raiz")
                    }
                }

                when {
                    !target.exists() -> {
                        apk.copyTo(target, overwrite = false)
                        println("APK copiado en la raiz del proyecto: $targetName")
                    }
                    deleteRetry(target) -> {
                        apk.copyTo(target, overwrite = false)
                        println("APK copiado en la raiz del proyecto: $targetName")
                    }
                    target.length() == apk.length() ->
                        println("APK ya presente y sin cambios: $targetName")
                    else -> {
                        val alt = File(rootDirRef, targetName.removeSuffix(".apk") + ".nuevo.apk")
                        apk.copyTo(alt, overwrite = true)
                        println(
                            "AVISO: $targetName esta bloqueado por otro programa. " +
                                "EL APK ACTUALIZADO ES: ${alt.name}. " +
                                "Cierra quien lo tenga abierto y recompila para consolidar.",
                        )
                    }
                }
            }
        }
    }
}

// El paquete para Google Play (AAB) también se copia a la raíz: CocoSurf-<versión>-play.aab
tasks.matching { it.name == "bundlePlayRelease" }.configureEach {
    doLast {
        val aab = File(buildDirRef, "outputs/bundle/playRelease").listFiles { f: File -> f.extension == "aab" }?.firstOrNull()
            ?: return@doLast
        rootDirRef.listFiles { f: File -> f.isFile && f.extension == "aab" && f.name.startsWith("CocoSurf-") }?.forEach { it.delete() }
        aab.copyTo(File(rootDirRef, "CocoSurf-$versionForFile-play.aab"), overwrite = true)
        println("AAB copiado en la raiz del proyecto: CocoSurf-$versionForFile-play.aab")
    }
}
