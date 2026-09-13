package com.example.dsh.dsh

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal const val DSH_IMAGE_COMPRESS_LONG_EDGE = 2048
internal const val DSH_IMAGE_PROMPT_TIMEOUT_SECONDS = 90

internal val DSH_IMAGE_MEDIA_TYPES = listOf(
    "image/png",
    "image/jpeg",
    "image/webp",
    "image/gif",
)

/** Official Host defaults when `imageLimits` has not arrived yet. Used for compression, not as a hard Host claim. */
internal val DSH_IMAGE_LIMITS_FALLBACK = DshImageLimits(
    maxImageBytes = 20L * 1024L * 1024L,
    maxImagesPerMessage = 20,
    maxMessageImageBytes = 200L * 1024L * 1024L,
    maxImagePixels = 64_000_000L,
    maxImageDimension = 8192,
    mediaTypes = DSH_IMAGE_MEDIA_TYPES,
)

internal enum class DshDraftImageStatus {
    READY,
    SENDING,
    FAILED,
}

internal data class DshImageAttachmentRef(
    val attachmentId: String = "",
    val mediaType: String = "",
    val bytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val name: String = "",
    val localId: String = "",
)

internal data class DshPromptImagePart(
    val mediaType: String,
    val data: String,
    val name: String,
)

internal data class DshDraftImage(
    val localId: String,
    val name: String,
    val mediaType: String,
    val bytes: Long,
    val width: Int,
    val height: Int,
    val data: String,
    val compressed: Boolean = false,
    val status: DshDraftImageStatus = DshDraftImageStatus.READY,
    val errorCode: String = "",
    val errorMessage: String = "",
) {
    fun toPromptPart(): DshPromptImagePart = DshPromptImagePart(mediaType, data, name)

    fun toRef(): DshImageAttachmentRef = DshImageAttachmentRef(
        mediaType = mediaType,
        bytes = bytes,
        width = width,
        height = height,
        name = name,
        localId = localId,
    )

    fun previewDataUrl(): String = dshImageDataUrl(mediaType, data)
}

internal data class DshAdmitImagesResult(
    val accepted: List<DshDraftImage>,
    val rejectedMessage: String = "",
    val rejectedCode: String = "",
)

internal fun parseDshImageLimits(raw: String): DshImageLimits? {
    if (raw.isBlank() || raw == "null") return null
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    return parseDshImageLimits(json)
}

internal fun parseDshImageLimits(json: JSONObject?): DshImageLimits? {
    if (json == null) return null
    val maxImageBytes = json.optLong("maxImageBytes")
    val maxImagesPerMessage = json.optInt("maxImagesPerMessage")
    val maxMessageImageBytes = json.optLong("maxMessageImageBytes")
    val maxImagePixels = json.optLong("maxImagePixels")
    if (maxImageBytes <= 0L || maxImagesPerMessage <= 0 || maxMessageImageBytes <= 0L || maxImagePixels <= 0L) {
        return null
    }
    val mediaTypes = buildList {
        val array = json.optJSONArray("mediaTypes")
        if (array != null) {
            for (index in 0 until array.length()) {
                array.optString(index)?.takeIf { it.isNotEmpty() }?.let(::add)
            }
        }
    }.ifEmpty { DSH_IMAGE_MEDIA_TYPES }
    return DshImageLimits(
        maxImageBytes = maxImageBytes,
        maxImagesPerMessage = maxImagesPerMessage,
        maxMessageImageBytes = maxMessageImageBytes,
        maxImagePixels = maxImagePixels,
        maxImageDimension = json.optInt("maxImageDimension").takeIf { it > 0 } ?: 8192,
        mediaTypes = mediaTypes,
    )
}

internal fun dshImageDataUrl(mediaType: String, data: String): String {
    val type = mediaType.ifEmpty { "image/jpeg" }
    return "data:$type;base64,$data"
}

internal fun dshCanonicalBase64(data: String): Boolean {
    if (data.isEmpty()) return false
    if (data.any { it.isWhitespace() || it == '-' || it == '_' }) return false
    if (data.length % 4 != 0) return false
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    var padding = 0
    for (index in data.indices) {
        val ch = data[index]
        when (ch) {
            '=' -> {
                padding += 1
                if (padding > 2 || index < data.length - 2) return false
            }
            else -> {
                if (padding > 0) return false
                if (ch !in alphabet) return false
            }
        }
    }
    return true
}

internal fun dshNormalizeMediaType(raw: String): String {
    val value = raw.trim().lowercase()
    return when {
        value == "image/jpg" || value == "jpg" || value == "jpeg" -> "image/jpeg"
        value == "png" -> "image/png"
        value == "webp" -> "image/webp"
        value == "gif" -> "image/gif"
        value == "heic" || value == "image/heic" || value == "image/heif" || value == "heif" -> "image/heic"
        else -> value
    }
}

internal fun dshSupportedImageMediaType(mediaType: String, limits: DshImageLimits?): Boolean {
    val allowed = limits?.mediaTypes?.ifEmpty { DSH_IMAGE_MEDIA_TYPES } ?: DSH_IMAGE_MEDIA_TYPES
    return allowed.contains(dshNormalizeMediaType(mediaType))
}

internal fun dshImageLimitsHint(limits: DshImageLimits, known: Boolean): String {
    val count = limits.maxImagesPerMessage
    val mb = (limits.maxImageBytes / (1024L * 1024L)).coerceAtLeast(1L)
    return if (known) {
        "最多 $count 张 · 单张不超过 ${mb}MB · PNG / JPEG / WebP / GIF"
    } else {
        "PNG / JPEG / WebP / GIF · 超大图会在发送前压缩"
    }
}

internal fun dshAttachmentErrorLabel(code: String, fallback: String = ""): String {
    val normalized = code.trim()
    val details = fallback.trim()
    return when {
        normalized.equals("TOO_MANY_IMAGES", ignoreCase = true) ->
            "超过本条消息的图片张数上限"
        normalized.equals("IMAGES_TOO_LARGE", ignoreCase = true) ->
            "图片体积或总大小超过上限"
        normalized.equals("INVALID_IMAGE_BASE64", ignoreCase = true) ->
            "图片编码无效，请重新选择后再试"
        normalized.equals("UNSUPPORTED_MEDIA", ignoreCase = true) ||
            normalized.equals("UNSUPPORTED_MEDIA_TYPE", ignoreCase = true) ->
            "仅支持 PNG、JPEG、WebP、GIF"
        normalized.equals("IMAGE_TOO_LARGE", ignoreCase = true) ||
            normalized.equals("IMAGE_PIXELS_TOO_LARGE", ignoreCase = true) ->
            "图片尺寸过大，压缩后仍超过上限"
        normalized.equals("permission-denied", ignoreCase = true) ->
            details.ifEmpty { "未获得相机权限，请在系统设置中允许后重试" }
        normalized.equals("cancelled", ignoreCase = true) -> ""
        normalized.equals("attachment-error", ignoreCase = true) ->
            details.ifEmpty { "图片未被 Host 接受" }
        normalized.startsWith("transport-") ->
            "网络错误，图片未发送成功"
        else -> details.ifEmpty { "图片发送失败，请重试" }
    }
}

internal fun dshAdmitDraftImages(
    current: List<DshDraftImage>,
    incoming: List<DshDraftImage>,
    limits: DshImageLimits?,
    limitsKnown: Boolean,
): DshAdmitImagesResult {
    if (incoming.isEmpty()) return DshAdmitImagesResult(emptyList())
    val cap = limits?.maxImagesPerMessage ?: DSH_IMAGE_LIMITS_FALLBACK.maxImagesPerMessage
    val remaining = (cap - current.size).coerceAtLeast(0)
    if (remaining <= 0) {
        return DshAdmitImagesResult(
            accepted = emptyList(),
            rejectedMessage = dshAttachmentErrorLabel("TOO_MANY_IMAGES"),
            rejectedCode = "TOO_MANY_IMAGES",
        )
    }
    val kept = mutableListOf<DshDraftImage>()
    var firstCode = ""
    var firstMessage = ""
    for (image in incoming) {
        if (kept.size >= remaining) {
            if (firstCode.isEmpty()) {
                firstCode = "TOO_MANY_IMAGES"
                firstMessage = dshAttachmentErrorLabel("TOO_MANY_IMAGES")
            }
            break
        }
        val mediaType = dshNormalizeMediaType(image.mediaType)
        if (!dshSupportedImageMediaType(mediaType, limits)) {
            if (firstCode.isEmpty()) {
                firstCode = "UNSUPPORTED_MEDIA"
                firstMessage = dshAttachmentErrorLabel("UNSUPPORTED_MEDIA")
            }
            continue
        }
        if (!dshCanonicalBase64(image.data)) {
            if (firstCode.isEmpty()) {
                firstCode = "INVALID_IMAGE_BASE64"
                firstMessage = dshAttachmentErrorLabel("INVALID_IMAGE_BASE64")
            }
            continue
        }
        if (limitsKnown && limits != null) {
            if (image.bytes > limits.maxImageBytes ||
                (image.width.toLong() * image.height.toLong() > limits.maxImagePixels)
            ) {
                if (firstCode.isEmpty()) {
                    firstCode = "IMAGES_TOO_LARGE"
                    firstMessage = dshAttachmentErrorLabel("IMAGES_TOO_LARGE")
                }
                continue
            }
        }
        kept += image.copy(mediaType = mediaType)
    }
    if (limitsKnown && limits != null) {
        val total = current.sumOf { it.bytes } + kept.sumOf { it.bytes }
        if (total > limits.maxMessageImageBytes) {
            return DshAdmitImagesResult(
                accepted = emptyList(),
                rejectedMessage = dshAttachmentErrorLabel("IMAGES_TOO_LARGE"),
                rejectedCode = "IMAGES_TOO_LARGE",
            )
        }
    }
    return DshAdmitImagesResult(kept, firstMessage, firstCode)
}

internal fun dshPromptContent(text: String, images: List<DshPromptImagePart>): JSONArray {
    return JSONArray().apply {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            put(JSONObject().apply {
                put("type", "text")
                put("text", trimmed)
            })
        }
        images.forEach { image ->
            put(JSONObject().apply {
                put("type", "image")
                put("mediaType", image.mediaType)
                put("data", image.data)
                if (image.name.isNotEmpty()) put("name", image.name)
            })
        }
    }
}

internal fun dshParseImageRef(attachment: JSONObject?): DshImageAttachmentRef? {
    if (attachment == null) return null
    val attachmentId = attachment.optString("attachmentId").trim()
    if (attachmentId.isEmpty()) return null
    return DshImageAttachmentRef(
        attachmentId = attachmentId,
        mediaType = dshNormalizeMediaType(attachment.optString("mediaType")),
        bytes = attachment.optLong("bytes"),
        width = attachment.optInt("width"),
        height = attachment.optInt("height"),
        name = attachment.optString("name").substringAfterLast('/').substringAfterLast('\\'),
    )
}

internal fun dshImageRefsFromBlocks(blocks: JSONArray?): List<DshImageAttachmentRef> {
    if (blocks == null) return emptyList()
    return buildList {
        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            if (block.optString("type") != "image") continue
            dshParseImageRef(block.optJSONObject("attachment"))?.let(::add)
        }
    }
}

internal fun dshHistoryHasRawImageBytes(blocks: JSONArray?): Boolean {
    if (blocks == null) return false
    for (index in 0 until blocks.length()) {
        val block = blocks.optJSONObject(index) ?: continue
        if (block.optString("type") != "image") continue
        if (block.optString("data").isNotEmpty()) return true
        if (block.optString("url").isNotEmpty() || block.optString("path").isNotEmpty()) return true
    }
    return false
}

internal fun dshParseBridgeImages(imagesJson: String): List<DshDraftImage> {
    if (imagesJson.isBlank()) return emptyList()
    val array = runCatching { JSONArray(imagesJson) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val data = item.optString("data")
            if (data.isEmpty()) continue
            add(
                DshDraftImage(
                    localId = item.optString("localId").ifEmpty { "draft-${index}-${data.hashCode()}" },
                    name = item.optString("name").substringAfterLast('/').substringAfterLast('\\')
                        .ifEmpty { "image.jpg" },
                    mediaType = dshNormalizeMediaType(item.optString("mediaType").ifEmpty { "image/jpeg" }),
                    bytes = item.optLong("bytes").takeIf { it > 0 } ?: item.optString("data").length.toLong(),
                    width = item.optInt("width"),
                    height = item.optInt("height"),
                    data = data,
                    compressed = item.optBoolean("compressed"),
                ),
            )
        }
    }
}

internal fun dshFormatAttachmentCaption(ref: DshImageAttachmentRef): String {
    val name = ref.name.ifEmpty { "图片" }
    val type = ref.mediaType.removePrefix("image/").uppercase().ifEmpty { "IMAGE" }
    val size = if (ref.bytes > 0) dshFormatByteSize(ref.bytes) else ""
    val dim = if (ref.width > 0 && ref.height > 0) "${ref.width}×${ref.height}" else ""
    return listOf(name, type, size, dim).filter { it.isNotEmpty() }.joinToString(" · ")
}

internal fun dshFormatByteSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.toInt()}KB"
    val mb = kb / 1024.0
    return if (mb < 10) {
        val tenths = ((mb * 10).toInt()) / 10.0
        "${tenths}MB"
    } else {
        "${mb.toInt()}MB"
    }
}
