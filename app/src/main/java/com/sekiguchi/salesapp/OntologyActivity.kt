package com.sekiguchi.salesapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import org.json.JSONObject

/**
 * オントロジー閲覧。
 * 設計書の概念設計を、考える枠として持ち歩けるようにしたもの。
 * 顧客の実データは載せない。載せた時点で知識グラフではなく顧客名簿になる。
 */
class OntologyActivity : Activity() {

    private var root: JSONObject? = null
    private var tab = 0        // 0=概念 1=課題と解決策 2=段階
    private var detail = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = try {
            Store.asset(this, "ontology.json")
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
            c.addView(Ui.body(this, "ontology.json を読み込めませんでした。"))
            setContentView(s)
            return
        }
        setContentView(if (detail >= 0) buildMapping(r) else buildMain(r))
    }

    private fun buildMain(r: JSONObject): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "オントロジー"))
        col.addView(Ui.body(this, r.optString("note"), Ui.SUB, 12f))

        val row = android.widget.LinearLayout(this)
        row.orientation = android.widget.LinearLayout.HORIZONTAL
        row.layoutParams = Ui.params(this, 10, 4)
        val names = listOf("概念", "課題と解決策", "段階")
        for (i in names.indices) {
            val b = Ui.button(this, names[i], if (tab == i) Ui.ACCENT else Ui.SUB) {
                tab = i
                detail = -1
                render()
            }
            b.textSize = 13f
            val lp = android.widget.LinearLayout.LayoutParams(0, Ui.WC, 1f)
            lp.rightMargin = Ui.dp(this, 4)
            b.layoutParams = lp
            row.addView(b)
        }
        col.addView(row)

        when (tab) {
            0 -> {
                val trees = r.getJSONArray("trees")
                for (i in 0 until trees.length()) {
                    val t = trees.getJSONObject(i)
                    val card = Ui.card(this)
                    val h = Ui.body(this, t.getString("title"), Ui.ACCENT, 16f)
                    h.typeface = Typeface.DEFAULT_BOLD
                    card.addView(h)
                    val nodes = t.getJSONArray("nodes")
                    val sb = StringBuilder()
                    for (j in 0 until nodes.length()) {
                        if (j > 0) sb.append("　·　")
                        sb.append(nodes.getString(j))
                    }
                    card.addView(Ui.body(this, sb.toString(), Ui.TEXT, 14f))
                    col.addView(card)
                }

                val flow = r.getJSONObject("flow")
                col.addView(Ui.heading(this, flow.getString("title")))
                val fc = Ui.card(this)
                val steps = flow.getJSONArray("steps")
                for (j in 0 until steps.length()) {
                    val t = Ui.body(this,
                        (if (j == 0) "" else "↓　") + steps.getString(j), Ui.TEXT, 15f)
                    t.layoutParams = Ui.params(this, if (j == 0) 0 else 2)
                    fc.addView(t)
                }
                fc.addView(Ui.body(this, flow.optString("note"), Ui.SUB, 12f))
                col.addView(fc)
            }
            1 -> {
                col.addView(Ui.body(this,
                    "相手の様子から課題を当てるための対応表。決めつけずに、質問で確かめる。",
                    Ui.SUB, 12f))
                val maps = r.getJSONArray("mappings")
                for (i in 0 until maps.length()) {
                    val o = maps.getJSONObject(i)
                    val index = i
                    val card = Ui.card(this)
                    card.isClickable = true
                    card.setOnClickListener {
                        detail = index
                        render()
                    }
                    val h = Ui.body(this, o.getString("problem"), Ui.ACCENT, 16f)
                    h.typeface = Typeface.DEFAULT_BOLD
                    card.addView(h)
                    col.addView(card)
                }
            }
            2 -> {
                col.addView(Ui.body(this,
                    "案件の段階と、その段階を抜けたと言える条件。", Ui.SUB, 12f))
                val st = r.getJSONArray("stages")
                for (i in 0 until st.length()) {
                    val o = st.getJSONObject(i)
                    val card = Ui.card(this)
                    val h = Ui.body(this, (i + 1).toString() + ". " + o.getString("name"), Ui.ACCENT, 16f)
                    h.typeface = Typeface.DEFAULT_BOLD
                    card.addView(h)
                    card.addView(Ui.body(this, "やること： " + o.getString("goal"), Ui.TEXT, 14f))
                    card.addView(Ui.body(this, "抜けた合図： " + o.getString("exit"), Ui.SUB, 13f))
                    col.addView(card)
                }
            }
        }
        return scroll
    }

    private fun buildMapping(r: JSONObject): android.view.View {
        val (scroll, col) = Ui.screen(this)
        val o = r.getJSONArray("mappings").getJSONObject(detail)

        col.addView(Ui.title(this, o.getString("problem")))

        col.addView(Ui.heading(this, "こういう時に疑う"))
        val sc = Ui.card(this)
        val signals = o.getJSONArray("signals")
        for (i in 0 until signals.length()) {
            val t = Ui.body(this, "・" + signals.getString(i), Ui.TEXT, 14f)
            t.layoutParams = Ui.params(this, if (i == 0) 0 else 6)
            sc.addView(t)
        }
        col.addView(sc)

        col.addView(Ui.heading(this, "打ち手の方向"))
        val ac = Ui.card(this)
        val sol = o.getJSONArray("solutions")
        for (i in 0 until sol.length()) {
            val t = Ui.body(this, "・" + sol.getString(i), Ui.TEXT, 14f)
            t.layoutParams = Ui.params(this, if (i == 0) 0 else 6)
            ac.addView(t)
        }
        col.addView(ac)

        col.addView(Ui.heading(this, "確かめる質問"))
        val qc = Ui.card(this, Ui.WARN_BG, Ui.WARN)
        val q = o.getJSONArray("questions")
        val qsb = StringBuilder()
        for (i in 0 until q.length()) {
            val t = Ui.body(this, "・" + q.getString(i), Ui.WARN, 14f)
            t.layoutParams = Ui.params(this, if (i == 0) 0 else 6)
            qc.addView(t)
            qsb.append(q.getString(i)).append("\n")
        }
        col.addView(qc)

        col.addView(Ui.button(this, "質問をコピー", Ui.SUB) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("questions", qsb.toString().trim()))
            Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
        })

        col.addView(Ui.button(this, "一覧に戻る", Ui.SUB) {
            detail = -1
            render()
        })
        return scroll
    }
}
