package com.sekiguchi.salesapp

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class EmergencyActivity : Activity() {

    private var root: JSONObject? = null
    private var current: Int = -1      // -1 = 一覧
    private var contactsMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = try {
            Store.asset(this, "emergency_manual.json")
        } catch (e: Exception) {
            null
        }
        render()
    }

    override fun onBackPressed() {
        if (current >= 0 || contactsMode) {
            current = -1
            contactsMode = false
            render()
        } else {
            super.onBackPressed()
        }
    }

    private fun render() {
        val r = root
        if (r == null) {
            val (s, c) = Ui.screen(this)
            c.addView(Ui.title(this, "読み込みエラー"))
            c.addView(Ui.body(this, "emergency_manual.json を読み込めませんでした。"))
            setContentView(s)
            return
        }
        when {
            contactsMode -> setContentView(buildContacts(r).first)
            current >= 0 -> setContentView(buildDetail(r, current).first)
            else -> setContentView(buildList(r).first)
        }
    }

    // ---------- 一覧 ----------

    private fun buildList(r: JSONObject): Pair<android.widget.ScrollView, LinearLayout> {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "緊急時マニュアル"))
        col.addView(Ui.body(this, "落ち着いて、上から順に。", Ui.SUB, 13f))

        val cats = r.getJSONArray("categories")
        for (i in 0 until cats.length()) {
            val cat = cats.getJSONObject(i)
            val index = i
            col.addView(
                Ui.bigButton(this, cat.getString("title"), "", Ui.DANGER) {
                    current = index
                    render()
                }
            )
        }

        col.addView(Ui.button(this, "連絡先を編集", Ui.ACCENT) {
            contactsMode = true
            render()
        })

        col.addView(Ui.divider(this))
        col.addView(Ui.body(this, r.optString("disclaimer"), Ui.SUB, 12f))
        return Pair(scroll, col)
    }

    // ---------- 詳細 ----------

    private fun buildDetail(r: JSONObject, index: Int): Pair<android.widget.ScrollView, LinearLayout> {
        val (scroll, col) = Ui.screen(this)
        val cat = r.getJSONArray("categories").getJSONObject(index)
        val catId = cat.getString("id")

        col.addView(Ui.title(this, cat.getString("title")))

        // 連絡：最上部に置く
        val contactIds = cat.optJSONArray("contacts")
        if (contactIds != null) {
            val cc = Ui.card(this)
            for (i in 0 until contactIds.length()) {
                val id = contactIds.getString(i)
                val ct = findContact(r, id) ?: continue
                val fallback = ct.optString("number")
                val number = Store.contactNumber(this, id, fallback)
                val label = ct.optString("label") +
                    (if (number.isBlank()) "（未登録）" else "  " + number)
                cc.addView(Ui.button(this, label, if (number.isBlank()) Ui.SUB else Ui.DANGER) {
                    if (number.isBlank()) {
                        contactsMode = true
                        render()
                    } else {
                        dial(number)
                    }
                })
            }
            col.addView(cc)
        }

        // 手順
        col.addView(Ui.heading(this, "すぐやること"))
        val steps = cat.getJSONArray("immediate")
        val stepCard = Ui.card(this)
        for (i in 0 until steps.length()) {
            val t = Ui.body(this, (i + 1).toString() + ". " + steps.getString(i))
            t.layoutParams = Ui.params(this, if (i == 0) 0 else 10)
            stepCard.addView(t)
        }
        col.addView(stepCard)

        // やってはいけない
        val never = cat.optJSONArray("never")
        if (never != null && never.length() > 0) {
            col.addView(Ui.heading(this, "やってはいけない"))
            val nc = Ui.card(this, Ui.DANGER_BG, Ui.DANGER)
            for (i in 0 until never.length()) {
                val t = Ui.body(this, "× " + never.getString(i), Ui.DANGER)
                t.layoutParams = Ui.params(this, if (i == 0) 0 else 8)
                nc.addView(t)
            }
            col.addView(nc)
        }

        // 後日対応
        val later = cat.optString("later")
        if (later.isNotEmpty()) {
            col.addView(Ui.heading(this, "後日"))
            val lc = Ui.card(this, Ui.WARN_BG, Ui.WARN)
            lc.addView(Ui.body(this, later, Ui.WARN))
            col.addView(lc)
        }

        // 現場記録（あとで必ず必要になる）
        val rec = cat.optJSONArray("record")
        if (rec != null && rec.length() > 0) {
            col.addView(Ui.heading(this, "記録（この場で入力）"))
            col.addView(Ui.body(this, "会社由来タグ。退職時削除の対象。", Ui.SUB, 12f))
            val rc = Ui.card(this)
            for (i in 0 until rec.length()) {
                val field = rec.getString(i)
                val e = Ui.input(this, field, Store.record(this, catId, field))
                e.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        Store.setRecord(this@EmergencyActivity, catId, field, s?.toString() ?: "")
                    }
                })
                rc.addView(e)
            }
            col.addView(rc)
        }

        col.addView(Ui.button(this, "一覧に戻る", Ui.SUB) {
            current = -1
            render()
        })
        col.addView(Ui.divider(this))
        col.addView(Ui.body(this, r.optString("disclaimer"), Ui.SUB, 12f))
        return Pair(scroll, col)
    }

    // ---------- 連絡先編集 ----------

    private fun buildContacts(r: JSONObject): Pair<android.widget.ScrollView, LinearLayout> {
        val (scroll, col) = Ui.screen(this)
        col.addView(Ui.title(this, "連絡先"))
        col.addView(Ui.body(this, "空欄の番号を登録しておく。編集不可の番号は共通番号。", Ui.SUB, 13f))

        val arr = r.getJSONArray("contacts")
        for (i in 0 until arr.length()) {
            val ct = arr.getJSONObject(i)
            val id = ct.getString("id")
            val editable = ct.optBoolean("editable", true)
            val tag = ct.optString("tag")

            val card = Ui.card(this)
            val head = Ui.body(this, ct.optString("label"), Ui.TEXT, 15f)
            head.typeface = Typeface.DEFAULT_BOLD
            card.addView(head)
            card.addView(Ui.body(this, "タグ: " + tag, Ui.SUB, 12f))

            val memo = ct.optString("memo")
            if (memo.isNotEmpty()) card.addView(Ui.body(this, memo, Ui.SUB, 12f))

            if (editable) {
                val e = Ui.input(this, "電話番号", Store.contactNumber(this, id, ""))
                e.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        Store.setContactNumber(this@EmergencyActivity, id, s?.toString() ?: "")
                    }
                })
                card.addView(e)
            } else {
                card.addView(Ui.button(this, ct.optString("number"), Ui.DANGER) {
                    dial(ct.optString("number"))
                })
            }
            col.addView(card)
        }

        col.addView(Ui.button(this, "戻る", Ui.SUB) {
            contactsMode = false
            render()
        })
        return Pair(scroll, col)
    }

    private fun findContact(r: JSONObject, id: String): JSONObject? {
        val arr: JSONArray = r.getJSONArray("contacts")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("id") == id) return o
        }
        return null
    }

    /** ACTION_DIAL は権限不要。番号を入れた状態で電話アプリを開く */
    private fun dial(number: String) {
        try {
            // Uri.parse だと #7119 の # がフラグメント扱いで消えるため fromParts を使う
            val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "電話アプリを開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }
}
