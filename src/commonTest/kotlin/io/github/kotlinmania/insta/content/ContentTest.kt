// port-lint: tests content/mod.rs
package io.github.kotlinmania.insta.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentTest {
    @Test
    fun testPrimsAndConversions() {
        val b = Content.from(true)
        assertEquals(true, b.asBool())
        assertNull(b.asString())
        assertFalse(b.isNil())

        val u8Val = Content.from(42.toUByte())
        assertEquals(42UL, u8Val.asULong())
        assertEquals(42L, u8Val.asLong())
        assertEquals(UInt128.from(42UL), u8Val.asUInt128())
        assertEquals(Int128.from(42L), u8Val.asInt128())

        val i8Val = Content.from((-10).toByte())
        assertNull(i8Val.asULong())
        assertEquals(-10L, i8Val.asLong())
        assertEquals(Int128.from(-10L), i8Val.asInt128())

        val fVal = Content.from(3.14)
        assertEquals(3.14, fVal.asDouble())

        val strVal = Content.from("hello")
        assertEquals("hello", strVal.asString())

        val bytesVal = Content.from(byteArrayOf(1, 2, 3))
        assertEquals(listOf(1.toUByte(), 2.toUByte(), 3.toUByte()), bytesVal.asBytes())

        val unitVal = Content.unit()
        assertTrue(unitVal.isNil())
        assertTrue(Content.None.isNil())
    }

    @Test
    fun testResolveInner() {
        val inner = Content.from("nested")
        val wrapped = Content.Some(Content.NewtypeStruct("MyStruct", inner))
        assertEquals("nested", wrapped.asString())
        assertEquals(inner, wrapped.resolveInner())
    }

    @Test
    fun testUInt128AndInt128() {
        val u128 = UInt128.parse("18446744073709551615")
        assertEquals(ULong.MAX_VALUE, u128.toULongOrNull())
        assertEquals("18446744073709551615", u128.toString())

        val i128 = Int128.parse("-9223372036854775808")
        assertEquals(Long.MIN_VALUE, i128.toLongOrNull())
        assertTrue(i128.isNegative)
        assertNull(i128.toUInt128OrNull())

        val positiveI128 = Int128.parse("100")
        assertFalse(positiveI128.isNegative)
        assertEquals(100L, positiveI128.toLongOrNull())
        assertEquals(UInt128.parse("100"), positiveI128.toUInt128OrNull())
    }

    @Test
    fun testWalk() {
        val content =
            Content.Seq(
                listOf(
                    Content.from("a"),
                    Content.Some(Content.from("b")),
                    Content.Struct("User", listOf("name" to Content.from("c"))),
                ),
            )

        val collected = mutableListOf<String>()
        content.walk { node ->
            if (node is Content.String) {
                collected.add(node.value)
            }
            true
        }

        assertEquals(listOf("a", "b", "c"), collected)
    }

    @Test
    fun testErrors() {
        val err1 = Error.FailedParsingYaml("foo.yaml")
        assertTrue(err1.toString().contains("foo.yaml"))

        val err2 = Error.UnexpectedDataType
        assertTrue(err2.toString().isNotEmpty())

        val err3 = Error.MissingField
        assertTrue(err3.toString().isNotEmpty())

        val err4 = Error.FileIo("Not found", "bar.txt")
        assertTrue(err4.toString().contains("bar.txt"))
    }
}
