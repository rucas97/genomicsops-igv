package com.genomicsops.igv

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genomicsops.igv.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Gene(val chrom: String, val start: Int, val end: Int, val name: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GenomicsTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    if (showSplash) SplashScreen { showSplash = false }
    else {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var genes by remember { mutableStateOf<List<Gene>>(emptyList()) }
        var fileName by rememberSaveable { mutableStateOf("No track loaded") }

        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val parsed = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
                                ?.filter { !it.startsWith("track") && !it.startsWith("#") && it.isNotBlank() }
                                ?.mapNotNull { line ->
                                    val parts = line.trim().split(Regex("\\s+"))
                                    if (parts.size >= 4) {
                                        Gene(parts[0], parts[1].toIntOrNull() ?: 0, parts[2].toIntOrNull() ?: 0, parts[3])
                                    } else null
                                }
                        } catch (e: Exception) { emptyList() }
                    }
                    genes = parsed ?: emptyList()
                    fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Loaded Track"
                }
            }
        }

        Column(Modifier.fillMaxSize().background(GenomicsDark)) {
            // Top Bar
            Surface(color = GenomicsSurface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Genome Browser", color = GenomicsCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(fileName, color = GenomicsMuted, fontSize = 12.sp)
                    }
                    Button(onClick = { filePicker.launch("*/*") }, colors = ButtonDefaults.buttonColors(containerColor = GenomicsDark)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Load", tint = GenomicsTeal)
                        Spacer(Modifier.width(8.dp))
                        Text("Load .bed", color = GenomicsText)
                    }
                }
            }

            if (genes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tap 'Load .bed' to view a genomic track", color = GenomicsMuted)
                }
            } else {
                // Native Canvas Track Viewer
                Surface(color = GenomicsSurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxEnd = genes.maxOfOrNull { it.end }?.toFloat() ?: 1f
                        val startX = 50f
                        val endX = size.width - 20f
                        val trackWidth = endX - startX
                        
                        // Draw chromosome line
                        drawLine(
                            color = GenomicsMuted,
                            start = Offset(startX, size.height / 2),
                            end = Offset(endX, size.height / 2),
                            strokeWidth = 4f
                        )
                        
                        // Draw genes
                        val colors = listOf(Color(0xFF00E5FF), Color(0xFF00FFAA), Color(0xFFFF0055), Color(0xFFFFAA00))
                        genes.forEachIndexed { index, gene ->
                            val x1 = startX + (gene.start / maxEnd) * trackWidth
                            val x2 = startX + (gene.end / maxEnd) * trackWidth
                            val y = (size.height / 2) - 20f
                            
                            drawRect(
                                color = colors[index % colors.size],
                                topLeft = Offset(x1, y),
                                size = Size(x2 - x1, 40f)
                            )
                        }
                    }
                }
                
                // Gene List
                genes.forEach { gene ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(gene.chrom, color = GenomicsTeal, modifier = Modifier.width(60.dp))
                        Text("${gene.start}..${gene.end}", color = GenomicsMuted, modifier = Modifier.width(120.dp))
                        Text(gene.name, color = GenomicsText, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = GenomicsSurface)
                }
            }
        }
    }
}
