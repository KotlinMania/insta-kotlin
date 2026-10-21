// port-lint: tests content/yaml/vendored/yaml.rs
package io.github.kotlinmania.insta.content.yaml.vendored

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YamlTest {
    @Test
    fun testCoerce() {
        val s = "---\na: 1\nb: 2.2\nc: [1, 2]\n"
        val out = YamlLoader.loadFromStr(s)
        assertNotNull(out)
        val doc = out[0]
        assertEquals(1L, doc["a"].asI64())
        assertEquals(2.2, doc["b"].asF64())
        assertEquals(2L, doc["c"][1].asI64())
        assertTrue(doc["d"][0].isBadValue())
    }

    @Test
    fun testEmptyDoc() {
        val s1 = ""
        val out1 = YamlLoader.loadFromStr(s1)
        assertNotNull(out1)
        assertTrue(out1.isEmpty())

        val s2 = "---"
        val out2 = YamlLoader.loadFromStr(s2)
        assertNotNull(out2)
        assertEquals(Yaml.Null, out2[0])
    }

    @Test
    fun testParser() {
        val s = """
# comment
a0 bb: val
a1:
    b1: 4
    b2: d
a2: 4 # i'm comment
a3: [1, 2, 3]
a4:
    - - a1
      - a2
    - 2
a5: 'single_quoted'
a6: "double_quoted"
a7: 你好
"""
        val out = YamlLoader.loadFromStr(s)
        assertNotNull(out)
        val doc = out[0]
        assertEquals("你好", doc["a7"].asStr())
    }

    @Test
    fun testMultiDoc() {
        val s = "\n'a scalar'\n---\n'a scalar'\n---\n'a scalar'\n"
        val out = YamlLoader.loadFromStr(s)
        assertNotNull(out)
        assertEquals(3, out.size)
    }

    @Test
    fun testBadAnchor() {
        val s = "\na1: &DEFAULT\n    b1: 4\n    b2: *DEFAULT\n"
        val out = YamlLoader.loadFromStr(s)
        assertNotNull(out)
        val doc = out[0]
        assertEquals(Yaml.BadValue, doc["a1"]["b2"])
    }

    @Test
    fun testGithub27() {
        val s = "&a"
        val out = YamlLoader.loadFromStr(s)
        assertNotNull(out)
        val doc = out[0]
        assertEquals("", doc.asStr())
    }

    @Test
    fun testPlainDatatype() {
        val s = """
- 'string'
- "string"
- string
- 123
- -321
- 1.23
- -1e4
- ~
- null
- true
- false
- !!str 0
- !!int 100
- !!float 2
- !!null ~
- !!bool true
- !!bool false
- 0xFF
# bad values
- !!int string
- !!float string
- !!bool null
- !!null val
- 0o77
- [ 0xF, 0xF ]
- +12345
- [ true, false ]
"""
        val out = YamlLoader.loadFromStr(s)
        assertNotNull(out)
        val doc = out[0]

        assertEquals("string", doc[0].asStr())
        assertEquals("string", doc[1].asStr())
        assertEquals("string", doc[2].asStr())
        assertEquals(123L, doc[3].asI64())
        assertEquals(-321L, doc[4].asI64())
        assertEquals(1.23, doc[5].asF64())
        assertEquals(-1e4, doc[6].asF64())
        assertTrue(doc[7].isNull())
        assertTrue(doc[8].isNull())
        assertTrue(doc[9].asBool() == true)
        assertFalse(doc[10].asBool() == true)
        assertEquals("0", doc[11].asStr())
        assertEquals(100L, doc[12].asI64())
        assertEquals(2.0, doc[13].asF64())
        assertTrue(doc[14].isNull())
        assertTrue(doc[15].asBool() == true)
        assertFalse(doc[16].asBool() == true)
        assertEquals(255L, doc[17].asI64())
        assertTrue(doc[18].isBadValue())
        assertTrue(doc[19].isBadValue())
        assertTrue(doc[20].isBadValue())
        assertTrue(doc[21].isBadValue())
        assertEquals(63L, doc[22].asI64())
        assertEquals(15L, doc[23][0].asI64())
        assertEquals(15L, doc[23][1].asI64())
        assertEquals(12345L, doc[24].asI64())
        assertTrue(doc[25][0].asBool() == true)
        assertFalse(doc[25][1].asBool() == true)
    }

    @Test
    fun testBadHyphen() {
        val s = "{-"
        assertNull(YamlLoader.loadFromStr(s))
    }

    @Test
    fun testIssue65() {
        val b = "\n\"ll\\\"ll\\\r\n\"ll\\\"ll\\\r\r\r\rU\r\r\rU"
        assertNull(YamlLoader.loadFromStr(b))
    }

    @Test
    fun testBadDocstart() {
        assertNotNull(YamlLoader.loadFromStr("---This used to cause an infinite loop"))
        assertEquals(
            listOf(Yaml.StringValue("----")),
            YamlLoader.loadFromStr("----"),
        )
        assertEquals(
            listOf(Yaml.Null),
            YamlLoader.loadFromStr("--- #here goes a comment"),
        )
        assertEquals(
            listOf(Yaml.StringValue("----")),
            YamlLoader.loadFromStr("---- #here goes a comment"),
        )
    }

    @Test
    fun testPlainDatatypeWithIntoMethods() {
        val s = """
- 'string'
- "string"
- string
- 123
- -321
- 1.23
- -1e4
- true
- false
- !!str 0
- !!int 100
- !!float 2
- !!bool true
- !!bool false
- 0xFF
- 0o77
- +12345
- -.INF
- .NAN
- !!float .INF
"""
        val out = YamlLoader.loadFromStr(s)
        assertNotNull(out)
        val doc = out[0].asVec()
        assertNotNull(doc)
        val iter = doc.iterator()

        assertEquals("string", iter.next().intoString())
        assertEquals("string", iter.next().intoString())
        assertEquals("string", iter.next().intoString())
        assertEquals(123L, iter.next().intoI64())
        assertEquals(-321L, iter.next().intoI64())
        assertEquals(1.23, iter.next().intoF64())
        assertEquals(-1e4, iter.next().intoF64())
        assertEquals(true, iter.next().intoBool())
        assertEquals(false, iter.next().intoBool())
        assertEquals("0", iter.next().intoString())
        assertEquals(100L, iter.next().intoI64())
        assertEquals(2.0, iter.next().intoF64())
        assertEquals(true, iter.next().intoBool())
        assertEquals(false, iter.next().intoBool())
        assertEquals(255L, iter.next().intoI64())
        assertEquals(63L, iter.next().intoI64())
        assertEquals(12345L, iter.next().intoI64())
        assertEquals(Double.NEGATIVE_INFINITY, iter.next().intoF64())
        assertNotNull(iter.next().intoF64())
        assertEquals(Double.POSITIVE_INFINITY, iter.next().intoF64())
    }

    @Test
    fun testHashOrder() {
        val s = "---\nb: ~\na: ~\nc: ~\n"
        val out = YamlLoader.loadFromStr(s)
        assertNotNull(out)
        val first = out[0]
        val hash = first.asHash()
        assertNotNull(hash)
        val iter = hash.iterator()
        assertEquals(YamlHashEntry(Yaml.StringValue("b"), Yaml.Null), iter.next())
        assertEquals(YamlHashEntry(Yaml.StringValue("a"), Yaml.Null), iter.next())
        assertEquals(YamlHashEntry(Yaml.StringValue("c"), Yaml.Null), iter.next())
        assertFalse(iter.hasNext())
    }

    @Test
    fun testIntegerKey() {
        val s = "\n0:\n    important: true\n1:\n    important: false\n"
        val out = YamlLoader.loadFromStr(s)
        assertNotNull(out)
        val first = out[0]
        assertEquals(true, first[0]["important"].asBool())
    }

    @Test
    fun testIndentationEquality() {
        val fourSpaces = YamlLoader.loadFromStr("\nhash:\n    with:\n        indentations\n")!![0]
        val twoSpaces = YamlLoader.loadFromStr("\nhash:\n  with:\n    indentations\n")!![0]
        val oneSpace = YamlLoader.loadFromStr("\nhash:\n with:\n  indentations\n")!![0]
        val mixedSpaces = YamlLoader.loadFromStr("\nhash:\n     with:\n               indentations\n")!![0]

        assertEquals(fourSpaces, twoSpaces)
        assertEquals(twoSpaces, oneSpace)
        assertEquals(fourSpaces, mixedSpaces)
    }

    @Test
    fun testTwoSpaceIndentations() {
        val s = """
subcommands:
  - server:
    about: server related commands
subcommands2:
  - server:
      about: server related commands
subcommands3:
 - server:
    about: server related commands
"""
        val out = YamlLoader.loadFromStr(s)
        assertNotNull(out)
        val doc = out[0]
        assertEquals(Yaml.Null, doc["subcommands"][0]["server"])
        assertNotNull(doc["subcommands2"][0]["server"].asHash())
        assertNotNull(doc["subcommands3"][0]["server"].asHash())
    }

    @Test
    fun testRecursionDepthCheckObjects() {
        val s = "{a:".repeat(10_000) + "}".repeat(10_000)
        assertNull(YamlLoader.loadFromStr(s))
    }

    @Test
    fun testRecursionDepthCheckArrays() {
        val s = "[".repeat(10_000) + "]".repeat(10_000)
        assertNull(YamlLoader.loadFromStr(s))
    }
}
