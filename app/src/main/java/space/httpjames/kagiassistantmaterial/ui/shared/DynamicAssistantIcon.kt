package space.httpjames.kagiassistantmaterial.ui.shared

import android.content.Context
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.size.Scale
import space.httpjames.kagiassistantmaterial.R
import java.io.File

@Composable
fun DynamicAssistantIcon(
    contentDescription: String = "Assistant icon",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val companionKey = remember(context) {
        context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
            .getString("companion", null)
    }
    val svgPath = remember(companionKey) {
        companionKey?.let { key ->
            File(context.cacheDir, "companion_$key.svg")
                .takeIf { it.exists() }
                ?.absolutePath
        }
    }

    if (svgPath != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(svgPath))
                .decoderFactory(SvgDecoder.Factory())
                .scale(Scale.FIT)
                .build(),
            contentDescription = contentDescription,
            modifier = modifier
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.fetch_ball_icon),
            contentDescription = contentDescription,
            tint = Color.Unspecified,
            modifier = modifier
        )
    }
}
