package com.movieshorts.pro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Clip(val no: Int, var startMs: Long, var endMs: Long, var format: String = "original", var status: String = "new")
data class Project(val id: Long, val name: String, val movieUri: String, val durationMs: Long, val clipSec: Int, val clips: MutableList<Clip>, val created: Long)

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("msp", Context.MODE_PRIVATE)
    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) { sp.edit().putString("token", v).apply() }
    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) { sp.edit().putString("name", v).apply() }
    var coins: Int
        get() = sp.getInt("coins", 0)
        set(v) { sp.edit().putInt("coins", v).apply() }
    var notif: Boolean
        get() = sp.getBoolean("notif", true)
        set(v) { sp.edit().putBoolean("notif", v).apply() }

    fun logout() { sp.edit().remove("token").remove("name").remove("coins").apply() }

    fun projects(): MutableList<Project> {
        val arr = JSONArray(sp.getString("projects", "[]"))
        val out = mutableListOf<Project>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ca = o.getJSONArray("clips")
            val cl = mutableListOf<Clip>()
            for (j in 0 until ca.length()) {
                val c = ca.getJSONObject(j)
                cl.add(Clip(c.getInt("no"), c.getLong("s"), c.getLong("e"), c.getString("f"), c.getString("st")))
            }
            out.add(Project(o.getLong("id"), o.getString("name"), o.getString("uri"), o.getLong("dur"), o.getInt("len"), cl, o.getLong("created")))
        }
        return out
    }

    fun save(list: List<Project>) {
        val arr = JSONArray()
        for (p in list) {
            val ca = JSONArray()
            for (c in p.clips) ca.put(JSONObject().put("no", c.no).put("s", c.startMs).put("e", c.endMs).put("f", c.format).put("st", c.status))
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("uri", p.movieUri).put("dur", p.durationMs).put("len", p.clipSec).put("created", p.created).put("clips", ca))
        }
        sp.edit().putString("projects", arr.toString()).apply()
    }
}

fun buildProject(name: String, uri: String, durMs: Long, lenSec: Int): Project {
    val len = lenSec * 1000L
    val n = ((durMs + len - 1) / len).toInt()
    val clips = (1..n).map { Clip(it, (it - 1) * len, minOf(it * len, durMs)) }.toMutableList()
    val now = System.currentTimeMillis()
    return Project(now, name, uri, durMs, lenSec, clips, now)
}

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

fun videoDuration(ctx: Context, uri: Uri): Long {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(ctx, uri)
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
    } catch (e: Exception) { 0L } finally { try { r.release() } catch (e: Exception) { } }
}

fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifEmpty { "project" }

object Api {
    private fun call(query: String, method: String, body: JSONObject?, token: String?): JSONObject {
        return try {
            val conn = URL(BuildConfig.API_BASE + "api/index.php?" + query).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrEmpty()) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) { conn.doOutput = true; conn.outputStream.use { it.write(body.toString().toByteArray()) } }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val j = try { JSONObject(text) } catch (e: Exception) { JSONObject().put("error", "Server se galat jawab aaya") }
            if (code !in 200..299 && !j.has("error")) j.put("error", "Error $code")
            j
        } catch (e: Exception) { JSONObject().put("error", "Internet ya server ki problem hai") }
    }
    suspend fun post(r: String, body: JSONObject, token: String? = null) = withContext(Dispatchers.IO) { call("r=$r", "POST", body, token) }
    suspend fun get(r: String, token: String?) = withContext(Dispatchers.IO) { call("r=$r", "GET", null, token) }
}

object Notifier {
    fun done(ctx: Context, store: Store, text: String) {
        if (!store.notif) return
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("msp", "MovieShorts", NotificationManager.IMPORTANCE_DEFAULT)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val n = NotificationCompat.Builder(ctx, "msp").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("MovieShorts Pro").setContentText(text).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt(), n) } catch (e: SecurityException) { }
    }
}

object Exporter {
    suspend fun export(ctx: Context, p: Project, c: Clip): File = suspendCancellableCoroutine { cont ->
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), safeName(p.name)).apply { mkdirs() }
        val out = File(dir, "${safeName(p.name)}_Clip_${c.no}${if (c.format == "reel_9_16") "_reel" else ""}.mp4")
        if (out.exists()) out.delete()
        val clipping = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build()
        val item = MediaItem.Builder().setUri(Uri.parse(p.movieUri)).setClippingConfiguration(clipping).build()
        val vfx: List<Effect> = if (c.format == "reel_9_16")
            listOf(Presentation.createForAspectRatio(9f / 16f, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)) else listOf()
        val edited = EditedMediaItem.Builder(item).setEffects(Effects(listOf<AudioProcessor>(), vfx)).build()
        val t = Transformer.Builder(ctx)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(out) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { if (cont.isActive) cont.resumeWithException(exportException) }
            }).build()
        t.start(edited, out.absolutePath)
        cont.invokeOnCancellation { t.cancel() }
    }

    fun saveToGallery(ctx: Context, f: File) {
        if (Build.VERSION.SDK_INT < 29) return
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, f.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MovieShortsPro")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
    }
}

suspend fun exportClip(ctx: Context, store: Store, p: Project, c: Clip): Result<File> {
    return try {
        val f = Exporter.export(ctx, p, c)
        val r = Api.post("spend", JSONObject(), store.token)
        if (r.has("error")) {
            f.delete()
            val m = if (r.optString("error") == "not enough coins") "Coins kam hain. Wallet se coins add karo." else r.optString("error")
            return Result.failure(Exception(m))
        }
        store.coins = r.optInt("coins", store.coins)
        Exporter.saveToGallery(ctx, f)
        val all = store.projects()
        all.find { it.id == p.id }?.clips?.find { it.no == c.no }?.status = "exported"
        store.save(all)
        Notifier.done(ctx, store, "${p.name} Clip ${c.no} ready hai")
        Result.success(f)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
}
