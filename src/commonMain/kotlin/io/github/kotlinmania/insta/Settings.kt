// port-lint: source insta/src/settings.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content

/**
 * Represents stored redactions.
 */
class Redactions {
    private val list: MutableList<Pair<Selector, Redaction>> = mutableListOf()

    constructor()

    internal constructor(list: List<Pair<Selector, Redaction>>) {
        this.list.addAll(list)
    }

    fun deepCopy(): Redactions = Redactions(list)

    fun add(selector: Selector, redaction: Redaction) {
        list.add(Pair(selector, redaction))
    }

    fun clear() {
        list.clear()
    }

    companion object {
        fun default(): Redactions = Redactions()

        fun from(pairs: Iterable<Pair<kotlin.String, Redaction>>): Redactions {
            val redactions = default()
            for ((selStr, redaction) in pairs) {
                redactions.add(Selector.parse(selStr), redaction)
            }
            return redactions
        }
    }

    /**
     * Applies all redactions to the given content.
     */
    fun applyToContent(content: Content): Content {
        var result = content
        for ((selector, redaction) in list) {
            result = selector.redact(result, redaction)
        }
        return result
    }
}

/**
 * Actual settings configuration.
 */
data class ActualSettings(
    var sortMaps: Boolean = false,
    var snapshotPath: kotlin.String = "snapshots",
    var snapshotSuffix: kotlin.String = "",
    var inputFile: kotlin.String? = null,
    var snapshotDescription: kotlin.String? = null,
    var info: Content? = null,
    var omitExpression: Boolean = false,
    var prependModuleToSnapshot: Boolean = true,
    var redactions: Redactions = Redactions(),
    var filters: Filters = Filters(),
    var allowEmptyGlob: Boolean = false,
) {
    fun sortMaps(value: Boolean) {
        sortMaps = value
    }

    fun snapshotPath(path: kotlin.String) {
        snapshotPath = path
    }

    fun snapshotSuffix(suffix: kotlin.String) {
        snapshotSuffix = suffix
    }

    fun inputFile(path: kotlin.String) {
        inputFile = path
    }

    fun setDescription(value: kotlin.String) {
        snapshotDescription = value
    }

    fun rawInfo(content: Content) {
        info = content
    }

    fun omitExpression(value: Boolean) {
        omitExpression = value
    }

    fun prependModuleToSnapshot(value: Boolean) {
        prependModuleToSnapshot = value
    }

    fun redactions(r: Redactions) {
        redactions = r
    }

    fun filters(f: Filters) {
        filters = f
    }

    fun allowEmptyGlob(value: Boolean) {
        allowEmptyGlob = value
    }

    fun deepCopy(): ActualSettings =
        ActualSettings(
            sortMaps = sortMaps,
            snapshotPath = snapshotPath,
            snapshotSuffix = snapshotSuffix,
            inputFile = inputFile,
            snapshotDescription = snapshotDescription,
            info = info,
            omitExpression = omitExpression,
            prependModuleToSnapshot = prependModuleToSnapshot,
            redactions = redactions.deepCopy(),
            filters = filters.deepCopy(),
            allowEmptyGlob = allowEmptyGlob,
        )
}

private var currentActualSettings: ActualSettings = ActualSettings()

/**
 * Configures how insta operates at test time.
 */
class Settings(
    private var inner: ActualSettings = currentActualSettings.deepCopy(),
) {
    companion object {
        fun default(): Settings = Settings(ActualSettings())

        fun new(): Settings = default()

        fun cloneCurrent(): Settings = Settings(currentActualSettings.deepCopy())

        fun <R> with(f: (Settings) -> R): R = f(Settings(currentActualSettings.deepCopy()))
    }

    fun privateInnerMut(): ActualSettings = inner

    fun setSortMaps(value: Boolean) {
        inner.sortMaps = value
    }

    fun sortMaps(): Boolean = inner.sortMaps

    fun setPrependModuleToSnapshot(value: Boolean) {
        inner.prependModuleToSnapshot = value
    }

    fun prependModuleToSnapshot(): Boolean = inner.prependModuleToSnapshot

    fun setAllowEmptyGlob(value: Boolean) {
        inner.allowEmptyGlob = value
    }

    fun allowEmptyGlob(): Boolean = inner.allowEmptyGlob

    fun setSnapshotSuffix(suffix: kotlin.String) {
        inner.snapshotSuffix = suffix
    }

    fun removeSnapshotSuffix() {
        inner.snapshotSuffix = ""
    }

    fun snapshotSuffix(): kotlin.String? =
        if (inner.snapshotSuffix.isEmpty()) null else inner.snapshotSuffix

    fun setInputFile(path: kotlin.String) {
        inner.inputFile = path
    }

    fun removeInputFile() {
        inner.inputFile = null
    }

    fun inputFile(): kotlin.String? = inner.inputFile

    fun setDescription(value: kotlin.String) {
        inner.snapshotDescription = value
    }

    fun removeDescription() {
        inner.snapshotDescription = null
    }

    fun snapshotDescription(): kotlin.String? = inner.snapshotDescription

    fun setRawInfo(content: Content) {
        inner.info = content
    }

    fun removeInfo() {
        inner.info = null
    }

    fun info(): Content? = inner.info

    fun hasInfo(): Boolean = inner.info != null

    fun setOmitExpression(value: Boolean) {
        inner.omitExpression = value
    }

    fun omitExpression(): Boolean = inner.omitExpression

    fun addRedaction(selector: kotlin.String, replacement: Redaction) {
        inner.redactions.add(Selector.parse(selector), replacement)
    }

    fun addDynamicRedaction(selector: kotlin.String, callback: (Content, ContentPath) -> Content) {
        addRedaction(selector, dynamicRedaction(callback))
    }

    fun sortSelector(selector: kotlin.String) {
        addRedaction(selector, sortedRedaction())
    }

    fun setRedactions(redactions: Redactions) {
        inner.redactions = redactions
    }

    fun clearRedactions() {
        inner.redactions.clear()
    }

    fun applyRedactions(content: Content): Content =
        inner.redactions.applyToContent(content)

    fun addFilter(regex: kotlin.String, replacement: kotlin.String) {
        inner.filters.add(regex, replacement)
    }

    fun setFilters(filters: Filters) {
        inner.filters = filters
    }

    fun clearFilters() {
        inner.filters.clear()
    }

    fun filters(): Filters = inner.filters

    fun setSnapshotPath(path: kotlin.String) {
        inner.snapshotPath = path
    }

    fun snapshotPath(): kotlin.String = inner.snapshotPath

    fun <R> bind(f: () -> R): R {
        val old = currentActualSettings.deepCopy()
        currentActualSettings = inner.deepCopy()
        return try {
            f()
        } finally {
            currentActualSettings = old
        }
    }

    fun bindToScope(): SettingsBindDropGuard {
        val old = currentActualSettings.deepCopy()
        currentActualSettings = inner.deepCopy()
        return SettingsBindDropGuard(old)
    }
}

class SettingsBindDropGuard(
    private val old: ActualSettings,
) {
    fun release() {
        currentActualSettings = old
    }
}

fun <R> withSettings(configure: Settings.() -> Unit, block: () -> R): R {
    val settings = Settings.cloneCurrent()
    settings.configure()
    return settings.bind(block)
}
