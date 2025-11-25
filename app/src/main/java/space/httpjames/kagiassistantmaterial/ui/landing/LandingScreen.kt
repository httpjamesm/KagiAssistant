package space.httpjames.kagiassistantmaterial.ui.landing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import space.httpjames.kagiassistantmaterial.R
import kotlin.math.abs

@Preview
@Composable
fun LandingScreen(onLoginSuccess: (String) -> Unit = {}) {
    val state = rememberLandingScreenState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var waitingOnAuth: Boolean by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.padding(top = 90.dp, start = 24.dp)
            ) {
                Text(
                    "Kagi",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Assistant",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            BouncingBall()

            Column (
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 30.dp, start = 24.dp, end = 24.dp)
            ) {
                Button(
                    enabled = !waitingOnAuth,
                    onClick = {
                        coroutineScope.launch {
                            val data = state.startCeremony()
                            waitingOnAuth = true
                            if (data.isSuccess) {
                                val token = data.getOrNull()
                                if (token != null) {
                                    val url = "https://kagi.com/settings/qr_authorize?t=$token"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                ) {
                    if (waitingOnAuth) {
                        CircularProgressIndicator()
                    } else {
                        Text("Sign in", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("We'll sync all of your chats to this device", style = MaterialTheme.typography.labelMedium, color = Color.Gray.copy(alpha = 0.8f))
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)          // wait 1 s
            val token = state.checkCeremony()
            if (token != null) {
                onLoginSuccess(token)
                break
            }
        }

    }
}

@Composable
fun BouncingBall() {
    val haptics = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition("bouncing_ball")
    val context = LocalContext.current
    val vibe = remember {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // 1. The Vertical Bounce (Gravity)
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -150f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    // Haptic effects for bounce impact and flight "vwoooop"
    LaunchedEffect(Unit) {
        var isTouchingFloor = true
        var previousY = 0f
        var lastHapticTime = 0L

        snapshotFlow { offsetY }
            .collect { y ->
                val currentTime = System.currentTimeMillis()

                if (y > -10f) {
                    // Impact haptic when hitting the floor
                    if (!isTouchingFloor) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isTouchingFloor = true
                    }
                } else {
                    isTouchingFloor = false

                    // Subtle "vwoooop" haptic while in flight - based on velocity
                    val velocity = abs(y - previousY)

                    // Trigger haptics every ~40ms when there's meaningful movement
                    if (currentTime - lastHapticTime > 40 && velocity > 0.3f) {
                        triggerFlightHaptic(vibe, velocity)
                        lastHapticTime = currentTime
                    }
                }
                previousY = y
            }
    }

    // 2. The Rotation (Syncs with gravity to "hang" at the top)
    val rotation by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Icon(
            painter = painterResource(id = R.drawable.fetch_ball_icon),
            contentDescription = null,
            modifier = Modifier
                .offset(y = offsetY.dp)
                .rotate(rotation)
                .padding(start = 24.dp)
                .size(300.dp),
            tint = Color.Unspecified
        )
        HorizontalDivider()
    }
}

private fun triggerFlightHaptic(vibe: Vibrator, velocity: Float) {
    when {
        // Android 11+ - Premium haptic primitives for smoothest feel
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            val intensity = (velocity / 8f).coerceIn(0.05f, 0.3f)
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, intensity)
                .compose()
            vibe.vibrate(effect)
        }
        // Android 8+ - Amplitude-controlled vibration
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            val amplitude = (velocity * 4).coerceIn(1f, 30f).toInt()
            val effect = VibrationEffect.createOneShot(12, amplitude)
            vibe.vibrate(effect)
        }
        // Older devices - skip subtle flight haptics (too coarse)
        else -> { /* No-op for pre-Oreo */ }
    }
}
