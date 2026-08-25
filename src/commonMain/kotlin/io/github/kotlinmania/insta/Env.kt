// port-lint: source env.rs
package io.github.kotlinmania.insta

enum class OutputBehavior {
    Diff,
    Summary,
    Minimal,
    Nothing,
}

enum class SnapshotUpdate {
    Always,
    Auto,
    Unseen,
    New,
    No,
    Force,
}

enum class TestRunner {
    Auto,
    CargoTest,
    Nextest,
}

enum class UnreferencedSnapshots {
    Auto,
    Reject,
    Delete,
    Warn,
    Ignore,
}

data class ToolConfig(
    val forcePass: Boolean = false,
    val requireFullMatch: Boolean = false,
    val output: OutputBehavior = OutputBehavior.Diff,
    val snapshotUpdate: SnapshotUpdate = SnapshotUpdate.Auto,
    val globFailFast: Boolean = false,
) {
    companion object {
        fun default(): ToolConfig = ToolConfig()
    }
}
