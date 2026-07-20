package com.sekiguchi.salesapp

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 機能3: 営業ルート最適化
 *
 * 位置情報の扱い:
 *   GPSも緯度経度も使わない。訪問先は「エリア名」（公開地名）だけを持つ。
 *   到着・出発の打刻から、エリア間の実測所要時間と滞在時間を学習し、
 *   翌日以降の訪問順を並べ替える。
 *   顧客名・住所・座標をどこにも保存しないため、顧客リストの復元ができない。
 */
class RouteActivity : Activity() {

    private var mode = 0   // 0=本日のルート 1=統計

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
        if (mode == 1) setContentView(buildStats()) else setContentView(buildToday())
    }

    // ---------- 本日のルート ----------

    private fun buildToday(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "本日のルート"))
        col.addView(Ui.body(this,
            "座標は取得も保存もしない。エリア名と打刻だけで所要時間を学習する。", Ui.SUB, 13f))

        val today = Store.today(this)
        val now = System.currentTimeMillis()
        val nowBucket = Store.bucket(now)

        // --- 訪問先の追加 ---
        col.addView(Ui.heading(this, "エリアを追加"))
        val known = Store.areas(this)
        val addCard = Ui.card(this)

        if (known.length() > 0) {
            val names = ArrayList<String>()
            for (i in 0 until known.length()) names.add(known.getString(i))
            val spinner = Spinner(this)
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            spinner.layoutParams = Ui.params(this, 2, 2)
            addCard.addView(spinner)
            addCard.addView(Ui.button(this, "登録済みから追加") {
                appendStop(spinner.selectedItem.toString())
            })
        }

        val newInput = Ui.input(this, "新しいエリア名（例：西宮北）", "")
        addCard.addView(newInput)
        addCard.addView(Ui.button(this, "新規エリアとして追加", Ui.SUB) {
            val name = newInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "エリア名を入力してください", Toast.LENGTH_SHORT).show()
            } else if (Leak.check(name).isNotEmpty()) {
                Toast.makeText(this, "社名らしき文字が含まれています。地名にしてください", Toast.LENGTH_LONG).show()
            } else {
                Store.addArea(this, name)
                appendStop(name)
            }
        })
        col.addView(addCard)

        // --- 並び替え ---
        if (today.length() >= 3) {
            col.addView(Ui.button(this, "所要時間の実測から順序を最適化") { optimize() })
        }

        // --- 訪問先リスト ---
        col.addView(Ui.heading(this, "訪問順"))
        if (today.length() == 0) {
            val c = Ui.card(this)
            c.addView(Ui.body(this, "まだ登録がありません。", Ui.SUB))
            col.addView(c)
        }

        var estimateTotal = 0
        for (i in 0 until today.length()) {
            val stop = today.getJSONObject(i)
            val area = stop.getString("area")
            val arrive = stop.optLong("arrive", 0L)
            val depart = stop.optLong("depart", 0L)
            val index = i

            val card = Ui.card(this)

            val head = Ui.body(this, (i + 1).toString() + ". " + area, Ui.TEXT, 17f)
            head.typeface = Typeface.DEFAULT_BOLD
            card.addView(head)

            if (i > 0) {
                val prev = today.getJSONObject(i - 1).getString("area")
                val est = Store.estimateTravel(this, prev, area, nowBucket)
                estimateTotal += est
                card.addView(Ui.body(this, "移動見込み " + est + "分（" + prev + "から）", Ui.SUB, 12f))
            }

            val state = StringBuilder()
            if (arrive > 0L) state.append("到着 ").append(hm(arrive))
            if (depart > 0L) state.append("　出発 ").append(hm(depart))
            if (arrive > 0L && depart > 0L) {
                state.append("　滞在 ").append(((depart - arrive) / 60000L)).append("分")
            }
            if (state.isNotEmpty()) card.addView(Ui.body(this, state.toString(), Ui.ACCENT, 13f))

            if (arrive == 0L) {
                card.addView(Ui.button(this, "到着を打刻") { punchArrive(index) })
            } else if (depart == 0L) {
                card.addView(Ui.button(this, "出発を打刻") { punchDepart(index) })
            }

            card.addView(Ui.button(this, "この訪問先を外す", Ui.SUB) { removeStop(index) })
            col.addView(card)
        }

        if (estimateTotal > 0) {
            val c = Ui.card(this)
            c.addView(Ui.body(this, "この順序での移動時間の見込み： 合計 " + estimateTotal + "分", Ui.TEXT, 15f))
            c.addView(Ui.body(this, "実測が少ないうちは既定値30分で計算されます。", Ui.SUB, 12f))
            col.addView(c)
        }

        col.addView(Ui.button(this, "実測データを見る", Ui.SUB) {
            mode = 1
            render()
        })

        if (today.length() > 0) {
            col.addView(Ui.button(this, "本日を締める（打刻を消す）", Ui.SUB) {
                AlertDialog.Builder(this)
                    .setTitle("本日を締める")
                    .setMessage("訪問リストを空にします。学習した所要時間は残ります。")
                    .setNegativeButton("キャンセル", null)
                    .setPositiveButton("締める") { _, _ ->
                        Store.setToday(this, JSONArray())
                        render()
                    }
                    .show()
            })
        }

        return scroll
    }

    // ---------- 統計 ----------

    private fun buildStats(): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "実測データ"))
        col.addView(Ui.body(this, "これが唯一保存されるもの。座標も顧客名も含まない。", Ui.SUB, 13f))

        col.addView(Ui.heading(this, "エリア間の所要時間"))
        val od = Store.statLines(this, "od")
        val odCard = Ui.card(this)
        if (od.isEmpty()) {
            odCard.addView(Ui.body(this, "まだ実測がありません。到着・出発を打刻すると貯まります。", Ui.SUB))
        } else {
            for (line in od) odCard.addView(Ui.body(this, line, Ui.TEXT, 14f))
        }
        col.addView(odCard)

        col.addView(Ui.heading(this, "エリア別の滞在時間"))
        val stay = Store.statLines(this, "stay")
        val stayCard = Ui.card(this)
        if (stay.isEmpty()) {
            stayCard.addView(Ui.body(this, "まだ実測がありません。", Ui.SUB))
        } else {
            for (line in stay) stayCard.addView(Ui.body(this, line, Ui.TEXT, 14f))
        }
        col.addView(stayCard)

        col.addView(Ui.button(this, "学習データを削除", Ui.DANGER) {
            AlertDialog.Builder(this)
                .setTitle("学習データの削除")
                .setMessage("所要時間・滞在時間の実測をすべて消します。元に戻せません。")
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除") { _, _ ->
                    Store.clearRouteLearning(this)
                    Toast.makeText(this, "削除しました", Toast.LENGTH_SHORT).show()
                    mode = 0
                    render()
                }
                .show()
        })

        col.addView(Ui.button(this, "戻る", Ui.SUB) {
            mode = 0
            render()
        })
        return scroll
    }

    // ---------- 操作 ----------

    private fun appendStop(area: String) {
        val today = Store.today(this)
        val o = JSONObject()
        o.put("area", area)
        o.put("arrive", 0L)
        o.put("depart", 0L)
        today.put(o)
        Store.setToday(this, today)
        render()
    }

    private fun removeStop(index: Int) {
        val today = Store.today(this)
        val out = JSONArray()
        for (i in 0 until today.length()) {
            if (i != index) out.put(today.getJSONObject(i))
        }
        Store.setToday(this, out)
        render()
    }

    private fun punchArrive(index: Int) {
        val today = Store.today(this)
        val now = System.currentTimeMillis()
        val stop = today.getJSONObject(index)
        stop.put("arrive", now)

        // 直前の訪問先を出発済みなら、区間の実測所要時間を記録する
        if (index > 0) {
            val prev = today.getJSONObject(index - 1)
            val prevDepart = prev.optLong("depart", 0L)
            if (prevDepart > 0L) {
                val minutes = ((now - prevDepart) / 60000L).toInt()
                Store.recordTravel(
                    this,
                    prev.getString("area"),
                    stop.getString("area"),
                    Store.bucket(prevDepart),
                    minutes
                )
            }
        }
        Store.setToday(this, today)
        render()
    }

    private fun punchDepart(index: Int) {
        val today = Store.today(this)
        val now = System.currentTimeMillis()
        val stop = today.getJSONObject(index)
        stop.put("depart", now)

        val arrive = stop.optLong("arrive", 0L)
        if (arrive > 0L) {
            val minutes = ((now - arrive) / 60000L).toInt()
            Store.recordStay(this, stop.getString("area"), Store.bucket(arrive), minutes)
        }
        Store.setToday(this, today)
        render()
    }

    // ---------- 最適化 ----------

    private fun optimize() {
        val today = Store.today(this)
        for (i in 0 until today.length()) {
            if (today.getJSONObject(i).optLong("arrive", 0L) > 0L) {
                Toast.makeText(this, "打刻済みの訪問先があるため並び替えできません", Toast.LENGTH_LONG).show()
                return
            }
        }

        val areas = ArrayList<String>()
        for (i in 0 until today.length()) areas.add(today.getJSONObject(i).getString("area"))

        val bucketName = Store.bucket(System.currentTimeMillis())
        val before = totalMinutes(areas, bucketName)
        val sorted = nearestThen2opt(areas, bucketName)
        val after = totalMinutes(sorted, bucketName)

        val out = JSONArray()
        for (a in sorted) {
            val o = JSONObject()
            o.put("area", a)
            o.put("arrive", 0L)
            o.put("depart", 0L)
            out.put(o)
        }
        Store.setToday(this, out)

        val diff = before - after
        val msg = if (diff > 0) "見込み " + before + "分 → " + after + "分（" + diff + "分短縮）"
        else "現在の順序が最短でした（" + after + "分）"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        render()
    }

    private fun totalMinutes(list: List<String>, bucketName: String): Int {
        var t = 0
        for (i in 1 until list.size) {
            t += Store.estimateTravel(this, list[i - 1], list[i], bucketName)
        }
        return t
    }

    /** 出発地（1番目）は固定。最近傍法で組んでから2-optで改善する */
    private fun nearestThen2opt(areas: List<String>, bucketName: String): List<String> {
        if (areas.size <= 2) return areas

        val rest = ArrayList<String>(areas.subList(1, areas.size))
        val out = ArrayList<String>()
        out.add(areas[0])
        var cur = areas[0]

        while (rest.isNotEmpty()) {
            var best = 0
            var bestV = Int.MAX_VALUE
            for (i in rest.indices) {
                val v = Store.estimateTravel(this, cur, rest[i], bucketName)
                if (v < bestV) {
                    bestV = v
                    best = i
                }
            }
            cur = rest.removeAt(best)
            out.add(cur)
        }

        var improved = true
        var guard = 0
        while (improved && guard < 50) {
            improved = false
            guard++
            for (i in 1 until out.size - 1) {
                for (j in i + 1 until out.size) {
                    val before = totalMinutes(out, bucketName)
                    reverse(out, i, j)
                    val after = totalMinutes(out, bucketName)
                    if (after < before) {
                        improved = true
                    } else {
                        reverse(out, i, j)
                    }
                }
            }
        }
        return out
    }

    private fun reverse(list: ArrayList<String>, from: Int, to: Int) {
        var a = from
        var b = to
        while (a < b) {
            val tmp = list[a]
            list[a] = list[b]
            list[b] = tmp
            a++
            b--
        }
    }

    private fun hm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.JAPAN).format(Date(ms))
}
