package micropolis.port

import android.view.View
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Settings → Background tab (task 055). */
internal fun MainActivity.backgroundSettingsTab(): View {
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    // 1. Run in background toggle
    col.addView(settingsToggle(
        "Run in background",
        "Keep the city going while the app is closed. You get a notification when something happens.",
        BackgroundPrefs.enabled(prefs)
    ) { c ->
        prefs.edit().putBoolean(BackgroundPrefs.KEY_ENABLED, c).apply()
        if (c) requestNotificationPermission()
    })

    // 2. Section label "Pace while closed"
    col.addView(TextView(this).apply {
        text = "Pace while closed"
        setTextColor(0xFF9AA7B4.toInt())
        textSize = 13f
        setPadding(dp(4), dp(12), 0, dp(4))
    })

    // 3. Pace selection grid
    val paceGrid = GridLayout(this).apply {
        columnCount = 5
    }
    val paceHandles = ArrayList<CardHandle>()
    fun selectPace(sel: Int) {
        paceHandles.forEachIndexed { i, h -> h.setSelected(i == sel) }
    }
    val paceGlyphs = arrayOf("🐢", "🚶", "🚗", "✈", "🚀")
    val paceLabels = arrayOf("5 yrs/h", "10 yrs/h", "25 yrs/h", "50 yrs/h", "100 yrs/h")
    val paceIndex = BackgroundPrefs.paceIndex(prefs)
    for (i in BackgroundPrefs.PACE_YEARS_PER_HOUR.indices) {
        val h = panelCard(paceGlyphs[i], paceLabels[i]) {
            prefs.edit().putInt(BackgroundPrefs.KEY_PACE, i).apply()
            selectPace(i)
        }
        paceHandles.add(h)
        addCard(paceGrid, h)
    }
    selectPace(paceIndex)
    col.addView(paceGrid)

    // 4. Section label "Events"
    col.addView(TextView(this).apply {
        text = "Events"
        setTextColor(0xFF9AA7B4.toInt())
        textSize = 13f
        setPadding(dp(4), dp(12), 0, dp(4))
    })

    // 5. Event group rows
    for (g in BackgroundPrefs.GROUPS) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Left: title and description
        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        leftCol.addView(TextView(this).apply {
            text = g.title
            setTextColor(0xFFEEF2F6.toInt())
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        leftCol.addView(TextView(this).apply {
            text = g.desc
            setTextColor(0xFF9AA7B4.toInt())
            textSize = 12f
        })
        row.addView(leftCol)

        // Right: Notify and Pause checkboxes
        val notifyCb = CheckBox(this).apply {
            text = "Notify"
            setTextColor(0xFFEEF2F6.toInt())
            isChecked = BackgroundPrefs.notify(prefs, g)
            setOnCheckedChangeListener { _, c ->
                prefs.edit().putBoolean(BackgroundPrefs.notifyKey(g), c).apply()
            }
        }
        val pauseCb = CheckBox(this).apply {
            text = "Pause"
            setTextColor(0xFFEEF2F6.toInt())
            isChecked = BackgroundPrefs.pause(prefs, g)
            setOnCheckedChangeListener { _, c ->
                prefs.edit().putBoolean(BackgroundPrefs.pauseKey(g), c).apply()
            }
        }
        row.addView(notifyCb)
        row.addView(pauseCb)

        col.addView(row)
    }

    // 6. Test notification button
    val testBtn = android.widget.Button(this).apply {
        setText("Send a test notification")
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setOnClickListener {
            requestNotificationPermission()
            Notifier.post(this@backgroundSettingsTab, BackgroundPrefs.GROUPS[0], "Fire reported!", "Test notification — tap to go to the map centre", 60, 50)
        }
    }
    col.addView(testBtn)

    // Exact alarms (Android 12+) are a special permission the user grants in system settings.
    if (android.os.Build.VERSION.SDK_INT >= 31 &&
        !getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()) {
        col.addView(settingsToggle("Exact timing",
            "Allow exact alarms so notifications arrive right when events happen (else they can be a few minutes late).",
            false) { c ->
            if (c) startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                android.net.Uri.parse("package:$packageName")))
        })
    }

    return col
}
