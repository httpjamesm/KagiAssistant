package space.httpjames.kagiassistantmaterial.ui.chat

import android.content.res.Configuration
import android.view.View
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
    key: String,
    modifier: Modifier = Modifier,
    minHeight: Int = MIN_WEBVIEW_HEIGHT,
    onHeightMeasured: (() -> Unit)? = null,
) {
    var isLoading by remember { mutableStateOf(true) }
    var heightState by remember { mutableIntStateOf(minHeight) }

    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = Color.Transparent
        ),
        shape = RoundedCornerShape(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightState.dp),
            contentAlignment = Alignment.Center,
        ) {
//            if (isLoading.value) {
//                CircularProgressIndicator()
//            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

                        addJavascriptInterface(
                            HtmlViewerJavaScriptInterface(
                                expectedMin =  minHeight,
                                onHeightMeasured = { h ->
                                    heightState = h
                                    isLoading = false
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

                        val styledHtml = wrapHtmlWithStyles(html, cssScheme)
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
                        val styledHtml = wrapHtmlWithStyles(html, cssScheme)
                        webView.loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
                        webView.tag = html  // Update tracked content
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun wrapHtmlWithStyles(html: String, cssScheme: String): String {
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
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                
                :root {
                    --main-bg-color: #17181b;
                    --header-bg: #17181bcc;
                    --main-text-color: #E2E2E9;
                    --divider-color: #27292e;
                    --modal-bg-color: #27292e;
                    --modal-divider-color: #3a3d44;

                    --skeleton-1: #3a3d44;
                    --skeleton-2: #4b4e55;
                    --skeleton-3: #3a3d44;
                    --outbound-message-bg-color: #34363c;

                    --citation-bg-color: #2a2d35;
                    --citation-border-color: #3a3d44;
                    --citation-text-color: #e2e8f0;

                    --summary-text-color: #f1f5f9;

                    --context-menu-bg-color: #2a2d32;

                    --document-bg-color: rgb(255, 255, 255, 0.1);
                }

                html.light {
                    --main-bg-color: #f9f9f9;
                    --header-bg: #f9f9f9cc;
                    --main-text-color: black;
                    --divider-color: #e5e7eb;
                    --modal-bg-color: #e5e7eb;
                    --modal-divider-color: #d1d5db;

                    --skeleton-1: #e5e7eb;
                    --skeleton-2: #d1d5db;
                    --skeleton-3: #e5e7eb;

                    --outbound-message-bg-color: #e9eef6;

                    --citation-bg-color: #f3f4f6;
                    --citation-border-color: #d1d5db;
                    --citation-text-color: #1e293b;

                    --summary-text-color: #1e293b;

                    --context-menu-bg-color: #fdfeff;

                    --document-bg-color: #eaecee;
                    
                    /* Light‑theme greys */
                    --gray-200: #e5e7eb;
                    /* very light gray – used as the shimmer highlight in light mode */
                    --gray-900: #414141;
                    /* dark gray – fallback for dark mode if the theme selector fails */
                }

                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    font-size: 16px;
                    line-height: 1.6;
                    color: var(--main-text-color);
                    padding: 0;
                    padding-bottom: 1rem;
                    margin: 0;
                    background: transparent;
                }
                p {
                    margin-bottom: 12px;
                }
                h1, h2, h3, h4, h5, h6 {
                    margin: 16px 0 8px 0;
                    font-weight: 600;
                }
                h1 { font-size: 28px; }
                h2 { font-size: 24px; }
                h3 { font-size: 20px; }
                ul, ol {
                    margin: 12px 0 12px 20px;
                }
                li {
                    margin-bottom: 6px;
                }
                a {
                    color: #1976D2;
                    text-decoration: none;
                }
                a:active {
                    opacity: 0.7;
                }
                img {
                    max-width: 100%;
                    height: auto;
                    display: block;
                    margin: 12px 0;
                }
                strong, b {
                    font-weight: 600;
                }
                em, i {
                    font-style: italic;
                }
                blockquote {
                    margin: 12px 0;
                    padding-left: 16px;
                    border-left: 4px solid #E0E0E0;
                    color: #666;
                }
                code {
                    background: #F5F5F5;
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-family: "Courier New", monospace;
                    font-size: 14px;
                }
                pre {
                    padding: 12px;
                    border-radius: 4px;
                    overflow-x: auto;
                    margin: 12px 0;
                }
                pre code {
                    background: transparent;
                    padding: 0;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin: 12px 0;
                }
                th, td {
                    border: 1px solid #E0E0E0;
                    padding: 8px;
                    text-align: left;
                }
                th {
                    background: #F5F5F5;
                    font-weight: 600;
                }
                
                .inline-detail-container {
                    margin: 0.5rem 0;
                    width: 100%;
                    box-sizing: border-box;
                }

                .inline-detail-status {
                    display: flex;
                    align-items: center;
                    gap: 0.375rem;
                    padding: 0.375rem 0.75rem;
                    /* background: rgba(106, 17, 203, .5); */
                    /* border: 1px solid rgba(106, 17, 203, 0.3); */
                    border-radius: 0.375rem;
                    transition: all 0.3s ease;
                    font-size: 0.875rem;
                    line-height: 1.4;
                    box-sizing: border-box;
                    width: 100%;
                    min-width: 0;
                    cursor: pointer;
                    user-select: none;
                }

                .inline-detail-preview {
                    margin-top: 0.5rem;
                    padding: 0.75rem;
                    /* background: rgba(106, 17, 203, 0.05);
                    border: 1px solid rgba(106, 17, 203, 0.2); */
                    border-radius: 0.375rem;
                    font-size: 0.875rem;
                    line-height: 1.5;
                    position: relative;
                    max-height: 4.5rem;
                    /* approximately 3 lines */
                    overflow: hidden;
                    color: rgb(255, 255, 255, 0.6);
                }

                .inline-detail-preview::after {
                    content: "";
                    position: absolute;
                    top: 0;
                    left: 0;
                    right: 0;
                    height: 1.5rem;
                    background: linear-gradient(rgba(23, 24, 27, 1), transparent);
                    pointer-events: none;
                }

                .inline-detail-content {
                    display: none;
                    margin-top: 0.5rem;
                    padding: 0.75rem;
                    border-radius: 0.375rem;
                    font-size: 0.875rem;
                    box-sizing: border-box;
                    line-height: 1.5;
                }

                .inline-detail-content.expanded {
                    display: block;
                }

                .inline-detail-preview.hidden {
                    display: none;
                }

                .inline-detail-icon {
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    width: 1.25rem;
                    height: 1.25rem;
                    color: #2575fc;
                    flex-shrink: 0;
                    animation: icon-bounce 1.5s ease-in-out infinite;
                }

                .inline-detail-text {
                    color: #f1f5f9;
                    font-weight: 600;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    flex: 1;
                    min-width: 0;
                }

                @keyframes detail-pulse {
                    0%,
                    100% {
                        box-shadow: 0 0 0 0 rgba(106, 17, 203, 0.4);
                    }

                    50% {
                        box-shadow: 0 0 0 4px rgba(106, 17, 203, 0.1);
                    }
                }

                @keyframes icon-bounce {
                    0%,
                    100% {
                        transform: scale(1);
                    }

                    50% {
                        transform: scale(1.1);
                    }
                }

                sup {
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    width: 1.25rem;
                    height: 1.25rem;
                    background: rgba(197, 197, 197, 0.8);
                    color: #ffffff;
                    border-radius: 50%;
                    font-size: 0.75rem;
                    font-weight: 600;
                    line-height: 1;
                    margin-left: 0.25rem;
                    vertical-align: super;
                    text-decoration: none;
                    transition: all 0.2s ease;
                    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
                }

                sup a {
                    color: black;
                    text-decoration: none;
                }

                /* remove default details/summary styling */
                details {
                    margin: 0.5rem 0;
                    border: none;
                    background: none;
                    padding: 0;
                    overflow: hidden;
                    transition: all 0.3s ease;
                }

                summary {
                    list-style: none;
                    cursor: pointer;
                    padding: 0.375rem 0.75rem;
                    border-radius: 0.375rem;
                    font-size: 0.875rem;
                    font-weight: 600;
                    color: var(--summary-text-color);
                    display: flex;
                    align-items: center;
                    gap: 0.375rem;
                    transition: all 0.3s ease;
                    user-select: none;
                    -webkit-user-select: none;
                }

                summary::-webkit-details-marker {
                    display: none;
                }

                summary::marker {
                    display: none;
                }

                /* --------------------------------------------------------------
                   Shimmer / “glistening” text for <summary class="in‑progress">
                   -------------------------------------------------------------- */
                summary.in-progress {
                    /* Gradient that follows the element’s current colour */
                    background: var(--gray-200)
                        linear-gradient(
                            to left,
                            var(--gray-200),
                            var(--gray-900) 50%,
                            var(--gray-200)
                        );

                    /* Clip the gradient to the text */
                    -webkit-background-clip: text;
                    background-clip: text;
                    -webkit-text-fill-color: transparent;
                    color: transparent;
                    /* fallback for non‑WebKit browsers */

                    /* Gradient size & start position */
                    background-size: 50% 200%;
                    background-position: -100% top;
                    background-repeat: no-repeat;

                    /* Animation */
                    animation-name: shimmer;
                    animation-duration: 1.25s;
                    /* same as your original glisten duration */
                    animation-timing-function: linear;
                    animation-iteration-count: infinite;
                    /* keeps the “reverse” you had */
                    animation-delay: 0.6s;
                    /* from the reference snippet */
                }

                html.light {
                    summary.in-progress {
                        background: var(--gray-900)
                            linear-gradient(
                                to left,
                                var(--gray-900),
                                var(--gray-200) 50%,
                                var(--gray-900)
                            );
                        /* Clip the gradient to the text */
                        -webkit-background-clip: text;
                        background-clip: text;
                        -webkit-text-fill-color: transparent;
                        color: transparent;
                        /* fallback for non‑WebKit browsers */

                        /* Gradient size & start position */
                        background-size: 50% 200%;
                        background-position: -100% top;
                        background-repeat: no-repeat;

                        /* Animation */
                        animation-name: shimmer;
                        animation-duration: 1.25s;
                        /* same as your original glisten duration */
                        animation-timing-function: linear;
                        animation-iteration-count: infinite;
                        /* keeps the “reverse” you had */
                        animation-delay: 0.6s;
                        /* from the reference snippet */
                    }
                }

                /* --------------------------------------------------------------
                   Keyframes – identical to the original `xU321`
                   -------------------------------------------------------------- */
                @keyframes shimmer {
                    0% {
                        background-position: -100% top;
                    }

                    70% {
                        background-position: 250% top;
                    }

                    100% {
                        background-position: 250% top;
                    }
                }

                @media (prefers-reduced-motion: reduce) {
                    summary.in-progress {
                        animation: none;
                        background-size: 100% 100%;
                    }
                }

                /* add custom check icon */
                summary::before {
                    content: "";
                    width: 12px;
                    height: 12px;
                    background-image: url("data:image/svg+xml,%3Csvg width='12' height='12' viewBox='0 0 12 12' fill='none' xmlns='http://www.w3.org/2000/svg'%3E%3Ccircle cx='6' cy='6' r='5' stroke='%232575fc' stroke-width='1.5'/%3E%3Cpath d='M4 6l1.5 1.5L8 4' stroke='%232575fc' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
                    background-repeat: no-repeat;
                    background-position: center;
                    margin-right: 0.375rem;
                    flex-shrink: 0;
                }

                /* style the content inside details */
                details > *:not(summary) {
                    margin-top: 0.5rem;
                    padding-left: 0.75rem;
                    border-radius: 0.375rem;
                    font-size: 0.8rem;
                    line-height: 1.5;
                    opacity: 0;
                    transform: translateY(-10px);
                    transition:
                        opacity 0.3s ease,
                        transform 0.3s ease;
                }

                /* animate content when details is open */
                details[open] > *:not(summary) {
                    opacity: 1;
                    transform: translateY(0);
                }

                /* remove margins from p elements inside details and apply muted color */
                details[open] > blockquote {
                    margin: 0;
                    color: var(--main-text-color);
                    opacity: 0.6;
                    border-left: 4px solid var(--divider-color);
                }

                details ul,
                details ol {
                    padding-left: 1rem;
                }

                a {
                    color: #2575fc;
                    text-decoration: none;
                }

                img.inbound-image {
                    max-width: 100%;
                    max-height: 20rem;
                }


.codehilite .hll {
    background-color: #49483e;
}

.codehilite {
    background: #0e0e0e;
    color: #f8f8f2;
    padding: 1rem;
    box-sizing: border-box;
    border-radius: 0.5rem;
    width: 100%;
    user-select: text;
    -webkit-user-select: text;
}

.codehilite pre code {
    white-space: pre-wrap;
    word-break: break-all;
}

.codehilite .filename {
    font-size: 0.75rem;
}

.codehilite .c {
    color: #75715e;
}

/* Comment */
.codehilite .err {
    color: #960050;
    background-color: #1e0010;
}

/* Error */
.codehilite .k {
    color: #66d9ef;
}

/* Keyword */
.codehilite .l {
    color: #ae81ff;
}

/* Literal */
.codehilite .n {
    color: #f8f8f2;
}

/* Name */
.codehilite .o {
    color: #f92672;
}

/* Operator */
.codehilite .p {
    color: #f8f8f2;
}

/* Punctuation */
.codehilite .ch {
    color: #75715e;
}

/* Comment.Hashbang */
.codehilite .cm {
    color: #75715e;
}

/* Comment.Multiline */
.codehilite .cp {
    color: #75715e;
}

/* Comment.Preproc */
.codehilite .cpf {
    color: #75715e;
}

/* Comment.PreprocFile */
.codehilite .c1 {
    color: #75715e;
}

/* Comment.Single */
.codehilite .cs {
    color: #75715e;
}

/* Comment.Special */
.codehilite .gd {
    color: #f92672;
}

/* Generic.Deleted */
.codehilite .ge {
    font-style: italic;
}

/* Generic.Emph */
.codehilite .gi {
    color: #a6e22e;
}

/* Generic.Inserted */
.codehilite .gs {
    font-weight: bold;
}

/* Generic.Strong */
.codehilite .gu {
    color: #75715e;
}

/* Generic.Subheading */
.codehilite .kc {
    color: #66d9ef;
}

/* Keyword.Constant */
.codehilite .kd {
    color: #66d9ef;
}

/* Keyword.Declaration */
.codehilite .kn {
    color: #f92672;
}

/* Keyword.Namespace */
.codehilite .kp {
    color: #66d9ef;
}

/* Keyword.Pseudo */
.codehilite .kr {
    color: #66d9ef;
}

/* Keyword.Reserved */
.codehilite .kt {
    color: #66d9ef;
}

/* Keyword.Type */
.codehilite .ld {
    color: #e6db74;
}

/* Literal.Date */
.codehilite .m {
    color: #ae81ff;
}

/* Literal.Number */
.codehilite .s {
    color: #e6db74;
}

/* Literal.String */
.codehilite .na {
    color: #a6e22e;
}

/* Name.Attribute */
.codehilite .nb {
    color: #f8f8f2;
}

/* Name.Builtin */
.codehilite .nc {
    color: #a6e22e;
}

/* Name.Class */
.codehilite .no {
    color: #66d9ef;
}

/* Name.Constant */
.codehilite .nd {
    color: #a6e22e;
}

/* Name.Decorator */
.codehilite .ni {
    color: #f8f8f2;
}

/* Name.Entity */
.codehilite .ne {
    color: #a6e22e;
}

/* Name.Exception */
.codehilite .nf {
    color: #a6e22e;
}

/* Name.Function */
.codehilite .nl {
    color: #f8f8f2;
}

/* Name.Label */
.codehilite .nn {
    color: #f8f8f2;
}

/* Name.Namespace */
.codehilite .nx {
    color: #a6e22e;
}

/* Name.Other */
.codehilite .py {
    color: #f8f8f2;
}

/* Name.Property */
.codehilite .nt {
    color: #f92672;
}

/* Name.Tag */
.codehilite .nv {
    color: #f8f8f2;
}

/* Name.Variable */
.codehilite .ow {
    color: #f92672;
}

/* Operator.Word */
.codehilite .w {
    color: #f8f8f2;
}

/* Text.Whitespace */
.codehilite .mb {
    color: #ae81ff;
}

/* Literal.Number.Bin */
.codehilite .mf {
    color: #ae81ff;
}

/* Literal.Number.Float */
.codehilite .mh {
    color: #ae81ff;
}

/* Literal.Number.Hex */
.codehilite .mi {
    color: #ae81ff;
}

/* Literal.Number.Integer */
.codehilite .mo {
    color: #ae81ff;
}

/* Literal.Number.Oct */
.codehilite .sa {
    color: #e6db74;
}

/* Literal.String.Affix */
.codehilite .sb {
    color: #e6db74;
}

/* Literal.String.Backtick */
.codehilite .sc {
    color: #e6db74;
}

/* Literal.String.Char */
.codehilite .dl {
    color: #e6db74;
}

/* Literal.String.Delimiter */
.codehilite .sd {
    color: #e6db74;
}

/* Literal.String.Doc */
.codehilite .s2 {
    color: #e6db74;
}

/* Literal.String.Double */
.codehilite .se {
    color: #ae81ff;
}

/* Literal.String.Escape */
.codehilite .sh {
    color: #e6db74;
}

/* Literal.String.Heredoc */
.codehilite .si {
    color: #e6db74;
}

/* Literal.String.Interpol */
.codehilite .sx {
    color: #e6db74;
}

/* Literal.String.Other */
.codehilite .sr {
    color: #e6db74;
}

/* Literal.String.Regex */
.codehilite .s1 {
    color: #e6db74;
}

/* Literal.String.Single */
.codehilite .ss {
    color: #e6db74;
}

/* Literal.String.Symbol */
.codehilite .bp {
    color: #f8f8f2;
}

/* Name.Builtin.Pseudo */
.codehilite .fm {
    color: #a6e22e;
}

/* Name.Function.Magic */
.codehilite .vc {
    color: #f8f8f2;
}

/* Name.Variable.Class */
.codehilite .vg {
    color: #f8f8f2;
}

/* Name.Variable.Global */
.codehilite .vi {
    color: #f8f8f2;
}

/* Name.Variable.Instance */
.codehilite .vm {
    color: #f8f8f2;
}

/* Name.Variable.Magic */
.codehilite .il {
    color: #ae81ff;
}

/* Literal.Number.Integer.Long */
.scrollable-table {
    overflow-x: auto;
    /* Enables horizontal scroll if needed */
    -webkit-overflow-scrolling: touch;
    /* Smooth scrolling on mobile */
    width: 100%;
    margin-bottom: 1rem;
    scrollbar-width: thin;
    scrollbar-color: var(--divider-color) transparent;
}

.scrollable-table::-webkit-scrollbar {
    height: 8px;
}

.scrollable-table::-webkit-scrollbar-track {
    background: transparent;
}

.scrollable-table::-webkit-scrollbar-thumb {
    background-color: var(--divider-color);
    border-radius: 4px;
}

.scrollable-table::-webkit-scrollbar-thumb:hover {
    background-color: var(--modal-divider-color);
}

.table-container {
    overflow-x: auto;
    /* Enables horizontal scroll if needed */
    -webkit-overflow-scrolling: touch;
    /* Smooth scrolling on mobile */
}

table {
    border-collapse: collapse;
    margin-bottom: 1rem;
    box-sizing: border-box;
    /* Ensures table can expand and scroll */
    overflow-x: auto;
}

table th,
table td {
    padding: 0.5rem;
    border: 1px solid var(--divider-color);
}

table th {
    background-color: var(--modal-bg-color);
    font-weight: 600;
}

table td {
    background-color: var(--main-bg-color);
    font-weight: 400;
}

table th,
table td {
    font-size: 0.875rem;
    line-height: 1.5;
    min-width: 5rem;
}
            </style>
        </head>
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

private class HtmlViewerJavaScriptInterface(
    private val expectedMin: Int,
    private val onHeightMeasured: (Int) -> Unit
) {
    private var lastHeight = 0

    @android.webkit.JavascriptInterface
    fun resize(height: Int) {
        // Only call if height actually changed
        if (height != lastHeight) {
            println("resizing to $height")
            lastHeight = height
            val safeHeight = height.coerceAtLeast(expectedMin)
            onHeightMeasured(safeHeight)
        }
    }
}
