// port-lint: source runtime.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content
import io.github.kotlinmania.insta.content.toString as jsonToString
import io.github.kotlinmania.insta.content.toStringCompact as jsonToStringCompact
import io.github.kotlinmania.insta.content.toStringPretty as jsonToStringPretty

/**
 * Special marker to use an automatic name.
 */
object AutoName

sealed class SnapshotValue {
    data class FileText(
        val name: kotlin.String?,
        val content: kotlin.String,
    ) : SnapshotValue()

    data class InlineText(
        val referenceContent: kotlin.String,
        val content: kotlin.String,
    ) : SnapshotValue()

    data class Binary(
        val name: kotlin.String?,
        val content: List<UByte>,
        val extension: kotlin.String,
    ) : SnapshotValue()
}

private var allowDuplicatesFlag: Boolean = false

fun <T> withAllowDuplicates(block: () -> T): T {
    val old = allowDuplicatesFlag
    allowDuplicatesFlag = true
    return try {
        block()
    } finally {
        allowDuplicatesFlag = old
    }
}

/**
 * Asserts a string snapshot against a reference or snapshot name.
 */
fun assertSnapshot(
    value: kotlin.String,
    reference: kotlin.String? = null,
    name: kotlin.String? = null,
    expr: kotlin.String? = null,
) {
    val settings = Settings.cloneCurrent()
    var processedValue = value
    val filters = settings.filters()
    processedValue = filters.applyTo(processedValue)

    if (reference != null) {
        val expected = filters.applyTo(reference)
        if (processedValue.trimEnd() != expected.trimEnd()) {
            throw AssertionError(
                "Snapshot assertion failed for '${name ?: "inline"}':\nExpected: $expected\nActual:   $processedValue",
            )
        }
    }
}

/**
 * Asserts a debug string snapshot of an object.
 */
fun assertDebugSnapshot(
    value: Any?,
    reference: kotlin.String? = null,
    name: kotlin.String? = null,
    expr: kotlin.String? = null,
) {
    assertSnapshot(value.toString(), reference, name, expr)
}

/**
 * Asserts a JSON serialized snapshot of a Content tree.
 */
fun assertJsonSnapshot(
    value: Content,
    reference: kotlin.String? = null,
    name: kotlin.String? = null,
    expr: kotlin.String? = null,
) {
    val settings = Settings.cloneCurrent()
    val redacted = settings.applyRedactions(value)
    val jsonStr = jsonToStringPretty(redacted)
    assertSnapshot(jsonStr, reference, name, expr)
}

/**
 * Asserts a compact JSON serialized snapshot of a Content tree.
 */
fun assertCompactJsonSnapshot(
    value: Content,
    reference: kotlin.String? = null,
    name: kotlin.String? = null,
    expr: kotlin.String? = null,
) {
    val settings = Settings.cloneCurrent()
    val redacted = settings.applyRedactions(value)
    val jsonStr = jsonToStringCompact(redacted)
    assertSnapshot(jsonStr, reference, name, expr)
}
