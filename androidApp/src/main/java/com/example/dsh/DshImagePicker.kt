package com.example.dsh

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

internal class DshImagePicker(private val module: com.example.dsh.module.KRBridgeModule) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var pendingCallback: ((Map<String, Any>) -> Unit)? = null
    private var captureFile: File? = null
    private var pendingLimits: Limits = Limits()
    private var pendingIsCapture = false

    data class Limits(
        val maxCount: Int = 20,
        val maxImageBytes: Long = 20L * 1024L * 1024L,
        val maxImagePixels: Long = 64_000_000L,
        val maxImageDimension: Int = 8192,
        val longEdge: Int = 2048,
    )

    fun pickImages(params: String?, callback: ((Map<String, Any>) -> Unit)?) {
        val limits = parseLimits(params)
        val act = module.hostActivity() ?: run {
            callback?.invoke(error("internal", "无法打开相册"))
            return
        }
        pendingCallback = callback
        pendingLimits = limits
        pendingIsCapture = false
        val intent = if (Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
                putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, limits.maxCount.coerceAtLeast(1))
            }
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, limits.maxCount > 1)
            }
        }
        try {
            act.startActivityForResult(intent, REQUEST_PICK)
        } catch (error: Exception) {
            finish(error("internal", "无法打开相册"))
        }
    }

    fun captureImage(params: String?, callback: ((Map<String, Any>) -> Unit)?) {
        val limits = parseLimits(params)
        val act = module.hostActivity() ?: run {
            callback?.invoke(error("internal", "无法打开相机"))
            return
        }
        pendingCallback = callback
        pendingLimits = limits
        pendingIsCapture = true
        if (act.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            return
        }
        launchCamera(act)
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQUEST_CAMERA_PERMISSION) return false
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        val act = module.hostActivity()
        if (!granted || act == null) {
            finish(error("permission-denied", "未获得相机权限，请在系统设置中允许后重试"))
            return true
        }
        launchCamera(act)
        return true
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        when (requestCode) {
            REQUEST_PICK -> {
                if (resultCode != Activity.RESULT_OK) {
                    finish(cancelled())
                    return true
                }
                val uris = urisFrom(data)
                if (uris.isEmpty()) {
                    finish(cancelled())
                    return true
                }
                encode(uris)
                return true
            }
            REQUEST_CAPTURE -> {
                val file = captureFile
                captureFile = null
                if (resultCode != Activity.RESULT_OK || file == null || !file.exists()) {
                    finish(cancelled())
                    return true
                }
                encode(listOf(Uri.fromFile(file)))
                return true
            }
            else -> return false
        }
    }

    private fun launchCamera(act: Activity) {
        val dir = File(act.cacheDir, "dsh-images").apply { mkdirs() }
        val file = File(dir, "capture-${UUID.randomUUID()}.jpg")
        captureFile = file
        val uri = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", file)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            act.startActivityForResult(intent, REQUEST_CAPTURE)
        } catch (error: Exception) {
            Toast.makeText(act, "无法打开相机", Toast.LENGTH_SHORT).show()
            finish(error("internal", "无法打开相机"))
        }
    }

    private fun encode(uris: List<Uri>) {
        val limits = pendingLimits
        val act = module.hostActivity() ?: run {
            finish(error("internal", "无法读取图片"))
            return
        }
        worker.execute {
            val images = JSONArray()
            var rejected: JSONObject? = null
            for (uri in uris.take(limits.maxCount)) {
                val prepared = runCatching { prepare(act, uri, limits) }.getOrNull()
                if (prepared == null) {
                    if (rejected == null) {
                        rejected = JSONObject().put("code", "UNSUPPORTED_MEDIA").put("message", "仅支持 PNG、JPEG、WebP、GIF")
                    }
                    continue
                }
                images.put(prepared)
            }
            val result = JSONObject()
                .put("ok", true)
                .put("imagesJson", images.toString())
                .put("rejectedJson", JSONArray().apply { rejected?.let(::put) }.toString())
            main.post { finish(result.toMap()) }
        }
    }

    private fun prepare(act: Activity, uri: Uri, limits: Limits): JSONObject? {
        val resolver = act.contentResolver
        val name = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.substringAfterLast('/')?.ifEmpty { null } ?: uri.lastPathSegment ?: "image.jpg"
        val mime = dshNormalizeAndroidMime(resolver.getType(uri).orEmpty(), name)
        val bytes = when (uri.scheme) {
            "file" -> File(uri.path ?: return null).takeIf { it.exists() }?.readBytes() ?: return null
            else -> resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        }
        if (mime == "image/gif" && bytes.size <= limits.maxImageBytes) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            return jsonImage(name, mime, bytes, bounds.outWidth, bounds.outHeight, compressed = false)
        }
        val oriented = decodeOriented(bytes) ?: return null
        val scaled = scale(oriented, limits)
        val encoded = encodeWithin(scaled, mime, limits.maxImageBytes) ?: return null
        val outName = if (encoded.mime == "image/jpeg" && !name.lowercase().endsWith(".jpg") && !name.lowercase().endsWith(".jpeg")) {
            name.substringBeforeLast('.') + ".jpg"
        } else name
        return jsonImage(outName, encoded.mime, encoded.bytes, scaled.width, scaled.height, compressed = encoded.bytes.size < bytes.size || scaled.width != oriented.width)
    }

    private fun decodeOriented(bytes: ByteArray): Bitmap? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scale(source: Bitmap, limits: Limits): Bitmap {
        val width = source.width.coerceAtLeast(1)
        val height = source.height.coerceAtLeast(1)
        val longEdge = maxOf(width, height)
        val targetLong = minOf(limits.longEdge, limits.maxImageDimension).coerceAtLeast(1)
        var scale = 1f
        if (longEdge > targetLong) scale = targetLong.toFloat() / longEdge
        val pixels = width.toLong() * height.toLong()
        if (pixels > limits.maxImagePixels) {
            val pixelScale = kotlin.math.sqrt(limits.maxImagePixels.toDouble() / pixels.toDouble()).toFloat()
            scale = minOf(scale, pixelScale)
        }
        if (scale >= 0.999f) return source
        val nextW = (width * scale).toInt().coerceAtLeast(1)
        val nextH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, nextW, nextH, true)
    }

    private fun encodeWithin(bitmap: Bitmap, sourceMime: String, maxBytes: Long): Encoded? {
        val preferPng = sourceMime == "image/png"
        if (preferPng) {
            val png = compress(bitmap, Bitmap.CompressFormat.PNG, 100)
            if (png.size <= maxBytes) return Encoded("image/png", png)
        }
        val webp = sourceMime == "image/webp"
        val qualities = intArrayOf(85, 70, 55, 40, 28)
        for (quality in qualities) {
            val format = if (webp && Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.JPEG
            val bytes = compress(bitmap, format, quality)
            if (bytes.size <= maxBytes) {
                return Encoded(if (format == Bitmap.CompressFormat.JPEG) "image/jpeg" else "image/webp", bytes)
            }
        }
        val last = compress(bitmap, Bitmap.CompressFormat.JPEG, 22)
        return if (last.size <= maxBytes) Encoded("image/jpeg", last) else null
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(format, quality, out)
        return out.toByteArray()
    }

    private fun jsonImage(name: String, mime: String, bytes: ByteArray, width: Int, height: Int, compressed: Boolean): JSONObject {
        return JSONObject()
            .put("localId", UUID.randomUUID().toString())
            .put("name", name)
            .put("mediaType", mime)
            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("bytes", bytes.size)
            .put("width", width)
            .put("height", height)
            .put("compressed", compressed)
    }

    private fun urisFrom(data: Intent?): List<Uri> {
        if (data == null) return emptyList()
        val clip: ClipData? = data.clipData
        if (clip != null && clip.itemCount > 0) {
            return buildList {
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let(::add)
                }
            }
        }
        return listOfNotNull(data.data)
    }

    private fun parseLimits(params: String?): Limits {
        val json = runCatching { JSONObject(params ?: "{}") }.getOrDefault(JSONObject())
        return Limits(
            maxCount = json.optInt("maxCount").takeIf { it > 0 } ?: 20,
            maxImageBytes = json.optLong("maxImageBytes").takeIf { it > 0 } ?: 20L * 1024L * 1024L,
            maxImagePixels = json.optLong("maxImagePixels").takeIf { it > 0 } ?: 64_000_000L,
            maxImageDimension = json.optInt("maxImageDimension").takeIf { it > 0 } ?: 8192,
            longEdge = json.optInt("longEdge").takeIf { it > 0 } ?: 2048,
        )
    }

    private fun finish(value: Map<String, Any>) {
        val callback = pendingCallback
        pendingCallback = null
        callback?.invoke(value)
    }

    private fun cancelled(): Map<String, Any> = mapOf("ok" to true, "cancelled" to true, "imagesJson" to "[]")
    private fun error(code: String, message: String): Map<String, Any> =
        mapOf("ok" to false, "code" to code, "message" to message, "imagesJson" to "[]")

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { key -> opt(key)?.let { map[key] = it } }
        return map
    }

    private data class Encoded(val mime: String, val bytes: ByteArray)

    companion object {
        const val REQUEST_PICK = 4093
        const val REQUEST_CAPTURE = 4094
        const val REQUEST_CAMERA_PERMISSION = 4095
    }
}

private fun dshNormalizeAndroidMime(type: String, name: String): String {
    val lower = type.lowercase()
    if (lower == "image/jpg") return "image/jpeg"
    if (lower.startsWith("image/")) return lower
    return when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic", "heif" -> "image/heic"
        else -> lower
    }
}
