package com.wafi.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

data class WafiNote(var id: Long, var title: String, var text: String)

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("wafi_data", MODE_PRIVATE) }
    private val notes = mutableListOf<WafiNote>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadNotes()

        findViewById<Button>(R.id.newTextButton).setOnClickListener { openEditor(null) }
        findViewById<Button>(R.id.myTextsButton).setOnClickListener { showNotes() }
        findViewById<Button>(R.id.searchButton).setOnClickListener { searchNotes() }
        findViewById<Button>(R.id.poetryButton).setOnClickListener { showPoetryTools() }
        findViewById<Button>(R.id.aiButton).setOnClickListener { showAi() }
    }

    private fun openEditor(existing: WafiNote?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 8)
        }
        val title = EditText(this).apply {
            hint = "عنوان القصيدة أو النص"
            textSize = 17f
            gravity = Gravity.RIGHT
            setSingleLine(true)
            setText(existing?.title ?: "")
        }
        val body = EditText(this).apply {
            hint = "اكتب قصيدتك أو نصك هنا..."
            textSize = 18f
            minLines = 12
            gravity = Gravity.TOP or Gravity.RIGHT
            setPadding(20, 20, 20, 20)
            setText(existing?.text ?: "")
        }
        box.addView(title)
        box.addView(body)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "نص جديد" else "تعديل النص")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val t = title.text.toString().trim().ifEmpty { "بدون عنوان" }
                val b = body.text.toString().trim()
                if (b.isEmpty()) {
                    Toast.makeText(this, "اكتب النص أولًا", Toast.LENGTH_SHORT).show()
                } else {
                    if (existing == null) notes.add(WafiNote(System.currentTimeMillis(), t, b))
                    else { existing.title = t; existing.text = b }
                    saveNotes()
                    Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showNotes() {
        if (notes.isEmpty()) {
            AlertDialog.Builder(this).setTitle("قصائدي ونصوصي")
                .setMessage("لا توجد نصوص محفوظة. ابدأ بكتابة نص جديد.")
                .setPositiveButton("حسنًا", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("قصائدي ونصوصي")
            .setItems(notes.map { it.title }.toTypedArray()) { _, i -> showNoteActions(notes[i]) }
            .setNegativeButton("إغلاق", null).show()
    }

    private fun showNoteActions(note: WafiNote) {
        AlertDialog.Builder(this).setTitle(note.title)
            .setItems(arrayOf("فتح وتعديل", "إحصائيات النص", "مشاركة", "نسخ النص", "حذف")) { _, which ->
                when (which) {
                    0 -> openEditor(note)
                    1 -> {
                        val words = note.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                        val lines = note.text.lines().size
                        AlertDialog.Builder(this).setTitle("إحصائيات")
                            .setMessage("عدد الكلمات: $words\\nعدد الأسطر: $lines\\nعدد الأحرف: ${note.text.length}")
                            .setPositiveButton("حسنًا", null).show()
                    }
                    2 -> share(note.text)
                    3 -> {
                        val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("برنامج الوافي", note.text))
                        Toast.makeText(this, "تم نسخ النص", Toast.LENGTH_SHORT).show()
                    }
                    4 -> {
                        notes.remove(note); saveNotes()
                        Toast.makeText(this, "تم حذف النص", Toast.LENGTH_SHORT).show()
                    }
                }
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun searchNotes() {
        val input = EditText(this).apply { hint = "كلمة أو عبارة"; gravity = Gravity.RIGHT }
        AlertDialog.Builder(this).setTitle("البحث في كتاباتي").setView(input)
            .setPositiveButton("بحث") { _, _ ->
                val q = input.text.toString().trim()
                if (q.isEmpty()) return@setPositiveButton
                val result = notes.filter { it.title.contains(q, true) || it.text.contains(q, true) }
                if (result.isEmpty()) Toast.makeText(this, "لا توجد نتائج", Toast.LENGTH_SHORT).show()
                else AlertDialog.Builder(this).setTitle("نتائج البحث")
                    .setItems(result.map { it.title }.toTypedArray()) { _, i -> showNoteActions(result[i]) }
                    .setNegativeButton("إغلاق", null).show()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun showPoetryTools() {
        val tools = arrayOf("إزالة التشكيل", "تنظيف النص", "إحصائيات نص", "اقتراحات شعرية")
        AlertDialog.Builder(this).setTitle("أدوات الشعر").setItems(tools) { _, which ->
            when (which) {
                0 -> transformDialog("إزالة التشكيل") { removeDiacritics(it) }
                1 -> transformDialog("تنظيف النص") { normalizeText(it) }
                2 -> chooseNoteForStats()
                3 -> showSuggestions()
            }
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun transformDialog(title: String, transform: (String) -> String) {
        val input = EditText(this).apply {
            hint = "ضع النص هنا..."
            minLines = 8; gravity = Gravity.TOP or Gravity.RIGHT; textSize = 18f
        }
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("تنفيذ") { _, _ ->
                val out = transform(input.text.toString())
                AlertDialog.Builder(this).setTitle("النتيجة").setMessage(out.ifEmpty { "لا يوجد نص." })
                    .setPositiveButton("نسخ") { _, _ ->
                        val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("الوافي", out))
                    }.setNegativeButton("إغلاق", null).show()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun chooseNoteForStats() {
        if (notes.isEmpty()) { Toast.makeText(this, "لا توجد نصوص محفوظة", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("اختر نصًا")
            .setItems(notes.map { it.title }.toTypedArray()) { _, i ->
                val n = notes[i]
                val words = n.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                AlertDialog.Builder(this).setTitle("إحصائيات: ${n.title}")
                    .setMessage("الكلمات: $words\\nالأحرف: ${n.text.length}\\nالأسطر: ${n.text.lines().size}")
                    .setPositiveButton("حسنًا", null).show()
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun showSuggestions() {
        AlertDialog.Builder(this).setTitle("مساعد شعري — اقتراحات أولية")
            .setMessage("• حدّد موضوع القصيدة أولًا.\\n• اختر شعورًا واضحًا: شوق، فخر، حزن، أمل.\\n• ثبّت القافية قبل كتابة الأبيات.\\n• اجعل المطلع قويًا وسهل الحفظ.\\n\\nالنسخة القادمة يمكنها ربط هذه الأدوات بمساعد ذكاء اصطناعي حقيقي.")
            .setPositiveButton("حسنًا", null).show()
    }

    private fun showAi() {
        AlertDialog.Builder(this).setTitle("🤖 مساعد الوافي الذكي")
            .setMessage("واجهة المساعد جاهزة. ربط نموذج ذكاء اصطناعي حقيقي يحتاج خدمة API أو نموذجًا محليًا. سنضيفه بعد اختيار مزود مجاني مناسب، مع عدم وضع مفتاح API داخل التطبيق.")
            .setPositiveButton("حسنًا", null).show()
    }

    private fun removeDiacritics(s: String): String =
        s.replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")

    private fun normalizeText(s: String): String =
        s.replace("\\r\\n", "\\n").replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\\n\\n").trim()

    private fun share(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, "مشاركة النص"))
    }

    private fun loadNotes() {
        notes.clear()
        try {
            val arr = JSONArray(prefs.getString("notes", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                notes.add(WafiNote(o.getLong("id"), o.getString("title"), o.getString("text")))
            }
        } catch (_: Exception) {}
    }

    private fun saveNotes() {
        val arr = JSONArray()
        notes.forEach { n -> arr.put(JSONObject().apply {
            put("id", n.id); put("title", n.title); put("text", n.text)
        }) }
        prefs.edit().putString("notes", arr.toString()).apply()
    }
}
