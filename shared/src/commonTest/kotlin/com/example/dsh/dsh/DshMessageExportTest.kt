package com.example.dsh.dsh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DshMessageExportTest {
    @Test
    fun assistantCopyStripsMarkdownAndKeepsCodeBodyOnly() {
        val message = DshMessage(
            "a1",
            DshMessageRole.ASSISTANT,
            """
            # Title
            Hello **world** and `x`.
            ```kotlin
            val n = 1
            println(n)
            ```
            See [docs](https://example.com).
            """.trimIndent(),
        )
        val copied = message.toReadableCopy()
        assertFalse(copied.contains("# Title"))
        assertFalse(copied.contains("**"))
        assertFalse(copied.contains("```"))
        assertTrue(copied.contains("Title"))
        assertTrue(copied.contains("Hello world and x."))
        assertEquals("val n = 1\nprintln(n)", dshParseCodeFence("""
            ```kotlin
            val n = 1
            println(n)
            ```
        """.trimIndent())?.body)
        assertTrue(copied.contains("val n = 1"))
        assertTrue(copied.contains("docs（https://example.com）"))
    }

    @Test
    fun codeFenceCopyDoesNotIncludeLanguageOrMarkers() {
        val fence = dshParseCodeFence(
            """
            ```python
            print("hi")
            ```
            """.trimIndent(),
        )
        assertEquals("python", fence?.language)
        assertEquals("print(\"hi\")", fence?.body)
    }

    @Test
    fun toolCopyUsesNameStatusAndPathThenBody() {
        val message = DshRemoteToolCallModel(
            callId = "c1",
            toolName = "bash",
            kind = DshRemoteToolKind.BASH,
            title = "Bash",
            summary = "src/App.kt",
            input = "cat src/App.kt",
            body = "fun main() {}",
            output = "fun main() {}",
            running = false,
            cardType = DshToolCardType.TERMINAL,
            filePath = "src/App.kt",
        ).toRemoteMessage("t1")
        val copied = message.toReadableCopy()
        assertEquals("Bash · 完成 · src/App.kt\nfun main() {}", copied)
    }

    @Test
    fun runningToolCopyKeepsExistingOutput() {
        val message = DshMessage(
            "t2",
            DshMessageRole.TOOL,
            "partial",
            toolName = "Read",
            toolRunning = true,
            remoteTool = DshRemoteToolCallModel(
                callId = "c2",
                toolName = "read",
                kind = DshRemoteToolKind.READ,
                title = "Read",
                summary = "a.txt",
                input = "a.txt",
                body = "hello",
                output = "hello",
                running = true,
                filePath = "a.txt",
            ),
        )
        assertTrue(message.copyEnabled(streaming = false))
        assertEquals("Read · 运行中 · a.txt\nhello", message.toReadableCopy())
    }

    @Test
    fun attachmentCopyIncludesNameAndReference() {
        val message = DshMessage(
            "img",
            DshMessageRole.ASSISTANT,
            "photo.png",
            attachmentId = "sha256:abc",
        )
        assertEquals("附件 · photo.png\n引用：sha256:abc", message.toReadableCopy())
    }

    @Test
    fun singleCopyOmitsRoleLabelsAndBatchKeepsOrder() {
        val user = DshMessage("u", DshMessageRole.USER, "你好")
        val assistant = DshMessage("a", DshMessageRole.ASSISTANT, "用 **Kotlin**")
        val tool = DshMessage("t", DshMessageRole.TOOL, "ok", toolName = "Bash")
        val hidden = DshMessage("h", DshMessageRole.ASSISTANT, "no", hidden = true)
        val think = DshMessage("r", DshMessageRole.ASSISTANT, "secret", isReasoning = true)
        val error = DshMessage("e", DshMessageRole.ERROR, "boom")
        val messages = listOf(user, think, assistant, tool, hidden, error)

        assertEquals("你好", user.toReadableCopy())
        val userImage = DshMessage(
            "u-img",
            DshMessageRole.USER,
            "看这张",
            attachments = listOf(
                DshImageAttachmentRef(
                    attachmentId = "sha256:user",
                    mediaType = "image/png",
                    bytes = 12,
                    width = 8,
                    height = 8,
                    name = "shot.png",
                ),
            ),
        )
        assertEquals(
            "看这张\n附件 · shot.png\nimage/png · 12 B · 8×8\n引用：sha256:user",
            userImage.toReadableCopy(),
        )
        assertEquals("用 Kotlin", assistant.toReadableCopy())
        assertNull(think.toExportBlock())
        assertNull(error.toExportBlock())
        assertFalse(hidden.isCopyExportable())

        val batch = dshSelectedMessagesReadable(
            messages,
            selectedIds = setOf("u", "a", "t", "r", "e"),
            labeled = true,
        )
        assertEquals(
            "你\n你好\n\n助手\n用 Kotlin\n\n工具\nBash · 完成\nok",
            batch,
        )
        assertTrue(!batch.contains("secret") && !batch.contains("boom"))
        assertEquals(
            "",
            dshSelectedMessagesReadable(messages, selectedIds = emptySet(), labeled = true),
        )
    }

    @Test
    fun htmlExportKeepsOrderAndEscapes() {
        val user = DshMessage("u", DshMessageRole.USER, "<script>")
        val assistant = DshMessage(
            "a",
            DshMessageRole.ASSISTANT,
            "```\nalert(1)\n```",
        )
        val html = dshExportHtml(
            "Demo <title>",
            dshSelectedExportBlocks(listOf(user, assistant), selectedIds = null),
        )
        assertTrue(html.contains("Demo &lt;title&gt;"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("<pre><code>alert(1)</code></pre>"))
        assertTrue(html.indexOf("你") < html.indexOf("助手"))
        assertTrue(html.contains("打印"))
    }

    @Test
    fun streamingAssistantCannotCopyUntilSettled() {
        val live = DshMessage("a", DshMessageRole.ASSISTANT, "partial", streaming = true)
        assertFalse(live.copyEnabled(streaming = true))
        assertTrue(live.copyEnabled(streaming = false))
    }

    @Test
    fun exportFileNameSanitizesTitle() {
        assertEquals("hello-world.html", dshExportFileName("hello world"))
        assertEquals("dsh-session.html", dshExportFileName("   "))
        assertFalse(dshExportFileName("a/b:c").contains("/"))
        assertFalse(dshExportFileName("../etc/passwd").contains(".."))
        assertEquals("passwd.html", dshExportFileName("../etc/passwd"))
    }
}
