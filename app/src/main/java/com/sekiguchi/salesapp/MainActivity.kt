package com.sekiguchi.salesapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.json.JSONArray

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val (scroll, col) = Ui.screen(this)

        col.addView(Ui.title(this, "営業支援"))
        col.addView(Ui.body(this, "個人作成・社内情報なし・オフライン動作", Ui.SUB, 13f))

        col.addView(Ui.bigButton(this, "緊急時マニュアル", "事故・急病・移動不能", Ui.DANGER) {
            startActivity(Intent(this, EmergencyActivity::class.java))
        })

        col.addView(Ui.bigButton(this, "営業プロンプト", "場面別・全10シチュエーション", Ui.ACCENT) {
            startActivity(Intent(this, PromptActivity::class.java))
        })

        col.addView(Ui.bigButton(this, "本日のルート", "エリア間の実測から訪問順を最適化", Ui.ACCENT) {
            startActivity(Intent(this, RouteActivity::class.java))
        })

        col.addView(Ui.bigButton(this, "トーク集・反省事例", "好評だった言い回しと失敗の記録", Ui.ACCENT) {
            startActivity(Intent(this, TalksActivity::class.java))
        })

        col.addView(Ui.heading(this, "保存済み"))
        val card = Ui.card(this)
        card.addView(Ui.body(this, "トーク集：" + Store.talkCount(this) + "件", Ui.TEXT, 15f))
        val log = Store.purgeLog(this)
        if (log.isNotEmpty()) {
            card.addView(Ui.body(this, log, Ui.SUB, 12f))
        }
        col.addView(card)

        col.addView(Ui.heading(this, "データ管理"))
        col.addView(Ui.body(this,
            "会社由来タグ（company-derived）が付いたデータのみを物理削除します。" +
                "個人作成分・公開情報は残ります。", Ui.SUB, 13f))
        col.addView(Ui.button(this, "会社由来データを削除", Ui.SUB) { confirmPurge() })

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

    /** emergency_manual.json から tag が company-derived の連絡先IDを集める */
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
