package com.movieshorts.pro

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun WalletScreen(store: Store, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var amount by remember { mutableStateOf("") }
    var utr by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        val r = Api.get("wallet", store.token)
        data = r
        if (r.has("coins")) store.coins = r.getInt("coins")
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar("Wallet", back)
        val d = data
        if (d == null) { CircularProgressIndicator(color = Cyan) }
        else if (d.has("error")) { Text(d.optString("error"), color = Color(0xFFFF7777)) }
        else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardC).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🪙 ${d.optInt("coins")}", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Cyan)
                Text("Coins balance", color = Muted)
            }
            Spacer(Modifier.height(16.dp))
            Text("Coins kharido", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("UPI ID: ${d.optString("upi_id").ifEmpty { "-" }}")
            if (d.optString("bank_details").isNotEmpty()) Text(d.optString("bank_details"))
            Text("₹1 = ${d.optInt("coins_per_inr")} coins. Payment karke amount aur UTR daalo. Admin approval ke baad coins add honge.", color = Muted, fontSize = 13.sp)
            Field(amount, { amount = it }, "Amount (₹)", KeyboardType.Decimal)
            Field(utr, { utr = it }, "UTR / Transaction ID")
            Button(onClick = {
                scope.launch {
                    busy = true
                    val r = Api.post("payment_submit", JSONObject().put("amount", amount.toDoubleOrNull() ?: 0.0).put("utr", utr.trim()), store.token)
                    busy = false
                    if (r.has("error")) Toast.makeText(ctx, r.optString("error"), Toast.LENGTH_LONG).show()
                    else { Toast.makeText(ctx, r.optString("message"), Toast.LENGTH_LONG).show(); amount = ""; utr = ""; reload++ }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Payment Submit Karo") }
            Spacer(Modifier.height(20.dp))
            Text("Transactions", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            val tx = d.optJSONArray("tx")
            if (tx != null) for (i in 0 until tx.length()) {
                val t = tx.getJSONObject(i)
                val credit = t.optString("type") == "credit"
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) { Text(t.optString("note")); Text(t.optString("created_at"), color = Muted, fontSize = 12.sp) }
                    Text((if (credit) "+" else "-") + t.optInt("coins"), color = if (credit) Color(0xFF4ADE80) else Color(0xFFFF7777), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(store: Store, back: () -> Unit, logout: () -> Unit) {
    val ctx = LocalContext.current
    var notif by remember { mutableStateOf(store.notif) }
    var latest by remember { mutableStateOf("") }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val r = Api.get("apk_latest", null)
        latest = r.optJSONObject("latest")?.optString("version") ?: ""
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TopBar("Settings", back)
        Text(store.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("🪙 ${store.coins} coins", color = Cyan)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Notifications"); Text("Export complete hone par alert", color = Muted, fontSize = 12.sp) }
            Switch(checked = notif, onCheckedChange = {
                notif = it; store.notif = it
                if (it && Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }
        Spacer(Modifier.height(16.dp))
        Text("App version: v${BuildConfig.VERSION_NAME}", color = Muted)
        if (latest.isNotEmpty() && latest != BuildConfig.VERSION_NAME) {
            Text("Naya version v$latest available hai", color = Cyan)
            Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.API_BASE))) }) { Text("Update Download Karo") }
        } else if (latest.isNotEmpty()) Text("Aapke paas latest version hai ✅", color = Color(0xFF4ADE80))
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = logout, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
    }
}
