package com.yufiplay.app.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object MediaStoreSaver {

    fun saveDownloadedMedia(
        context: Context,
        sourceFile: File,
        displayName: String,
        isAudio: Boolean
    ): Uri? {
        val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
        val ext = if (isAudio) "mp3" else "mp4"
        val finalName = if (displayName.endsWith(".$ext", ignoreCase = true)) {
            displayName
        } else {
            "$displayName.$ext"
        }

        // Separate folders for Music and Video inside YufiPlay
        val folder = if (isAudio) "YufiPlay/Music" else "YufiPlay/Video"

        return FileInputStream(sourceFile).use { input ->
            saveStreamToPublicMedia(
                context = context,
                inputStream = input,
                fileName = finalName,
                mimeType = mimeType,
                isAudio = isAudio,
                subFolder = folder
            )
        }
    }

    fun saveStatusToGallery(
        context: Context,
        sourceUri: Uri,
        fileName: String,
        isVideo: Boolean
    ): Uri? {
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        return context.contentResolver.openInputStream(sourceUri)?.use { input ->
            saveStreamToPublicMedia(
                context = context,
                inputStream = input,
                fileName = fileName,
                mimeType = mimeType,
                isAudio = false,
                isImage = !isVideo,
                subFolder = "YufiPlay Status"
            )
        }
    }

    private fun saveStreamToPublicMedia(
        context: Context,
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        isAudio: Boolean,
        isImage: Boolean = false,
        subFolder: String
    ): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = when {
                isAudio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val baseDir = when {
                isAudio -> Environment.DIRECTORY_MUSIC
                isImage -> Environment.DIRECTORY_PICTURES
                else -> Environment.DIRECTORY_MOVIES
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/$subFolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val itemUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(itemUri)?.use { out ->
                inputStream.copyTo(out)
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            return itemUri
        } else {
            @Suppress("DEPRECATION")
            val baseDir = when {
                isAudio -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                isImage -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            }
            val targetDir = File(baseDir, subFolder).apply { mkdirs() }
            val targetFile = File(targetDir, fileName)
            targetFile.outputStream().use { out ->
                inputStream.copyTo(out)
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(mimeType),
                null
            )
            return Uri.fromFile(targetFile)
        }
    }
}
