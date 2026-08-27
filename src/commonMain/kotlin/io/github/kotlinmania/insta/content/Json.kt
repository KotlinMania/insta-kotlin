// port-lint: source insta/src/content/json.rs
package io.github.kotlinmania.insta.content

/**
 * The maximum number of characters to print in a single line
 * when [toStringPretty] is used.
 */
const val COMPACT_MAX_CHARS: Int = 120

enum class Format {
    Condensed,
    SingleLine,
    Pretty,
}

/**
 * Serializes content to JSON.
 */
class Serializer(
    private val out: StringBuilder = StringBuilder(),
    var format: Format = Format.Condensed,
    private var indentation: Int = 0,
) {
    companion object {
        fun new(): Serializer = Serializer()
    }

    fun intoResult(): kotlin.String = out.toString()

    private fun writeIndentation() {
        if (format == Format.Pretty) {
            val spaces = indentation * 2
            for (i in 0 until spaces) {
                out.append(' ')
            }
        }
    }

    private fun startContainer(c: kotlin.Char) {
        writeChar(c)
        indentation += 1
    }

    private fun endContainer(c: kotlin.Char, empty: Boolean) {
        indentation -= 1
        if (format == Format.Pretty && !empty) {
            writeChar('\n')
            writeIndentation()
        }
        writeChar(c)
    }

    private fun writeComma(first: Boolean) {
        when (format) {
            Format.Pretty -> {
                if (first) {
                    writeChar('\n')
                } else {
                    writeStr(",\n")
                }
                writeIndentation()
            }
            Format.Condensed -> {
                if (!first) {
                    writeChar(',')
                }
            }
            Format.SingleLine -> {
                if (!first) {
                    writeStr(", ")
                }
            }
        }
    }

    private fun writeColon() {
        when (format) {
            Format.Pretty, Format.SingleLine -> writeStr(": ")
            Format.Condensed -> writeChar(':')
        }
    }

    private fun serializeArray(items: List<Content>) {
        startContainer('[')
        for ((idx, item) in items.withIndex()) {
            writeComma(idx == 0)
            serialize(item)
        }
        endContainer(']', items.isEmpty())
    }

    private fun serializeObject(fields: List<Content.Field>) {
        startContainer('{')
        for ((idx, field) in fields.withIndex()) {
            writeComma(idx == 0)
            writeEscapedStr(field.name)
            writeColon()
            serialize(field.value)
        }
        endContainer('}', fields.isEmpty())
    }

    fun serialize(value: Content) {
        when (value) {
            is Content.Bool -> if (value.value) writeStr("true") else writeStr("false")
            is Content.U8 -> writeStr(value.value.toString())
            is Content.U16 -> writeStr(value.value.toString())
            is Content.U32 -> writeStr(value.value.toString())
            is Content.U64 -> writeStr(value.value.toString())
            is Content.U128 -> writeStr(value.value.toString())
            is Content.I8 -> writeStr(value.value.toString())
            is Content.I16 -> writeStr(value.value.toString())
            is Content.I32 -> writeStr(value.value.toString())
            is Content.I64 -> writeStr(value.value.toString())
            is Content.I128 -> writeStr(value.value.toString())
            is Content.F32 -> writeFloat(value.value.toDouble(), !value.value.isNaN() && !value.value.isInfinite())
            is Content.F64 -> writeFloat(value.value, !value.value.isNaN() && !value.value.isInfinite())
            is Content.Char -> writeEscapedStr(value.value.toString())
            is Content.Str -> writeEscapedStr(value.value)
            is Content.Bytes -> {
                startContainer('[')
                for ((idx, byte) in value.value.withIndex()) {
                    writeComma(idx == 0)
                    writeStr(byte.toString())
                }
                endContainer(']', value.value.isEmpty())
            }
            Content.None, Content.UnitValue, is Content.UnitStruct -> writeStr("null")
            is Content.Some -> serialize(value.value)
            is Content.UnitVariant -> writeEscapedStr(value.variant)
            is Content.NewtypeStruct -> serialize(value.value)
            is Content.NewtypeVariant -> {
                startContainer('{')
                writeComma(true)
                writeEscapedStr(value.variant)
                writeColon()
                serialize(value.value)
                endContainer('}', false)
            }
            is Content.Seq -> serializeArray(value.value)
            is Content.Tuple -> serializeArray(value.value)
            is Content.TupleStruct -> serializeArray(value.value)
            is Content.TupleVariant -> {
                startContainer('{')
                writeComma(true)
                writeEscapedStr(value.variant)
                writeColon()
                serializeArray(value.value)
                endContainer('}', false)
            }
            is Content.Map -> {
                startContainer('{')
                for ((idx, entry) in value.value.withIndex()) {
                    writeComma(idx == 0)
                    val realKey = entry.key.resolveInner()
                    when {
                        realKey is Content.Str -> writeEscapedStr(realKey.value)
                        realKey.asI64() != null -> writeEscapedStr(realKey.asI64()!!.toString())
                        realKey.asI128() != null -> writeEscapedStr(realKey.asI128()!!.toString())
                        realKey.asU64() != null -> writeEscapedStr(realKey.asU64()!!.toString())
                        realKey.asU128() != null -> writeEscapedStr(realKey.asU128()!!.toString())
                        else -> error("cannot serialize maps without string keys to JSON")
                    }
                    writeColon()
                    serialize(entry.value)
                }
                endContainer('}', value.value.isEmpty())
            }
            is Content.Struct -> serializeObject(value.fields)
            is Content.StructVariant -> {
                startContainer('{')
                writeComma(true)
                writeEscapedStr(value.variant)
                writeColon()
                serializeObject(value.fields)
                endContainer('}', false)
            }
        }
    }

    private fun writeFloat(n: Double, isFinite: Boolean) {
        if (isFinite) {
            val start = out.length
            out.append(n.toString())
            if (!out.substring(start).contains('.')) {
                out.append(".0")
            }
        } else {
            writeStr("null")
        }
    }

    private fun writeStr(s: kotlin.String) {
        out.append(s)
    }

    private fun writeChar(c: kotlin.Char) {
        out.append(c)
    }

    private fun writeEscapedStr(value: kotlin.String) {
        writeChar('"')
        var start = 0
        for (i in value.indices) {
            val ch = value[i]
            val code = ch.code
            val escape = if (code < 256) ESCAPE[code] else 0
            if (escape == 0) {
                continue
            }
            if (start < i) {
                writeStr(value.substring(start, i))
            }
            when (escape.toByte()) {
                BB -> writeStr("\\b")
                TT -> writeStr("\\t")
                NN -> writeStr("\\n")
                FF -> writeStr("\\f")
                RR -> writeStr("\\r")
                QU -> writeStr("\\\"")
                BS -> writeStr("\\\\")
                U -> {
                    val hex = "0123456789abcdef"
                    writeStr("\\u00")
                    writeChar(hex[(code shr 4) and 0xF])
                    writeChar(hex[code and 0xF])
                }
                else -> {}
            }
            start = i + 1
        }
        if (start != value.length) {
            writeStr(value.substring(start))
        }
        writeChar('"')
    }
}

private const val BB: Byte = 'b'.code.toByte() // \x08
private const val TT: Byte = 't'.code.toByte() // \x09
private const val NN: Byte = 'n'.code.toByte() // \x0A
private const val FF: Byte = 'f'.code.toByte() // \x0C
private const val RR: Byte = 'r'.code.toByte() // \x0D
private const val QU: Byte = '"'.code.toByte() // \x22
private const val BS: Byte = '\\'.code.toByte() // \x5C
private const val U: Byte = 'u'.code.toByte() // \x00...\x1F control characters

private val ESCAPE: IntArray = IntArray(256) { idx ->
    when (idx) {
        0x08 -> BB.toInt()
        0x09 -> TT.toInt()
        0x0A -> NN.toInt()
        0x0C -> FF.toInt()
        0x0D -> RR.toInt()
        0x22 -> QU.toInt()
        0x5C -> BS.toInt()
        in 0x00..0x1F -> U.toInt()
        else -> 0
    }
}

/**
 * Serializes a value to JSON.
 */
fun toString(value: Content): kotlin.String {
    val ser = Serializer()
    ser.serialize(value)
    return ser.intoResult()
}

/**
 * Serializes a value to JSON in single-line format.
 */
fun toStringCompact(value: Content): kotlin.String {
    val ser = Serializer(format = Format.SingleLine)
    ser.serialize(value)
    val rv = ser.intoResult()
    return if (rv.length > COMPACT_MAX_CHARS) {
        toStringPretty(value)
    } else {
        rv
    }
}

/**
 * Serializes a value to JSON pretty.
 */
fun toStringPretty(value: Content): kotlin.String {
    val ser = Serializer(format = Format.Pretty)
    ser.serialize(value)
    return ser.intoResult()
}
