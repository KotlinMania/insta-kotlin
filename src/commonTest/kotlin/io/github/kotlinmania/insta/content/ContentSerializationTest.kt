// port-lint: tests content/serialization.rs
package io.github.kotlinmania.insta.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentSerializationTest {
    @Test
    fun testF64TotalCmp() {
        assertTrue(f64TotalCmp(-100.0, -1.0) < 0)
        assertTrue(f64TotalCmp(-1.0, -0.0) < 0)
        assertTrue(f64TotalCmp(-0.0, 0.0) < 0)
        assertTrue(f64TotalCmp(0.0, 1.0) < 0)
        assertTrue(f64TotalCmp(1.0, 100.0) < 0)
        assertEquals(0, f64TotalCmp(42.0, 42.0))
    }

    @Test
    fun testKeyOrdering() {
        val kBool = Key.Bool(true)
        val kU64 = Key.U64(10UL)
        val kI64 = Key.I64(10L)
        val kF64 = Key.F64(10.0)
        val kU128 = Key.U128(UInt128.parse("10"))
        val kI128 = Key.I128(Int128.parse("10"))
        val kStr = Key.Str("10")
        val kBytes = Key.Bytes(listOf(10.toByte()))
        val kOther = Key.Other

        val keys = listOf(kOther, kBytes, kStr, kI128, kU128, kF64, kI64, kU64, kBool)
        val sorted = keys.sortedWith { a, b -> a.compareTo(b) }
        assertEquals(
            listOf(kBool, kU64, kI64, kF64, kU128, kI128, kStr, kBytes, kOther),
            sorted,
        )
    }

    @Test
    fun testContentSerializerPrimitives() {
        val s = ContentSerializer.new()
        assertEquals(Content.Bool(true), s.serializeBool(true).getOrThrow())
        assertEquals(Content.I8(5.toByte()), s.serializeI8(5.toByte()).getOrThrow())
        assertEquals(Content.I16(5.toShort()), s.serializeI16(5.toShort()).getOrThrow())
        assertEquals(Content.I32(5), s.serializeI32(5).getOrThrow())
        assertEquals(Content.I64(5L), s.serializeI64(5L).getOrThrow())
        assertEquals(Content.U8(5.toUByte()), s.serializeU8(5.toUByte()).getOrThrow())
        assertEquals(Content.U16(5.toUShort()), s.serializeU16(5.toUShort()).getOrThrow())
        assertEquals(Content.U32(5.toUInt()), s.serializeU32(5.toUInt()).getOrThrow())
        assertEquals(Content.U64(5.toULong()), s.serializeU64(5.toULong()).getOrThrow())
        assertEquals(Content.F32(3.14f), s.serializeF32(3.14f).getOrThrow())
        assertEquals(Content.F64(3.14), s.serializeF64(3.14).getOrThrow())
        assertEquals(Content.Char('a'), s.serializeChar('a').getOrThrow())
        assertEquals(Content.Str("hello"), s.serializeStr("hello").getOrThrow())
        assertEquals(Content.Bytes(listOf(1, 2, 3)), s.serializeBytes(byteArrayOf(1, 2, 3)).getOrThrow())
        assertEquals(Content.None, s.serializeNone().getOrThrow())
        assertEquals(Content.UnitValue, s.serializeUnit().getOrThrow())
    }

    @Test
    fun testContentSerializerContainers() {
        val s = ContentSerializer.new()

        val seq = s.serializeSeq(2).getOrThrow()
        seq.serializeElement(Content.I32(1)).getOrThrow()
        seq.serializeElement(Content.I32(2)).getOrThrow()
        assertEquals(Content.Seq(listOf(Content.I32(1), Content.I32(2))), seq.end().getOrThrow())

        val tuple = s.serializeTuple(2).getOrThrow()
        tuple.serializeElement(Content.Str("a")).getOrThrow()
        tuple.serializeElement(Content.Str("b")).getOrThrow()
        assertEquals(Content.Tuple(listOf(Content.Str("a"), Content.Str("b"))), tuple.end().getOrThrow())

        val ts = s.serializeTupleStruct("Point", 2).getOrThrow()
        ts.serializeField(Content.I32(10)).getOrThrow()
        ts.serializeField(Content.I32(20)).getOrThrow()
        assertEquals(Content.TupleStruct("Point", listOf(Content.I32(10), Content.I32(20))), ts.end().getOrThrow())

        val tv = s.serializeTupleVariant("Shape", 1u, "Circle", 1).getOrThrow()
        tv.serializeField(Content.F64(5.0)).getOrThrow()
        assertEquals(Content.TupleVariant("Shape", 1u, "Circle", listOf(Content.F64(5.0))), tv.end().getOrThrow())

        val map = s.serializeMap(1).getOrThrow()
        map.serializeEntry(Content.Str("key"), Content.I32(100)).getOrThrow()
        assertEquals(
            Content.Map(listOf(Content.Entry(Content.Str("key"), Content.I32(100)))),
            map.end().getOrThrow(),
        )

        val st = s.serializeStruct("Person", 2).getOrThrow()
        st.serializeField("name", Content.Str("Bob")).getOrThrow()
        st.serializeField("age", Content.I32(30)).getOrThrow()
        assertEquals(
            Content.Struct(
                "Person",
                listOf(
                    Content.Field("name", Content.Str("Bob")),
                    Content.Field("age", Content.I32(30)),
                ),
            ),
            st.end().getOrThrow(),
        )

        val sv = s.serializeStructVariant("Msg", 0u, "Text", 1).getOrThrow()
        sv.serializeField("content", Content.Str("hi")).getOrThrow()
        assertEquals(
            Content.StructVariant("Msg", 0u, "Text", listOf(Content.Field("content", Content.Str("hi")))),
            sv.end().getOrThrow(),
        )
    }

    @Test
    fun testContentSortMaps() {
        val map =
            Content.Map(
                listOf(
                    Content.Entry(Content.Str("z"), Content.I32(1)),
                    Content.Entry(Content.Str("a"), Content.I32(2)),
                    Content.Entry(Content.Str("m"), Content.I32(3)),
                ),
            )
        val sorted = map.sortMaps()
        val expected =
            Content.Map(
                listOf(
                    Content.Entry(Content.Str("a"), Content.I32(2)),
                    Content.Entry(Content.Str("m"), Content.I32(3)),
                    Content.Entry(Content.Str("z"), Content.I32(1)),
                ),
            )
        assertEquals(expected, sorted)
    }
}
