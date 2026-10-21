// port-lint: source content/yaml/vendored/parser.rs
package io.github.kotlinmania.insta.content.yaml.vendored

internal enum class State {
    StreamStart,
    ImplicitDocumentStart,
    DocumentStart,
    DocumentContent,
    DocumentEnd,
    BlockNode,
    BlockSequenceFirstEntry,
    BlockSequenceEntry,
    IndentlessSequenceEntry,
    BlockMappingFirstKey,
    BlockMappingKey,
    BlockMappingValue,
    FlowSequenceFirstEntry,
    FlowSequenceEntry,
    FlowSequenceEntryMappingKey,
    FlowSequenceEntryMappingValue,
    FlowSequenceEntryMappingEnd,
    FlowMappingFirstKey,
    FlowMappingKey,
    FlowMappingValue,
    FlowMappingEmptyValue,
    End,
}

/**
 * Event used with the low-level event base parsing API.
 */
internal sealed class Event {
    data object StreamStart : Event()

    data object StreamEnd : Event()

    data object DocumentStart : Event()

    data object DocumentEnd : Event()

    data class Alias(
        val anchorId: Int,
    ) : Event()

    data class Scalar(
        val value: String,
        val style: TScalarStyle,
        val anchorId: Int,
        val tag: TokenType?,
    ) : Event()

    data class SequenceStart(
        val anchorId: Int,
    ) : Event()

    data object SequenceEnd : Event()

    data class MappingStart(
        val anchorId: Int,
    ) : Event()

    data object MappingEnd : Event()

    companion object {
        fun emptyScalar(): Event = Event.Scalar("~", TScalarStyle.Plain, 0, null)

        fun emptyScalarWithAnchor(
            anchor: Int,
            tag: TokenType?,
        ): Event = Event.Scalar("", TScalarStyle.Plain, anchor, tag)
    }
}

/**
 * Callback receiver for parsing events.
 */
internal fun interface EventReceiver {
    fun onEvent(ev: Event)
}

/**
 * Callback receiver for parsing events with position markers.
 */
internal fun interface MarkedEventReceiver {
    fun onEvent(
        ev: Event,
        mark: Marker,
    )
}

/**
 * YAML parser producing a stream of [Event]s from a character stream.
 */
internal class Parser internal constructor(
    src: Iterator<Char>,
) {
    constructor(source: String) : this(source.iterator())

    private val scanner: Scanner = Scanner.new(src)
    private val states: ArrayList<State> = ArrayList()
    private var state: State = State.StreamStart
    private var token: Token? = null
    private var current: Pair<Event, Marker>? = null
    private val anchors: HashMap<String, Int> = HashMap()
    private var anchorId: Int = 1

    fun next(): Pair<Event, Marker> {
        val curr = current
        return if (curr == null) {
            parse()
        } else {
            current = null
            curr
        }
    }

    private fun peekToken(): Token {
        if (token == null) {
            token = scanNextToken()
        }
        return token ?: error("unexpected null token")
    }

    private fun scanNextToken(): Token {
        val t = scanner.nextToken()
        return when {
            t == null -> {
                val err = scanner.getError()
                if (err != null) {
                    throw err
                } else {
                    throw ScanError(scanner.mark(), "unexpected eof")
                }
            }

            else -> {
                t
            }
        }
    }

    private fun fetchToken(): Token {
        val t = token ?: error("fetch_token needs to be preceded by peek_token")
        token = null
        return t
    }

    private fun skip() {
        token = null
    }

    private fun popState() {
        state = states.removeLast()
    }

    private fun pushState(s: State) {
        states.add(s)
    }

    private fun parse(): Pair<Event, Marker> {
        if (state == State.End) {
            return Pair(Event.StreamEnd, scanner.mark())
        }
        return stateMachine()
    }

    fun <R : MarkedEventReceiver> load(
        recv: R,
        multi: Boolean,
    ) {
        if (!scanner.streamStarted()) {
            val (ev, mark) = next()
            check(ev == Event.StreamStart) { "expected StreamStart, got $ev" }
            recv.onEvent(ev, mark)
        }

        if (scanner.streamEnded()) {
            recv.onEvent(Event.StreamEnd, scanner.mark())
            return
        }

        while (true) {
            val (ev, mark) = next()
            if (ev == Event.StreamEnd) {
                recv.onEvent(ev, mark)
                return
            }
            anchors.clear()
            loadDocument(ev, mark, recv)
            if (!multi) {
                break
            }
        }
    }

    private fun <R : MarkedEventReceiver> loadDocument(
        firstEv: Event,
        mark: Marker,
        recv: R,
    ) {
        check(firstEv == Event.DocumentStart) { "expected DocumentStart, got $firstEv" }
        recv.onEvent(firstEv, mark)

        val (ev, nodeMark) = next()
        loadNode(ev, nodeMark, recv)

        val (endEv, endMark) = next()
        check(endEv == Event.DocumentEnd) { "expected DocumentEnd, got $endEv" }
        recv.onEvent(endEv, endMark)
    }

    private fun <R : MarkedEventReceiver> loadNode(
        firstEv: Event,
        mark: Marker,
        recv: R,
    ) {
        when (firstEv) {
            is Event.Alias, is Event.Scalar -> {
                recv.onEvent(firstEv, mark)
            }

            is Event.SequenceStart -> {
                recv.onEvent(firstEv, mark)
                loadSequence(recv)
            }

            is Event.MappingStart -> {
                recv.onEvent(firstEv, mark)
                loadMapping(recv)
            }

            else -> {
                error("UNREACHABLE EVENT: $firstEv")
            }
        }
    }

    private fun <R : MarkedEventReceiver> loadMapping(recv: R) {
        var (keyEv, keyMark) = next()
        while (keyEv != Event.MappingEnd) {
            loadNode(keyEv, keyMark, recv)
            val (valEv, valMark) = next()
            loadNode(valEv, valMark, recv)
            val (nextEv, nextMark) = next()
            keyEv = nextEv
            keyMark = nextMark
        }
        recv.onEvent(keyEv, keyMark)
    }

    private fun <R : MarkedEventReceiver> loadSequence(recv: R) {
        var (ev, mark) = next()
        while (ev != Event.SequenceEnd) {
            loadNode(ev, mark, recv)
            val (nextEv, nextMark) = next()
            ev = nextEv
            mark = nextMark
        }
        recv.onEvent(ev, mark)
    }

    private fun stateMachine(): Pair<Event, Marker> =
        when (state) {
            State.StreamStart -> streamStart()
            State.ImplicitDocumentStart -> documentStart(true)
            State.DocumentStart -> documentStart(false)
            State.DocumentContent -> documentContent()
            State.DocumentEnd -> documentEnd()
            State.BlockNode -> parseNode(block = true, indentlessSequence = false)
            State.BlockMappingFirstKey -> blockMappingKey(true)
            State.BlockMappingKey -> blockMappingKey(false)
            State.BlockMappingValue -> blockMappingValue()
            State.BlockSequenceFirstEntry -> blockSequenceEntry(true)
            State.BlockSequenceEntry -> blockSequenceEntry(false)
            State.FlowSequenceFirstEntry -> flowSequenceEntry(true)
            State.FlowSequenceEntry -> flowSequenceEntry(false)
            State.FlowMappingFirstKey -> flowMappingKey(true)
            State.FlowMappingKey -> flowMappingKey(false)
            State.FlowMappingValue -> flowMappingValue(false)
            State.IndentlessSequenceEntry -> indentlessSequenceEntry()
            State.FlowSequenceEntryMappingKey -> flowSequenceEntryMappingKey()
            State.FlowSequenceEntryMappingValue -> flowSequenceEntryMappingValue()
            State.FlowSequenceEntryMappingEnd -> flowSequenceEntryMappingEnd()
            State.FlowMappingEmptyValue -> flowMappingValue(true)
            State.End -> error("unreachable")
        }

    private fun streamStart(): Pair<Event, Marker> {
        val tok = peekToken()
        return when (tok.tokenType) {
            is TokenType.StreamStart -> {
                state = State.ImplicitDocumentStart
                skip()
                Pair(Event.StreamStart, tok.mark)
            }

            else -> {
                throw ScanError(tok.mark, "did not find expected <stream-start>")
            }
        }
    }

    private fun documentStart(implicit: Boolean): Pair<Event, Marker> {
        if (!implicit) {
            while (peekToken().tokenType is TokenType.DocumentEnd) {
                skip()
            }
        }
        val tok = peekToken()
        return when (tok.tokenType) {
            is TokenType.StreamEnd -> {
                state = State.End
                skip()
                Pair(Event.StreamEnd, tok.mark)
            }

            is TokenType.VersionDirective, is TokenType.TagDirective, is TokenType.DocumentStart -> {
                explicitDocumentStart()
            }

            else -> {
                if (implicit) {
                    parserProcessDirectives()
                    pushState(State.DocumentEnd)
                    state = State.BlockNode
                    Pair(Event.DocumentStart, tok.mark)
                } else {
                    explicitDocumentStart()
                }
            }
        }
    }

    private fun parserProcessDirectives() {
        while (true) {
            when (peekToken().tokenType) {
                is TokenType.VersionDirective, is TokenType.TagDirective -> skip()
                else -> break
            }
        }
    }

    private fun explicitDocumentStart(): Pair<Event, Marker> {
        parserProcessDirectives()
        val tok = peekToken()
        return when (tok.tokenType) {
            is TokenType.DocumentStart -> {
                pushState(State.DocumentEnd)
                state = State.DocumentContent
                skip()
                Pair(Event.DocumentStart, tok.mark)
            }

            else -> {
                throw ScanError(tok.mark, "did not find expected <document start>")
            }
        }
    }

    private fun documentContent(): Pair<Event, Marker> {
        val tok = peekToken()
        return when (tok.tokenType) {
            is TokenType.VersionDirective, is TokenType.TagDirective,
            is TokenType.DocumentStart, is TokenType.DocumentEnd, is TokenType.StreamEnd,
            -> {
                popState()
                Pair(Event.emptyScalar(), tok.mark)
            }

            else -> {
                parseNode(block = true, indentlessSequence = false)
            }
        }
    }

    private fun documentEnd(): Pair<Event, Marker> {
        val tok = peekToken()
        val marker =
            when (tok.tokenType) {
                is TokenType.DocumentEnd -> {
                    skip()
                    tok.mark
                }

                else -> {
                    tok.mark
                }
            }
        state = State.DocumentStart
        return Pair(Event.DocumentEnd, marker)
    }

    private fun registerAnchor(
        name: String,
        mark: Marker,
    ): Int {
        val newId = anchorId
        anchorId += 1
        anchors[name] = newId
        return newId
    }

    private fun parseNode(
        block: Boolean,
        indentlessSequence: Boolean,
    ): Pair<Event, Marker> {
        var currentAnchorId = 0
        var tag: TokenType? = null
        val peeked = peekToken()
        when (peeked.tokenType) {
            is TokenType.Alias -> {
                popState()
                val tok = fetchToken()
                val alias = tok.tokenType as TokenType.Alias
                val id =
                    anchors[alias.value]
                        ?: throw ScanError(tok.mark, "while parsing node, found unknown anchor")
                return Pair(Event.Alias(id), tok.mark)
            }

            is TokenType.Anchor -> {
                val tok = fetchToken()
                val anchor = tok.tokenType as TokenType.Anchor
                currentAnchorId = registerAnchor(anchor.value, tok.mark)
                if (peekToken().tokenType is TokenType.Tag) {
                    tag = fetchToken().tokenType
                }
            }

            is TokenType.Tag -> {
                tag = fetchToken().tokenType
                if (peekToken().tokenType is TokenType.Anchor) {
                    val tok = fetchToken()
                    val anchor = tok.tokenType as TokenType.Anchor
                    currentAnchorId = registerAnchor(anchor.value, tok.mark)
                }
            }

            else -> {}
        }

        val tok = peekToken()
        val tokType = tok.tokenType
        return when {
            tokType is TokenType.BlockEntry && indentlessSequence -> {
                state = State.IndentlessSequenceEntry
                Pair(Event.SequenceStart(currentAnchorId), tok.mark)
            }

            tokType is TokenType.Scalar -> {
                popState()
                val fetched = fetchToken()
                val scalar = fetched.tokenType as TokenType.Scalar
                Pair(Event.Scalar(scalar.value, scalar.style, currentAnchorId, tag), fetched.mark)
            }

            tokType is TokenType.FlowSequenceStart -> {
                state = State.FlowSequenceFirstEntry
                Pair(Event.SequenceStart(currentAnchorId), tok.mark)
            }

            tokType is TokenType.FlowMappingStart -> {
                state = State.FlowMappingFirstKey
                Pair(Event.MappingStart(currentAnchorId), tok.mark)
            }

            tokType is TokenType.BlockSequenceStart && block -> {
                state = State.BlockSequenceFirstEntry
                Pair(Event.SequenceStart(currentAnchorId), tok.mark)
            }

            tokType is TokenType.BlockMappingStart && block -> {
                state = State.BlockMappingFirstKey
                Pair(Event.MappingStart(currentAnchorId), tok.mark)
            }

            else -> {
                if (tag != null || currentAnchorId > 0) {
                    popState()
                    Pair(Event.emptyScalarWithAnchor(currentAnchorId, tag), tok.mark)
                } else {
                    throw ScanError(tok.mark, "while parsing a node, did not find expected node content")
                }
            }
        }
    }

    private fun blockMappingKey(first: Boolean): Pair<Event, Marker> {
        if (first) {
            peekToken()
            skip()
        }
        val tok = peekToken()
        return when (tok.tokenType) {
            is TokenType.Key -> {
                skip()
                val nextTok = peekToken()
                when (nextTok.tokenType) {
                    is TokenType.Key, is TokenType.Value, is TokenType.BlockEnd -> {
                        state = State.BlockMappingValue
                        Pair(Event.emptyScalar(), nextTok.mark)
                    }

                    else -> {
                        pushState(State.BlockMappingValue)
                        parseNode(block = true, indentlessSequence = true)
                    }
                }
            }

            is TokenType.Value -> {
                state = State.BlockMappingValue
                Pair(Event.emptyScalar(), tok.mark)
            }

            is TokenType.BlockEnd -> {
                popState()
                skip()
                Pair(Event.MappingEnd, tok.mark)
            }

            else -> {
                throw ScanError(tok.mark, "while parsing a block mapping, did not find expected key")
            }
        }
    }

    private fun blockMappingValue(): Pair<Event, Marker> {
        val tok = peekToken()
        return when (tok.tokenType) {
            is TokenType.Value -> {
                skip()
                val nextTok = peekToken()
                when (nextTok.tokenType) {
                    is TokenType.Key, is TokenType.Value, is TokenType.BlockEnd -> {
                        state = State.BlockMappingKey
                        Pair(Event.emptyScalar(), nextTok.mark)
                    }

                    else -> {
                        pushState(State.BlockMappingKey)
                        parseNode(block = true, indentlessSequence = true)
                    }
                }
            }

            else -> {
                state = State.BlockMappingKey
                Pair(Event.emptyScalar(), tok.mark)
            }
        }
    }

    private fun flowMappingKey(first: Boolean): Pair<Event, Marker> {
        if (first) {
            peekToken()
            skip()
        }
        var tok = peekToken()
        val marker: Marker =
            if (tok.tokenType is TokenType.FlowMappingEnd) {
                tok.mark
            } else {
                if (!first) {
                    when (tok.tokenType) {
                        is TokenType.FlowEntry -> skip()
                        else -> throw ScanError(tok.mark, "while parsing a flow mapping, did not find expected ',' or '}'")
                    }
                }
                tok = peekToken()
                when (tok.tokenType) {
                    is TokenType.Key -> {
                        skip()
                        val nextTok = peekToken()
                        when (nextTok.tokenType) {
                            is TokenType.Value, is TokenType.FlowEntry, is TokenType.FlowMappingEnd -> {
                                state = State.FlowMappingValue
                                return Pair(Event.emptyScalar(), nextTok.mark)
                            }

                            else -> {
                                pushState(State.FlowMappingValue)
                                return parseNode(block = false, indentlessSequence = false)
                            }
                        }
                    }

                    is TokenType.Value -> {
                        state = State.FlowMappingValue
                        return Pair(Event.emptyScalar(), tok.mark)
                    }

                    is TokenType.FlowMappingEnd -> {
                        tok.mark
                    }

                    else -> {
                        pushState(State.FlowMappingEmptyValue)
                        return parseNode(block = false, indentlessSequence = false)
                    }
                }
            }
        popState()
        skip()
        return Pair(Event.MappingEnd, marker)
    }

    private fun flowMappingValue(empty: Boolean): Pair<Event, Marker> {
        val mark: Marker =
            if (empty) {
                val tok = peekToken()
                state = State.FlowMappingKey
                return Pair(Event.emptyScalar(), tok.mark)
            } else {
                val tok = peekToken()
                when (tok.tokenType) {
                    is TokenType.Value -> {
                        skip()
                        when (peekToken().tokenType) {
                            is TokenType.FlowEntry, is TokenType.FlowMappingEnd -> {}

                            else -> {
                                pushState(State.FlowMappingKey)
                                return parseNode(block = false, indentlessSequence = false)
                            }
                        }
                        tok.mark
                    }

                    else -> {
                        tok.mark
                    }
                }
            }
        state = State.FlowMappingKey
        return Pair(Event.emptyScalar(), mark)
    }

    private fun flowSequenceEntry(first: Boolean): Pair<Event, Marker> {
        if (first) {
            peekToken()
            skip()
        }
        var tok = peekToken()
        when (tok.tokenType) {
            is TokenType.FlowSequenceEnd -> {
                popState()
                skip()
                return Pair(Event.SequenceEnd, tok.mark)
            }

            is TokenType.FlowEntry -> {
                if (!first) {
                    skip()
                }
            }

            else -> {
                if (!first) {
                    throw ScanError(tok.mark, "while parsing a flow sequence, expected ',' or ']'")
                }
            }
        }
        tok = peekToken()
        return when (tok.tokenType) {
            is TokenType.FlowSequenceEnd -> {
                popState()
                skip()
                Pair(Event.SequenceEnd, tok.mark)
            }

            is TokenType.Key -> {
                state = State.FlowSequenceEntryMappingKey
                skip()
                Pair(Event.MappingStart(0), tok.mark)
            }

            else -> {
                pushState(State.FlowSequenceEntry)
                parseNode(block = false, indentlessSequence = false)
            }
        }
    }

    private fun indentlessSequenceEntry(): Pair<Event, Marker> {
        val tok = peekToken()
        when (tok.tokenType) {
            is TokenType.BlockEntry -> {}

            else -> {
                popState()
                return Pair(Event.SequenceEnd, tok.mark)
            }
        }
        skip()
        val nextTok = peekToken()
        return when (nextTok.tokenType) {
            is TokenType.BlockEntry, is TokenType.Key, is TokenType.Value, is TokenType.BlockEnd -> {
                state = State.IndentlessSequenceEntry
                Pair(Event.emptyScalar(), nextTok.mark)
            }

            else -> {
                pushState(State.IndentlessSequenceEntry)
                parseNode(block = true, indentlessSequence = false)
            }
        }
    }

    private fun blockSequenceEntry(first: Boolean): Pair<Event, Marker> {
        if (first) {
            peekToken()
            skip()
        }
        val tok = peekToken()
        return when (tok.tokenType) {
            is TokenType.BlockEnd -> {
                popState()
                skip()
                Pair(Event.SequenceEnd, tok.mark)
            }

            is TokenType.BlockEntry -> {
                skip()
                val nextTok = peekToken()
                when (nextTok.tokenType) {
                    is TokenType.BlockEntry, is TokenType.BlockEnd -> {
                        state = State.BlockSequenceEntry
                        Pair(Event.emptyScalar(), nextTok.mark)
                    }

                    else -> {
                        pushState(State.BlockSequenceEntry)
                        parseNode(block = true, indentlessSequence = false)
                    }
                }
            }

            else -> {
                throw ScanError(tok.mark, "while parsing a block collection, did not find expected '-' indicator")
            }
        }
    }

    private fun flowSequenceEntryMappingKey(): Pair<Event, Marker> {
        val tok = peekToken()
        return when (tok.tokenType) {
            is TokenType.Value, is TokenType.FlowEntry, is TokenType.FlowSequenceEnd -> {
                skip()
                state = State.FlowSequenceEntryMappingValue
                Pair(Event.emptyScalar(), tok.mark)
            }

            else -> {
                pushState(State.FlowSequenceEntryMappingValue)
                parseNode(block = false, indentlessSequence = false)
            }
        }
    }

    private fun flowSequenceEntryMappingValue(): Pair<Event, Marker> {
        val tok = peekToken()
        return when (tok.tokenType) {
            is TokenType.Value -> {
                skip()
                state = State.FlowSequenceEntryMappingValue
                val nextTok = peekToken()
                when (nextTok.tokenType) {
                    is TokenType.FlowEntry, is TokenType.FlowSequenceEnd -> {
                        state = State.FlowSequenceEntryMappingEnd
                        Pair(Event.emptyScalar(), nextTok.mark)
                    }

                    else -> {
                        pushState(State.FlowSequenceEntryMappingEnd)
                        parseNode(block = false, indentlessSequence = false)
                    }
                }
            }

            else -> {
                state = State.FlowSequenceEntryMappingEnd
                Pair(Event.emptyScalar(), tok.mark)
            }
        }
    }

    private fun flowSequenceEntryMappingEnd(): Pair<Event, Marker> {
        state = State.FlowSequenceEntry
        return Pair(Event.MappingEnd, scanner.mark())
    }

    companion object {
        fun new(source: String): Parser = Parser(source)

        internal fun new(src: Iterator<Char>): Parser = Parser(src)
    }
}
