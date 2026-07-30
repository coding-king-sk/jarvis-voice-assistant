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
 * Poore assistant ka dimaag. STT -> (offline rules ya Gemini) -> Tools -> TTS.
 *
 * Multi-step: ek hi baat me kai kaam ho to sab ek ke baad ek chalte hain,
 * jaise "YouTube kholo aur gaana chalao" ya "screenshot lo aur silent kar do".
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

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    /** Turn khatam hone par call hota hai — service isse wake word wapas start karta hai. */
    var onTurnFinished: (() -> Unit)? = null

    private var preferGeminiTts = true

    init {
        tts.init()
        stt.onPartialResult = { _partialText.value = it }
        stt.onRmsChanged = { rms ->
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

    fun startListening() {
        if (_state.value != AssistantState.IDLE) return
        tts.stopSpeaking()
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
                        "call, message, music, screenshot aur notifications jaise kaam bol sakte ho."
                )
            }
            return@launch
        }

        _state.value = AssistantState.THINKING
        handleReply(gemini.ask(userText), userText)
    }

    /**
     * Bina internet ke samajhne ki koshish.
     * "Torch on karo aur silent kar do" jaise do-do kaam bhi chalte hain.
     */
    private suspend fun runOffline(userText: String): Boolean {
        val outputs = mutableListOf<String>()

        for (part in OfflineRouter.split(userText)) {
            when (val local = OfflineRouter.match(part)) {
                null -> Unit

                is OfflineResult.Speak -> outputs.add(local.text)

                is OfflineResult.Tool -> {
                    _state.value = AssistantState.ACTING
                    Log.i(TAG, "offline tool: ${local.tool} ${local.args}")
                    outputs.add(tools.execute(local.tool, local.args))
                }
            }
        }

        if (outputs.isEmpty()) return false
        respond(outputs.joinToString(" "))
        return true
    }

    /**
     * Gemini ka jawab sambhalo.
     *
     * Purana bug: sirf ek hi tool chalta tha aur kahani wahin khatam ho jaati thi.
     * Ab hum loop chalate hain — Gemini jitne steps maange, sab karte hain.
     */
    private suspend fun handleReply(firstReply: LlmReply, originalText: String) {
        var reply = firstReply
        val done = mutableListOf<String>()
        var round = 0

        while (reply.isFunctionCall && round < MAX_TOOL_ROUNDS) {
            round++
            _state.value = AssistantState.ACTING

            val results = reply.calls.map { call ->
                Log.i(TAG, "tool ${round}: ${call.name} ${call.args}")
                val result = tools.execute(call.name, call.args)
                done.add(result)
                call.name to result
            }

            _state.value = AssistantState.THINKING
            reply = gemini.sendFunctionResults(results)
        }

        when {
            // Sab kaam ho gaye lekin Gemini ka confirmation nahi aaya?
            // Tools ne jo kaha wahi bol do — chup mat raho.
            reply.error != null && done.isNotEmpty() -> respond(done.joinToString(" "))

            reply.error != null -> {
                if (!runOffline(originalText)) respond(reply.error)
            }

            else -> {
                val text = reply.text?.takeIf { it.isNotBlank() }
                    ?: done.joinToString(" ").ifBlank { "Ho gaya." }
                respond(text)
            }
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
        private const val MAX_TOOL_ROUNDS = 5

        @Volatile private var instance: AssistantEngine? = null

        fun get(context: Context): AssistantEngine =
            instance ?: synchronized(this) {
                instance ?: AssistantEngine(context.applicationContext).also { instance = it }
            }
    }
}
