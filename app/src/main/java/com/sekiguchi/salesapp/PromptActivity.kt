package com.sekiguchi.salesapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import org.json.JSONObject

class PromptActivity : Activity() {

    private var root: JSONObject? = null
    private var current: Int = -1          // -1 = 一覧
    private var generated: String? = null
    private var profileIndex = 0

    private val slotFields = HashMap<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = try {
            Store.asset(this, "prompt_library.json")
        } catch (e: Exception) {
            null
        }
        render()
    }

    override fun onBackPressed() {
        when {
            generated != null -> {
                generated = null
                render()
            }
            current >= 0 -> {
                current = -1
                render()
            }
            else -> super.onBackPressed()
        }
    }

    private fun render() {
        val r = root
        if (r == null) {
            val (s, c) = Ui.screen(this)
            c.addView(Ui.title(this, "読み込みエラー"))
            c.addView(Ui.body(this, "prompt_library.json を読み込めませんでした。"))
            setContentView(s)
            return
        }
        when {
            generated != null -> setContentView(buildResult(generated!!))
            current >= 0 -> setContentView(buildForm(r, current))
            else -> setContentView(buildList(r))
        }
    }

    // ---------- シチュエーション一覧 ----------

    private fun buildList(r: JSONObject): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "営業プロンプト"))
        col.addView(Ui.body(this,
            "生成するのはプロンプト文。コピーしてAIアプリに貼って使う。" +
                "通信もAPIキーも使わない。", Ui.SUB, 13f))

        val sits = r.getJSONArray("situations")
        for (i in 0 until sits.length()) {
            val s = sits.getJSONObject(i)
            val index = i
            val card = Ui.card(this)
            card.isClickable = true
            card.setOnClickListener {
                current = index
                generated = null
                render()
            }
            card.addView(Ui.body(this, s.getString("title"), Ui.ACCENT, 17f))
            col.addView(card)
        }
        return scroll
    }

    // ---------- スロット入力 ----------

    private fun buildForm(r: JSONObject, index: Int): android.view.View {
        val (scroll, col) = Ui.screen(this)
        slotFields.clear()

        val sit = r.getJSONArray("situations").getJSONObject(index)
        val template = sit.getString("template")
        val slots = r.getJSONObject("slots")

        col.addView(Ui.title(this, sit.getString("title")))

        // テンプレート内の {key} を抽出して、必要な入力欄だけ出す
        val keys = Regex("\\{([a-zA-Z_]+)\\}").findAll(template)
            .map { it.groupValues[1] }.distinct().toList()

        val card = Ui.card(this)
        for (k in keys) {
            card.addView(Ui.label(this, slots.optString(k, k)))
            val e = Ui.input(this, "", Store.slot(this, k), if (k == "constraints") 2 else 1)
            slotFields[k] = e
            card.addView(e)
        }
        col.addView(card)

        // 相手タイプ層（第2段階。modifier が空なら出力に影響しない）
        col.addView(Ui.heading(this, "相手タイプ"))
        val types = r.getJSONObject("profile_layer").getJSONArray("types")
        val labels = ArrayList<String>()
        labels.add("指定しない")
        for (i in 0 until types.length()) labels.add(types.getJSONObject(i).getString("label"))

        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(profileIndex)
        spinner.layoutParams = Ui.params(this, 4, 4)
        col.addView(spinner)

        // 混入チェック結果の置き場
        val warnBox = LinearLayout(this)
        warnBox.orientation = LinearLayout.VERTICAL
        warnBox.layoutParams = Ui.params(this, 4)
        col.addView(warnBox)

        col.addView(Ui.button(this, "プロンプトを生成") {
            profileIndex = spinner.selectedItemPosition

            val values = HashMap<String, String>()
            for ((k, e) in slotFields) {
                val v = e.text.toString().trim()
                values[k] = v
                Store.setSlot(this, k, v)
            }

            warnBox.removeAllViews()
            val hits = Leak.check(values.values.joinToString(" "))
            if (hits.isNotEmpty()) {
                val wc = Ui.card(this, Ui.WARN_BG, Ui.WARN)
                wc.addView(Ui.body(this,
                    "社内情報らしき記述を検出：" + hits.joinToString("、") +
                        "\nこのまま生成できますが、固有名詞・型番は一般名称に置き換えることを推奨します。",
                    Ui.WARN, 13f))
                warnBox.addView(wc)
            }

            generated = compose(r, sit, values, spinner.selectedItemPosition, types)
            render()
        })

        col.addView(Ui.button(this, "一覧に戻る", Ui.SUB) {
            current = -1
            render()
        })
        return scroll
    }

    // ---------- 生成結果 ----------

    private fun buildResult(text: String): android.view.View {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "生成されたプロンプト"))
        col.addView(Ui.body(this, "コピーしてAIアプリに貼り付けて使う。", Ui.SUB, 13f))

        val out = Ui.input(this, "", text, 12)
        out.isFocusable = true
        col.addView(out)

        col.addView(Ui.button(this, "コピー") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("prompt", out.text.toString()))
            Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
        })

        col.addView(Ui.button(this, "共有", Ui.SUB) {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, out.text.toString())
            startActivity(Intent.createChooser(i, "共有"))
        })

        col.addView(Ui.button(this, "トーク集へ保存", Ui.ACCENT) {
            val title = root?.getJSONArray("situations")?.getJSONObject(current)
                ?.getString("title") ?: "プロンプト"
            Store.saveTalk(this, title, out.text.toString())
            Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
        })

        col.addView(Ui.button(this, "入力に戻る", Ui.SUB) {
            generated = null
            render()
        })
        return scroll
    }

    // ---------- 組み立て ----------

    private fun compose(
        r: JSONObject,
        sit: JSONObject,
        values: Map<String, String>,
        profileSelection: Int,
        types: org.json.JSONArray
    ): String {
        val base = r.getJSONObject("system_base").getString("text")

        var t = sit.getString("template")
        for ((k, v) in values) {
            t = t.replace("{" + k + "}", if (v.isBlank()) "（未入力）" else v)
        }

        val sb = StringBuilder()
        sb.append(base)
        sb.append("\n\n----------------\n\n")
        sb.append(t)

        if (profileSelection > 0) {
            val type = types.getJSONObject(profileSelection - 1)
            val modifier = type.optString("modifier")
            sb.append("\n\n【相手タイプ】")
            sb.append(type.getString("label"))
            if (modifier.isNotBlank()) {
                sb.append("\n")
                sb.append(modifier)
            }
        }
        return sb.toString()
    }

}
