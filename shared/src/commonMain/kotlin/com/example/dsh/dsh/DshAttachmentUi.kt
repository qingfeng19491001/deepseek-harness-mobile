package com.example.dsh.dsh

import com.example.dsh.theme.tokens
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.layout.FlexDirection
import com.tencent.kuikly.core.layout.FlexWrap
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.ActionSheet
import com.tencent.kuikly.core.views.ActivityIndicator
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal const val DRAFT_THUMB_SIZE = 72f

internal fun ViewContainer<*, *>.DshDraftImageStrip(
    images: () -> ObservableList<DshDraftImage>,
    sending: () -> Boolean,
    onPreview: (DshDraftImage) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    vif({ images().isNotEmpty() }) {
        Scroller {
            attr {
                height(DRAFT_THUMB_SIZE + 8f)
                marginBottom(8f)
                flexDirection(FlexDirection.ROW)
                showScrollerIndicator(false)
            }
            vfor({ images() }) { image ->
                View {
                    attr {
                        size(DRAFT_THUMB_SIZE, DRAFT_THUMB_SIZE)
                        marginRight(8f)
                        borderRadius(10f)
                        overflow(true)
                        backgroundColor(tokens.surfaceVariant)
                        border(
                            Border(
                                1f,
                                BorderStyle.SOLID,
                                if (image.status == DshDraftImageStatus.FAILED) tokens.error.foreground else tokens.divider,
                            ),
                        )
                    }
                    Image {
                        attr {
                            src(image.previewDataUrl())
                            size(DRAFT_THUMB_SIZE, DRAFT_THUMB_SIZE)
                            resizeCover()
                        }
                    }
                    DshHitButton { onPreview(image) }
                    vif({ image.status == DshDraftImageStatus.SENDING || sending() }) {
                        View {
                            attr {
                                absolutePositionAllZero()
                                allCenter()
                                backgroundColor(Color(0x66000000L))
                            }
                            ActivityIndicator { }
                        }
                    }
                    vif({ image.status == DshDraftImageStatus.FAILED }) {
                        View {
                            attr {
                                absolutePositionAllZero()
                                allCenter()
                                backgroundColor(Color(0x99000000L))
                            }
                            Text {
                                attr {
                                    text("重试")
                                    fontSize(11f)
                                    color(Color.WHITE)
                                }
                            }
                            DshHitButton { onRetry(image.localId) }
                        }
                    }
                    vif({ image.compressed && image.status == DshDraftImageStatus.READY }) {
                        Text {
                            attr {
                                absolutePosition(bottom = 4f, left = 4f)
                                text("已压缩")
                                fontSize(9f)
                                color(Color.WHITE)
                                backgroundColor(Color(0x99000000L))
                            }
                        }
                    }
                    View {
                        attr {
                            absolutePosition(top = 4f, right = 4f)
                            size(20f, 20f)
                            allCenter()
                            borderRadius(10f)
                            backgroundColor(Color(0xCC000000L))
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("x.svg"))
                                size(12f, 12f)
                                tintColor(Color.WHITE)
                            }
                        }
                        DshHitButton { onRemove(image.localId) }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshMessageImageGrid(
    attachments: List<DshImageAttachmentRef>,
    attachmentDataUrl: (String) -> String?,
    onPreview: (DshImageAttachmentRef) -> Unit,
    maxWidth: Float,
) {
    if (attachments.isEmpty()) return
    val columns = when {
        attachments.size == 1 -> 1
        attachments.size == 2 -> 2
        else -> 3
    }
    val gap = 6f
    val tile = ((maxWidth - gap * (columns - 1)).coerceAtLeast(72f) / columns)
    View {
        attr {
            flexDirectionRow()
            flexWrap(FlexWrap.WRAP)
            marginTop(8f)
        }
        attachments.forEach { ref ->
            val key = ref.attachmentId.ifEmpty { ref.localId }
            val preview = when {
                ref.localId.isNotEmpty() -> attachmentDataUrl(ref.localId)
                else -> attachmentDataUrl(ref.attachmentId)
            }
            View {
                attr {
                    size(tile, tile)
                    marginRight(gap)
                    marginBottom(gap)
                    borderRadius(10f)
                    overflow(true)
                    backgroundColor(tokens.surfaceVariant)
                    allCenter()
                }
                if (preview != null) {
                    Image {
                        attr {
                            src(preview)
                            size(tile, tile)
                            resizeCover()
                        }
                    }
                } else {
                    Text {
                        attr {
                            text(if (ref.name.isNotEmpty()) ref.name else "图片加载中")
                            fontSize(11f)
                            color(tokens.secondaryText)
                            textAlignCenter()
                        }
                    }
                }
                DshHitButton { onPreview(ref) }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshAttachmentSheet(
    visible: () -> Boolean,
    hint: () -> String,
    onPickAlbum: () -> Unit,
    onCapture: () -> Unit,
    onClose: () -> Unit,
) {
    ActionSheet {
        attr {
            showActionSheet(visible())
            inWindow(true)
            customContentView {
                View {
                    attr {
                        flexDirectionColumn()
                        backgroundColor(tokens.surface)
                        borderRadius(16f, 16f, 0f, 0f)
                        padding(16f, 16f, 24f, 16f)
                    }
                    Text {
                        attr {
                            text("添加图片")
                            fontSize(16f)
                            fontWeightMedium()
                            color(tokens.primaryText)
                        }
                    }
                    Text {
                        attr {
                            text(hint())
                            marginTop(6f)
                            marginBottom(12f)
                            fontSize(12f)
                            color(tokens.tertiaryText)
                        }
                    }
                    DshSheetAction("从相册选择", onPickAlbum)
                    DshSheetAction("拍照", onCapture)
                    View {
                        attr {
                            height(44f)
                            marginTop(8f)
                            allCenter()
                            borderRadius(12f)
                            backgroundColor(tokens.surfaceVariant)
                        }
                        Text {
                            attr {
                                text("取消")
                                fontSize(15f)
                                color(tokens.secondaryText)
                            }
                        }
                        DshHitButton { onClose() }
                    }
                }
            }
        }
        event {
            clickBackgroundMask { onClose() }
        }
    }
}

private fun ViewContainer<*, *>.DshSheetAction(title: String, onClick: () -> Unit) {
    View {
        attr {
            height(48f)
            marginBottom(8f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(14f)
            borderRadius(12f)
            backgroundColor(tokens.surfaceVariant)
        }
        Text {
            attr {
                text(title)
                fontSize(16f)
                color(tokens.primaryText)
            }
        }
        DshHitButton { onClick() }
    }
}

internal fun ViewContainer<*, *>.DshImagePreviewModal(
    visible: () -> Boolean,
    title: () -> String,
    src: () -> String,
    caption: () -> String,
    onClose: () -> Unit,
) {
    vif({ visible() }) {
        Modal(inWindow = true) {
            attr {
                absolutePositionAllZero()
                backgroundColor(Color(0xF2000000L))
            }
            View {
                attr {
                    absolutePositionAllZero()
                    flexDirectionColumn()
                    paddingTop(pagerData.statusBarHeight + 8f)
                    paddingBottom(pagerData.safeAreaInsets.bottom + 16f)
                    paddingLeft(16f)
                    paddingRight(16f)
                }
                View {
                    attr {
                        height(44f)
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    Text {
                        attr {
                            text(title())
                            flex(1f)
                            fontSize(16f)
                            fontWeightMedium()
                            color(Color.WHITE)
                            lines(1)
                        }
                    }
                    View {
                        attr { size(36f, 36f); allCenter() }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("x.svg"))
                                size(20f, 20f)
                                tintColor(Color.WHITE)
                            }
                        }
                        DshHitButton { onClose() }
                    }
                }
                View {
                    attr {
                        flex(1f)
                        allCenter()
                    }
                    vif({ src().isNotEmpty() }) {
                        Image {
                            attr {
                                src(src())
                                width(pagerData.pageViewWidth - 32f)
                                height((pagerData.pageViewHeight - 160f).coerceAtLeast(200f))
                                resizeContain()
                            }
                        }
                    }
                    vif({ src().isEmpty() }) {
                        Text {
                            attr {
                                text("图片加载中")
                                fontSize(14f)
                                color(Color.WHITE)
                            }
                        }
                    }
                    DshHitButton { onClose() }
                }
                vif({ caption().isNotEmpty() }) {
                    Text {
                        attr {
                            text(caption())
                            fontSize(12f)
                            color(Color(0xCCFFFFFFL))
                            marginTop(8f)
                        }
                    }
                }
            }
        }
    }
}
