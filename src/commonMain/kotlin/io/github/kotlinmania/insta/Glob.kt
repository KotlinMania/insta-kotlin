// port-lint: source glob.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.globset.GlobBuilder
import io.github.kotlinmania.globset.GlobMatcher
import io.github.kotlinmania.oncecell.sync.Lazy

/**
 * The glob collector holds failure count and an indication if reviews should be run.
 */
class GlobCollector(
    var failFast: Boolean,
    var failed: Int,
    var showInstaHint: Boolean,
)

/**
 * The glob stack holds failure count and an indication if cargo insta review should be run.
 */
internal val GLOB_STACK: Lazy<MutableList<GlobCollector>> =
    Lazy.new { mutableListOf() }

private val GLOB_FILTER: Lazy<List<GlobMatcher>> =
    Lazy.new {
        emptyList<GlobMatcher>()
    }

/**
 * Executes a glob assertion against matching files in the given directory.
 */
fun globExec(
    workspaceDir: kotlin.String,
    base: kotlin.String,
    pattern: kotlin.String,
    f: (kotlin.String) -> Unit,
) {
    if (pattern.contains("../") || pattern.startsWith("..")) {
        error(
            "Parent directory traversal is not supported in glob patterns. Use the three-argument form of glob! with an explicit base directory instead.",
        )
    }

    val settings = Settings.cloneCurrent()
    if (settings.allowEmptyGlob() && !baseExists(base)) {
        return
    }

    val glob =
        GlobBuilder(pattern)
            .caseInsensitive(true)
            .literalSeparator(true)
            .build()
            .compileMatcher()

    val walker = walkDirFiles(base)
    var globFoundMatches = false

    val stack = GLOB_STACK.value
    stack.add(
        GlobCollector(
            failFast = getToolConfig(workspaceDir).globFailFast,
            failed = 0,
            showInstaHint = false,
        ),
    )

    val allMatchingFiles = mutableListOf<kotlin.String>()
    val filteredFiles = mutableListOf<kotlin.String>()

    for (path in walker) {
        val strippedPath = stripPrefix(path, base)
        if (!glob.isMatch(strippedPath)) {
            continue
        }

        globFoundMatches = true
        allMatchingFiles.add(path)

        val filters = GLOB_FILTER.value
        if (filters.isNotEmpty() && filters.none { it.isMatch(strippedPath) }) {
            println("Skipping $strippedPath due to glob filter")
            continue
        }

        filteredFiles.add(path)
    }

    allMatchingFiles.sort()
    filteredFiles.sort()

    val commonPrefix = findCommonPrefix(allMatchingFiles)
    val matchingFiles = filteredFiles

    for (path in matchingFiles) {
        settings.setInputFile(path)

        val snapshotSuffix =
            if (commonPrefix != null) {
                stripPrefix(path, commonPrefix)
            } else {
                path.substringAfterLast('/').substringAfterLast('\\')
            }

        settings.setSnapshotSuffix(snapshotSuffix)
        settings.bind {
            f(path)
        }
    }

    val top = stack.removeAt(stack.size - 1)
    if (!globFoundMatches && !settings.allowEmptyGlob()) {
        error("the glob! macro did not match any files.")
    }

    if (top.failed > 0) {
        if (top.showInstaHint) {
            println(style("To update snapshots run `cargo insta review`").dim())
        }
        if (top.failed > 1) {
            println(
                style("To enable fast failing for glob! export INSTA_GLOB_FAIL_FAST=1 as environment variable.").dim(),
            )
        }
        val suffix = if (top.failed == 1) "" else "s"
        error("glob! resulted in ${top.failed} snapshot assertion failure$suffix")
    }
}

/**
 * Finds the common prefix components across sorted paths.
 */
fun findCommonPrefix(sortedPaths: List<kotlin.String>): kotlin.String? {
    val first = sortedPaths.firstOrNull() ?: return null
    val last = sortedPaths.lastOrNull() ?: return null
    val firstComponents = splitPathComponents(first)
    val lastComponents = splitPathComponents(last)
    var prefixLen = 0
    while (prefixLen < firstComponents.size &&
        prefixLen < lastComponents.size &&
        firstComponents[prefixLen] == lastComponents[prefixLen]
    ) {
        prefixLen++
    }

    if (prefixLen == 0) {
        return null
    }

    val isAbsolute = first.startsWith('/') || first.startsWith('\\')
    val prefix = firstComponents.subList(0, prefixLen).joinToString("/")
    return if (isAbsolute) "/$prefix" else prefix
}

private fun splitPathComponents(path: kotlin.String): List<kotlin.String> =
    path.replace('\\', '/').split('/').filter { it.isNotEmpty() }

internal fun stripPrefix(
    path: kotlin.String,
    prefix: kotlin.String,
): kotlin.String {
    val normPath = path.replace('\\', '/')
    val normPrefix = prefix.replace('\\', '/').trimEnd('/')
    return if (normPath.startsWith(normPrefix)) {
        normPath.substring(normPrefix.length).trimStart('/')
    } else {
        normPath
    }
}

internal fun baseExists(base: kotlin.String): Boolean {
    val normBase = base.replace('\\', '/').trimEnd('/')
    val files = walkDirFiles(base)
    return files.isNotEmpty() || normBase == "." || normBase.isEmpty()
}

internal fun walkDirFiles(base: kotlin.String): List<kotlin.String> {
    val normBase = base.replace('\\', '/').trimEnd('/')
    val results = mutableSetOf<kotlin.String>()
    for (k in listVirtualFileKeys()) {
        val normK = k.replace('\\', '/')
        if (normBase.isEmpty() || normBase == "." || normK.startsWith("$normBase/") || normK == normBase) {
            results.add(k)
        }
    }
    return results.toList()
}
