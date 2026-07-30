# Fully Offline Mode

Jarvis ab bina internet ke bhi pura kaam karta hai. Neeche kya-kya chalta hai
aur ek baar ka setup likha hai.

---

## Kaise kaam karta hai

```
Aapki awaaz
    |
    v
Phone ka offline speech pack   (internet nahi -> EXTRA_PREFER_OFFLINE)
    |
    v
OfflineRouter  <-- 0 MB, koi AI model nahi, sirf Hinglish pattern matching
    |                    |
    | tool               | seedha jawab
    v                    v
ToolExecutor         Phone ki apni TTS
```

Internet ho to Gemini use hota hai (behtar samajh ke liye).
Agar Gemini fail ho jaye — net gir gaya, quota khatam, key galat — to Jarvis chup
nahi baithta, wahi command offline dimaag se chala deta hai.

---

## Ek baar ka setup (zaroori)

**Offline speech pack download karo:**

`Settings > Google > All services > Search, Assistant & Voice > Voice >
Offline speech recognition > English (India)` → Download

(Kuch phones me raasta: `Settings > System > Languages & input > Voice input >
Google > Offline speech recognition`)

**Offline TTS voice download karo:**

`Settings > Accessibility > Text-to-speech output > Google TTS > Install voice data
> English (India)`

Bas. Ab flight mode me bhi Jarvis sunta aur bolta hai.

---

## Offline me kya-kya chalta hai

### Device controls

| Bolo | Kaam |
| --- | --- |
| "Torch on karo" / "Batti band karo" | Flashlight |
| "Phone silent kar do" | Ringer silent |
| "Vibrate pe laga do" | Ringer vibrate |
| "Awaaz chalu karo" | Ringer normal |
| "DND on karo" | Do Not Disturb |
| "Volume 60 percent karo" / "Awaaz badhao" | Volume |
| "Brightness kam karo" | Screen brightness |
| "WiFi band karo" | WiFi |
| "Bluetooth kholo" / "Hotspot chalu karo" | Settings page |
| "Battery kitni hai?" | Battery + charging + time |

### Alarm aur reminder

| Bolo | Kaam |
| --- | --- |
| "Subah 7 baje ka alarm laga do" | Alarm 07:00 |
| "Shaam 6 baje alarm" | Alarm 18:00 |
| "Saat baje ka alarm" | Hindi number bhi samajhta hai |
| "10 minute baad yaad dilana ki dawai leni hai" | Reminder notification |

### Call aur message

| Bolo | Kaam |
| --- | --- |
| "Mummy ko call karo" | Direct call |
| "Papa ko sms karo ki late aaunga" | SMS (cellular pe chalta hai) |
| "Bhai ko whatsapp karo ki pahunch gaya" | WhatsApp queue kar dega |

### Music

"Gaana chalao", "Pause karo", "Agla gaana", "Pichla gaana" — downloaded music pe
sab chalta hai, kyunki hum wahi signal bhejte hain jo headphone button bhejta hai.

### Padhna

"Notifications sunao", "Clipboard padho", "Screen padho", "Reply karo: theek hai"

### Bina tool ke seedhe jawab

| Bolo | Jawab |
| --- | --- |
| "Kitne baje hain?" | "Abhi 9:14 PM baje hain." |
| "Aaj kaunsa din hai?" | Poori date |
| "Kal ki tarikh?" | Kal ki date |
| "25 into 4" / "das jama paanch" | Calculator |
| "Namaste" / "Kaise ho?" | Chhoti baat |
| "Kya kar sakte ho?" | Saari abilities |

---

## Hindi numbers

OfflineRouter pehle shabdon ko numbers me badalta hai, isliye ye sab chalta hai:

`ek, do, teen, char, paanch, chhe, saat, aath, nau, das, gyarah, barah, pandrah,
bees, pachees, tees, chalis, pachas, saath, sattar, assi, nabbe, sau`

Matlab "pachas percent volume" aur "50 percent volume" dono kaam karte hain.

---

## Offline me kya NAHI chalta

- **Camera se sawaal** — photo samajhne ke liye Gemini chahiye
- **General knowledge** — "Taj Mahal kisne banaya?" jaise sawaal
- **Lambi baat-cheet** — offline dimaag commands ke liye hai, gappe ke liye nahi
- **Gemini TTS ki premium awaaz** — offline me phone ki apni awaaz aati hai

Internet wapas aate hi ye sab apne aap chalu ho jaate hain. Koi switch nahi dabana.

---

## Naya offline command kaise jodein

`app/src/main/java/com/rehan/jarvis/core/OfflineRouter.kt` kholo aur apne pattern
ka ek line jod do:

```kotlin
if (has(t, "selfie", "photo kheencho")) {
    return tool("open_app", JSONObject().put("app_name", "camera"))
}
```

Seedha jawab dena ho to:

```kotlin
if (has(t, "i love you")) return speak("Main bhi, boss.")
```
