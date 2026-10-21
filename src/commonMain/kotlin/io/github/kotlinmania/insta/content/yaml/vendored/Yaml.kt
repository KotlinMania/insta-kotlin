// port-lint: source content/yaml/vendored/yaml.rs
package io.github.kotlinmania.insta.content.yaml.vendored

/**
 * An entry in a YAML hash map.
 */
data class YamlHashEntry(
    val key: Yaml,
    val value: Yaml,
)

/**
 * A YAML node representation supporting easy document traversal and inspection.
 */
sealed class Yaml {
    data class Real(
        val value: String,
    ) : Yaml()

    data class Integer(
        val value: Long,
    ) : Yaml()

    data class StringValue(
        val value: String,
    ) : Yaml()

    data class BooleanValue(
        val value: Boolean,
    ) : Yaml()

    data class Array(
        val value: List<Yaml>,
    ) : Yaml()

    data class Hash(
        val value: List<YamlHashEntry>,
    ) : Yaml()

    data object Null : Yaml()

    data object BadValue : Yaml()

    fun asStr(): String? = (this as? StringValue)?.value

    fun asI64(): Long? = (this as? Integer)?.value

    fun asF64(): Double? = (this as? Real)?.let { parseF64(it.value) }

    fun asBool(): Boolean? = (this as? BooleanValue)?.value

    fun asVec(): List<Yaml>? = (this as? Array)?.value

    fun asHash(): List<YamlHashEntry>? = (this as? Hash)?.value

    fun intoString(): String? = asStr()

    fun intoI64(): Long? = asI64()

    fun intoF64(): Double? = asF64()

    fun intoBool(): Boolean? = asBool()

    fun intoVec(): List<Yaml>? = asVec()

    fun intoHash(): List<YamlHashEntry>? = asHash()

    fun isNull(): Boolean = this is Null

    fun isBadValue(): Boolean = this is BadValue

    fun isArray(): Boolean = this is Array

    operator fun get(idx: String): Yaml {
        if (this is Hash) {
            for (entry in value) {
                val k = entry.key
                if (k is StringValue && k.value == idx) {
                    return entry.value
                }
            }
        }
        return BadValue
    }

    operator fun get(idx: Int): Yaml {
        if (this is Array) {
            return value.getOrNull(idx) ?: BadValue
        }
        if (this is Hash) {
            return value.getOrNull(idx)?.value ?: BadValue
        }
        return BadValue
    }

    operator fun iterator(): Iterator<Yaml> =
        when (this) {
            is Array -> value.iterator()
            is Hash -> value.map { it.value }.iterator()
            else -> emptyList<Yaml>().iterator()
        }

    companion object {
        fun fromStr(v: String): Yaml {
            if (v.startsWith("0x")) {
                val rest = v.substring(2)
                val n = rest.toLongOrNull(16)
                if (n != null) {
                    return Integer(n)
                }
            }
            if (v.startsWith("0o")) {
                val rest = v.substring(2)
                val n = rest.toLongOrNull(8)
                if (n != null) {
                    return Integer(n)
                }
            }
            if (v.startsWith("+")) {
                val rest = v.substring(1)
                val n = rest.toLongOrNull()
                if (n != null) {
                    return Integer(n)
                }
            }
            return when (v) {
                "~", "null" -> {
                    Null
                }

                "true" -> {
                    BooleanValue(true)
                }

                "false" -> {
                    BooleanValue(false)
                }

                else -> {
                    val intVal = v.toLongOrNull()
                    if (intVal != null) {
                        Integer(intVal)
                    } else if (parseF64(v) != null) {
                        Real(v)
                    } else {
                        StringValue(v)
                    }
                }
            }
        }
    }
}

internal fun parseF64(v: String): Double? =
    when (v) {
        ".inf", ".Inf", ".INF", "+.inf", "+.Inf", "+.INF" -> Double.POSITIVE_INFINITY
        "-.inf", "-.Inf", "-.INF" -> Double.NEGATIVE_INFINITY
        ".nan", "NaN", ".NAN" -> Double.NaN
        else -> v.toDoubleOrNull()
    }

/**
 * Event receiver that constructs a tree of [Yaml] nodes from parser events.
 */
internal class YamlLoader internal constructor() : MarkedEventReceiver {
    internal val docs: ArrayList<Yaml> = ArrayList()
    private val docStack: ArrayList<Pair<Yaml, Int>> = ArrayList()
    private val keyStack: ArrayList<Yaml> = ArrayList()
    private val anchorMap: MutableMap<Int, Yaml> = LinkedHashMap()

    override fun onEvent(
        ev: Event,
        mark: Marker,
    ) {
        when (ev) {
            is Event.DocumentStart -> {}

            is Event.DocumentEnd -> {
                when (docStack.size) {
                    0 -> docs.add(Yaml.BadValue)
                    1 -> docs.add(docStack.removeLast().first)
                    else -> error("unreachable")
                }
            }

            is Event.SequenceStart -> {
                docStack.add(Pair(Yaml.Array(ArrayList()), ev.anchorId))
            }

            is Event.SequenceEnd -> {
                val node = docStack.removeLast()
                insertNewNode(node)
            }

            is Event.MappingStart -> {
                docStack.add(Pair(Yaml.Hash(ArrayList()), ev.anchorId))
                keyStack.add(Yaml.BadValue)
            }

            is Event.MappingEnd -> {
                keyStack.removeLast()
                val node = docStack.removeLast()
                insertNewNode(node)
            }

            is Event.Scalar -> {
                val node =
                    if (ev.style != TScalarStyle.Plain) {
                        Yaml.StringValue(ev.value)
                    } else if (ev.tag is TokenType.Tag) {
                        val handle = ev.tag.handle
                        val suffix = ev.tag.suffix
                        if (handle == "!!") {
                            when (suffix) {
                                "bool" -> {
                                    when (ev.value) {
                                        "true" -> Yaml.BooleanValue(true)
                                        "false" -> Yaml.BooleanValue(false)
                                        else -> Yaml.BadValue
                                    }
                                }

                                "int" -> {
                                    ev.value.toLongOrNull()?.let { Yaml.Integer(it) } ?: Yaml.BadValue
                                }

                                "float" -> {
                                    if (parseF64(ev.value) != null) Yaml.Real(ev.value) else Yaml.BadValue
                                }

                                "null" -> {
                                    when (ev.value) {
                                        "~", "null" -> Yaml.Null
                                        else -> Yaml.BadValue
                                    }
                                }

                                else -> {
                                    Yaml.StringValue(ev.value)
                                }
                            }
                        } else {
                            Yaml.StringValue(ev.value)
                        }
                    } else {
                        Yaml.fromStr(ev.value)
                    }
                insertNewNode(Pair(node, ev.anchorId))
            }

            is Event.Alias -> {
                val node = anchorMap[ev.anchorId] ?: Yaml.BadValue
                insertNewNode(Pair(node, 0))
            }

            else -> {}
        }
    }

    private fun insertNewNode(node: Pair<Yaml, Int>) {
        if (node.second > 0) {
            anchorMap[node.second] = node.first
        }
        if (docStack.isEmpty()) {
            docStack.add(node)
        } else {
            val parent = docStack.last()
            val parentVal = parent.first
            when (parentVal) {
                is Yaml.Array -> {
                    (parentVal.value as MutableList<Yaml>).add(node.first)
                }

                is Yaml.Hash -> {
                    val curKey = keyStack.last()
                    if (curKey.isBadValue()) {
                        keyStack[keyStack.size - 1] = node.first
                    } else {
                        val newKey = curKey
                        keyStack[keyStack.size - 1] = Yaml.BadValue
                        (parentVal.value as MutableList<YamlHashEntry>).add(YamlHashEntry(newKey, node.first))
                    }
                }

                else -> {
                    error("unreachable")
                }
            }
        }
    }

    companion object {
        fun loadFromStr(source: String): List<Yaml>? {
            val loader = YamlLoader()
            val parser = Parser.new(source.iterator())
            return try {
                parser.load(loader, true)
                loader.docs
            } catch (e: ScanError) {
                null
            }
        }
    }
}
