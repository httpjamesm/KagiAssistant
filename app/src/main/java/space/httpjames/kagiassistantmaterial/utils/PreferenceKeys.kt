package space.httpjames.kagiassistantmaterial.utils

enum class PreferenceKey(val key: String) {
    SESSION_TOKEN("session_token"),
    MIC_GRANTED("mic_granted"),
    ASSISTANT_MODEL("assistant_model"),
    USE_MINI_OVERLAY("use_mini_overlay"),
    AUTO_SPEAK_REPLIES("auto_speak_replies"),
    AUTO_TOGGLE_INTERNET("auto_toggle_internet"),
    OPEN_KEYBOARD_AUTOMATICALLY("open_keyboard_automatically"),
    STICKY_SCROLL("sticky_scroll"),
    SAVED_TEXT("savedText"),
    SAVED_THREAD_ID("savedThreadId"),
    COMPANION("companion"),
    PROFILE("profile"),
    RECENTLY_USED_PROFILES("recently_used_profiles"),
    TTS_VOICE("tts_voice");

    companion object {
        const val DEFAULT_ASSISTANT_MODEL = "gemini-2-5-flash-lite"
        const val DEFAULT_TTS_VOICE = ""
        val DEFAULT_SESSION_TOKEN =
            null // this will never be filled. but it's there for graceful error handling (and maybe some dangerous debugging
        const val DEFAULT_SAVED_TEXT = ""
        const val DEFAULT_RECENTLY_USED_PROFILES = "[]"
        const val DEFAULT_USE_MINI_OVERLAY = true
        const val DEFAULT_AUTO_SPEAK_REPLIES = true
        const val DEFAULT_AUTO_TOGGLE_INTERNET = true
        const val DEFAULT_OPEN_KEYBOARD_AUTOMATICALLY = false
        const val DEFAULT_STICKY_SCROLL = true
    }
}
