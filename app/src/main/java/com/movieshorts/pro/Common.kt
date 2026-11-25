package com.movieshorts.pro

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun Field(v: String, onChange: (String) -> Unit, label: String, kb: KeyboardType = KeyboardType.Text, pw: Boolean = false) {
    OutlinedTextField(
        value = v, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        visualTransformation = if (pw) PasswordVisualTransformation() else VisualTransformation.None
    )
}

@Composable
fun TopBar(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = back) { Text("←  Back") }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Tile(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(CardC).clickable { onClick() }.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, content = content
    )
}

fun shareFile(ctx: Context, f: File, facebook: Boolean) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (facebook) {
        i.setPackage("com.facebook.katana")
        try { ctx.startActivity(i); return } catch (e: Exception) {
            i.setPackage(null)
            Toast.makeText(ctx, "Facebook app nahi mila, share menu khul raha hai", Toast.LENGTH_SHORT).show()
        }
    }
    ctx.startActivity(Intent.createChooser(i, "Share"))
}

@Composable
fun ResultDialog(file: File, ctx: Context, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Clip ready ✅") },
        text = { Text("Gallery (Movies/MovieShortsPro) mein save ho gayi.\n${file.name}") },
        confirmButton = { Button(onClick = { shareFile(ctx, file, true) }) { Text("Facebook par share") } },
        dismissButton = {
            Row {
                TextButton(onClick = { shareFile(ctx, file, false) }) { Text("Share") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    )
}
