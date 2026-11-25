package com.movieshorts.pro

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        delay(900)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.logo), "MovieShorts Pro",
                Modifier.fillMaxWidth(0.9f).graphicsLayer { alpha = a.value; scaleX = 0.85f + 0.15f * a.value; scaleY = 0.85f + 0.15f * a.value },
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(20.dp))
            Text("Poori movie, chhote shorts", color = Muted, fontSize = 14.sp, modifier = Modifier.graphicsLayer { alpha = a.value })
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(progress = { a.value }, modifier = Modifier.width(140.dp).graphicsLayer { alpha = a.value }, color = Cyan, trackColor = CardC)
        }
    }
}

@Composable
fun AuthScreen(store: Store, onDone: () -> Unit) {
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var ident by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.logo), null, Modifier.fillMaxWidth(0.7f), contentScale = ContentScale.Fit)
        Text(if (register) "Naya Account Banao" else "Login Karo", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (register) {
            Field(name, { name = it }, "Naam")
            Field(email, { email = it }, "Email", KeyboardType.Email)
            Field(phone, { phone = it }, "Mobile Number", KeyboardType.Phone)
        } else {
            Field(ident, { ident = it }, "Email ya Mobile Number")
        }
        Field(pass, { pass = it }, "Password", KeyboardType.Password, true)
        if (err.isNotEmpty()) Text(err, color = Color(0xFFFF7777), modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                scope.launch {
                    busy = true; err = ""
                    val r = if (register) Api.post("register", JSONObject().put("name", name).put("email", email).put("phone", phone).put("password", pass))
                    else Api.post("login", JSONObject().put("identifier", ident).put("password", pass))
                    busy = false
                    if (r.has("token")) {
                        store.token = r.getString("token"); store.name = r.optString("name"); store.coins = r.optInt("coins")
                        onDone()
                    } else err = r.optString("error", "Error")
                }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (busy) "Please wait…" else if (register) "Account Banao" else "Login") }
        TextButton(onClick = { register = !register; err = "" }) {
            Text(if (register) "Pehle se account hai? Login karo" else "Naya account banao")
        }
    }
}

@Composable
fun HomeScreen(store: Store, tick: Int, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val projects = remember(tick) { store.projects() }
    var askName by remember { mutableStateOf(false) }
    var pname by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var durMs by remember { mutableLongStateOf(0L) }
    var sel by remember { mutableIntStateOf(90) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            val d = videoDuration(ctx, uri)
            if (d <= 0) Toast.makeText(ctx, "Ye video read nahi ho paayi", Toast.LENGTH_LONG).show()
            else { durMs = d; pickedUri = uri }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Image(painterResource(R.drawable.logo), null, Modifier.fillMaxWidth(0.55f).align(Alignment.CenterHorizontally), contentScale = ContentScale.Fit)
        Text("Namaste, ${store.name} 👋", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Aaj kaunsi movie ke shorts banayenge?", color = Muted)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f), { nav(Screen.Wallet) }) { Text("🪙 ${store.coins}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Cyan); Text("Coins", color = Muted) }
            Tile(Modifier.weight(1f), { nav(Screen.Projects) }) { Text("${projects.size}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Cyan); Text("Projects", color = Muted) }
            Tile(Modifier.weight(1f), { nav(Screen.Projects) }) { Text("${projects.sumOf { it.clips.size }}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Cyan); Text("Clips", color = Muted) }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { askName = true }, Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp)) {
            Text("⬆  Movie Upload Karo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f), { nav(Screen.Projects) }) { Text("📁", fontSize = 26.sp); Text("Projects") }
            Tile(Modifier.weight(1f), { nav(Screen.Wallet) }) { Text("💰", fontSize = 26.sp); Text("Wallet") }
            Tile(Modifier.weight(1f), { nav(Screen.Settings) }) { Text("⚙️", fontSize = 26.sp); Text("Settings") }
        }
        if (projects.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Recent Projects", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            for (p in projects.take(3)) {
                Spacer(Modifier.height(8.dp))
                Tile(Modifier.fillMaxWidth(), { nav(Screen.Detail(p.id)) }) { Text("${p.name}  ·  ${p.clips.size} clips") }
            }
        }
    }
    if (askName) {
        AlertDialog(
            onDismissRequest = { askName = false },
            title = { Text("Project ka naam") },
            text = { Field(pname, { pname = it }, "Project Name") },
            confirmButton = {
                Button(onClick = {
                    if (pname.isBlank()) Toast.makeText(ctx, "Project ka naam daalo", Toast.LENGTH_SHORT).show()
                    else { askName = false; picker.launch(arrayOf("video/*")) }
                }) { Text("Movie Chuno") }
            },
            dismissButton = { TextButton(onClick = { askName = false }) { Text("Cancel") } }
        )
    }
    if (pickedUri != null) {
        AlertDialog(
            onDismissRequest = { pickedUri = null },
            title = { Text("Clip duration chuno") },
            text = {
                Column {
                    Text("Movie: ${fmtTime(durMs)}", color = Muted)
                    for ((sec, label) in listOf(60 to "1 min", 90 to "1.5 min", 180 to "3 min", 300 to "5 min")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sel == sec, onClick = { sel = sec })
                            Text(label)
                        }
                    }
                    Text("${((durMs + sel * 1000L - 1) / (sel * 1000L))} clips banenge", color = Cyan)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val p = buildProject(pname.trim(), pickedUri.toString(), durMs, sel)
                    val all = store.projects(); all.add(0, p); store.save(all)
                    val token = store.token; val secs = ((durMs + 999) / 1000).toInt(); val len = sel; val nm = p.name
                    CoroutineScope(Dispatchers.Default).launch {
                        Api.post("project_create", JSONObject().put("name", nm).put("movie_name", nm).put("movie_duration", secs).put("clip_seconds", len), token)
                    }
                    pickedUri = null; pname = ""
                    nav(Screen.Detail(p.id))
                }) { Text("Clips Banao") }
            },
            dismissButton = { TextButton(onClick = { pickedUri = null }) { Text("Cancel") } }
        )
    }
}
