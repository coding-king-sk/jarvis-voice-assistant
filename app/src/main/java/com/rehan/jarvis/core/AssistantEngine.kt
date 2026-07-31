package com.rehan.jarvis.core

import android.content.Context
import android.util.Log
import com.rehan.jarvis.BuildConfig
import com.rehan.jarvis.audio.BargeInDetector
import com.rehan.jarvis.llm.GeminiClient
import com.rehan.jarvis.llm.LlmReply
import com.rehan.jarvis.stt.SpeechRecognizerManager
import com.rehan.jarvis.tools.ToolExecutor
import com.rehan.jarvis.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 * Poore assistant ka dimaag. STT -> (local rules ya Gemini) -> Tools -> TTS.
 *
 * Teen khaas cheezein:
 * 1. Fast path — "torch on karo" jaise seedhe kaam bina internet ke, turant.
 * 2. Multi-step — ek hi baat me kai kaam ho to sab ek ke baad ek chalte hain.
 * 3. Live call mode — jawab ke baad khud sunta rehta hai, beech me tok bhi sakte ho.
 */
class AssistantEngine(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val gemini = GeminiClient(BuildConfig.GEMINI_API_KEY)
    private val tools = ToolExecutor(appContext)
    private val tts = TtsManager(appContext, BuildConfig.GEMINI_API_KEY)
    private val stt = SpeechRecognizerManager(appContext)
    private val bargeIn = BargeInDetector(appContext)

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

    /** Live call mode chal raha hai ya nahi — UI ise dikha sakta hai. */
    private val _liveMode = MutableStateFlow(false)
    val liveMode: StateFlow<Boolean> = _liveMode.asStateFlow()

    /** Turn khatam hone par call hota hai — service isse wake word wapas start karta hai. */
    var onTurnFinished: (() -> Unit)? = null

    /**
     * Gemini ki awaaz sunne me achhi hai lekin har jawab pe 2-3 second lagti hai.
     * Isliye default phone ki apni awaaz — turant bolti hai. Settings se badal sakte ho.
     */
    private var preferGeminiTts = false

    private var liveStartedAt = 0L
    private var missedTurns = 0

    init {
        tts.init()
        stt.onPartialResult = { _partialText.value = it }
        stt.onRmsChanged = { rms ->
            _micLevel.value = ((rms + 2f) / 12f).coerceIn(0f, 1f)
        }
        stt.onFinalResult = { text ->
            _partialText.value = ""
            _micLevel.value = 0f
            missedTurns = 0
            addMessage(text, fromUser = true)
            think(text)
        }
        stt.onError = { message ->
            _partialText.value = ""
            _micLevel.value = 0f
            _state.value = AssistantState.IDLE

            // Live mode me har chhoti si khamoshi pe bolna irritating lagta hai.
            // Do baar kuch na aaye to chupchaap wake word pe wapas chale jao.
            if (_liveMode.value) {
                missedTurns++
                if (missedTurns >= MAX_MISSES) endLive(silent = true) else finishTurn()
            } else {
                scope.launch {
                    speakOut(message)
                    finishTurn()
                }
            }
        }
    }

    fun startListening() {
        if (_state.value != AssistantState.IDLE) return
        tts.stopSpeaking()
        bargeIn.stop()
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

    /** Live call mode band karo — UI ka cross button ya "bas" bolne pe. */
    fun endLive(silent: Boolean = false) {
        _liveMode.value = false
        liveStartedAt = 0L
        missedTurns = 0
        bargeIn.stop()
        stt.stopListening()
        _micLevel.value = 0f
        _state.value = AssistantState.IDLE
        if (!silent) addMessage("Live baat band. Wake word bol ke phir bula lena.", fromUser = false)
        onTurnFinished?.invoke()
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
        // "Bas", "chup ho ja", "bye" — live baat khatam
        if (isGoodbye(userText)) {
            addMessage("Theek hai, bula lena jab kaam ho.", fromUser = false)
            _state.value = AssistantState.SPEAKING
            speakOut("Theek hai, bula lena jab kaam ho.")
            endLive(silent = true)
            return@launch
        }

        val online = OfflineRouter.isOnline(appContext)
        _offline.value = !online

        // FAST PATH — "torch on karo", "volume 50", "YouTube kholo" jaise kaam
        // seedhe phone pe ho jaate hain. Gemini ka intezaar (2-4 second) bach jaata hai.
        if (runOffline(userText)) return@launch

        if (!online) {
            respond(
                "Internet nahi hai, isliye ye samajh nahi paya. " +
                    "Abhi torch, volume, brightness, silent mode, alarm, reminder, " +
                    "call, message, music, screenshot aur notifications jaise kaam bol sakte ho."
            )
            return@launch
        }

        _state.value = AssistantState.THINKING
        handleReply(gemini.ask(userText), userText)
    }

    private fun isGoodbye(text: String): Boolean {
        val t = text.lowercase().trim()
        return t.length <= 30 && GOODBYE_WORDS.any { t.contains(it) }
    }

    /**
     * Bina Gemini ke samajhne ki koshish.
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
                    Log.i(TAG, "local tool: ${local.tool} ${local.args}")
                    outputs.add(tools.execute(local.tool, local.args))
                }
            }
        }

        if (outputs.isEmpty()) return false
        respond(outputs.joinToString(" "))
        return true
    }

    /**
     * Gemini ka jawab sambhalo. Jitne steps chahiye utne chalte hain,
     * isliye "YouTube kholo aur gaana chalao" jaise kaam poore hote hain.
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

        // Local val — warna Kotlin smart cast nahi kar paata
        val error = reply.error
        val text = reply.text

        when {
            // Kaam ho gaye lekin Gemini ka confirmation nahi aaya? Chup mat raho.
            error != null && done.isNotEmpty() -> respond(done.joinToString(" "))

            error != null -> {
                if (!runOffline(originalText)) respond(error)
            }

            else -> {
                val finalText = text?.takeIf { it.isNotBlank() }
                    ?: done.joinToString(" ").ifBlank { "Ho gaya." }
                respond(finalText)
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

    /**
     * Bolo, aur bolte waqt mic khula rakho.
     * User beech me bol de to turant chup ho jao — asli call jaisa.
     */
    private suspend fun speakOut(text: String) {
        tts.useGeminiTts = preferGeminiTts && OfflineRouter.isOnline(appContext)

        bargeIn.start {
            Log.i(TAG, "user ne beech me toka")
            tts.stopSpeaking()
        }
        try {
            tts.speak(text)
        } finally {
            bargeIn.stop()
        }
    }

    /**
     * Jawab ke baad chup mat baitho — thodi der aur suno.
     * Isse har baar wake word bolne ki zarurat nahi padti.
     */
    private fun finishTurn() {
        _micLevel.value = 0f
        _state.value = AssistantState.IDLE

        if (liveStartedAt == 0L) liveStartedAt = System.currentTimeMillis()
        val liveTooLong = System.currentTimeMillis() - liveStartedAt > LIVE_MAX_MS

        if (liveTooLong || missedTurns >= MAX_MISSES) {
            _liveMode.value = false
            liveStartedAt = 0L
            missedTurns = 0
            onTurnFinished?.invoke()
            return
        }

        _liveMode.value = true
        scope.launch {
            delay(FOLLOW_UP_DELAY_MS)
            if (_liveMode.value && _state.value == AssistantState.IDLE) startListening()
        }
    }

    private fun addMessage(text: String, fromUser: Boolean) {
        _messages.value = (_messages.value + ChatMessage(text, fromUser)).takeLast(100)
    }

    fun newConversation() {
        gemini.clearHistory()
        _messages.value = emptyList()
        endLive(silent = true)
    }

    fun setVoice(voice: String) { tts.voiceName = voice }

    fun setUseGeminiTts(enabled: Boolean) {
        preferGeminiTts = enabled
        tts.useGeminiTts = enabled
    }

    fun release() {
        bargeIn.stop()
        stt.destroy()
        tts.release()
    }

    companion object {
        private const val TAG = "AssistantEngine"
        private const val MAX_TOOL_ROUNDS = 5

        /** Jawab ke baad itni der me dobara sun-na shuru. */
        private const val FOLLOW_UP_DELAY_MS = 300L

        /** Itni der tak koi baat na ho to live mode band. */
        private const val LIVE_MAX_MS = 3 * 60 * 1000L

        /** Itni baar kuch sunai na de to live mode band. */
        private const val MAX_MISSES = 2

        private val GOODBYE_WORDS = listOf(
            "bas", "bye", "chup ho", "chup ja", "band karo", "stop", "khatam",
            "thik hai bas", "jao", "so ja", "good night"
        )

        @Volatile private var instance: AssistantEngine? = null

        fun get(context: Context): AssistantEngine =
            instance ?: synchronized(this) {
                instance ?: AssistantEngine(context.applicationContext).also { instance = it }
            }
    }
}
