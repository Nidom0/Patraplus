package ir.patraplus.webui

import android.webkit.WebView
import java.lang.ref.WeakReference

object WebViewHolder {
    private var webViewRef: WeakReference<WebView>? = null

    fun setWebView(webView: WebView) {
        webViewRef = WeakReference(webView)
    }

    fun clear() {
        webViewRef?.clear()
        webViewRef = null
    }

    fun getWebView(): WebView? = webViewRef?.get()
}
