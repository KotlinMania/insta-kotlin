// port-lint: source serialization.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content
import io.github.kotlinmania.insta.content.sortMaps
import io.github.kotlinmania.insta.content.toStringCompact
import io.github.kotlinmania.insta.content.toStringPretty

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
        SerializationFormat.Json -> toStringPretty(c)

        SerializationFormat.JsonCompact -> toStringCompact(c)

        SerializationFormat.Yaml,
        SerializationFormat.Csv,
        SerializationFormat.Ron,
        SerializationFormat.Toml,
        -> toStringPretty(c)
    }
}
