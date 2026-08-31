// port-lint: tests insta/src/filters.rs
package io.github.kotlinmania.insta

import kotlin.test.Test
import kotlin.test.assertEquals

class FiltersTest {
    @Test
    fun testFilters() {
        val filters = Filters.default()
        filters.add("\\bhello\\b", "[NAME]")
        filters.add("(a)", "[$1]")
        assertEquals(
            "hellohello [NAME] [a]bc",
            filters.applyTo("hellohello hello abc"),
        )
    }

    @Test
    fun testStaticStrArrayConversion() {
        val arr = listOf(Pair("a1", "b1"), Pair("a2", "b2"))
        val filters = Filters.fromIter(arr)
        assertEquals("b1 b2", filters.applyTo("a1 a2"))
    }

    @Test
    fun testVecStrConversion() {
        val vec = listOf(Pair("a1", "b1"), Pair("a2", "b2"))
        val filters = Filters.from(vec)
        assertEquals("b1 b2", filters.applyTo("a1 a2"))
    }
}
