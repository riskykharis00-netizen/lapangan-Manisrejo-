package com.example

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    companion object {
        private const val LOCAL_WEB_HOST = "manisrejo.web.local"
        private const val ENTRY_URL = "https://$LOCAL_WEB_HOST/index.html"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        webView = WebView(this).apply {
            setBackgroundColor(0xFFF3F7F4.toInt())
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportMultipleWindows(false)
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            addJavascriptInterface(ShareBridge(), "AndroidShareBridge")

            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    if (uri.host != LOCAL_WEB_HOST) return null
                    val rawPath = (uri.path ?: "/index.html").trimStart('/')
                    val assetPath = if (rawPath.isEmpty()) "www/index.html" else "www/$rawPath"
                    return try {
                        val stream = assets.open(assetPath)
                        WebResourceResponse(
                            mimeTypeFor(assetPath),
                            "UTF-8",
                            200,
                            "OK",
                            mapOf("Cache-Control" to "no-cache"),
                            stream
                        )
                    } catch (_: Exception) {
                        WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            404,
                            "Not Found",
                            emptyMap(),
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.host == LOCAL_WEB_HOST) {
                        return false
                    }
                    openExternalUri(uri)
                    return true
                }
            }

            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                if (url.startsWith("blob:")) {
                    val fileName = guessFileName(contentDisposition, mimeType)
                    val safeUrl = url.replace("'", "\\'")
                    val safeName = fileName.replace("'", "\\'")
                    evaluateJavascript(
                        """
                        (function() {
                          fetch('$safeUrl')
                            .then(function(r) { return r.text(); })
                            .then(function(txt) { window.AndroidShareBridge.shareFile(txt, '$safeName'); })
                            .catch(function() {});
                        })();
                        """.trimIndent(),
                        null
                    )
                } else {
                    openExternalUri(Uri.parse(url))
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(ENTRY_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    private fun openExternalUri(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
        }
    }

    private fun mimeTypeFor(path: String): String = when {
        path.endsWith(".html", ignoreCase = true) -> "text/html"
        path.endsWith(".css", ignoreCase = true) -> "text/css"
        path.endsWith(".js", ignoreCase = true) -> "application/javascript"
        path.endsWith(".json", ignoreCase = true) -> "application/json"
        path.endsWith(".csv", ignoreCase = true) -> "text/csv"
        path.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
        path.endsWith(".png", ignoreCase = true) -> "image/png"
        path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        else -> "application/octet-stream"
    }

    private fun guessFileName(contentDisposition: String?, mimeType: String?): String {
        if (!contentDisposition.isNullOrBlank()) {
            val match = Regex("filename=\"?([^\";]+)\"?").find(contentDisposition)
            if (match != null) return match.groupValues[1]
        }
        return if (mimeType?.contains("json") == true) {
            "manisrejo-badminton-backup.json"
        } else {
            "database-orang-pesan-manisrejo.csv"
        }
    }

    inner class ShareBridge {
        @JavascriptInterface
        fun shareFile(content: String, fileName: String) {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, fileName)
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                startActivity(Intent.createChooser(intent, "Simpan / Bagikan $fileName"))
            }
        }
    }
}
