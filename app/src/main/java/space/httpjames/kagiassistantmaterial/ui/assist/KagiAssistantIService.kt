package space.httpjames.kagiassistantmaterial.ui.assist

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class KagiAssistantIService : VoiceInteractionSessionService() {
    override fun onNewSession(p0: Bundle): VoiceInteractionSession {
        return KagiAssistantSession(this)
    }
}

