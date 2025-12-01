package space.httpjames.kagiassistantmaterial

import android.Manifest
import android.R
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
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
import space.httpjames.kagiassistantmaterial.ui.overlay.AssistantOverlayScreen
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

        val rootView: View = findViewById(R.id.content)

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

