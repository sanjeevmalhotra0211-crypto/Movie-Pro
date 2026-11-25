@file:OptIn(ExperimentalMaterial3Api::class)

package com.movieshorts.pro

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectsScreen(store: Store, tick: Int, bump: () -> Unit, open: (Long) -> Unit, back: () -> Unit) {
    val projects = remember(tick) { store.projects() }
    var del by remember { mutableStateOf<Project?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("Mere Projects", back)
        if (projects.isEmpty()) Text("Abhi koi project nahi hai. Home par jaakar Movie Upload karo.", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(projects) { p ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardC).clickable { open(p.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${p.clips.size} clips · ${p.clipSec / 60.0} min each · ${fmtTime(p.durationMs)}", color = Muted, fontSize = 13.sp)
                        Text(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(p.created)), color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { del = p }) { Text("🗑") }
                }
            }
        }
    }
    del?.let { p ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("Project delete karein?") },
            text = { Text("${p.name} app se hat jayega (gallery mein export hui clips bani rahengi).") },
            confirmButton = {
                Button(onClick = {
                    val all = store.projects(); all.removeAll { it.id == p.id }; store.save(all); del = null; bump()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { del = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun DetailScreen(store: Store, pid: Long, tick: Int, bump: () -> Unit, edit: (Int) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val project = remember(tick) { store.projects().find { it.id == pid } } ?: return
    var menu by remember { mutableStateOf<Clip?>(null) }
    var busy by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<File?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val fmt = project.clips.firstOrNull()?.format ?: "original"

    fun setAll(f: String) {
        val all = store.projects()
        all.find { it.id == pid }?.clips?.forEach { it.format = f; if (it.status == "exported") it.status = "new" }
        store.save(all); bump()
    }
    fun exportOne(c: Clip) {
        job = scope.launch {
            busy = "Clip ${c.no} export ho rahi hai…"
            val r = exportClip(ctx, store, project, c)
            busy = ""
            r.onSuccess { result = it }.onFailure { Toast.makeText(ctx, it.message ?: "Export fail", Toast.LENGTH_LONG).show() }
            bump()
        }
    }

    LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                TopBar(project.name, back)
                Text("${project.clips.size} clips · ${project.clipSec / 60.0} min · Movie ${fmtTime(project.durationMs)}", color = Muted)
                Spacer(Modifier.height(10.dp))
                Text("Clip Format (saari clips par lagega):", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = fmt == "original", onClick = { setAll("original") }, label = { Text("Original 16:9") })
                    FilterChip(selected = fmt == "reel_9_16", onClick = { setAll("reel_9_16") }, label = { Text("Reel 9:16") })
                }
                Spacer(Modifier.height(8.dp))
                if (busy.isEmpty()) {
                    Button(onClick = {
                        job = scope.launch {
                            val fresh = store.projects().find { it.id == pid } ?: return@launch
                            val todo = fresh.clips.filter { it.status != "exported" }
                            var done = 0
                            for (c in todo) {
                                busy = "Export ${done + 1}/${todo.size} · Clip ${c.no}"
                                val r = exportClip(ctx, store, fresh, c)
                                if (r.isFailure) { Toast.makeText(ctx, r.exceptionOrNull()?.message ?: "Export fail", Toast.LENGTH_LONG).show(); break }
                                done++
                            }
                            busy = ""; bump()
                            if (done > 0) Toast.makeText(ctx, "$done clips export ho gayi ✅", Toast.LENGTH_LONG).show()
                        }
                    }, Modifier.fillMaxWidth()) { Text("⬇ Saari Clips Export Karo") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Cyan, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(busy, Modifier.weight(1f))
                        TextButton(onClick = { job?.cancel(); busy = "" }) { Text("Stop") }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        items(project.clips) { c ->
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).background(CardC).clickable { menu = c }.padding(12.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (c.status == "exported") "✅" else if (c.status == "edited") "✏️" else "🎞️", fontSize = 22.sp)
                Text("Clip ${c.no}", fontWeight = FontWeight.Bold)
                Text("${fmtTime(c.startMs)}–${fmtTime(c.endMs)}", color = Muted, fontSize = 11.sp)
            }
        }
    }
    menu?.let { c ->
        AlertDialog(
            onDismissRequest = { menu = null },
            title = { Text("${project.name} Clip ${c.no}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ menu = null; exportOne(c) }, Modifier.fillMaxWidth()) { Text("✅ Done (Export)") }
                    OutlinedButton({ menu = null; edit(c.no) }, Modifier.fillMaxWidth()) { Text("✏️ Edit") }
                    TextButton({ menu = null }, Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            },
            confirmButton = {}
        )
    }
    result?.let { ResultDialog(it, ctx) { result = null } }
}

@Composable
fun ClipPlayer(uri: String, startMs: Long, endMs: Long, reel: Boolean) {
    val ctx = LocalContext.current
    val exo = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { exo.release() } }
    LaunchedEffect(uri, startMs, endMs) {
        val cc = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()
        exo.setMediaItem(MediaItem.Builder().setUri(uri).setClippingConfiguration(cc).build())
        exo.prepare()
        exo.playWhenReady = false
    }
    val shape = if (reel) Modifier.height(380.dp).aspectRatio(9f / 16f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    Box(shape.clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { player = exo; resizeMode = if (reel) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT } },
            update = { it.resizeMode = if (reel) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun EditorScreen(store: Store, pid: Long, no: Int, bump: () -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = remember { store.projects().find { it.id == pid } } ?: return
    val clip = p.clips.find { it.no == no } ?: return
    val origS = (no - 1) * p.clipSec * 1000L
    val origE = minOf(no * p.clipSec * 1000L, p.durationMs)
    var range by remember { mutableStateOf(clip.startMs.toFloat()..clip.endMs.toFloat()) }
    var applied by remember { mutableStateOf(clip.startMs to clip.endMs) }
    var fmt by remember { mutableStateOf(clip.format) }
    var applyAll by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<File?>(null) }
    val valid = (range.endInclusive - range.start) >= 2000f

    fun save() {
        val all = store.projects()
        val pp = all.find { it.id == pid } ?: return
        val c = pp.clips.find { it.no == no } ?: return
        c.startMs = range.start.toLong(); c.endMs = range.endInclusive.toLong(); c.format = fmt; c.status = "edited"
        if (applyAll) pp.clips.forEach { it.format = fmt }
        store.save(all); bump()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { TopBar("Edit · ${p.name} Clip $no", back) }
        ClipPlayer(p.movieUri, applied.first, applied.second, fmt == "reel_9_16")
        Spacer(Modifier.height(12.dp))
        Text("${fmtTime(range.start.toLong())} → ${fmtTime(range.endInclusive.toLong())}  (${fmtTime((range.endInclusive - range.start).toLong())})", color = Cyan, fontWeight = FontWeight.Bold)
        RangeSlider(
            value = range, onValueChange = { range = it },
            onValueChangeFinished = { if (valid) applied = range.start.toLong() to range.endInclusive.toLong() },
            valueRange = origS.toFloat()..origE.toFloat()
        )
        Text("Format", Modifier.align(Alignment.Start), fontSize = 13.sp)
        Row(Modifier.align(Alignment.Start), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = fmt == "original", onClick = { fmt = "original" }, label = { Text("Original 16:9") })
            FilterChip(selected = fmt == "reel_9_16", onClick = { fmt = "reel_9_16" }, label = { Text("Reel 9:16") })
        }
        Row(Modifier.align(Alignment.Start), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = applyAll, onCheckedChange = { applyAll = it })
            Text("Saari clips isi format mein karo")
        }
        Spacer(Modifier.height(8.dp))
        if (busy) {
            CircularProgressIndicator(color = Cyan)
            Text("Export ho rahi hai…", color = Muted)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { save(); Toast.makeText(ctx, "Save ho gaya", Toast.LENGTH_SHORT).show(); back() }, enabled = valid) { Text("💾 Save") }
                Button(onClick = {
                    save()
                    scope.launch {
                        busy = true
                        val fresh = store.projects().find { it.id == pid }
                        val c = fresh?.clips?.find { it.no == no }
                        if (fresh != null && c != null) {
                            exportClip(ctx, store, fresh, c).onSuccess { result = it }.onFailure { Toast.makeText(ctx, it.message ?: "Export fail", Toast.LENGTH_LONG).show() }
                        }
                        busy = false; bump()
                    }
                }, enabled = valid) { Text("⬇ Save & Export") }
            }
        }
        if (!valid) Text("Clip kam se kam 2 second ki honi chahiye", color = Color(0xFFFF7777))
    }
    result?.let { ResultDialog(it, ctx) { result = null; back() } }
}
