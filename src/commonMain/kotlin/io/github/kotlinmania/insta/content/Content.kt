package io.github.kotlinmania.insta.content

import io.github.kotlinmania.serde.SerdeResult
import io.github.kotlinmania.serde.serdeCatching
import io.github.kotlinmania.serdecore.ser.Serialize
import io.github.kotlinmania.serdecore.ser.Serializer

private const val LONG_MAX_DECIMAL = "9223372036854775807"
private const val LONG_MIN_ABS_DECIMAL = "9223372036854775808"

/**
 * This module implements a generic `Content` type that can hold runtime typed
 * data.
 *
 * It is modelled after serde's data format, but it can also be used
 * independently of serde. The YAML and JSON support implemented here works
 * without serde. Only YAML has an implemented parser; because YAML is a
 * superset of JSON, insta currently parses JSON through the YAML implementation.
 */

/** An internal error type for content-related errors. */
sealed class ContentError {
    data class FailedParsingYaml(
        val path: kotlin.String,
    ) : ContentError() {
        override fun toString(): kotlin.String = fmt()
    }

    data object UnexpectedDataType : ContentError() {
        override fun toString(): kotlin.String = fmt()
    }

    data object MissingField : ContentError() {
        override fun toString(): kotlin.String = fmt()
    }

    data class FileIo(
        val message: kotlin.String,
        val path: kotlin.String,
    ) : ContentError() {
        override fun toString(): kotlin.String = fmt()
    }

    fun fmt(): kotlin.String =
        when (this) {
            is FailedParsingYaml -> "Failed parsing the YAML from $path"
            UnexpectedDataType -> "The present data type wasn't what was expected"
            MissingField -> "A required field was missing"
            is FileIo -> "File error for $path: $message"
        }

    override fun toString(): kotlin.String = fmt()
}

/**
 * Unsigned 128-bit integer content represented as a normalized decimal string.
 */
class UInt128 private constructor(
    val decimal: kotlin.String,
) : Comparable<UInt128> {
    companion object {
        fun from(value: ULong): UInt128 = UInt128(value.toString())

        fun parse(value: kotlin.String): UInt128 {
            require(value.isNotEmpty()) { "UInt128 cannot be empty" }
            require(value.all { it in '0'..'9' }) { "UInt128 must be decimal digits" }
            return UInt128(value.trimLeadingZeroes())
        }
    }

    override fun compareTo(other: UInt128): Int {
        if (decimal.length != other.decimal.length) {
            return decimal.length.compareTo(other.decimal.length)
        }
        return decimal.compareTo(other.decimal)
    }

    fun toULongOrNull(): ULong? {
        var result = 0UL
        for (char in decimal) {
            val digit = (char.code - '0'.code).toULong()
            if (result > (ULong.MAX_VALUE - digit) / 10UL) {
                return null
            }
            result = result * 10UL + digit
        }
        return result
    }

    override fun toString(): kotlin.String = decimal

    override fun equals(other: Any?): Boolean =
        other is UInt128 && decimal == other.decimal

    override fun hashCode(): Int = decimal.hashCode()
}

/**
 * Signed 128-bit integer content represented as a normalized decimal string.
 */
class Int128 private constructor(
    val decimal: kotlin.String,
) : Comparable<Int128> {
    companion object {
        fun from(value: Long): Int128 = Int128(value.toString())

        fun parse(value: kotlin.String): Int128 {
            require(value.isNotEmpty()) { "Int128 cannot be empty" }
            val negative = value.first() == '-'
            val digits = if (negative) value.drop(1) else value
            require(digits.isNotEmpty()) { "Int128 must include digits" }
            require(digits.all { it in '0'..'9' }) { "Int128 must be decimal digits" }
            val normalized = digits.trimLeadingZeroes()
            return Int128(if (negative && normalized != "0") "-$normalized" else normalized)
        }
    }

    val isNegative: Boolean
        get() = decimal.startsWith("-")

    override fun compareTo(other: Int128): Int {
        if (isNegative != other.isNegative) {
            return if (isNegative) -1 else 1
        }
        val s1 = if (isNegative) decimal.substring(1) else decimal
        val s2 = if (other.isNegative) other.decimal.substring(1) else other.decimal
        val cmp =
            if (s1.length != s2.length) {
                s1.length.compareTo(s2.length)
            } else {
                s1.compareTo(s2)
            }
        return if (isNegative) -cmp else cmp
    }

    fun toLongOrNull(): Long? {
        val negative = isNegative
        val digits = if (negative) decimal.drop(1) else decimal
        val limit = if (negative) LONG_MIN_ABS_DECIMAL else LONG_MAX_DECIMAL
        if (digits.hasGreaterMagnitudeThan(limit)) {
            return null
        }
        var result = 0L
        for (char in digits) {
            val digit = char.code - '0'.code
            result = result * 10L + digit
        }
        return if (negative) {
            if (digits == LONG_MIN_ABS_DECIMAL) Long.MIN_VALUE else -result
        } else {
            result
        }
    }

    fun toUInt128OrNull(): UInt128? =
        if (isNegative) null else UInt128.parse(decimal)

    override fun toString(): kotlin.String = decimal

    override fun equals(other: Any?): Boolean =
        other is Int128 && decimal == other.decimal

    override fun hashCode(): Int = decimal.hashCode()
}

/**
 * Represents variable typed content.
 *
 * This is used for the serialization system to represent values before the
 * actual snapshots are written, and is also exposed to dynamic redaction
 * functions.
 *
 * Some variants are intentionally not exposed to user code. It is generally
 * recommended to construct content objects with the conversion helpers and to
 * use accessor methods to assert on it.
 *
 * While matching on content is possible in theory, it is recommended against.
 * The reason is that the content enum holds variants that can wrap values where
 * it is not expected. For instance, if a field holds a nullable string, you
 * cannot match the string directly because it may be contained in an internal
 * nullable wrapper that is not exposed. On the other hand, asString()
 * automatically resolves such internal wrappers.
 *
 * If you do need to pattern match, use resolveInner() to resolve such internal
 * wrappers first.
 */
sealed class Content :
    Serialize,
    Comparable<Content> {
    data class Bool(
        val value: Boolean,
    ) : Content()

    data class U8(
        val value: UByte,
    ) : Content()

    data class U16(
        val value: UShort,
    ) : Content()

    data class U32(
        val value: UInt,
    ) : Content()

    data class U64(
        val value: ULong,
    ) : Content()

    data class U128(
        val value: UInt128,
    ) : Content()

    data class I8(
        val value: Byte,
    ) : Content()

    data class I16(
        val value: Short,
    ) : Content()

    data class I32(
        val value: Int,
    ) : Content()

    data class I64(
        val value: Long,
    ) : Content()

    data class I128(
        val value: Int128,
    ) : Content()

    data class F32(
        val value: Float,
    ) : Content()

    data class F64(
        val value: Double,
    ) : Content()

    data class Char(
        val value: kotlin.Char,
    ) : Content()

    data class Str(
        val value: kotlin.String,
    ) : Content()

    data class Bytes(
        val value: List<Byte>,
    ) : Content()

    data object None : Content()

    data class Some(
        val value: Content,
    ) : Content()

    data object UnitValue : Content()

    data class UnitStruct(
        val name: kotlin.String,
    ) : Content()

    data class UnitVariant(
        val name: kotlin.String,
        val index: UInt,
        val variant: kotlin.String,
    ) : Content()

    data class NewtypeStruct(
        val name: kotlin.String,
        val value: Content,
    ) : Content()

    data class NewtypeVariant(
        val name: kotlin.String,
        val index: UInt,
        val variant: kotlin.String,
        val value: Content,
    ) : Content()

    data class Seq(
        val value: List<Content>,
    ) : Content()

    data class Tuple(
        val value: List<Content>,
    ) : Content()

    data class TupleStruct(
        val name: kotlin.String,
        val value: List<Content>,
    ) : Content()

    data class TupleVariant(
        val name: kotlin.String,
        val index: UInt,
        val variant: kotlin.String,
        val value: List<Content>,
    ) : Content()

    data class Entry(
        val key: Content,
        val value: Content,
    )

    data class Field(
        val name: kotlin.String,
        val value: Content,
    )

    data class Map(
        val value: List<Entry>,
    ) : Content()

    data class Struct(
        val name: kotlin.String,
        val fields: List<Field>,
    ) : Content()

    data class StructVariant(
        val name: kotlin.String,
        val index: UInt,
        val variant: kotlin.String,
        val fields: List<Field>,
    ) : Content()

    /** This resolves the innermost content in a chain of wrapped content. */
    fun resolveInner(): Content =
        when (this) {
            is Some -> value.resolveInner()
            is NewtypeStruct -> value.resolveInner()
            is NewtypeVariant -> value.resolveInner()
            else -> this
        }

    /** This resolves the innermost content in a chain of wrapped content. */
    fun resolveInnerMut(): Content = resolveInner()

    /** Returns the value as string. */
    fun asString(): kotlin.String? =
        when (val inner = resolveInner()) {
            is Str -> inner.value
            else -> null
        }

    /** Returns the value as string. */
    fun asStr(): kotlin.String? = asString()

    /** Returns the value as bytes. */
    fun asBytes(): List<Byte>? =
        when (val inner = resolveInner()) {
            is Bytes -> inner.value
            else -> null
        }

    /** Returns the value as a list of content values. */
    fun asSlice(): List<Content>? =
        when (val inner = resolveInner()) {
            is Seq -> inner.value
            is Tuple -> inner.value
            is TupleVariant -> inner.value
            else -> null
        }

    /** Returns true if the value is nil. */
    fun isNil(): Boolean =
        when (resolveInner()) {
            None, UnitValue -> true
            else -> false
        }

    /** Returns the value as bool. */
    fun asBool(): Boolean? =
        when (val inner = resolveInner()) {
            is Bool -> inner.value
            else -> null
        }

    /** Returns the value as unsigned 64-bit integer. */
    fun asULong(): ULong? =
        when (val inner = resolveInner()) {
            is U8 -> {
                inner.value.toULong()
            }

            is U16 -> {
                inner.value.toULong()
            }

            is U32 -> {
                inner.value.toULong()
            }

            is U64 -> {
                inner.value
            }

            is U128 -> {
                inner.value.toULongOrNull()
            }

            is I8 -> {
                inner.value.takeIf { it >= 0 }?.toULong()
            }

            is I16 -> {
                inner.value.takeIf { it >= 0 }?.toULong()
            }

            is I32 -> {
                inner.value.takeIf { it >= 0 }?.toULong()
            }

            is I64 -> {
                inner.value.takeIf { it >= 0 }?.toULong()
            }

            is I128 -> {
                inner.value
                    .toLongOrNull()
                    ?.takeIf { it >= 0 }
                    ?.toULong()
            }

            else -> {
                null
            }
        }

    /** Returns the value as unsigned 64-bit integer. */
    fun asU64(): ULong? = asULong()

    /** Returns the value as unsigned 128-bit integer. */
    fun asUInt128(): UInt128? =
        when (val inner = resolveInner()) {
            is U8 -> UInt128.from(inner.value.toULong())
            is U16 -> UInt128.from(inner.value.toULong())
            is U32 -> UInt128.from(inner.value.toULong())
            is U64 -> UInt128.from(inner.value)
            is U128 -> inner.value
            is I8 -> inner.value.takeIf { it >= 0 }?.let { UInt128.from(it.toULong()) }
            is I16 -> inner.value.takeIf { it >= 0 }?.let { UInt128.from(it.toULong()) }
            is I32 -> inner.value.takeIf { it >= 0 }?.let { UInt128.from(it.toULong()) }
            is I64 -> inner.value.takeIf { it >= 0 }?.let { UInt128.from(it.toULong()) }
            is I128 -> inner.value.toUInt128OrNull()
            else -> null
        }

    /** Returns the value as unsigned 128-bit integer. */
    fun asU128(): UInt128? = asUInt128()

    /** Returns the value as signed 64-bit integer. */
    fun asLong(): Long? =
        when (val inner = resolveInner()) {
            is I8 -> {
                inner.value.toLong()
            }

            is I16 -> {
                inner.value.toLong()
            }

            is I32 -> {
                inner.value.toLong()
            }

            is I64 -> {
                inner.value
            }

            is I128 -> {
                inner.value.toLongOrNull()
            }

            is U8 -> {
                inner.value.toLong()
            }

            is U16 -> {
                inner.value.toLong()
            }

            is U32 -> {
                inner.value.toLong()
            }

            is U64 -> {
                if (inner.value <= Long.MAX_VALUE.toULong()) {
                    inner.value.toLong()
                } else {
                    null
                }
            }

            is U128 -> {
                inner.value
                    .toULongOrNull()
                    ?.takeIf { it <= Long.MAX_VALUE.toULong() }
                    ?.toLong()
            }

            else -> {
                null
            }
        }

    /** Returns the value as signed 64-bit integer. */
    fun asI64(): Long? = asLong()

    /** Returns the value as signed 128-bit integer. */
    fun asInt128(): Int128? =
        when (val inner = resolveInner()) {
            is I8 -> Int128.from(inner.value.toLong())
            is I16 -> Int128.from(inner.value.toLong())
            is I32 -> Int128.from(inner.value.toLong())
            is I64 -> Int128.from(inner.value)
            is I128 -> inner.value
            is U8 -> Int128.from(inner.value.toLong())
            is U16 -> Int128.from(inner.value.toLong())
            is U32 -> Int128.from(inner.value.toLong())
            is U64 -> Int128.parse(inner.value.toString())
            is U128 -> Int128.parse(inner.value.decimal)
            else -> null
        }

    /** Returns the value as signed 128-bit integer. */
    fun asI128(): Int128? = asInt128()

    /** Returns the value as 64-bit float. */
    fun asDouble(): Double? =
        when (val inner = resolveInner()) {
            is F32 -> inner.value.toDouble()
            is F64 -> inner.value
            else -> null
        }

    /** Returns the value as 64-bit float. */
    fun asF64(): Double? = asDouble()

    /** Walks recursively through the content tree. */
    fun walk(visit: ContentVisitor) {
        if (!visit(this)) {
            return
        }

        when (this) {
            is Some -> {
                value.walk(visit)
            }

            is NewtypeStruct -> {
                value.walk(visit)
            }

            is NewtypeVariant -> {
                value.walk(visit)
            }

            is Seq -> {
                value.forEach { it.walk(visit) }
            }

            is Map -> {
                value.forEach { entry ->
                    entry.key.walk(visit)
                    entry.value.walk(visit)
                }
            }

            is Struct -> {
                fields.forEach { field -> field.value.walk(visit) }
            }

            is StructVariant -> {
                fields.forEach { field -> field.value.walk(visit) }
            }

            is Tuple -> {
                value.forEach { it.walk(visit) }
            }

            is TupleStruct -> {
                value.forEach { it.walk(visit) }
            }

            is TupleVariant -> {
                value.forEach { it.walk(visit) }
            }

            is Bool,
            is U8,
            is U16,
            is U32,
            is U64,
            is U128,
            is I8,
            is I16,
            is I32,
            is I64,
            is I128,
            is F32,
            is F64,
            is Char,
            is Str,
            is Bytes,
            None,
            UnitValue,
            is UnitStruct,
            is UnitVariant,
            -> {
                return
            }
        }
    }

    fun discriminant(): Int =
        when (this) {
            is Bool -> 1
            is U8 -> 2
            is U16 -> 3
            is U32 -> 4
            is U64 -> 5
            is U128 -> 6
            is I8 -> 7
            is I16 -> 8
            is I32 -> 9
            is I64 -> 10
            is I128 -> 11
            is F32 -> 12
            is F64 -> 13
            is Char -> 14
            is Str -> 15
            is Bytes -> 16
            None -> 17
            is Some -> 18
            UnitValue -> 19
            is UnitStruct -> 20
            is UnitVariant -> 21
            is NewtypeStruct -> 22
            is NewtypeVariant -> 23
            is Seq -> 24
            is Tuple -> 25
            is TupleStruct -> 26
            is TupleVariant -> 27
            is Map -> 28
            is Struct -> 29
            is StructVariant -> 30
        }

    override fun compareTo(other: Content): Int {
        val d1 = discriminant()
        val d2 = other.discriminant()
        if (d1 != d2) return d1.compareTo(d2)
        return when (this) {
            is Bool -> {
                value.compareTo((other as Bool).value)
            }

            is U8 -> {
                value.compareTo((other as U8).value)
            }

            is U16 -> {
                value.compareTo((other as U16).value)
            }

            is U32 -> {
                value.compareTo((other as U32).value)
            }

            is U64 -> {
                value.compareTo((other as U64).value)
            }

            is U128 -> {
                value.compareTo((other as U128).value)
            }

            is I8 -> {
                value.compareTo((other as I8).value)
            }

            is I16 -> {
                value.compareTo((other as I16).value)
            }

            is I32 -> {
                value.compareTo((other as I32).value)
            }

            is I64 -> {
                value.compareTo((other as I64).value)
            }

            is I128 -> {
                value.compareTo((other as I128).value)
            }

            is F32 -> {
                f64TotalCmp(value.toDouble(), (other as F32).value.toDouble())
            }

            is F64 -> {
                f64TotalCmp(value, (other as F64).value)
            }

            is Char -> {
                value.compareTo((other as Char).value)
            }

            is Str -> {
                value.compareTo((other as Str).value)
            }

            is Bytes -> {
                compareBytes(value, (other as Bytes).value)
            }

            None -> {
                0
            }

            is Some -> {
                value.compareTo((other as Some).value)
            }

            UnitValue -> {
                0
            }

            is UnitStruct -> {
                name.compareTo((other as UnitStruct).name)
            }

            is UnitVariant -> {
                val o = other as UnitVariant
                var c = name.compareTo(o.name)
                if (c != 0) return c
                c = index.compareTo(o.index)
                if (c != 0) return c
                variant.compareTo(o.variant)
            }

            is NewtypeStruct -> {
                val o = other as NewtypeStruct
                val c = name.compareTo(o.name)
                if (c != 0) return c
                value.compareTo(o.value)
            }

            is NewtypeVariant -> {
                val o = other as NewtypeVariant
                var c = name.compareTo(o.name)
                if (c != 0) return c
                c = index.compareTo(o.index)
                if (c != 0) return c
                c = variant.compareTo(o.variant)
                if (c != 0) return c
                value.compareTo(o.value)
            }

            is Seq -> {
                compareList(value, (other as Seq).value)
            }

            is Tuple -> {
                compareList(value, (other as Tuple).value)
            }

            is TupleStruct -> {
                val o = other as TupleStruct
                val c = name.compareTo(o.name)
                if (c != 0) return c
                compareList(value, o.value)
            }

            is TupleVariant -> {
                val o = other as TupleVariant
                var c = name.compareTo(o.name)
                if (c != 0) return c
                c = index.compareTo(o.index)
                if (c != 0) return c
                c = variant.compareTo(o.variant)
                if (c != 0) return c
                compareList(value, o.value)
            }

            is Map -> {
                compareEntries(value, (other as Map).value)
            }

            is Struct -> {
                val o = other as Struct
                val c = name.compareTo(o.name)
                if (c != 0) return c
                compareFields(fields, o.fields)
            }

            is StructVariant -> {
                val o = other as StructVariant
                var c = name.compareTo(o.name)
                if (c != 0) return c
                c = index.compareTo(o.index)
                if (c != 0) return c
                c = variant.compareTo(o.variant)
                if (c != 0) return c
                compareFields(fields, o.fields)
            }
        }
    }

    override fun <Ok> serialize(serializer: Serializer<Ok>): SerdeResult<Ok> =
        when (this) {
            is Bool -> {
                serializer.serializeBool(value)
            }

            is U8 -> {
                serializer.serializeU8(value)
            }

            is U16 -> {
                serializer.serializeU16(value)
            }

            is U32 -> {
                serializer.serializeU32(value)
            }

            is U64 -> {
                serializer.serializeU64(value)
            }

            is U128 -> {
                serializer.serializeU128(value.toString())
            }

            is I8 -> {
                serializer.serializeI8(value)
            }

            is I16 -> {
                serializer.serializeI16(value)
            }

            is I32 -> {
                serializer.serializeI32(value)
            }

            is I64 -> {
                serializer.serializeI64(value)
            }

            is I128 -> {
                serializer.serializeI128(value.toString())
            }

            is F32 -> {
                serializer.serializeF32(value)
            }

            is F64 -> {
                serializer.serializeF64(value)
            }

            is Char -> {
                serializer.serializeChar(value)
            }

            is Str -> {
                serializer.serializeStr(value)
            }

            is Bytes -> {
                serializer.serializeBytes(value.toByteArray())
            }

            None -> {
                serializer.serializeNone()
            }

            is Some -> {
                serializer.serializeSome(value)
            }

            UnitValue -> {
                serializer.serializeUnit()
            }

            is UnitStruct -> {
                serializer.serializeUnitStruct(name)
            }

            is UnitVariant -> {
                serializer.serializeUnitVariant(name, index, variant)
            }

            is NewtypeStruct -> {
                serializer.serializeNewtypeStruct(name, value)
            }

            is NewtypeVariant -> {
                serializer.serializeNewtypeVariant(name, index, variant, value)
            }

            is Seq -> {
                serdeCatching {
                    val seq = serializer.serializeSeq(value.size).getOrThrow()
                    for (element in value) {
                        seq.serializeElement(element).getOrThrow()
                    }
                    seq.end().getOrThrow()
                }
            }

            is Tuple -> {
                serdeCatching {
                    val tuple = serializer.serializeTuple(value.size).getOrThrow()
                    for (element in value) {
                        tuple.serializeElement(element).getOrThrow()
                    }
                    tuple.end().getOrThrow()
                }
            }

            is TupleStruct -> {
                serdeCatching {
                    val ts = serializer.serializeTupleStruct(name, value.size).getOrThrow()
                    for (field in value) {
                        ts.serializeField(field).getOrThrow()
                    }
                    ts.end().getOrThrow()
                }
            }

            is TupleVariant -> {
                serdeCatching {
                    val tv = serializer.serializeTupleVariant(name, index, variant, value.size).getOrThrow()
                    for (field in value) {
                        tv.serializeField(field).getOrThrow()
                    }
                    tv.end().getOrThrow()
                }
            }

            is Map -> {
                serdeCatching {
                    val map = serializer.serializeMap(value.size).getOrThrow()
                    for (entry in value) {
                        map.serializeEntry(entry.key, entry.value).getOrThrow()
                    }
                    map.end().getOrThrow()
                }
            }

            is Struct -> {
                serdeCatching {
                    val s = serializer.serializeStruct(name, fields.size).getOrThrow()
                    for (field in fields) {
                        s.serializeField(field.name, field.value).getOrThrow()
                    }
                    s.end().getOrThrow()
                }
            }

            is StructVariant -> {
                serdeCatching {
                    val sv = serializer.serializeStructVariant(name, index, variant, fields.size).getOrThrow()
                    for (field in fields) {
                        sv.serializeField(field.name, field.value).getOrThrow()
                    }
                    sv.end().getOrThrow()
                }
            }
        }

    companion object {
        fun from(value: Boolean): Content = Bool(value)

        fun from(value: UByte): Content = U8(value)

        fun from(value: UShort): Content = U16(value)

        fun from(value: UInt): Content = U32(value)

        fun from(value: ULong): Content = U64(value)

        fun from(value: UInt128): Content = U128(value)

        fun from(value: Byte): Content = I8(value)

        fun from(value: Short): Content = I16(value)

        fun from(value: Int): Content = I32(value)

        fun from(value: Long): Content = I64(value)

        fun from(value: Int128): Content = I128(value)

        fun from(value: Float): Content = F32(value)

        fun from(value: Double): Content = F64(value)

        fun fromChar(value: kotlin.Char): Content = Char(value)

        fun from(value: kotlin.String): Content = Str(value)

        fun from(value: List<Byte>): Content = Bytes(value)

        fun unit(): Content = UnitValue
    }
}

/** Visitor callback for walking a content tree. */
fun interface ContentVisitor {
    operator fun invoke(content: Content): Boolean
}

private fun kotlin.String.trimLeadingZeroes(): kotlin.String =
    trimStart('0').ifEmpty { "0" }

private fun kotlin.String.hasGreaterMagnitudeThan(limit: kotlin.String): Boolean {
    val normalized = trimLeadingZeroes()
    return normalized.length > limit.length || (normalized.length == limit.length && normalized > limit)
}

private fun compareBytes(a: List<Byte>, b: List<Byte>): Int {
    val minLen = minOf(a.size, b.size)
    for (i in 0 until minLen) {
        val cmp = a[i].toUByte().compareTo(b[i].toUByte())
        if (cmp != 0) return cmp
    }
    return a.size.compareTo(b.size)
}

private fun compareList(a: List<Content>, b: List<Content>): Int {
    val minLen = minOf(a.size, b.size)
    for (i in 0 until minLen) {
        val cmp = a[i].compareTo(b[i])
        if (cmp != 0) return cmp
    }
    return a.size.compareTo(b.size)
}

private fun compareEntries(a: List<Content.Entry>, b: List<Content.Entry>): Int {
    val minLen = minOf(a.size, b.size)
    for (i in 0 until minLen) {
        val kCmp = a[i].key.compareTo(b[i].key)
        if (kCmp != 0) return kCmp
        val vCmp = a[i].value.compareTo(b[i].value)
        if (vCmp != 0) return vCmp
    }
    return a.size.compareTo(b.size)
}

private fun compareFields(a: List<Content.Field>, b: List<Content.Field>): Int {
    val minLen = minOf(a.size, b.size)
    for (i in 0 until minLen) {
        val nCmp = a[i].name.compareTo(b[i].name)
        if (nCmp != 0) return nCmp
        val vCmp = a[i].value.compareTo(b[i].value)
        if (vCmp != 0) return vCmp
    }
    return a.size.compareTo(b.size)
}
