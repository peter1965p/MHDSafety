package com.paeffgen.mhdsafety.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.paeffgen.mhdsafety.domain.VideoLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Eingebetteter YouTube-Player (youtube-nocookie), Vollbild über den Player-Knopf. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoPlayer(videoId: String, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? ComponentActivity
    var fullscreen: View? = null
    AndroidView(
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        val root = activity?.window?.decorView as? FrameLayout ?: return
                        fullscreen = view
                        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }

                    override fun onHideCustomView() {
                        val root = activity?.window?.decorView as? FrameLayout
                        fullscreen?.let { root?.removeView(it) }
                        fullscreen = null
                    }
                }
                setBackgroundColor(android.graphics.Color.BLACK)
                // Player direkt laden, mit App-Referer: YouTube verlangt eine Herkunft (sonst Fehler 153 / schwarz).
                // Kein clip() am WebView: abgerundetes Zuschneiden lässt die Video-Fläche auf vielen Geräten schwarz.
                loadUrl(VideoLink.embedUrl(videoId), mapOf("Referer" to "https://${ctx.packageName}"))
            }
        },
    )
    DisposableEffect(videoId) { onDispose { } }
}

/** Vorschaubild aus dem Netz, ohne Bildbibliothek; zeigt ▶ bei Videos. */
@Composable
fun RecipeThumb(url: String?, isVideo: Boolean, size: Dp = 64.dp) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = url?.let {
            withContext(Dispatchers.IO) {
                runCatching {
                    val c = URL(it).openConnection() as HttpURLConnection
                    c.connectTimeout = 8_000
                    c.readTimeout = 10_000
                    c.inputStream.use(BitmapFactory::decodeStream).also { c.disconnect() }
                }.getOrNull()
            }
        }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (isVideo) Text("▶", style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
    }
}
