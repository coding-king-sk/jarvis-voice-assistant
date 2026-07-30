# Jarvis — Features aur Setup

Sab kuch Hinglish me bol sakte ho. Neeche har feature, uske example commands,
aur agar koi extra permission chahiye to uska raasta likha hai.

---

## 1. Rozmarra ke chhote kaam

| Bolo | Kya hoga |
| --- | --- |
| "Torch on karo" / "Light band karo" | Flashlight on/off (instant, koi permission nahi) |
| "Phone silent kar do" | Ringer silent |
| "Vibrate pe laga do" | Ringer vibrate |
| "Do not disturb on karo" | DND on/off |
| "Battery kitni hai?" | Battery, charging, volume, ringer, time |
| "Bluetooth kholo" | Bluetooth settings |
| "Hotspot chalu karo" | Hotspot settings page |
| "Brightness 30 percent karo" | Screen brightness |

**Permission chahiye:** silent/DND ke liye ek baar
`Settings > Notifications > Do Not Disturb access > Jarvis` — app khud kholegi.

---

## 2. Awaaz se zindagi aasan

| Bolo | Kya hoga |
| --- | --- |
| "Notifications sunao" / "Kya naya aaya?" | Naye notifications padh ke sunata hai |
| "Reply karo: theek hai aa raha hoon" | Aakhri message ka reply bhejta hai, app khole bina |
| "Clipboard padho" | Jo copy kiya hai wo sunata hai |
| "Screen padho" / "Ye kya likha hai?" | Screen ka text sunata hai |

**Permission chahiye (ek baar):**

- Notifications ke liye:
  `Settings > Notifications > Device & app notifications > Jarvis` → on
- Screen padhne ke liye:
  `Settings > Accessibility > Jarvis` → on

Dono baar app khud settings khol degi jab pehli baar bologe.

> Clipboard Android 10+ pe sirf tab padha ja sakta hai jab Jarvis screen pe khuli ho.

---

## 3. Offline mode

Internet na ho to bhi ye chalte hain — Gemini ki zaroorat hi nahi:

- Torch, silent, vibrate, DND
- Volume, brightness
- Battery / time / status
- Music play, pause, next, previous
- Notifications, clipboard, screen padhna
- WiFi / Bluetooth / hotspot / location settings
- "Mummy ko call karo"
- "YouTube kholo"

Offline hone par awaaz bhi phone ki apni TTS se aayegi (Gemini TTS ki jagah).
Jo cheez samajh na aaye, Jarvis saaf bata dega ki internet chahiye.

Logic: `core/OfflineRouter.kt`

---

## 4. Camera se sawaal

Input bar me **camera icon** dabao → photo kheencho → Jarvis batayega usme kya hai.

Kaam aata hai:

- "Ye kaunsa plant hai?"
- Kisi form ya bill ko padhwana
- Kisi cheez ka naam pata karna

Pehli baar camera permission maangega. Iske liye internet zaroori hai.

---

## 5. Music control

| Bolo | Kya hoga |
| --- | --- |
| "Gaana chalao" / "Pause karo" | Play / pause |
| "Agla gaana" / "Pichla gaana" | Next / previous |
| "Arijit Singh chalao" | Us gaane ko search karke chalata hai |

Spotify, YouTube Music, Gaana, JioSaavn — sab pe kaam karta hai, kyunki hum wahi
signal bhejte hain jo headphone ka button bhejta hai. Koi permission nahi chahiye.

---

## 6. Widget + Quick Settings tile

**Home screen widget**
Home screen pe lambi der tap karo → Widgets → Jarvis → drag karo.
Orb pe tap = seedha sunna shuru.

**Quick Settings tile**
Notification shade neeche kheencho → pencil / edit → "Jarvis" tile ko upar drag karo.
Ab kisi bhi app se, shade kholo aur ek tap me Jarvis.

---

## 7. Orb me asli voice waveform

Jab Jarvis sun raha hota hai, orb ke chaaro taraf 72 lehrein banti hain jo
aapki awaaz ke hisaab se hilti hain — zor se bologe to badi, dheere bologe to chhoti.

Ye fake animation nahi hai: `SpeechRecognizer` ka live RMS level seedha orb tak
jaata hai (`AssistantEngine.micLevel`), aur spring animation se smooth kiya jaata hai.

Orb ke rang har state pe badalte hain:

| State | Rang |
| --- | --- |
| Idle | Neela-purple, dheere saans leta hua |
| Listening | Cyan + waveform + ripple rings |
| Thinking | Purple-pink |
| Acting | Amber-orange |
| Speaking | Teal, dhadakta hua |

---

## Pehli baar setup (5 minute)

1. `local.properties` me `GEMINI_API_KEY=...` daalo
2. App install karke kholo → mic, contacts, call, SMS permissions do
3. "Wake word band" pill pe tap karke background listening on karo
4. Battery optimization se Jarvis ko chhoot do (app khud maangegi)
5. Notification access on karo (notifications sunane ke liye)
6. Accessibility on karo (screen padhne ke liye)
7. Widget aur QS tile add kar lo
