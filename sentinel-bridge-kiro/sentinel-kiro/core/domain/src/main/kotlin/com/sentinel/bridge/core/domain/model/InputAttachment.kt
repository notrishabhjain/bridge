package com.sentinel.bridge.core.domain.model

/**
 * Represents an additional input attachment associated with a pipeline session.
 *
 * Attachments carry supplementary data (audio files, images, documents) that
 * accompany the primary transcript content in [InputContext].
 *
 * @property type Logical type of the attachment (e.g., "audio", "image", "document").
 * @property uri URI string pointing to the attachment content.
 * @property mimeType Optional MIME type of the attachment (e.g., "audio/wav", "image/png").
 */
data class InputAttachment(
    val type: String,
    val uri: String,
    val mimeType: String?
)
