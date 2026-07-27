package com.sekiguchi.salesapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONArray

/**
 * トップページ。
 * 営業中 / 営業準備中 / 情報ツール の3分類。
 * 現場で咄嗟に押すものほど上に、参照するだけのものは下にまとめる。
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun section(col: LinearLayout, title: String, note: String) {
        val t = Ui.body(this, title, Ui.ACCENT, 15f)
        t.typeface = Typeface.DEFAULT_BOLD
        t.letterSpacing = 0.06f
        t.layoutParams = Ui.params(this, 22, 2)
        col.addView(t)
        col.addView(Ui.body(this, note, Ui.SUB, 12f))
    }

    private fun item(col: LinearLayout, label: String, sub: String, color: Int, cls: Class<*>) {
        col.addView(Ui.bigButton(this, label, sub, color) {
            startActivity(Intent(this, cls))
        })
    }

    private fun build() {
        val (scroll, col) = Ui.screen(this)

        col.addView(Ui.title(this, "営業支援"))
        col.addView(Ui.body(this, "個人作成・社内情報なし・通信なし・権限ゼロ", Ui.SUB, 12f))

        // ---- 営業中 ----
        section(col, "営業中", "外に出ている間に開くもの")
        item(col, "切り返し", "言われた言葉から引く", Ui.ACCENT, RebuttalActivity::class.java)
        item(col, "概算計算", "月額・回収年数・粗利・電気代", Ui.ACCENT, CalcActivity::class.java)
        item(col, "商談タイマー", "持ち時間を4つに配分", Ui.ACCENT, TimerActivity::class.java)
        item(col, "本日のルート", "打刻から訪問順を最適化", Ui.ACCENT, RouteActivity::class.java)
        item(col, "クイックメモ", "訪問直後に音声入力で残す", Ui.ACCENT, MemoActivity::class.java)
        item(col, "緊急時マニュアル", "事故・急病・移動不能", Ui.DANGER, EmergencyActivity::class.java)

        // ---- 営業準備中 ----
        section(col, "営業準備中", "机やクルマの中で組み立てるもの")
        item(col, "営業プロンプト", "場面別・全10シチュエーション", Ui.ACCENT, PromptActivity::class.java)
        item(col, "トーク集・反省事例", "好評だった言い回しと失敗の記録", Ui.ACCENT, TalksActivity::class.java)
        item(col, "走行距離・経費", "自分の精算と申告のため", Ui.ACCENT, ExpenseActivity::class.java)

        // ---- 情報ツール ----
        section(col, "情報ツール", "現場で開く頻度は低い。調べる・整える用")
        item(col, "参考情報", "法令・業界地図・単位換算", Ui.SUB, ReferenceActivity::class.java)

        val card = Ui.card(this)
        card.addView(Ui.body(this, "保存済みの記録　" + Store.talkCount(this) + "件", Ui.TEXT, 14f))
        val log = Store.purgeLog(this)
        if (log.isNotEmpty()) card.addView(Ui.body(this, log, Ui.SUB, 12f))
        card.addView(Ui.body(this,
            "会社由来タグが付いたデータのみを物理削除します。個人作成分・公開情報は残ります。",
            Ui.SUB, 12f))
        card.addView(Ui.button(this, "会社由来データを削除", Ui.SUB) { confirmPurge() })
        col.addView(card)

        setContentView(scroll)
    }

    private fun confirmPurge() {
        AlertDialog.Builder(this)
            .setTitle("会社由来データの削除")
            .setMessage("会社の緊急連絡先と、全ての現場記録を削除します。元に戻せません。実行しますか。")
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("削除する") { _, _ ->
                val ids = companyContactIds()
                val n = Store.purgeCompanyDerived(this, ids)
                Toast.makeText(this, n.toString() + "件を削除しました", Toast.LENGTH_LONG).show()
                build()
            }
            .show()
    }

    private fun companyContactIds(): List<String> {
        val ids = ArrayList<String>()
        try {
            val root = Store.asset(this, "emergency_manual.json")
            val arr: JSONArray = root.getJSONArray("contacts")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("tag") == "company-derived") ids.add(o.getString("id"))
            }
        } catch (e: Exception) {
            // assets が読めない場合は何もしない
        }
        return ids
    }
}
