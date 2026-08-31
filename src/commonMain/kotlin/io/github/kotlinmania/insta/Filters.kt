// port-lint: source insta/src/filters.rs
package io.github.kotlinmania.insta

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

        fun from(pairs: Iterable<Pair<kotlin.String, kotlin.String>>): Filters {
            val filters = default()
            for ((regex, replacement) in pairs) {
                filters.add(regex, replacement)
            }
            return filters
        }

        fun fromIter(iter: Iterable<Pair<kotlin.String, kotlin.String>>): Filters = from(iter)
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
