// port-lint: tests serialization.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content
import io.github.kotlinmania.serde.SerdeResult
import io.github.kotlinmania.serdecore.ser.Serialize
import io.github.kotlinmania.serdecore.ser.Serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializationTest {
    @Test
    fun testYamlSerialization() {
        val content =
            Content.Map(
                listOf(
                    Content.Entry(
                        Content.from("env"),
                        Content.Seq(
                            listOf(
                                Content.from("ENVIRONMENT"),
                                Content.from("production"),
                            ),
                        ),
                    ),
                    Content.Entry(
                        Content.from("cmdline"),
                        Content.Seq(
                            listOf(
                                Content.from("my-tool"),
                                Content.from("run"),
                            ),
                        ),
                    ),
                ),
            )

        val yaml = serializeContent(content, SerializationFormat.Yaml)
        val expected = "env:\n  - ENVIRONMENT\n  - production\ncmdline:\n  - my-tool\n  - run\n"
        assertEquals(expected, yaml)

        val inlineYaml = serializeContent(content, SerializationFormat.Yaml)
        assertEquals(expected, inlineYaml)
    }

    @Test
    fun testSerializeValueAndRedacted() {
        val sample =
            object : Serialize {
                override fun <Ok> serialize(serializer: Serializer<Ok>): SerdeResult<Ok> {
                    val s = serializer.serializeStruct("Person", 2).getOrThrow()
                    s.serializeField("name", Content.from("Alice")).getOrThrow()
                    s.serializeField("token", Content.from("secret-123")).getOrThrow()
                    return s.end()
                }
            }

        val json = serializeValue(sample, SerializationFormat.Json)
        assertTrue(json.contains("\"name\": \"Alice\""))
        assertTrue(json.contains("\"token\": \"secret-123\""))

        val redactedJson =
            serializeValueRedacted(
                sample,
                listOf(Pair(Selector.parse(".token"), Redaction.from("[REDACTED]"))),
                SerializationFormat.Json,
            )
        assertTrue(redactedJson.contains("\"name\": \"Alice\""))
        assertTrue(redactedJson.contains("\"token\": \"[REDACTED]\""))
    }

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
