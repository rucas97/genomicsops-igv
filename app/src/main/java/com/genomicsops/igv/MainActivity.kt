package com.genomicsops.igv

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            // Pass the local file URI to IGV.js
            val js = "igv.browser.loadTrack({ url: '$uri', name: 'Loaded Track' })"
            webViewInstance?.evaluateJavascript(js, null)
            Toast.makeText(webViewInstance?.context, "Loading track...", Toast.LENGTH_SHORT).show()
        }
    }

    if (showSplash) {
        SplashScreen { showSplash = false }
    } else {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    // 1. Hardware acceleration fixes the black screen
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    
                    // 2. Allow file access
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    
                    webViewClient = WebViewClient()
                    
                    // 3. Add Bridge so JS can trigger the Android file picker
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun openFilePicker() {
                            (ctx as? android.app.Activity)?.runOnUiThread {
                                filePicker.launch("*/*")
                            }
                        }
                    }, "AndroidBridge")
                    
                    // Load the HTML file
                    loadUrl("file:///android_asset/igv.html")
                    webViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
