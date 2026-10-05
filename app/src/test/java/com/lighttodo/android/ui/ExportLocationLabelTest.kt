package com.lighttodo.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportLocationLabelTest {
    @Test
    fun `formats primary tree document id as readable storage path`() {
        assertEquals(
            "内部存储 / @Personal / 待办 / 配置",
            formatDocumentTreeId("primary:@Personal/待办/配置"),
        )
    }

    @Test
    fun `formats storage root without exposing a content uri`() {
        assertEquals("内部存储", formatDocumentTreeId("primary:"))
    }

    @Test
    fun `returns null when document id cannot be parsed`() {
        assertNull(formatDocumentTreeId("not-a-document-id"))
    }
}
