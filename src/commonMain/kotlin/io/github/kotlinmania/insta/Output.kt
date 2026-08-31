// port-lint: source insta/src/output.rs
package io.github.kotlinmania.insta

/**
 * Snapshot printer utility.
 */
class SnapshotPrinter(
    private val workspaceRoot: kotlin.String,
    private val oldSnapshot: Snapshot?,
    private val newSnapshot: Snapshot,
) {
    private var oldSnapshotHint: kotlin.String = "old snapshot"
    private var newSnapshotHint: kotlin.String = "new results"
    private var showInfo: Boolean = false
    private var showDiff: Boolean = false
    private var title: kotlin.String? = null
    private var line: UInt? = null
    private var snapshotFile: kotlin.String? = null

    fun setSnapshotHints(old: kotlin.String, new: kotlin.String) {
        oldSnapshotHint = old
        newSnapshotHint = new
    }

    fun setShowInfo(yes: Boolean) {
        showInfo = yes
    }

    fun setShowDiff(yes: Boolean) {
        showDiff = yes
    }

    fun setTitle(title: kotlin.String?) {
        this.title = title
    }

    fun setLine(line: UInt?) {
        this.line = line
    }

    fun setSnapshotFile(file: kotlin.String?) {
        this.snapshotFile = file
    }

    fun print() {
        if (title != null) {
            println("=== $title ===")
        }
        printSnapshotDiff()
    }

    private fun printSnapshotDiff() {
        printSnapshotSummary()
        if (showDiff) {
            printChangeset()
        } else {
            printSnapshot()
        }
    }

    private fun printSnapshotSummary() {
        val name = newSnapshot.snapshotName ?: "unnamed"
        println("Snapshot: $name")
        val expr = newSnapshot.metadata.expression
        if (expr != null) {
            println("Expression: $expr")
        }
    }

    private fun printSnapshot() {
        println("Snapshot Contents:")
        when (val c = newSnapshot.contents()) {
            is SnapshotContents.Text -> {
                println(c.value.contents)
            }
            is SnapshotContents.Binary -> {
                println("<binary data (${c.value.size} bytes)>")
            }
        }
    }

    private fun printChangeset() {
        println("Diff:")
        val oldText =
            when (val o = oldSnapshot?.contents()) {
                is SnapshotContents.Text -> o.value.contents
                else -> ""
            }
        val newText =
            when (val n = newSnapshot.contents()) {
                is SnapshotContents.Text -> n.value.contents
                else -> ""
            }
        println("--- $oldSnapshotHint\n+++ $newSnapshotHint")
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        for (line in oldLines) {
            if (!newLines.contains(line)) {
                println("-$line")
            }
        }
        for (line in newLines) {
            if (!oldLines.contains(line)) {
                println("+$line")
            }
        }
    }
}
