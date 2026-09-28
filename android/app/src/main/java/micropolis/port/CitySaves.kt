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
 * Saves are plain .cty files in the public Documents/Andropolis folder (visible in the Files app).
 * Load / Save-as go through the system file picker; autosaves are written here directly, one
 * per-city subfolder each (`Documents/Andropolis/<city> - autosave/`), as
 * `<city>-<gameYear>-<gameMonth>-<yyyyMMdd-HHmmss>.cty`. The engine only reads/writes paths, so
 * picker URIs are staged through a temp file in cacheDir.
 *
 * Each save can carry a message-log side file next to it (same name, `.cty` → `.messages.json`),
 * so loading a save restores its message feed. This only works for saves under Documents (API 29+):
 * the picker grants access to the one file it returns, so the sibling is written through
 * MediaStore by relative path, which only resolves under Documents/.
 */
object CitySaves {
    const val FOLDER = "Andropolis"
    private const val AUTOSAVES_KEPT = 10
    private val autosaveSuffix = Regex("-\\d{1,5}-\\d{2}-\\d{8}-\\d{6}$")

    /** Picker start location: Documents/Andropolis on the primary volume. */
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
        var name = "My City"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0)
        }
        return name.removeSuffix(".cty").replace(autosaveSuffix, "").ifBlank { "My City" }
    }

    /** Subfolder autosaves of `city` live in, under Documents/Andropolis. */
    private fun autosaveDir(city: String) = "$city - autosave"

    /** Regex matching this city's autosave file names (used by prune and moveFlatAutosaves). */
    private fun autosavePattern(city: String) =
        Regex(Regex.escape(city) + autosaveSuffix.pattern.removeSuffix("$") + "\\.cty$")

    /** Message-log side file name for a .cty display name. */
    private fun sideName(ctyName: String) = ctyName.removeSuffix(".cty") + ".messages.json"

    /** (relative path with trailing slash, display name) for a picker URI, or null if it isn't
     *  a file under Documents/ on the primary volume (Drive, Downloads, other providers/volumes —
     *  MediaStore can't resolve a sibling there). */
    private fun locate(ctx: Context, uri: Uri): Pair<String, String>? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        return try {
            val id = DocumentsContract.getDocumentId(uri)   // "primary:Documents/Andropolis/Damesville.cty"
            val path = id.removePrefix("primary:")
            if (path == id || !path.startsWith("Documents/")) return null
            val slash = path.lastIndexOf('/')
            if (slash < 0) return null
            path.substring(0, slash + 1) to path.substring(slash + 1)
        } catch (e: Exception) { null }
    }

    /** MediaStore upsert of a small text file at relPath/name. A side file must never break a
     *  save, so any failure is swallowed (logged only). */
    private fun writeMedia(ctx: Context, relPath: String, name: String, text: String) {
        try {
            val collection = MediaStore.Files.getContentUri("external")
            var uri: Uri? = null
            ctx.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(relPath, name), null)?.use { c ->
                if (c.moveToFirst()) uri = android.content.ContentUris.withAppendedId(collection, c.getLong(0))
            }
            if (uri == null) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                }
                uri = ctx.contentResolver.insert(collection, values)
            }
            uri?.let { ctx.contentResolver.openOutputStream(it, "wt")!!.use { out -> out.write(text.toByteArray()) } }
        } catch (e: Exception) { android.util.Log.w("Andropolis", "side file write failed", e) }
    }

    /** Read a small text file at relPath/name via MediaStore; null if there is none or on error. */
    private fun readMedia(ctx: Context, relPath: String, name: String): String? {
        return try {
            val collection = MediaStore.Files.getContentUri("external")
            var uri: Uri? = null
            ctx.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(relPath, name), null)?.use { c ->
                if (c.moveToFirst()) uri = android.content.ContentUris.withAppendedId(collection, c.getLong(0))
            }
            uri?.let { ctx.contentResolver.openInputStream(it)!!.use { inp -> inp.readBytes().toString(Charsets.UTF_8) } }
        } catch (e: Exception) { null }
    }

    /** Write the message-log side file next to a picked .cty (Save-as). API 29+ only. */
    fun writeSideFile(ctx: Context, cityUri: Uri, json: String) {
        if (Build.VERSION.SDK_INT < 29) return
        val (relPath, name) = locate(ctx, cityUri) ?: return
        writeMedia(ctx, relPath, sideName(name), json)
    }

    /** Read the message-log side file next to a picked .cty (Load), or null if there isn't one. */
    fun readSideFile(ctx: Context, cityUri: Uri): String? {
        if (Build.VERSION.SDK_INT < 29) return null
        val (relPath, name) = locate(ctx, cityUri) ?: return null
        return readMedia(ctx, relPath, sideName(name))
    }

    /** Write `src` into the city's autosave subfolder as `<city>-<year>-<month>-<timestamp>.cty`,
     *  with a message-log side file alongside if `messagesJson` is given, sweep in any autosaves
     *  left over from the old flat layout, then prune old ones. */
    fun writeAutosave(ctx: Context, src: File, city: String, year: Int, month: Int, messagesJson: String? = null) {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val name = "$city-$year-%02d-$stamp.cty".format(month + 1)
        writePublic(ctx, src, name, subdir = autosaveDir(city))
        if (messagesJson != null) {
            if (Build.VERSION.SDK_INT >= 29) {
                writeMedia(ctx, "Documents/$FOLDER/${autosaveDir(city)}/", sideName(name), messagesJson)
            } else {
                try { File(legacyDir(ctx), autosaveDir(city)).apply { mkdirs() }
                    .let { File(it, sideName(name)).writeText(messagesJson) } }
                catch (e: Exception) { android.util.Log.w("Andropolis", "autosave side file failed", e) }
            }
        }
        moveFlatAutosaves(ctx, city)
        prune(ctx, city)
    }

    /** Copy a file into the saves folder (or a subfolder of it) under `name`. Used by the
     *  one-time migration too. */
    fun writePublic(ctx: Context, src: File, name: String, subdir: String? = null) {
        if (Build.VERSION.SDK_INT >= 29) {
            val relPath = if (subdir != null) "Documents/$FOLDER/$subdir/" else "Documents/$FOLDER/"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return
            copyToUri(ctx, src, uri)
        } else {
            // Pre-Q: app-specific external dir needs no permission.
            val dir = if (subdir != null) File(legacyDir(ctx), subdir).apply { mkdirs() } else legacyDir(ctx)
            src.copyTo(File(dir, name), overwrite = true)
        }
    }

    private fun legacyDir(ctx: Context) = File(ctx.getExternalFilesDir(null), FOLDER).apply { mkdirs() }

    /** One-time tidy: move any autosaves of `city` still sitting flat in the saves folder (the
     *  old layout) into its autosave subfolder. Cheap once the move has happened once. */
    private fun moveFlatAutosaves(ctx: Context, city: String) {
        val pattern = autosavePattern(city)
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = MediaStore.Files.getContentUri("external")
            ctx.contentResolver.query(collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf("Documents/$FOLDER/"), null)?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(1) ?: continue
                    if (!pattern.matches(n)) continue
                    val uri = android.content.ContentUris.withAppendedId(collection, c.getLong(0))
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/$FOLDER/${autosaveDir(city)}/")
                        }
                        ctx.contentResolver.update(uri, values, null, null)
                    } catch (e: Exception) {
                        // Not ours to move (SecurityException) or a name clash; leave it.
                    }
                }
            }
        } else {
            val dest = File(legacyDir(ctx), autosaveDir(city)).apply { mkdirs() }
            legacyDir(ctx).listFiles { f -> pattern.matches(f.name) }
                ?.forEach { it.renameTo(File(dest, it.name)) }
        }
    }

    /** Keep only the newest AUTOSAVES_KEPT autosaves of this city (only files this app wrote);
     *  a pruned autosave's message-log side file goes with it. Side files don't count toward
     *  AUTOSAVES_KEPT themselves. */
    private fun prune(ctx: Context, city: String) {
        val pattern = autosavePattern(city)
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = MediaStore.Files.getContentUri("external")
            val found = ArrayList<Pair<String, Uri>>()
            val sideUris = HashMap<String, Uri>()   // display name -> uri, for side files in this folder
            ctx.contentResolver.query(collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf("Documents/$FOLDER/${autosaveDir(city)}/"), null)?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(1) ?: continue
                    val uri = android.content.ContentUris.withAppendedId(collection, c.getLong(0))
                    if (pattern.matches(n)) found.add(n to uri)
                    else if (n.endsWith(".messages.json")) sideUris[n] = uri
                }
            }
            // names end in a sortable timestamp; newest last
            found.sortBy { it.first.takeLast(19) }
            found.dropLast(AUTOSAVES_KEPT).forEach { (n, uri) ->
                ctx.contentResolver.delete(uri, null, null)
                sideUris[sideName(n)]?.let { ctx.contentResolver.delete(it, null, null) }
            }
        } else {
            val dir = File(legacyDir(ctx), autosaveDir(city))
            dir.listFiles { f -> pattern.matches(f.name) }
                ?.sortedBy { it.name.takeLast(19) }?.dropLast(AUTOSAVES_KEPT)?.forEach {
                    it.delete()
                    File(dir, sideName(it.name)).delete()
                }
        }
    }
}
