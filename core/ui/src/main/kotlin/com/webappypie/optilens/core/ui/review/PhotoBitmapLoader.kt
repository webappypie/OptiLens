package com.webappypie.optilens.core.ui.review

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface PhotoBitmapLoader {
    suspend fun loadBitmap(uriString: String): Bitmap?
    suspend fun saveEnhancedBitmap(bitmap: Bitmap, originalUri: String, keepOriginal: Boolean): String?
}

@Singleton
class DefaultPhotoBitmapLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoBitmapLoader {

    override suspend fun loadBitmap(uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (uriString.startsWith("content://")) {
                val uri = Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                val file = File(uriString)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun saveEnhancedBitmap(
        bitmap: Bitmap,
        originalUri: String,
        keepOriginal: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val filename = "OptiLens_AI_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/OptiLens")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return@withContext null

            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 96, stream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            uri.toString()
        } catch (_: Exception) {
            null
        }
    }
}
