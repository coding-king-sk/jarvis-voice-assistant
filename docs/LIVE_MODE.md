# Live Call Mode aur Personality

Jarvis ab robot ki tarah baat nahi karta. Do badle hue hisse:

## 1. Bolne ka andaaz

- Casual Hinglish, bilkul dost jaisi — "lo bhai ho gaya", "ek second", "arre haan".
- Halka sa mazaak, par har baar nahi.
- Jawab 1-2 line ka, kyunki ye suna jaata hai padha nahi jaata.
- Har jawab ke baad "aur kya madad karun" nahi bolta.
- Awaaz: Gemini ki **Puck** voice, casual style instruction ke saath.
  Settings me dusri voice bhi choose kar sakte ho.

## 2. Live call mode

Pehle: wake word bolo -> ek sawal -> jawab -> phir se wake word bolo.

Ab: ek baar shuru hone ke baad Jarvis **sunta rehta hai**, bilkul phone call jaisa.

- Jawab khatam hote hi dobara mic khul jaata hai, wake word ki zarurat nahi.
- **Beech me tok sakte ho.** Jarvis bol raha ho aur tum bolna shuru kar do,
  to wo turant chup ho kar sun-ne lag jaata hai.
- 3 minute tak koi baat na ho, ya do baar kuch sunai na de, to apne aap
  wapas wake word mode me chala jaata hai (battery bachane ke liye).
- "Bas", "bye", "chup ho ja", "band karo" bolne pe turant live baat khatam.

### Beech me tokna kaise kaam karta hai

`BargeInDetector` bolte waqt mic sunta rehta hai. Speaker ki apni awaaz mic me
wapas na aaye, iske liye phone ka hardware **echo canceller (AEC)** aur
**noise suppressor** on kiye jaate hain. Lagataar teen frames tak awaaz tez rahe
tabhi maana jaata hai ki user bol raha hai — isse khaansi ya darwaze ki awaaz se
Jarvis chup nahi hota.

Agar phone me AEC na ho to Jarvis kabhi kabhi apni hi awaaz se ruk sakta hai.
Aisa ho to headphone laga lo, ya `BargeInDetector` me `THRESHOLD` badha do.
