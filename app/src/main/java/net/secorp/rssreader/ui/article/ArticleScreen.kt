package net.secorp.rssreader.ui.article

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import net.secorp.rssreader.data.db.entity.FeedItemEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    onBack: () -> Unit,
    viewModel: ArticleViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val item by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val bodyHex = remember(colors) { hex(colors.onSurface.toArgb()) }
    val bgHex = remember(colors) { hex(colors.background.toArgb()) }
    val linkHex = remember(colors) { hex(colors.primary.toArgb()) }
    val mutedHex = remember(colors) { hex(colors.onSurfaceVariant.toArgb()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = item?.title ?: "Article",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val current = item
                    if (current != null) {
                        TextButton(onClick = { viewModel.toggleRead() }) {
                            Text(if (current.isRead) "Mark unread" else "Mark read")
                        }
                    }
                    val link = current?.link
                    if (!link.isNullOrBlank()) {
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                        }) {
                            Text("Open")
                        }
                    }
                },
            )
        },
    ) { inner ->
        val html = item?.let { buildHtml(it, bgHex, bodyHex, linkHex, mutedHex) }
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = false
                    settings.loadsImagesAutomatically = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { webView ->
                if (html != null) {
                    webView.loadDataWithBaseURL(
                        item?.link,
                        html,
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            // WebViews retain process resources until destroy() is called.
            // Without this, popping back from ArticleScreen leaves the
            // WebView alive long enough to leak memory across many opens.
            onRelease = { webView ->
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            },
        )
    }
}

private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

private fun buildHtml(
    item: FeedItemEntity,
    bgHex: String,
    bodyHex: String,
    linkHex: String,
    mutedHex: String,
): String {
    val body = item.contentHtml ?: item.description ?: ""
    val author = item.author?.takeIf { it.isNotBlank() }
    return """
        <!DOCTYPE html>
        <html><head><meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <style>
          html, body { background: $bgHex; color: $bodyHex; }
          body { font-family: -apple-system, system-ui, sans-serif;
                 font-size: 17px; line-height: 1.6;
                 margin: 0; padding: 16px; }
          h1 { font-size: 1.4em; margin: 0 0 8px 0; }
          .meta { color: $mutedHex; font-size: 0.9em; margin-bottom: 16px; }
          a { color: $linkHex; }
          img, video, iframe { max-width: 100%; height: auto; }
          pre, code { background: rgba(127,127,127,0.12);
                      border-radius: 4px; padding: 2px 4px;
                      font-family: ui-monospace, monospace; font-size: 0.9em; }
          pre { padding: 12px; overflow-x: auto; }
          blockquote { border-left: 3px solid $mutedHex;
                       margin: 0; padding: 0 12px; color: $mutedHex; }
        </style></head><body>
        <h1>${escape(item.title)}</h1>
        <div class="meta">${author?.let { escape(it) + " · " } ?: ""}${item.pubDate ?: ""}</div>
        $body
        </body></html>
    """.trimIndent()
}

private fun escape(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
