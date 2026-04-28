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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun HtmlCard(
    html: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 60.dp,
    maxHeight: Dp = Dp.Unspecified,
    allowInternalScroll: Boolean = false,
    onHeightMeasured: (() -> Unit)? = null,
) {
    var heightState by remember { mutableIntStateOf(0) }

    // Resolve Material theme colors that need to be available inside the WebView.
    // Convert ARGB ints into CSS hex (#RRGGBB) so they can be injected as CSS variables.
    val errorColorCss = MaterialTheme.colorScheme.error.toCssHex()

    val animatedHeight by animateDpAsState(
        targetValue = if (maxHeight != Dp.Unspecified) {
            heightState.dp.coerceIn(minHeight, maxHeight)
        } else {
            heightState.dp.coerceAtLeast(minHeight)
        },
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
                .let {
                    if (maxHeight != Dp.Unspecified) {
                        it.heightIn(min = minHeight, max = maxHeight)
                    } else {
                        it.heightIn(min = minHeight)
                    }
                }
                .height(animatedHeight),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    val webView = if (allowInternalScroll) {
                        WebView(context)
                    } else {
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
                                // Prevent all internal scrolling in inline cards.
                            }
                        }
                    }

                    webView.apply {
                        isVerticalScrollBarEnabled = allowInternalScroll
                        isHorizontalScrollBarEnabled = false
                        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                        isNestedScrollingEnabled = false
                        overScrollMode = if (allowInternalScroll) {
                            View.OVER_SCROLL_IF_CONTENT_SCROLLS
                        } else {
                            View.OVER_SCROLL_NEVER
                        }
                        isScrollContainer = allowInternalScroll

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
                            wrapHtmlWithStyles(
                                context = context,
                                html = html,
                                cssScheme = if (night) "dark" else "light",
                                allowPageScroll = allowInternalScroll,
                                errorColorCss = errorColorCss,
                            )
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
                modifier = Modifier.fillMaxSize(),
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

private fun Color.toCssHex(): String {
    val argb = this.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "#%02X%02X%02X".format(r, g, b)
}

private fun wrapHtmlWithStyles(
    context: Context,
    html: String,
    cssScheme: String,
    allowPageScroll: Boolean = false,
    errorColorCss: String,
): String {
    val codehiliteStyles =
        context.assets.open("HtmlCardCodehiliteStyles.css").bufferedReader().use { it.readText() }
    val mainStyles =
        context.assets.open("HtmlCardStyles.css").bufferedReader().use { it.readText() }
    val scrollingStyles = if (allowPageScroll) {
        """
        html, body {
            overflow-x: hidden;
            overflow-y: auto;
            touch-action: pan-y;
            -webkit-overflow-scrolling: touch;
        }
        """.trimIndent()
    } else {
        ""
    }

    return """
        <!DOCTYPE html>
        <html class="$cssScheme">
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { margin: 0; padding: 0; background-color: transparent; }
                :root { --error-color: $errorColorCss; }
                $mainStyles
                $codehiliteStyles
                $scrollingStyles
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
