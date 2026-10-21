// port-lint: tests content/yaml/vendored/scanner.rs
package io.github.kotlinmania.insta.content.yaml.vendored

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScannerTest {
    private fun next(p: Scanner): TokenType {
        assertTrue(p.hasNext(), "expected token but scanner had no next token")
        return p.next().tokenType
    }

    private fun nextScalar(
        p: Scanner,
        style: TScalarStyle,
        value: String,
    ) {
        val tok = next(p)
        assertTrue(tok is TokenType.Scalar, "expected TokenType.Scalar, got $tok")
        assertEquals(style, tok.style)
        assertEquals(value, tok.value)
    }

    private fun end(p: Scanner) {
        assertFalse(p.hasNext(), "expected end of tokens")
    }

    @Test
    fun testEmpty() {
        val s = ""
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testScalar() {
        val s = "a scalar"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testExplicitScalar() {
        val s = "---\n'a scalar'\n...\n"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.DocumentStart)
        val sc = next(p)
        assertTrue(sc is TokenType.Scalar && sc.style == TScalarStyle.SingleQuoted)
        assertTrue(next(p) is TokenType.DocumentEnd)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testMultipleDocuments() {
        val s = "\n'a scalar'\n---\n'a scalar'\n---\n'a scalar'\n"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        val sc1 = next(p)
        assertTrue(sc1 is TokenType.Scalar && sc1.style == TScalarStyle.SingleQuoted)
        assertTrue(next(p) is TokenType.DocumentStart)
        val sc2 = next(p)
        assertTrue(sc2 is TokenType.Scalar && sc2.style == TScalarStyle.SingleQuoted)
        assertTrue(next(p) is TokenType.DocumentStart)
        val sc3 = next(p)
        assertTrue(sc3 is TokenType.Scalar && sc3.style == TScalarStyle.SingleQuoted)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testAFlowSequence() {
        val s = "[item 1, item 2, item 3]"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.FlowSequenceStart)
        nextScalar(p, TScalarStyle.Plain, "item 1")
        assertTrue(next(p) is TokenType.FlowEntry)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.FlowEntry)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.FlowSequenceEnd)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testAFlowMapping() {
        val s = "\n{\n    a simple key: a value, # Note that the KEY token is produced.\n    ? a complex key: another value,\n}\n"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.FlowMappingStart)
        assertTrue(next(p) is TokenType.Key)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.FlowEntry)
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "a complex key")
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.FlowEntry)
        assertTrue(next(p) is TokenType.FlowMappingEnd)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testBlockSequences() {
        val s = "\n- item 1\n- item 2\n-\n  - item 3.1\n  - item 3.2\n-\n  key 1: value 1\n  key 2: value 2\n"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.BlockSequenceStart)
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "item 1")
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "item 2")
        assertTrue(next(p) is TokenType.BlockEntry)
        assertTrue(next(p) is TokenType.BlockSequenceStart)
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "item 3.1")
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "item 3.2")
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.BlockEntry)
        assertTrue(next(p) is TokenType.BlockMappingStart)
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "key 1")
        assertTrue(next(p) is TokenType.Value)
        nextScalar(p, TScalarStyle.Plain, "value 1")
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "key 2")
        assertTrue(next(p) is TokenType.Value)
        nextScalar(p, TScalarStyle.Plain, "value 2")
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testBlockMappings() {
        val s = "\na simple key: a value   # The KEY token is produced here.\n? a complex key\n: another value\na mapping:\n  key 1: value 1\n  key 2: value 2\na sequence:\n  - item 1\n  - item 2\n"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.BlockMappingStart)
        assertTrue(next(p) is TokenType.Key)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.Key)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.Key)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.BlockMappingStart)
        assertTrue(next(p) is TokenType.Key)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.Key)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.Key)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.BlockSequenceStart)
        assertTrue(next(p) is TokenType.BlockEntry)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.BlockEntry)
        assertTrue(next(p) is TokenType.Scalar)
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testNoBlockSequenceStart() {
        val s = "\nkey:\n- item 1\n- item 2\n"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.BlockMappingStart)
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "key")
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "item 1")
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "item 2")
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testCollectionsInSequence() {
        val s = "\n- - item 1\n  - item 2\n- key 1: value 1\n  key 2: value 2\n- ? complex key\n  : complex value\n"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.BlockSequenceStart)
        assertTrue(next(p) is TokenType.BlockEntry)
        assertTrue(next(p) is TokenType.BlockSequenceStart)
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "item 1")
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "item 2")
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.BlockEntry)
        assertTrue(next(p) is TokenType.BlockMappingStart)
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "key 1")
        assertTrue(next(p) is TokenType.Value)
        nextScalar(p, TScalarStyle.Plain, "value 1")
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "key 2")
        assertTrue(next(p) is TokenType.Value)
        nextScalar(p, TScalarStyle.Plain, "value 2")
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.BlockEntry)
        assertTrue(next(p) is TokenType.BlockMappingStart)
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "complex key")
        assertTrue(next(p) is TokenType.Value)
        nextScalar(p, TScalarStyle.Plain, "complex value")
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testCollectionsInMapping() {
        val s = "\n? a sequence\n: - item 1\n  - item 2\n? a mapping\n: key 1: value 1\n  key 2: value 2\n"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.BlockMappingStart)
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "a sequence")
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.BlockSequenceStart)
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "item 1")
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "item 2")
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "a mapping")
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.BlockMappingStart)
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "key 1")
        assertTrue(next(p) is TokenType.Value)
        nextScalar(p, TScalarStyle.Plain, "value 1")
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "key 2")
        assertTrue(next(p) is TokenType.Value)
        nextScalar(p, TScalarStyle.Plain, "value 2")
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testSpecEx7_3() {
        val s = "\n{\n    ? foo :,\n    : bar,\n}\n"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.FlowMappingStart)
        assertTrue(next(p) is TokenType.Key)
        nextScalar(p, TScalarStyle.Plain, "foo")
        assertTrue(next(p) is TokenType.Value)
        assertTrue(next(p) is TokenType.FlowEntry)
        assertTrue(next(p) is TokenType.Value)
        nextScalar(p, TScalarStyle.Plain, "bar")
        assertTrue(next(p) is TokenType.FlowEntry)
        assertTrue(next(p) is TokenType.FlowMappingEnd)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testPlainScalarStartingWithIndicatorsInFlow() {
        val s1 = "{a: :b}"
        val p1 = Scanner.new(s1.iterator())
        assertTrue(next(p1) is TokenType.StreamStart)
        assertTrue(next(p1) is TokenType.FlowMappingStart)
        assertTrue(next(p1) is TokenType.Key)
        nextScalar(p1, TScalarStyle.Plain, "a")
        assertTrue(next(p1) is TokenType.Value)
        nextScalar(p1, TScalarStyle.Plain, ":b")
        assertTrue(next(p1) is TokenType.FlowMappingEnd)
        assertTrue(next(p1) is TokenType.StreamEnd)
        end(p1)

        val s2 = "{a: ?b}"
        val p2 = Scanner.new(s2.iterator())
        assertTrue(next(p2) is TokenType.StreamStart)
        assertTrue(next(p2) is TokenType.FlowMappingStart)
        assertTrue(next(p2) is TokenType.Key)
        nextScalar(p2, TScalarStyle.Plain, "a")
        assertTrue(next(p2) is TokenType.Value)
        nextScalar(p2, TScalarStyle.Plain, "?b")
        assertTrue(next(p2) is TokenType.FlowMappingEnd)
        assertTrue(next(p2) is TokenType.StreamEnd)
        end(p2)
    }

    @Test
    fun testPlainScalarStartingWithIndicatorsInBlock() {
        val s1 = ":a"
        val p1 = Scanner.new(s1.iterator())
        assertTrue(next(p1) is TokenType.StreamStart)
        nextScalar(p1, TScalarStyle.Plain, ":a")
        assertTrue(next(p1) is TokenType.StreamEnd)
        end(p1)

        val s2 = "?a"
        val p2 = Scanner.new(s2.iterator())
        assertTrue(next(p2) is TokenType.StreamStart)
        nextScalar(p2, TScalarStyle.Plain, "?a")
        assertTrue(next(p2) is TokenType.StreamEnd)
        end(p2)
    }

    @Test
    fun testPlainScalarContainingIndicatorsInBlock() {
        val s1 = "a:,b"
        val p1 = Scanner.new(s1.iterator())
        assertTrue(next(p1) is TokenType.StreamStart)
        nextScalar(p1, TScalarStyle.Plain, "a:,b")
        assertTrue(next(p1) is TokenType.StreamEnd)
        end(p1)

        val s2 = ":,b"
        val p2 = Scanner.new(s2.iterator())
        assertTrue(next(p2) is TokenType.StreamStart)
        nextScalar(p2, TScalarStyle.Plain, ":,b")
        assertTrue(next(p2) is TokenType.StreamEnd)
        end(p2)
    }

    @Test
    fun testScannerCr() {
        val s = "---\r\n- tok1\r\n- tok2"
        val p = Scanner.new(s.iterator())
        assertTrue(next(p) is TokenType.StreamStart)
        assertTrue(next(p) is TokenType.DocumentStart)
        assertTrue(next(p) is TokenType.BlockSequenceStart)
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "tok1")
        assertTrue(next(p) is TokenType.BlockEntry)
        nextScalar(p, TScalarStyle.Plain, "tok2")
        assertTrue(next(p) is TokenType.BlockEnd)
        assertTrue(next(p) is TokenType.StreamEnd)
        end(p)
    }

    @Test
    fun testUri() {
        // upstream TODO test
    }

    @Test
    fun testUriEscapes() {
        // upstream TODO test
    }
}
