package com.sekiguchi.salesapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 2層構造:
 *   assets 内の JSON       … 本体。アプリ更新で上書きされる
 *   SharedPreferences      … ユーザー編集分。更新しても残る
 *
 * 保存する値には必ず出所タグ (personal / public / company-derived) を持たせる。
 * 退職時は company-derived のみを物理削除できる。
 *
 * 機能3の設計方針:
 *   緯度経度は一切保存しない。保存するのは
 *   「エリア間の実測所要時間」「エリア別の滞在時間」という匿名の特徴量だけ。
 *   顧客名も住所も持たないため、顧客リストの復元ができない。
 */
object Store {

    private const val PREF = "salesapp"

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun asset(c: Context, name: String): JSONObject {
        val text = c.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return JSONObject(text)
    }

    fun get(c: Context, key: String, def: String = ""): String =
        prefs(c).getString(key, def) ?: def

    fun put(c: Context, key: String, value: String) {
        prefs(c).edit().putString(key, value).apply()
    }

    // ============ 機能6 ============

    fun contactNumber(c: Context, id: String, fallback: String): String {
        val v = get(c, "contact.$id", "")
        return if (v.isBlank()) fallback else v
    }

    fun setContactNumber(c: Context, id: String, number: String) = put(c, "contact.$id", number)

    fun record(c: Context, categoryId: String, field: String): String =
        get(c, "rec.$categoryId.$field", "")

    fun setRecord(c: Context, categoryId: String, field: String, value: String) =
        put(c, "rec.$categoryId.$field", value)

    // ============ 機能2 ============

    fun slot(c: Context, key: String): String = get(c, "slot.$key", "")

    fun setSlot(c: Context, key: String, value: String) = put(c, "slot.$key", value)

    // ============ 機能7: トーク集・反省事例 ============

    fun talks(c: Context): JSONArray = try {
        JSONArray(get(c, "talks", "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    /** kind: "good" 好評トーク / "review" 反省事例 / "prompt" 生成プロンプト */
    fun addTalk(c: Context, kind: String, title: String, bodyText: String) {
        val arr = talks(c)
        val o = JSONObject()
        o.put("kind", kind)
        o.put("title", title)
        o.put("body", bodyText)
        o.put("at", System.currentTimeMillis())
        o.put("tag", "personal")
        arr.put(o)
        put(c, "talks", arr.toString())
    }

    fun saveTalk(c: Context, title: String, bodyText: String) = addTalk(c, "prompt", title, bodyText)

    fun deleteTalk(c: Context, index: Int) {
        val arr = talks(c)
        if (index < 0 || index >= arr.length()) return
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            if (i != index) out.put(arr.getJSONObject(i))
        }
        put(c, "talks", out.toString())
    }

    fun talkCount(c: Context): Int = talks(c).length()

    // ============ 機能3: ルート最適化 ============

    fun areas(c: Context): JSONArray = try {
        JSONArray(get(c, "areas", "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    fun addArea(c: Context, name: String) {
        val arr = areas(c)
        for (i in 0 until arr.length()) {
            if (arr.getString(i) == name) return
        }
        arr.put(name)
        put(c, "areas", arr.toString())
    }

    /** 本日の訪問予定。要素は area / arrive / depart（時刻はミリ秒、0は未打刻） */
    fun today(c: Context): JSONArray = try {
        JSONArray(get(c, "today", "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    fun setToday(c: Context, arr: JSONArray) = put(c, "today", arr.toString())

    fun bucket(timeMs: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timeMs
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            h < 9 -> "朝"
            h < 12 -> "午前"
            h < 14 -> "昼"
            h < 17 -> "午後"
            else -> "夕"
        }
    }

    private fun addStat(c: Context, storeKey: String, key: String, minutes: Int) {
        if (minutes <= 0 || minutes > 480) return
        val root = try {
            JSONObject(get(c, storeKey, "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
        val o = if (root.has(key)) root.getJSONObject(key) else JSONObject()
        o.put("sum", o.optLong("sum", 0L) + minutes)
        o.put("n", o.optInt("n", 0) + 1)
        root.put(key, o)
        put(c, storeKey, root.toString())
    }

    /** エリア間の実測所要時間を記録（座標は使わない） */
    fun recordTravel(c: Context, from: String, to: String, bucketName: String, minutes: Int) =
        addStat(c, "od", from + ">" + to + "@" + bucketName, minutes)

    /** エリアごとの滞在時間を記録 */
    fun recordStay(c: Context, area: String, bucketName: String, minutes: Int) =
        addStat(c, "stay", area + "@" + bucketName, minutes)

    private fun avg(root: JSONObject, key: String): Int {
        if (!root.has(key)) return -1
        val o = root.getJSONObject(key)
        val n = o.optInt("n", 0)
        if (n <= 0) return -1
        return (o.optLong("sum", 0L) / n).toInt()
    }

    /** 推定所要時間（分）。実測がなければ時間帯を無視した平均、それも無ければ既定値 */
    fun estimateTravel(c: Context, from: String, to: String, bucketName: String): Int {
        if (from == to) return 0
        val root = try {
            JSONObject(get(c, "od", "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
        val exact = avg(root, from + ">" + to + "@" + bucketName)
        if (exact >= 0) return exact

        var sum = 0L
        var n = 0
        val prefix = from + ">" + to + "@"
        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.startsWith(prefix)) {
                val o = root.getJSONObject(k)
                sum += o.optLong("sum", 0L)
                n += o.optInt("n", 0)
            }
        }
        if (n > 0) return (sum / n).toInt()
        return 30
    }

    fun estimateStay(c: Context, area: String, bucketName: String): Int {
        val root = try {
            JSONObject(get(c, "stay", "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
        val exact = avg(root, area + "@" + bucketName)
        if (exact >= 0) return exact
        var sum = 0L
        var n = 0
        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.startsWith(area + "@")) {
                val o = root.getJSONObject(k)
                sum += o.optLong("sum", 0L)
                n += o.optInt("n", 0)
            }
        }
        if (n > 0) return (sum / n).toInt()
        return 30
    }

    /** 統計表示用の行 */
    fun statLines(c: Context, storeKey: String): List<String> {
        val out = ArrayList<String>()
        val root = try {
            JSONObject(get(c, storeKey, "{}"))
        } catch (e: Exception) {
            return out
        }
        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val o = root.getJSONObject(k)
            val n = o.optInt("n", 0)
            if (n <= 0) continue
            val m = o.optLong("sum", 0L) / n
            out.add(k.replace(">", " → ").replace("@", " / ") + "   " + m + "分（" + n + "回）")
        }
        out.sort()
        return out
    }

    fun clearRouteLearning(c: Context) {
        val e = prefs(c).edit()
        e.remove("od")
        e.remove("stay")
        e.remove("today")
        e.apply()
    }

    // ============ 退職時：会社由来データの物理削除 ============

    fun purgeCompanyDerived(c: Context, companyContactIds: List<String>): Int {
        val editor = prefs(c).edit()
        var count = 0
        for (id in companyContactIds) {
            if (get(c, "contact.$id", "").isNotBlank()) count++
            editor.remove("contact.$id")
        }
        for (key in prefs(c).all.keys) {
            if (key.startsWith("rec.")) {
                editor.remove(key)
                count++
            }
        }
        // 案件データは会社の営業活動に由来するため company-derived 扱い
        val dealCount = try {
            JSONArray(get(c, "deals", "[]")).length()
        } catch (e: Exception) {
            0
        }
        if (dealCount > 0) {
            editor.remove("deals")
            count += dealCount
        }
        editor.apply()
        put(c, "purge.log", "削除実行 " + java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm", java.util.Locale.JAPAN
        ).format(java.util.Date()) + " / " + count + "件")
        return count
    }

    fun purgeLog(c: Context): String = get(c, "purge.log", "")
}
