// port-lint: source redaction.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content
import kotlin.math.pow
import kotlin.math.round

/**
 * Error returned when parsing a selector string.
 */
class SelectorParseError(
    val message: kotlin.String,
    private val col: Int = 0,
) {
    /**
     * Returns the column where the error occurred.
     */
    fun column(): Int = col

    /**
     * Formats the error message.
     */
    fun fmt(): kotlin.String = message

    override fun toString(): kotlin.String = fmt()
}

/**
 * Parser for selectors.
 */
object SelectParser {
    fun parse(selector: kotlin.String): Selector = Selector.parse(selector)
}

internal class SelectorParseException(
    val error: SelectorParseError,
) : IllegalArgumentException(error.message)

/**
 * Callback function for dynamic redactions.
 */
fun interface DynamicRedactionCallback {
    operator fun invoke(value: Content, path: ContentPath): Content
}

/**
 * Represents a path for a callback function.
 */
class ContentPath(
    val items: List<PathItem>,
) {
    override fun toString(): kotlin.String {
        val sb = StringBuilder()
        for (item in items) {
            sb.append('.')
            when (item) {
                is PathItem.ContentItem -> {
                    val s = item.content.asStr()
                    if (s != null) {
                        sb.append(s)
                    } else {
                        sb.append("<content>")
                    }
                }

                is PathItem.Field -> {
                    sb.append(item.name)
                }

                is PathItem.Index -> {
                    sb.append(item.idx.toString())
                }
            }
        }
        return sb.toString()
    }
}

/**
 * Component item in a content path.
 */
sealed class PathItem {
    /** Content node item. */
    data class ContentItem(
        val content: Content,
    ) : PathItem()

    /** Struct field name item. */
    data class Field(
        val name: kotlin.String,
    ) : PathItem()

    /** Sequence index and length item. */
    data class Index(
        val idx: ULong,
        val len: ULong,
    ) : PathItem()

    /**
     * Returns string value if this is a content item or field.
     */
    fun asStr(): kotlin.String? =
        when (this) {
            is ContentItem -> content.asStr()
            is Field -> name
            is Index -> null
        }

    /**
     * Returns unsigned 64-bit integer index if this is an index or integer content item.
     */
    fun asU64(): ULong? =
        when (this) {
            is ContentItem -> content.asU64()
            is Field -> null
            is Index -> idx
        }

    /**
     * Checks if this item falls within the given start and end range bounds.
     */
    fun rangeCheck(start: Long?, end: Long?): Boolean {
        fun expandRange(sel: Long, length: Long): Long =
            if (sel < 0) {
                maxOf(0L, length + sel)
            } else {
                sel
            }

        val (idxVal, lenVal) =
            when (this) {
                is Index -> Pair(idx.toLong(), len.toLong())
                else -> return false
            }

        return when {
            start == null && end == null -> {
                true
            }

            start == null && end != null -> {
                idxVal < expandRange(end, lenVal)
            }

            start != null && end == null -> {
                idxVal >= expandRange(start, lenVal)
            }

            start != null && end != null -> {
                idxVal >= expandRange(start, lenVal) && idxVal < expandRange(end, lenVal)
            }

            else -> {
                false
            }
        }
    }
}

/**
 * Path segment in a selector pattern.
 */
sealed class Segment {
    /** Matches any depth of nested elements. */
    data object DeepWildcard : Segment()

    /** Matches a single element. */
    data object Wildcard : Segment()

    /** Matches a struct field or map key. */
    data class Key(
        val key: kotlin.String,
    ) : Segment()

    /** Matches an exact sequence index. */
    data class Index(
        val idx: ULong,
    ) : Segment()

    /** Matches a range of sequence indices. */
    data class Range(
        val start: Long?,
        val end: Long?,
    ) : Segment()
}

/**
 * Replaces a value with another one.
 *
 * Represents a redaction rule.
 */
sealed class Redaction {
    /**
     * Static redaction with pre-computed content.
     */
    data class Static(
        val content: Content,
    ) : Redaction()

    /**
     * Dynamic redaction with a callback.
     */
    data class Dynamic(
        val callback: DynamicRedactionCallback,
    ) : Redaction()

    companion object {
        /** Creates a static redaction from content. */
        fun from(content: Content): Redaction = Static(content)

        /** Creates a static redaction from string. */
        fun from(value: kotlin.String): Redaction = Static(Content.from(value))

        /** Creates a static redaction from boolean. */
        fun from(value: Boolean): Redaction = Static(Content.from(value))

        /** Creates a static redaction from long integer. */
        fun from(value: Long): Redaction = Static(Content.from(value))

        /** Creates a static redaction from integer. */
        fun from(value: Int): Redaction = Static(Content.from(value))

        /** Creates a static redaction from floating point. */
        fun from(value: Double): Redaction = Static(Content.from(value))
    }

    /**
     * Performs the redaction of the value at the given path.
     */
    fun redact(value: Content, path: List<PathItem>): Content =
        when (this) {
            is Static -> content
            is Dynamic -> callback(value, ContentPath(path))
        }
}

/**
 * Creates a dynamic redaction.
 *
 * Can be used to redact a value with a dynamically computed replacement
 * or perform assertions before replacing.
 */
fun dynamicRedaction(callback: DynamicRedactionCallback): Redaction =
    Redaction.Dynamic(callback)

/**
 * Creates a dynamic redaction that sorts elements at the selector.
 *
 * Useful to force sets or maps to have deterministic ordering.
 */
fun sortedRedaction(): Redaction {
    fun sort(value: Content): Content =
        when (val inner = value.resolveInner()) {
            is Content.Seq -> Content.Seq(inner.value.sortedWith { a, b -> a.toString().compareTo(b.toString()) })
            is Content.Map -> Content.Map(inner.value.sortedWith { a, b -> a.key.toString().compareTo(b.key.toString()) })
            is Content.Struct -> Content.Struct(inner.name, inner.fields.sortedWith { a, b -> a.name.compareTo(b.name) })
            is Content.StructVariant -> Content.StructVariant(inner.name, inner.index, inner.variant, inner.fields.sortedWith { a, b -> a.name.compareTo(b.name) })
            else -> value
        }
    return dynamicRedaction { value, _ -> sort(value) }
}

/**
 * Creates a redaction that rounds floating point numbers to decimal places.
 */
fun roundedRedaction(decimals: Int): Redaction =
    dynamicRedaction { value, _ ->
        val f =
            when (val inner = value.resolveInner()) {
                is Content.F32 -> inner.value.toDouble()
                is Content.F64 -> inner.value
                else -> return@dynamicRedaction value
            }
        val x = 10.0.pow(decimals.toDouble())
        Content.F64(round(f * x) / x)
    }

/**
 * Parsed selector structure used to match paths for redactions.
 */
class Selector(
    val selectors: List<List<Segment>>,
) {
    fun makeStatic(): Selector = this

    private fun segmentIsMatch(segment: Segment, element: PathItem): Boolean =
        when (segment) {
            Segment.Wildcard, Segment.DeepWildcard -> true
            is Segment.Key -> element.asStr() == segment.key
            is Segment.Index -> element.asU64() == segment.idx
            is Segment.Range -> element.rangeCheck(segment.start, segment.end)
        }

    private fun selectorIsMatch(selector: List<Segment>, path: List<PathItem>): Boolean {
        val deepIdx = selector.indexOfFirst { it is Segment.DeepWildcard }
        if (deepIdx != -1) {
            val forwardSel = selector.subList(0, deepIdx)
            val backwardSel = selector.subList(deepIdx + 1, selector.size)

            if (path.size <= deepIdx) {
                return false
            }

            for (i in forwardSel.indices) {
                if (!segmentIsMatch(forwardSel[i], path[i])) {
                    return false
                }
            }

            val backLen = backwardSel.size
            for (i in 0 until backLen) {
                val seg = backwardSel[backwardSel.size - 1 - i]
                val elem = path[path.size - 1 - i]
                if (!segmentIsMatch(seg, elem)) {
                    return false
                }
            }

            return true
        } else {
            if (selector.size != path.size) {
                return false
            }
            for (i in selector.indices) {
                if (!segmentIsMatch(selector[i], path[i])) {
                    return false
                }
            }
            return true
        }
    }

    fun isMatch(path: List<PathItem>): Boolean {
        for (selector in selectors) {
            if (selectorIsMatch(selector, path)) {
                return true
            }
        }
        return false
    }

    fun redact(value: Content, redaction: Redaction): Content =
        redactImpl(value, redaction, mutableListOf())

    private fun redactSeq(
        seq: List<Content>,
        redaction: Redaction,
        path: MutableList<PathItem>,
    ): List<Content> {
        val len = seq.size.toULong()
        return seq.mapIndexed { idx, item ->
            path.add(PathItem.Index(idx.toULong(), len))
            val res = redactImpl(item, redaction, path)
            path.removeAt(path.size - 1)
            res
        }
    }

    private fun redactStruct(
        fields: List<Content.Field>,
        redaction: Redaction,
        path: MutableList<PathItem>,
    ): List<Content.Field> =
        fields.map { field ->
            path.add(PathItem.Field(field.name))
            val res = redactImpl(field.value, redaction, path)
            path.removeAt(path.size - 1)
            Content.Field(field.name, res)
        }

    private fun redactImpl(
        value: Content,
        redaction: Redaction,
        path: MutableList<PathItem>,
    ): Content {
        if (isMatch(path)) {
            return redaction.redact(value, path)
        }
        return when (value) {
            is Content.Map -> {
                Content.Map(
                    value.value.map { entry ->
                        path.add(PathItem.Field("\$key"))
                        val newKey = redactImpl(entry.key, redaction, path)
                        path.removeAt(path.size - 1)

                        path.add(PathItem.ContentItem(entry.key))
                        val newVal = redactImpl(entry.value, redaction, path)
                        path.removeAt(path.size - 1)

                        Content.Entry(newKey, newVal)
                    },
                )
            }

            is Content.Seq -> {
                Content.Seq(redactSeq(value.value, redaction, path))
            }

            is Content.Tuple -> {
                Content.Tuple(redactSeq(value.value, redaction, path))
            }

            is Content.TupleStruct -> {
                Content.TupleStruct(value.name, redactSeq(value.value, redaction, path))
            }

            is Content.TupleVariant -> {
                Content.TupleVariant(
                    value.name,
                    value.index,
                    value.variant,
                    redactSeq(value.value, redaction, path),
                )
            }

            is Content.Struct -> {
                Content.Struct(value.name, redactStruct(value.fields, redaction, path))
            }

            is Content.StructVariant -> {
                Content.StructVariant(
                    value.name,
                    value.index,
                    value.variant,
                    redactStruct(value.fields, redaction, path),
                )
            }

            is Content.NewtypeStruct -> {
                Content.NewtypeStruct(
                    value.name,
                    redactImpl(value.value, redaction, path),
                )
            }

            is Content.NewtypeVariant -> {
                Content.NewtypeVariant(
                    value.name,
                    value.index,
                    value.variant,
                    redactImpl(value.value, redaction, path),
                )
            }

            is Content.Some -> {
                Content.Some(redactImpl(value.value, redaction, path))
            }

            else -> {
                value
            }
        }
    }

    companion object {
        fun parse(selector: kotlin.String): Selector {
            val parts = selector.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val list = parts.map { parseSingle(it) }
            return Selector(list)
        }

        private fun parseSingle(s: kotlin.String): List<Segment> {
            var i = 0
            val segments = mutableListOf<Segment>()
            var haveDeepWildcard = false

            while (i < s.length) {
                val c = s[i]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++
                    continue
                }
                if (c == '.') {
                    if (i + 2 < s.length && s[i + 1] == '*' && s[i + 2] == '*') {
                        if (haveDeepWildcard) {
                            throw SelectorParseException(SelectorParseError("deep wildcard used twice", i))
                        }
                        haveDeepWildcard = true
                        segments.add(Segment.DeepWildcard)
                        i += 3
                    } else if (i + 1 < s.length && s[i + 1] == '*') {
                        segments.add(Segment.Wildcard)
                        i += 2
                    } else if (i + 1 < s.length && (s[i + 1] == '_' || s[i + 1] == '$' || s[i + 1].isLetter())) {
                        val start = i + 1
                        i++
                        while (i < s.length && (s[i] == '_' || s[i] == '$' || s[i].isLetterOrDigit())) {
                            i++
                        }
                        segments.add(Segment.Key(s.substring(start, i)))
                    } else {
                        // identity .
                        i++
                    }
                } else if (c == '[') {
                    val close = s.indexOf(']', i)
                    if (close == -1) {
                        throw SelectorParseException(SelectorParseError("unclosed subscript", i))
                    }
                    val inside = s.substring(i + 1, close).trim()
                    i = close + 1
                    if (inside.isEmpty()) {
                        segments.add(Segment.Range(null, null))
                    } else if (inside.contains(':')) {
                        val colon = inside.indexOf(':')
                        val left = inside.substring(0, colon).trim()
                        val right = inside.substring(colon + 1).trim()
                        val start = if (left.isEmpty()) null else left.toLongOrNull()
                        val end = if (right.isEmpty()) null else right.toLongOrNull()
                        segments.add(Segment.Range(start, end))
                    } else if (inside.startsWith('"') && inside.endsWith('"') && inside.length >= 2) {
                        val unquoted = inside.substring(1, inside.length - 1)
                        segments.add(Segment.Key(unquoted))
                    } else {
                        val idx =
                            inside.toULongOrNull()
                                ?: throw SelectorParseException(SelectorParseError("invalid index: $inside", i))
                        segments.add(Segment.Index(idx))
                    }
                } else {
                    throw SelectorParseException(SelectorParseError("unexpected character: $c", i))
                }
            }
            return segments
        }
    }
}
