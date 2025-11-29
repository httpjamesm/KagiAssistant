package space.httpjames.kagiassistantmaterial

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import space.httpjames.kagiassistantmaterial.ui.landing.LandingScreen
import space.httpjames.kagiassistantmaterial.ui.main.MainScreen
import space.httpjames.kagiassistantmaterial.ui.settings.SettingsScreen
import space.httpjames.kagiassistantmaterial.ui.theme.KagiAssistantTheme

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

        setContent {
            KagiAssistantTheme {
                val navController = rememberNavController()
                var sessionToken = prefs.getString("session_token", null)

                NavHost(
                    navController = navController,
                    startDestination = if (sessionToken != null) "main" else "landing"
                ) {
                    composable("landing") {
                        LandingScreen(onLoginSuccess = {
                            prefs.edit().putString("session_token", it).apply()
                            sessionToken = it
                            navController.navigate("main") {
                                popUpTo("landing") { inclusive = true }
                            }
                        })
                    }
                    composable("main") {
                        val assistantClient = AssistantClient(sessionToken!!)
                        MainScreen(assistantClient = assistantClient, navController = navController)
                    }
                    composable("settings") {
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

class KagiAssistantService : VoiceInteractionService()
class KagiAssistantSession(context: Context) : VoiceInteractionSession(context)

class KagiAssistantIService : VoiceInteractionSessionService() {
    override fun onNewSession(p0: Bundle): VoiceInteractionSession {
        return KagiAssistantSession(this)
    }
}
