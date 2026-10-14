// port-lint: source filters.rs
package io.github.kotlinmania.insta

/**
 * A single regex filter rule and its replacement string.
 */
data class FilterRule(
    val pattern: kotlin.String,
    val replacement: kotlin.String,
)

/**
 * Represents stored filters.
 */
class Filters {
    private val rules: MutableList<Pair<Regex, kotlin.String>> = mutableListOf()

    constructor()

    internal constructor(rules: List<Pair<Regex, kotlin.String>>) {
        this.rules.addAll(rules)
    }

    fun deepCopy(): Filters = Filters(rules)

    companion object {
        fun default(): Filters = Filters()

        fun from(rules: List<FilterRule>): Filters {
            val filters = default()
            for (rule in rules) {
                filters.add(rule.pattern, rule.replacement)
            }
            return filters
        }

        fun fromIter(iter: List<FilterRule>): Filters = from(iter)
    }

    /**
     * Adds a simple regex with a replacement.
     */
    fun add(regex: kotlin.String, replacement: kotlin.String) {
        rules.add(Pair(Regex(regex), replacement))
    }

    /**
     * Clears all filters.
     */
    fun clear() {
        rules.clear()
    }

    /**
     * Applies all filters to the given snapshot.
     */
    fun applyTo(s: kotlin.String): kotlin.String {
        var result = s
        for ((regex, replacement) in rules) {
            result = regex.replace(result, replacement)
        }
        return result
    }
}
