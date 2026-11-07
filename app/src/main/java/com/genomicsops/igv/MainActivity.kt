package com.genomicsops.igv

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.genomicsops.igv.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GenomicsTheme { AppRoot() } }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun AppRoot() {
    var showSplash by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoadingTrack by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoadingTrack = true
            val js = "igv.browser.loadTrack({ url: '$uri', name: 'Loaded Track' })"
            webViewInstance?.evaluateJavascript(js, null)
            Toast.makeText(context, "Loading genomic track...", Toast.LENGTH_SHORT).show()
            isLoadingTrack = false
        }
    }

    if (showSplash) {
        SplashScreen { showSplash = false }
    } else {
        Column(Modifier.fillMaxSize().background(GenomicsDark)) {
            // NATIVE TOOLBAR
            Surface(color = GenomicsSurface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("IGV Browser", color = GenomicsCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Human Genome (hg38)", color = GenomicsMuted, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { filePicker.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = GenomicsDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Load Track", tint = GenomicsTeal)
                        Spacer(Modifier.width(8.dp))
                        Text("Load .bed", color = GenomicsText)
                    }
                }
            }

            // WEBVIEW
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = true
                        
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun openFilePicker() {
                                (ctx as? android.app.Activity)?.runOnUiThread {
                                    filePicker.launch("*/*")
                                }
                            }
                        }, "AndroidBridge")
                        
                        loadUrl("file:///android_asset/igv.html")
                        webViewInstance = this
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}
