package ai.opencode.feature.session

/*
 * Emulador de terminal (estilo xterm): pantalla de celdas, historial, pantalla alterna,
 * regiones de scroll, colores de 256 y truecolor. Todo corre en el hilo principal.
 */

internal const val VT_BOLD = 1
internal const val VT_FAINT = 2
internal const val VT_ITALIC = 4
internal const val VT_UNDERLINE = 8
internal const val VT_INVERSE = 16
internal const val VT_INVISIBLE = 32
internal const val VT_STRIKE = 64

private val VT_ANSI = intArrayOf(
    0xFF000000.toInt(), 0xFFCD0000.toInt(), 0xFF00CD00.toInt(), 0xFFCDCD00.toInt(),
    0xFF6495ED.toInt(), 0xFFCD00CD.toInt(), 0xFF00CDCD.toInt(), 0xFFE5E5E5.toInt(),
    0xFF7F7F7F.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFFFFFF00.toInt(),
    0xFF5C5CFF.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(), 0xFFFFFFFF.toInt(),
)

private val VT_ACS = intArrayOf(
    0x20, 0x25C6, 0x2592, 0x2409, 0x240C, 0x240D, 0x240A, 0x00B0,
    0x00B1, 0x2424, 0x240B, 0x2518, 0x2510, 0x250C, 0x2514, 0x253C,
    0x23BA, 0x23BB, 0x2500, 0x23BC, 0x23BD, 0x251C, 0x2524, 0x2534,
    0x252C, 0x2502, 0x2264, 0x2265, 0x03C0, 0x2260, 0x00A3, 0x00B7,
)

private fun vt256(value: Int): Int {
    val i = value.coerceIn(0, 255)
    if (i < 16) return VT_ANSI[i]
    if (i < 232) {
        val v = i - 16
        val r = v / 36
        val g = (v / 6) % 6
        val b = v % 6
        val lr = if (r == 0) 0 else 55 + r * 40
        val lg = if (g == 0) 0 else 55 + g * 40
        val lb = if (b == 0) 0 else 55 + b * 40
        return (0xFF shl 24) or (lr shl 16) or (lg shl 8) or lb
    }
    val gray = 8 + (i - 232) * 10
    return (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
}

private fun vtWidth(cp: Int): Int {
    if (cp < 0x300) return 1
    if (cp in 0x0300..0x036F || cp in 0x200B..0x200F || cp in 0xFE00..0xFE0F || cp == 0x20E3) return 0
    if (cp in 0x1100..0x115F || cp in 0x2E80..0xA4CF || cp in 0xAC00..0xD7A3 ||
        cp in 0xF900..0xFAFF || cp in 0xFE30..0xFE6F || cp in 0xFF00..0xFF60 ||
        cp in 0xFFE0..0xFFE6 || cp in 0x1F300..0x1F6FF || cp in 0x1F900..0x1F9FF ||
        cp in 0x20000..0x3FFFD
    ) return 2
    return 1
}

/** Una fila de la pantalla. cp: 0 = vacio, -1 = segunda mitad de un caracter ancho. */
internal class VtLine(cols: Int) {
    var cp = IntArray(cols)
    var fg = IntArray(cols)
    var bg = IntArray(cols)
    var at = IntArray(cols)
    var wrapped = false

    fun erase(from: Int, to: Int, bgColor: Int) {
        val end = minOf(to, cp.size)
        var i = maxOf(from, 0)
        while (i < end) {
            cp[i] = 0
            fg[i] = 0
            bg[i] = bgColor
            at[i] = 0
            i++
        }
    }

    fun copyCell(from: Int, to: Int) {
        cp[to] = cp[from]
        fg[to] = fg[from]
        bg[to] = bg[from]
        at[to] = at[from]
    }

    fun isBlank(): Boolean {
        for (i in cp.indices) if (cp[i] != 0 || bg[i] != 0) return false
        return true
    }

    fun resized(cols: Int): VtLine {
        val n = VtLine(cols)
        val m = minOf(cols, cp.size)
        System.arraycopy(cp, 0, n.cp, 0, m)
        System.arraycopy(fg, 0, n.fg, 0, m)
        System.arraycopy(bg, 0, n.bg, 0, m)
        System.arraycopy(at, 0, n.at, 0, m)
        n.wrapped = wrapped
        return n
    }
}

internal class VtTerminal(cols0: Int, rows0: Int) {
    var cols = cols0
        private set
    var rows = rows0
        private set
    var cx = 0
        private set
    var cy = 0
        private set
    var cursorVisible = true
        private set
    var appCursor = false
        private set
    var bracketedPaste = false
        private set
    var mouseMode = 0
        private set
    var mouseSgr = false
        private set
    var altActive = false
        private set
    var trimmed = 0
        private set

    /** Lineas que el usuario subio al mirar el historial (0 = pegado al final). */
    var scrollBack = 0

    /** Respuestas que el programa espera (posicion del cursor, etc.). */
    var respond: (String) -> Unit = {}
    var onChange: (() -> Unit)? = null

    private val maxScroll = 3000
    private val main = ArrayDeque<VtLine>()
    private var alt: Array<VtLine>? = null
    private var top = 0
    private var bottom = 0
    private var tabs = BooleanArray(0)
    private var autoWrap = true
    private var origin = false
    private var insertMode = false
    private var pendingWrap = false
    private var lineDraw = false
    private var lastCp = 32

    private var curFg = 0
    private var curBg = 0
    private var curAt = 0
    private var sCx = 0
    private var sCy = 0
    private var sFg = 0
    private var sBg = 0
    private var sAt = 0
    private var mCx = 0
    private var mCy = 0
    private var mFg = 0
    private var mBg = 0
    private var mAt = 0

    private var state = ST_GROUND
    private val csiBuf = StringBuilder()
    private val oscBuf = StringBuilder()
    private var oscBel = false
    private var pendingHigh = 0
    private var charsetSlot = '('

    init {
        for (i in 0 until rows) main.addLast(VtLine(cols))
        tabs = BooleanArray(cols) { it % 8 == 0 }
        bottom = rows - 1
    }

    // ---------- acceso para dibujar ----------

    fun maxScrollBack(): Int = if (altActive) 0 else maxOf(0, main.size - rows)

    private fun viewIndex(r: Int): Int = if (altActive) r else main.size - rows - scrollBack + r

    fun absIndex(r: Int): Int = if (altActive) r else viewIndex(r) + trimmed

    fun lineAtView(r: Int): VtLine? {
        if (altActive) return alt?.getOrNull(r)
        return main.getOrNull(viewIndex(r))
    }

    fun lineAbs(abs: Int): VtLine? {
        if (altActive) return alt?.getOrNull(abs)
        return main.getOrNull(abs - trimmed)
    }

    fun firstAbs(): Int = if (altActive) 0 else trimmed

    fun lastAbs(): Int = if (altActive) rows - 1 else trimmed + main.size - 1

    fun viewRowOfAbs(abs: Int): Int = abs - absIndex(0)

    fun extract(aAbs: Int, aCol: Int, bAbs: Int, bCol: Int): String {
        val sb = StringBuilder()
        for (abs in aAbs..bAbs) {
            val line = lineAbs(abs) ?: continue
            val from = if (abs == aAbs) aCol else 0
            val to = if (abs == bAbs) bCol else cols - 1
            var end = minOf(to, line.cp.size - 1)
            if (to >= cols - 1 && !line.wrapped) {
                while (end >= from && (line.cp[end] == 0 || line.cp[end] == 32)) end--
            }
            var c = from
            while (c <= end) {
                val cp = line.cp[c]
                if (cp > 0) sb.appendCodePoint(cp) else if (cp == 0) sb.append(' ')
                c++
            }
            if (abs != bAbs && !line.wrapped) sb.append('\n')
        }
        return sb.toString()
    }

    // ---------- pantalla ----------

    private fun scr(r: Int): VtLine {
        if (altActive) return alt!![r]
        return main[main.size - rows + r]
    }

    private fun setScr(r: Int, line: VtLine) {
        if (altActive) alt!![r] = line else main[main.size - rows + r] = line
    }

    private fun newLine(): VtLine {
        val l = VtLine(cols)
        if (curBg != 0) l.erase(0, cols, curBg)
        return l
    }

    private fun scrollUp(n: Int) {
        for (k in 0 until n) {
            if (!altActive && top == 0 && bottom == rows - 1) {
                main.addLast(newLine())
                if (scrollBack > 0) scrollBack++
                if (main.size > maxScroll + rows) {
                    main.removeFirst()
                    trimmed++
                }
            } else {
                for (r in top until bottom) setScr(r, scr(r + 1))
                setScr(bottom, newLine())
            }
        }
        scrollBack = scrollBack.coerceIn(0, maxScrollBack())
    }

    private fun scrollDown(n: Int) {
        for (k in 0 until n) {
            for (r in bottom downTo top + 1) setScr(r, scr(r - 1))
            setScr(top, newLine())
        }
    }

    private fun insertLines(n0: Int) {
        if (cy < top || cy > bottom) return
        val n = minOf(n0, bottom - cy + 1)
        for (r in bottom downTo cy + n) setScr(r, scr(r - n))
        for (r in cy until cy + n) setScr(r, newLine())
        cx = 0
        pendingWrap = false
    }

    private fun deleteLines(n0: Int) {
        if (cy < top || cy > bottom) return
        val n = minOf(n0, bottom - cy + 1)
        for (r in cy..(bottom - n)) setScr(r, scr(r + n))
        for (r in (bottom - n + 1)..bottom) setScr(r, newLine())
        cx = 0
        pendingWrap = false
    }

    private fun insertCells(n0: Int) {
        val line = scr(cy)
        val n = minOf(n0, cols - cx)
        var i = cols - 1
        while (i >= cx + n) {
            line.copyCell(i - n, i)
            i--
        }
        line.erase(cx, cx + n, curBg)
    }

    private fun deleteCells(n0: Int) {
        val line = scr(cy)
        val n = minOf(n0, cols - cx)
        var i = cx
        while (i < cols - n) {
            line.copyCell(i + n, i)
            i++
        }
        line.erase(cols - n, cols, curBg)
    }

    private fun lineFeed() {
        if (cy == bottom) scrollUp(1) else if (cy < rows - 1) cy++
    }

    private fun eraseLine(mode: Int) {
        val l = scr(cy)
        when (mode) {
            0 -> {
                l.erase(cx, cols, curBg)
                l.wrapped = false
            }
            1 -> l.erase(0, cx + 1, curBg)
            else -> {
                l.erase(0, cols, curBg)
                l.wrapped = false
            }
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (r in cy + 1 until rows) {
                    val l = scr(r)
                    l.erase(0, cols, curBg)
                    l.wrapped = false
                }
            }
            1 -> {
                for (r in 0 until cy) {
                    val l = scr(r)
                    l.erase(0, cols, curBg)
                    l.wrapped = false
                }
                scr(cy).erase(0, cx + 1, curBg)
            }
            2 -> {
                for (r in 0 until rows) {
                    val l = scr(r)
                    l.erase(0, cols, curBg)
                    l.wrapped = false
                }
            }
            3 -> if (!altActive) {
                while (main.size > rows) {
                    main.removeFirst()
                    trimmed++
                }
                scrollBack = 0
            }
        }
    }

    fun reset() {
        main.clear()
        alt = null
        altActive = false
        for (i in 0 until rows) main.addLast(VtLine(cols))
        cx = 0
        cy = 0
        top = 0
        bottom = rows - 1
        autoWrap = true
        origin = false
        insertMode = false
        pendingWrap = false
        lineDraw = false
        cursorVisible = true
        appCursor = false
        bracketedPaste = false
        mouseMode = 0
        mouseSgr = false
        curFg = 0
        curBg = 0
        curAt = 0
        scrollBack = 0
        state = ST_GROUND
        pendingHigh = 0
        tabs = BooleanArray(cols) { it % 8 == 0 }
        onChange?.invoke()
    }

    fun resize(newCols0: Int, newRows0: Int) {
        val newCols = maxOf(newCols0, 2)
        val newRows = maxOf(newRows0, 1)
        if (newCols == cols && newRows == rows) return
        if (newCols != cols) {
            for (i in 0 until main.size) main[i] = main[i].resized(newCols)
            alt?.let { a -> for (i in a.indices) a[i] = a[i].resized(newCols) }
            cols = newCols
            tabs = BooleanArray(cols) { it % 8 == 0 }
        }
        var mcy = if (altActive) mCy else cy
        if (newRows < rows) {
            var diff = rows - newRows
            while (diff > 0 && rows - 1 > mcy && main[main.size - 1].isBlank()) {
                main.removeLast()
                rows--
                diff--
            }
            if (diff > 0) {
                rows -= diff
                mcy = maxOf(0, mcy - diff)
            }
        } else if (newRows > rows) {
            var grow = newRows - rows
            val pull = minOf(grow, main.size - rows)
            rows += pull
            mcy += pull
            grow -= pull
            while (grow > 0) {
                main.addLast(VtLine(cols))
                rows++
                grow--
            }
        }
        if (altActive) mCy = mcy.coerceIn(0, rows - 1) else cy = mcy.coerceIn(0, rows - 1)
        alt?.let { old -> alt = Array(rows) { r -> if (r < old.size) old[r] else VtLine(cols) } }
        top = 0
        bottom = rows - 1
        cx = cx.coerceIn(0, cols - 1)
        if (altActive) cy = cy.coerceIn(0, rows - 1)
        pendingWrap = false
        scrollBack = scrollBack.coerceIn(0, maxScrollBack())
        onChange?.invoke()
    }

    // ---------- entrada ----------

    fun feed(s: String) {
        try {
            for (ch in s) step(ch)
        } catch (t: Throwable) {
            state = ST_GROUND
            cx = cx.coerceIn(0, cols - 1)
            cy = cy.coerceIn(0, rows - 1)
        }
        onChange?.invoke()
    }

    private fun step(c: Char) {
        when (state) {
            ST_GROUND -> ground(c)
            ST_ESC -> escape(c)
            ST_CSI -> csiChar(c)
            ST_OSC -> {
                if (c == '\u0007') {
                    oscBel = true
                    finishOsc()
                    state = ST_GROUND
                } else if (c == '\u001B') {
                    state = ST_OSC_ESC
                } else if (oscBuf.length < 120) {
                    oscBuf.append(c)
                }
            }
            ST_OSC_ESC -> {
                if (c == '\\') {
                    oscBel = false
                    finishOsc()
                    state = ST_GROUND
                } else {
                    oscBel = false
                    finishOsc()
                    state = ST_ESC
                    escape(c)
                }
            }
            ST_STR -> {
                if (c == '\u001B') state = ST_STR_ESC else if (c == '\u0007') state = ST_GROUND
            }
            ST_STR_ESC -> state = if (c == '\\') ST_GROUND else ST_STR
            ST_CHARSET -> {
                if (charsetSlot == '(') lineDraw = (c == '0')
                state = ST_GROUND
            }
            else -> state = ST_GROUND
        }
    }

    private fun ground(c: Char) {
        val code = c.code
        if (pendingHigh != 0) {
            if (Character.isLowSurrogate(c)) {
                val cp = Character.toCodePoint(pendingHigh.toChar(), c)
                pendingHigh = 0
                put(cp)
                return
            }
            pendingHigh = 0
        }
        when {
            c == '\u001B' -> state = ST_ESC
            code < 0x20 -> control(c)
            code == 0x7F || code in 0x80..0x9F -> {}
            Character.isHighSurrogate(c) -> pendingHigh = code
            Character.isLowSurrogate(c) -> {}
            else -> put(code)
        }
    }

    private fun control(c: Char) {
        when (c) {
            '\b' -> {
                if (cx > 0) cx--
                pendingWrap = false
            }
            '\t' -> {
                var x = cx + 1
                while (x < cols - 1 && !tabs[x]) x++
                cx = minOf(x, cols - 1)
                pendingWrap = false
            }
            '\n', '\u000B', '\u000C' -> {
                lineFeed()
                pendingWrap = false
            }
            '\r' -> {
                cx = 0
                pendingWrap = false
            }
        }
    }

    private fun put(cp0: Int) {
        val cp = if (lineDraw && cp0 in 0x5F..0x7E) VT_ACS[cp0 - 0x5F] else cp0
        val w = vtWidth(cp)
        if (w == 0) return
        if (pendingWrap && autoWrap) {
            scr(cy).wrapped = true
            cx = 0
            lineFeed()
        }
        pendingWrap = false
        if (cx + w > cols) {
            if (autoWrap) {
                scr(cy).wrapped = true
                cx = 0
                lineFeed()
            } else {
                cx = cols - w
            }
        }
        if (insertMode) insertCells(w)
        val line = scr(cy)
        if (line.cp[cx] == -1 && cx > 0) line.cp[cx - 1] = 0
        if (w == 1 && cx + 1 < cols && line.cp[cx + 1] == -1) line.cp[cx + 1] = 0
        line.cp[cx] = cp
        line.fg[cx] = curFg
        line.bg[cx] = curBg
        line.at[cx] = curAt
        if (w == 2) {
            line.cp[cx + 1] = -1
            line.fg[cx + 1] = curFg
            line.bg[cx + 1] = curBg
            line.at[cx + 1] = curAt
        }
        lastCp = cp
        cx += w
        if (cx >= cols) {
            cx = cols - 1
            pendingWrap = true
        }
    }

    private fun escape(c: Char) {
        when {
            c == '[' -> {
                csiBuf.setLength(0)
                state = ST_CSI
            }
            c == ']' -> {
                oscBuf.setLength(0)
                state = ST_OSC
            }
            c == 'P' || c == 'X' || c == '^' || c == '_' -> state = ST_STR
            c == '(' || c == ')' || c == '*' || c == '+' -> {
                charsetSlot = c
                state = ST_CHARSET
            }
            c == '#' || c == ' ' || c == '%' -> state = ST_SKIP
            c == '\u001B' -> state = ST_ESC
            c.code < 0x20 -> control(c)
            else -> {
                state = ST_GROUND
                when (c) {
                    '7' -> saveCursor()
                    '8' -> restoreCursor()
                    'D' -> {
                        lineFeed()
                        pendingWrap = false
                    }
                    'E' -> {
                        cx = 0
                        lineFeed()
                        pendingWrap = false
                    }
                    'M' -> {
                        if (cy == top) scrollDown(1) else if (cy > 0) cy--
                        pendingWrap = false
                    }
                    'H' -> if (cx in tabs.indices) tabs[cx] = true
                    'c' -> reset()
                }
            }
        }
    }

    private fun saveCursor() {
        sCx = cx
        sCy = cy
        sFg = curFg
        sBg = curBg
        sAt = curAt
    }

    private fun restoreCursor() {
        cx = sCx.coerceIn(0, cols - 1)
        cy = sCy.coerceIn(0, rows - 1)
        curFg = sFg
        curBg = sBg
        curAt = sAt
        pendingWrap = false
    }

    private fun finishOsc() {
        val t = oscBuf.toString()
        val end = if (oscBel) "\u0007" else "\u001B\\"
        if (t == "10;?") respond("\u001B]10;rgb:ffff/ffff/ffff$end")
        else if (t == "11;?") respond("\u001B]11;rgb:0000/0000/0000$end")
    }

    private fun csiChar(c: Char) {
        val code = c.code
        when {
            c == '\u001B' -> state = ST_ESC
            code < 0x20 -> control(c)
            code in 0x40..0x7E -> {
                state = ST_GROUND
                dispatchCsi(c)
            }
            else -> if (csiBuf.length < 80) csiBuf.append(c)
        }
    }

    private fun parseParams(s: String): IntArray {
        if (s.isEmpty()) return IntArray(0)
        val parts = s.split(';', ':')
        return IntArray(parts.size) { parts[it].toIntOrNull() ?: -1 }
    }

    private fun dispatchCsi(fin: Char) {
        val raw = csiBuf.toString()
        var marker = ' '
        var body = raw
        if (raw.isNotEmpty() && (raw[0] == '?' || raw[0] == '>' || raw[0] == '<' || raw[0] == '=')) {
            marker = raw[0]
            body = raw.substring(1)
        }
        var k = body.length
        while (k > 0 && body[k - 1].code in 0x20..0x2F) k--
        val inter = body.substring(k)
        body = body.substring(0, k)
        val ps = parseParams(body)

        if (marker == '?') {
            when (fin) {
                'h' -> for (p in ps) setMode(p, true)
                'l' -> for (p in ps) setMode(p, false)
            }
            return
        }
        if (marker == '>') {
            if (fin == 'c') respond("\u001B[>41;320;0c")
            return
        }
        if (marker != ' ' || inter.isNotEmpty()) return

        fun arg(i: Int, def: Int): Int {
            val v = if (i < ps.size) ps[i] else -1
            return if (v <= 0) def else v
        }
        val p0 = if (ps.isNotEmpty() && ps[0] >= 0) ps[0] else 0
        val n = arg(0, 1)

        when (fin) {
            '@' -> insertCells(n)
            'A' -> {
                val min = if (cy >= top) top else 0
                cy = maxOf(min, cy - n)
                pendingWrap = false
            }
            'B' -> {
                val max = if (cy <= bottom) bottom else rows - 1
                cy = minOf(max, cy + n)
                pendingWrap = false
            }
            'C' -> {
                cx = minOf(cols - 1, cx + n)
                pendingWrap = false
            }
            'D' -> {
                cx = maxOf(0, cx - n)
                pendingWrap = false
            }
            'E' -> {
                cy = minOf(rows - 1, cy + n)
                cx = 0
                pendingWrap = false
            }
            'F' -> {
                cy = maxOf(0, cy - n)
                cx = 0
                pendingWrap = false
            }
            'G', '`' -> {
                cx = (n - 1).coerceIn(0, cols - 1)
                pendingWrap = false
            }
            'H', 'f' -> {
                var row = arg(0, 1) - 1
                val col = arg(1, 1) - 1
                if (origin) row = (row + top).coerceIn(top, bottom)
                cy = row.coerceIn(0, rows - 1)
                cx = col.coerceIn(0, cols - 1)
                pendingWrap = false
            }
            'I' -> repeat(n) { control('\t') }
            'Z' -> {
                repeat(n) {
                    var x = cx - 1
                    while (x > 0 && !tabs[x]) x--
                    cx = maxOf(x, 0)
                }
                pendingWrap = false
            }
            'J' -> eraseDisplay(p0)
            'K' -> eraseLine(p0)
            'L' -> insertLines(n)
            'M' -> deleteLines(n)
            'P' -> deleteCells(n)
            'S' -> scrollUp(n)
            'T' -> if (ps.size <= 1) scrollDown(n)
            'X' -> scr(cy).erase(cx, cx + n, curBg)
            'b' -> repeat(minOf(n, 500)) { put(lastCp) }
            'd' -> {
                var row = n - 1
                if (origin) row += top
                cy = row.coerceIn(0, rows - 1)
                pendingWrap = false
            }
            'm' -> sgr(ps)
            'h' -> for (p in ps) if (p == 4) insertMode = true
            'l' -> for (p in ps) if (p == 4) insertMode = false
            'n' -> {
                if (p0 == 6) {
                    val row = if (origin) cy - top else cy
                    respond("\u001B[" + (row + 1) + ";" + (cx + 1) + "R")
                } else if (p0 == 5) {
                    respond("\u001B[0n")
                }
            }
            'c' -> if (p0 == 0) respond("\u001B[?64;1;2;6;9;15;18;21;22c")
            'r' -> {
                val t = arg(0, 1) - 1
                val b = arg(1, rows) - 1
                if (t < b && b < rows) {
                    top = t
                    bottom = b
                    cx = 0
                    cy = if (origin) top else 0
                    pendingWrap = false
                }
            }
            's' -> saveCursor()
            'u' -> restoreCursor()
        }
    }

    private fun setMode(p: Int, on: Boolean) {
        when (p) {
            1 -> appCursor = on
            6 -> {
                origin = on
                cx = 0
                cy = if (on) top else 0
                pendingWrap = false
            }
            7 -> autoWrap = on
            25 -> cursorVisible = on
            47, 1047 -> switchAlt(on, false)
            1049 -> switchAlt(on, true)
            1000, 1002, 1003 -> {
                if (on) mouseMode = p else if (mouseMode == p) mouseMode = 0
            }
            1006 -> mouseSgr = on
            2004 -> bracketedPaste = on
        }
    }

    private fun switchAlt(on: Boolean, save: Boolean) {
        if (on == altActive) return
        if (on) {
            if (save) {
                mCx = cx
                mCy = cy
                mFg = curFg
                mBg = curBg
                mAt = curAt
            }
            alt = Array(rows) { VtLine(cols) }
            altActive = true
            top = 0
            bottom = rows - 1
            scrollBack = 0
        } else {
            altActive = false
            alt = null
            top = 0
            bottom = rows - 1
            if (save) {
                cx = mCx.coerceIn(0, cols - 1)
                cy = mCy.coerceIn(0, rows - 1)
                curFg = mFg
                curBg = mBg
                curAt = mAt
            }
        }
        pendingWrap = false
    }

    private fun sgr(ps: IntArray) {
        if (ps.isEmpty()) {
            curFg = 0
            curBg = 0
            curAt = 0
            return
        }
        var i = 0
        while (i < ps.size) {
            val v = maxOf(ps[i], 0)
            when {
                v == 0 -> {
                    curFg = 0
                    curBg = 0
                    curAt = 0
                }
                v == 1 -> curAt = curAt or VT_BOLD
                v == 2 -> curAt = curAt or VT_FAINT
                v == 3 -> curAt = curAt or VT_ITALIC
                v == 4 || v == 21 -> curAt = curAt or VT_UNDERLINE
                v == 7 -> curAt = curAt or VT_INVERSE
                v == 8 -> curAt = curAt or VT_INVISIBLE
                v == 9 -> curAt = curAt or VT_STRIKE
                v == 22 -> curAt = curAt and (VT_BOLD or VT_FAINT).inv()
                v == 23 -> curAt = curAt and VT_ITALIC.inv()
                v == 24 -> curAt = curAt and VT_UNDERLINE.inv()
                v == 27 -> curAt = curAt and VT_INVERSE.inv()
                v == 28 -> curAt = curAt and VT_INVISIBLE.inv()
                v == 29 -> curAt = curAt and VT_STRIKE.inv()
                v in 30..37 -> curFg = VT_ANSI[v - 30]
                v == 39 -> curFg = 0
                v in 40..47 -> curBg = VT_ANSI[v - 40]
                v == 49 -> curBg = 0
                v in 90..97 -> curFg = VT_ANSI[v - 90 + 8]
                v in 100..107 -> curBg = VT_ANSI[v - 100 + 8]
                v == 38 || v == 48 -> {
                    val mode = if (i + 1 < ps.size) ps[i + 1] else -1
                    var color = 0
                    if (mode == 5) {
                        color = vt256(if (i + 2 < ps.size) maxOf(ps[i + 2], 0) else 0)
                        i += 2
                    } else if (mode == 2) {
                        val r = (if (i + 2 < ps.size) maxOf(ps[i + 2], 0) else 0).coerceIn(0, 255)
                        val g = (if (i + 3 < ps.size) maxOf(ps[i + 3], 0) else 0).coerceIn(0, 255)
                        val b = (if (i + 4 < ps.size) maxOf(ps[i + 4], 0) else 0).coerceIn(0, 255)
                        color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        i += 4
                    }
                    if (color != 0) {
                        if (v == 38) curFg = color else curBg = color
                    }
                }
            }
            i++
        }
    }

    private companion object {
        const val ST_GROUND = 0
        const val ST_ESC = 1
        const val ST_CSI = 2
        const val ST_OSC = 3
        const val ST_OSC_ESC = 4
        const val ST_STR = 5
        const val ST_STR_ESC = 6
        const val ST_CHARSET = 7
        const val ST_SKIP = 8
    }
}
