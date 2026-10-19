package com.antena.tv.browser

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Guardar la página como PDF e imprimirla. El PDF lo genera GeckoView
 * (`saveAsPdf`); aquí sólo se escribe a disco y se entrega.
 */

/** Nombre de archivo seguro a partir del título de la página. */
fun nombrePdf(titulo: String, url: String): String {
    val base = titulo.ifBlank { hostOf(url) ?: "pagina" }
        .replace(Regex("[^\\p{L}\\p{N} ._-]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(60)
        .ifBlank { "pagina" }
    return "$base.pdf"
}

/**
 * Pide el PDF al motor y lo copia a [destino] fuera del hilo principal.
 * [alTerminar] se llama en el hilo principal con el archivo, o `null` si falló.
 */
fun generarPdf(
    context: Context,
    pestana: WebTab,
    destino: File,
    alTerminar: (File?) -> Unit,
) {
    val principal = android.os.Handler(android.os.Looper.getMainLooper())
    runCatching {
        pestana.comoPdf().accept(
            { flujo ->
                Thread {
                    val ok = runCatching {
                        destino.parentFile?.mkdirs()
                        flujo?.use { entrada -> FileOutputStream(destino).use { entrada.copyTo(it) } }
                        destino.length() > 0
                    }.getOrDefault(false)
                    principal.post { alTerminar(if (ok) destino else null) }
                }.apply { isDaemon = true }.start()
            },
            { principal.post { alTerminar(null) } },
        )
    }.onFailure { principal.post { alTerminar(null) } }
}

/** Entrega un PDF ya generado al servicio de impresión del sistema. */
fun imprimirPdf(actividad: Activity, archivo: File, nombre: String): Boolean = runCatching {
    val pm = actividad.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(
        nombre,
        object : PrintDocumentAdapter() {
            override fun onLayout(
                antes: PrintAttributes?,
                despues: PrintAttributes?,
                senal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?,
            ) {
                if (senal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                callback.onLayoutFinished(
                    PrintDocumentInfo.Builder(nombre)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build(),
                    true,
                )
            }

            override fun onWrite(
                paginas: Array<out PageRange>?,
                destino: ParcelFileDescriptor,
                senal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                runCatching {
                    FileInputStream(archivo).use { entrada ->
                        FileOutputStream(destino.fileDescriptor).use { entrada.copyTo(it) }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }.onFailure { callback.onWriteFailed(it.message) }
            }
        },
        null,
    )
    true
}.getOrDefault(false)
