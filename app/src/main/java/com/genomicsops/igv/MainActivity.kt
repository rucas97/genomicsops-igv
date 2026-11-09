package com.genomicsops.igv

import android.net.Uri
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genomicsops.igv.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TrackFeature(val chrom: String, val start: Int, val end: Int, val name: String, val strand: String)

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
        var features by remember { mutableStateOf<List<TrackFeature>>(emptyList()) }
        var fileName by rememberSaveable { mutableStateOf("No track loaded") }
        var zoomLevel by rememberSaveable { mutableStateOf(1f) }
        val scrollState = rememberScrollState()

        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val parsed = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
                                ?.mapNotNull { line ->
                                    val trimmed = line.trim()
                                    if (trimmed.startsWith("track") || trimmed.startsWith("#") || trimmed.isEmpty()) null
                                    else {
                                        val parts = trimmed.split(Regex("\\s+"))
                                        if (parts.size >= 3) {
                                            val name = if (parts.size >= 4) parts[3] else "Feature_${parts[1]}"
                                            val strand = if (parts.size >= 6) parts[5] else "+"
                                            TrackFeature(parts[0], parts[1].toIntOrNull() ?: 0, parts[2].toIntOrNull() ?: 0, name, strand)
                                        } else null
                                    }
                                }
                        } catch (e: Exception) { emptyList() }
                    }
                    features = parsed ?: emptyList()
                    fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Loaded Track"
                    if (features.isEmpty()) Toast.makeText(context, "No valid BED features found.", Toast.LENGTH_LONG).show()
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { zoomLevel = (zoomLevel - 0.2f).coerceAtLeast(0.5f) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = GenomicsCyan)
                        }
                        IconButton(onClick = { zoomLevel = (zoomLevel + 0.2f).coerceAtMost(3f) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = GenomicsCyan)
                        }
                        Button(onClick = { filePicker.launch("*/*") }, colors = ButtonDefaults.buttonColors(containerColor = GenomicsDark), shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Load", tint = GenomicsTeal)
                            Spacer(Modifier.width(8.dp))
                            Text("Load", color = GenomicsText)
                        }
                    }
                }
            }

            if (features.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Load a .bed file to view genomic coordinates", color = GenomicsMuted)
                }
            } else {
                // Calculate genome span
                val minStart = features.minOfOrNull { it.start }?.toFloat() ?: 0f
                val maxEnd = features.maxOfOrNull { it.end }?.toFloat() ?: 1f
                val genomicSpan = maxEnd - minStart

                // Base width per pixel (scaled by zoom)
                val canvasWidth = (genomicSpan / 100f * zoomLevel).dp.coerceAtLeast(1000.dp)

                Column(Modifier.fillMaxSize().horizontalScroll(scrollState)) {
                    Surface(
                        color = Color(0xFF12171C),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.width(canvasWidth).height(300.dp).padding(16.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val startX = 40f
                            val endX = width - 20f
                            val trackWidth = endX - startX

                            // 1. Draw Genomic Ruler (Top)
                            val rulerY = 30f
                            drawLine(color = GenomicsMuted, start = Offset(startX, rulerY), end = Offset(endX, rulerY), strokeWidth = 2f)
                            
                            // Draw Ticks (every 10% of the span)
                            for (i in 0..10) {
                                val x = startX + (trackWidth / 10f) * i
                                drawLine(color = GenomicsMuted, start = Offset(x, rulerY - 10f), end = Offset(x, rulerY), strokeWidth = 2f)
                                
                                // Calculate the actual genomic coordinate for the label
                                val coord = (minStart + (genomicSpan / 10f) * i).toLong()
                                drawContext.canvas.nativeCanvas.drawText(
                                    coord.toString(),
                                    x - 15f, // Center text roughly
                                    rulerY - 15f,
                                    android.graphics.Paint().apply {
                                        color = android.graphics.Color.LTGRAY
                                        textSize = 24f
                                        typeface = android.graphics.Typeface.MONOSPACE
                                    }
                                )
                            }
                            // 2. Draw Track Lane Background
                            val trackY = 80f
                            val trackHeight = 100f
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.3f),
                                topLeft = Offset(startX, trackY),
                                size = Size(trackWidth, trackHeight),
                                cornerRadius = CornerRadius(8f, 8f)
                            )

                            // 3. Draw Features
                            val colors = listOf(Color(0xFF00E5FF), Color(0xFF00FFAA), Color(0xFFFF0055), Color(0xFFFFAA00))
                            features.forEachIndexed { index, feature ->
                                val x1 = startX + ((feature.start - minStart) / genomicSpan) * trackWidth
                                val x2 = startX + ((feature.end - minStart) / genomicSpan) * trackWidth
                                val boxWidth = (x2 - x1).coerceAtLeast(4f) // Ensure tiny features are still visible
                                
                                // Draw the feature box
                                drawRoundRect(
                                    color = colors[index % colors.size],
                                    topLeft = Offset(x1, trackY + 20f),
                                    size = Size(boxWidth, trackHeight - 40f),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )

                                // Draw a subtle arrow for strand direction (if room)
                                if (boxWidth > 30f) {
                                    val arrowPath = Path()
                                    val arrowX = if (feature.strand == "+") x2 - 15f else x1 + 15f
                                    val dir = if (feature.strand == "+") 1 else -1
                                    
                                    arrowPath.moveTo(arrowX, trackY + 50f)
                                    arrowPath.lineTo(arrowX - (10f * dir), trackY + 40f)
                                    arrowPath.lineTo(arrowX - (10f * dir), trackY + 60f)
                                    arrowPath.close()
                                    
                                    drawPath(arrowPath, color = Color.Black.copy(alpha = 0.5f))
                                }
                                
                                // Draw Feature Name inside the box if wide enough
                                if (boxWidth > 100f) {
                                    drawContext.canvas.nativeCanvas.drawText(
                                        feature.name,
                                        x1 + 10f,
                                        trackY + 75f,
                                        android.graphics.Paint().apply {
                                            color = android.graphics.Color.BLACK
                                            textSize = 28f
                                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // List View underneath
                    Column(Modifier.padding(16.dp)) {
                        features.forEach { feature ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(GenomicsTeal, RoundedCornerShape(50)))
                                Spacer(Modifier.width(12.dp))
                                Text(feature.chrom, color = GenomicsCyan, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                                Text("${feature.start}..${feature.end}", color = GenomicsMuted, fontFamily = FontFamily.Monospace, modifier = Modifier.width(140.dp))
                                Text(feature.name, color = GenomicsText, fontWeight = FontWeight.Medium)
                            }
                            HorizontalDivider(color = GenomicsSurface)
                        }
                    }
                }
            }
        }
    }
}
