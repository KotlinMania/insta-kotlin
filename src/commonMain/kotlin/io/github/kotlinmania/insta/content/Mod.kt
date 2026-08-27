// port-lint: source content/mod.rs
package io.github.kotlinmania.insta.content

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
sealed class Error {
    data class FailedParsingYaml(
        val path: kotlin.String,
    ) : Error()

    data object UnexpectedDataType : Error()

    data object MissingField : Error()

    data class FileIo(
        val message: kotlin.String,
        val path: kotlin.String,
    ) : Error()

    fun fmt(): kotlin.String = toString()

    override fun toString(): kotlin.String =
        when (this) {
            is FailedParsingYaml -> "Failed parsing the YAML from $path"
            UnexpectedDataType -> "The present data type wasn't what was expected"
            MissingField -> "A required field was missing"
            is FileIo -> "File error for $path: $message"
        }
}

/**
 * Unsigned 128-bit integer content represented as a normalized decimal string.
 */
class UInt128 private constructor(
    val decimal: kotlin.String,
) {
    companion object {
        fun from(value: ULong): UInt128 = UInt128(value.toString())

        fun parse(value: kotlin.String): UInt128 {
            require(value.isNotEmpty()) { "UInt128 cannot be empty" }
            require(value.all { it in '0'..'9' }) { "UInt128 must be decimal digits" }
            return UInt128(value.trimLeadingZeroes())
        }
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
) {
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
sealed class Content {
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
        val value: List<UByte>,
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
    fun asBytes(): List<UByte>? =
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
            is U8 -> inner.value.toULong()
            is U16 -> inner.value.toULong()
            is U32 -> inner.value.toULong()
            is U64 -> inner.value
            is U128 -> inner.value.toULongOrNull()
            is I8 -> inner.value.takeIf { it >= 0 }?.toULong()
            is I16 -> inner.value.takeIf { it >= 0 }?.toULong()
            is I32 -> inner.value.takeIf { it >= 0 }?.toULong()
            is I64 -> inner.value.takeIf { it >= 0 }?.toULong()
            is I128 ->
                inner.value
                    .toLongOrNull()
                    ?.takeIf { it >= 0 }
                    ?.toULong()
            else -> null
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
            is I8 -> inner.value.toLong()
            is I16 -> inner.value.toLong()
            is I32 -> inner.value.toLong()
            is I64 -> inner.value
            is I128 -> inner.value.toLongOrNull()
            is U8 -> inner.value.toLong()
            is U16 -> inner.value.toLong()
            is U32 -> inner.value.toLong()
            is U64 ->
                if (inner.value <= Long.MAX_VALUE.toULong()) {
                    inner.value.toLong()
                } else {
                    null
                }
            is U128 ->
                inner.value
                    .toULongOrNull()
                    ?.takeIf { it <= Long.MAX_VALUE.toULong() }
                    ?.toLong()
            else -> null
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
    fun walk(visit: (Content) -> Boolean) {
        if (!visit(this)) {
            return
        }

        when (this) {
            is Some -> value.walk(visit)
            is NewtypeStruct -> value.walk(visit)
            is NewtypeVariant -> value.walk(visit)
            is Seq -> value.forEach { it.walk(visit) }
            is Map ->
                value.forEach { entry ->
                    entry.key.walk(visit)
                    entry.value.walk(visit)
                }
            is Struct -> fields.forEach { field -> field.value.walk(visit) }
            is StructVariant -> fields.forEach { field -> field.value.walk(visit) }
            is Tuple -> value.forEach { it.walk(visit) }
            is TupleStruct -> value.forEach { it.walk(visit) }
            is TupleVariant -> value.forEach { it.walk(visit) }
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
            -> return
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

        fun from(value: ByteArray): Content = Bytes(value.map { it.toUByte() })

        fun from(value: UByteArray): Content = Bytes(value.toList())

        fun unit(): Content = UnitValue
    }
}

private fun kotlin.String.trimLeadingZeroes(): kotlin.String =
    trimStart('0').ifEmpty { "0" }

private fun kotlin.String.hasGreaterMagnitudeThan(limit: kotlin.String): Boolean {
    val normalized = trimLeadingZeroes()
    return normalized.length > limit.length || (normalized.length == limit.length && normalized > limit)
}
