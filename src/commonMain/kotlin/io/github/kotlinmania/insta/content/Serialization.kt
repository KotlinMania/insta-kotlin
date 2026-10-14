// port-lint: source content/serialization.rs
package io.github.kotlinmania.insta.content

/**
 * Key representation for content mapping keys.
 */
sealed class Key : Comparable<Key> {
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
        val value: ByteArray,
    ) : Key() {
        override fun equals(other: Any?): Boolean =
            other is Bytes && value.contentEquals(other.value)

        override fun hashCode(): Int = value.contentHashCode()
    }

    data object Other : Key()

    private fun discriminant(): Int =
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

    override fun compareTo(other: Key): Int {
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
                value.compareTo((other as F64).value)
            }

            is U128 -> {
                (value.toULongOrNull() ?: 0UL).compareTo((other as U128).value.toULongOrNull() ?: 0UL)
            }

            is I128 -> {
                (value.toLongOrNull() ?: 0L).compareTo((other as I128).value.toLongOrNull() ?: 0L)
            }

            is Str -> {
                value.compareTo((other as Str).value)
            }

            is Bytes -> {
                val oBytes = (other as Bytes).value
                val minLen = minOf(value.size, oBytes.size)
                for (i in 0 until minLen) {
                    val cmp = value[i].compareTo(oBytes[i])
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
        is Content.Bytes -> Key.Bytes(inner.value.map { it.toByte() }.toByteArray())
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
                            a.key.toString().compareTo(b.key.toString())
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
