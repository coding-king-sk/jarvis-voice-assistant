# Setup — Step by Step (zero experience ke liye)

## 1. Android Studio install karo

1. https://developer.android.com/studio se download karo
2. Install karte waqt sab default rehne do (SDK bhi साथ install ho jaayega)
3. Pehli baar khulne me 10-15 minute lagta hai — ghabrana nahi

## 2. Project kholo

```bash
git clone https://github.com/coding-king-sk/jarvis-voice-assistant.git
```

Android Studio -> **Open** -> ye folder select karo.
Gradle sync apne aap chalega (pehli baar 5-10 min, internet chahiye).

## 3. Gemini API key daalo

1. https://aistudio.google.com/apikey pe jao, **Create API key** dabao
2. Project ke root folder me `local.properties` file kholo (nahi hai to bana lo)
3. Ye line add karo:

```properties
GEMINI_API_KEY=AIzaSy...yahan_apni_key
```

> `local.properties` `.gitignore` me hai, isliye ye kabhi GitHub pe nahi jaayegi. Key kabhi kisi ko mat do.

## 4. Phone connect karo

1. Phone pe **Settings -> About Phone -> Build Number** pe 7 baar tap karo (Developer mode on)
2. **Settings -> Developer Options -> USB Debugging** on karo
3. USB cable se laptop se jodo, phone pe "Allow" dabao
4. Android Studio me upar phone ka naam dikhega -> green **Run** button dabao

## 5. Permissions allow karo

App khulte hi ye maangega — sab **Allow** karo:

- Microphone (bolne ke liye)
- Contacts (naam se call karne ke liye)
- Phone (call lagane ke liye)
- SMS (message bhejne ke liye)
- Notifications (service aur reminder ke liye)

## 6. Test karo

Mic button dabao aur bolo:

- "Volume 50 kar do"
- "YouTube kholo"
- "Subah 7 baje ka alarm laga do"
- "Delhi ka capital kya hai"

Sab kaam kare to Phase 1 done!

## 7. Wake word on karo

1. ONNX files daalo — [`WAKE_WORD.md`](WAKE_WORD.md) padho
2. App me **Start** dabao
3. Battery optimization ka popup aaye to **Allow** karo
4. Ab phone jeb me rakhkar bolo: **"Hey Jarvis"**

## 8. Zaroori: OEM battery settings

Xiaomi / Redmi / Poco / Oppo / Vivo / Realme phones background service ko maar dete hain.

Phone Settings me jaake ye karo:

- **Battery -> App battery saver -> Jarvis -> No restrictions**
- **Autostart / Auto-launch -> Jarvis -> ON**
- Recent apps me Jarvis ko **lock** kar do (padlock icon)

Bina iske wake word kuch der baad band ho jaayega.

---

## Common problems

| Problem | Solution |
| --- | --- |
| "Gemini API key nahi mili" | `local.properties` check karo, phir **Sync Project** dabao |
| Gradle sync fail | Internet check karo; **File -> Invalidate Caches -> Restart** |
| Bolne pe kuch nahi hota | Logcat kholo, filter `Jarvis` — error dikh jaayega |
| Wake word trigger nahi hota | `threshold` 0.5 se 0.35 karo `WakeWordDetector.kt` me |
| Galat trigger bar bar | `threshold` 0.6-0.7 kar do |
| Call nahi lagti | CALL_PHONE permission check karo (Settings -> Apps -> Jarvis) |
| Awaaz robotic hai | Gemini TTS fail ho raha hai (internet ya key) — Android TTS pe fallback ho gaya |
