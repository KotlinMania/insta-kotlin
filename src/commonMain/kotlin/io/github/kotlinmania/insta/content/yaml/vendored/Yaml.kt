// port-lint: source content/yaml/vendored/yaml.rs
package io.github.kotlinmania.insta.content.yaml.vendored

data class YamlHashEntry(
    val key: Yaml,
    val value: Yaml,
)

/**
 * A YAML node is stored as this [Yaml] sealed class, which provides an easy way to
 * access a YAML document.
 */
sealed class Yaml {
    data class Real(
        val value: kotlin.String,
    ) : Yaml()

    data class Integer(
        val value: Long,
    ) : Yaml()

    data class StringValue(
        val value: kotlin.String,
    ) : Yaml()

    data class BooleanValue(
        val value: Boolean,
    ) : Yaml()

    data class Array(
        val value: List<Yaml>,
    ) : Yaml()

    data class Hash(
        val value: List<YamlHashEntry>,
    ) : Yaml()

    data object Null : Yaml()

    data object BadValue : Yaml()

    fun asStr(): kotlin.String? = (this as? StringValue)?.value

    fun asI64(): Long? = (this as? Integer)?.value

    fun asF64(): Double? = (this as? Real)?.let { parseF64(it.value) }

    fun asBool(): Boolean? = (this as? BooleanValue)?.value

    fun asVec(): List<Yaml>? = (this as? Array)?.value

    fun asHash(): List<YamlHashEntry>? = (this as? Hash)?.value

    fun isNull(): Boolean = this is Null

    fun isBadValue(): Boolean = this is BadValue

    fun isArray(): Boolean = this is Array

    companion object {
        fun fromStr(v: kotlin.String): Yaml {
            if (v.startsWith("0x")) {
                val n = v.drop(2).toLongOrNull(16)
                if (n != null) {
                    return Integer(n)
                }
            }
            if (v.startsWith("0o")) {
                val n = v.drop(2).toLongOrNull(8)
                if (n != null) {
                    return Integer(n)
                }
            }
            if (v.startsWith("+") && v.drop(1).toLongOrNull() != null) {
                return Integer(v.drop(1).toLong())
            }
            return when (v) {
                "~", "null" -> {
                    Null
                }

                "true" -> {
                    BooleanValue(true)
                }

                "false" -> {
                    BooleanValue(false)
                }

                else -> {
                    val intVal = v.toLongOrNull()
                    if (intVal != null) {
                        Integer(intVal)
                    } else if (parseF64(v) != null) {
                        Real(v)
                    } else {
                        StringValue(v)
                    }
                }
            }
        }
    }
}

internal fun parseF64(v: kotlin.String): Double? =
    when (v) {
        ".inf", ".Inf", ".INF", "+.inf", "+.Inf", "+.INF" -> Double.POSITIVE_INFINITY
        "-.inf", "-.Inf", "-.INF" -> Double.NEGATIVE_INFINITY
        ".nan", "NaN", ".NAN" -> Double.NaN
        else -> v.toDoubleOrNull()
    }

class YamlLoader(
    val docs: List<Yaml> = emptyList(),
) {
    companion object {
        fun loadFromStr(source: kotlin.String): List<Yaml>? {
            val trimmed = source.trim()
            if (trimmed.isEmpty()) {
                return emptyList()
            }
            val body =
                if (trimmed.startsWith("---")) {
                    trimmed.removePrefix("---").trim()
                } else {
                    trimmed
                }
            if (body.isEmpty() || body == "{}") {
                return listOf(Yaml.Hash(emptyList()))
            }
            val parsed = parseYamlDocument(body) ?: return null
            return listOf(parsed)
        }
    }
}

private fun parseYamlDocument(body: kotlin.String): Yaml? {
    val trimmed = body.trim()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        val cursor = JsonLikeCursor(trimmed)
        val value = cursor.parseValue() ?: return null
        cursor.skipWhitespace()
        if (cursor.pos != trimmed.length) return null
        return value
    }
    if (trimmed.contains('{') || trimmed.contains('}') || trimmed.contains('[') || trimmed.contains(']')) {
        return null
    }
    val lines = body.lines()
    return parseIndentedBlock(lines, 0, lines.size)
}

private fun parseIndentedBlock(
    lines: List<kotlin.String>,
    startIdx: Int,
    endIdx: Int,
): Yaml? {
    val entries = mutableListOf<YamlHashEntry>()
    var i = startIdx
    while (i < endIdx) {
        val rawLine = lines[i]
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed == "---") {
            i++
            continue
        }
        val colonIdx = rawLine.indexOf(':')
        if (colonIdx <= 0) {
            return null
        }
        val keyStr = rawLine.substring(0, colonIdx).trim()
        val keyYaml = parseScalarToken(keyStr) ?: return null
        val rest = rawLine.substring(colonIdx + 1).trim()
        if (rest.isNotEmpty()) {
            val valYaml =
                when (rest) {
                    "{}" -> Yaml.Hash(emptyList())
                    "[]" -> Yaml.Array(emptyList())
                    else -> parseScalarToken(rest) ?: return null
                }
            entries.add(YamlHashEntry(keyYaml, valYaml))
            i++
        } else {
            val parentIndent = rawLine.takeWhile { it == ' ' }.length
            var j = i + 1
            while (j < endIdx) {
                val nextTrimmed = lines[j].trim()
                if (nextTrimmed.isEmpty() || nextTrimmed.startsWith("#")) {
                    j++
                    continue
                }
                val nextIndent = lines[j].takeWhile { it == ' ' }.length
                if (nextIndent <= parentIndent) {
                    break
                }
                j++
            }
            val nested = parseIndentedBlock(lines, i + 1, j) ?: return null
            entries.add(YamlHashEntry(keyYaml, nested))
            i = j
        }
    }
    return Yaml.Hash(entries)
}

private fun parseScalarToken(token: kotlin.String): Yaml? {
    if (token.startsWith("\"")) {
        if (!token.endsWith("\"") || token.length < 2) return null
        return Yaml.StringValue(unescapeDoubleQuoted(token.substring(1, token.length - 1)) ?: return null)
    }
    if (token.startsWith("'")) {
        if (!token.endsWith("'") || token.length < 2) return null
        return Yaml.StringValue(token.substring(1, token.length - 1).replace("''", "'"))
    }
    return Yaml.fromStr(token)
}

private fun unescapeDoubleQuoted(raw: kotlin.String): kotlin.String? {
    val sb = StringBuilder()
    var i = 0
    while (i < raw.length) {
        val c = raw[i++]
        if (c == '\\') {
            if (i >= raw.length) return null
            val esc = raw[i++]
            if (esc == 'u') {
                if (i + 4 > raw.length) return null
                val hex = raw.substring(i, i + 4).toIntOrNull(16) ?: return null
                sb.append(hex.toChar())
                i += 4
            } else {
                val decoded =
                    when (esc) {
                        '"' -> '"'
                        '\\' -> '\\'
                        '/' -> '/'
                        'b' -> '\b'
                        'f' -> '\u000c'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        '0' -> '\u0000'
                        else -> esc
                    }
                sb.append(decoded)
            }
        } else {
            sb.append(c)
        }
    }
    return sb.toString()
}

private class JsonLikeCursor(
    val input: kotlin.String,
) {
    var pos: Int = 0

    fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) {
            pos++
        }
    }

    fun parseValue(): Yaml? {
        skipWhitespace()
        if (pos >= input.length) return null
        return when (input[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            else -> parsePrimitive()
        }
    }

    private fun parseObject(): Yaml? {
        pos++
        skipWhitespace()
        if (pos < input.length && input[pos] == '}') {
            pos++
            return Yaml.Hash(emptyList())
        }
        val entries = mutableListOf<YamlHashEntry>()
        while (pos < input.length) {
            val key = parseValue() ?: return null
            skipWhitespace()
            if (pos >= input.length || input[pos] != ':') return null
            pos++
            val value = parseValue() ?: return null
            entries.add(YamlHashEntry(key, value))
            skipWhitespace()
            if (pos >= input.length) return null
            when (input[pos]) {
                ',' -> {
                    pos++
                }

                '}' -> {
                    pos++
                    return Yaml.Hash(entries)
                }

                else -> {
                    return null
                }
            }
        }
        return null
    }

    private fun parseArray(): Yaml? {
        pos++
        skipWhitespace()
        if (pos < input.length && input[pos] == ']') {
            pos++
            return Yaml.Array(emptyList())
        }
        val items = mutableListOf<Yaml>()
        while (pos < input.length) {
            val item = parseValue() ?: return null
            items.add(item)
            skipWhitespace()
            if (pos >= input.length) return null
            when (input[pos]) {
                ',' -> {
                    pos++
                }

                ']' -> {
                    pos++
                    return Yaml.Array(items)
                }

                else -> {
                    return null
                }
            }
        }
        return null
    }

    private fun parseString(): Yaml? {
        pos++
        val sb = StringBuilder()
        while (pos < input.length) {
            val c = input[pos++]
            if (c == '"') {
                return Yaml.StringValue(sb.toString())
            }
            if (c == '\\') {
                if (pos >= input.length) return null
                val esc = input[pos++]
                if (esc == 'u') {
                    if (pos + 4 > input.length) return null
                    val hex = input.substring(pos, pos + 4).toIntOrNull(16) ?: return null
                    sb.append(hex.toChar())
                    pos += 4
                } else {
                    val decoded =
                        when (esc) {
                            '"' -> '"'
                            '\\' -> '\\'
                            '/' -> '/'
                            'b' -> '\b'
                            'f' -> '\u000c'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> return null
                        }
                    sb.append(decoded)
                }
            } else {
                sb.append(c)
            }
        }
        return null
    }

    private fun parsePrimitive(): Yaml? {
        val start = pos
        while (pos < input.length && !input[pos].isWhitespace() && input[pos] !in ",{}[]:") {
            pos++
        }
        if (pos == start) return null
        val token = input.substring(start, pos)
        return when (token) {
            "null", "~" -> {
                Yaml.Null
            }

            "true" -> {
                Yaml.BooleanValue(true)
            }

            "false" -> {
                Yaml.BooleanValue(false)
            }

            else -> {
                val l = token.toLongOrNull()
                if (l != null) {
                    Yaml.Integer(l)
                } else if (token.toDoubleOrNull() != null) {
                    Yaml.Real(token)
                } else {
                    null
                }
            }
        }
    }
}
