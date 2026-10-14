// port-lint: tests snapshot.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.prettyassertions.assertEq
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SnapshotTest {
    private fun normalizedOfLiteral(snapshot: kotlin.String): kotlin.String =
        normalizeInline(TextSnapshotContents.fromInlineLiteral(snapshot).contents)

    @Test
    fun testRequiredHashes() {
        assertSnapshot(requiredHashes("").toString(), "0")
        assertSnapshot(requiredHashes("Hello, world!").toString(), "0")
        assertSnapshot(requiredHashes("\"\"").toString(), "1")
        assertSnapshot(requiredHashes("##").toString(), "0")
        assertSnapshot(requiredHashes("\"#\"#").toString(), "2")
        assertSnapshot(requiredHashes("\"#").toString(), "2")
        assertSnapshot(requiredHashes("foo \"\"##### bar \"###\" baz").toString(), "6")
        assertSnapshot(requiredHashes("\"\"\"").toString(), "1")
        assertSnapshot(requiredHashes("####").toString(), "0")
        assertSnapshot(requiredHashes("\\\"\\\"##\\\"\\\"").toString(), "3")
        assertSnapshot(requiredHashes("r\"#\"Raw string\"#\"").toString(), "2")
    }

    @Test
    fun testNormalizeInlineSnapshot() {
        assertEq(
            "1\n2",
            normalizedOfLiteral(
                "\n   1\n   2\n",
            ),
        )

        assertEq(
            "        1\n2\n",
            normalizedOfLiteral(
                "\n            1\n    2\n    ",
            ),
        )

        assertEq(
            "1\n2\n",
            normalizedOfLiteral(
                "\n            1\n            2\n    ",
            ),
        )

        assertEq(
            "1\n2",
            normalizedOfLiteral(
                "\n   1\n   2\n",
            ),
        )

        assertEq(
            "        a",
            normalizedOfLiteral(
                "\n        a\n    ",
            ),
        )

        assertEq("", normalizedOfLiteral(""))

        assertEq(
            "    a\n    b\nc\n    ",
            normalizedOfLiteral(
                "\n    a\n    b\nc\n    ",
            ),
        )

        assertEq(
            "a",
            normalizedOfLiteral(
                "\na\n    ",
            ),
        )

        assertEq(
            "    a",
            normalizedOfLiteral(
                "\n    a",
            ),
        )
    }

    @Test
    fun testNamesOfPath() {
        val first = namesOfPath("/src/snapshots/insta_tests__tests__name_foo.snap")
        assertDebugSnapshot(
            "(\n    \"${first.first}\",\n    \"${first.second}\",\n)",
            "\n    (\n        \"name_foo\",\n        \"insta_tests__tests\",\n    )\n    ",
        )
        val second = namesOfPath("/src/snapshots/name_foo.snap")
        assertDebugSnapshot(
            "(\n    \"${second.first}\",\n    \"${second.second}\",\n)",
            "\n    (\n        \"name_foo\",\n        \"\",\n    )\n    ",
        )
        val third = namesOfPath("foo/src/snapshots/go1.20.5.snap")
        assertDebugSnapshot(
            "(\n    \"${third.first}\",\n    \"${third.second}\",\n)",
            "\n    (\n        \"go1.20.5\",\n        \"\",\n    )\n    ",
        )
    }

    @Test
    fun testSnapshotContentsToInline() {
        val snapshotContents =
            TextSnapshotContents.new("testing", TextSnapshotKind.Inline)
        assertEq("\"testing\"", snapshotContents.toInline(""))

        assertEq(
            "\"\n\na\nb\n\"",
            TextSnapshotContents.new("\na\nb", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"\n    a\n    b\n    \"",
            TextSnapshotContents.new("a\nb", TextSnapshotKind.Inline).toInline("    "),
        )

        assertEq(
            "\"\n\na\nb\n\"",
            TextSnapshotContents.new("\n    a\n    b", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"\n\n    a\n\n    b\n    \"",
            TextSnapshotContents.new("\na\n\nb", TextSnapshotKind.Inline).toInline("    "),
        )

        assertEq(
            "\"ab\"",
            TextSnapshotContents
                .new(
                    "ab\n    ",
                    TextSnapshotKind.Inline,
                ).toInline(""),
        )

        assertEq(
            "\"    ab\"",
            TextSnapshotContents
                .new(
                    "    ab\n    ",
                    TextSnapshotKind.Inline,
                ).toInline(""),
        )

        assertEq(
            "\"\n\nab\n\"",
            TextSnapshotContents.new("\n    ab\n", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"ab\"",
            TextSnapshotContents.new("ab", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"a\tb\"",
            TextSnapshotContents.new("a\tb", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"\na\t\nb\n\"",
            TextSnapshotContents.new("a\t\nb", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"a\\rb\"",
            TextSnapshotContents.new("a\rb", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"\\n\\r foo  bar\"",
            TextSnapshotContents.new("\n\r foo  bar", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"a\\0b\"",
            TextSnapshotContents.new("a\u0000b", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"a\uFFFDb\"",
            TextSnapshotContents.new("a\uFFFDb", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"\n    hello\n    world\n    \"",
            TextSnapshotContents.new("hello\r\nworld", TextSnapshotKind.Inline).toInline("    "),
        )

        assertEq(
            "\"\n\nhello\n\"",
            TextSnapshotContents.new("\r\nhello", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"hello\"",
            TextSnapshotContents.new("hello\r\n", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"hello\\n\\rworld\"",
            TextSnapshotContents.new("hello\n\rworld", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "\"a\\rb\\nc\"",
            TextSnapshotContents.new("a\rb\r\nc", TextSnapshotKind.Inline).toInline(""),
        )
    }

    @Test
    fun testEscapedFormatPreservesContent() {
        assertEq(
            "\n\r foo",
            TextSnapshotContents.fromInlineLiteral("\n\r foo").contents,
        )
        assertEq(
            "a\rb",
            TextSnapshotContents.fromInlineLiteral("a\rb").contents,
        )

        assertEq(
            "foo\nbar",
            TextSnapshotContents.fromInlineLiteral("\nfoo\nbar\n").contents,
        )
    }

    @Test
    fun testSnapshotContentsHashes() {
        assertEq(
            "\"a###b\"",
            TextSnapshotContents.new("a###b", TextSnapshotKind.Inline).toInline(""),
        )

        assertEq(
            "r\"\na\n\\###b\n\"",
            TextSnapshotContents.new("a\n\\###b", TextSnapshotKind.Inline).toInline(""),
        )
    }

    @Test
    fun testMinIndentation() {
        assertEq(
            "   ",
            minIndentation(
                "\n   1\n   2\n   ",
            ),
        )

        assertEq(
            "    ",
            minIndentation(
                "\n            1\n    2",
            ),
        )

        assertEq(
            "            ",
            minIndentation(
                "\n            1\n            2\n    ",
            ),
        )

        assertEq(
            "   ",
            minIndentation(
                "\n   1\n   2\n",
            ),
        )

        assertEq(
            "        ",
            minIndentation(
                "\n        a\n    ",
            ),
        )

        assertEq("", minIndentation(""))

        assertEq(
            "",
            minIndentation(
                "\n    a\n    b\nc\n    ",
            ),
        )

        assertEq(
            "",
            minIndentation(
                "\na\n    ",
            ),
        )

        assertEq(
            "    ",
            minIndentation(
                "\n    a",
            ),
        )

        assertEq(
            "",
            minIndentation(
                "a\n  a",
            ),
        )

        assertEq(
            "\n\t\t1\n2",
            normalizeInline(
                "\n\t\t\t1\n\t2",
            ),
        )

        assertEq(
            "\n1\n2\n",
            normalizeInline(
                "\n\t  \t  1\n\t  \t  2\n    ",
            ),
        )
    }

    @Test
    fun testMinIndentationAdditional() {
        var t = "\n   1\n   2\n"
        assertEq("   ", minIndentation(t))

        t = "\n        a\n    "
        assertEq("        ", minIndentation(t))

        t = ""
        assertEq("", minIndentation(t))

        t = "\n    a\n    b\nc\n    "
        assertEq("", minIndentation(t))

        t = "\na"
        assertEq("", minIndentation(t))

        t = "\n    a"
        assertEq("    ", minIndentation(t))

        t = "a\n  a"
        assertEq("", minIndentation(t))

        t = "\n \t1\n \t2\n    "
        assertEq(" \t", minIndentation(t))

        t = "\n  \t  \t  \t1\n  \t2"
        assertEq("  \t", minIndentation(t))

        t = "\n\t\t\t1\n\t2"
        assertEq("\t", minIndentation(t))
    }

    @Test
    fun testInlineSnapshotValueNewline() {
        assertEq("", normalizeInline("\n"))
    }

    @Test
    fun testParseYamlError() {
        val temp = "bad.yaml"
        val invalid =
            "---\n" +
                "    This is invalid yaml:\n" +
                "     {\n" +
                "        {\n" +
                "    ---\n" +
                "    "
        writeTextFile(temp, invalid)

        val error = assertFails { Snapshot.fromFile(temp) }.message.orEmpty()
        assertTrue(error.contains("Failed parsing the YAML from"))
        assertTrue(error.contains("bad.yaml"))
    }

    @Test
    fun testOwnership() {
        val r = 0 until 10
        assertDebugSnapshot("0..10", "0..10")
        assertDebugSnapshot("${r.first}..${r.last + 1}", "0..10")
    }

    @Test
    fun testEmptyLines() {
        assertSnapshot(
            "single line should fit on a single line",
            "single line should fit on a single line",
        )
        assertSnapshot(
            "single line should fit on a single line, even if it's really really really really really really really really really long",
            "single line should fit on a single line, even if it's really really really really really really really really really long",
        )

        assertSnapshot(
            "multiline content starting on first line\n\n    final line\n    ",
            "\n    multiline content starting on first line\n\n        final line\n    ",
        )

        assertSnapshot(
            "\n    multiline content starting on second line\n\n    final line\n    ",
            "\n\n    multiline content starting on second line\n\n    final line\n    ",
        )
    }
}
