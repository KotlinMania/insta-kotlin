// port-lint: tests serialization.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializationTest {
    @Test
    fun testSerializeContentJson() {
        val content = Content.Map(listOf(Content.Entry(Content.Str("key"), Content.Str("value"))))
        val json = serializeContent(content, SerializationFormat.Json)
        assertTrue(json.contains("\"key\": \"value\""))

        val compact = serializeContent(content, SerializationFormat.JsonCompact)
        assertTrue(compact.contains("\"key\": \"value\""))
    }

    @Test
    fun testSerializationFormatEnum() {
        assertEquals(6, SerializationFormat.entries.size)
        assertEquals(2, SnapshotLocation.entries.size)
    }
}
