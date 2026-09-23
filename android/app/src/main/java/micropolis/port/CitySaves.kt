package micropolis.port

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/**
 * Saves are plain .cty files in the public Documents/Micropolis folder (visible in the Files app).
 * Load / Save-as go through the system file picker; autosaves are written here directly as
 * `<city>-<gameYear>-<gameMonth>-<yyyyMMdd-HHmmss>.cty`. The engine only reads/writes paths, so picker
 * URIs are staged through a temp file in cacheDir.
 */
object CitySaves {
    const val FOLDER = "Micropolis"
    private const val AUTOSAVES_KEPT = 10
    private val autosaveSuffix = Regex("-\\d{1,5}-\\d{2}-\\d{8}-\\d{6}$")

    /** Picker start location: Documents/Micropolis on the primary volume. */
    private val folderUri: Uri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents", "primary:Documents/$FOLDER")

    /** Open-a-.cty picker that starts in the saves folder. */
    class OpenCity : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent =
            super.createIntent(context, input).apply {
                if (Build.VERSION.SDK_INT >= 26) putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
            }
    }

    /** Save-as picker (input = suggested file name) that starts in the saves folder. */
    class SaveCity : ActivityResultContracts.CreateDocument("application/octet-stream") {
        override fun createIntent(context: Context, input: String): Intent =
            super.createIntent(context, input).apply {
                if (Build.VERSION.SDK_INT >= 26) putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
            }
    }

    fun tempFile(ctx: Context) = File(ctx.cacheDir, "staging.cty")

    fun copyToUri(ctx: Context, src: File, dst: Uri) {
        ctx.contentResolver.openOutputStream(dst, "wt")!!.use { out -> src.inputStream().use { it.copyTo(out) } }
    }

    fun copyFromUri(ctx: Context, src: Uri, dst: File) {
        ctx.contentResolver.openInputStream(src)!!.use { inp -> dst.outputStream().use { inp.copyTo(it) } }
    }

    /** City name for a picked file: display name minus ".cty" and minus any autosave suffix. */
    fun cityNameFor(ctx: Context, uri: Uri): String {
        var name = "Micropolis"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0)
        }
        return name.removeSuffix(".cty").replace(autosaveSuffix, "").ifBlank { "Micropolis" }
    }

    /** Write `src` into the saves folder as `<city>-<year>-<month>-<timestamp>.cty`, then prune old ones. */
    fun writeAutosave(ctx: Context, src: File, city: String, year: Int, month: Int) {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        writePublic(ctx, src, "$city-$year-%02d-$stamp.cty".format(month + 1))
        prune(ctx, city)
    }

    /** Copy a file into the saves folder under `name` (used by the one-time migration too). */
    fun writePublic(ctx: Context, src: File, name: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/$FOLDER/")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return
            copyToUri(ctx, src, uri)
        } else {
            // Pre-Q: app-specific external dir needs no permission.
            src.copyTo(File(legacyDir(ctx), name), overwrite = true)
        }
    }

    private fun legacyDir(ctx: Context) = File(ctx.getExternalFilesDir(null), FOLDER).apply { mkdirs() }

    /** Keep only the newest AUTOSAVES_KEPT autosaves of this city (only files this app wrote). */
    private fun prune(ctx: Context, city: String) {
        val pattern = Regex(Regex.escape(city) + autosaveSuffix.pattern.removeSuffix("$") + "\\.cty$")
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = MediaStore.Files.getContentUri("external")
            val found = ArrayList<Pair<String, Uri>>()
            ctx.contentResolver.query(collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf("Documents/$FOLDER/"), null)?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(1) ?: continue
                    if (pattern.matches(n)) found.add(n to android.content.ContentUris.withAppendedId(collection, c.getLong(0)))
                }
            }
            // names end in a sortable timestamp; newest last
            found.sortBy { it.first.takeLast(19) }
            found.dropLast(AUTOSAVES_KEPT).forEach { ctx.contentResolver.delete(it.second, null, null) }
        } else {
            legacyDir(ctx).listFiles { f -> pattern.matches(f.name) }
                ?.sortedBy { it.name.takeLast(19) }?.dropLast(AUTOSAVES_KEPT)?.forEach { it.delete() }
        }
    }
}
