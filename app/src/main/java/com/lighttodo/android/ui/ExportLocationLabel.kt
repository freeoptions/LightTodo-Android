package com.lighttodo.android.ui

/**
 * Turns a DocumentsProvider tree document id into a label suitable for display.
 * The document id is only presentation data; the original tree URI remains the
 * source of truth for persistence and file operations.
 */
internal fun formatDocumentTreeId(documentId: String?): String? {
    val decodedDocumentId = documentId
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    val separatorIndex = decodedDocumentId.indexOf(':')
    if (separatorIndex <= 0) {
        return null
    }

    val volumeId = decodedDocumentId.substring(0, separatorIndex).trim()
    if (volumeId.isEmpty()) {
        return null
    }

    val volumeLabel = when (volumeId.lowercase()) {
        "primary" -> "内部存储"
        "downloads" -> "下载"
        "documents" -> "文档"
        "pictures" -> "图片"
        "movies" -> "视频"
        "music" -> "音乐"
        else -> volumeId
    }
    val pathSegments = decodedDocumentId
        .substring(separatorIndex + 1)
        .split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "." }

    return (listOf(volumeLabel) + pathSegments).joinToString(" / ")
}
