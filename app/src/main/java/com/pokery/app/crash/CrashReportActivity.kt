package com.pokery.app.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.pokery.app.MainActivity

/**
 * Shows the last crash report. Built with plain Android views on purpose, so it still
 * works if the crash came from Compose or anything else the main screen depends on.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashReporter.pending(this) ?: run {
            openMain()
            return
        }
        val pad = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "Pokery crashed last time"
            textSize = 20f
            setPadding(pad, pad * 2, pad, pad / 2)
        }
        val hint = TextView(this).apply {
            text = "Copy this report and send it to the developer. Tap Continue to try opening the app again."
            setPadding(pad, 0, pad, pad / 2)
        }
        val copy = Button(this).apply {
            text = "Copy report"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Pokery crash report", report))
                Toast.makeText(this@CrashReportActivity, "Copied", Toast.LENGTH_SHORT).show()
            }
        }
        val share = Button(this).apply {
            text = "Share"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                startActivity(Intent.createChooser(send, "Share crash report"))
            }
        }
        val proceed = Button(this).apply {
            text = "Continue"
            setOnClickListener {
                CrashReporter.clear(this@CrashReportActivity)
                openMain()
            }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, 0, pad, 0)
            addView(copy)
            addView(share)
            addView(proceed)
        }
        val body = TextView(this).apply {
            text = report
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(pad, pad, pad, pad)
        }
        val scroll = ScrollView(this).apply {
            addView(HorizontalScrollView(this@CrashReportActivity).apply { addView(body) })
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                fitsSystemWindows = true
                addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                addView(hint, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            }
        )
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
