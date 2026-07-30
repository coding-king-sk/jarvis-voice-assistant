# Jarvis — Android Voice Assistant (Hey Google jaisa)

Ek **always-on Android voice assistant** jo custom wake word sunta hai, Hinglish command samajhta hai (Gemini Flash), phone pe kaam karta hai aur Gemini TTS se bol kar jawab deta hai.

> Beginner-friendly project. Kotlin + Jetpack Compose. Poora code ready hai — bas API key aur wake word model daalna hai.

---

## Pipeline

```
AudioRecord (16kHz, always on)
  -> openWakeWord (ONNX, on-device)  "Hey Jarvis"
  -> Android SpeechRecognizer (en-IN, Hinglish)
  -> Gemini Flash + function calling
  -> ToolExecutor (call / whatsapp / sms / app / alarm / volume / brightness / wifi)
  -> Gemini TTS (fallback: Android TextToSpeech)
  -> wapas listening
```

## Features

| Feature | Status |
| --- | --- |
| Custom wake word (openWakeWord ONNX) | ✅ |
| Always-on Foreground Service | ✅ |
| Lock screen pe bhi kaam | ✅ |
| Hinglish speech-to-text | ✅ |
| Gemini Flash function calling | ✅ |
| Gemini TTS + Android TTS fallback | ✅ |
| Call / WhatsApp / SMS | ✅ |
| App kholna | ✅ |
| Alarm & reminder | ✅ |
| Volume / brightness / WiFi panel | ✅ |
| General sawal-jawab | ✅ |
| Compose chat UI | ✅ |

## Quick Start

1. Repo clone karo, Android Studio me kholo.
2. Root me `local.properties` file me apni key daalo:
   ```properties
   GEMINI_API_KEY=AIza...your_key_here
   ```
   Key yahan se lo: https://aistudio.google.com/apikey
3. Wake word model files `app/src/main/assets/` me daalo:
   - `melspectrogram.onnx`
   - `embedding_model.onnx`
   - `wakeword.onnx`  (aapka custom naam)
   
   Detail me steps: [`docs/WAKE_WORD.md`](docs/WAKE_WORD.md)
4. Run karo. App khulte hi saari permissions allow karo.
5. "Start Listening" dabao -> ab bolo: **"Hey Jarvis, volume 50 kar do"**

Bina wake word model ke bhi app chalega — mic button se manually bol sakte ho.

## Docs

- [`docs/SETUP.md`](docs/SETUP.md) — step-by-step setup (zero experience ke liye)
- [`docs/WAKE_WORD.md`](docs/WAKE_WORD.md) — custom wake word train karna
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — code kaise kaam karta hai

## Zaroori Limitations

- **WiFi/Bluetooth toggle**: Android 10+ pe app silently on/off nahi kar sakti. App Settings panel kholta hai.
- **WhatsApp**: message type ho kar khulta hai, send button user ko dabana padta hai (WhatsApp ka rule).
- **Battery**: OEM phones (Xiaomi/Oppo/Vivo) service kill karte hain — app me "No restrictions" + Autostart on karo.
- **API key**: local build ke liye theek hai. Public app me backend proxy use karna.

## License

MIT
