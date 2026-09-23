package micropolis.port

import android.content.SharedPreferences

/** Settings for background play (DESIGN.md §12). Task 055 shows them; 056/057 read them. */
object BackgroundPrefs {
    const val KEY_ENABLED = "bgEnabled"
    const val KEY_PACE = "bgPace"                     // index into PACE_YEARS_PER_HOUR
    val PACE_YEARS_PER_HOUR = intArrayOf(5, 10, 25, 50, 100)
    const val DEFAULT_PACE = 1                        // 10 game-years per hour

    /** Event groups: id (used in pref keys), title, description, default notify, default pause. */
    class Group(val id: String, val title: String, val desc: String, val notify: Boolean, val pause: Boolean)
    val GROUPS = listOf(
        Group("disasters",  "Disasters",      "Fire, flood, tornado, earthquake, monster, meltdown, crashes.", true, true),
        Group("newYear",    "New year",       "The annual report is ready.",                                    true, false),
        Group("problems",   "City problems",  "Brownouts, traffic jams, high crime or pollution, low funds.",   true, false),
        Group("milestones", "Milestones",     "Your city reaches a new size: town, city, capital, …",          true, false),
    )

    fun notifyKey(g: Group) = "bgNotify_${g.id}"
    fun pauseKey(g: Group) = "bgPause_${g.id}"

    fun enabled(p: SharedPreferences) = p.getBoolean(KEY_ENABLED, false)
    fun paceIndex(p: SharedPreferences) = p.getInt(KEY_PACE, DEFAULT_PACE).coerceIn(0, PACE_YEARS_PER_HOUR.size - 1)
    fun notify(p: SharedPreferences, g: Group) = p.getBoolean(notifyKey(g), g.notify)
    fun pause(p: SharedPreferences, g: Group) = p.getBoolean(pauseKey(g), g.pause)
}
