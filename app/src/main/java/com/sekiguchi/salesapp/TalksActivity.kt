package com.sekiguchi.salesapp

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 機能7: 好評だった営業トーク集 と 反省事例集（社内情報を除く） */
class TalksActivity : Activity() {

    private var mode = 0            // 0=一覧 1=新規追加 2=詳細
    private var filter = "all"      // all / good / review / prompt
    private var detailIndex = -1

    private var kindOfNew = "good"

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
        when (mode) {
            1 -> setContentView(buildNew())
            2 -> setContentView(buildDetail())
            else -> setContentView(buildList())
        }
    }

    private fun kindLabel(k: String): String = when (k) {
        "good" -> "好評トーク"
        "review" -> "反省事例"
        "prompt" -> "生成プロンプト"
        else -> "その他"
    }

    private fun kindColor(k: String): Int = when (k) {
        "good" -> Ui.ACCENT
        "review" -> Ui.WARN
        else -> Ui.SUB
    }

    // ---------- 一覧 ----------

    private fun buildList(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "トーク集・反省事例"))
        col.addView(Ui.body(this, "社内情報は入れない。数字や固有名詞は一般化して残す。", Ui.SUB, 13f))

        col.addView(Ui.button(this, "＋ 新しく記録する") {
            kindOfNew = "good"
            mode = 1
            render()
        })

        // フィルタ
        val row = android.widget.LinearLayout(this)
        row.orientation = android.widget.LinearLayout.HORIZONTAL
        row.layoutParams = Ui.params(this, 12, 4)
        val filters = listOf(
            Pair("all", "すべて"), Pair("good", "好評"),
            Pair("review", "反省"), Pair("prompt", "プロンプト")
        )
        for (f in filters) {
            val b = Ui.button(this, f.second, if (filter == f.first) Ui.ACCENT else Ui.SUB) {
                filter = f.first
                render()
            }
            b.textSize = 13f
            val lp = android.widget.LinearLayout.LayoutParams(0, Ui.WC, 1f)
            lp.rightMargin = Ui.dp(this, 4)
            b.layoutParams = lp
            row.addView(b)
        }
        col.addView(row)

        val arr = Store.talks(this)
        var shown = 0
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            val kind = o.optString("kind", "prompt")
            if (filter != "all" && filter != kind) continue
            shown++

            val idx = i
            val card = Ui.card(this)
            card.isClickable = true
            card.setOnClickListener {
                detailIndex = idx
                mode = 2
                render()
            }

            val head = Ui.body(this, kindLabel(kind), kindColor(kind), 12f)
            head.typeface = Typeface.DEFAULT_BOLD
            card.addView(head)

            val t = Ui.body(this, o.optString("title", "（無題）"), Ui.TEXT, 16f)
            t.typeface = Typeface.DEFAULT_BOLD
            card.addView(t)

            val bodyText = o.optString("body", "")
            val preview = if (bodyText.length > 60) bodyText.substring(0, 60) + "…" else bodyText
            card.addView(Ui.body(this, preview, Ui.SUB, 13f))
            card.addView(Ui.body(this, fmt(o.optLong("at", 0L)), Ui.SUB, 11f))
            col.addView(card)
        }

        if (shown == 0) {
            val card = Ui.card(this)
            card.addView(Ui.body(this, "まだ記録がありません。", Ui.SUB))
            col.addView(card)
        }

        return scroll
    }

    // ---------- 新規追加 ----------

    private fun buildNew(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "記録する"))

        // 種別
        col.addView(Ui.heading(this, "種別"))
        val kindRow = android.widget.LinearLayout(this)
        kindRow.orientation = android.widget.LinearLayout.HORIZONTAL
        kindRow.layoutParams = Ui.params(this, 4, 4)
        for (k in listOf(Pair("good", "好評トーク"), Pair("review", "反省事例"))) {
            val b = Ui.button(this, k.second, if (kindOfNew == k.first) Ui.ACCENT else Ui.SUB) {
                kindOfNew = k.first
                render()
            }
            val lp = android.widget.LinearLayout.LayoutParams(0, Ui.WC, 1f)
            lp.rightMargin = Ui.dp(this, 6)
            b.layoutParams = lp
            kindRow.addView(b)
        }
        col.addView(kindRow)

        // 場面（prompt_library のシチュエーションを流用）
        col.addView(Ui.heading(this, "場面"))
        val titles = ArrayList<String>()
        try {
            val lib = Store.asset(this, "prompt_library.json")
            val sits = lib.getJSONArray("situations")
            for (i in 0 until sits.length()) titles.add(sits.getJSONObject(i).getString("title"))
        } catch (e: Exception) {
            // assets が読めなくても記録はできるようにする
        }
        titles.add("その他")

        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, titles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.layoutParams = Ui.params(this, 4, 4)
        col.addView(spinner)

        // 本文
        col.addView(Ui.heading(this, "内容"))
        col.addView(Ui.body(this,
            if (kindOfNew == "good") "実際に効いた言い回しを、そのまま書く。"
            else "何が起きて、次はどうするかまで書く。", Ui.SUB, 13f))
        val bodyInput: EditText = Ui.input(this, "", "", 8)
        col.addView(bodyInput)

        val warnBox = android.widget.LinearLayout(this)
        warnBox.orientation = android.widget.LinearLayout.VERTICAL
        warnBox.layoutParams = Ui.params(this, 4)
        col.addView(warnBox)

        col.addView(Ui.button(this, "保存") {
            val text = bodyInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "内容が空です", Toast.LENGTH_SHORT).show()
            } else {
                warnBox.removeAllViews()
                val hits = Leak.check(text)
                if (hits.isNotEmpty()) {
                    val wc = Ui.card(this, Ui.WARN_BG, Ui.WARN)
                    wc.addView(Ui.body(this,
                        "社内情報らしき記述を検出：" + hits.joinToString("、") +
                            "\n一般名称に置き換えてから保存することを推奨します。もう一度押すと保存します。",
                        Ui.WARN, 13f))
                    warnBox.addView(wc)
                    // 2回目の押下で保存させるため、フラグ代わりにタグを使う
                    if (warnAcknowledged) {
                        doSave(spinner.selectedItem.toString(), text)
                    } else {
                        warnAcknowledged = true
                    }
                } else {
                    doSave(spinner.selectedItem.toString(), text)
                }
            }
        })

        col.addView(Ui.button(this, "戻る", Ui.SUB) {
            mode = 0
            warnAcknowledged = false
            render()
        })
        return scroll
    }

    private var warnAcknowledged = false

    private fun doSave(title: String, text: String) {
        Store.addTalk(this, kindOfNew, title, text)
        warnAcknowledged = false
        mode = 0
        Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
        render()
    }

    // ---------- 詳細 ----------

    private fun buildDetail(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        val arr = Store.talks(this)
        if (detailIndex < 0 || detailIndex >= arr.length()) {
            mode = 0
            return buildList()
        }
        val o: JSONObject = arr.getJSONObject(detailIndex)
        val kind = o.optString("kind", "prompt")

        col.addView(Ui.body(this, kindLabel(kind), kindColor(kind), 13f))
        col.addView(Ui.title(this, o.optString("title", "（無題）")))
        col.addView(Ui.body(this, fmt(o.optLong("at", 0L)), Ui.SUB, 12f))

        val card = Ui.card(this)
        card.addView(Ui.body(this, o.optString("body", "")))
        col.addView(card)

        col.addView(Ui.button(this, "コピー") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("talk", o.optString("body", "")))
            Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
        })

        col.addView(Ui.button(this, "削除", Ui.DANGER) {
            AlertDialog.Builder(this)
                .setTitle("削除")
                .setMessage("この記録を削除します。元に戻せません。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除") { _, _ ->
                    Store.deleteTalk(this, detailIndex)
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
        return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(ms))
    }
}
