// port-lint: tests test.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content
import kotlin.test.Test
import kotlin.test.assertEquals

class InstaTest {
    @Test
    fun testEmbeddedTest() {
        assertSnapshot("Just a string", "Just a string", "embedded")
    }

    @Test
    fun testJsonSnapshot() {
        val content = Content.Struct(
            "User",
            listOf(
                Content.Field("id", Content.from(12345L)),
                Content.Field("name", Content.from("John Doe")),
            ),
        )
        assertJsonSnapshot(
            content,
            """{
  "id": 12345,
  "name": "John Doe"
}""",
        )
    }

    @Test
    fun testRedactionInSettings() {
        val content = Content.Struct(
            "User",
            listOf(
                Content.Field("id", Content.from(12345L)),
                Content.Field("name", Content.from("John Doe")),
            ),
        )

        withSettings({
            addRedaction(".id", Redaction.from("[REDACTED]"))
        }) {
            assertJsonSnapshot(
                content,
                """{
  "id": "[REDACTED]",
  "name": "John Doe"
}""",
            )
        }
    }
}
