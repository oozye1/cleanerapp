package com.d4rk.cleaner.app.images.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import java.io.File

object ImageHashUtils {
    fun perceptualHash(file: File): String? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        options.inSampleSize = calculateInSampleSize(options, 8, 8)
        options.inJustDecodeBounds = false
        BitmapFactory.decodeFile(file.absolutePath, options)?.use { bitmap ->
            bitmap.scale(8, 8).use { resized ->
                val pixels = IntArray(64)
                resized.getPixels(pixels, 0, 8, 0, 0, 8, 8)
                var sum = 0
                val gray = IntArray(64)
                pixels.forEachIndexed { index, pixel ->
                    val r = (pixel shr 16) and 0xff
                    val g = (pixel shr 8) and 0xff
                    val b = pixel and 0xff
                    val lum = (r + g + b) / 3
                    gray[index] = lum
                    sum += lum
                }
                val avg = sum / 64
                var hash = 0L
                gray.forEachIndexed { index, lum ->
                    if (lum >= avg) {
                        hash = hash or (1L shl (63 - index))
                    }
                }
                java.lang.Long.toHexString(hash)
            }
        }
    }.getOrNull()

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private inline fun <T> Bitmap.use(block: (Bitmap) -> T): T {
        return try {
            block(this)
        } finally {
            recycle()
        }
    }
}

