package com.sekiguchi.salesapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/**
 * 概算計算機。
 * 客先で「月々いくら」に即答できないと商談が止まるための道具。
 * 数式だけなので会社情報を一切含まない。
 */
class CalcActivity : Activity() {

    private var mode = 0   // 0=一覧 1=リース 2=回収 3=値引きと粗利 4=電気代

    private val fields = HashMap<String, EditText>()
    private var resultText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onBackPressed() {
        if (mode != 0) {
            mode = 0
            resultText = ""
            render()
        } else {
            super.onBackPressed()
        }
    }

    private fun render() {
        setContentView(if (mode == 0) buildList() else buildCalc())
    }

    private fun buildList(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "概算計算"))
        col.addView(Ui.body(this, "その場で答えるための概算。正式な見積は社内手続きで。", Ui.SUB, 13f))

        col.addView(Ui.bigButton(this, "リース料の概算", "本体価格から月額を出す", Ui.ACCENT) {
            mode = 1; resultText = ""; render()
        })
        col.addView(Ui.bigButton(this, "投資回収年数", "削減額から何年で回収できるか", Ui.ACCENT) {
            mode = 2; resultText = ""; render()
        })
        col.addView(Ui.bigButton(this, "値引きと粗利", "この値引きで粗利がどう動くか", Ui.ACCENT) {
            mode = 3; resultText = ""; render()
        })
        col.addView(Ui.bigButton(this, "電気代の試算", "消費電力から年間コスト", Ui.ACCENT) {
            mode = 4; resultText = ""; render()
        })
        return scroll
    }

    private fun num(c: Context, hint: String, key: String, parent: LinearLayout, decimal: Boolean = false) {
        parent.addView(Ui.label(this, hint))
        val e = Ui.input(this, "", "")
        e.inputType = if (decimal)
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        else
            InputType.TYPE_CLASS_NUMBER
        fields[key] = e
        parent.addView(e)
    }

    private fun v(key: String): Double {
        val e = fields[key] ?: return 0.0
        return e.text.toString().trim().toDoubleOrNull() ?: 0.0
    }

    private fun yen(x: Double): String {
        val r = Math.round(x)
        return String.format("%,d", r) + "円"
    }

    private fun buildCalc(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        fields.clear()

        val card = Ui.card(this)

        when (mode) {
            1 -> {
                col.addView(Ui.title(this, "リース料の概算"))
                col.addView(Ui.body(this,
                    "月額 ＝ 本体価格 × リース料率。料率はリース会社と期間で決まります。", Ui.SUB, 13f))
                num(this, "本体価格（円）", "price", card)
                num(this, "リース期間（月）　例 60", "months", card)
                num(this, "リース料率（％／月）　例 1.8", "rate", card, true)
            }
            2 -> {
                col.addView(Ui.title(this, "投資回収年数"))
                col.addView(Ui.body(this, "導入額を年間の削減額で割った単純回収年数です。", Ui.SUB, 13f))
                num(this, "導入額（円）", "invest", card)
                num(this, "年間削減額（円）", "save", card)
            }
            3 -> {
                col.addView(Ui.title(this, "値引きと粗利"))
                col.addView(Ui.body(this, "値引き前提で話す前に、自分で影響を把握しておく。", Ui.SUB, 13f))
                num(this, "定価・提示額（円）", "list", card)
                num(this, "仕入原価（円）", "cost", card)
                num(this, "値引き額（円）", "disc", card)
            }
            4 -> {
                col.addView(Ui.title(this, "電気代の試算"))
                col.addView(Ui.body(this, "消費電力・稼働時間・単価から年間コストを出します。", Ui.SUB, 13f))
                num(this, "消費電力（kW）", "kw", card, true)
                num(this, "1日の稼働時間（h）", "hours", card, true)
                num(this, "年間の稼働日数（日）　例 240", "days", card)
                num(this, "電力単価（円／kWh）　例 25", "unit", card, true)
            }
        }

        col.addView(card)

        col.addView(Ui.button(this, "計算する") {
            resultText = calc()
            render()
        })

        if (resultText.isNotEmpty()) {
            col.addView(Ui.heading(this, "結果"))
            val rc = Ui.card(this)
            rc.addView(Ui.body(this, resultText, Ui.TEXT, 16f))
            col.addView(rc)

            col.addView(Ui.button(this, "コピー", Ui.SUB) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("calc", resultText))
                Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
            })
        }

        val note = Ui.card(this, Ui.WARN_BG, Ui.WARN)
        note.addView(Ui.body(this,
            "概算です。実際の条件・税・手数料は含みません。客先に伝える際は概算である旨を必ず添えてください。",
            Ui.WARN, 13f))
        col.addView(note)

        col.addView(Ui.button(this, "戻る", Ui.SUB) {
            mode = 0
            resultText = ""
            render()
        })
        return scroll
    }

    private fun calc(): String {
        val sb = StringBuilder()
        when (mode) {
            1 -> {
                val price = v("price")
                val months = v("months")
                val rate = v("rate")
                if (price <= 0.0 || rate <= 0.0) return "本体価格とリース料率を入力してください。"
                val monthly = price * rate / 100.0
                sb.append("月額　約 ").append(yen(monthly)).append("\n")
                if (months > 0) {
                    val total = monthly * months
                    sb.append("総額　約 ").append(yen(total)).append("（").append(months.toInt()).append("か月）\n")
                    sb.append("総額 ÷ 本体価格　").append(String.format("%.2f", total / price)).append(" 倍\n")
                    sb.append("1日あたり　約 ").append(yen(monthly / 30.0))
                }
            }
            2 -> {
                val invest = v("invest")
                val save = v("save")
                if (invest <= 0.0 || save <= 0.0) return "導入額と年間削減額を入力してください。"
                val years = invest / save
                sb.append("回収年数　約 ").append(String.format("%.1f", years)).append(" 年\n")
                sb.append("　　　　　約 ").append(Math.round(years * 12)).append(" か月\n")
                sb.append("月あたり削減　約 ").append(yen(save / 12.0)).append("\n")
                sb.append("5年間の累計削減　約 ").append(yen(save * 5 - invest)).append("（導入額差引後）")
            }
            3 -> {
                val list = v("list")
                val cost = v("cost")
                val disc = v("disc")
                if (list <= 0.0 || cost <= 0.0) return "提示額と仕入原価を入力してください。"
                val before = list - cost
                val after = list - disc - cost
                val bRate = before / list * 100.0
                val aRate = if (list - disc > 0.0) after / (list - disc) * 100.0 else 0.0
                sb.append("値引き前　粗利 ").append(yen(before))
                    .append("（").append(String.format("%.1f", bRate)).append("％）\n")
                sb.append("値引き後　粗利 ").append(yen(after))
                    .append("（").append(String.format("%.1f", aRate)).append("％）\n")
                if (before > 0.0) {
                    val lost = (before - after) / before * 100.0
                    sb.append("粗利の減少　").append(String.format("%.1f", lost)).append("％\n")
                }
                if (after < 0.0) sb.append("\n※ 原価割れです。")
                else if (aRate < 10.0) sb.append("\n※ 粗利率が1割を切ります。")
            }
            4 -> {
                val kw = v("kw")
                val hours = v("hours")
                val days = v("days")
                val unit = v("unit")
                if (kw <= 0.0 || hours <= 0.0 || days <= 0.0 || unit <= 0.0)
                    return "すべての項目を入力してください。"
                val kwh = kw * hours * days
                val cost = kwh * unit
                sb.append("年間消費電力　約 ").append(String.format("%,.0f", kwh)).append(" kWh\n")
                sb.append("年間電気代　　約 ").append(yen(cost)).append("\n")
                sb.append("月あたり　　　約 ").append(yen(cost / 12.0)).append("\n")
                sb.append("1日あたり　　 約 ").append(yen(cost / days))
            }
        }
        return sb.toString()
    }
}
