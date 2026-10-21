// port-lint: tests test_glob.rs
package io.github.kotlinmania.insta

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GlobTest {
    @Test
    fun testBasicGlobbing() {
        writeTextFile("inputs/a.txt", "hello a")
        writeTextFile("inputs/b.txt", "hello b")

        val visited = mutableListOf<kotlin.String>()
        globExec(".", "inputs", "*.txt") { path ->
            val contents = readTextFile(path).orEmpty()
            visited.add(contents)
        }
        assertEquals(listOf("hello a", "hello b"), visited)
    }

    @Test
    fun testBasicGlobbingNested() {
        writeTextFile("inputs-nested/dir1/a.txt", "nested a")
        writeTextFile("inputs-nested/dir2/b.txt", "nested b")

        val visited = mutableListOf<kotlin.String>()
        globExec(".", "inputs-nested", "*/*.txt") { path ->
            val contents = readTextFile(path).orEmpty()
            visited.add(contents)
        }
        assertEquals(listOf("nested a", "nested b"), visited)
    }

    @Test
    fun testGlobsFollowLinks() {
        writeTextFile("link-to-inputs/c.txt", "link c")

        val visited = mutableListOf<kotlin.String>()
        globExec(".", "link-to-inputs", "*.txt") { path ->
            val contents = readTextFile(path).orEmpty()
            visited.add(contents)
        }
        assertEquals(listOf("link c"), visited)
    }

    @Test
    fun testEmptyGlobFails() {
        val ex =
            assertFailsWith<IllegalStateException> {
                globExec(".", "nonexistent", "*.txt") { _ -> }
            }
        assertTrue(ex.message?.contains("the glob! macro did not match any files.") == true)
    }

    @Test
    fun testParentDirGlobFailsWithHelpfulMessage() {
        val ex =
            assertFailsWith<IllegalStateException> {
                globExec(".", ".", "../**/*.rs") { _ -> }
            }
        assertTrue(
            ex.message?.contains("Parent directory traversal is not supported in glob patterns") == true,
        )
    }

    @Test
    fun testFindCommonPrefix() {
        val paths = listOf("inputs/nested/a.txt", "inputs/nested/b.txt")
        assertEquals("inputs/nested", findCommonPrefix(paths))

        val single = listOf("inputs/a.txt")
        assertEquals("inputs/a.txt", findCommonPrefix(single))

        val empty = emptyList<kotlin.String>()
        assertEquals(null, findCommonPrefix(empty))
    }
}
