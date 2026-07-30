# Custom Wake Word banana

Aapne custom naam choose kiya hai, isliye khud ka model train karna padega.
Good news: **coding nahi karni**, sab Google Colab pe ho jaata hai, aur free hai.

## Kya chahiye

| File | Kaam | Kahan se |
| --- | --- | --- |
| `melspectrogram.onnx` | audio -> mel frames | ready-made |
| `embedding_model.onnx` | mel -> 96-dim embedding | ready-made |
| `wakeword.onnx` | embedding -> score | **aap train karoge** |

## Step 1 — Ready-made models download karo

openWakeWord ke pre-trained shared models:

https://github.com/dscripka/openWakeWord/releases

Release assets me se `melspectrogram.onnx` aur `embedding_model.onnx` download karo.
Dono ko `app/src/main/assets/` me daal do.

## Step 2 — Apna wake word train karo

openWakeWord ka official Colab notebook use karo (automatic synthetic data generation):

https://github.com/dscripka/openWakeWord#training-new-models

Colab me:

1. `target_phrase` me apna wake word likho, jaise `"hey rehan"` ya `"suno jarvis"`
2. Notebook chalao — ye khud hazaaron synthetic samples banata hai (TTS se)
3. Training me GPU pe ~1 ghanta lagta hai
4. Output `.onnx` file download karo

### Achha wake word kaise choose karein

- **3-4 syllable** rakho — chhote shabd (jaise "Ravi") bahut false trigger denge
- Rozmarra ki baat me na aane wala combination lo
- ✅ Achhe: "Hey Rehan", "Suno Jarvis", "Okay Bantu"
- ❌ Bure: "Bhai", "Sun", "Hello"

## Step 3 — App me daalo

Trained file ka naam `wakeword.onnx` rakho aur `app/src/main/assets/` me daal do.

Folder aisa dikhna chahiye:

```
app/src/main/assets/
  melspectrogram.onnx
  embedding_model.onnx
  wakeword.onnx
```

App rebuild karo. Ab **Start** dabate hi wake word listening chalu.

## Step 4 — Sensitivity tune karo

`WakeWordDetector.kt` me:

```kotlin
var threshold: Float = 0.5f
```

| Problem | Fix |
| --- | --- |
| Bulane pe nahi sunta | `0.35f` kar do |
| Bina bulaye trigger ho jaata hai | `0.65f` kar do |

Apne kamre me, TV chalte hue, aur bahar — teeno jagah test karo.

## Aasaan alternative

Agar training jhanjhat lage, to shuru me openWakeWord ka ready-made
`hey_jarvis_v0.1.onnx` use karo. Bas usko `wakeword.onnx` naam de do.
Baad me apna custom model bana kar replace kar dena.

## Technical detail (samajhne ke liye)

```
1280 samples (80ms audio)
   -> melspectrogram.onnx  -> mel frames [32 bins]
   -> 76 frames jama karo
   -> embedding_model.onnx -> 96-dim vector
   -> 16 embeddings jama karo (~1.3 second audio)
   -> wakeword.onnx        -> score 0.0 se 1.0
   -> score > threshold ? WAKE!
```

Sab kuch **on-device** hota hai — audio kabhi internet pe nahi jaata jab tak
wake word detect na ho.
