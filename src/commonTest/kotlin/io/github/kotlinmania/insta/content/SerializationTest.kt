// port-lint: tests content/serialization.rs
package io.github.kotlinmania.insta.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializationTest {
    @Test
    fun testKeyOrdering() {
        val b1 = Key.Bool(false)
        val b2 = Key.Bool(true)
        assertTrue(b1 < b2)

        val u1 = Key.U64(10uL)
        val u2 = Key.U64(20uL)
        assertTrue(u1 < u2)
        assertTrue(b1 < u1)

        val s1 = Key.Str("apple")
        val s2 = Key.Str("banana")
        assertTrue(s1 < s2)
        assertTrue(u1 < s1)

        val bytes1 = Key.Bytes(listOf(1.toByte(), 2.toByte()))
        val bytes2 = Key.Bytes(listOf(1.toByte(), 3.toByte()))
        assertTrue(bytes1 < bytes2)
        assertTrue(s1 < bytes1)
        assertTrue(bytes1 < Key.Other)
        assertEquals(0, Key.Other.compareTo(Key.Other))
    }
}
