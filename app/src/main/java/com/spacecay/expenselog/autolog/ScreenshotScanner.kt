package com.spacecay.expenselog.autolog

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.spacecay.expenselog.data.Graph
import com.spacecay.expenselog.data.TxSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Looks at new screenshots, reads them with on-device OCR and logs the ones that are
 * telebirr / CBE payment confirmations. Runs when the app opens and every ~15 minutes.
 */
object ScreenshotScanner {
    private val lock = Mutex()

    fun hasPermission(ctx: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    val permission: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    data class Result(val scanned: Int, val logged: Int)

    suspend fun scan(ctx: Context, force: Boolean = false): Result = lock.withLock {
        Graph.init(ctx)
        val prefs = Graph.prefs
        if (!force && !prefs.autoLogScreenshots) return@withLock Result(0, 0)
        if (!hasPermission(ctx)) return@withLock Result(0, 0)

        // First run: only look back one day so old screenshots aren't imported.
        val since = prefs.lastScreenshotScan.takeIf { it > 0 } ?: (System.currentTimeMillis() - 86_400_000L)
        val sinceSec = since / 1000
        val shots = mutableListOf<Pair<Uri, Long>>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val sel = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, sel, arrayOf(sinceSec.toString()),
            "${MediaStore.Images.Media.DATE_ADDED} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketCol = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val bucket = if (bucketCol >= 0) c.getString(bucketCol).orEmpty() else ""
                if (!bucket.contains("screenshot", ignoreCase = true)) continue
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol))
                shots += uri to c.getLong(dateCol) * 1000
            }
        }

        var logged = 0
        var newest = since
        Graph.repo.ensureSeeded()
        for ((uri, added) in shots.takeLast(25)) {
            newest = maxOf(newest, added)
            val text = try { AutoLogger.ocr(ctx, uri) } catch (e: Exception) { continue }
            if (!AutoLogger.looksLikePayment(text)) continue
            val parsed = PaymentParser.parse(text, null, fromScreenshot = true) ?: continue
            val withTime = if (parsed.time == null) parsed.copy(time = added) else parsed
            if (AutoLogger.isDuplicate(withTime, prefs.bookId.value)) continue
            val photo = AutoLogger.keepPhoto(ctx, uri)
            if (AutoLogger.log(ctx, withTime, TxSource.SCREENSHOT, photo) != null) logged++
        }
        prefs.lastScreenshotScan = maxOf(newest, System.currentTimeMillis() - 60_000L).coerceAtLeast(since)
        Result(shots.size, logged)
    }
}
