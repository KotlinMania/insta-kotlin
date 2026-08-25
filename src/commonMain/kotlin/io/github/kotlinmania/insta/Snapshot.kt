// port-lint: source snapshot.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content
import io.github.kotlinmania.insta.content.toString as jsonToString

sealed class SnapshotKind {
    data object Text : SnapshotKind()
    data class Binary(val extension: kotlin.String) : SnapshotKind()
}

/**
 * Snapshot metadata information.
 */
data class MetaData(
    var source: kotlin.String? = null,
    var assertionLine: UInt? = null,
    var snapshotDescription: kotlin.String? = null,
    var expression: kotlin.String? = null,
    var info: Content? = null,
    var inputFile: kotlin.String? = null,
    var snapshotKind: SnapshotKind = SnapshotKind.Text,
) {
    fun privateInfo(): Content? = info

    fun trimForPersistence(): MetaData =
        if (assertionLine != null) {
            copy(assertionLine = null)
        } else {
            this
        }

    fun asContent(): Content {
        val fields = mutableListOf<Content.Field>()
        source?.let { fields.add(Content.Field("source", Content.from(it))) }
        assertionLine?.let { fields.add(Content.Field("assertion_line", Content.from(it))) }
        snapshotDescription?.let { fields.add(Content.Field("description", Content.from(it))) }
        expression?.let { fields.add(Content.Field("expression", Content.from(it))) }
        info?.let { fields.add(Content.Field("info", it)) }
        inputFile?.let { fields.add(Content.Field("input_file", Content.from(it))) }

        when (val kind = snapshotKind) {
            is SnapshotKind.Text -> {}
            is SnapshotKind.Binary -> {
                fields.add(Content.Field("extension", Content.from(kind.extension)))
                fields.add(Content.Field("snapshot_kind", Content.from("binary")))
            }
        }

        return Content.Struct("MetaData", fields)
    }

    companion object {
        fun fromContent(content: Content): MetaData {
            var src: kotlin.String? = null
            var line: UInt? = null
            var desc: kotlin.String? = null
            var expr: kotlin.String? = null
            var inf: Content? = null
            var input: kotlin.String? = null
            var isBinary = false
            var ext: kotlin.String? = null

            when (val inner = content.resolveInner()) {
                is Content.Map -> {
                    for (entry in inner.value) {
                        when (entry.key.asStr()) {
                            "source" -> src = entry.value.asStr()
                            "assertion_line" -> line = entry.value.asU64()?.toUInt()
                            "description" -> desc = entry.value.asStr()
                            "expression" -> expr = entry.value.asStr()
                            "info" -> if (!entry.value.isNil()) inf = entry.value
                            "input_file" -> input = entry.value.asStr()
                            "snapshot_kind" -> isBinary = entry.value.asStr() == "binary"
                            "extension" -> ext = entry.value.asStr()
                        }
                    }
                }
                is Content.Struct -> {
                    for (field in inner.fields) {
                        when (field.name) {
                            "source" -> src = field.value.asStr()
                            "assertion_line" -> line = field.value.asU64()?.toUInt()
                            "description" -> desc = field.value.asStr()
                            "expression" -> expr = field.value.asStr()
                            "info" -> if (!field.value.isNil()) inf = field.value
                            "input_file" -> input = field.value.asStr()
                            "snapshot_kind" -> isBinary = field.value.asStr() == "binary"
                            "extension" -> ext = field.value.asStr()
                        }
                    }
                }
                else -> {}
            }

            val kind = if (isBinary && ext != null) SnapshotKind.Binary(ext) else SnapshotKind.Text
            return MetaData(src, line, desc, expr, inf, input, kind)
        }
    }
}

enum class TextSnapshotKind {
    Inline,
    File,
}

data class TextSnapshotContents(
    val contents: kotlin.String,
    val kind: TextSnapshotKind = TextSnapshotKind.File,
) {
    override fun toString(): kotlin.String = contents

    fun matchesLatest(other: TextSnapshotContents): Boolean =
        contents.trimEnd() == other.contents.trimEnd()
}

sealed class SnapshotContents {
    data class Text(val value: TextSnapshotContents) : SnapshotContents()
    data class Binary(val value: List<UByte>) : SnapshotContents()

    fun isBinary(): Boolean = this is Binary

    fun isText(): Boolean = this is Text
}

/**
 * A helper to work with file snapshots.
 */
data class Snapshot(
    val moduleName: kotlin.String,
    val snapshotName: kotlin.String?,
    val metadata: MetaData,
    val snapshot: SnapshotContents,
) {
    fun contents(): SnapshotContents = snapshot

    fun matches(other: Snapshot): Boolean =
        snapshot == other.snapshot && metadata.snapshotKind == other.metadata.snapshotKind

    fun matchesFully(other: Snapshot): Boolean =
        when {
            snapshot is SnapshotContents.Text && other.snapshot is SnapshotContents.Text -> {
                val match = snapshot.value.matchesLatest(other.snapshot.value)
                metadata.trimForPersistence() == other.metadata.trimForPersistence() && match
            }
            else -> matches(other)
        }

    fun serializeSnapshot(md: MetaData = metadata): kotlin.String {
        val sb = StringBuilder()
        sb.append(jsonToString(md.asContent()))
        sb.append("\n---\n")
        if (snapshot is SnapshotContents.Text) {
            sb.append(snapshot.value.contents)
            sb.append('\n')
        }
        return sb.toString()
    }

    companion object {
        fun fromComponents(
            moduleName: kotlin.String,
            snapshotName: kotlin.String?,
            metadata: MetaData,
            snapshot: SnapshotContents,
        ): Snapshot = Snapshot(moduleName, snapshotName, metadata, snapshot)
    }
}
