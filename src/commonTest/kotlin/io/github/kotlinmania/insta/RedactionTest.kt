// port-lint: tests redaction.rs
package io.github.kotlinmania.insta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactionTest {
    @Test
    fun testRangeChecks() {
        assertEquals(true, PathItem.Index(0u, 10u).rangeCheck(null, -1L))
        assertEquals(false, PathItem.Index(9u, 10u).rangeCheck(null, -1L))
        assertEquals(false, PathItem.Index(0u, 10u).rangeCheck(1L, -1L))
        assertEquals(true, PathItem.Index(1u, 10u).rangeCheck(1L, -1L))
        assertEquals(false, PathItem.Index(9u, 10u).rangeCheck(1L, -1L))
        assertEquals(false, PathItem.Index(0u, 10u).rangeCheck(1L, null))
        assertEquals(true, PathItem.Index(1u, 10u).rangeCheck(1L, null))
        assertEquals(true, PathItem.Index(9u, 10u).rangeCheck(1L, null))
    }

    @Test
    fun testSelectorMatching() {
        val sel = Selector.parse(".id, .[0]")
        assertTrue(sel.isMatch(listOf(PathItem.Field("id"))))
        assertTrue(sel.isMatch(listOf(PathItem.Index(0u, 5u))))
        assertFalse(sel.isMatch(listOf(PathItem.Index(1u, 5u))))
    }
}
