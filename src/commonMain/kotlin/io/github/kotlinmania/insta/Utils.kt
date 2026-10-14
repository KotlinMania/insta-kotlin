// port-lint: source utils.rs
package io.github.kotlinmania.insta

class FakeStyledObject(
    val value: kotlin.String,
) {
    fun red(): FakeStyledObject = this

    fun green(): FakeStyledObject = this

    fun yellow(): FakeStyledObject = this

    fun cyan(): FakeStyledObject = this

    fun bold(): FakeStyledObject = this

    fun dim(): FakeStyledObject = this

    fun underlined(): FakeStyledObject = this

    override fun toString(): kotlin.String = value
}

fun style(val_: kotlin.String): FakeStyledObject = FakeStyledObject(val_)

/**
 * Are we running in a CI environment?
 */
fun isCi(): Boolean = false

/**
 * Returns the term width that insta should use.
 */
fun termWidth(): Int = 74

/**
 * Converts a path into a string that can be persisted.
 */
fun pathToStorage(path: kotlin.String): kotlin.String = path.replace('\\', '/')

/**
 * Tries to format a given expression with formatting rules.
 */
fun formatRustExpression(value: kotlin.String): kotlin.String = value.trim()

/**
 * Returns cargo binary name.
 */
fun getCargo(): kotlin.String = "cargo"
