package space.httpjames.kagiassistantmaterial

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import space.httpjames.kagiassistantmaterial.ui.landing.LandingScreen
import space.httpjames.kagiassistantmaterial.ui.main.MainScreen
import space.httpjames.kagiassistantmaterial.ui.settings.SettingsScreen
import space.httpjames.kagiassistantmaterial.ui.theme.KagiAssistantTheme

enum class Screens(val route: String) {
    LANDING("landing"),
    MAIN("main"),
    SETTINGS("settings")
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rootView: View = findViewById(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

            // Here’s where you can tell the WebView (or your leptos viewport)
            // to resize/pad itself.
            if (imeVisible) {
                v.setPadding(0, 0, 0, imeHeight)
            } else {
                v.setPadding(0, 0, 0, 0)
            }

            insets
        }

        val prefs = getSharedPreferences("assistant_prefs", MODE_PRIVATE)

        val launcher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                prefs.edit().putBoolean("mic_granted", granted).apply()
            }
        launcher.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            KagiAssistantTheme {
                val navController = rememberNavController()
                var sessionToken = prefs.getString("session_token", null)

                NavHost(
                    navController = navController,
                    startDestination = if (sessionToken != null) Screens.MAIN.route else Screens.LANDING.route
                ) {
                    composable(Screens.LANDING.route) {
                        LandingScreen(onLoginSuccess = {
                            prefs.edit().putString("session_token", it).apply()
                            sessionToken = it
                            navController.navigate(Screens.MAIN.route) {
                                popUpTo(Screens.LANDING.route) { inclusive = true }
                            }
                        })
                    }
                    composable(Screens.MAIN.route) {
                        val assistantClient = AssistantClient(sessionToken!!)
                        MainScreen(assistantClient = assistantClient, navController = navController)
                    }
                    composable(Screens.SETTINGS.route) {
                        val assistantClient = AssistantClient(sessionToken!!)
                        SettingsScreen(
                            assistantClient = assistantClient,
                            navController = navController
                        )
                    }
                }
            }
        }
    }
}

class KagiAssistantService : VoiceInteractionService() {
    override fun onPrepareToShowSession(args: Bundle, flags: Int) {
        println("onPrepareToShowSession()")
        // Do NOT call showSession here. Just prepare internal state if needed.
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        println("onLaunchVoiceAssistFromKeyguard()")
    }
}


class KagiAssistantSession(context: Context) : VoiceInteractionSession(context),
    LifecycleOwner,
    SavedStateRegistryOwner {

    // 2. Initialize the Registries
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        // 3. Initialize the saved state registry
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateContentView(): View {
        val prefs = context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        val sessionToken = prefs.getString("session_token", null)

        val composeView = ComposeView(context).apply {
            // 4. IMPORTANT: Attach the Lifecycle and Registry to the ViewTree
            setViewTreeLifecycleOwner(this@KagiAssistantSession)
            setViewTreeSavedStateRegistryOwner(this@KagiAssistantSession)

            setContent {
                KagiAssistantTheme {
                    // Added transparent background to Surface to ensure overlay look
                    Surface(
                        modifier = Modifier.background(Color.Transparent),
                        color = Color.Transparent
                    ) {
                        if (sessionToken != null) {
                            val assistantClient = AssistantClient(sessionToken)
                            AssistantOverlayScreen(
                                assistantClient = assistantClient,
                                onDismiss = { finish() }
                            )
                        }
                    }
                }
            }
        }
        return composeView
    }

    // 5. Drive the Lifecycle events based on the Session events
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onHide() {
        super.onHide()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

class KagiAssistantIService : VoiceInteractionSessionService() {
    override fun onNewSession(p0: Bundle): VoiceInteractionSession {
        return KagiAssistantSession(this)
    }
}

@Composable
fun AssistantOverlayScreen(
    assistantClient: AssistantClient,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }

    // 1. Check permission the old-school way
    val permissionOk = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    val listener = remember {
        object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
            }

            override fun onPartialResults(b: Bundle?) {
                text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: text
            }

            override fun onError(e: Int) {}
            override fun onReadyForSpeech(b: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(db: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(e: Int, b: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(listener)
        onDispose {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        }
    }

    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    // 2. Start listening immediately if we have permission
    LaunchedEffect(permissionOk) {
        if (permissionOk) speechRecognizer.startListening(intent)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 36.dp)
                .height(200.dp),
            color = Color.White,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Assistant Overlay", color = Color.Black)
                Text(text, color = Color.Black)
                Button(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
