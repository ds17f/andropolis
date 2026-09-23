package micropolis.port

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock

/** Small SoundPool wrapper. Throttles repeats so fast sims don't spam the speaker. */
class SoundFx(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val engineRes = intArrayOf(
        R.raw.snd_siren, R.raw.snd_explosion_low, R.raw.snd_explosion_high, R.raw.snd_monster,
        R.raw.snd_honk_low, R.raw.snd_honk_med, R.raw.snd_honk_high, R.raw.snd_heavy_traffic,
        R.raw.snd_foghorn, R.raw.snd_uhuh, R.raw.snd_sorry)
    private val engineIds = IntArray(engineRes.size) { pool.load(context, engineRes[it], 1) }
    private val buildId = pool.load(context, R.raw.snd_build, 1)
    private val dozeId = pool.load(context, R.raw.snd_bulldozer, 1)
    private val lastPlayed = LongArray(engineRes.size + 2)
    var enabled = true

    private fun play(slot: Int, id: Int, minGapMs: Long) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayed[slot] < minGapMs) return
        lastPlayed[slot] = now
        pool.play(id, 0.8f, 0.8f, 1, 0, 1f)
    }
    fun engineSound(sound: Int) { if (sound in engineIds.indices) play(sound, engineIds[sound], 400) }
    fun build() = play(engineRes.size, buildId, 120)
    fun bulldoze() = play(engineRes.size + 1, dozeId, 120)
    fun release() = pool.release()
}
