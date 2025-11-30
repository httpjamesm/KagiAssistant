package space.httpjames.kagiassistantmaterial.ui.chat

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val MIN_WEBVIEW_HEIGHT = 0

@Composable
fun HtmlCard(
    html: String,
    modifier: Modifier = Modifier,
    minHeight: Int = MIN_WEBVIEW_HEIGHT,
    onHeightMeasured: (() -> Unit)? = null,
) {
    var heightState by remember { mutableIntStateOf(minHeight) }

    val context = LocalContext.current

    val animatedHeight = animateDpAsState(
        targetValue = heightState.dp,
        animationSpec = tween(300)
    ).value

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = Color.Transparent
        ),
        shape = RoundedCornerShape(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .height(animatedHeight),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

                        addJavascriptInterface(
                            HtmlViewerJavaScriptInterface(
                                expectedMin = minHeight,
                                onHeightMeasured = { h ->
                                    heightState = h
                                    onHeightMeasured?.invoke()
                                }

                            ),
                            "HtmlViewer"
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            defaultTextEncodingName = "utf-8"
                            blockNetworkImage = false
                        }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)

                        val night = (context.resources.configuration.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                        val cssScheme = if (night) "dark" else "light"

                        val styledHtml = wrapHtmlWithStyles(context, html, cssScheme)
                        loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
                        tag = html
                    }
                },
                update = { webView ->
                    val lastHtml = webView.tag as? String
                    if (lastHtml != html) {
                        val night = (context.resources.configuration.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                        val cssScheme = if (night) "dark" else "light"
                        val styledHtml = wrapHtmlWithStyles(context, html, cssScheme)
                        webView.loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
                        webView.tag = html  // Update tracked content
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


private class HtmlViewerJavaScriptInterface(
    private val expectedMin: Int,
    private val onHeightMeasured: (Int) -> Unit
) {
    private var lastHeight = 0

    @android.webkit.JavascriptInterface
    fun resize(height: Int) {
        // Only call if height actually changed
        if (height != lastHeight) {
            lastHeight = height
            val safeHeight = height.coerceAtLeast(expectedMin)
            onHeightMeasured(safeHeight)
        }
    }
}

private fun wrapHtmlWithStyles(context: Context, html: String, cssScheme: String): String {
    val codehiliteStyles =
        context.assets.open("HtmlCardCodehiliteStyles.css").bufferedReader().use { it.readText() }
    val mainStyles =
        context.assets.open("HtmlCardStyles.css").bufferedReader().use { it.readText() }


    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <script>
                const light = '$cssScheme' === 'light';
                document.documentElement.classList.toggle('light', light);
                document.documentElement.classList.toggle('dark', !light);
            </script>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
        </head>
        <style>
        $mainStyles
        $codehiliteStyles
        </style>
        <body>
            $html
            <script>
                let lastHeight = 0;

                const updateHeight = () => {
                    const newHeight = Math.ceil(document.body.scrollHeight);
                    // Only notify if height changed significantly (avoid micro-updates)
                    if (Math.abs(newHeight - lastHeight) > 50) {
                        lastHeight = newHeight;
                        window.HtmlViewer.resize(newHeight);
                    }
                };

                // Only measure on load, NOT on mutations
                window.addEventListener('load', updateHeight);

                // Longer debounce for any other changes
                let debounceTimeout;
                const debouncedUpdateHeight = () => {
                    clearTimeout(debounceTimeout);
                    debounceTimeout = setTimeout(updateHeight, 500);
                };

                // Minimal observer - only watch image loads, not all mutations
                const imageObserver = new MutationObserver((mutations) => {
                    const hasImages = mutations.some(m => {
                        return m.addedNodes.length > 0 && 
                               Array.from(m.addedNodes).some(n => n.tagName === 'IMG');
                    });
                    if (hasImages) {
                        debouncedUpdateHeight();
                    }
                });

                imageObserver.observe(document.body, {
                    childList: true,
                    subtree: true,
                });

                // Initial update after a small delay
                setTimeout(updateHeight, 100);
            </script>
        </body>
        </html>
    """.trimIndent()
}
