// port-lint: source content/serialization.rs
package io.github.kotlinmania.insta.content

import io.github.kotlinmania.serde.SerdeError
import io.github.kotlinmania.serde.SerdeResult
import io.github.kotlinmania.serde.serdeCatching
import io.github.kotlinmania.serdecore.ser.Serialize
import io.github.kotlinmania.serdecore.ser.Serializer

/**
 * Total ordering comparison for 64-bit floating point numbers.
 *
 * Implements IEEE 754 total ordering identical to Rust's `f64::total_cmp`.
 */
fun f64TotalCmp(left: Double, right: Double): Int {
    var l = left.toBits()
    var r = right.toBits()
    l = l xor ((l shr 63) ushr 1)
    r = r xor ((r shr 63) ushr 1)
    return l.compareTo(r)
}

/**
 * Key representation for content mapping keys.
 */
sealed class Key {
    data class Bool(
        val value: Boolean,
    ) : Key()

    data class U64(
        val value: ULong,
    ) : Key()

    data class I64(
        val value: Long,
    ) : Key()

    data class F64(
        val value: Double,
    ) : Key()

    data class U128(
        val value: UInt128,
    ) : Key()

    data class I128(
        val value: Int128,
    ) : Key()

    data class Str(
        val value: String,
    ) : Key()

    data class Bytes(
        val value: List<Byte>,
    ) : Key()

    data object Other : Key()

    fun discriminant(): Int =
        when (this) {
            is Bool -> 1
            is U64 -> 2
            is I64 -> 3
            is F64 -> 4
            is U128 -> 5
            is I128 -> 6
            is Str -> 7
            is Bytes -> 8
            Other -> 9
        }

    operator fun compareTo(other: Key): Int {
        val selfDisc = discriminant()
        val otherDisc = other.discriminant()
        if (selfDisc != otherDisc) {
            return selfDisc.compareTo(otherDisc)
        }
        return when (this) {
            is Bool -> {
                value.compareTo((other as Bool).value)
            }

            is U64 -> {
                value.compareTo((other as U64).value)
            }

            is I64 -> {
                value.compareTo((other as I64).value)
            }

            is F64 -> {
                f64TotalCmp(value, (other as F64).value)
            }

            is U128 -> {
                value.compareTo((other as U128).value)
            }

            is I128 -> {
                value.compareTo((other as I128).value)
            }

            is Str -> {
                value.compareTo((other as Str).value)
            }

            is Bytes -> {
                val oBytes = (other as Bytes).value
                val minLen = minOf(value.size, oBytes.size)
                for (i in 0 until minLen) {
                    val cmp = value[i].toUByte().compareTo(oBytes[i].toUByte())
                    if (cmp != 0) return cmp
                }
                value.size.compareTo(oBytes.size)
            }

            Other -> {
                0
            }
        }
    }
}

/**
 * Converts [Content] to [Key] for map key comparison.
 */
fun Content.asKey(): Key =
    when (val inner = resolveInner()) {
        is Content.Bool -> Key.Bool(inner.value)
        is Content.Char -> Key.U64(inner.value.code.toULong())
        is Content.U8 -> Key.U64(inner.value.toULong())
        is Content.U16 -> Key.U64(inner.value.toULong())
        is Content.U32 -> Key.U64(inner.value.toULong())
        is Content.U64 -> Key.U64(inner.value)
        is Content.U128 -> Key.U128(inner.value)
        is Content.I8 -> Key.I64(inner.value.toLong())
        is Content.I16 -> Key.I64(inner.value.toLong())
        is Content.I32 -> Key.I64(inner.value.toLong())
        is Content.I64 -> Key.I64(inner.value)
        is Content.I128 -> Key.I128(inner.value)
        is Content.F32 -> Key.F64(inner.value.toDouble())
        is Content.F64 -> Key.F64(inner.value)
        is Content.Str -> Key.Str(inner.value)
        is Content.Bytes -> Key.Bytes(inner.value)
        else -> Key.Other
    }

/**
 * Returns a new [Content] with all nested maps sorted by their keys.
 */
fun Content.sortMaps(): Content =
    when (this) {
        is Content.Map -> {
            val sorted =
                value
                    .map { Content.Entry(it.key.sortMaps(), it.value.sortMaps()) }
                    .sortedWith { a, b ->
                        val aKey = a.key.asKey()
                        val bKey = b.key.asKey()
                        if (aKey is Key.Other || bKey is Key.Other) {
                            a.key.compareTo(b.key)
                        } else {
                            aKey.compareTo(bKey)
                        }
                    }
            Content.Map(sorted)
        }

        is Content.Seq -> {
            Content.Seq(value.map { it.sortMaps() })
        }

        is Content.Some -> {
            Content.Some(value.sortMaps())
        }

        is Content.NewtypeStruct -> {
            Content.NewtypeStruct(name, value.sortMaps())
        }

        is Content.NewtypeVariant -> {
            Content.NewtypeVariant(name, index, variant, value.sortMaps())
        }

        is Content.Struct -> {
            Content.Struct(name, fields.map { Content.Field(it.name, it.value.sortMaps()) })
        }

        is Content.StructVariant -> {
            Content.StructVariant(name, index, variant, fields.map { Content.Field(it.name, it.value.sortMaps()) })
        }

        is Content.Tuple -> {
            Content.Tuple(value.map { it.sortMaps() })
        }

        is Content.TupleStruct -> {
            Content.TupleStruct(name, value.map { it.sortMaps() })
        }

        is Content.TupleVariant -> {
            Content.TupleVariant(name, index, variant, value.map { it.sortMaps() })
        }

        else -> {
            this
        }
    }

/**
 * Serializer that converts serializable values into [Content].
 */
class ContentSerializer : Serializer<Content> {
    companion object {
        fun new(): ContentSerializer = ContentSerializer()
    }

    override fun serializeBool(v: Boolean): SerdeResult<Content> =
        SerdeResult.success(Content.Bool(v))

    override fun serializeI8(v: Byte): SerdeResult<Content> =
        SerdeResult.success(Content.I8(v))

    override fun serializeI16(v: Short): SerdeResult<Content> =
        SerdeResult.success(Content.I16(v))

    override fun serializeI32(v: Int): SerdeResult<Content> =
        SerdeResult.success(Content.I32(v))

    override fun serializeI64(v: Long): SerdeResult<Content> =
        SerdeResult.success(Content.I64(v))

    override fun serializeI128(value: String): SerdeResult<Content> =
        SerdeResult.success(Content.I128(Int128.parse(value)))

    override fun serializeU8(v: UByte): SerdeResult<Content> =
        SerdeResult.success(Content.U8(v))

    override fun serializeU16(v: UShort): SerdeResult<Content> =
        SerdeResult.success(Content.U16(v))

    override fun serializeU32(v: UInt): SerdeResult<Content> =
        SerdeResult.success(Content.U32(v))

    override fun serializeU64(v: ULong): SerdeResult<Content> =
        SerdeResult.success(Content.U64(v))

    override fun serializeU128(value: String): SerdeResult<Content> =
        SerdeResult.success(Content.U128(UInt128.parse(value)))

    override fun serializeF32(v: Float): SerdeResult<Content> =
        SerdeResult.success(Content.F32(v))

    override fun serializeF64(v: Double): SerdeResult<Content> =
        SerdeResult.success(Content.F64(v))

    override fun serializeChar(v: Char): SerdeResult<Content> =
        SerdeResult.success(Content.Char(v))

    override fun serializeStr(v: String): SerdeResult<Content> =
        SerdeResult.success(Content.Str(v))

    override fun serializeBytes(v: ByteArray): SerdeResult<Content> =
        SerdeResult.success(Content.Bytes(v.toList()))

    override fun serializeNone(): SerdeResult<Content> =
        SerdeResult.success(Content.None)

    override fun <T : Serialize> serializeSome(value: T): SerdeResult<Content> =
        value.serialize(this).map { Content.Some(it) }

    override fun serializeUnit(): SerdeResult<Content> =
        SerdeResult.success(Content.UnitValue)

    override fun serializeUnitStruct(name: String): SerdeResult<Content> =
        SerdeResult.success(Content.UnitStruct(name))

    override fun serializeUnitVariant(
        name: String,
        variantIndex: UInt,
        variant: String,
    ): SerdeResult<Content> =
        SerdeResult.success(Content.UnitVariant(name, variantIndex, variant))

    override fun <T : Serialize> serializeNewtypeStruct(
        name: String,
        value: T,
    ): SerdeResult<Content> =
        value.serialize(this).map { Content.NewtypeStruct(name, it) }

    override fun <T : Serialize> serializeNewtypeVariant(
        name: String,
        variantIndex: UInt,
        variant: String,
        value: T,
    ): SerdeResult<Content> =
        value.serialize(this).map { Content.NewtypeVariant(name, variantIndex, variant, it) }

    override fun serializeSeq(len: Int?): SerdeResult<io.github.kotlinmania.serdecore.ser.SerializeSeq<Content>> =
        SerdeResult.success(SerializeSeq(ArrayList(len ?: 0)))

    override fun serializeTuple(len: Int): SerdeResult<io.github.kotlinmania.serdecore.ser.SerializeTuple<Content>> =
        SerdeResult.success(SerializeTuple(ArrayList(len)))

    override fun serializeTupleStruct(
        name: String,
        len: Int,
    ): SerdeResult<io.github.kotlinmania.serdecore.ser.SerializeTupleStruct<Content>> =
        SerdeResult.success(SerializeTupleStruct(name, ArrayList(len)))

    override fun serializeTupleVariant(
        name: String,
        variantIndex: UInt,
        variant: String,
        len: Int,
    ): SerdeResult<io.github.kotlinmania.serdecore.ser.SerializeTupleVariant<Content>> =
        SerdeResult.success(SerializeTupleVariant(name, variantIndex, variant, ArrayList(len)))

    override fun serializeMap(len: Int?): SerdeResult<io.github.kotlinmania.serdecore.ser.SerializeMap<Content>> =
        SerdeResult.success(SerializeMap(ArrayList(len ?: 0)))

    override fun serializeStruct(
        name: String,
        len: Int,
    ): SerdeResult<io.github.kotlinmania.serdecore.ser.SerializeStruct<Content>> =
        SerdeResult.success(SerializeStruct(name, ArrayList(len)))

    override fun serializeStructVariant(
        name: String,
        variantIndex: UInt,
        variant: String,
        len: Int,
    ): SerdeResult<io.github.kotlinmania.serdecore.ser.SerializeStructVariant<Content>> =
        SerdeResult.success(SerializeStructVariant(name, variantIndex, variant, ArrayList(len)))
}

/**
 * Container serializer for sequences into [Content.Seq].
 */
internal class SerializeSeq(
    val elements: MutableList<Content> = mutableListOf(),
) : io.github.kotlinmania.serdecore.ser.SerializeSeq<Content> {
    override fun <T : Serialize> serializeElement(value: T): SerdeResult<Unit> =
        value.serialize(ContentSerializer.new()).map {
            elements += it
        }

    override fun end(): SerdeResult<Content> =
        SerdeResult.success(Content.Seq(elements))
}

/**
 * Container serializer for tuples into [Content.Tuple].
 */
internal class SerializeTuple(
    internal val elements: MutableList<Content> = mutableListOf(),
) : io.github.kotlinmania.serdecore.ser.SerializeTuple<Content> {
    override fun <T : Serialize> serializeElement(value: T): SerdeResult<Unit> =
        value.serialize(ContentSerializer.new()).map {
            elements += it
        }

    override fun end(): SerdeResult<Content> =
        SerdeResult.success(Content.Tuple(elements))
}

/**
 * Container serializer for tuple structs into [Content.TupleStruct].
 */
internal class SerializeTupleStruct(
    val name: String,
    internal val fields: MutableList<Content> = mutableListOf(),
) : io.github.kotlinmania.serdecore.ser.SerializeTupleStruct<Content> {
    override fun <T : Serialize> serializeField(value: T): SerdeResult<Unit> =
        value.serialize(ContentSerializer.new()).map {
            fields += it
        }

    override fun end(): SerdeResult<Content> =
        SerdeResult.success(Content.TupleStruct(name, fields))
}

/**
 * Container serializer for tuple variants into [Content.TupleVariant].
 */
internal class SerializeTupleVariant(
    val name: String,
    val variantIndex: UInt,
    val variant: String,
    internal val fields: MutableList<Content> = mutableListOf(),
) : io.github.kotlinmania.serdecore.ser.SerializeTupleVariant<Content> {
    override fun <T : Serialize> serializeField(value: T): SerdeResult<Unit> =
        value.serialize(ContentSerializer.new()).map {
            fields += it
        }

    override fun end(): SerdeResult<Content> =
        SerdeResult.success(Content.TupleVariant(name, variantIndex, variant, fields))
}

/**
 * Container serializer for maps into [Content.Map].
 */
internal class SerializeMap(
    internal val entries: MutableList<Content.Entry> = mutableListOf(),
    private var key: Content? = null,
) : io.github.kotlinmania.serdecore.ser.SerializeMap<Content> {
    override fun <T : Serialize> serializeKey(key: T): SerdeResult<Unit> =
        key.serialize(ContentSerializer.new()).map {
            this.key = it
        }

    override fun <T : Serialize> serializeValue(value: T): SerdeResult<Unit> {
        val currentKey =
            this.key
                ?: return SerdeResult.failure(SerdeError.custom("serialize_value called before serialize_key"))
        this.key = null
        return value.serialize(ContentSerializer.new()).map {
            entries += Content.Entry(currentKey, it)
        }
    }

    override fun <K : Serialize, V : Serialize> serializeEntry(key: K, value: V): SerdeResult<Unit> =
        serdeCatching {
            val k = key.serialize(ContentSerializer.new()).getOrThrow()
            val v = value.serialize(ContentSerializer.new()).getOrThrow()
            entries += Content.Entry(k, v)
        }

    override fun end(): SerdeResult<Content> =
        SerdeResult.success(Content.Map(entries))
}

/**
 * Container serializer for structs into [Content.Struct].
 */
internal class SerializeStruct(
    val name: String,
    internal val fields: MutableList<Content.Field> = mutableListOf(),
) : io.github.kotlinmania.serdecore.ser.SerializeStruct<Content> {
    override fun <T : Serialize> serializeField(key: String, value: T): SerdeResult<Unit> =
        value.serialize(ContentSerializer.new()).map {
            fields += Content.Field(key, it)
        }

    override fun skipField(key: String): SerdeResult<Unit> =
        SerdeResult.success(Unit)

    override fun end(): SerdeResult<Content> =
        SerdeResult.success(Content.Struct(name, fields))
}

/**
 * Container serializer for struct variants into [Content.StructVariant].
 */
internal class SerializeStructVariant(
    val name: String,
    val variantIndex: UInt,
    val variant: String,
    internal val fields: MutableList<Content.Field> = mutableListOf(),
) : io.github.kotlinmania.serdecore.ser.SerializeStructVariant<Content> {
    override fun <T : Serialize> serializeField(key: String, value: T): SerdeResult<Unit> =
        value.serialize(ContentSerializer.new()).map {
            fields += Content.Field(key, it)
        }

    override fun skipField(key: String): SerdeResult<Unit> =
        SerdeResult.success(Unit)

    override fun end(): SerdeResult<Content> =
        SerdeResult.success(Content.StructVariant(name, variantIndex, variant, fields))
}
