package com.spacecay.expenselog.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

object Photos {
    fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    /** Copy (and downscale) a picked image into app storage. Returns stored file name. */
    fun import(ctx: Context, uri: Uri): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (bmp == null) null else {
            val name = UUID.randomUUID().toString() + ".jpg"
            file(ctx, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            name
        }
    } catch (e: Exception) {
        null
    }

    fun split(photos: String): List<String> = photos.split('|').filter { it.isNotBlank() }
    fun join(list: List<String>): String = list.joinToString("|")
}
