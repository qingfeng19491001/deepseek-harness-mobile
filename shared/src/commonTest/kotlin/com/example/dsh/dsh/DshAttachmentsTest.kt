package com.example.dsh.dsh

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DshAttachmentsTest {
    private val limits = DSH_IMAGE_LIMITS_FALLBACK

    @Test
    fun promptContentPutsImagesBesideTextAndOmitsEmptyText() {
        val withText = dshPromptContent(
            "看这张图",
            listOf(DshPromptImagePart("image/png", "abcd", "a.png")),
        )
        assertEquals(2, withText.length())
        assertEquals("text", withText.optJSONObject(0)?.optString("type"))
        assertEquals("image", withText.optJSONObject(1)?.optString("type"))
        assertEquals("abcd", withText.optJSONObject(1)?.optString("data"))
        assertEquals("a.png", withText.optJSONObject(1)?.optString("name"))

        val imagesOnly = dshPromptContent("  ", listOf(DshPromptImagePart("image/jpeg", "efgh", "b.jpg")))
        assertEquals(1, imagesOnly.length())
        assertEquals("image", imagesOnly.optJSONObject(0)?.optString("type"))
    }

    @Test
    fun canonicalBase64RejectsUrlSafeAndWhitespace() {
        assertTrue(dshCanonicalBase64("abcd"))
        assertTrue(dshCanonicalBase64("abc="))
        assertFalse(dshCanonicalBase64(""))
        assertFalse(dshCanonicalBase64("ab-d"))
        assertFalse(dshCanonicalBase64("ab_d"))
        assertFalse(dshCanonicalBase64("ab cd"))
        assertFalse(dshCanonicalBase64("abc"))
    }

    @Test
    fun admitRejectsTooManyAndUnsupportedTypes() {
        val current = List(20) { sampleDraft("n$it.png") }
        val overflow = dshAdmitDraftImages(current, listOf(sampleDraft("extra.png")), limits, true)
        assertTrue(overflow.accepted.isEmpty())
        assertEquals("TOO_MANY_IMAGES", overflow.rejectedCode)

        val badType = dshAdmitDraftImages(emptyList(), listOf(sampleDraft("a.pdf", "application/pdf")), limits, true)
        assertTrue(badType.accepted.isEmpty())
        assertEquals("UNSUPPORTED_MEDIA", badType.rejectedCode)
    }

    @Test
    fun admitRejectsAggregateBytesWhenLimitsKnown() {
        val huge = sampleDraft("big.png").copy(bytes = limits.maxMessageImageBytes)
        val second = sampleDraft("two.png").copy(bytes = 1)
        val result = dshAdmitDraftImages(listOf(huge), listOf(second), limits, true)
        assertTrue(result.accepted.isEmpty())
        assertEquals("IMAGES_TOO_LARGE", result.rejectedCode)
    }

    @Test
    fun errorLabelsAlignWithHostCodes() {
        assertEquals("超过本条消息的图片张数上限", dshAttachmentErrorLabel("TOO_MANY_IMAGES"))
        assertEquals("图片体积或总大小超过上限", dshAttachmentErrorLabel("IMAGES_TOO_LARGE"))
        assertEquals("图片编码无效，请重新选择后再试", dshAttachmentErrorLabel("INVALID_IMAGE_BASE64"))
        assertEquals("仅支持 PNG、JPEG、WebP、GIF", dshAttachmentErrorLabel("UNSUPPORTED_MEDIA"))
        assertEquals("网络错误，图片未发送成功", dshAttachmentErrorLabel("transport-0"))
        assertEquals("未获得相机权限，请在系统设置中允许后重试", dshAttachmentErrorLabel("permission-denied"))
    }

    @Test
    fun parseImageLimitsAndRefs() {
        val json = JSONObject().apply {
            put("maxImageBytes", 1024)
            put("maxImagesPerMessage", 3)
            put("maxMessageImageBytes", 2048)
            put("maxImagePixels", 100)
            put("mediaTypes", JSONArray().apply { put("image/png") })
        }
        val parsed = parseDshImageLimits(json)!!
        assertEquals(1024, parsed.maxImageBytes)
        assertEquals(3, parsed.maxImagesPerMessage)
        assertEquals(listOf("image/png"), parsed.mediaTypes)
        assertNull(parseDshImageLimits("{}"))

        val ref = dshParseImageRef(JSONObject().apply {
            put("attachmentId", "sha256:abc")
            put("mediaType", "image/png")
            put("bytes", 12)
            put("width", 8)
            put("height", 6)
            put("name", "/tmp/secret/photo.png")
        })!!
        assertEquals("sha256:abc", ref.attachmentId)
        assertEquals("photo.png", ref.name)
        assertFalse(ref.name.contains("/tmp"))
    }

    @Test
    fun webTimelineKeepsUserImagesWithoutRawBytes() {
        val events = JSONArray(
            """
            [
              {
                "event": {
                  "seq": 1,
                  "type": "user/message",
                  "data": {
                    "source": { "kind": "user" },
                    "content": [
                      { "type": "text", "text": "见图" },
                      {
                        "type": "image",
                        "attachment": {
                          "attachmentId": "sha256:user",
                          "mediaType": "image/jpeg",
                          "bytes": 2048,
                          "width": 64,
                          "height": 48,
                          "name": "shot.jpg"
                        }
                      }
                    ]
                  }
                }
              }
            ]
            """.trimIndent(),
        )
        val timeline = DshWebTimelineParser.parseWebTimeline(events)
        assertEquals(listOf(DshWebTimelineItem.Kind.USER), timeline.map { it.kind })
        assertEquals("见图", timeline[0].text)
        assertEquals("sha256:user", timeline[0].attachments.single().attachmentId)
        assertEquals("shot.jpg", timeline[0].attachments.single().name)
        val content = JSONArray(events.optJSONObject(0)!!.optJSONObject("event")!!.optJSONObject("data")!!.optJSONArray("content")!!.toString())
        assertFalse(dshHistoryHasRawImageBytes(content))
    }

    @Test
    fun webTimelineKeepsImageOnlyUserMessage() {
        val events = JSONArray(
            """
            [
              {
                "event": {
                  "seq": 2,
                  "type": "user/message",
                  "data": {
                    "source": { "kind": "user" },
                    "content": [
                      {
                        "type": "image",
                        "attachment": { "attachmentId": "sha256:solo", "mediaType": "image/png" }
                      }
                    ]
                  }
                }
              }
            ]
            """.trimIndent(),
        )
        val timeline = DshWebTimelineParser.parseWebTimeline(events)
        assertEquals(1, timeline.size)
        assertEquals("", timeline[0].text)
        assertEquals("sha256:solo", timeline[0].attachments.single().attachmentId)
    }

    private fun sampleDraft(name: String, mediaType: String = "image/png"): DshDraftImage =
        DshDraftImage(
            localId = name,
            name = name,
            mediaType = mediaType,
            bytes = 16,
            width = 8,
            height = 8,
            data = "abcd",
        )
}
