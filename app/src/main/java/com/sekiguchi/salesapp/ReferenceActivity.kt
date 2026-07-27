package com.sekiguchi.salesapp

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONObject

/** 機能5: 関連法令集・業界地図 と 単位換算。すべて公開情報のみ。 */
class ReferenceActivity : Activity() {

    private var root: JSONObject? = null
    private var tab = 0        // 0=法令 1=業界地図 2=単位換算
    private var detail = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = try {
            Store.asset(this, "reference.json")
        } catch (e: Exception) {
            null
        }
        render()
    }

    override fun onBackPressed() {
        if (detail >= 0) {
            detail = -1
            render()
        } else {
            super.onBackPressed()
        }
    }

    private fun render() {
        val r = root
        if (r == null) {
            val (s, c) = Ui.screen(this)
            c.addView(Ui.title(this, "読み込みエラー"))
            c.addView(Ui.body(this, "reference.json を読み込めませんでした。"))
            setContentView(s)
            return
        }
        setContentView(if (detail >= 0) buildDetail(r) else buildMain(r))
    }

    private fun tabs(col: android.widget.LinearLayout) {
        val row = android.widget.LinearLayout(this)
        row.orientation = android.widget.LinearLayout.HORIZONTAL
        row.layoutParams = Ui.params(this, 8, 4)
        val names = listOf("法令", "業界地図", "単位換算")
        for (i in names.indices) {
            val b = Ui.button(this, names[i], if (tab == i) Ui.ACCENT else Ui.SUB) {
                tab = i
                detail = -1
                render()
            }
            b.textSize = 14f
            val lp = android.widget.LinearLayout.LayoutParams(0, Ui.WC, 1f)
            lp.rightMargin = Ui.dp(this, 4)
            b.layoutParams = lp
            row.addView(b)
        }
        col.addView(row)
    }

    private fun buildMain(r: JSONObject): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "参考情報"))
        tabs(col)

        when (tab) {
            0 -> {
                val arr = r.getJSONArray("laws")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val index = i
                    val card = Ui.card(this)
                    card.isClickable = true
                    card.setOnClickListener {
                        detail = index
                        render()
                    }
                    val t = Ui.body(this, o.getString("title"), Ui.ACCENT, 16f)
                    t.typeface = Typeface.DEFAULT_BOLD
                    card.addView(t)
                    card.addView(Ui.body(this, o.getString("summary"), Ui.SUB, 13f))
                    col.addView(card)
                }
                col.addView(Ui.divider(this))
                col.addView(Ui.body(this, r.optString("disclaimer"), Ui.SUB, 12f))
            }
            1 -> {
                val arr = r.getJSONArray("industry")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val card = Ui.card(this)
                    val t = Ui.body(this, o.getString("title"), Ui.ACCENT, 16f)
                    t.typeface = Typeface.DEFAULT_BOLD
                    card.addView(t)
                    card.addView(Ui.body(this, o.getString("body"), Ui.TEXT, 14f))
                    val kw = o.optJSONArray("keywords")
                    if (kw != null) {
                        val sb = StringBuilder()
                        for (j in 0 until kw.length()) {
                            if (j > 0) sb.append("　/　")
                            sb.append(kw.getString(j))
                        }
                        card.addView(Ui.body(this, sb.toString(), Ui.SUB, 12f))
                    }
                    col.addView(card)
                }
            }
            2 -> buildUnits(r, col)
        }
        return scroll
    }

    private fun buildUnits(r: JSONObject, col: android.widget.LinearLayout) {
        val arr = r.getJSONArray("units")
        val labels = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            labels.add(o.getString("from") + " → " + o.getString("to"))
        }

        val card = Ui.card(this)
        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.layoutParams = Ui.params(this, 2, 6)
        card.addView(spinner)

        val input = Ui.input(this, "値を入力", "")
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        card.addView(input)

        val out = TextView(this)
        out.textSize = 26f
        out.typeface = Typeface.DEFAULT_BOLD
        out.setTextColor(Ui.ACCENT)
        out.layoutParams = Ui.params(this, 10, 4)
        card.addView(out)

        val note = Ui.body(this, "", Ui.SUB, 12f)
        card.addView(note)

        val update = {
            val idx = spinner.selectedItemPosition
            val o = arr.getJSONObject(if (idx < 0) 0 else idx)
            val x = input.text.toString().toDoubleOrNull()
            if (x == null) {
                out.text = ""
                note.text = o.optString("note")
            } else {
                val y = x * o.getDouble("factor")
                out.text = String.format("%.4f", y).trimEnd('0').trimEnd('.') + " " + o.getString("to")
                note.text = o.optString("note")
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                update()
            }
        })

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: android.view.View?,
                position: Int, id: Long
            ) {
                update()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        col.addView(card)

        val hint = Ui.card(this, Ui.WARN_BG, Ui.WARN)
        hint.addView(Ui.body(this,
            "カタログの単位と現場の言い方が違う場面用。温度差以外は絶対量の換算です。",
            Ui.WARN, 13f))
        col.addView(hint)
    }

    private fun buildDetail(r: JSONObject): android.view.View {
        val (scroll, col) = Ui.screen(this)
        val o = r.getJSONArray("laws").getJSONObject(detail)

        col.addView(Ui.title(this, o.getString("title")))
        col.addView(Ui.body(this, o.getString("summary"), Ui.TEXT, 15f))

        col.addView(Ui.heading(this, "要点"))
        val pc = Ui.card(this)
        val points = o.getJSONArray("points")
        for (i in 0 until points.length()) {
            val t = Ui.body(this, "・" + points.getString(i), Ui.TEXT, 14f)
            t.layoutParams = Ui.params(this, if (i == 0) 0 else 8)
            pc.addView(t)
        }
        col.addView(pc)

        val watch = o.optString("watch")
        if (watch.isNotEmpty()) {
            col.addView(Ui.heading(this, "実務での注意"))
            val wc = Ui.card(this, Ui.WARN_BG, Ui.WARN)
            wc.addView(Ui.body(this, watch, Ui.WARN, 14f))
            col.addView(wc)
        }

        col.addView(Ui.body(this, "出典の目安： " + o.optString("source"), Ui.SUB, 12f))
        col.addView(Ui.divider(this))
        col.addView(Ui.body(this, r.optString("disclaimer"), Ui.SUB, 12f))

        col.addView(Ui.button(this, "一覧に戻る", Ui.SUB) {
            detail = -1
            render()
        })
        return scroll
    }
}
