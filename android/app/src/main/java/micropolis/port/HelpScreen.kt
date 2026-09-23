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
    var helpWeb: WebView? = null
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
                        // The manual pages set no background: show them black-on-white. tips.html is dark.
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            view.setBackgroundColor(if (url.endsWith("tips.html")) 0xFF12161C.toInt() else 0xFFFFFFFF.toInt())
                        }
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
                helpWeb = webView
            }
        )
        // Back key: previous page, else close (on the dialog: a layout never gets the key)
        setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        val w = helpWeb
                        if (w != null && w.canGoBack()) w.goBack() else dismiss()
                        true
                    } else {
                        keyCode == KeyEvent.KEYCODE_BACK      // swallow the DOWN too
                    }
                }
        setOnDismissListener { onClose() }
    }
    dialog.show()
}
