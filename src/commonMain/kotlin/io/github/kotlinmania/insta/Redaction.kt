// port-lint: source redaction.rs
package io.github.kotlinmania.insta

import io.github.kotlinmania.insta.content.Content
import kotlin.math.pow
import kotlin.math.round

class SelectorParseError(
    message: kotlin.String,
    private val col: Int = 0,
) : Exception(message) {
    fun column(): Int = col
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
                is PathItem.Field -> sb.append(item.name)
                is PathItem.Index -> sb.append(item.idx.toString())
            }
        }
        return sb.toString()
    }
}

sealed class PathItem {
    data class ContentItem(
        val content: Content,
    ) : PathItem()

    data class Field(
        val name: kotlin.String,
    ) : PathItem()

    data class Index(
        val idx: ULong,
        val len: ULong,
    ) : PathItem()

    fun asStr(): kotlin.String? =
        when (this) {
            is ContentItem -> content.asStr()
            is Field -> name
            is Index -> null
        }

    fun asU64(): ULong? =
        when (this) {
            is ContentItem -> content.asU64()
            is Field -> null
            is Index -> idx
        }

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
            start == null && end == null -> true
            start == null && end != null -> idxVal < expandRange(end, lenVal)
            start != null && end == null -> idxVal >= expandRange(start, lenVal)
            start != null && end != null ->
                idxVal >= expandRange(start, lenVal) && idxVal < expandRange(end, lenVal)
            else -> false
        }
    }
}

sealed class Segment {
    data object DeepWildcard : Segment()

    data object Wildcard : Segment()

    data class Key(
        val key: kotlin.String,
    ) : Segment()

    data class Index(
        val idx: ULong,
    ) : Segment()

    data class Range(
        val start: Long?,
        val end: Long?,
    ) : Segment()
}

/**
 * Replaces a value with another one.
 */
sealed class Redaction {
    data class Static(
        val content: Content,
    ) : Redaction()

    data class Dynamic(
        val callback: (Content, ContentPath) -> Content,
    ) : Redaction()

    companion object {
        fun from(content: Content): Redaction = Static(content)

        fun from(value: kotlin.String): Redaction = Static(Content.from(value))

        fun from(value: Boolean): Redaction = Static(Content.from(value))

        fun from(value: Long): Redaction = Static(Content.from(value))

        fun from(value: Int): Redaction = Static(Content.from(value))

        fun from(value: Double): Redaction = Static(Content.from(value))
    }

    fun redact(value: Content, path: List<PathItem>): Content =
        when (this) {
            is Static -> content
            is Dynamic -> callback(value, ContentPath(path))
        }
}

fun dynamicRedaction(callback: (Content, ContentPath) -> Content): Redaction =
    Redaction.Dynamic(callback)

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
            is Content.Map ->
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
            is Content.Seq -> Content.Seq(redactSeq(value.value, redaction, path))
            is Content.Tuple -> Content.Tuple(redactSeq(value.value, redaction, path))
            is Content.TupleStruct -> Content.TupleStruct(value.name, redactSeq(value.value, redaction, path))
            is Content.TupleVariant ->
                Content.TupleVariant(
                    value.name,
                    value.index,
                    value.variant,
                    redactSeq(value.value, redaction, path),
                )
            is Content.Struct -> Content.Struct(value.name, redactStruct(value.fields, redaction, path))
            is Content.StructVariant ->
                Content.StructVariant(
                    value.name,
                    value.index,
                    value.variant,
                    redactStruct(value.fields, redaction, path),
                )
            is Content.NewtypeStruct ->
                Content.NewtypeStruct(
                    value.name,
                    redactImpl(value.value, redaction, path),
                )
            is Content.NewtypeVariant ->
                Content.NewtypeVariant(
                    value.name,
                    value.index,
                    value.variant,
                    redactImpl(value.value, redaction, path),
                )
            is Content.Some -> Content.Some(redactImpl(value.value, redaction, path))
            else -> value
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
                            throw SelectorParseError("deep wildcard used twice", i)
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
                        throw SelectorParseError("unclosed subscript", i)
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
                        val idx = inside.toULongOrNull() ?: throw SelectorParseError("invalid index: $inside", i)
                        segments.add(Segment.Index(idx))
                    }
                } else {
                    throw SelectorParseError("unexpected character: $c", i)
                }
            }
            return segments
        }
    }
}
