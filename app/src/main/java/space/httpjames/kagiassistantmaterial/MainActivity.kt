package space.httpjames.kagiassistantmaterial

import android.Manifest
import android.R
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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




