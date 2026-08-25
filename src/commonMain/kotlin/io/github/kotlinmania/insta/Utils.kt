// port-lint: source utils.rs
package io.github.kotlinmania.insta

class FakeStyledObject<D>(val value: D) {
    fun red(): FakeStyledObject<D> = this
    fun green(): FakeStyledObject<D> = this
    fun yellow(): FakeStyledObject<D> = this
    fun cyan(): FakeStyledObject<D> = this
    fun bold(): FakeStyledObject<D> = this
    fun dim(): FakeStyledObject<D> = this
    fun underlined(): FakeStyledObject<D> = this

    override fun toString(): kotlin.String = value.toString()
}

fun <D> style(val_: D): FakeStyledObject<D> = FakeStyledObject(val_)

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
