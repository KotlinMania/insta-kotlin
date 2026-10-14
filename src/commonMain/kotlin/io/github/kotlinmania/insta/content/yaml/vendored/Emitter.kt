// port-lint: source content/yaml/vendored/emitter.rs
package io.github.kotlinmania.insta.content.yaml.vendored

sealed class EmitError {
    data object FmtError : EmitError()

    fun fmt(): kotlin.String = "Format error"

    override fun toString(): kotlin.String = fmt()
}

private fun escapeStr(
    wr: StringBuilder,
    v: kotlin.String,
) {
    wr.append('"')

    var start = 0
    val bytes = v.encodeToByteArray()

    for (i in bytes.indices) {
        val byte = bytes[i].toInt() and 0xFF
        val escaped =
            when (byte) {
                '"'.code -> "\\\""
                '\\'.code -> "\\\\"
                0x00 -> "\\u0000"
                0x01 -> "\\u0001"
                0x02 -> "\\u0002"
                0x03 -> "\\u0003"
                0x04 -> "\\u0004"
                0x05 -> "\\u0005"
                0x06 -> "\\u0006"
                0x07 -> "\\u0007"
                0x08 -> "\\b"
                0x09 -> "\\t"
                0x0A -> "\\n"
                0x0B -> "\\u000b"
                0x0C -> "\\f"
                0x0D -> "\\r"
                0x0E -> "\\u000e"
                0x0F -> "\\u000f"
                0x10 -> "\\u0010"
                0x11 -> "\\u0011"
                0x12 -> "\\u0012"
                0x13 -> "\\u0013"
                0x14 -> "\\u0014"
                0x15 -> "\\u0015"
                0x16 -> "\\u0016"
                0x17 -> "\\u0017"
                0x18 -> "\\u0018"
                0x19 -> "\\u0019"
                0x1A -> "\\u001a"
                0x1B -> "\\u001b"
                0x1C -> "\\u001c"
                0x1D -> "\\u001d"
                0x1E -> "\\u001e"
                0x1F -> "\\u001f"
                0x7F -> "\\u007f"
                else -> continue
            }

        if (start < i) {
            wr.append(bytes.decodeToString(start, i))
        }

        wr.append(escaped)
        start = i + 1
    }

    if (start != bytes.size) {
        wr.append(bytes.decodeToString(start, bytes.size))
    }

    wr.append('"')
}

class YamlEmitter internal constructor(
    private val writer: StringBuilder,
    private val bestIndent: Int = 2,
    private val compact: Boolean = true,
    private var level: Int = -1,
) {
    fun dump(doc: Yaml) {
        writer.append("---\n")
        level = -1
        emitNode(doc)
    }

    private fun writeIndent() {
        if (level <= 0) {
            return
        }
        repeat(level) {
            repeat(bestIndent) {
                writer.append(' ')
            }
        }
    }

    private fun emitNode(node: Yaml) {
        when (node) {
            is Yaml.Array -> {
                emitArray(node.value)
            }

            is Yaml.Hash -> {
                emitHash(node.value)
            }

            is Yaml.StringValue -> {
                if (needQuotes(node.value)) {
                    escapeStr(writer, node.value)
                } else {
                    writer.append(node.value)
                }
            }

            is Yaml.BooleanValue -> {
                if (node.value) {
                    writer.append("true")
                } else {
                    writer.append("false")
                }
            }

            is Yaml.Integer -> {
                writer.append(node.value.toString())
            }

            is Yaml.Real -> {
                writer.append(node.value)
            }

            is Yaml.Null, is Yaml.BadValue -> {
                writer.append('~')
            }
        }
    }

    private fun emitArray(v: List<Yaml>) {
        if (v.isEmpty()) {
            writer.append("[]")
        } else {
            level += 1
            for ((cnt, x) in v.withIndex()) {
                if (cnt > 0) {
                    writer.append('\n')
                    writeIndent()
                }
                writer.append('-')
                emitVal(true, x)
            }
            level -= 1
        }
    }

    private fun emitHash(h: List<YamlHashEntry>) {
        if (h.isEmpty()) {
            writer.append("{}")
        } else {
            level += 1
            for ((cnt, entry) in h.withIndex()) {
                val k = entry.key
                val v = entry.value
                val complexKey = k is Yaml.Hash || k is Yaml.Array
                if (cnt > 0) {
                    writer.append('\n')
                    writeIndent()
                }
                if (complexKey) {
                    writer.append('?')
                    emitVal(true, k)
                    writer.append('\n')
                    writeIndent()
                    writer.append(':')
                    emitVal(true, v)
                } else {
                    emitNode(k)
                    writer.append(':')
                    emitVal(false, v)
                }
            }
            level -= 1
        }
    }

    private fun emitVal(
        inline: Boolean,
        valNode: Yaml,
    ) {
        when (valNode) {
            is Yaml.Array -> {
                if ((inline && compact) || valNode.value.isEmpty()) {
                    writer.append(' ')
                } else {
                    writer.append('\n')
                    level += 1
                    writeIndent()
                    level -= 1
                }
                emitArray(valNode.value)
            }

            is Yaml.Hash -> {
                if ((inline && compact) || valNode.value.isEmpty()) {
                    writer.append(' ')
                } else {
                    writer.append('\n')
                    level += 1
                    writeIndent()
                    level -= 1
                }
                emitHash(valNode.value)
            }

            else -> {
                writer.append(' ')
                emitNode(valNode)
            }
        }
    }

    companion object {
        internal fun new(writer: StringBuilder): YamlEmitter = YamlEmitter(writer)
    }
}

private val RESERVED_YAML_KEYWORDS: Set<kotlin.String> =
    setOf(
        "yes",
        "Yes",
        "YES",
        "no",
        "No",
        "NO",
        "True",
        "TRUE",
        "true",
        "False",
        "FALSE",
        "false",
        "on",
        "On",
        "ON",
        "off",
        "Off",
        "OFF",
        "null",
        "Null",
        "NULL",
        "~",
    )

private fun needQuotesSpaces(string: kotlin.String): Boolean =
    string.startsWith(' ') || string.endsWith(' ')

internal fun needQuotes(string: kotlin.String): Boolean =
    string.isEmpty() ||
        needQuotesSpaces(string) ||
        string.first() in "&*?|->=<!%@" ||
        string.any { character ->
            character in ":{}[],#`\"'\\\t\n\r" ||
                character in '\u0000'..'\u0006' ||
                character in '\u000e'..'\u001a' ||
                character in '\u001c'..'\u001f'
        } ||
        string in RESERVED_YAML_KEYWORDS ||
        string.startsWith('.') ||
        string.startsWith("0x") ||
        string.toLongOrNull() != null ||
        string.toDoubleOrNull() != null
