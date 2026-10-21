// port-lint: source settings.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content
import io.github.kotlinmania.oncecell.sync.Lazy

/**
 * A single redaction selector and its replacement rule.
 */
data class RedactionEntry(
    val selector: kotlin.String,
    val redaction: Redaction,
)

/**
 * Represents stored redactions.
 */
class Redactions {
    private val list: MutableList<Pair<Selector, Redaction>> = mutableListOf()

    constructor()

    internal constructor(list: List<Pair<Selector, Redaction>>) {
        this.list.addAll(list)
    }

    /**
     * Creates a deep copy of these redactions.
     */
    fun deepCopy(): Redactions = Redactions(list)

    /**
     * Adds a parsed selector and redaction pair.
     */
    fun add(
        selector: Selector,
        redaction: Redaction,
    ) {
        list.add(Pair(selector, redaction))
    }

    /**
     * Clears all stored redactions.
     */
    fun clear() {
        list.clear()
    }

    companion object {
        /**
         * Returns an empty set of redactions.
         */
        fun default(): Redactions = Redactions()

        /**
         * Constructs redactions from a list of entries.
         */
        fun from(entries: List<RedactionEntry>): Redactions {
            val redactions = default()
            for (entry in entries) {
                redactions.add(Selector.parse(entry.selector), entry.redaction)
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
 * Actual settings configuration holding mutable snapshot parameters.
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
    /**
     * Sets map sorting flag.
     */
    fun sortMaps(value: Boolean) {
        sortMaps = value
    }

    /**
     * Sets snapshot storage directory path.
     */
    fun snapshotPath(path: kotlin.String) {
        snapshotPath = path
    }

    /**
     * Sets the snapshot suffix.
     */
    fun snapshotSuffix(suffix: kotlin.String) {
        snapshotSuffix = suffix
    }

    /**
     * Sets the input file path reference.
     */
    fun inputFile(path: kotlin.String) {
        inputFile = path
    }

    /**
     * Sets the snapshot description.
     */
    fun description(value: kotlin.String) {
        snapshotDescription = value
    }

    /**
     * Sets the snapshot description.
     */
    fun setDescription(value: kotlin.String) {
        snapshotDescription = value
    }

    /**
     * Sets structured info metadata.
     */
    fun info(content: Content) {
        rawInfo(content)
    }

    /**
     * Sets raw content info metadata.
     */
    fun rawInfo(content: Content) {
        info = content
    }

    /**
     * Sets whether expression strings should be omitted.
     */
    fun omitExpression(value: Boolean) {
        omitExpression = value
    }

    /**
     * Sets whether the module name is prepended to the snapshot file name.
     */
    fun prependModuleToSnapshot(value: Boolean) {
        prependModuleToSnapshot = value
    }

    /**
     * Sets the redaction rules.
     */
    fun redactions(r: Redactions) {
        redactions = r
    }

    /**
     * Sets regex string filters.
     */
    fun filters(f: Filters) {
        filters = f
    }

    /**
     * Sets whether empty globs are allowed.
     */
    fun allowEmptyGlob(value: Boolean) {
        allowEmptyGlob = value
    }

    /**
     * Creates a deep copy of actual settings.
     */
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

private val defaultSettings: Lazy<ActualSettings> =
    Lazy.new {
        ActualSettings(
            sortMaps = false,
            snapshotPath = "snapshots",
            snapshotSuffix = "",
            inputFile = null,
            snapshotDescription = null,
            info = null,
            omitExpression = false,
            prependModuleToSnapshot = true,
            redactions = Redactions.default(),
            filters = Filters.default(),
            allowEmptyGlob = false,
        )
    }

private var currentActualSettings: ActualSettings = defaultSettings.value.deepCopy()

/**
 * Configures how insta operates at test time.
 *
 * Settings are bound to the execution scope and influence snapshot testing behaviors,
 * including sorting maps, stripping paths, applying redactions, or adding filters.
 */
class Settings internal constructor(
    private var inner: ActualSettings,
) {
    constructor() : this(currentActualSettings.deepCopy())

    companion object {
        /**
         * Returns default settings.
         */
        fun default(): Settings = Settings(defaultSettings.value.deepCopy())

        /**
         * Creates a new instance of settings with defaults.
         */
        fun new(): Settings = default()

        /**
         * Returns a copy of the currently active settings.
         */
        fun cloneCurrent(): Settings = Settings(currentActualSettings.deepCopy())

        internal fun <R> with(f: (Settings) -> R): R = f(Settings(currentActualSettings.deepCopy()))
    }

    /**
     * Internal mutable accessor for settings.
     */
    fun privateInnerMut(): ActualSettings = inner

    /**
     * Enables forceful sorting of maps before serialization.
     */
    fun setSortMaps(value: Boolean) {
        inner.sortMaps = value
    }

    /**
     * Returns the current value for map sorting.
     */
    fun sortMaps(): Boolean = inner.sortMaps

    /**
     * Sets whether the module name should be prepended to the snapshot file name.
     */
    fun setPrependModuleToSnapshot(value: Boolean) {
        inner.prependModuleToSnapshot = value
    }

    /**
     * Returns true if module name is prepended to the snapshot file name.
     */
    fun prependModuleToSnapshot(): Boolean = inner.prependModuleToSnapshot

    /**
     * Allows glob operations to match zero files without raising an error.
     */
    fun setAllowEmptyGlob(value: Boolean) {
        inner.allowEmptyGlob = value
    }

    /**
     * Returns the current value for the empty glob setting.
     */
    fun allowEmptyGlob(): Boolean = inner.allowEmptyGlob

    /**
     * Sets the snapshot suffix added to snapshot names.
     */
    fun setSnapshotSuffix(suffix: kotlin.String) {
        inner.snapshotSuffix = suffix
    }

    /**
     * Removes any configured snapshot suffix.
     */
    fun removeSnapshotSuffix() {
        inner.snapshotSuffix = ""
    }

    /**
     * Returns the current snapshot suffix if non-empty.
     */
    fun snapshotSuffix(): kotlin.String? = if (inner.snapshotSuffix.isEmpty()) null else inner.snapshotSuffix

    /**
     * Sets the input file reference path.
     */
    fun setInputFile(path: kotlin.String) {
        inner.inputFile = path
    }

    /**
     * Removes the input file reference path.
     */
    fun removeInputFile() {
        inner.inputFile = null
    }

    /**
     * Returns the current input file reference path.
     */
    fun inputFile(): kotlin.String? = inner.inputFile

    /**
     * Sets the snapshot description displayed in reviews and diffs.
     */
    fun setDescription(value: kotlin.String) {
        inner.snapshotDescription = value
    }

    /**
     * Removes the description from the snapshot.
     */
    fun removeDescription() {
        inner.snapshotDescription = null
    }

    /**
     * Returns the current description.
     */
    fun description(): kotlin.String? = inner.snapshotDescription

    /**
     * Returns the snapshot description.
     */
    fun snapshotDescription(): kotlin.String? = description()

    /**
     * Sets structured metadata info for the snapshot.
     */
    fun setInfo(content: Content) {
        setRawInfo(content)
    }

    /**
     * Sets raw content info metadata.
     */
    fun setRawInfo(content: Content) {
        inner.info = content
    }

    /**
     * Removes metadata info from the snapshot.
     */
    fun removeInfo() {
        inner.info = null
    }

    /**
     * Returns the current metadata info if present.
     */
    fun info(): Content? = inner.info

    /**
     * Returns true if metadata info is set.
     */
    fun hasInfo(): Boolean = inner.info != null

    /**
     * If true, omits expression strings from saved snapshots.
     */
    fun setOmitExpression(value: Boolean) {
        inner.omitExpression = value
    }

    /**
     * Returns true if expressions are omitted from snapshots.
     */
    fun omitExpression(): Boolean = inner.omitExpression

    /**
     * Registers a redaction replacement rule for the given selector string.
     */
    fun addRedaction(
        selector: kotlin.String,
        replacement: Redaction,
    ) {
        addRedactionImpl(selector, replacement)
    }

    private fun addRedactionImpl(
        selector: kotlin.String,
        replacement: Redaction,
    ) {
        inner.redactions.add(Selector.parse(selector), replacement)
    }

    /**
     * Registers a dynamic replacement callback for matching paths.
     */
    fun addDynamicRedaction(
        selector: kotlin.String,
        callback: DynamicRedactionCallback,
    ) {
        addRedaction(selector, dynamicRedaction(callback))
    }

    /**
     * Adds a special redaction that sorts elements matching the selector.
     */
    fun sortSelector(selector: kotlin.String) {
        addRedaction(selector, sortedRedaction())
    }

    /**
     * Replaces the currently set redactions.
     */
    fun setRedactions(redactions: Redactions) {
        inner.redactions = redactions
    }

    /**
     * Clears all configured redactions.
     */
    fun clearRedactions() {
        inner.redactions.clear()
    }

    /**
     * Applies the configured redactions to the given content.
     */
    fun applyRedactions(content: Content): Content = inner.redactions.applyToContent(content)

    /**
     * Adds a regex replacement filter applied to the formatted snapshot text.
     */
    fun addFilter(
        regex: kotlin.String,
        replacement: kotlin.String,
    ) {
        inner.filters.add(regex, replacement)
    }

    /**
     * Replaces the currently set regex filters.
     */
    fun setFilters(filters: Filters) {
        inner.filters = filters
    }

    /**
     * Clears all regex filters.
     */
    fun clearFilters() {
        inner.filters.clear()
    }

    /**
     * Returns the currently configured filters.
     */
    fun filters(): Filters = inner.filters

    /**
     * Sets the directory path where snapshots are written.
     */
    fun setSnapshotPath(path: kotlin.String) {
        inner.snapshotPath = path
    }

    /**
     * Returns the directory path where snapshots are written.
     */
    fun snapshotPath(): kotlin.String = inner.snapshotPath

    /**
     * Runs a function with the current settings bound to the scope.
     */
    fun <R> bind(f: () -> R): R {
        val old = currentActualSettings.deepCopy()
        currentActualSettings = inner.deepCopy()
        return try {
            f()
        } finally {
            currentActualSettings = old
        }
    }

    // Asynchronous binding future polling and custom drop guard semantics do not translate to Kotlin.

    /**
     * Binds the settings to the current scope and returns a drop guard.
     */
    fun bindToScope(): SettingsBindDropGuard {
        val old = currentActualSettings.deepCopy()
        currentActualSettings = inner.deepCopy()
        return SettingsBindDropGuard(old)
    }
}

/**
 * Drop guard returned from bindToScope.
 */
class SettingsBindDropGuard internal constructor(
    private val old: ActualSettings,
) {
    /**
     * Restores the previous settings configuration.
     */
    fun release() {
        currentActualSettings = old
    }
}

/**
 * Executes a block of code with modified snapshot settings.
 */
fun <R> withSettings(
    configure: (Settings) -> Unit,
    block: () -> R,
): R {
    val settings = Settings.cloneCurrent()
    configure(settings)
    return settings.bind(block)
}


