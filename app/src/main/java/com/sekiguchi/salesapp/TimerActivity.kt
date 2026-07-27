package com.sekiguchi.salesapp

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.TextView

/**
 * 商談タイマー。
 * 喋りすぎて聞けずに終わる失敗を減らすためのもの。
 * 権限ゼロを保つため、振動や通知は使わず画面表示のみ。
 */
class TimerActivity : Activity() {

    private val phases = listOf(
        Triple("導入", 0.17, "自己紹介と本題への移行。長くしない"),
        Triple("ヒアリング", 0.50, "ここが最長。相手に喋らせる"),
        Triple("提案", 0.27, "聞いた課題に紐づけて話す"),
        Triple("クロージング", 0.06, "次アクションを必ず取り付ける")
    )

    private var totalMinutes = 30
    private var startedAt = 0L
    private var running = false

    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    private var clockView: TextView? = null
    private var phaseView: TextView? = null
    private var detailView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTicker()
    }

    private fun stopTicker() {
        val t = ticker
        if (t != null) handler.removeCallbacks(t)
        ticker = null
    }

    private fun render() {
        stopTicker()
        setContentView(if (running) buildRunning() else buildSetup())
    }

    // ---------- 設定 ----------

    private fun buildSetup(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "商談タイマー"))
        col.addView(Ui.body(this, "持ち時間を4つに配分して、残りを表示します。", Ui.SUB, 13f))

        col.addView(Ui.heading(this, "持ち時間"))
        val card = Ui.card(this)
        val input = Ui.input(this, "分", totalMinutes.toString())
        input.inputType = InputType.TYPE_CLASS_NUMBER
        card.addView(input)
        col.addView(card)

        val quick = android.widget.LinearLayout(this)
        quick.orientation = android.widget.LinearLayout.HORIZONTAL
        quick.layoutParams = Ui.params(this, 6, 4)
        for (m in listOf(15, 30, 45, 60)) {
            val b = Ui.button(this, m.toString() + "分", Ui.SUB) {
                input.setText(m.toString())
            }
            b.textSize = 14f
            val lp = android.widget.LinearLayout.LayoutParams(0, Ui.WC, 1f)
            lp.rightMargin = Ui.dp(this, 4)
            b.layoutParams = lp
            quick.addView(b)
        }
        col.addView(quick)

        col.addView(Ui.heading(this, "配分の目安"))
        val pc = Ui.card(this)
        val m = input.text.toString().toIntOrNull() ?: totalMinutes
        for (p in phases) {
            val mins = Math.max(1, Math.round(m * p.second).toInt())
            val t = Ui.body(this, p.first + "　" + mins + "分　　" + p.third, Ui.TEXT, 14f)
            t.layoutParams = Ui.params(this, 6)
            pc.addView(t)
        }
        col.addView(pc)

        col.addView(Ui.button(this, "開始") {
            totalMinutes = input.text.toString().toIntOrNull() ?: 30
            if (totalMinutes < 1) totalMinutes = 1
            startedAt = System.currentTimeMillis()
            running = true
            render()
        })
        return scroll
    }

    // ---------- 計測中 ----------

    private fun buildRunning(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "残り時間"))

        val big = TextView(this)
        big.textSize = 52f
        big.typeface = Typeface.DEFAULT_BOLD
        big.setTextColor(Ui.ACCENT)
        big.layoutParams = Ui.params(this, 8, 4)
        clockView = big
        col.addView(big)

        val ph = Ui.body(this, "", Ui.TEXT, 20f)
        ph.typeface = Typeface.DEFAULT_BOLD
        phaseView = ph
        col.addView(ph)

        val de = Ui.body(this, "", Ui.SUB, 14f)
        detailView = de
        col.addView(de)

        col.addView(Ui.heading(this, "全体の配分"))
        val pc = Ui.card(this)
        for (p in phases) {
            val mins = Math.max(1, Math.round(totalMinutes * p.second).toInt())
            val t = Ui.body(this, p.first + "　" + mins + "分", Ui.SUB, 14f)
            t.layoutParams = Ui.params(this, 6)
            pc.addView(t)
        }
        col.addView(pc)

        col.addView(Ui.button(this, "終了", Ui.DANGER) {
            running = false
            render()
        })

        tick()
        val r = object : Runnable {
            override fun run() {
                tick()
                handler.postDelayed(this, 1000L)
            }
        }
        ticker = r
        handler.postDelayed(r, 1000L)

        return scroll
    }

    private fun tick() {
        val elapsedSec = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
        val totalSec = totalMinutes * 60
        val leftSec = totalSec - elapsedSec

        val cv = clockView
        if (cv != null) {
            val abs = Math.abs(leftSec)
            val text = (abs / 60).toString() + ":" + String.format("%02d", abs % 60)
            cv.text = if (leftSec < 0) "-" + text else text
            cv.setTextColor(
                when {
                    leftSec < 0 -> Ui.DANGER
                    leftSec < 180 -> Ui.WARN
                    else -> Ui.ACCENT
                }
            )
        }

        // 現在のフェーズを求める
        var acc = 0
        var name = phases[phases.size - 1].first
        var advice = phases[phases.size - 1].third
        var phaseLeft = 0
        for (p in phases) {
            val len = Math.max(60, Math.round(totalSec * p.second).toInt())
            if (elapsedSec < acc + len) {
                name = p.first
                advice = p.third
                phaseLeft = acc + len - elapsedSec
                break
            }
            acc += len
        }

        val pv = phaseView
        if (pv != null) {
            pv.text = if (leftSec < 0) "時間超過" else "いま：" + name
            pv.setTextColor(if (leftSec < 0) Ui.DANGER else Ui.TEXT)
        }
        val dv = detailView
        if (dv != null) {
            dv.text = if (leftSec < 0) "切り上げて次アクションを決める"
            else advice + "　（この段階の残り " + (phaseLeft / 60) + "分）"
        }
    }
}
