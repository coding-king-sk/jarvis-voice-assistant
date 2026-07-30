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
 * Poore assistant ka dimaag. STT -> Gemini -> Tool -> TTS ka flow yahan hai.
 * Service aur UI dono isi ek instance ko share karte hain.
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

    /** Turn khatam hone par call hota hai — service isse wake word wapas start karta hai. */
    var onTurnFinished: (() -> Unit)? = null

    init {
        tts.init()
        stt.onPartialResult = { _partialText.value = it }
        stt.onFinalResult = { text ->
            _partialText.value = ""
            addMessage(text, fromUser = true)
            think(text)
        }
        stt.onError = { message ->
            _partialText.value = ""
            _state.value = AssistantState.IDLE
            scope.launch {
                tts.speak(message)
                finishTurn()
            }
        }
    }

    /** Mic se sunna shuru karo (wake word ke baad ya button dabane par). */
    fun startListening() {
        if (_state.value != AssistantState.IDLE) return
        tts.stopSpeaking()
        _state.value = AssistantState.LISTENING
        stt.startListening()
    }

    fun stopListening() {
        stt.stopListening()
        _state.value = AssistantState.IDLE
    }

    /** Keyboard se type kiya hua command. */
    fun sendText(text: String) {
        if (text.isBlank()) return
        addMessage(text, fromUser = true)
        think(text)
    }

    private fun think(userText: String) = scope.launch {
        _state.value = AssistantState.THINKING
        handleReply(gemini.ask(userText))
    }

    private suspend fun handleReply(reply: LlmReply) {
        when {
            reply.error != null -> {
                respond(reply.error)
            }

            reply.isFunctionCall -> {
                _state.value = AssistantState.ACTING
                val name = reply.functionName!!
                val args = reply.functionArgs ?: org.json.JSONObject()
                Log.i(TAG, "tool: $name $args")

                val result = tools.execute(name, args)
                val followUp = gemini.sendFunctionResult(name, result)

                // Gemini ka confirmation na aaye to tool ka apna message bol do
                respond(followUp.text ?: followUp.error ?: result)
            }

            else -> respond(reply.text.orEmpty())
        }
    }

    private suspend fun respond(text: String) {
        if (text.isNotBlank()) {
            addMessage(text, fromUser = false)
            _state.value = AssistantState.SPEAKING
            tts.speak(text)
        }
        finishTurn()
    }

    private fun finishTurn() {
        _state.value = AssistantState.IDLE
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
    fun setUseGeminiTts(enabled: Boolean) { tts.useGeminiTts = enabled }

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
