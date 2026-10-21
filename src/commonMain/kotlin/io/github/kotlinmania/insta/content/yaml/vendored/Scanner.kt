// port-lint: source content/yaml/vendored/scanner.rs
package io.github.kotlinmania.insta.content.yaml.vendored

/**
 * Supported character encoding for YAML scanning.
 */
internal enum class TEncoding {
    Utf8,
}

/**
 * Scalar style in YAML tokens.
 */
internal enum class TScalarStyle {
    Plain,
    SingleQuoted,
    DoubleQuoted,
    Literal,
    Foled,
}

/**
 * Position marker in the input source stream.
 */
internal data class Marker(
    var index: Int,
    var line: Int,
    var col: Int,
) {
    companion object {
        fun new(
            index: Int,
            line: Int,
            col: Int,
        ): Marker = Marker(index, line, col)
    }
}

/**
 * Scanning error containing source position and error message.
 */
internal class ScanError(
    val mark: Marker,
    val info: String,
) : Exception("$info at line ${mark.line} column ${mark.col + 1}") {
    override fun toString(): String = "$info at line ${mark.line} column ${mark.col + 1}"
}

/**
 * Token types produced by the YAML scanner.
 */
internal sealed class TokenType {
    data class StreamStart(
        val encoding: TEncoding,
    ) : TokenType()

    data object StreamEnd : TokenType()

    data class VersionDirective(
        val major: Long,
        val minor: Long,
    ) : TokenType()

    data class TagDirective(
        val handle: String,
        val prefix: String,
    ) : TokenType()

    data object DocumentStart : TokenType()

    data object DocumentEnd : TokenType()

    data object BlockSequenceStart : TokenType()

    data object BlockMappingStart : TokenType()

    data object BlockEnd : TokenType()

    data object FlowSequenceStart : TokenType()

    data object FlowSequenceEnd : TokenType()

    data object FlowMappingStart : TokenType()

    data object FlowMappingEnd : TokenType()

    data object BlockEntry : TokenType()

    data object FlowEntry : TokenType()

    data object Key : TokenType()

    data object Value : TokenType()

    data class Alias(
        val value: String,
    ) : TokenType()

    data class Anchor(
        val value: String,
    ) : TokenType()

    data class Tag(
        val handle: String,
        val suffix: String,
    ) : TokenType()

    data class Scalar(
        val style: TScalarStyle,
        val value: String,
    ) : TokenType()
}

/**
 * A token with its marker location in the source stream.
 */
internal data class Token(
    val mark: Marker,
    val tokenType: TokenType,
)

/**
 * Simple key tracking struct.
 */
internal data class SimpleKey(
    var possible: Boolean = false,
    var required: Boolean = false,
    var tokenNumber: Int = 0,
    var mark: Marker,
) {
    companion object {
        fun new(mark: Marker): SimpleKey =
            SimpleKey(
                possible = false,
                required = false,
                tokenNumber = 0,
                mark = mark,
            )
    }
}

private fun isZ(c: Char): Boolean = c == '\u0000'

private fun isBreak(c: Char): Boolean = c == '\n' || c == '\r'

private fun isBreakZ(c: Char): Boolean = isBreak(c) || isZ(c)

private fun isBlank(c: Char): Boolean = c == ' ' || c == '\t'

private fun isBlankZ(c: Char): Boolean = isBlank(c) || isBreakZ(c)

private fun isDigit(c: Char): Boolean = c in '0'..'9'

private fun isAlpha(c: Char): Boolean =
    c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c == '-'

private fun isHex(c: Char): Boolean =
    c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

private fun asHex(c: Char): Long =
    when (c) {
        in '0'..'9' -> (c.code - '0'.code).toLong()
        in 'a'..'f' -> (c.code - 'a'.code + 10).toLong()
        in 'A'..'F' -> (c.code - 'A'.code + 10).toLong()
        else -> error("unreachable")
    }

private fun isFlow(c: Char): Boolean =
    c == ',' || c == '[' || c == ']' || c == '{' || c == '}'

/**
 * YAML tokenizer implementing [Iterator] of [Token].
 */
internal class Scanner internal constructor(
    private val rdr: Iterator<Char>,
) : Iterator<Token> {
    constructor(source: String) : this(source.iterator())

    private val mark: Marker = Marker.new(0, 1, 0)
    private val tokens: ArrayDeque<Token> = ArrayDeque()
    private val buffer: ArrayDeque<Char> = ArrayDeque()
    private var error: ScanError? = null

    private var streamStartProduced: Boolean = false
    private var streamEndProduced: Boolean = false
    private var adjacentValueAllowedAt: Int = 0
    private var simpleKeyAllowed: Boolean = true
    private val simpleKeys: ArrayList<SimpleKey> = ArrayList()
    private var indent: Int = -1
    private val indents: ArrayList<Int> = ArrayList()
    private var flowLevel: Int = 0
    private var tokensParsed: Int = 0
    private var tokenAvailable: Boolean = false

    private var lookaheadToken: Token? = null
    private var lookaheadDone: Boolean = false

    fun getError(): ScanError? = error

    fun streamStarted(): Boolean = streamStartProduced

    fun streamEnded(): Boolean = streamEndProduced

    fun mark(): Marker = mark.copy()

    override fun hasNext(): Boolean {
        if (error != null) return false
        if (!lookaheadDone) {
            try {
                lookaheadToken = nextToken()
                lookaheadDone = true
            } catch (e: ScanError) {
                error = e
                return false
            }
        }
        return lookaheadToken != null
    }

    override fun next(): Token {
        if (!hasNext()) throw NoSuchElementException()
        val tok = lookaheadToken ?: throw NoSuchElementException()
        lookaheadToken = null
        lookaheadDone = false
        return tok
    }

    /**
     * Retrieves the next token from the scanner, throwing [ScanError] on syntax error.
     */
    fun nextToken(): Token? {
        if (lookaheadDone) {
            val tok = lookaheadToken
            lookaheadToken = null
            lookaheadDone = false
            return tok
        }
        if (streamEndProduced) {
            return null
        }
        if (!tokenAvailable) {
            fetchMoreTokens()
        }
        if (tokens.isEmpty()) {
            return null
        }
        val t = tokens.removeFirst()
        tokenAvailable = false
        tokensParsed += 1

        if (t.tokenType is TokenType.StreamEnd) {
            streamEndProduced = true
        }
        return t
    }

    private fun lookahead(count: Int) {
        if (buffer.size >= count) {
            return
        }
        val needed = count - buffer.size
        repeat(needed) {
            if (rdr.hasNext()) {
                buffer.addLast(rdr.next())
            } else {
                buffer.addLast('\u0000')
            }
        }
    }

    private fun skip() {
        val c = buffer.removeFirst()
        mark.index += 1
        if (c == '\n') {
            mark.line += 1
            mark.col = 0
        } else {
            mark.col += 1
        }
    }

    private fun skipLine() {
        if (buffer.size >= 2 && buffer[0] == '\r' && buffer[1] == '\n') {
            skip()
            skip()
        } else if (buffer.isNotEmpty() && isBreak(buffer[0])) {
            skip()
        }
    }

    private fun ch(): Char = buffer[0]

    private fun chIs(c: Char): Boolean = buffer.isNotEmpty() && buffer[0] == c

    private fun readBreak(s: StringBuilder) {
        if (buffer.size >= 2 && buffer[0] == '\r' && buffer[1] == '\n') {
            s.append('\n')
            skip()
            skip()
        } else if (buffer.isNotEmpty() && (buffer[0] == '\r' || buffer[0] == '\n')) {
            s.append('\n')
            skip()
        } else {
            error("unreachable")
        }
    }

    private fun insertToken(
        pos: Int,
        tok: Token,
    ) {
        tokens.add(pos, tok)
    }

    private fun allowSimpleKey() {
        simpleKeyAllowed = true
    }

    private fun disallowSimpleKey() {
        simpleKeyAllowed = false
    }

    fun fetchNextToken() {
        lookahead(1)
        if (!streamStartProduced) {
            fetchStreamStart()
            return
        }
        skipToNextToken()
        staleSimpleKeys()

        val markCopy = mark.copy()
        unrollIndent(markCopy.col)

        lookahead(4)

        if (isZ(ch())) {
            fetchStreamEnd()
            return
        }

        if (mark.col == 0 && chIs('%')) {
            fetchDirective()
            return
        }

        if (mark.col == 0 &&
            buffer[0] == '-' && buffer[1] == '-' && buffer[2] == '-' &&
            isBlankZ(buffer[3])
        ) {
            fetchDocumentIndicator(TokenType.DocumentStart)
            return
        }

        if (mark.col == 0 &&
            buffer[0] == '.' && buffer[1] == '.' && buffer[2] == '.' &&
            isBlankZ(buffer[3])
        ) {
            fetchDocumentIndicator(TokenType.DocumentEnd)
            return
        }

        val c = buffer[0]
        val nc = buffer[1]
        when (c) {
            '[' -> {
                fetchFlowCollectionStart(TokenType.FlowSequenceStart)
            }

            '{' -> {
                fetchFlowCollectionStart(TokenType.FlowMappingStart)
            }

            ']' -> {
                fetchFlowCollectionEnd(TokenType.FlowSequenceEnd)
            }

            '}' -> {
                fetchFlowCollectionEnd(TokenType.FlowMappingEnd)
            }

            ',' -> {
                fetchFlowEntry()
            }

            '-' -> {
                if (isBlankZ(nc)) fetchBlockEntry() else fetchPlainScalar()
            }

            '?' -> {
                if (isBlankZ(nc)) fetchKey() else fetchPlainScalar()
            }

            ':' -> {
                if (isBlankZ(nc) || (flowLevel > 0 && (isFlow(nc) || mark.index == adjacentValueAllowedAt))) {
                    fetchValue()
                } else {
                    fetchPlainScalar()
                }
            }

            '*' -> {
                fetchAnchor(true)
            }

            '&' -> {
                fetchAnchor(false)
            }

            '!' -> {
                fetchTag()
            }

            '|' -> {
                if (flowLevel == 0) fetchBlockScalar(true) else fetchPlainScalar()
            }

            '>' -> {
                if (flowLevel == 0) fetchBlockScalar(false) else fetchPlainScalar()
            }

            '\'' -> {
                fetchFlowScalar(true)
            }

            '"' -> {
                fetchFlowScalar(false)
            }

            '%', '@', '`' -> {
                throw ScanError(mark.copy(), "unexpected character: `$c'")
            }

            else -> {
                fetchPlainScalar()
            }
        }
    }

    fun fetchMoreTokens() {
        var needMore: Boolean
        while (true) {
            needMore = false
            if (tokens.isEmpty()) {
                needMore = true
            } else {
                staleSimpleKeys()
                for (sk in simpleKeys) {
                    if (sk.possible && sk.tokenNumber == tokensParsed) {
                        needMore = true
                        break
                    }
                }
            }
            if (!needMore) {
                break
            }
            fetchNextToken()
        }
        tokenAvailable = true
    }

    private fun staleSimpleKeys() {
        for (sk in simpleKeys) {
            if (sk.possible && (sk.mark.line < mark.line || sk.mark.index + 1024 < mark.index)) {
                if (sk.required) {
                    throw ScanError(mark.copy(), "simple key expect ':'")
                }
                sk.possible = false
            }
        }
    }

    private fun skipToNextToken() {
        while (true) {
            lookahead(1)
            when (ch()) {
                ' ' -> {
                    skip()
                }

                '\t' -> {
                    if (flowLevel > 0 || !simpleKeyAllowed) {
                        skip()
                    } else {
                        break
                    }
                }

                '\n', '\r' -> {
                    lookahead(2)
                    skipLine()
                    if (flowLevel == 0) {
                        allowSimpleKey()
                    }
                }

                '#' -> {
                    while (!isBreakZ(ch())) {
                        skip()
                        lookahead(1)
                    }
                }

                else -> {
                    break
                }
            }
        }
    }

    private fun fetchStreamStart() {
        val startMark = mark.copy()
        indent = -1
        streamStartProduced = true
        allowSimpleKey()
        tokens.addLast(Token(startMark, TokenType.StreamStart(TEncoding.Utf8)))
        simpleKeys.add(SimpleKey.new(Marker.new(0, 0, 0)))
    }

    private fun fetchStreamEnd() {
        if (mark.col != 0) {
            mark.col = 0
            mark.line += 1
        }
        unrollIndent(-1)
        removeSimpleKey()
        disallowSimpleKey()
        tokens.addLast(Token(mark.copy(), TokenType.StreamEnd))
    }

    private fun fetchDirective() {
        unrollIndent(-1)
        removeSimpleKey()
        disallowSimpleKey()
        val tok = scanDirective()
        tokens.addLast(tok)
    }

    private fun scanDirective(): Token {
        val startMark = mark.copy()
        skip()

        val name = scanDirectiveName()
        val tok =
            when (name) {
                "YAML" -> {
                    scanVersionDirectiveValue(startMark)
                }

                "TAG" -> {
                    scanTagDirectiveValue(startMark)
                }

                else -> {
                    lookahead(1)
                    while (!isBreakZ(ch())) {
                        skip()
                        lookahead(1)
                    }
                    Token(startMark, TokenType.TagDirective("", ""))
                }
            }
        lookahead(1)
        while (isBlank(ch())) {
            skip()
            lookahead(1)
        }
        if (ch() == '#') {
            while (!isBreakZ(ch())) {
                skip()
                lookahead(1)
            }
        }
        if (!isBreakZ(ch())) {
            throw ScanError(startMark, "while scanning a directive, did not find expected comment or line break")
        }
        if (isBreak(ch())) {
            lookahead(2)
            skipLine()
        }
        return tok
    }

    private fun scanVersionDirectiveValue(startMark: Marker): Token {
        lookahead(1)
        while (isBlank(ch())) {
            skip()
            lookahead(1)
        }
        val major = scanVersionDirectiveNumber(startMark)
        if (ch() != '.') {
            throw ScanError(startMark, "while scanning a YAML directive, did not find expected digit or '.' character")
        }
        skip()
        val minor = scanVersionDirectiveNumber(startMark)
        return Token(startMark, TokenType.VersionDirective(major, minor))
    }

    private fun scanDirectiveName(): String {
        val startMark = mark.copy()
        val sb = StringBuilder()
        lookahead(1)
        while (isAlpha(ch())) {
            sb.append(ch())
            skip()
            lookahead(1)
        }
        if (sb.isEmpty()) {
            throw ScanError(startMark, "while scanning a directive, could not find expected directive name")
        }
        if (!isBlankZ(ch())) {
            throw ScanError(startMark, "while scanning a directive, found unexpected non-alphabetical character")
        }
        return sb.toString()
    }

    private fun scanVersionDirectiveNumber(startMark: Marker): Long {
        var value = 0L
        var length = 0
        lookahead(1)
        while (isDigit(ch())) {
            if (length + 1 > 9) {
                throw ScanError(startMark, "while scanning a YAML directive, found extremely long version number")
            }
            length += 1
            value = value * 10 + (ch().code - '0'.code).toLong()
            skip()
            lookahead(1)
        }
        if (length == 0) {
            throw ScanError(startMark, "while scanning a YAML directive, did not find expected version number")
        }
        return value
    }

    private fun scanTagDirectiveValue(startMark: Marker): Token {
        lookahead(1)
        while (isBlank(ch())) {
            skip()
            lookahead(1)
        }
        val handle = scanTagHandle(true, startMark)
        lookahead(1)
        while (isBlank(ch())) {
            skip()
            lookahead(1)
        }
        val isSecondary = handle == "!!"
        val prefix = scanTagUri(true, isSecondary, "", startMark)
        lookahead(1)
        if (isBlankZ(ch())) {
            return Token(startMark, TokenType.TagDirective(handle, prefix))
        } else {
            throw ScanError(startMark, "while scanning TAG, did not find expected whitespace or line break")
        }
    }

    private fun fetchTag() {
        saveSimpleKey()
        disallowSimpleKey()
        val tok = scanTag()
        tokens.addLast(tok)
    }

    private fun scanTag(): Token {
        val startMark = mark.copy()
        var handle = ""
        var suffix: String
        var secondary = false

        lookahead(2)
        if (buffer[1] == '<') {
            skip()
            skip()
            suffix = scanTagUri(false, false, "", startMark)
            if (ch() != '>') {
                throw ScanError(startMark, "while scanning a tag, did not find the expected '>'")
            }
            skip()
        } else {
            handle = scanTagHandle(false, startMark)
            if (handle.length >= 2 && handle.startsWith('!') && handle.endsWith('!')) {
                if (handle == "!!") {
                    secondary = true
                }
                suffix = scanTagUri(false, secondary, "", startMark)
            } else {
                suffix = scanTagUri(false, false, handle, startMark)
                handle = "!"
                if (suffix.isEmpty()) {
                    handle = ""
                    suffix = "!"
                }
            }
        }
        lookahead(1)
        if (isBlankZ(ch())) {
            return Token(startMark, TokenType.Tag(handle, suffix))
        } else {
            throw ScanError(startMark, "while scanning a tag, did not find expected whitespace or line break")
        }
    }

    private fun scanTagHandle(
        directive: Boolean,
        startMark: Marker,
    ): String {
        val sb = StringBuilder()
        lookahead(1)
        if (ch() != '!') {
            throw ScanError(startMark, "while scanning a tag, did not find expected '!'")
        }
        sb.append(ch())
        skip()
        lookahead(1)
        while (isAlpha(ch())) {
            sb.append(ch())
            skip()
            lookahead(1)
        }
        if (ch() == '!') {
            sb.append(ch())
            skip()
        } else if (directive && sb.toString() != "!") {
            throw ScanError(startMark, "while parsing a tag directive, did not find expected '!'")
        }
        return sb.toString()
    }

    private fun scanTagUri(
        directive: Boolean,
        isSecondary: Boolean,
        head: String,
        startMark: Marker,
    ): String {
        var length = head.length
        val sb = StringBuilder()
        if (length > 1) {
            sb.append(head.substring(1))
        }
        lookahead(1)
        while (
            when (ch()) {
                ';', '/', '?', ':', '@', '&',
                '=', '+', '$', ',', '.', '!', '~', '*', '\'', '(', ')', '[', ']',
                '%',
                -> true

                else -> isAlpha(ch())
            }
        ) {
            if (ch() == '%') {
                sb.append(scanUriEscapes(directive, startMark))
            } else {
                sb.append(ch())
                skip()
            }
            length += 1
            lookahead(1)
        }
        if (length == 0) {
            throw ScanError(startMark, "while parsing a tag, did not find expected tag URI")
        }
        return sb.toString()
    }

    private fun scanUriEscapes(
        directive: Boolean,
        startMark: Marker,
    ): Char {
        var width = 0
        val bytes = ArrayList<Byte>()
        while (true) {
            lookahead(3)
            if (!(ch() == '%' && isHex(buffer[1]) && isHex(buffer[2]))) {
                throw ScanError(startMark, "while parsing a tag, did not find URI escaped octet")
            }
            val octet = ((asHex(buffer[1]) shl 4) + asHex(buffer[2])).toInt()
            if (width == 0) {
                width =
                    when {
                        (octet and 0x80) == 0x00 -> 1
                        (octet and 0xE0) == 0xC0 -> 2
                        (octet and 0xF0) == 0xE0 -> 3
                        (octet and 0xF8) == 0xF0 -> 4
                        else -> throw ScanError(startMark, "while parsing a tag, found an incorrect leading UTF-8 octet")
                    }
            } else {
                if ((octet and 0xC0) != 0x80) {
                    throw ScanError(startMark, "while parsing a tag, found an incorrect trailing UTF-8 octet")
                }
            }
            bytes.add(octet.toByte())
            skip()
            skip()
            skip()
            width -= 1
            if (width == 0) {
                break
            }
        }
        val s = bytes.toByteArray().decodeToString()
        if (s.isEmpty()) {
            throw ScanError(startMark, "while parsing a tag, found an invalid UTF-8 codepoint")
        }
        return s[0]
    }

    private fun fetchAnchor(alias: Boolean) {
        saveSimpleKey()
        disallowSimpleKey()
        val tok = scanAnchor(alias)
        tokens.addLast(tok)
    }

    private fun scanAnchor(alias: Boolean): Token {
        val sb = StringBuilder()
        val startMark = mark.copy()

        skip()
        lookahead(1)
        while (isAlpha(ch())) {
            sb.append(ch())
            skip()
            lookahead(1)
        }

        val c = ch()
        val invalidTail =
            when {
                isBlankZ(c) -> false
                c == '?' || c == ':' || c == ',' || c == ']' || c == '}' || c == '%' || c == '@' || c == '`' -> false
                else -> true
            }
        if (sb.isEmpty() || invalidTail) {
            throw ScanError(
                startMark,
                "while scanning an anchor or alias, did not find expected alphabetic or numeric character",
            )
        }

        val str = sb.toString()
        return if (alias) {
            Token(startMark, TokenType.Alias(str))
        } else {
            Token(startMark, TokenType.Anchor(str))
        }
    }

    private fun fetchFlowCollectionStart(tok: TokenType) {
        saveSimpleKey()
        increaseFlowLevel()
        allowSimpleKey()
        val startMark = mark.copy()
        skip()
        tokens.addLast(Token(startMark, tok))
    }

    private fun fetchFlowCollectionEnd(tok: TokenType) {
        removeSimpleKey()
        decreaseFlowLevel()
        disallowSimpleKey()
        val startMark = mark.copy()
        skip()
        tokens.addLast(Token(startMark, tok))
    }

    private fun fetchFlowEntry() {
        removeSimpleKey()
        allowSimpleKey()
        val startMark = mark.copy()
        skip()
        tokens.addLast(Token(startMark, TokenType.FlowEntry))
    }

    private fun increaseFlowLevel() {
        simpleKeys.add(SimpleKey.new(Marker.new(0, 0, 0)))
        if (flowLevel >= 255) {
            throw ScanError(mark.copy(), "recursion limit exceeded")
        }
        flowLevel += 1
    }

    private fun decreaseFlowLevel() {
        if (flowLevel > 0) {
            flowLevel -= 1
            simpleKeys.removeLast()
        }
    }

    private fun fetchBlockEntry() {
        if (flowLevel == 0) {
            if (!simpleKeyAllowed) {
                throw ScanError(mark.copy(), "block sequence entries are not allowed in this context")
            }
            val markCopy = mark.copy()
            rollIndent(markCopy.col, null, TokenType.BlockSequenceStart, markCopy)
        } else {
            throw ScanError(mark.copy(), "\"-\" is only valid inside a block")
        }
        removeSimpleKey()
        allowSimpleKey()
        val startMark = mark.copy()
        skip()
        tokens.addLast(Token(startMark, TokenType.BlockEntry))
    }

    private fun fetchDocumentIndicator(t: TokenType) {
        unrollIndent(-1)
        removeSimpleKey()
        disallowSimpleKey()
        val markCopy = mark.copy()
        skip()
        skip()
        skip()
        tokens.addLast(Token(markCopy, t))
    }

    private fun fetchBlockScalar(literal: Boolean) {
        saveSimpleKey()
        allowSimpleKey()
        val tok = scanBlockScalar(literal)
        tokens.addLast(tok)
    }

    private fun scanBlockScalar(literal: Boolean): Token {
        val startMark = mark.copy()
        var chomping = 0
        var increment = 0
        var indent = 0
        var trailingBlank: Boolean
        var leadingBlank = false

        val string = StringBuilder()
        val leadingBreak = StringBuilder()
        val trailingBreaks = StringBuilder()

        skip()
        lookahead(1)

        if (ch() == '+' || ch() == '-') {
            chomping = if (ch() == '+') 1 else -1
            skip()
            lookahead(1)
            if (isDigit(ch())) {
                if (ch() == '0') {
                    throw ScanError(startMark, "while scanning a block scalar, found an indentation indicator equal to 0")
                }
                increment = ch().code - '0'.code
                skip()
            }
        } else if (isDigit(ch())) {
            if (ch() == '0') {
                throw ScanError(startMark, "while scanning a block scalar, found an indentation indicator equal to 0")
            }
            increment = ch().code - '0'.code
            skip()
            lookahead(1)
            if (ch() == '+' || ch() == '-') {
                chomping = if (ch() == '+') 1 else -1
                skip()
            }
        }

        lookahead(1)
        while (isBlank(ch())) {
            skip()
            lookahead(1)
        }
        if (ch() == '#') {
            while (!isBreakZ(ch())) {
                skip()
                lookahead(1)
            }
        }
        if (!isBreakZ(ch())) {
            throw ScanError(startMark, "while scanning a block scalar, did not find expected comment or line break")
        }
        if (isBreak(ch())) {
            lookahead(2)
            skipLine()
        }

        if (increment > 0) {
            indent = if (this.indent >= 0) this.indent + increment else increment
        }

        val indentBox = intArrayOf(indent)
        blockScalarBreaks(indentBox, trailingBreaks)
        indent = indentBox[0]

        lookahead(1)
        val contentStartMark = mark.copy()

        while (mark.col == indent && !isZ(ch())) {
            trailingBlank = isBlank(ch())
            if (!literal && leadingBreak.isNotEmpty() && !leadingBlank && !trailingBlank) {
                if (trailingBreaks.isEmpty()) {
                    string.append(' ')
                }
                leadingBreak.clear()
            } else {
                string.append(leadingBreak)
                leadingBreak.clear()
            }

            string.append(trailingBreaks)
            trailingBreaks.clear()

            leadingBlank = isBlank(ch())

            while (!isBreakZ(ch())) {
                string.append(ch())
                skip()
                lookahead(1)
            }

            if (isZ(ch())) {
                break
            }

            lookahead(2)
            readBreak(leadingBreak)

            indentBox[0] = indent
            blockScalarBreaks(indentBox, trailingBreaks)
            indent = indentBox[0]
        }

        if (chomping != -1) {
            string.append(leadingBreak)
        }
        if (chomping == 1) {
            string.append(trailingBreaks)
        }

        val style = if (literal) TScalarStyle.Literal else TScalarStyle.Foled
        return Token(contentStartMark, TokenType.Scalar(style, string.toString()))
    }

    private fun blockScalarBreaks(
        indentBox: IntArray,
        breaks: StringBuilder,
    ) {
        var maxIndent = 0
        var indent = indentBox[0]
        while (true) {
            lookahead(1)
            while ((indent == 0 || mark.col < indent) && buffer[0] == ' ') {
                skip()
                lookahead(1)
            }
            if (mark.col > maxIndent) {
                maxIndent = mark.col
            }
            if ((indent == 0 || mark.col < indent) && buffer[0] == '\t') {
                throw ScanError(
                    mark.copy(),
                    "while scanning a block scalar, found a tab character where an indentation space is expected",
                )
            }
            if (!isBreak(ch())) {
                break
            }
            lookahead(2)
            readBreak(breaks)
        }
        if (indent == 0) {
            indent = maxIndent
            if (indent < this.indent + 1) {
                indent = this.indent + 1
            }
            if (indent < 1) {
                indent = 1
            }
            indentBox[0] = indent
        }
    }

    private fun fetchFlowScalar(single: Boolean) {
        saveSimpleKey()
        disallowSimpleKey()
        val tok = scanFlowScalar(single)
        adjacentValueAllowedAt = mark.index
        tokens.addLast(tok)
    }

    private fun scanFlowScalar(single: Boolean): Token {
        val startMark = mark.copy()

        val string = StringBuilder()
        val leadingBreak = StringBuilder()
        val trailingBreaks = StringBuilder()
        val whitespaces = StringBuilder()
        var leadingBlanks: Boolean

        skip()

        while (true) {
            lookahead(4)
            if (mark.col == 0 &&
                (
                    ((buffer[0] == '-') && (buffer[1] == '-') && (buffer[2] == '-')) ||
                        ((buffer[0] == '.') && (buffer[1] == '.') && (buffer[2] == '.'))
                ) &&
                isBlankZ(buffer[3])
            ) {
                throw ScanError(startMark, "while scanning a quoted scalar, found unexpected document indicator")
            }
            if (isZ(ch())) {
                throw ScanError(startMark, "while scanning a quoted scalar, found unexpected end of stream")
            }

            lookahead(2)
            leadingBlanks = false

            while (!isBlankZ(ch())) {
                if (ch() == '\'' && buffer[1] == '\'' && single) {
                    string.append('\'')
                    skip()
                    skip()
                } else if (ch() == '\'' && single) {
                    break
                } else if (ch() == '"' && !single) {
                    break
                } else if (ch() == '\\' && !single && isBreak(buffer[1])) {
                    lookahead(3)
                    skip()
                    skipLine()
                    leadingBlanks = true
                    break
                } else if (ch() == '\\' && !single) {
                    var codeLength = 0
                    when (buffer[1]) {
                        '0' -> string.append('\u0000')
                        'a' -> string.append('\u0007')
                        'b' -> string.append('\b')
                        't', '\t' -> string.append('\t')
                        'n' -> string.append('\n')
                        'v' -> string.append('\u000b')
                        'f' -> string.append('\u000c')
                        'r' -> string.append('\r')
                        'e' -> string.append('\u001b')
                        ' ' -> string.append(' ')
                        '"' -> string.append('"')
                        '\'' -> string.append('\'')
                        '\\' -> string.append('\\')
                        'N' -> string.append('\u0085')
                        '_' -> string.append('\u00a0')
                        'L' -> string.append('\u2028')
                        'P' -> string.append('\u2029')
                        'x' -> codeLength = 2
                        'u' -> codeLength = 4
                        'U' -> codeLength = 8
                        else -> throw ScanError(startMark, "while parsing a quoted scalar, found unknown escape character")
                    }
                    skip()
                    skip()
                    if (codeLength > 0) {
                        lookahead(codeLength)
                        var value = 0L
                        for (i in 0 until codeLength) {
                            if (!isHex(buffer[i])) {
                                throw ScanError(startMark, "while parsing a quoted scalar, did not find expected hexadecimal number")
                            }
                            value = (value shl 4) + asHex(buffer[i])
                        }
                        if (value <= 0xFFFFL) {
                            string.append(value.toInt().toChar())
                        } else {
                            val v = value.toInt() - 0x10000
                            string.append(((v ushr 10) + 0xD800).toChar())
                            string.append(((v and 0x3FF) + 0xDC00).toChar())
                        }
                        repeat(codeLength) {
                            skip()
                        }
                    }
                } else {
                    string.append(ch())
                    skip()
                }
                lookahead(2)
            }

            lookahead(1)
            if (ch() == '\'' && single) break
            if (ch() == '"' && !single) break

            while (isBlank(ch()) || isBreak(ch())) {
                if (isBlank(ch())) {
                    if (leadingBlanks) {
                        skip()
                    } else {
                        whitespaces.append(ch())
                        skip()
                    }
                } else {
                    lookahead(2)
                    if (leadingBlanks) {
                        readBreak(trailingBreaks)
                    } else {
                        whitespaces.clear()
                        readBreak(leadingBreak)
                        leadingBlanks = true
                    }
                }
                lookahead(1)
            }

            if (leadingBlanks) {
                if (leadingBreak.isEmpty()) {
                    string.append(leadingBreak)
                    string.append(trailingBreaks)
                    trailingBreaks.clear()
                    leadingBreak.clear()
                } else {
                    if (trailingBreaks.isEmpty()) {
                        string.append(' ')
                    } else {
                        string.append(trailingBreaks)
                        trailingBreaks.clear()
                    }
                    leadingBreak.clear()
                }
            } else {
                string.append(whitespaces)
                whitespaces.clear()
            }
        }

        skip()

        val style = if (single) TScalarStyle.SingleQuoted else TScalarStyle.DoubleQuoted
        return Token(startMark, TokenType.Scalar(style, string.toString()))
    }

    private fun fetchPlainScalar() {
        saveSimpleKey()
        disallowSimpleKey()
        val tok = scanPlainScalar()
        tokens.addLast(tok)
    }

    private fun scanPlainScalar(): Token {
        val indent = this.indent + 1
        val startMark = mark.copy()

        val string = StringBuilder()
        val leadingBreak = StringBuilder()
        val trailingBreaks = StringBuilder()
        val whitespaces = StringBuilder()
        var leadingBlanks = false

        while (true) {
            lookahead(4)
            if (mark.col == 0 &&
                (
                    ((buffer[0] == '-') && (buffer[1] == '-') && (buffer[2] == '-')) ||
                        ((buffer[0] == '.') && (buffer[1] == '.') && (buffer[2] == '.'))
                ) &&
                isBlankZ(buffer[3])
            ) {
                break
            }
            if (ch() == '#') {
                break
            }
            while (!isBlankZ(ch())) {
                if (ch() == ':' && (isBlankZ(buffer[1]) || (flowLevel > 0 && isFlow(buffer[1])))) {
                    break
                }
                if ((ch() == ',' || ch() == '[' || ch() == ']' || ch() == '{' || ch() == '}') && flowLevel > 0) {
                    break
                }
                if (leadingBlanks || whitespaces.isNotEmpty()) {
                    if (leadingBlanks) {
                        if (leadingBreak.isEmpty()) {
                            string.append(leadingBreak)
                            string.append(trailingBreaks)
                            trailingBreaks.clear()
                            leadingBreak.clear()
                        } else {
                            if (trailingBreaks.isEmpty()) {
                                string.append(' ')
                            } else {
                                string.append(trailingBreaks)
                                trailingBreaks.clear()
                            }
                            leadingBreak.clear()
                        }
                        leadingBlanks = false
                    } else {
                        string.append(whitespaces)
                        whitespaces.clear()
                    }
                }

                string.append(ch())
                skip()
                lookahead(2)
            }

            if (!(isBlank(ch()) || isBreak(ch()))) {
                break
            }
            lookahead(1)

            while (isBlank(ch()) || isBreak(ch())) {
                if (isBlank(ch())) {
                    if (leadingBlanks && mark.col < indent && ch() == '\t') {
                        throw ScanError(startMark, "while scanning a plain scalar, found a tab")
                    }
                    if (leadingBlanks) {
                        skip()
                    } else {
                        whitespaces.append(ch())
                        skip()
                    }
                } else {
                    lookahead(2)
                    if (leadingBlanks) {
                        readBreak(trailingBreaks)
                    } else {
                        whitespaces.clear()
                        readBreak(leadingBreak)
                        leadingBlanks = true
                    }
                }
                lookahead(1)
            }

            if (flowLevel == 0 && mark.col < indent) {
                break
            }
        }

        if (leadingBlanks) {
            allowSimpleKey()
        }

        return Token(startMark, TokenType.Scalar(TScalarStyle.Plain, string.toString()))
    }

    private fun fetchKey() {
        val startMark = mark.copy()
        if (flowLevel == 0) {
            if (!simpleKeyAllowed) {
                throw ScanError(mark.copy(), "mapping keys are not allowed in this context")
            }
            rollIndent(startMark.col, null, TokenType.BlockMappingStart, startMark)
        }
        removeSimpleKey()
        if (flowLevel == 0) {
            allowSimpleKey()
        } else {
            disallowSimpleKey()
        }
        skip()
        tokens.addLast(Token(startMark, TokenType.Key))
    }

    private fun fetchValue() {
        val sk = simpleKeys.last().copy()
        val startMark = mark.copy()
        if (sk.possible) {
            val tok = Token(sk.mark, TokenType.Key)
            insertToken(sk.tokenNumber - tokensParsed, tok)
            rollIndent(sk.mark.col, sk.tokenNumber, TokenType.BlockMappingStart, startMark)
            simpleKeys.last().possible = false
            disallowSimpleKey()
        } else {
            if (flowLevel == 0) {
                if (!simpleKeyAllowed) {
                    throw ScanError(startMark, "mapping values are not allowed in this context")
                }
                rollIndent(startMark.col, null, TokenType.BlockMappingStart, startMark)
            }
            if (flowLevel == 0) {
                allowSimpleKey()
            } else {
                disallowSimpleKey()
            }
        }
        skip()
        tokens.addLast(Token(startMark, TokenType.Value))
    }

    private fun rollIndent(
        col: Int,
        number: Int?,
        tok: TokenType,
        mark: Marker,
    ) {
        if (flowLevel > 0) {
            return
        }
        if (indent < col) {
            indents.add(indent)
            indent = col
            if (number != null) {
                insertToken(number - tokensParsed, Token(mark, tok))
            } else {
                tokens.addLast(Token(mark, tok))
            }
        }
    }

    private fun unrollIndent(col: Int) {
        if (flowLevel > 0) {
            return
        }
        while (indent > col) {
            tokens.addLast(Token(mark.copy(), TokenType.BlockEnd))
            indent = indents.removeLast()
        }
    }

    private fun saveSimpleKey() {
        val required = flowLevel > 0 && indent == mark.col
        if (simpleKeyAllowed) {
            val sk = SimpleKey.new(mark.copy())
            sk.possible = true
            sk.required = required
            sk.tokenNumber = tokensParsed + tokens.size
            removeSimpleKey()
            simpleKeys.removeLast()
            simpleKeys.add(sk)
        }
    }

    private fun removeSimpleKey() {
        val last = simpleKeys.last()
        if (last.possible && last.required) {
            throw ScanError(mark.copy(), "simple key expected")
        }
        last.possible = false
    }

    companion object {
        fun new(source: String): Scanner = Scanner(source)

        internal fun new(rdr: Iterator<Char>): Scanner = Scanner(rdr)
    }
}
