package com.nhom2.booking.data.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.File

const val MIME_PDF = "application/pdf"
const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private const val EXPORT_DIR = "exports"
private const val UPLOAD_FIELD = "file"
// The backend accepts uploads by file extension and refuses anything else (ImageStorageService).
private val ALLOWED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")

class UnsupportedImageException : Exception("Unsupported image type")

/** Reads a picked image into the multipart part the upload endpoints expect. */
suspend fun imagePart(context: Context, uri: Uri): MultipartBody.Part = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: throw UnsupportedImageException()
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        ?.takeIf { it in ALLOWED_IMAGE_EXTENSIONS }
        ?: throw UnsupportedImageException()
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw UnsupportedImageException()
    MultipartBody.Part.createFormData(UPLOAD_FIELD, "image.$extension", bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
}

/** Saves a downloaded document in the app cache, where [openDocument] can share it with a viewer app. */
suspend fun saveToCache(context: Context, body: ResponseBody, fileName: String): File = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
    val file = File(dir, fileName)
    body.use { response -> file.outputStream().use { response.byteStream().copyTo(it) } }
    file
}

/** Returns false when no installed app can open [mimeType]. */
fun openDocument(context: Context, file: File, mimeType: String): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
