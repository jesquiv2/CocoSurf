package com.antena.tv.probe

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.media.MediaDrm
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.UUID

data class CodecRow(
    val label: String,
    val supported: Boolean,
    val sample: String?,
)

data class ProbeResult(
    val manufacturer: String,
    val model: String,
    val system: String,
    val appVersion: String,
    val ram: String,
    val screen: String,
    val storage: String,
    val webView: String,
    val userAgent: String,
    val codecs: List<CodecRow>,
    val decoderCount: Int,
    val widevine: String?,
    val gms: String?,
    val playStore: String?,
    val autofill: String,
    val passkeys: String,
)

/**
 * M0: sonda de capacidades. Recoge en el televisor todo lo que condiciona
 * la decisión de motor del PLAN.md §3 (WebView del TV, códecs, DRM, GMS).
 */
object Probe {

    private val WIDEVINE_UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

    private val TRACKED_CODECS = listOf(
        "H.264 (AVC)" to "video/avc",
        "HEVC (H.265)" to "video/hevc",
        "VP9" to "video/x-vnd.on2.vp9",
        "AV1" to "video/av01",
    )

    suspend fun collect(context: Context): ProbeResult = coroutineScope {
        // WebView y UA se leen en el hilo principal (el proveedor de WebView lo exige)
        val web = withContext(Dispatchers.Main) { webviewInfo(context) }
        val rest = withContext(Dispatchers.IO) { collectOffline(context) }
        rest.copy(webView = web.first, userAgent = web.second)
    }

    private fun collectOffline(context: Context): ProbeResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val totalGb = mem.totalMem / 1073741824.0
        val availGb = mem.availMem / 1073741824.0

        val stat = StatFs(context.filesDir.path)
        val freeGb = stat.availableBlocksLong * stat.blockSizeLong / 1e9

        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos.filter { !it.isEncoder }

        val byType = HashMap<String, MutableList<String>>()
        for (d in decoders) {
            for (t in d.supportedTypes) {
                byType.getOrPut(t.lowercase()) { mutableListOf() }.add(d.name)
            }
        }

        val codecRows = TRACKED_CODECS.map { (label, mime) ->
            val names = byType[mime].orEmpty()
            CodecRow(label, names.isNotEmpty(), names.firstOrNull())
        }

        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Throwable) {
            "?"
        }

        return ProbeResult(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            system = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
            appVersion = appVersion,
            ram = String.format(
                "%.1f GB en total · %.1f GB libres", totalGb, availGb,
            ),
            screen = screenInfo(context),
            storage = String.format("%.1f GB libres en datos", freeGb),
            webView = "pendiente",
            userAgent = "pendiente",
            codecs = codecRows,
            decoderCount = decoders.size,
            widevine = widevineLevel(),
            gms = pkgVersion(context, "com.google.android.gms"),
            playStore = pkgVersion(context, "com.android.vending"),
            autofill = autofillInfo(context),
            passkeys = passkeysInfo(context),
        )
    }

    /**
     * P0 · contraseñas sobre la plataforma: el gestor lo da el sistema
     * (Autofill Framework, API 26+). Se lee como cadena para no tocar
     * clases que no existen en Android 7, donde además no hay autofill.
     */
    fun autofillInfo(context: Context): String {
        if (Build.VERSION.SDK_INT < 26) return "no disponible · Android 7 (API 24/25)"
        val servicio = try {
            // Clave del ajuste seguro «autofill_service» (API 26+); se usa la
            // cadena literal para no compilar contra constantes que varían.
            Settings.Secure.getString(
                context.contentResolver,
                "autofill_service",
            )
        } catch (_: Throwable) {
            null
        }
        if (servicio.isNullOrBlank()) return "ninguno · sin gestor de contraseñas en el sistema"
        val paquete = servicio.substringBefore('/')
        val nombre = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(paquete, 0)).toString()
        } catch (_: Throwable) {
            paquete
        }
        return "$nombre · $paquete"
    }

    /** P0 · passkeys: dependen de un proveedor de credenciales (Credential Manager). */
    fun passkeysInfo(context: Context): String {
        val gms = pkgVersion(context, "com.google.android.gms")
        return if (gms != null) {
            "disponible · proveedor GMS $gms (validar en P3)"
        } else {
            "no disponible · sin proveedor de credenciales"
        }
    }

    private fun screenInfo(context: Context): String {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            "${b.width()}×${b.height()} px · ${context.resources.configuration.densityDpi} dpi"
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            "${dm.widthPixels}×${dm.heightPixels} px · ${dm.densityDpi} dpi"
        }
    }

    private fun widevineLevel(): String? = try {
        val drm = MediaDrm(WIDEVINE_UUID)
        val level = drm.getPropertyString("securityLevel")
        if (Build.VERSION.SDK_INT >= 26) {
            drm.close()
        }
        level
    } catch (_: Throwable) {
        null
    }

    private fun pkgVersion(context: Context, pkg: String): String? = try {
        context.packageManager.getPackageInfo(pkg, 0).versionName
    } catch (_: Throwable) {
        null
    }

    private fun webviewInfo(context: Context): Pair<String, String> {
        var pkg = "desconocido"
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                WebView.getCurrentWebViewPackage()?.let {
                    pkg = "${it.packageName} ${it.versionName}"
                }
            }
        } catch (_: Throwable) {
            // proveedor de WebView no cargado aún
        }
        if (pkg == "desconocido") {
            val candidates = listOf(
                "com.google.android.webview",
                "com.android.webview",
                "com.android.chrome",
                "com.google.android.webview.beta",
                "com.google.android.webview.dev",
            )
            for (name in candidates) {
                try {
                    val pi = context.packageManager.getPackageInfo(name, 0)
                    pkg = "$name ${pi.versionName}"
                    break
                } catch (_: Throwable) {
                    // no instalado
                }
            }
        }
        val ua = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (t: Throwable) {
            "no disponible (${t.javaClass.simpleName})"
        }
        return pkg to ua
    }
}
