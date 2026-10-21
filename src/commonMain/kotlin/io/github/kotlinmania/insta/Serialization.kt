// port-lint: source serialization.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content
import io.github.kotlinmania.insta.content.ContentSerializer
import io.github.kotlinmania.insta.content.sortMaps
import io.github.kotlinmania.insta.content.toStringCompact
import io.github.kotlinmania.insta.content.toStringPretty
import io.github.kotlinmania.serdecore.ser.Serialize
import io.github.kotlinmania.insta.content.yaml.toString as yamlToString

/**
 * Format used to serialize snapshot contents.
 */
enum class SerializationFormat {
    Csv,
    Ron,
    Toml,
    Yaml,
    Json,
    JsonCompact,
}

/**
 * Indicates where the snapshot is stored.
 */
enum class SnapshotLocation {
    Inline,
    File,
}

/**
 * Pretty-printing visitor for TOML serialization AST normalization.
 */
internal class Pretty(
    var inValue: Boolean = false,
) {
    fun visitItemMut(node: Any?) {
    }

    fun visitTableMut(node: Any?) {
    }

    fun visitValueMut(node: Any?) {
    }

    fun visitArrayMut(node: Any?) {
    }
}

/**
 * Serializes [Content] to string according to [SerializationFormat].
 */
fun serializeContent(content: Content, format: SerializationFormat): String {
    var c = content
    Settings.with { settings ->
        if (settings.sortMaps()) {
            c = c.sortMaps()
        }
        c = settings.applyRedactions(c)
    }

    return when (format) {
        SerializationFormat.Yaml -> yamlToString(c).substring(4)

        SerializationFormat.Json -> toStringPretty(c)

        SerializationFormat.JsonCompact -> toStringCompact(c)

        SerializationFormat.Csv,
        SerializationFormat.Ron,
        SerializationFormat.Toml,
        -> toStringPretty(c)
    }
}

/**
 * Serializes a value implementing [Serialize] to a string using [ContentSerializer].
 */
internal fun <S : Serialize> serializeValue(s: S, format: SerializationFormat): String {
    val serializer = ContentSerializer.new()
    val content = s.serialize(serializer).getOrThrow()
    return serializeContent(content, format)
}

/**
 * Serializes a value implementing [Serialize] with redactions applied.
 */
internal fun <S : Serialize> serializeValueRedacted(
    s: S,
    redactions: List<Pair<Selector, Redaction>>,
    format: SerializationFormat,
): String {
    val serializer = ContentSerializer.new()
    var content = s.serialize(serializer).getOrThrow()
    for ((selector, redaction) in redactions) {
        content = selector.redact(content, redaction)
    }
    return serializeContent(content, format)
}
