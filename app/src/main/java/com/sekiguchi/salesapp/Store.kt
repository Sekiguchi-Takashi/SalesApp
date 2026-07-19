package com.sekiguchi.salesapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 2層構造:
 *   assets/*.json          … 本体。アプリ更新で上書きされる
 *   SharedPreferences      … ユーザー編集分。更新しても残る
 *
 * 保存する値には必ず出所タグ (personal / public / company-derived) を持たせる。
 * 退職時は company-derived のみを物理削除できる。
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

    // ---- 連絡先（機能6） ----

    fun contactNumber(c: Context, id: String, fallback: String): String {
        val v = get(c, "contact.$id", "")
        return if (v.isBlank()) fallback else v
    }

    fun setContactNumber(c: Context, id: String, number: String) = put(c, "contact.$id", number)

    // ---- 現場記録（機能6） ----

    fun record(c: Context, categoryId: String, field: String): String =
        get(c, "rec.$categoryId.$field", "")

    fun setRecord(c: Context, categoryId: String, field: String, value: String) =
        put(c, "rec.$categoryId.$field", value)

    // ---- スロット入力の記憶（機能2） ----

    fun slot(c: Context, key: String): String = get(c, "slot.$key", "")

    fun setSlot(c: Context, key: String, value: String) = put(c, "slot.$key", value)

    // ---- トーク集（機能7の受け皿） ----

    fun saveTalk(c: Context, title: String, bodyText: String) {
        val arr = JSONArray(get(c, "talks", "[]"))
        val o = JSONObject()
        o.put("title", title)
        o.put("body", bodyText)
        o.put("at", System.currentTimeMillis())
        o.put("tag", "personal")
        arr.put(o)
        put(c, "talks", arr.toString())
    }

    fun talkCount(c: Context): Int = try {
        JSONArray(get(c, "talks", "[]")).length()
    } catch (e: Exception) {
        0
    }

    // ---- 退職時：会社由来データの物理削除 ----

    /** company-derived タグの連絡先と、全ての現場記録を削除し、削除件数を返す */
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
        editor.apply()
        put(c, "purge.log", "削除実行 " + java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm", java.util.Locale.JAPAN
        ).format(java.util.Date()) + " / " + count + "件")
        return count
    }

    fun purgeLog(c: Context): String = get(c, "purge.log", "")
}
