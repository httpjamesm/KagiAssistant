package space.httpjames.kagiassistantmaterial

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import space.httpjames.kagiassistantmaterial.ui.companions.CompanionsScreen
import space.httpjames.kagiassistantmaterial.ui.landing.LandingScreen
import space.httpjames.kagiassistantmaterial.ui.main.MainScreen
import space.httpjames.kagiassistantmaterial.ui.settings.AssistantSettingsWebViewScreen
import space.httpjames.kagiassistantmaterial.ui.settings.SettingsScreen
import space.httpjames.kagiassistantmaterial.ui.theme.KagiAssistantTheme
import space.httpjames.kagiassistantmaterial.utils.PreferenceKey

enum class Screens(val route: String) {
    LANDING("landing"),
    MAIN("main"),
    SETTINGS("settings"),
    COMPANIONS("companions"),
    ASSISTANT_SETTINGS_WEBVIEW("assistant_settings_webview")
}

class MainActivity : ComponentActivity() {

    private val pendingSharedUris = mutableStateListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle initial intent (e.g., when app is launched from share sheet)
        extractSharedUris(intent)?.let { pendingSharedUris.addAll(it) }

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
                prefs.edit().putBoolean(PreferenceKey.MIC_GRANTED.key, granted).apply()
            }
        launcher.launch(Manifest.permission.RECORD_AUDIO)

        val notifLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            KagiAssistantTheme {
                val navController = rememberNavController()
                var sessionToken = prefs.getString(
                    PreferenceKey.SESSION_TOKEN.key,
                    PreferenceKey.DEFAULT_SESSION_TOKEN
                )

                NavHost(
                    navController = navController,
                    startDestination = if (sessionToken != null) Screens.MAIN.route else Screens.LANDING.route
                ) {
                    composable(Screens.LANDING.route) {
                        LandingScreen(onLoginSuccess = {
                            prefs.edit().putString(PreferenceKey.SESSION_TOKEN.key, it).apply()
                            sessionToken = it
                            navController.navigate(Screens.MAIN.route) {
                                popUpTo(Screens.LANDING.route) { inclusive = true }
                            }
                        })
                    }
                    composable(Screens.MAIN.route) {
                        val assistantClient = AssistantClient(sessionToken!!)
                        // Observe the shared URIs state and pass a copy to MainScreen
                        val sharedUris = pendingSharedUris.toList()
                        // Clear after consuming by taking all elements at once
                        if (sharedUris.isNotEmpty()) {
                            pendingSharedUris.clear()
                        }
                        MainScreen(
                            assistantClient = assistantClient,
                            navController = navController,
                            sharedUris = sharedUris
                        )
                    }
                    composable(Screens.SETTINGS.route) {
                        val assistantClient = AssistantClient(sessionToken!!)
                        SettingsScreen(
                            assistantClient = assistantClient,
                            navController = navController
                        )
                    }
                    composable(Screens.COMPANIONS.route) {
                        val assistantClient = AssistantClient(sessionToken!!)
                        CompanionsScreen(
                            assistantClient = assistantClient,
                            navController = navController
                        )
                    }
                    composable(Screens.ASSISTANT_SETTINGS_WEBVIEW.route) {
                        AssistantSettingsWebViewScreen(
                            sessionToken = sessionToken ?: "",
                            navController = navController
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle new intent when app is already running (singleTask launch mode)
        extractSharedUris(intent)?.let { uris ->
            pendingSharedUris.clear()
            pendingSharedUris.addAll(uris)
        }
    }

    /**
     * Extract shared URIs from an intent.
     * Handles both ACTION_SEND (single item) and ACTION_SEND_MULTIPLE (multiple items).
     */
    private fun extractSharedUris(intent: Intent?): List<Uri>? {
        if (intent == null) return null

        val action = intent.action ?: return null
        val type = intent.type

        println("$action $type")

        return when (action) {
            Intent.ACTION_SEND -> {
                if (type != null) {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) listOf(uri) else null
                } else null
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                if (type != null) {
                    val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    if (!uris.isNullOrEmpty()) uris else null
                } else null
            }

            else -> null
        }
    }
}




