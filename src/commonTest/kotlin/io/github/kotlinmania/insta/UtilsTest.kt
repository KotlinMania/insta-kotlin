// port-lint: tests insta/src/utils.rs
package io.github.kotlinmania.insta

import kotlin.test.Test
import kotlin.test.assertEquals

class UtilsTest {
    @Test
    fun testFormatRustExpression() {
        assertEquals("vec![1,2,3]", formatRustExpression("vec![1,2,3]"))
        assertEquals("\"aoeu\"", formatRustExpression("    \"aoeu\""))
        assertEquals("\"aoe😄\"", formatRustExpression("  \"aoe😄\""))
        assertEquals("😄😄😄😄😄", formatRustExpression("😄😄😄😄😄"))
    }

    @Test
    fun testPathToStorage() {
        assertEquals("foo/bar/baz", pathToStorage("foo\\bar\\baz"))
    }
}
