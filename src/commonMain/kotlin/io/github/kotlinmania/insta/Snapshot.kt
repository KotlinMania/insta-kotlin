// port-lint: source snapshot.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content
import io.github.kotlinmania.insta.content.ContentError
import io.github.kotlinmania.oncecell.sync.Lazy
import kotlin.time.Clock
import io.github.kotlinmania.insta.content.toString as jsonToString
import io.github.kotlinmania.insta.content.yaml.parseStr as yamlParseStr
import io.github.kotlinmania.insta.content.yaml.toString as yamlToString

private val RUN_ID: Lazy<kotlin.String> =
    Lazy.new {
        val d = Clock.System.now()
        "${d.epochSeconds}-${d.nanosecondsOfSecond}"
    }

private val virtualTextFiles: MutableMap<kotlin.String, kotlin.String> = mutableMapOf()
private val virtualBinaryFiles: MutableMap<kotlin.String, List<Byte>> = mutableMapOf()

internal fun writeTextFile(
    path: kotlin.String,
    contents: kotlin.String,
) {
    virtualTextFiles[path] = contents
}

internal fun appendTextFile(
    path: kotlin.String,
    contents: kotlin.String,
) {
    val existing = virtualTextFiles[path].orEmpty()
    virtualTextFiles[path] = existing + contents
}

internal fun readTextFile(path: kotlin.String): kotlin.String? = virtualTextFiles[path]

internal fun removeVirtualFile(path: kotlin.String) {
    virtualTextFiles.remove(path)
    virtualBinaryFiles.remove(path)
}

internal fun writeBinaryFile(
    path: kotlin.String,
    contents: List<Byte>,
) {
    virtualBinaryFiles[path] = contents
}

internal fun readBinaryFile(path: kotlin.String): List<Byte>? = virtualBinaryFiles[path]

/**
 * Holds a pending inline snapshot loaded from a json file or read from an assert
 * macro.
 */
data class PendingInlineSnapshot(
    val runId: kotlin.String,
    val line: Int,
    val new: Snapshot?,
    val old: Snapshot?,
) {
    fun save(p: kotlin.String) {
        val s = jsonToString(asContent()) + "\n"
        appendTextFile(p, s)
    }

    fun asContent(): Content {
        val fields =
            listOf(
                Content.Field("run_id", Content.from(runId)),
                Content.Field("line", Content.from(line.toUInt())),
                Content.Field("new", new?.asContent() ?: Content.None),
                Content.Field("old", old?.asContent() ?: Content.None),
            )
        return Content.Struct("PendingInlineSnapshot", fields)
    }

    companion object {
        fun new(
            new: Snapshot?,
            old: Snapshot?,
            line: Int,
        ): PendingInlineSnapshot =
            PendingInlineSnapshot(
                runId = RUN_ID.value,
                line = line,
                new = new,
                old = old,
            )

        fun loadBatch(p: kotlin.String): List<PendingInlineSnapshot> {
            val contents =
                readTextFile(p)
                    ?: throw IllegalArgumentException(ContentError.FileIo("File not found", p).toString())

            val rv =
                rustLines(contents)
                    .map { line ->
                        val value = yamlParseStr(line, p)
                        fromContent(value)
                    }.toMutableList()

            val lastRunId = rv.lastOrNull()?.runId
            if (lastRunId != null) {
                rv.retainAll { it.runId == lastRunId }
            }

            return rv
        }

        fun saveBatch(
            p: kotlin.String,
            batch: List<PendingInlineSnapshot>,
        ) {
            removeVirtualFile(p)
            for (snap in batch) {
                snap.save(p)
            }
        }

        fun fromContent(content: Content): PendingInlineSnapshot {
            when (val inner = content.resolveInner()) {
                is Content.Map -> {
                    var runId: kotlin.String? = null
                    var line: Int? = null
                    var old: Snapshot? = null
                    var new: Snapshot? = null

                    for (entry in inner.value) {
                        when (entry.key.asStr()) {
                            "run_id" -> {
                                runId = entry.value.asStr()
                            }

                            "line" -> {
                                line = entry.value.asU64()?.toInt()
                            }

                            "old" -> {
                                if (!entry.value.isNil()) {
                                    old = Snapshot.fromContent(entry.value, TextSnapshotKind.Inline)
                                }
                            }

                            "new" -> {
                                if (!entry.value.isNil()) {
                                    new = Snapshot.fromContent(entry.value, TextSnapshotKind.Inline)
                                }
                            }
                        }
                    }

                    return PendingInlineSnapshot(
                        runId = runId ?: throw IllegalArgumentException(ContentError.MissingField.toString()),
                        line = line ?: throw IllegalArgumentException(ContentError.MissingField.toString()),
                        new = new,
                        old = old,
                    )
                }

                is Content.Struct -> {
                    var runId: kotlin.String? = null
                    var line: Int? = null
                    var old: Snapshot? = null
                    var new: Snapshot? = null

                    for (field in inner.fields) {
                        when (field.name) {
                            "run_id" -> {
                                runId = field.value.asStr()
                            }

                            "line" -> {
                                line = field.value.asU64()?.toInt()
                            }

                            "old" -> {
                                if (!field.value.isNil()) {
                                    old = Snapshot.fromContent(field.value, TextSnapshotKind.Inline)
                                }
                            }

                            "new" -> {
                                if (!field.value.isNil()) {
                                    new = Snapshot.fromContent(field.value, TextSnapshotKind.Inline)
                                }
                            }
                        }
                    }

                    return PendingInlineSnapshot(
                        runId = runId ?: throw IllegalArgumentException(ContentError.MissingField.toString()),
                        line = line ?: throw IllegalArgumentException(ContentError.MissingField.toString()),
                        new = new,
                        old = old,
                    )
                }

                else -> {
                    throw IllegalArgumentException(ContentError.UnexpectedDataType.toString())
                }
            }
        }
    }
}

sealed class SnapshotKind {
    data object Text : SnapshotKind()

    data class Binary(
        val extension: kotlin.String,
    ) : SnapshotKind()
}

private enum class TmpSnapshotKind {
    Text,
    Binary,
}

/**
 * Snapshot metadata information.
 */
class MetaData(
    source: kotlin.String? = null,
    assertionLine: Int? = null,
    description: kotlin.String? = null,
    expression: kotlin.String? = null,
    info: Content? = null,
    inputFile: kotlin.String? = null,
    var snapshotKind: SnapshotKind = SnapshotKind.Text,
) {
    internal var sourceValue: kotlin.String? = source
    internal var assertionLineValue: Int? = assertionLine
    internal var descriptionValue: kotlin.String? = description
    internal var expressionValue: kotlin.String? = expression
    internal var infoValue: Content? = info
    internal var inputFileValue: kotlin.String? = inputFile

    /** Returns the absolute source path. */
    fun source(): kotlin.String? = sourceValue

    /** Returns the assertion line. */
    fun assertionLine(): Int? = assertionLineValue

    /** Returns the expression that created the snapshot. */
    fun expression(): kotlin.String? = expressionValue

    /** Returns the description that created the snapshot. */
    fun description(): kotlin.String? = descriptionValue?.takeIf { it.isNotEmpty() }

    /** Returns the embedded info. */
    fun privateInfo(): Content? = infoValue

    /** Returns the relative source path. */
    fun getRelativeSource(base: kotlin.String): kotlin.String? {
        val currentSource = sourceValue ?: return null
        val normalizedBase = normalizePathSegments(base)
        val joined =
            if (currentSource.startsWith("/")) {
                normalizePathSegments(currentSource)
            } else {
                normalizePathSegments("$normalizedBase/$currentSource")
            }
        val prefix = if (normalizedBase.endsWith("/")) normalizedBase else "$normalizedBase/"
        return if (joined.startsWith(prefix)) {
            joined.removePrefix(prefix)
        } else {
            base
        }
    }

    /** Returns the input file reference. */
    fun inputFile(): kotlin.String? = inputFileValue

    /**
     * Trims the metadata of fields that we do not save to `.snap` files.
     */
    fun trimForPersistence(): MetaData =
        if (assertionLineValue != null) {
            copy(assertionLine = null)
        } else {
            this
        }

    fun copy(
        source: kotlin.String? = sourceValue,
        assertionLine: Int? = assertionLineValue,
        description: kotlin.String? = descriptionValue,
        expression: kotlin.String? = expressionValue,
        info: Content? = infoValue,
        inputFile: kotlin.String? = inputFileValue,
        snapshotKind: SnapshotKind = this.snapshotKind,
    ): MetaData =
        MetaData(
            source = source,
            assertionLine = assertionLine,
            description = description,
            expression = expression,
            info = info,
            inputFile = inputFile,
            snapshotKind = snapshotKind,
        )

    fun asContent(): Content {
        val fields = mutableListOf<Content.Field>()
        sourceValue?.let { fields.add(Content.Field("source", Content.from(it))) }
        assertionLineValue?.let { fields.add(Content.Field("assertion_line", Content.from(it.toUInt()))) }
        descriptionValue?.let { fields.add(Content.Field("description", Content.from(it))) }
        expressionValue?.let { fields.add(Content.Field("expression", Content.from(it))) }
        infoValue?.let { fields.add(Content.Field("info", it)) }
        inputFileValue?.let { fields.add(Content.Field("input_file", Content.from(it))) }

        when (val kind = snapshotKind) {
            is SnapshotKind.Text -> {
            }

            is SnapshotKind.Binary -> {
                fields.add(Content.Field("extension", Content.from(kind.extension)))
                fields.add(Content.Field("snapshot_kind", Content.from("binary")))
            }
        }

        return Content.Struct("MetaData", fields)
    }

    override fun equals(other: Any?): Boolean =
        other is MetaData &&
            sourceValue == other.sourceValue &&
            assertionLineValue == other.assertionLineValue &&
            descriptionValue == other.descriptionValue &&
            expressionValue == other.expressionValue &&
            infoValue == other.infoValue &&
            inputFileValue == other.inputFileValue &&
            snapshotKind == other.snapshotKind

    override fun hashCode(): Int {
        var result = sourceValue?.hashCode() ?: 0
        result = 31 * result + (assertionLineValue ?: 0)
        result = 31 * result + (descriptionValue?.hashCode() ?: 0)
        result = 31 * result + (expressionValue?.hashCode() ?: 0)
        result = 31 * result + (infoValue?.hashCode() ?: 0)
        result = 31 * result + (inputFileValue?.hashCode() ?: 0)
        result = 31 * result + snapshotKind.hashCode()
        return result
    }

    override fun toString(): kotlin.String =
        "MetaData(source=$sourceValue, assertionLine=$assertionLineValue, description=$descriptionValue, " +
            "expression=$expressionValue, info=$infoValue, inputFile=$inputFileValue, snapshotKind=$snapshotKind)"

    companion object {
        fun default(): MetaData = MetaData()

        fun fromContent(content: Content): MetaData {
            var source: kotlin.String? = null
            var assertionLine: Int? = null
            var description: kotlin.String? = null
            var expression: kotlin.String? = null
            var info: Content? = null
            var inputFile: kotlin.String? = null
            var snapshotType = TmpSnapshotKind.Text
            var extension: kotlin.String? = null

            when (val inner = content.resolveInner()) {
                is Content.Map -> {
                    for (entry in inner.value) {
                        when (entry.key.asStr()) {
                            "source" -> {
                                source = entry.value.asStr()
                            }

                            "assertion_line" -> {
                                assertionLine = entry.value.asU64()?.toInt()
                            }

                            "description" -> {
                                description = entry.value.asStr()
                            }

                            "expression" -> {
                                expression = entry.value.asStr()
                            }

                            "info" -> {
                                if (!entry.value.isNil()) info = entry.value
                            }

                            "input_file" -> {
                                inputFile = entry.value.asStr()
                            }

                            "snapshot_kind" -> {
                                snapshotType =
                                    when (entry.value.asStr()) {
                                        "binary" -> TmpSnapshotKind.Binary
                                        else -> TmpSnapshotKind.Text
                                    }
                            }

                            "extension" -> {
                                extension = entry.value.asStr()
                            }
                        }
                    }
                }

                is Content.Struct -> {
                    for (field in inner.fields) {
                        when (field.name) {
                            "source" -> {
                                source = field.value.asStr()
                            }

                            "assertion_line" -> {
                                assertionLine = field.value.asU64()?.toInt()
                            }

                            "description" -> {
                                description = field.value.asStr()
                            }

                            "expression" -> {
                                expression = field.value.asStr()
                            }

                            "info" -> {
                                if (!field.value.isNil()) info = field.value
                            }

                            "input_file" -> {
                                inputFile = field.value.asStr()
                            }

                            "snapshot_kind" -> {
                                snapshotType =
                                    when (field.value.asStr()) {
                                        "binary" -> TmpSnapshotKind.Binary
                                        else -> TmpSnapshotKind.Text
                                    }
                            }

                            "extension" -> {
                                extension = field.value.asStr()
                            }
                        }
                    }
                }

                else -> {
                    throw IllegalArgumentException(ContentError.UnexpectedDataType.toString())
                }
            }

            val kind =
                when (snapshotType) {
                    TmpSnapshotKind.Text -> {
                        SnapshotKind.Text
                    }

                    TmpSnapshotKind.Binary -> {
                        SnapshotKind.Binary(
                            extension =
                                extension
                                    ?: throw IllegalArgumentException(ContentError.MissingField.toString()),
                        )
                    }
                }
            return MetaData(source, assertionLine, description, expression, info, inputFile, kind)
        }
    }
}

enum class TextSnapshotKind {
    Inline,
    File,
}

/**
 * A helper to work with file snapshots.
 */
class Snapshot(
    moduleName: kotlin.String,
    snapshotName: kotlin.String?,
    metadata: MetaData,
    snapshot: SnapshotContents,
) {
    private val moduleNameValue: kotlin.String = moduleName
    private val snapshotNameValue: kotlin.String? = snapshotName
    private val metadataValue: MetaData = metadata
    private val snapshotValue: SnapshotContents = snapshot

    /** Returns the module name. */
    fun moduleName(): kotlin.String = moduleNameValue

    /** Returns the snapshot name. */
    fun snapshotName(): kotlin.String? = snapshotNameValue

    /** The metadata in the snapshot. */
    fun metadata(): MetaData = metadataValue

    /** The snapshot contents. */
    fun contents(): SnapshotContents = snapshotValue

    /** Snapshot contents match another snapshot's. */
    fun matches(other: Snapshot): Boolean =
        contents() == other.contents() &&
            metadataValue.snapshotKind == other.metadataValue.snapshotKind

    /** Both the exact snapshot contents and the persisted metadata match another snapshot's. */
    fun matchesFully(other: Snapshot): Boolean {
        val selfContents = contents()
        val otherContents = other.contents()
        return when {
            selfContents is SnapshotContents.Text && otherContents is SnapshotContents.Text -> {
                val contentsMatchExact = selfContents.value.matchesLatest(otherContents.value)
                when (selfContents.value.kind) {
                    TextSnapshotKind.File -> {
                        metadataValue.trimForPersistence() == other.metadataValue.trimForPersistence() &&
                            contentsMatchExact
                    }

                    TextSnapshotKind.Inline -> {
                        contentsMatchExact
                    }
                }
            }

            else -> {
                matches(other)
            }
        }
    }

    fun serializeSnapshot(md: MetaData = metadataValue): kotlin.String {
        val buf = StringBuilder(yamlToString(md.asContent()))
        buf.append("---\n")
        if (snapshotValue is SnapshotContents.Text) {
            buf.append(snapshotValue.value.toString())
            buf.append('\n')
        }
        return buf.toString()
    }

    private fun saveWithMetadata(
        path: kotlin.String,
        md: MetaData,
    ) {
        val serializedSnapshot = serializeSnapshot(md)
        writeTextFile(path, serializedSnapshot)

        if (snapshotValue is SnapshotContents.Binary) {
            val binaryPath =
                buildBinaryPath(path)
                    ?: throw IllegalArgumentException(ContentError.MissingField.toString())
            writeBinaryFile(binaryPath, snapshotValue.value)
        }
    }

    fun buildBinaryPath(path: kotlin.String): kotlin.String? =
        when (val kind = metadataValue.snapshotKind) {
            is SnapshotKind.Binary -> buildBinaryPath(kind.extension, path)
            is SnapshotKind.Text -> null
        }

    /** Saves the snapshot. */
    fun save(path: kotlin.String) {
        saveWithMetadata(path, metadataValue.trimForPersistence())
    }

    /**
     * Same as [save] but instead of writing a normal snapshot file this will write
     * a `.snap.new` file with additional information.
     */
    fun saveNew(path: kotlin.String): kotlin.String {
        val newPath = replaceExtension(path, "snap.new")
        saveWithMetadata(newPath, metadataValue)
        return newPath
    }

    override fun equals(other: Any?): Boolean =
        other is Snapshot &&
            moduleNameValue == other.moduleNameValue &&
            snapshotNameValue == other.snapshotNameValue &&
            metadataValue == other.metadataValue &&
            snapshotValue == other.snapshotValue

    override fun hashCode(): Int {
        var result = moduleNameValue.hashCode()
        result = 31 * result + (snapshotNameValue?.hashCode() ?: 0)
        result = 31 * result + metadataValue.hashCode()
        result = 31 * result + snapshotValue.hashCode()
        return result
    }

    override fun toString(): kotlin.String =
        "Snapshot(moduleName=$moduleNameValue, snapshotName=$snapshotNameValue, " +
            "metadata=$metadataValue, snapshot=$snapshotValue)"

    companion object {
        /** Loads a snapshot from a file. */
        fun fromFile(p: kotlin.String): Snapshot {
            val raw =
                readTextFile(p)
                    ?: throw IllegalArgumentException(ContentError.FileIo("File not found", p).toString())
            val allLines = rustLines(raw)
            var lineIdx = 0
            val firstLine = if (lineIdx < allLines.size) allLines[lineIdx++] else ""

            val metadata =
                if (firstLine.trimEnd() == "---") {
                    val headerLines = mutableListOf<kotlin.String>()
                    while (lineIdx < allLines.size) {
                        val line = allLines[lineIdx++]
                        if (line.trimEnd() == "---") {
                            break
                        }
                        headerLines.add(line)
                    }
                    val content = yamlParseStr(headerLines.joinToString("\n"), p)
                    MetaData.fromContent(content)
                } else {
                    val rv = MetaData.default()
                    var currentLine = firstLine
                    while (true) {
                        if (currentLine.trimEnd().isEmpty()) {
                            break
                        }
                        val parts = currentLine.split(':', limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].lowercase()
                            val value = parts[1].trim()
                            when (key) {
                                "expression" -> rv.expressionValue = value
                                "source" -> rv.sourceValue = value
                            }
                        }
                        if (lineIdx >= allLines.size) {
                            break
                        }
                        currentLine = allLines[lineIdx++]
                    }
                    println(
                        "A snapshot uses a legacy snapshot format; please update it to the new format with " +
                            "`cargo insta test --force-update-snapshots --accept`.\nSnapshot is at: $p",
                    )
                    rv
                }

            val contents =
                when (val kind = metadata.snapshotKind) {
                    is SnapshotKind.Text -> {
                        val body =
                            if (lineIdx < allLines.size) {
                                allLines.subList(lineIdx, allLines.size).joinToString("\n")
                            } else {
                                ""
                            }
                        SnapshotContents.from(
                            TextSnapshotContents(
                                contents = body,
                                kind = TextSnapshotKind.File,
                            ),
                        )
                    }

                    is SnapshotKind.Binary -> {
                        val binaryPath = buildBinaryPath(kind.extension, p)
                        val bytes =
                            readBinaryFile(binaryPath)
                                ?: throw IllegalArgumentException(
                                    ContentError.FileIo("File not found", binaryPath).toString(),
                                )
                        SnapshotContents.Binary(bytes)
                    }
                }

            val (snapshotName, moduleName) = namesOfPath(p)
            return fromComponents(
                moduleName = moduleName,
                snapshotName = snapshotName,
                metadata = metadata,
                snapshot = contents,
            )
        }

        fun fromComponents(
            moduleName: kotlin.String,
            snapshotName: kotlin.String?,
            metadata: MetaData,
            snapshot: SnapshotContents,
        ): Snapshot = Snapshot(moduleName, snapshotName, metadata, snapshot)

        fun fromContent(
            content: Content,
            kind: TextSnapshotKind,
        ): Snapshot {
            when (val inner = content.resolveInner()) {
                is Content.Map -> {
                    var moduleName: kotlin.String? = null
                    var snapshotName: kotlin.String? = null
                    var metadata: MetaData? = null
                    var snapshot: SnapshotContents? = null

                    for (entry in inner.value) {
                        when (entry.key.asStr()) {
                            "module_name" -> {
                                moduleName = entry.value.asStr()
                            }

                            "snapshot_name" -> {
                                snapshotName = entry.value.asStr()
                            }

                            "metadata" -> {
                                metadata = MetaData.fromContent(entry.value)
                            }

                            "snapshot" -> {
                                val text =
                                    entry.value.asStr()
                                        ?: throw IllegalArgumentException(ContentError.UnexpectedDataType.toString())
                                snapshot = SnapshotContents.from(TextSnapshotContents(text, kind))
                            }
                        }
                    }

                    return Snapshot(
                        moduleName =
                            moduleName
                                ?: throw IllegalArgumentException(ContentError.MissingField.toString()),
                        snapshotName = snapshotName,
                        metadata =
                            metadata
                                ?: throw IllegalArgumentException(ContentError.MissingField.toString()),
                        snapshot =
                            snapshot
                                ?: throw IllegalArgumentException(ContentError.MissingField.toString()),
                    )
                }

                is Content.Struct -> {
                    var moduleName: kotlin.String? = null
                    var snapshotName: kotlin.String? = null
                    var metadata: MetaData? = null
                    var snapshot: SnapshotContents? = null

                    for (field in inner.fields) {
                        when (field.name) {
                            "module_name" -> {
                                moduleName = field.value.asStr()
                            }

                            "snapshot_name" -> {
                                snapshotName = field.value.asStr()
                            }

                            "metadata" -> {
                                metadata = MetaData.fromContent(field.value)
                            }

                            "snapshot" -> {
                                val text =
                                    field.value.asStr()
                                        ?: throw IllegalArgumentException(ContentError.UnexpectedDataType.toString())
                                snapshot = SnapshotContents.from(TextSnapshotContents(text, kind))
                            }
                        }
                    }

                    return Snapshot(
                        moduleName =
                            moduleName
                                ?: throw IllegalArgumentException(ContentError.MissingField.toString()),
                        snapshotName = snapshotName,
                        metadata =
                            metadata
                                ?: throw IllegalArgumentException(ContentError.MissingField.toString()),
                        snapshot =
                            snapshot
                                ?: throw IllegalArgumentException(ContentError.MissingField.toString()),
                    )
                }

                else -> {
                    throw IllegalArgumentException(ContentError.UnexpectedDataType.toString())
                }
            }
        }
    }

    fun asContent(): Content {
        val fields = mutableListOf(Content.Field("module_name", Content.from(moduleNameValue)))
        snapshotNameValue?.let { fields.add(Content.Field("snapshot_name", Content.from(it))) }
        fields.add(Content.Field("metadata", metadataValue.asContent()))
        if (snapshotValue is SnapshotContents.Text) {
            fields.add(Content.Field("snapshot", Content.from(snapshotValue.value.toString())))
        }
        return Content.Struct("Content", fields)
    }
}

/**
 * The contents of a Snapshot.
 */
sealed class SnapshotContents {
    data class Text(
        val value: TextSnapshotContents,
    ) : SnapshotContents() {
        override fun equals(other: Any?): Boolean =
            other is SnapshotContents && snapshotContentsEquals(this, other)

        override fun hashCode(): Int = value.toString().hashCode()
    }

    data class Binary(
        val value: List<Byte>,
    ) : SnapshotContents() {
        override fun equals(other: Any?): Boolean =
            other is SnapshotContents && snapshotContentsEquals(this, other)

        override fun hashCode(): Int = value.hashCode()
    }

    fun isBinary(): Boolean = this is Binary

    fun isText(): Boolean = this is Text

    fun eq(other: SnapshotContents): Boolean = snapshotContentsEquals(this, other)

    companion object {
        fun from(value: TextSnapshotContents): SnapshotContents = Text(value)
    }
}

private fun snapshotContentsEquals(
    self: SnapshotContents,
    other: SnapshotContents,
): Boolean =
    when {
        self is SnapshotContents.Text && other is SnapshotContents.Text -> {
            if (self.value.matchesLatest(other.value)) {
                true
            } else if (self.value.matchesLegacy(other.value)) {
                println(
                    "${style(
                        "Snapshot test passes but the existing value is in a legacy format. " +
                            "Please run `cargo insta test --force-update-snapshots` to update to a newer format.",
                    ).yellow().bold()} Snapshot contents:\n${self.value}",
                )
                true
            } else {
                false
            }
        }

        self is SnapshotContents.Binary && other is SnapshotContents.Binary -> {
            self.value == other.value
        }

        else -> {
            false
        }
    }

data class TextSnapshotContents(
    val contents: kotlin.String,
    val kind: TextSnapshotKind = TextSnapshotKind.File,
) {
    /** Matches another snapshot without any normalization. */
    fun matchesFully(other: TextSnapshotContents): Boolean = contents == other.contents

    /** Snapshot matches based on the latest format. */
    fun matchesLatest(other: TextSnapshotContents): Boolean = toString() == other.toString()

    fun matchesLegacy(other: TextSnapshotContents): Boolean = asStrLegacy(this) == asStrLegacy(other)

    private fun asStrLegacy(sc: TextSnapshotContents): kotlin.String {
        var out = sc.toString()
        out = out.trimStart('\r', '\n')
        if (out.startsWith("---\n")) {
            out = out.removePrefix("---\n")
        }
        return when (sc.kind) {
            TextSnapshotKind.Inline -> {
                val normalized = legacyInlineNormalize(out)
                val isLegacySingleLineInMultiline =
                    sc.contents.contains('\n') && rustLines(sc.contents.trimEnd()).size <= 1
                if (isLegacySingleLineInMultiline) {
                    normalized.trimStart()
                } else {
                    normalized
                }
            }

            TextSnapshotKind.File -> {
                out
            }
        }
    }

    internal fun normalize(): kotlin.String {
        val kindSpecificNormalization =
            when (kind) {
                TextSnapshotKind.Inline -> normalizeInline(contents)
                TextSnapshotKind.File -> contents
            }
        val out = kindSpecificNormalization.trimEnd()
        return out.replace("\r\n", "\n")
    }

    /**
     * Returns the string literal, including `#` delimiters, to insert into a
     * source file.
     */
    fun toInline(indentation: kotlin.String): kotlin.String {
        val normalizedContents = normalize()
        val out = StringBuilder()

        val needsEscaping = needsEscapedFormat(normalizedContents)

        if (!needsEscaping && (normalizedContents.contains('\\') || normalizedContents.contains('"'))) {
            out.append('r')
        }

        val delimiter = "#".repeat(requiredHashes(normalizedContents))
        out.append(delimiter)

        if (needsEscaping) {
            out.append(debugEscapeString(normalizedContents))
        } else {
            out.append('"')
            if (normalizedContents.contains('\n')) {
                for (l in rustLines(normalizedContents)) {
                    val i = if (l.isEmpty()) "" else indentation
                    out.append('\n')
                    out.append(i)
                    out.append(l)
                }
                out.append('\n')
                out.append(indentation)
            } else {
                out.append(normalizedContents)
            }
            out.append('"')
        }

        out.append(delimiter)
        return out.toString()
    }

    fun fmt(): kotlin.String = normalize()

    override fun toString(): kotlin.String = fmt()

    companion object {
        fun new(
            contents: kotlin.String,
            kind: TextSnapshotKind,
        ): TextSnapshotContents = TextSnapshotContents(contents, kind)

        internal fun needsEscapedFormat(contents: kotlin.String): Boolean =
            contents.any { c -> c.isISOControl() && c != '\n' && c != '\t' && c != '\u001b' }

        fun fromInlineLiteral(contents: kotlin.String): TextSnapshotContents {
            if (rustLines(contents.trimEnd()).size <= 1) {
                return new(contents.trimEnd(), TextSnapshotKind.Inline)
            }

            if (needsEscapedFormat(contents)) {
                return new(contents.trimEnd(), TextSnapshotKind.Inline)
            }

            val lines = rustLines(contents)
            val first = lines.first()
            val remainder = lines.drop(1)
            val snapshot =
                if (first.isNotEmpty()) {
                    println(
                        "${style(
                            "Multiline inline snapshot values should start and end with a newline.",
                        ).yellow().bold()} The current value will fail to match in the future. " +
                            "Run `cargo insta test --force-update-snapshots` to rewrite snapshots. " +
                            "The existing value's first line is `$first`. Full value:\n$contents",
                    )
                    lines.joinToString("\n")
                } else {
                    remainder.joinToString("\n")
                }
            return new(snapshot, TextSnapshotKind.Inline)
        }
    }
}

internal fun buildBinaryPath(
    extension: kotlin.String,
    path: kotlin.String,
): kotlin.String = "$path.$extension"

/** The number of `#` we need to surround a raw string literal with. */
internal fun requiredHashes(text: kotlin.String): Int =
    text
        .split('"')
        .drop(1)
        .maxOfOrNull { s -> s.takeWhile { c -> c == '#' }.length + 1 }
        ?: 0

internal fun leadingSpace(value: kotlin.String): kotlin.String =
    value.takeWhile { it == ' ' || it == '\t' }

internal fun minIndentation(snapshot: kotlin.String): kotlin.String {
    val lines = rustLines(snapshot.trimEnd())
    return lines
        .filter { it.isNotEmpty() }
        .map { leadingSpace(it) }
        .minByOrNull { it.length }
        .orEmpty()
}

/**
 * Normalize snapshot value, which we apply to both generated and literal
 * snapshots. Remove excess indentation, excess ending whitespace and coerce
 * newlines to `\n`.
 */
internal fun normalizeInline(snapshot: kotlin.String): kotlin.String {
    if (rustLines(snapshot.trimEnd()).size <= 1) {
        return snapshot.trimEnd()
    }

    val indentation = minIndentation(snapshot)
    return rustLines(snapshot).joinToString("\n") { l ->
        if (l.length >= indentation.length) l.substring(indentation.length) else ""
    }
}

/** Extracts the module and snapshot name from a snapshot path. */
internal fun namesOfPath(path: kotlin.String): Pair<kotlin.String, kotlin.String> {
    val fileName = path.substringAfterLast('/').substringAfterLast('\\')
    val stem =
        if (fileName.contains('.')) {
            fileName.substringBeforeLast('.')
        } else {
            fileName
        }
    val splitIndex = stem.lastIndexOf("__")
    return if (splitIndex >= 0) {
        val moduleName = stem.substring(0, splitIndex)
        val snapshotName = stem.substring(splitIndex + 2)
        Pair(snapshotName, moduleName)
    } else {
        Pair(stem, "")
    }
}

/** Legacy format - retain so old snapshots still work. */
internal fun legacyInlineNormalize(frozenValue: kotlin.String): kotlin.String {
    if (!frozenValue.trimStart().startsWith('⋮')) {
        return frozenValue
    }
    val buf = StringBuilder()
    val lines = rustLines(frozenValue)
    var lineIndex = 0
    var indentation = 0

    while (lineIndex < lines.size) {
        val line = lines[lineIndex++]
        val lineTrimmed = line.trimStart()
        if (lineTrimmed.isEmpty()) {
            continue
        }
        indentation = line.length - lineTrimmed.length
        buf.append(lineTrimmed.removePrefix("⋮"))
        buf.append('\n')
        break
    }

    while (lineIndex < lines.size) {
        val line = lines[lineIndex++]
        if (line.length >= indentation) {
            val prefix = line.substring(0, indentation)
            if (prefix.trim().isNotEmpty()) {
                return ""
            }
            val remainder = line.substring(indentation)
            if (remainder.startsWith('⋮')) {
                buf.append(remainder.removePrefix("⋮"))
                buf.append('\n')
            } else if (remainder.trim().isEmpty()) {
                continue
            } else {
                return ""
            }
        }
    }

    return buf.toString().trimEnd()
}

private fun rustLines(s: kotlin.String): List<kotlin.String> {
    if (s.isEmpty()) {
        return emptyList()
    }
    val stripped =
        when {
            s.endsWith("\r\n") -> s.dropLast(2)
            s.endsWith("\n") -> s.dropLast(1)
            else -> s
        }
    return stripped.split('\n').map { line ->
        if (line.endsWith('\r')) line.dropLast(1) else line
    }
}

private fun debugEscapeString(s: kotlin.String): kotlin.String {
    val sb = StringBuilder()
    sb.append('"')
    for (c in s) {
        when (c) {
            '\u0000' -> sb.append("\\0")
            '\t' -> sb.append("\\t")
            '\r' -> sb.append("\\r")
            '\n' -> sb.append("\\n")
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            else -> if (c.isISOControl()) sb.append("\\u{${c.code.toString(16)}}") else sb.append(c)
        }
    }
    sb.append('"')
    return sb.toString()
}

private fun replaceExtension(
    path: kotlin.String,
    newExtension: kotlin.String,
): kotlin.String {
    val lastSlash = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
    val lastDot = path.lastIndexOf('.')
    return if (lastDot > lastSlash) {
        path.substring(0, lastDot + 1) + newExtension
    } else {
        "$path.$newExtension"
    }
}

private fun normalizePathSegments(path: kotlin.String): kotlin.String {
    val isAbsolute = path.startsWith("/")
    val segments = path.replace('\\', '/').split('/')
    val stack = mutableListOf<kotlin.String>()
    for (seg in segments) {
        if (seg == "..") {
            if (stack.isNotEmpty()) {
                stack.removeAt(stack.size - 1)
            }
        } else if (seg.isNotEmpty() && seg != ".") {
            stack.add(seg)
        }
    }
    return (if (isAbsolute) "/" else "") + stack.joinToString("/")
}
