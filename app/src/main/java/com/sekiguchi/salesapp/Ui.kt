package com.sekiguchi.salesapp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** XMLを使わずに画面を組むための最小ヘルパー。外部ライブラリは一切使わない。 */
object Ui {

    val BG = Color.parseColor("#F4F2EE")
    val CARD = Color.parseColor("#FFFFFF")
    val TEXT = Color.parseColor("#1B1A18")
    val SUB = Color.parseColor("#6B675F")
    val LINE = Color.parseColor("#DFDBD3")
    val ACCENT = Color.parseColor("#1F4E5F")
    val DANGER = Color.parseColor("#B3261E")
    val DANGER_BG = Color.parseColor("#FCEBE9")
    val WARN_BG = Color.parseColor("#FFF6E0")
    val WARN = Color.parseColor("#8A6100")

    val MP = ViewGroup.LayoutParams.MATCH_PARENT
    val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    fun dp(c: Context, v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), c.resources.displayMetrics
    ).toInt()

    fun params(c: Context, top: Int = 0, bottom: Int = 0, height: Int = WC): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(MP, height)
        p.topMargin = dp(c, top)
        p.bottomMargin = dp(c, bottom)
        return p
    }

    /** 画面全体のスクロールコンテナを作り、中身の縦LinearLayoutを返す */
    fun screen(c: Context): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(c)
        scroll.setBackgroundColor(BG)
        scroll.isFillViewport = true
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        val pad = dp(c, 16)
        col.setPadding(pad, dp(c, 20), pad, dp(c, 32))
        scroll.addView(col, ViewGroup.LayoutParams(MP, WC))
        return Pair(scroll, col)
    }

    fun rounded(color: Int, radius: Float, strokeColor: Int? = null): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = radius
        if (strokeColor != null) d.setStroke(2, strokeColor)
        return d
    }

    fun title(c: Context, text: String): TextView {
        val t = TextView(c)
        t.text = text
        t.setTextColor(TEXT)
        t.textSize = 22f
        t.typeface = Typeface.DEFAULT_BOLD
        t.layoutParams = params(c, 0, 4)
        return t
    }

    fun heading(c: Context, text: String): TextView {
        val t = TextView(c)
        t.text = text
        t.setTextColor(ACCENT)
        t.textSize = 14f
        t.typeface = Typeface.DEFAULT_BOLD
        t.letterSpacing = 0.06f
        t.layoutParams = params(c, 20, 6)
        return t
    }

    fun body(c: Context, text: String, color: Int = TEXT, size: Float = 15f): TextView {
        val t = TextView(c)
        t.text = text
        t.setTextColor(color)
        t.textSize = size
        t.setLineSpacing(dp(c, 4).toFloat(), 1f)
        t.layoutParams = params(c, 0, 0)
        return t
    }

    fun card(c: Context, bg: Int = CARD, stroke: Int? = LINE): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.background = rounded(bg, dp(c, 12).toFloat(), stroke)
        val pad = dp(c, 14)
        l.setPadding(pad, pad, pad, pad)
        l.layoutParams = params(c, 8, 4)
        return l
    }

    fun button(c: Context, text: String, bg: Int = ACCENT, fg: Int = Color.WHITE, onClick: () -> Unit): Button {
        val b = Button(c)
        b.text = text
        b.setAllCaps(false)
        b.setTextColor(fg)
        b.textSize = 16f
        b.background = rounded(bg, dp(c, 10).toFloat())
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        b.layoutParams = params(c, 8, 0)
        b.setOnClickListener { onClick() }
        return b
    }

    /** 動揺している状態でも押せる大きめのボタン */
    fun bigButton(c: Context, text: String, sub: String, bg: Int, onClick: () -> Unit): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.background = rounded(bg, dp(c, 14).toFloat())
        l.setPadding(dp(c, 18), dp(c, 22), dp(c, 18), dp(c, 22))
        l.layoutParams = params(c, 12, 0)
        l.gravity = Gravity.CENTER_VERTICAL
        l.isClickable = true
        l.setOnClickListener { onClick() }

        val t = TextView(c)
        t.text = text
        t.setTextColor(Color.WHITE)
        t.textSize = 20f
        t.typeface = Typeface.DEFAULT_BOLD
        l.addView(t)

        if (sub.isNotEmpty()) {
            val s = TextView(c)
            s.text = sub
            s.setTextColor(Color.parseColor("#E6EDEF"))
            s.textSize = 13f
            s.layoutParams = params(c, 4)
            l.addView(s)
        }
        return l
    }

    fun input(c: Context, hint: String, value: String, lines: Int = 1): EditText {
        val e = EditText(c)
        e.hint = hint
        e.setText(value)
        e.setTextColor(TEXT)
        e.setHintTextColor(SUB)
        e.textSize = 15f
        e.background = rounded(Color.WHITE, dp(c, 8).toFloat(), LINE)
        e.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12))
        if (lines > 1) {
            e.setLines(lines)
            e.gravity = Gravity.TOP or Gravity.START
        }
        e.layoutParams = params(c, 6, 2)
        return e
    }

    fun label(c: Context, text: String): TextView {
        val t = TextView(c)
        t.text = text
        t.setTextColor(SUB)
        t.textSize = 13f
        t.layoutParams = params(c, 10, 0)
        return t
    }

    fun divider(c: Context): View {
        val v = View(c)
        v.setBackgroundColor(LINE)
        v.layoutParams = params(c, 12, 4, dp(c, 1))
        return v
    }
}
