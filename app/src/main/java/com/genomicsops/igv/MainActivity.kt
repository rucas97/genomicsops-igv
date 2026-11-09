package com.genomicsops.igv

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
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
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            if (content != null) {
                val safeJsString = org.json.JSONObject.quote(content)
                webViewInstance?.evaluateJavascript("""
                    var blob = new Blob([$safeJsString], {type: 'text/plain'});
                    var url = URL.createObjectURL(blob);
                    igv.browser.loadTrack({ url: url, name: 'Loaded Track' });
                """.trimIndent(), null)
            }
        }
    }

    if (showSplash) SplashScreen { showSplash = false }
    else {
        Column(Modifier.fillMaxSize().background(GenomicsDark)) {
            Surface(color = GenomicsSurface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("IGV Browser", color = GenomicsCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Local Genome (Offline)", color = GenomicsMuted, fontSize = 12.sp)
                    }
                    Button(onClick = { filePicker.launch("*/*") }, colors = ButtonDefaults.buttonColors(containerColor = GenomicsDark)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Load", tint = GenomicsTeal)
                        Spacer(Modifier.width(8.dp))
                        Text("Load Track", color = GenomicsText)
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        
                        val assetLoader = WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                            .build()
                            
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                return assetLoader.shouldInterceptRequest(request?.url)
                            }
                        }
                        
                        addJavascriptInterface(object {
                            @JavascriptInterface 
                            fun openFilePicker() { 
                                (ctx as? android.app.Activity)?.runOnUiThread { filePicker.launch("*/*") } 
                            }
                        }, "AndroidBridge")
                        
                        loadUrl("https://appassets.androidplatform.net/assets/igv.html")
                        webViewInstance = this
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}
