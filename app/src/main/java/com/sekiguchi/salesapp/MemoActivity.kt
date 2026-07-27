package com.sekiguchi.salesapp

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * クイックメモ。
 * 訪問直後に手が塞がっている状態で残すための入口。
 * マイク権限は取らず、キーボードの音声入力ボタンを使ってもらう方式。
 * 保存先は機能7と同じトーク集。
 */
class MemoActivity : Activity() {

    private var kind = "good"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "クイックメモ"))
        col.addView(Ui.body(this,
            "キーボードのマイクボタンで話しても入力できます。あとでトーク集に残ります。",
            Ui.SUB, 13f))

        val row = android.widget.LinearLayout(this)
        row.orientation = android.widget.LinearLayout.HORIZONTAL
        row.layoutParams = Ui.params(this, 8, 4)
        for (k in listOf(Pair("good", "効いた"), Pair("review", "反省"))) {
            val b = Ui.button(this, k.second, if (kind == k.first) Ui.ACCENT else Ui.SUB) {
                kind = k.first
                render()
            }
            val lp = android.widget.LinearLayout.LayoutParams(0, Ui.WC, 1f)
            lp.rightMargin = Ui.dp(this, 6)
            b.layoutParams = lp
            row.addView(b)
        }
        col.addView(row)

        val input = Ui.input(this, "訪問直後に、思い出せるうちに", "", 8)
        col.addView(input)
        input.requestFocus()

        col.addView(Ui.button(this, "保存") {
            val text = input.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "内容が空です", Toast.LENGTH_SHORT).show()
            } else {
                val hits = Leak.check(text)
                if (hits.isNotEmpty()) {
                    Toast.makeText(this,
                        "社内情報らしき記述：" + hits.joinToString("、") + "　一般名称に直してください",
                        Toast.LENGTH_LONG).show()
                } else {
                    Store.addTalk(this, kind, "クイックメモ", text)
                    Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        })

        col.addView(Ui.button(this, "閉じる", Ui.SUB) { finish() })
        setContentView(scroll)
    }
}
