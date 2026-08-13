package com.sekiguchi.salesapp

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 案件トラッカー。
 *
 * 設計書の「商談管理」に相当するが、顧客名・社名・担当者名は入力させない。
 * 持つのは 自分用ラベル / 業種 / 段階 / 次アクション / 期限 だけ。
 *
 * それでも会社の営業活動に由来するデータなので company-derived タグを付け、
 * 退職時削除の対象にしている。ここを個人データ扱いにすると、
 * このアプリ全体の前提が崩れる。
 */
class DealActivity : Activity() {

    private var mode = 0        // 0=一覧 1=追加 2=詳細
    private var detail = -1

    private val stages = listOf("接触", "把握", "提案", "条件", "決裁", "納入", "定着")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onBackPressed() {
        if (mode != 0) {
            mode = 0
            render()
        } else {
            super.onBackPressed()
        }
    }

    private fun render() {
        setContentView(
            when (mode) {
                1 -> buildAdd()
                2 -> buildDetail()
                else -> buildList()
            }
        )
    }

    private fun deals(): JSONArray = try {
        JSONArray(Store.get(this, "deals", "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    private fun save(arr: JSONArray) = Store.put(this, "deals", arr.toString())

    private fun daysSince(ms: Long): Int {
        if (ms <= 0L) return 0
        return ((System.currentTimeMillis() - ms) / 86400000L).toInt()
    }

    // ---------- 一覧 ----------

    private fun buildList(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "案件"))
        col.addView(Ui.body(this,
            "社名・担当者名は入れない。自分だけが分かるラベルで管理する。", Ui.SUB, 13f))

        col.addView(Ui.button(this, "＋ 案件を追加") {
            mode = 1
            render()
        })

        val arr = deals()

        // 停滞検知
        val stalled = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optBoolean("closed", false)) continue
            val d = daysSince(o.optLong("touched", 0L))
            if (d >= 14) stalled.add(o.optString("label") + "（" + d + "日）")
        }
        if (stalled.isNotEmpty()) {
            col.addView(Ui.heading(this, "止まっている"))
            val wc = Ui.card(this, Ui.WARN_BG, Ui.WARN)
            for (s in stalled) wc.addView(Ui.body(this, "・" + s, Ui.WARN, 14f))
            wc.addView(Ui.body(this,
                "2週間動いていない案件です。動かすか、落とすかを決めてください。", Ui.WARN, 12f))
            col.addView(wc)
        }

        col.addView(Ui.heading(this, "進行中"))
        var open = 0
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            if (o.optBoolean("closed", false)) continue
            open++
            col.addView(dealCard(o, i))
        }
        if (open == 0) {
            val c = Ui.card(this)
            c.addView(Ui.body(this, "進行中の案件はありません。", Ui.SUB))
            col.addView(c)
        }

        var closed = 0
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optBoolean("closed", false)) closed++
        }
        if (closed > 0) {
            col.addView(Ui.heading(this, "終了した案件"))
            for (i in arr.length() - 1 downTo 0) {
                val o = arr.getJSONObject(i)
                if (!o.optBoolean("closed", false)) continue
                col.addView(dealCard(o, i))
            }
        }

        col.addView(Ui.divider(this))
        col.addView(Ui.body(this,
            "案件データは company-derived タグです。トップページの「会社由来データを削除」で" +
                "まとめて消えます。", Ui.SUB, 12f))
        return scroll
    }

    private fun dealCard(o: JSONObject, index: Int): android.widget.LinearLayout {
        val card = Ui.card(this)
        card.isClickable = true
        card.setOnClickListener {
            detail = index
            mode = 2
            render()
        }

        val t = Ui.body(this, o.optString("label", "（無題）"), Ui.TEXT, 17f)
        t.typeface = Typeface.DEFAULT_BOLD
        card.addView(t)

        val sub = StringBuilder()
        sub.append(o.optString("stage", "接触"))
        val ind = o.optString("industry")
        if (ind.isNotEmpty()) sub.append("　/　").append(ind)
        card.addView(Ui.body(this, sub.toString(), Ui.ACCENT, 13f))

        val next = o.optString("next")
        if (next.isNotEmpty()) card.addView(Ui.body(this, "次： " + next, Ui.TEXT, 14f))

        val d = daysSince(o.optLong("touched", 0L))
        card.addView(Ui.body(this,
            "最終更新 " + d + "日前", if (d >= 14) Ui.WARN else Ui.SUB, 12f))
        return card
    }

    // ---------- 追加 ----------

    private fun buildAdd(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "案件を追加"))

        val warn = Ui.card(this, Ui.WARN_BG, Ui.WARN)
        warn.addView(Ui.body(this,
            "社名・担当者名・型番は入れないでください。" +
                "「駅前の工場」「紹介の件」のように、自分だけが分かる呼び方にします。",
            Ui.WARN, 13f))
        col.addView(warn)

        val card = Ui.card(this)
        card.addView(Ui.label(this, "自分用ラベル"))
        val label = Ui.input(this, "例：南部の設備更新", "")
        card.addView(label)

        card.addView(Ui.label(this, "業種（一般名称）"))
        val industry = Ui.input(this, "例：食品製造", "")
        card.addView(industry)

        card.addView(Ui.label(this, "段階"))
        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, stages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.layoutParams = Ui.params(this, 4, 4)
        card.addView(spinner)

        card.addView(Ui.label(this, "次にやること"))
        val next = Ui.input(this, "例：見積の前提を確認", "", 2)
        card.addView(next)
        col.addView(card)

        col.addView(Ui.button(this, "保存") {
            val l = label.text.toString().trim()
            val joined = l + " " + industry.text.toString() + " " + next.text.toString()
            if (l.isEmpty()) {
                Toast.makeText(this, "ラベルを入力してください", Toast.LENGTH_SHORT).show()
            } else if (Leak.check(joined).isNotEmpty()) {
                Toast.makeText(this,
                    "社名・型番らしき記述があります： " + Leak.check(joined).joinToString("、"),
                    Toast.LENGTH_LONG).show()
            } else {
                val arr = deals()
                val o = JSONObject()
                o.put("label", l)
                o.put("industry", industry.text.toString().trim())
                o.put("stage", stages[spinner.selectedItemPosition])
                o.put("next", next.text.toString().trim())
                o.put("touched", System.currentTimeMillis())
                o.put("created", System.currentTimeMillis())
                o.put("closed", false)
                o.put("tag", "company-derived")
                arr.put(o)
                save(arr)
                mode = 0
                render()
            }
        })

        col.addView(Ui.button(this, "戻る", Ui.SUB) {
            mode = 0
            render()
        })
        return scroll
    }

    // ---------- 詳細 ----------

    private fun buildDetail(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        val arr = deals()
        if (detail < 0 || detail >= arr.length()) {
            mode = 0
            return buildList()
        }
        val o = arr.getJSONObject(detail)

        col.addView(Ui.title(this, o.optString("label")))
        col.addView(Ui.body(this,
            o.optString("stage") + "　/　" + o.optString("industry"), Ui.ACCENT, 14f))
        col.addView(Ui.body(this,
            "登録 " + fmt(o.optLong("created", 0L)) +
                "　最終更新 " + daysSince(o.optLong("touched", 0L)) + "日前", Ui.SUB, 12f))

        col.addView(Ui.heading(this, "段階を進める"))
        val sc = Ui.card(this)
        val idx = stages.indexOf(o.optString("stage", "接触"))
        for (i in stages.indices) {
            val name = stages[i]
            val b = Ui.button(this, name, if (i == idx) Ui.ACCENT else Ui.SUB) {
                o.put("stage", name)
                o.put("touched", System.currentTimeMillis())
                save(arr)
                render()
            }
            b.textSize = 14f
            sc.addView(b)
        }
        col.addView(sc)

        col.addView(Ui.heading(this, "次にやること"))
        val nc = Ui.card(this)
        val next = Ui.input(this, "", o.optString("next"), 3)
        nc.addView(next)
        nc.addView(Ui.button(this, "更新") {
            val v = next.text.toString().trim()
            if (Leak.check(v).isNotEmpty()) {
                Toast.makeText(this, "社名・型番らしき記述があります", Toast.LENGTH_LONG).show()
            } else {
                o.put("next", v)
                o.put("touched", System.currentTimeMillis())
                save(arr)
                Toast.makeText(this, "更新しました", Toast.LENGTH_SHORT).show()
                render()
            }
        })
        col.addView(nc)

        col.addView(Ui.button(this, "この案件でAIに相談するプロンプトを作る", Ui.ACCENT) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val sb = StringBuilder()
            sb.append("業界：").append(o.optString("industry")).append("\n")
            sb.append("段階：").append(o.optString("stage")).append("\n")
            sb.append("次にやること：").append(o.optString("next")).append("\n")
            sb.append("最終接触からの日数：").append(daysSince(o.optLong("touched", 0L))).append("日\n")
            sb.append("\n上の状況をもとに、営業プロンプト画面の「推論」から質問を選んで貼ってください。")
            cm.setPrimaryClip(ClipData.newPlainText("deal", sb.toString()))
            Toast.makeText(this, "状況をコピーしました", Toast.LENGTH_SHORT).show()
        })

        if (!o.optBoolean("closed", false)) {
            col.addView(Ui.button(this, "この案件を終了する", Ui.SUB) {
                o.put("closed", true)
                o.put("touched", System.currentTimeMillis())
                save(arr)
                mode = 0
                render()
            })
        } else {
            col.addView(Ui.button(this, "進行中に戻す", Ui.SUB) {
                o.put("closed", false)
                o.put("touched", System.currentTimeMillis())
                save(arr)
                render()
            })
        }

        col.addView(Ui.button(this, "削除", Ui.DANGER) {
            AlertDialog.Builder(this)
                .setTitle("削除")
                .setMessage("この案件を削除します。元に戻せません。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除") { _, _ ->
                    val out = JSONArray()
                    for (i in 0 until arr.length()) {
                        if (i != detail) out.put(arr.getJSONObject(i))
                    }
                    save(out)
                    mode = 0
                    render()
                }
                .show()
        })

        col.addView(Ui.button(this, "一覧に戻る", Ui.SUB) {
            mode = 0
            render()
        })
        return scroll
    }

    private fun fmt(ms: Long): String {
        if (ms <= 0L) return ""
        return SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).format(Date(ms))
    }
}
