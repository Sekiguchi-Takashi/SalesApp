package com.sekiguchi.salesapp

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import org.json.JSONObject

/**
 * 切り返し即引き。
 * 機能2が「AIに考えさせる準備」なのに対し、こちらは商談中にその場で開くもの。
 * 用途が違うので画面を分けている。
 */
class RebuttalActivity : Activity() {

    private var root: JSONObject? = null
    private var current = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = try {
            Store.asset(this, "rebuttals.json")
        } catch (e: Exception) {
            null
        }
        render()
    }

    override fun onBackPressed() {
        if (current >= 0) {
            current = -1
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
            c.addView(Ui.body(this, "rebuttals.json を読み込めませんでした。"))
            setContentView(s)
            return
        }
        if (current >= 0) setContentView(buildDetail(r)) else setContentView(buildList(r))
    }

    private fun buildList(r: JSONObject): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "切り返し"))
        col.addView(Ui.body(this, "言われた言葉を選ぶ。", Ui.SUB, 13f))

        val items = r.getJSONArray("items")
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val index = i
            col.addView(Ui.bigButton(this, o.getString("trigger"), "", Ui.ACCENT) {
                current = index
                render()
            })
        }
        return scroll
    }

    private fun buildDetail(r: JSONObject): android.view.View {
        val (scroll, col) = Ui.screen(this)
        val o = r.getJSONArray("items").getJSONObject(current)

        col.addView(Ui.title(this, "「" + o.getString("trigger") + "」"))

        val hint = Ui.card(this, Ui.WARN_BG, Ui.WARN)
        hint.addView(Ui.body(this, o.optString("hint"), Ui.WARN, 14f))
        col.addView(hint)

        col.addView(Ui.heading(this, "返し方"))
        val replies = o.getJSONArray("replies")
        for (i in 0 until replies.length()) {
            val card = Ui.card(this)
            val n = Ui.body(this, (i + 1).toString(), Ui.ACCENT, 12f)
            n.typeface = Typeface.DEFAULT_BOLD
            card.addView(n)
            card.addView(Ui.body(this, replies.getString(i), Ui.TEXT, 15f))
            col.addView(card)
        }

        val avoid = o.optJSONArray("avoid")
        if (avoid != null && avoid.length() > 0) {
            col.addView(Ui.heading(this, "やってはいけない"))
            val ac = Ui.card(this, Ui.DANGER_BG, Ui.DANGER)
            for (i in 0 until avoid.length()) {
                val t = Ui.body(this, "× " + avoid.getString(i), Ui.DANGER, 14f)
                t.layoutParams = Ui.params(this, if (i == 0) 0 else 8)
                ac.addView(t)
            }
            col.addView(ac)
        }

        // 機能7に貯めた自分の実績トークを、同じ場面として引ける
        col.addView(Ui.heading(this, "自分の記録"))
        val talks = Store.talks(this)
        val myCard = Ui.card(this)
        var found = 0
        for (i in talks.length() - 1 downTo 0) {
            val t = talks.getJSONObject(i)
            if (t.optString("kind") != "good") continue
            found++
            if (found > 3) break
            myCard.addView(Ui.body(this, "・" + t.optString("body"), Ui.TEXT, 14f))
        }
        if (found == 0) {
            myCard.addView(Ui.body(this,
                "好評だったトークを記録しておくと、ここに出ます。", Ui.SUB, 13f))
        }
        col.addView(myCard)

        col.addView(Ui.button(this, "一覧に戻る", Ui.SUB) {
            current = -1
            render()
        })
        return scroll
    }
}
