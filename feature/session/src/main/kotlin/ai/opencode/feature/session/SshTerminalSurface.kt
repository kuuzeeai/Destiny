package ai.opencode.feature.session

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlin.math.abs

/*
 * Pantalla de la terminal: dibuja la cuadricula del emulador, recibe el teclado (con borrado
 * continuo), selecciona con pulsacion larga y copia / pega.
 */
internal class SshTerminalSurface(context: Context, private val term: VtTerminal) : View(context) {
    var onText: (String) -> Unit = {}
    var onSend: (String) -> Unit = {}
    var onResize: (Int, Int) -> Unit = { _, _ -> }

    private val density = resources.displayMetrics.density
    private val charW = 18.3f
    private val lineH = 36f
    private val padX = 3f * density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint()
    private val menuPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val normalFace: Typeface = Typeface.MONOSPACE
    private val boldFace: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private var baseline = 0f
    private val tmp = CharArray(2)
    private val quad = intArrayOf(4, 8, 1, 13, 9, 7, 11, 2, 6, 14)

    private var selActive = false
    private var selecting = false
    private var aAbs = 0
    private var aCol = 0
    private var fAbs = 0
    private var fCol = 0
    private var menuVisible = false
    private val menuRects = ArrayList<RectF>()
    private val menuLabels = arrayOf("Copiar", "Pegar", "Todo")
    private var accumY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var menuConsumed = false

    private val changeListener: () -> Unit = { postInvalidateOnAnimation() }

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (selActive) clearSelection() else showKeyboard()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            startSelection(e.x, e.y)
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!selecting) dragScroll(-distanceY)
            return true
        }
    })

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        paint.textSize = charW / 0.6f
        paint.typeface = normalFace
        val fm = paint.fontMetrics
        baseline = (lineH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        menuPaint.textSize = 15f * density
    }

    // ---------- ciclo de vida y tamano ----------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        term.onChange = changeListener
        invalidate()
    }

    override fun onDetachedFromWindow() {
        if (term.onChange === changeListener) term.onChange = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cols = ((w - padX) / charW).toInt().coerceAtLeast(20)
        val rows = (h / lineH).toInt().coerceAtLeast(5)
        clearSelection()
        term.resize(cols, rows)
        onResize(cols, rows)
    }

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    // ---------- teclado ----------

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        val owner = this
        return object : BaseInputConnection(owner, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                editable?.clear()
                if (text != null) deliverText(text.toString())
                return true
            }

            override fun finishComposingText(): Boolean {
                val e = editable
                if (e != null && e.isNotEmpty()) {
                    val s = e.toString()
                    e.clear()
                    deliverText(s)
                }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                val e = editable
                if (e != null && e.isNotEmpty()) return super.deleteSurroundingText(beforeLength, afterLength)
                repeat(beforeLength) { deliverRaw("\u007F") }
                return true
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                return deleteSurroundingText(beforeLength, afterLength)
            }

            override fun performEditorAction(editorAction: Int): Boolean {
                deliverRaw("\r")
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) handleKey(event.keyCode, event)
                return true
            }
        }
    }

    private fun deliverText(s: String) {
        if (s.isEmpty()) return
        term.scrollBack = 0
        onText(s.replace('\n', '\r'))
    }

    private fun deliverRaw(s: String) {
        term.scrollBack = 0
        onSend(s)
    }

    private fun cursorKey(c: Char): String = if (term.appCursor) "\u001BO$c" else "\u001B[$c"

    private fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        val seq: String? = when (keyCode) {
            KeyEvent.KEYCODE_DEL -> "\u007F"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001B[3~"
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "\r"
            KeyEvent.KEYCODE_TAB -> "\t"
            KeyEvent.KEYCODE_ESCAPE -> "\u001B"
            KeyEvent.KEYCODE_DPAD_UP -> cursorKey('A')
            KeyEvent.KEYCODE_DPAD_DOWN -> cursorKey('B')
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursorKey('C')
            KeyEvent.KEYCODE_DPAD_LEFT -> cursorKey('D')
            KeyEvent.KEYCODE_MOVE_HOME -> cursorKey('H')
            KeyEvent.KEYCODE_MOVE_END -> cursorKey('F')
            KeyEvent.KEYCODE_PAGE_UP -> "\u001B[5~"
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001B[6~"
            else -> null
        }
        if (seq != null) {
            deliverRaw(seq)
            return true
        }
        if (event.isCtrlPressed) {
            val plain = event.getUnicodeChar(0)
            if (plain > 0) {
                val up = Character.toUpperCase(plain)
                if (up in 0x40..0x5F) {
                    deliverRaw((up and 0x1F).toChar().toString())
                    return true
                }
            }
            return false
        }
        val u = event.unicodeChar
        if (u > 0) {
            deliverText(String(Character.toChars(u)))
            return true
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKey(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    // ---------- toque, scroll y seleccion ----------

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        lastX = ev.x
        lastY = ev.y
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
            accumY = 0f
            menuConsumed = false
            if (menuVisible) {
                val hit = menuHit(ev.x, ev.y)
                if (hit >= 0) {
                    menuConsumed = true
                    runMenu(hit)
                    return true
                }
            }
        }
        if (menuConsumed) return true
        detector.onTouchEvent(ev)
        if (selecting) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> extendSelection(ev.x, ev.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    selecting = false
                    menuVisible = true
                    invalidate()
                }
            }
        }
        return true
    }

    private fun rowAt(y: Float): Int = (y / lineH).toInt().coerceIn(0, term.rows - 1)

    private fun colAt(x: Float): Int = ((x - padX) / charW).toInt().coerceIn(0, term.cols - 1)

    private fun dragScroll(dy: Float) {
        accumY += dy
        val steps = (accumY / lineH).toInt()
        if (steps == 0) return
        accumY -= steps * lineH
        if (term.mouseMode != 0) {
            repeat(abs(steps)) { sendWheel(steps > 0) }
        } else if (term.altActive) {
            repeat(abs(steps)) { onSend(if (steps > 0) cursorKey('A') else cursorKey('B')) }
        } else {
            term.scrollBack = (term.scrollBack + steps).coerceIn(0, term.maxScrollBack())
            invalidate()
        }
    }

    private fun sendWheel(up: Boolean) {
        val btn = if (up) 64 else 65
        val col = colAt(lastX) + 1
        val row = rowAt(lastY) + 1
        if (term.mouseSgr) {
            onSend("\u001B[<$btn;$col;${row}M")
        } else {
            onSend("\u001B[M" + (32 + btn).toChar() + (32 + col).toChar() + (32 + row).toChar())
        }
    }

    private fun startSelection(x: Float, y: Float) {
        val r = rowAt(y)
        val c = colAt(x)
        val line = term.lineAtView(r)
        var s = c
        var e = c
        if (line != null && c < line.cp.size && line.cp[c] > 32) {
            while (s > 0 && line.cp[s - 1] > 32) s--
            while (e < line.cp.size - 1 && line.cp[e + 1] > 32) e++
        }
        val abs = term.absIndex(r)
        aAbs = abs
        aCol = s
        fAbs = abs
        fCol = e
        selActive = true
        selecting = true
        menuVisible = false
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    private fun extendSelection(x: Float, y: Float) {
        fAbs = term.absIndex(rowAt(y))
        fCol = colAt(x)
        invalidate()
    }

    private fun clearSelection() {
        if (!selActive && !menuVisible) return
        selActive = false
        selecting = false
        menuVisible = false
        invalidate()
    }

    private fun selectionText(): String {
        val aFirst = aAbs < fAbs || (aAbs == fAbs && aCol <= fCol)
        return if (aFirst) term.extract(aAbs, aCol, fAbs, fCol) else term.extract(fAbs, fCol, aAbs, aCol)
    }

    private fun runMenu(i: Int) {
        when (i) {
            0 -> {
                val text = selectionText()
                if (text.isNotBlank()) {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Terminal", text))
                    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                }
                clearSelection()
            }
            1 -> {
                pasteClipboard()
                clearSelection()
            }
            2 -> {
                aAbs = term.firstAbs()
                aCol = 0
                fAbs = term.lastAbs()
                fCol = term.cols - 1
                selActive = true
                menuVisible = true
                invalidate()
            }
        }
    }

    private fun pasteClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
        paste(text)
    }

    fun paste(text0: String) {
        val text = text0.replace("\u001B", "").replace("\r\n", "\r").replace('\n', '\r')
        if (text.isEmpty()) return
        term.scrollBack = 0
        if (term.bracketedPaste) onSend("\u001B[200~" + text + "\u001B[201~") else onSend(text)
    }

    private fun menuHit(x: Float, y: Float): Int {
        for (i in menuRects.indices) if (menuRects[i].contains(x, y)) return i
        return -1
    }

    // ---------- dibujo ----------

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        try {
            drawScreen(canvas)
            if (menuVisible && selActive) drawMenu(canvas)
        } catch (t: Throwable) {
            // un error de dibujo no debe cerrar la app
        }
    }

    private fun drawScreen(canvas: Canvas) {
        val rows = term.rows
        val cols = term.cols
        var s0r = Int.MAX_VALUE
        var s0c = 0
        var s1r = Int.MIN_VALUE
        var s1c = 0
        if (selActive) {
            if (aAbs < fAbs || (aAbs == fAbs && aCol <= fCol)) {
                s0r = aAbs; s0c = aCol; s1r = fAbs; s1c = fCol
            } else {
                s0r = fAbs; s0c = fCol; s1r = aAbs; s1c = aCol
            }
        }
        val showCursor = term.cursorVisible && term.scrollBack == 0
        for (r in 0 until rows) {
            val line = term.lineAtView(r) ?: continue
            val abs = term.absIndex(r)
            val y = r * lineH
            val n = minOf(cols, line.cp.size)
            for (c in 0 until n) {
                val x = padX + c * charW
                val cp = line.cp[c]
                val at = line.at[c]
                var f = line.fg[c]
                var b = line.bg[c]
                if (f == 0) f = Color.WHITE
                if ((at and VT_INVERSE) != 0) {
                    val t = f
                    f = if (b == 0) Color.BLACK else b
                    b = t
                }
                if (selActive && abs >= s0r && abs <= s1r &&
                    (abs > s0r || c >= s0c) && (abs < s1r || c <= s1c)
                ) {
                    b = SEL_BG
                    f = Color.WHITE
                }
                if (showCursor && r == term.cy && c == term.cx) {
                    b = Color.WHITE
                    f = Color.BLACK
                }
                if (b != 0) {
                    fillPaint.color = b
                    canvas.drawRect(x, y, x + charW, y + lineH, fillPaint)
                }
                if (cp > 32 && (at and VT_INVISIBLE) == 0) drawGlyph(canvas, cp, at, f, x, y)
                if ((at and VT_UNDERLINE) != 0) {
                    fillPaint.color = f
                    box(canvas, x, y + lineH - 5f, x + charW, y + lineH - 3f)
                }
                if ((at and VT_STRIKE) != 0) {
                    fillPaint.color = f
                    box(canvas, x, y + lineH / 2f, x + charW, y + lineH / 2f + 2f)
                }
            }
        }
    }

    private fun drawGlyph(canvas: Canvas, cp: Int, at: Int, color: Int, x: Float, y: Float) {
        if (drawBlockOrBox(canvas, cp, color, x, y)) return
        val face = if ((at and VT_BOLD) != 0) boldFace else normalFace
        if (paint.typeface !== face) paint.typeface = face
        paint.color = color
        if ((at and VT_FAINT) != 0) paint.alpha = 140
        paint.textSkewX = if ((at and VT_ITALIC) != 0) -0.2f else 0f
        val count = Character.toChars(cp, tmp, 0)
        canvas.drawText(tmp, 0, count, x, y + baseline, paint)
    }

    private fun box(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        canvas.drawRect(
            Math.round(l).toFloat(), Math.round(t).toFloat(),
            Math.round(r).toFloat(), Math.round(b).toFloat(), fillPaint,
        )
    }

    private fun drawBlockOrBox(canvas: Canvas, cp: Int, color: Int, x: Float, y: Float): Boolean {
        if (cp < 0x2500 || cp > 0x259F) return false
        val w = charW
        val h = lineH
        fillPaint.color = color
        if (cp >= 0x2580) {
            when (cp) {
                0x2580 -> box(canvas, x, y, x + w, y + h / 2f)
                in 0x2581..0x2587 -> {
                    val k = cp - 0x2580
                    box(canvas, x, y + h - h * k / 8f, x + w, y + h)
                }
                0x2588 -> box(canvas, x, y, x + w, y + h)
                in 0x2589..0x258F -> {
                    val k = 0x2590 - cp
                    box(canvas, x, y, x + w * k / 8f, y + h)
                }
                0x2590 -> box(canvas, x + w / 2f, y, x + w, y + h)
                0x2591, 0x2592, 0x2593 -> {
                    val alpha = (cp - 0x2590) * 64
                    fillPaint.color = (color and 0x00FFFFFF) or (alpha shl 24)
                    box(canvas, x, y, x + w, y + h)
                }
                0x2594 -> box(canvas, x, y, x + w, y + h / 8f)
                0x2595 -> box(canvas, x + w * 7f / 8f, y, x + w, y + h)
                else -> {
                    val q = quad[cp - 0x2596]
                    val mx = x + w / 2f
                    val my = y + h / 2f
                    if ((q and 1) != 0) box(canvas, x, y, mx, my)
                    if ((q and 2) != 0) box(canvas, mx, y, x + w, my)
                    if ((q and 4) != 0) box(canvas, x, my, mx, y + h)
                    if ((q and 8) != 0) box(canvas, mx, my, x + w, y + h)
                }
            }
            return true
        }
        val seg = boxSegments(cp)
        if (seg == 0) return false
        val l = seg and 3
        val r = (seg shr 2) and 3
        val u = (seg shr 4) and 3
        val d = (seg shr 6) and 3
        val light = maxOf(2f, density * 0.8f)
        val cx = x + w / 2f
        val cy = y + h / 2f
        if (l != 0) {
            val t = if (l == 2) light * 2f else light
            box(canvas, x, cy - t / 2f, cx + t / 2f, cy + t / 2f)
        }
        if (r != 0) {
            val t = if (r == 2) light * 2f else light
            box(canvas, cx - t / 2f, cy - t / 2f, x + w, cy + t / 2f)
        }
        if (u != 0) {
            val t = if (u == 2) light * 2f else light
            box(canvas, cx - t / 2f, y, cx + t / 2f, cy + t / 2f)
        }
        if (d != 0) {
            val t = if (d == 2) light * 2f else light
            box(canvas, cx - t / 2f, cy - t / 2f, cx + t / 2f, y + h)
        }
        return true
    }

    private fun seg(l: Int, r: Int, u: Int, d: Int): Int = l or (r shl 2) or (u shl 4) or (d shl 6)

    private fun boxSegments(cp: Int): Int = when (cp) {
        0x2500 -> seg(1, 1, 0, 0)
        0x2501 -> seg(2, 2, 0, 0)
        0x2502 -> seg(0, 0, 1, 1)
        0x2503 -> seg(0, 0, 2, 2)
        0x250C, 0x256D -> seg(0, 1, 0, 1)
        0x250F -> seg(0, 2, 0, 2)
        0x2510, 0x256E -> seg(1, 0, 0, 1)
        0x2513 -> seg(2, 0, 0, 2)
        0x2514, 0x2570 -> seg(0, 1, 1, 0)
        0x2517 -> seg(0, 2, 2, 0)
        0x2518, 0x256F -> seg(1, 0, 1, 0)
        0x251B -> seg(2, 0, 2, 0)
        0x251C -> seg(0, 1, 1, 1)
        0x2523 -> seg(0, 2, 2, 2)
        0x2524 -> seg(1, 0, 1, 1)
        0x252B -> seg(2, 0, 2, 2)
        0x252C -> seg(1, 1, 0, 1)
        0x2533 -> seg(2, 2, 0, 2)
        0x2534 -> seg(1, 1, 1, 0)
        0x253B -> seg(2, 2, 2, 0)
        0x253C -> seg(1, 1, 1, 1)
        0x254B -> seg(2, 2, 2, 2)
        0x2574 -> seg(1, 0, 0, 0)
        0x2575 -> seg(0, 0, 1, 0)
        0x2576 -> seg(0, 1, 0, 0)
        0x2577 -> seg(0, 0, 0, 1)
        0x2578 -> seg(2, 0, 0, 0)
        0x2579 -> seg(0, 0, 2, 0)
        0x257A -> seg(0, 2, 0, 0)
        0x257B -> seg(0, 0, 0, 2)
        else -> 0
    }

    private fun drawMenu(canvas: Canvas) {
        val rows = term.rows
        val s0Abs: Int
        val s1Abs: Int
        val s0Col: Int
        if (aAbs < fAbs || (aAbs == fAbs && aCol <= fCol)) {
            s0Abs = aAbs; s1Abs = fAbs; s0Col = aCol
        } else {
            s0Abs = fAbs; s1Abs = aAbs; s0Col = fCol
        }
        val r0 = term.viewRowOfAbs(s0Abs).coerceIn(0, rows - 1)
        val r1 = term.viewRowOfAbs(s1Abs).coerceIn(0, rows - 1)
        val padBtn = 14f * density
        val menuH = 40f * density
        var total = 0f
        val widths = FloatArray(menuLabels.size)
        for (i in menuLabels.indices) {
            widths[i] = menuPaint.measureText(menuLabels[i]) + padBtn * 2f
            total += widths[i]
        }
        var left = padX + s0Col * charW
        if (left + total > width - 6f * density) left = width - 6f * density - total
        if (left < 6f * density) left = 6f * density
        var topY = r0 * lineH - menuH - 8f * density
        if (topY < 4f * density) topY = (r1 + 1) * lineH + 8f * density
        if (topY + menuH > height) topY = height - menuH - 4f * density
        menuRects.clear()
        fillPaint.color = 0xF2303030.toInt()
        canvas.drawRoundRect(RectF(left, topY, left + total, topY + menuH), 10f * density, 10f * density, fillPaint)
        var x = left
        menuPaint.color = Color.WHITE
        val fm = menuPaint.fontMetrics
        val textY = topY + (menuH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        for (i in menuLabels.indices) {
            canvas.drawText(menuLabels[i], x + padBtn, textY, menuPaint)
            menuRects.add(RectF(x, topY, x + widths[i], topY + menuH))
            x += widths[i]
        }
    }

    private companion object {
        val SEL_BG: Int = 0xFF3A6EA5.toInt()
    }
}
