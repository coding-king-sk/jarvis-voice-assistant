# Architecture — code kaise kaam karta hai

## Flow

```
[MicStream]  16kHz mono, 1280-sample chunks
     |
[WakeWordDetector]  3 ONNX models, on-device, ~1% battery
     |  wake!  -> mic release karo
[SpeechRecognizerManager]  Android STT, locale en-IN
     |  "mummy ko call karo"
[AssistantEngine]
     |
[GeminiClient]  gemini-2.0-flash + functionDeclarations
     |
     +--- text jawab ------------------+
     |                                 |
     +--- functionCall                 |
            |                          |
       [ToolExecutor]                  |
            |  result string           |
       [GeminiClient] (confirmation)   |
            |                          |
            +--------------------------+
                        |
                  [TtsManager]  Gemini TTS -> AudioTrack
                        |       (fail -> Android TextToSpeech)
                  wapas wake word listening
```

## Files ka kaam

| File | Zimmedari |
| --- | --- |
| `JarvisApp.kt` | Notification channels banata hai |
| `MainActivity.kt` | Permissions + Compose UI host |
| `ui/AssistantScreen.kt` | Chat bubbles, mic button, service toggle |
| `core/AssistantEngine.kt` | Poore turn ka orchestration, singleton |
| `service/JarvisForegroundService.kt` | Always-on wake word loop |
| `service/BootReceiver.kt` | Restart ke baad service chalu |
| `wakeword/MicStream.kt` | AudioRecord wrapper |
| `wakeword/WakeWordDetector.kt` | openWakeWord 3-model ONNX pipeline |
| `stt/SpeechRecognizerManager.kt` | Speech-to-text |
| `llm/GeminiClient.kt` | Gemini API + conversation history |
| `llm/FunctionDeclarations.kt` | Gemini ko tools ka schema |
| `tools/ToolExecutor.kt` | Asli Android actions |
| `tools/ContactResolver.kt` | Naam -> number, fuzzy match |
| `tools/ReminderReceiver.kt` | Reminder notification |
| `tts/TtsManager.kt` | Gemini TTS + fallback |

## Important design decisions

**Mic sharing** — `AudioRecord` (wake word) aur `SpeechRecognizer` ek saath mic
nahi le sakte. Isliye wake word detect hote hi `MicStream.stop()` hota hai,
phir STT chalti hai, aur turn khatam hone par `onTurnFinished` callback se
wake word loop wapas start hota hai.

**Singleton engine** — Service aur Activity dono `AssistantEngine.get(context)`
se ek hi instance lete hain, taaki conversation history aur state share ho.

**Function calling loop** — Gemini function call karta hai -> hum execute karte
hain -> result wapas Gemini ko `functionResponse` role me bhejte hain -> Gemini
natural Hinglish confirmation banata hai. Isse hard-coded jawab nahi likhne padte.

**History trim** — Sirf last 20 turns rakhe jaate hain (`trimHistory()`),
warna har request mehngi ho jaati.

**TTS fallback** — Gemini TTS raw PCM (24kHz, 16-bit, mono) deta hai jo
`AudioTrack` se stream hota hai. Network fail ho to bina rukavat Android TTS
pe switch ho jaata hai.

## Battery

| Component | Kharcha |
| --- | --- |
| Wake word (ONNX, 1 thread) | ~1-2% per hour |
| STT | sirf command ke waqt |
| Gemini API | sirf command ke waqt |

Sabse bada risk battery nahi, **OEM task killers** hain — SETUP.md ka step 8 padho.

## Aage kya add kar sakte ho

- Room DB me conversation persist karna
- whisper.cpp se offline STT
- Music control (`MediaSession`)
- Calendar aur Gmail tools
- Multi-turn clarification ("kis Rahul ko?")
- Settings screen: voice picker, sensitivity slider
