package space.httpjames.kagiassistantmaterial.ui.chat

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun HtmlCard(
    html: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 60.dp,
    onHeightMeasured: (() -> Unit)? = null,
) {
    var heightState by remember { mutableIntStateOf(0) }

    val animatedHeight by animateDpAsState(
        targetValue = heightState.dp.coerceAtLeast(minHeight),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "height"
    )

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
                .heightIn(min = minHeight)
                .height(animatedHeight),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    object : WebView(context) {
                        override fun overScrollBy(
                            deltaX: Int, deltaY: Int,
                            scrollX: Int, scrollY: Int,
                            scrollRangeX: Int, scrollRangeY: Int,
                            maxOverScrollX: Int, maxOverScrollY: Int,
                            isTouchEvent: Boolean
                        ): Boolean {
                            return false
                        }

                        override fun scrollTo(x: Int, y: Int) {
                            // Prevent all internal scrolling
                        }

                    }.apply {
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                        isNestedScrollingEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isScrollContainer = false

                        addJavascriptInterface(
                            HtmlViewerJavaScriptInterface(
                                expectedMin = 0,
                                onHeightMeasured = { h ->
                                    if (h > 50) {
                                        heightState = h
                                        onHeightMeasured?.invoke()
                                    }
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

                        val night =
                            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                        val styledHtml =
                            wrapHtmlWithStyles(context, html, if (night) "dark" else "light")
                        tag = html
                        loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
                    }
                },
                update = { webView ->
                    val lastHtml = webView.tag as? String
                    if (lastHtml != html) {
                        webView.tag = html

                        // STREAMING FIX: Instead of loadDataWithBaseURL, we inject via JS
                        val escapedHtml = html
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\n", "\\n")
                            .replace("\r", "")

                        webView.evaluateJavascript("updateContent('$escapedHtml');", null)
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
        if (height != lastHeight) {
            lastHeight = height
            onHeightMeasured(height.coerceAtLeast(expectedMin))
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
        <html class="$cssScheme">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { margin: 0; padding: 0; background-color: transparent; }
                $mainStyles
                $codehiliteStyles
            </style>
        </head>
        <body>
            <div id="content-container">$html</div>
            <script>
                const container = document.getElementById('content-container');

                // This function updates the text without refreshing the page
                window.updateContent = (newHtml) => {
                    container.innerHTML = newHtml;
                };

                // ResizeObserver is much more performant for streaming content
                const observer = new ResizeObserver(() => {
                    const height = Math.ceil(document.documentElement.scrollHeight);
                    window.HtmlViewer.resize(height);
                });
                observer.observe(document.body);
            </script>
        </body>
        </html>
    """.trimIndent()
}
