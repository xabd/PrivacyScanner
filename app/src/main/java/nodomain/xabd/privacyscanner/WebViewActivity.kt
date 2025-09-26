package nodomain.xabd.privacyscanner

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import nodomain.xabd.privacyscanner.R


class WebViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val webView = findViewById<WebView>(R.id.webView)

        // Enable JS + DOM storage for modern web apps
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true

        // Stay inside the app instead of opening browser
        webView.webViewClient = WebViewClient()

        // Load your site
        webView.loadUrl("https://digital-escape-tools.vercel.app")
    }
}

