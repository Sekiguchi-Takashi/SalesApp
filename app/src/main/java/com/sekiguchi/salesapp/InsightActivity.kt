package com.sekiguchi.salesapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 分析。
 * 設計書の「分析ダッシュボード」「個人特性分析」に相当。
 * 分析対象は顧客ではなく自分の行動。これは完全に個人の資産になる。
 */
class InsightActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "分析"))
        col.addView(Ui.body(this, "分析するのは顧客ではなく自分の動き方。", Ui.SUB, 13f))

        // --- 記録の量 ---
        val talks = Store.talks(this)
        var good = 0
        var review = 0
        for (i in 0 until talks.length()) {
            when (talks.getJSONObject(i).optString("kind")) {
                "good" -> good++
                "review" -> review++
            }
        }

        col.addView(Ui.heading(this, "記録"))
        val rc = Ui.card(this)
        rc.addView(big(good.toString() + " 件", "効いたトーク"))
        rc.addView(big(review.toString() + " 件", "反省事例"))
        if (good + review == 0) {
            rc.addView(Ui.body(this,
                "記録がないと分析できません。クイックメモから残してください。", Ui.SUB, 13f))
        } else if (review > good * 2) {
            rc.addView(Ui.body(this,
                "反省の記録が多く、うまくいった時の記録が少ない状態です。" +
                    "成功パターンは失敗より言語化しにくいので、意識して残すと差が出ます。",
                Ui.SUB, 13f))
        }
        col.addView(rc)

        // --- 場面の偏り ---
        val counts = HashMap<String, Int>()
        for (i in 0 until talks.length()) {
            val t = talks.getJSONObject(i).optString("title", "その他")
            counts[t] = (counts[t] ?: 0) + 1
        }
        if (counts.isNotEmpty()) {
            col.addView(Ui.heading(this, "記録が多い場面"))
            val cc = Ui.card(this)
            val sorted = counts.entries.sortedByDescending { it.value }
            var shown = 0
            for (e in sorted) {
                if (shown >= 5) break
                shown++
                cc.addView(Ui.body(this, e.key + "　" + e.value + "件", Ui.TEXT, 14f))
            }
            cc.addView(Ui.body(this,
                "記録が偏っている場面は、得意か、苦戦しているかのどちらかです。", Ui.SUB, 12f))
            col.addView(cc)
        }

        // --- 案件の状況 ---
        val deals = try {
            JSONArray(Store.get(this, "deals", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        if (deals.length() > 0) {
            var open = 0
            var stalled = 0
            val byStage = HashMap<String, Int>()
            for (i in 0 until deals.length()) {
                val o = deals.getJSONObject(i)
                if (o.optBoolean("closed", false)) continue
                open++
                val st = o.optString("stage", "接触")
                byStage[st] = (byStage[st] ?: 0) + 1
                val d = ((System.currentTimeMillis() - o.optLong("touched", 0L)) / 86400000L).toInt()
                if (d >= 14) stalled++
            }
            col.addView(Ui.heading(this, "案件"))
            val dc = Ui.card(this)
            dc.addView(big(open.toString() + " 件", "進行中"))
            if (stalled > 0) {
                dc.addView(Ui.body(this,
                    "うち " + stalled + " 件が2週間動いていません。", Ui.WARN, 14f))
            }
            for (st in listOf("接触", "把握", "提案", "条件", "決裁", "納入", "定着")) {
                val n = byStage[st] ?: 0
                if (n > 0) dc.addView(Ui.body(this, st + "　" + n + "件", Ui.TEXT, 14f))
            }
            val early = (byStage["接触"] ?: 0) + (byStage["把握"] ?: 0)
            if (open >= 3 && early == open) {
                dc.addView(Ui.body(this,
                    "全て前半の段階に溜まっています。数を増やすより、1件を前に進めるほうが効きます。",
                    Ui.SUB, 13f))
            }
            col.addView(dc)
        }

        // --- 移動 ---
        val od = Store.statLines(this, "od")
        val stay = Store.statLines(this, "stay")
        if (od.isNotEmpty() || stay.isNotEmpty()) {
            col.addView(Ui.heading(this, "移動"))
            val mc = Ui.card(this)
            mc.addView(Ui.body(this,
                "区間データ " + od.size + "件　滞在データ " + stay.size + "件", Ui.TEXT, 14f))
            mc.addView(Ui.body(this,
                "実測が増えるほど、ルートの並び替えが正確になります。", Ui.SUB, 12f))
            col.addView(mc)
        }

        // --- 経費 ---
        val exp = try {
            JSONArray(Store.get(this, "expenses", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        if (exp.length() > 0) {
            val month = SimpleDateFormat("yyyy/MM", Locale.JAPAN).format(Date())
            var km = 0.0
            var yen = 0.0
            for (i in 0 until exp.length()) {
                val o = exp.getJSONObject(i)
                if (SimpleDateFormat("yyyy/MM", Locale.JAPAN)
                        .format(Date(o.optLong("at", 0L))) != month) continue
                km += o.optDouble("km", 0.0)
                yen += o.optDouble("yen", 0.0)
            }
            col.addView(Ui.heading(this, "今月の移動コスト"))
            val ec = Ui.card(this)
            ec.addView(big(String.format("%.1f", km) + " km", "走行"))
            ec.addView(big(String.format("%,.0f", yen) + " 円", "経費"))
            col.addView(ec)
        }

        // --- AIに渡す ---
        col.addView(Ui.heading(this, "AIに読ませる"))
        col.addView(Ui.body(this,
            "自分の記録をまとめてコピーし、営業プロンプトの「推論」→" +
                "「自分の成功パターンを言語化」に貼ると分析できます。", Ui.SUB, 13f))
        col.addView(Ui.button(this, "記録をまとめてコピー") {
            val sb = StringBuilder()
            for (i in 0 until talks.length()) {
                val o: JSONObject = talks.getJSONObject(i)
                val k = o.optString("kind")
                if (k != "good" && k != "review") continue
                sb.append(if (k == "good") "【効いた】" else "【反省】")
                sb.append(o.optString("title")).append("　")
                sb.append(o.optString("body")).append("\n\n")
            }
            if (sb.isEmpty()) {
                Toast.makeText(this, "記録がありません", Toast.LENGTH_SHORT).show()
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("insight", sb.toString().trim()))
                Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
            }
        })

        setContentView(scroll)
    }

    private fun big(value: String, label: String): android.widget.LinearLayout {
        val l = android.widget.LinearLayout(this)
        l.orientation = android.widget.LinearLayout.HORIZONTAL
        l.layoutParams = Ui.params(this, 6, 2)

        val v = Ui.body(this, value, Ui.ACCENT, 22f)
        v.typeface = Typeface.DEFAULT_BOLD
        v.layoutParams = android.widget.LinearLayout.LayoutParams(Ui.WC, Ui.WC)
        l.addView(v)

        val t = Ui.body(this, "　" + label, Ui.SUB, 14f)
        t.layoutParams = android.widget.LinearLayout.LayoutParams(Ui.WC, Ui.WC)
        l.addView(t)
        return l
    }
}
