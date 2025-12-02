package space.httpjames.kagiassistantmaterial.ui.assist

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import space.httpjames.kagiassistantmaterial.AssistantClient
import space.httpjames.kagiassistantmaterial.ui.overlay.AssistantOverlayScreen
import space.httpjames.kagiassistantmaterial.ui.theme.KagiAssistantTheme

class KagiAssistantSession(context: Context) : VoiceInteractionSession(context),
    LifecycleOwner,
    SavedStateRegistryOwner {

    // 2. Initialize the Registries
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private var isShowingAlready = false

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry


    private val _reinvokeFlow = MutableSharedFlow<Bundle?>(extraBufferCapacity = 1)
    val reinvokeFlow: SharedFlow<Bundle?> = _reinvokeFlow

    private val _screenshotFlow = MutableSharedFlow<Bitmap?>(replay = 1)
    val screenshotFlow: SharedFlow<Bitmap?> = _screenshotFlow

    override fun onCreate() {
        super.onCreate()
        // 3. Initialize the saved state registry
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateContentView(): View {
        val prefs = context.getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        val sessionToken = prefs.getString("session_token", null)
        val flow = reinvokeFlow

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
                                reinvokeFlow = flow,
                                screenshotFlow = screenshotFlow,
                                onDismiss = { finish() }
                            )
                        }
                    }
                }
            }
        }
        return composeView
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        screenshot?.let {
            _screenshotFlow.tryEmit(it)
        }
    }

    // 5. Drive the Lifecycle events based on the Session events
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        if (isShowingAlready) {
            // Session was re-invoked while open
            _reinvokeFlow.tryEmit(args)
        } else {
            isShowingAlready = true
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onHide() {
        super.onHide()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        isShowingAlready = false
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
