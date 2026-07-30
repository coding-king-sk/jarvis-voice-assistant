package com.rehan.jarvis.core

import android.content.Context
import android.util.Log
import com.rehan.jarvis.BuildConfig
import com.rehan.jarvis.llm.GeminiClient
import com.rehan.jarvis.llm.LlmReply
import com.rehan.jarvis.stt.SpeechRecognizerManager
import com.rehan.jarvis.tools.ToolExecutor
import com.rehan.jarvis.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AssistantState { IDLE, LISTENING, THINKING, ACTING, SPEAKING }

data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Poore assistant ka dimaag. STT -> (offline rules ya Gemini) -> Tool -> TTS.
 *
 * Offline policy:
 *  - Internet nahi hai  -> sab kuch phone pe hi, OfflineRouter se
 *  - Internet hai       -> Gemini (behtar samajh), lekin fail hone par offline fallback
 */
class AssistantEngine(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val gemini = GeminiClient(BuildConfig.GEMINI_API_KEY)
    private val tools = ToolExecutor(appContext)
    private val tts = TtsManager(appContext, BuildConfig.GEMINI_API_KEY)
    private val stt = SpeechRecognizerManager(appContext)

    private val _state = MutableStateFlow(AssistantState.IDLE)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    /** Mic ka live level, 0 se 1 ke beech — orb ka waveform isi se hilta hai. */
    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    /** UI me chhota sa badge dikhane ke liye. */
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    /** Turn khatam hone par call hota hai — service isse wake word wapas start karta hai. */
    var onTurnFinished: (() -> Unit)? = null

    private var preferGeminiTts = true

    init {
        tts.init()
        stt.onPartialResult = { _partialText.value = it }
        stt.onRmsChanged = { rms ->
            // rms roughly -2 se 10 dB tak aata hai; use 0..1 me badal dete hain
            _micLevel.value = ((rms + 2f) / 12f).coerceIn(0f, 1f)
        }
        stt.onFinalResult = { text ->
            _partialText.value = ""
            _micLevel.value = 0f
            addMessage(text, fromUser = true)
            think(text)
        }
        stt.onError = { message ->
            _partialText.value = ""
            _micLevel.value = 0f
            _state.value = AssistantState.IDLE
            scope.launch {
                speakOut(message)
                finishTurn()
            }
        }
    }

    /** Mic se sunna shuru karo (wake word ke baad ya button dabane par). */
    fun startListening() {
        if (_state.value != AssistantState.IDLE) return
        tts.stopSpeaking()
        // Internet nahi hai to phone ka apna offline speech pack use karo
        val online = OfflineRouter.isOnline(appContext)
        _offline.value = !online
        stt.preferOffline = !online
        _state.value = AssistantState.LISTENING
        stt.startListening()
    }

    fun stopListening() {
        stt.stopListening()
        _micLevel.value = 0f
        _state.value = AssistantState.IDLE
    }

    /** Keyboard se type kiya hua command. */
    fun sendText(text: String) {
        if (text.isBlank()) return
        addMessage(text, fromUser = true)
        think(text)
    }

    /** Camera se li gayi photo ke baare me sawaal. */
    fun sendImage(jpegBase64: String, question: String) = scope.launch {
        val prompt = question.ifBlank { "Is photo me kya hai? Chhota sa jawab do." }
        addMessage(prompt, fromUser = true)

        if (!OfflineRouter.isOnline(appContext)) {
            respond("Photo samajhne ke liye internet chahiye. Baaki sab kaam offline chalte hain.")
            return@launch
        }

        _state.value = AssistantState.THINKING
        handleReply(gemini.askWithImage(jpegBase64, prompt), prompt)
    }

    private fun think(userText: String) = scope.launch {
        val online = OfflineRouter.isOnline(appContext)
        _offline.value = !online

        if (!online) {
            if (!runOffline(userText)) {
                respond(
                    "Internet nahi hai, isliye ye samajh nahi paya. " +
                        "Abhi torch, volume, brightness, silent mode, alarm, reminder, " +
                        "call, message, music, notifications aur time jaise kaam bol sakte ho."
                )
            }
            return@launch
        }

        _state.value = AssistantState.THINKING
        handleReply(gemini.ask(userText), userText)
    }

    /**
     * Bina internet ke samajhne ki koshish.
     * @return true agar kaam ho gaya
     */
    private suspend fun runOffline(userText: String): Boolean {
        return when (val local = OfflineRouter.match(userText)) {
            null -> false

            is OfflineResult.Speak -> {
                Log.i(TAG, "offline answer")
                respond(local.text)
                true
            }

            is OfflineResult.Tool -> {
                _state.value = AssistantState.ACTING
                Log.i(TAG, "offline tool: ${local.tool} ${local.args}")
                respond(tools.execute(local.tool, local.args))
                true
            }
        }
    }

    private suspend fun handleReply(reply: LlmReply, originalText: String) {
        when {
            reply.error != null -> {
                // Gemini fail ho gaya (net gir gaya, quota khatam, key galat)?
                // Chup mat baitho — offline dimaag se koshish karo.
                if (!runOffline(originalText)) respond(reply.error)
            }

            reply.isFunctionCall -> {
                _state.value = AssistantState.ACTING
                val name = reply.functionName!!
                val args = reply.functionArgs ?: org.json.JSONObject()
                Log.i(TAG, "tool: $name $args")

                val result = tools.execute(name, args)
                val followUp = gemini.sendFunctionResult(name, result)

                // Gemini ka confirmation na aaye to tool ka apna message bol do
                respond(followUp.text ?: result)
            }

            else -> respond(reply.text.orEmpty())
        }
    }

    private suspend fun respond(text: String) {
        if (text.isNotBlank()) {
            addMessage(text, fromUser = false)
            _state.value = AssistantState.SPEAKING
            speakOut(text)
        }
        finishTurn()
    }

    /** Offline ho to hamesha phone ki apni awaaz use karo. */
    private suspend fun speakOut(text: String) {
        tts.useGeminiTts = preferGeminiTts && OfflineRouter.isOnline(appContext)
        tts.speak(text)
    }

    private fun finishTurn() {
        _state.value = AssistantState.IDLE
        _micLevel.value = 0f
        onTurnFinished?.invoke()
    }

    private fun addMessage(text: String, fromUser: Boolean) {
        _messages.value = (_messages.value + ChatMessage(text, fromUser)).takeLast(100)
    }

    fun newConversation() {
        gemini.clearHistory()
        _messages.value = emptyList()
    }

    fun setVoice(voice: String) { tts.voiceName = voice }

    fun setUseGeminiTts(enabled: Boolean) {
        preferGeminiTts = enabled
        tts.useGeminiTts = enabled
    }

    fun release() {
        stt.destroy()
        tts.release()
    }

    companion object {
        private const val TAG = "AssistantEngine"

        @Volatile private var instance: AssistantEngine? = null

        fun get(context: Context): AssistantEngine =
            instance ?: synchronized(this) {
                instance ?: AssistantEngine(context.applicationContext).also { instance = it }
            }
    }
}
