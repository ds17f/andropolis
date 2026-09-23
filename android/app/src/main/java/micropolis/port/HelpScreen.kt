package micropolis.port

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen help: tips.html first, then the bundled manual. onClose runs when it closes. */
internal fun MainActivity.showHelp(onClose: () -> Unit) {
    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
        setContentView(
            LinearLayout(this@showHelp).apply {
                setBackgroundColor(0xFF12161C.toInt())
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))

                // Top row: title + close button
                addView(
                    LinearLayout(this@showHelp).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 0, 0, dp(12))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        addView(
                            TextView(this@showHelp).apply {
                                text = "How to play"
                                setTextColor(0xFFEEF2F6.toInt())
                                textSize = 18f
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                layoutParams = LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { weight = 1f }
                            }
                        )
                        addView(
                            TextView(this@showHelp).apply {
                                text = "✕"
                                setTextColor(0xFF9AA7B4.toInt())
                                textSize = 18f
                                setPadding(dp(12), dp(12), dp(12), dp(12))
                                setOnClickListener { dismiss() }
                            }
                        )
                    }
                )

                // WebView for help content
                val webView = WebView(this@showHelp).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0
                    ).apply { weight = 1f }
                    setBackgroundColor(0xFF12161C.toInt())
                    settings.javaScriptEnabled = false
                    loadUrl("file:///android_asset/manual/tips.html")
                    webViewClient = object : WebViewClient() {
                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                startActivity(intent)
                                return true
                            }
                            return false
                        }
                    }
                }
                addView(webView)

                // Back key handling
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            dismiss()
                        }
                        true
                    } else {
                        false
                    }
                }
            }
        )
        setOnDismissListener { onClose() }
    }
    dialog.show()
}
