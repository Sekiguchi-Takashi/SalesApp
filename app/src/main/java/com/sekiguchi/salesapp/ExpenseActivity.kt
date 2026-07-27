package com.sekiguchi.salesapp

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 走行距離・経費メモ。
 * 自分の経費精算と確定申告のための個人記録なので、帰属の争点にならない。
 * 訪問先はエリア名のみ。顧客名は入力させない。
 */
class ExpenseActivity : Activity() {

    private var adding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onBackPressed() {
        if (adding) {
            adding = false
            render()
        } else {
            super.onBackPressed()
        }
    }

    private fun render() {
        setContentView(if (adding) buildAdd() else buildList())
    }

    private fun items() = try {
        org.json.JSONArray(Store.get(this, "expenses", "[]"))
    } catch (e: Exception) {
        org.json.JSONArray()
    }

    private fun buildList(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "走行距離・経費"))
        col.addView(Ui.body(this, "自分の精算用。顧客名は入れない。", Ui.SUB, 13f))

        col.addView(Ui.button(this, "＋ 記録する") {
            adding = true
            render()
        })

        val arr = items()
        var km = 0.0
        var yen = 0.0
        val month = SimpleDateFormat("yyyy/MM", Locale.JAPAN).format(Date())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (SimpleDateFormat("yyyy/MM", Locale.JAPAN).format(Date(o.optLong("at", 0L))) == month) {
                km += o.optDouble("km", 0.0)
                yen += o.optDouble("yen", 0.0)
            }
        }

        col.addView(Ui.heading(this, "今月の合計"))
        val sum = Ui.card(this)
        sum.addView(Ui.body(this, "走行 " + String.format("%.1f", km) + " km", Ui.TEXT, 17f))
        sum.addView(Ui.body(this, "経費 " + String.format("%,.0f", yen) + " 円", Ui.TEXT, 17f))
        col.addView(sum)

        col.addView(Ui.button(this, "今月分をコピー（精算用）", Ui.SUB) {
            val sb = StringBuilder()
            sb.append(month).append(" 走行・経費\n")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val at = o.optLong("at", 0L)
                if (SimpleDateFormat("yyyy/MM", Locale.JAPAN).format(Date(at)) != month) continue
                sb.append(SimpleDateFormat("MM/dd", Locale.JAPAN).format(Date(at)))
                    .append("　").append(o.optString("area"))
                    .append("　").append(String.format("%.1f", o.optDouble("km", 0.0))).append("km")
                    .append("　").append(String.format("%,.0f", o.optDouble("yen", 0.0))).append("円")
                    .append("　").append(o.optString("memo")).append("\n")
            }
            sb.append("合計　").append(String.format("%.1f", km)).append("km　")
                .append(String.format("%,.0f", yen)).append("円")
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("expense", sb.toString()))
            Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
        })

        col.addView(Ui.heading(this, "履歴"))
        if (arr.length() == 0) {
            val c = Ui.card(this)
            c.addView(Ui.body(this, "まだ記録がありません。", Ui.SUB))
            col.addView(c)
        }
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            val index = i
            val card = Ui.card(this)
            card.addView(Ui.body(this,
                SimpleDateFormat("MM/dd", Locale.JAPAN).format(Date(o.optLong("at", 0L))) +
                    "　" + o.optString("area"), Ui.TEXT, 15f))
            card.addView(Ui.body(this,
                String.format("%.1f", o.optDouble("km", 0.0)) + " km　" +
                    String.format("%,.0f", o.optDouble("yen", 0.0)) + " 円", Ui.SUB, 14f))
            val memo = o.optString("memo")
            if (memo.isNotEmpty()) card.addView(Ui.body(this, memo, Ui.SUB, 13f))
            card.addView(Ui.button(this, "削除", Ui.SUB) {
                AlertDialog.Builder(this)
                    .setTitle("削除")
                    .setMessage("この記録を削除します。")
                    .setNegativeButton("キャンセル", null)
                    .setPositiveButton("削除") { _, _ ->
                        val src = items()
                        val out = org.json.JSONArray()
                        for (j in 0 until src.length()) {
                            if (j != index) out.put(src.getJSONObject(j))
                        }
                        Store.put(this, "expenses", out.toString())
                        render()
                    }
                    .show()
            })
            col.addView(card)
        }
        return scroll
    }

    private fun buildAdd(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "記録する"))

        val card = Ui.card(this)
        card.addView(Ui.label(this, "エリア（地名。顧客名は入れない）"))
        val area = Ui.input(this, "例：尼崎", "")
        card.addView(area)

        card.addView(Ui.label(this, "走行距離（km）"))
        val km = Ui.input(this, "", "")
        km.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        card.addView(km)

        card.addView(Ui.label(this, "経費（円）　高速・駐車場・手土産など"))
        val yen = Ui.input(this, "", "")
        yen.inputType = InputType.TYPE_CLASS_NUMBER
        card.addView(yen)

        card.addView(Ui.label(this, "メモ"))
        val memo = Ui.input(this, "例：高速代", "", 2)
        card.addView(memo)
        col.addView(card)

        col.addView(Ui.button(this, "保存") {
            val a = area.text.toString().trim()
            if (a.isEmpty()) {
                Toast.makeText(this, "エリアを入力してください", Toast.LENGTH_SHORT).show()
            } else if (Leak.check(a + " " + memo.text.toString()).isNotEmpty()) {
                Toast.makeText(this, "社名らしき文字が含まれています。地名・一般名にしてください",
                    Toast.LENGTH_LONG).show()
            } else {
                val arr = items()
                val o = JSONObject()
                o.put("at", System.currentTimeMillis())
                o.put("area", a)
                o.put("km", km.text.toString().toDoubleOrNull() ?: 0.0)
                o.put("yen", yen.text.toString().toDoubleOrNull() ?: 0.0)
                o.put("memo", memo.text.toString().trim())
                o.put("tag", "personal")
                arr.put(o)
                Store.put(this, "expenses", arr.toString())
                Store.addArea(this, a)
                adding = false
                Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
                render()
            }
        })

        col.addView(Ui.button(this, "戻る", Ui.SUB) {
            adding = false
            render()
        })
        return scroll
    }
}
